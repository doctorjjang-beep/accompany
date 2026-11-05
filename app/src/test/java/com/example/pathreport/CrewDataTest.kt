package com.example.pathreport

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CrewDataTest {
    private val driver = StaffMember("d", "가상 운전자")
    private val helper = StaffMember("h", "가상 동승자")
    private val nextDriver = StaffMember("n", "다음 운전자")
    private val place = StopPlace("p", "가상 장소", latitude = 37.0, longitude = 127.0)
    private fun data() = CareData(places = listOf(place), elders = listOf(Elder("e", "가상 어르신")),
        links = listOf(PlaceElder("p", "e")), staff = listOf(driver, helper, nextDriver),
        crewSelection = CrewSelection(driver.id, listOf(helper.id)))
    @Test fun settingsAndSnapshotsSurviveReloadAndChanges() {
        val loaded = CareCodec.decode(CareCodec.encode(data()))
        assertEquals(data(), loaded)
        val started = loaded.startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 1000)
        val changed = started.selectCrew(CrewSelection(nextDriver.id)).withStaff(driver.copy(name = "수정 이름"))
        assertEquals(TripCrew(driver, listOf(helper)), changed.trips.single().crew)
        val ended = changed.endTrip(changed.trips.single().id, 5000)
        val tomorrow = CareCodec.decode(CareCodec.encode(ended)).startTrip(TripDirection.FROM_CENTER, mapOf("e" to "p"), 86401000)
        assertEquals(nextDriver, tomorrow.trips.last().crew?.driver)
        assertEquals(TripCrew(driver, listOf(helper)), tomorrow.trips.first().crew)
    }
    @Test fun legacyRecordsRemainUnknownRatherThanUsingCurrentStaff() {
        val original = data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 1000)
        val json = JSONObject(String(CareCodec.encode(original), Charsets.UTF_8)).put("version", 7)
        json.remove("staff"); json.remove("crewSelection")
        json.getJSONArray("trips").getJSONObject(0).remove("crew")
        val restored = CareCodec.decode(json.toString().toByteArray())
        assertTrue(restored.staff.isEmpty())
        assertEquals(CrewSelection(), restored.crewSelection)
        assertNull(restored.trips.single().crew)
        assertEquals(original.trips.single().targets, restored.trips.single().targets)
    }
    @Test fun duplicateRolesAndDanglingStaffAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { data().selectCrew(CrewSelection("d", listOf("d"))) }
        assertThrows(IllegalArgumentException::class.java) { data().selectCrew(CrewSelection("missing")) }
        assertThrows(IllegalArgumentException::class.java) { data().selectCrew(CrewSelection("d", listOf("h", "h"))) }
        assertThrows(IllegalArgumentException::class.java) { data().withStaff(StaffMember(name = " ")) }
        assertThrows(IllegalArgumentException::class.java) { data().withStaff(StaffMember(name = driver.name)) }
    }
    private fun trip(id: String, direction: TripDirection, time: Long, crew: TripCrew?) = Trip(id, direction,
        listOf(TripTarget("e", "가상 어르신", place)), time, time + 60000,
        listOf(Arrival("e", "p", time + 1000, time + 21000)), crew)
    @Test fun excelCombinesSameCrewButSeparatesChangedCrewAndLegacy() {
        val crew = TripCrew(driver, listOf(helper, nextDriver))
        val morning = trip("a", TripDirection.TO_CENTER, 3600000, crew)
        val evening = trip("b", TripDirection.FROM_CENTER, 7200000, crew.copy(companions = crew.companions.reversed()))
        val all = listOf(morning, evening, trip("c", TripDirection.FROM_CENTER, 10800000, TripCrew(nextDriver)),
            trip("old", TripDirection.TO_CENTER, 14400000, null))
        val rows = selectedTripExcelRows(all, all.map { it.id }.toSet(), TimeZone.getTimeZone("UTC"))
        assertEquals(3, rows.size)
        val paired = rows.single { it.crew?.driver == driver }
        assertEquals("01:00", paired.arrival); assertEquals("02:00", paired.departure)
        assertEquals("", rows.single { it.crew?.driver == nextDriver }.arrival)
        assertEquals(1, selectedTripExcelRows(all, setOf("old"), TimeZone.getTimeZone("UTC")).size)
        val out = ByteArrayOutputStream()
        writeTripExcel(out, rows)
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; parts[entry.name] = zip.readBytes() }
        }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.values.forEach { factory.newDocumentBuilder().parse(ByteArrayInputStream(it)) }
        assertEquals(3, parts.keys.count { it.startsWith("xl/worksheets/") })
        val workbook = factory.newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("xl/workbook.xml")))
        assertEquals(3, workbook.getElementsByTagName("sheet").length)
        val headers = mutableListOf<String>()
        for (i in 1..3) {
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("xl/worksheets/sheet$i.xml")))
            assertEquals(0, doc.getElementsByTagName("f").length)
            val sheetRows = doc.getElementsByTagName("row")
            headers += sheetRows.item(0).textContent
            assertTrue(sheetRows.item(0).textContent.startsWith("운전자명:"))
            assertEquals("날짜어르신이름등원하원", sheetRows.item(1).textContent)
            assertFalse(sheetRows.item(2).textContent.contains("운전자명:"))
            assertEquals("A2:D3", doc.getElementsByTagName("autoFilter").item(0).attributes.getNamedItem("ref").nodeValue)
        }
        assertTrue(headers.any { it.contains("미기록") })
        assertTrue(headers.any { it.contains(driver.name) && it.contains(helper.name) })
        System.getenv("CREW_EXPORT_SAMPLE")?.let { java.io.File(it).writeBytes(out.toByteArray()) }
    }
    @Test fun formulaLikeStaffNamesRemainLiteral() {
        val out = ByteArrayOutputStream()
        writeTripExcel(out, listOf(TripExcelRow("2026-11-05", "가상", "09:00", "", TripCrew(driver.copy(name = "=SUM(1,2)<&")))))
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(zip.readBytes()))
                    assertEquals(0, doc.getElementsByTagName("f").length)
                    assertTrue(doc.documentElement.textContent.contains("=SUM(1,2)<&"))
                }
            }
        }
    }

    @Test fun staffDeletionClearsSelectionButPreservesHistoricalNamesAndExport() {
        val started = data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 1000)
        val ended = started.endTrip(started.trips.single().id, 5000)
        val before = tripExcelRows(ended.trips)
        val removedDriver = ended.withoutStaff(driver.id)
        assertNull(removedDriver.crewSelection.driverId)
        assertEquals(listOf(helper.id), removedDriver.crewSelection.companionIds)
        val removedBoth = removedDriver.withoutStaff(helper.id)
        assertEquals(CrewSelection(), removedBoth.crewSelection)
        assertEquals(listOf(nextDriver), removedBoth.staff)
        assertEquals(ended.trips, removedBoth.trips)
        assertEquals(before, tripExcelRows(removedBoth.trips))
        assertEquals(removedBoth, CareCodec.decode(CareCodec.encode(removedBoth)))
        assertEquals(removedBoth, removedBoth.withoutStaff("missing"))
    }
}
