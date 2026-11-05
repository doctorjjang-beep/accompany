package com.example.pathreport

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TripExcelRow(val date: String, val name: String, val arrival: String, val departure: String, val crew: TripCrew? = null)

fun selectedTripExcelRows(trips: List<Trip>, selectedIds: Set<String>, zone: TimeZone = TimeZone.getDefault()): List<TripExcelRow> =
    tripExcelRows(trips.filter { it.id in selectedIds }, zone)

/** Group by stable identity, not name; use the same local start date as history. */
fun tripExcelRows(trips: List<Trip>, zone: TimeZone = TimeZone.getDefault()): List<TripExcelRow> {
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { timeZone = zone }
    val time = SimpleDateFormat("HH:mm", Locale.KOREA).apply { timeZone = zone }
    data class Entry(val trip: Trip, val target: TripTarget)
    return trips.filter { it.endedAt != null }.sortedBy { it.startedAt }
        .flatMap { trip -> trip.targets.map { Entry(trip, it) } }
        .groupBy { Triple(day.format(Date(it.trip.startedAt)), it.target.elderId, it.trip.crew?.canonical()) }
        .map { (key, entries) ->
            fun times(direction: TripDirection) = entries.filter { it.trip.direction == direction }
                .mapNotNull { entry -> entry.trip.arrivals.find { it.elderId == entry.target.elderId }?.estimatedAt }
            TripExcelRow(key.first, entries.last().target.elderName,
                times(TripDirection.TO_CENTER).minOrNull()?.let { time.format(Date(it)) }.orEmpty(),
                times(TripDirection.FROM_CENTER).maxOrNull()?.let { time.format(Date(it)) }.orEmpty(), key.third)
        }.sortedWith(compareBy<TripExcelRow> { it.date }.thenBy { it.name })
}

/** Small, dependency-free OOXML workbook. Inline strings cannot execute as formulas. */
fun writeTripExcel(output: OutputStream, rows: List<TripExcelRow>) {
    val sheets = rows.groupBy { it.crew?.canonical() }.entries.toList()
    require(sheets.isNotEmpty())
    require(sheets.size <= 1000) { "담당자 조합이 너무 많습니다. 날짜를 나누어 내보내 주세요." }
    require(sheets.all { it.value.size <= 1_048_574 }) { "엑셀의 최대 행 수를 초과했습니다." }
    fun xml(value: String) = buildString {
        value.codePoints().forEach { c ->
            when (c) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                34 -> append("&quot;")
                else -> if (c == 9 || c == 10 || c == 13 || c in 0x20..0xD7FF || c in 0xE000..0xFFFD || c in 0x10000..0x10FFFF) appendCodePoint(c)
            }
        }
    }
    ZipOutputStream(output).use { zip ->
        fun part(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>${sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
        part("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        part("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.indices.joinToString("") { "<sheet name=\"운행 내역 ${it + 1}\" sheetId=\"${it + 1}\" r:id=\"rId${it + 1}\"/>" }}</sheets></workbook>""")
        part("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" }}<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        part("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="11"/><name val="맑은 고딕"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment wrapText="1" vertical="center"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
        sheets.forEachIndexed { sheetIndex, entry ->
        val crew = entry.key
        val sheetRows = entry.value
        val heading = "운전자명: ${crew.driverLabel()}    동승자명: ${crew.companionsLabel()}"
        require(heading.length <= 32767) { "담당자 이름이 엑셀 셀 길이 제한을 초과했습니다." }
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet${sheetIndex + 1}.xml"))
        fun emit(value: String) { zip.write(value.toByteArray(Charsets.UTF_8)) }
        emit("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="24" customWidth="1"/><col min="3" max="4" width="16" customWidth="1"/></cols><sheetData>""")
        fun row(number: Int, cells: List<String>) {
            emit("<row r=\"$number\"" + (if (number == 1) " ht=\"${maxOf(30, ((heading.length + 49) / 50) * 20).coerceAtMost(409)}\" customHeight=\"1\"" else "") + ">")
            cells.forEachIndexed { index, value ->
                emit("<c r=\"${'A' + index}$number\" t=\"inlineStr\" s=\"${if (number <= 2) 1 else 0}\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
            }
            emit("</row>")
        }
        row(1, listOf(heading))
        row(2, listOf("날짜", "어르신이름", "등원", "하원"))
        sheetRows.forEachIndexed { index, r -> row(index + 3, listOf(r.date, r.name, r.arrival, r.departure)) }
        emit("</sheetData><autoFilter ref=\"A2:D${sheetRows.size + 2}\"/><mergeCells count=\"1\"><mergeCell ref=\"A1:D1\"/></mergeCells></worksheet>")
        zip.closeEntry()
        }
    }
}
