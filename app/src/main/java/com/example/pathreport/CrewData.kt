package com.example.pathreport

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StaffMember(val id: String = UUID.randomUUID().toString(), val name: String)
data class CrewSelection(val driverId: String? = null, val companionIds: List<String> = emptyList())
data class TripCrew(val driver: StaffMember? = null, val companions: List<StaffMember> = emptyList()) {
    fun validate() {
        val people = listOfNotNull(driver) + companions
        require(people.all { it.id.isNotBlank() && it.name.isNotBlank() })
        require(people.map { it.id }.distinct().size == people.size)
    }
    fun canonical() = copy(companions = companions.sortedBy { it.id })
}

internal fun CareData.withStaff(member: StaffMember): CareData {
    val clean = member.copy(name = member.name.trim())
    require(clean.name.isNotBlank() && clean.name.length <= 60)
    require(staff.none { it.id != member.id && it.name.equals(clean.name, true) })
    return copy(staff = staff.filterNot { it.id == member.id } + clean)
}
internal fun CareData.selectCrew(selection: CrewSelection): CareData = copy(crewSelection = selection).also { it.validate() }
internal fun CareData.crewSnapshot(): TripCrew = TripCrew(
    crewSelection.driverId?.let { id -> staff.single { it.id == id }.copy() },
    crewSelection.companionIds.map { id -> staff.single { it.id == id }.copy() }
).canonical()

internal object CrewCodec {
    fun person(p: StaffMember) = JSONObject().put("id", p.id).put("name", p.name)
    fun person(p: JSONObject) = StaffMember(p.getString("id"), p.getString("name"))
    fun people(list: List<StaffMember>) = JSONArray().apply { list.forEach { put(person(it)) } }
    fun people(array: JSONArray) = List(array.length()) { person(array.getJSONObject(it)) }
    fun selection(s: CrewSelection) = JSONObject().put("driver", s.driverId ?: JSONObject.NULL).put("companions", JSONArray(s.companionIds))
    fun selection(o: JSONObject): CrewSelection {
        val ids = o.getJSONArray("companions")
        return CrewSelection(if (o.isNull("driver")) null else o.getString("driver"), List(ids.length()) { ids.getString(it) })
    }
    fun snapshot(c: TripCrew) = JSONObject().put("driver", c.driver?.let { person(it) } ?: JSONObject.NULL).put("companions", people(c.companions))
    fun snapshot(o: JSONObject) = TripCrew(if (o.isNull("driver")) null else person(o.getJSONObject("driver")), people(o.getJSONArray("companions")))
}

internal fun TripCrew?.driverLabel() = if (this == null) "미기록" else driver?.name ?: "미선택"
internal fun TripCrew?.companionsLabel() = if (this == null) "미기록" else companions.joinToString(", ") { it.name }.ifEmpty { "없음" }

/** Remove future selection only; historical trip snapshots remain intact. */
internal fun CareData.withoutStaff(id: String): CareData = copy(
    staff = staff.filterNot { it.id == id },
    crewSelection = crewSelection.copy(
        driverId = crewSelection.driverId?.takeUnless { it == id },
        companionIds = crewSelection.companionIds.filterNot { it == id }
    )
).also { it.validate() }
