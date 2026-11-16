package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class TripAutoEndTest {
    private fun sample(t: Long, lat: Double = 37.0, speed: Double? = 0.0, accuracy: Double = 5.0) =
        GpsSample(lat, 127.0, accuracy, t, 1_000_000 + t, speed)
    private fun still(detector: TripIdleDetector, from: Long, until: Long, lat: Double = 37.0) {
        for (t in from..until step 1000) detector.accept(sample(t, lat), t)
    }
    @Test fun endsOnlyAfterFullHourProvenByFreshFixes() {
        val detector = TripIdleDetector()
        still(detector, 0, TripIdleDetector.IDLE_MS - 1000)
        assertFalse(detector.shouldEnd(TripIdleDetector.IDLE_MS))
        detector.accept(sample(TripIdleDetector.IDLE_MS), TripIdleDetector.IDLE_MS)
        assertTrue(detector.shouldEnd(TripIdleDetector.IDLE_MS))
        assertFalse(detector.shouldEnd(TripIdleDetector.IDLE_MS + TripIdleDetector.MAX_GAP_MS + 1))
    }
    @Test fun movementAndSpeedRestartTheHour() {
        val detector = TripIdleDetector()
        still(detector, 0, 3_599_000)
        detector.accept(sample(3_600_000, lat = 37.001), 3_600_000)
        assertFalse(detector.shouldEnd(3_600_000))
        still(detector, 3_601_000, 7_199_000, 37.001)
        detector.accept(sample(7_200_000, lat = 37.001, speed = 1.0), 7_200_000)
        assertFalse(detector.shouldEnd(7_200_000))
    }
    @Test fun badAccuracyMissingGpsAndRestartNeverCountAsAnIdleHour() {
        val detector = TripIdleDetector()
        still(detector, 0, 3_599_000)
        detector.accept(sample(3_600_000, accuracy = 50.0), 3_600_000)
        detector.accept(sample(3_601_000), 3_601_000)
        assertFalse(detector.shouldEnd(3_601_000))
        detector.accept(sample(7_300_000), 7_300_000)
        assertFalse(detector.shouldEnd(7_300_000))
        detector.reset()
        assertFalse(detector.shouldEnd(99_000_000))
        assertFalse(TripIdleDetector().shouldEnd(99_000_000))
    }
    @Test fun jitterClockChangesReplaysAndSlowDrift() {
        val detector = TripIdleDetector()
        for (t in 0L..3_600_000L step 1000) {
            // Small positional jitter and a backwards wall clock do not affect elapsed duration.
            detector.accept(sample(t, lat = 37.0 + if (t % 2000L == 0L) 0.00005 else -0.00005, speed = null)
                .copy(wallTimeMs = 1), t)
        }
        assertTrue(detector.shouldEnd(3_600_000))
        detector.reset()
        detector.accept(sample(0), 0)
        detector.accept(sample(0), 3_600_000)
        assertFalse(detector.shouldEnd(3_600_000))
        for (t in 0L..3_600_000L step 1000) detector.accept(sample(t, lat = 37.0 + t / 3_600_000.0 * 0.001, speed = null), t)
        assertFalse(detector.shouldEnd(3_600_000))
    }
    private val place = StopPlace("p", "가상 장소", latitude = 37.0, longitude = 127.0)
    private val trip = Trip("t", TripDirection.TO_CENTER,
        listOf(TripTarget("a", "가상 가", place), TripTarget("b", "가상 나", place)), 1000)
    private val a = Arrival("a", "p", 1000, 6000)
    private val b = Arrival("b", "p", 2000, 7000)
    @Test fun finalArrivalAndEndAreSavedTogetherAndRetryIsIdempotent() {
        val initial = CareData(trips = listOf(trip))
        val partial = initial.saveDetectedProgress("t", listOf(a), false, 8000)
        assertNull(partial.trips.single().endedAt)
        val completed = partial.saveDetectedProgress("t", listOf(a, b), false, 9000)
        assertEquals(7000L, completed.trips.single().endedAt)
        assertEquals(2, completed.trips.single().arrivals.size)
        assertEquals(completed, completed.saveDetectedProgress("t", listOf(b), false, 10000))
        assertEquals(completed, completed.endActiveTrip("t", 10000))
    }
    @Test fun idleEndKeepsPendingAndMissingTargetsAndDoesNotReopenManualEnd() {
        val initial = CareData(trips = listOf(trip))
        val ended = initial.saveDetectedProgress("t", listOf(a), true, 3_601_000)
        assertEquals(3_601_000L, ended.trips.single().endedAt)
        assertEquals(2, ended.trips.single().targets.size)
        assertEquals(listOf(a), ended.trips.single().arrivals)
        assertEquals(ended, ended.saveDetectedProgress("t", listOf(b), true, 4_000_000))
        assertEquals(ended, CareCodec.decode(CareCodec.encode(ended)))
    }
    @Test fun recoversPreviouslySavedCompleteTripWithoutChangingEndedHistory() {
        val completed = trip.copy(arrivals = listOf(a,b))
        val oldEnded = completed.copy(id = "old", endedAt = 9000)
        val data = CareData(trips = listOf(completed, oldEnded))
        val recovered = data.closeCompletedTrips()
        assertEquals(7000L, recovered.trips.first().endedAt)
        assertEquals(oldEnded, recovered.trips.last())
        assertEquals(recovered, recovered.closeCompletedTrips())
    }
    @Test fun invalidSampleAndWallClockRollbackCannotProduceInvalidEnd() {
        val detector = TripIdleDetector()
        detector.accept(sample(0), 0)
        detector.accept(sample(3_600_000).copy(latitude = Double.NaN), 3_600_000)
        assertFalse(detector.shouldEnd(3_600_000))
        val ended = CareData(trips = listOf(trip)).saveDetectedProgress("t", listOf(a), true, 500)
        assertEquals(6000L, ended.trips.single().endedAt)
    }
}
