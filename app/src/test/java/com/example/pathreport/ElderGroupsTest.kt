package com.example.pathreport

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ElderGroupsTest {
    private val a = Elder("a", "동명이인")
    private val b = Elder("b", "동명이인")
    private val c = Elder("c", "장소 없음")
    private val p = StopPlace("p", "장소1", latitude = 37.0, longitude = 127.0)
    private val q = StopPlace("q", "장소2", latitude = 37.1, longitude = 127.1)
    private val data = CareData(places = listOf(p, q), elders = listOf(a,b,c),
        links = listOf(PlaceElder("p","a"), PlaceElder("q","a"), PlaceElder("p","b")),
        groups = listOf(ElderGroup("g1", "1호차", listOf("a","c")), ElderGroup("g2", "2호차", listOf("a","b"))))

    @Test fun overlappingGroupsPreserveChosenPlaceAndSkipUnavailableMembers() {
        val first = data.addTripMembers(mapOf("a" to "q"), data.groups[0].elderIds)
        val both = data.addTripMembers(first, data.groups[1].elderIds)
        assertEquals(mapOf("a" to "q", "b" to "p"), both)
        assertEquals(both, data.addTripMembers(both, data.groups[1].elderIds))
    }
    @Test fun staleChoicesAreRemovedAndEligibleMembersCanBeAddedAgain() {
        assertEquals(emptyMap<String,String>(), data.addTripMembers(mapOf("a" to "gone", "gone" to "p"), emptyList()))
        assertEquals(mapOf("a" to "p"), data.addTripMembers(mapOf("a" to "gone"), listOf("a")))
    }
    @Test fun membershipsUseIdsAndPreserveOtherMembers() {
        val changed = data.setElderGroups("a", setOf("g2"))
        assertEquals(listOf("c"), changed.groups[0].elderIds)
        assertEquals(setOf("a","b"), changed.groups[1].elderIds.toSet())
        assertEquals(data.elders, changed.elders)
        assertEquals(data.links, changed.links)
    }
    @Test fun deletingPersonCleansAllGroups() {
        val changed = data.withoutElder("a")
        changed.validate()
        assertTrue(changed.groups.all { "a" !in it.elderIds })
        assertEquals(2, changed.groups.size)
    }
    @Test fun groupsRoundTripAndLegacySchemaLoads() {
        val recorded = data.startTrip(TripDirection.TO_CENTER, mapOf("a" to "q"), 1000).let {
            it.endTrip(it.trips.single().id, 2000)
        }
        assertEquals(recorded, CareCodec.decode(CareCodec.encode(recorded)))
        val legacy = JSONObject(String(CareCodec.encode(recorded), Charsets.UTF_8)).put("version", 6)
        legacy.remove("groups")
        assertEquals(recorded.copy(groups = emptyList()), CareCodec.decode(legacy.toString().toByteArray()))
        val removed = recorded.copy(groups = emptyList())
        removed.validate()
        assertEquals(recorded.trips, removed.trips)
    }
    @Test fun invalidGroupsAreRejectedAndSavingTrimsNames() {
        assertThrows(IllegalArgumentException::class.java) { data.copy(groups = listOf(ElderGroup(name="bad",elderIds=listOf("missing")))).validate() }
        assertThrows(IllegalArgumentException::class.java) { data.saveGroup(ElderGroup(name=" 1호차 ")) }
        val changed = data.saveGroup(ElderGroup("g3", " 오전반 ", listOf("a","a","missing")))
        assertEquals(ElderGroup("g3", "오전반", listOf("a")), changed.groups.last())
        changed.validate()
    }
}
