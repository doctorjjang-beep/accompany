package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class QuickElderDataTest {
    @Test fun registersLinkedPlaceAndElderAndReloads() {
        val saved = CareData().registerElderAtLocation("  가상어르신  ", 37.0, 127.0)
        val restored = CareCodec.decode(CareCodec.encode(saved))
        assertEquals(saved, restored)
        assertEquals("가상어르신", restored.elders.single().name)
        assertEquals("가상어르신 승하차장소", restored.places.single().name)
        assertEquals(37.0, restored.places.single().latitude!!, 0.0)
        assertEquals(127.0, restored.places.single().longitude!!, 0.0)
        assertEquals(listOf(PlaceElder(restored.places.single().id, restored.elders.single().id)), restored.links)
    }
    @Test fun sameNamesDoNotOverwriteExistingPeopleOrLocations() {
        val original = CareData().registerElderAtLocation("가상어르신", 37.0, 127.0)
        val saved = original.registerElderAtLocation("가상어르신", 36.0, 128.0)
        assertEquals(original.elders.single(), saved.elders.first())
        assertEquals(original.places.single(), saved.places.first())
        assertEquals(2, saved.elders.map { it.id }.distinct().size)
        assertEquals(2, saved.places.map { it.id }.distinct().size)
        assertEquals(2, saved.links.size)
    }
    @Test fun rejectsEmptyNamesAndInvalidCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { CareData().registerElderAtLocation("  ", 37.0, 127.0) }
        assertThrows(IllegalArgumentException::class.java) { CareData().registerElderAtLocation("가상", Double.NaN, 127.0) }
        assertThrows(IllegalArgumentException::class.java) { CareData().registerElderAtLocation("가상", 91.0, 127.0) }
    }
}
