package com.example.pathreport

/** In-memory only: never store the idle anchor or raw GPS samples. */
internal class TripIdleDetector {
    companion object {
        const val IDLE_MS = 60 * 60 * 1000L
        const val RADIUS_METERS = 50.0
        const val MAX_GAP_MS = 15_000L
    }
    private var anchor: GpsSample? = null
    private var lastElapsed: Long? = null
    fun reset() { anchor = null; lastElapsed = null }
    fun tick(now: Long) {
        if (lastElapsed?.let { now < it || now - it > MAX_GAP_MS } == true) reset()
    }
    fun accept(sample: GpsSample, now: Long) {
        tick(now)
        if (!validCoordinates(sample.latitude, sample.longitude) || !sample.accuracyMeters.isFinite() ||
            sample.accuracyMeters !in 0.0..StopPolicy.MAX_ACCURACY_METERS || sample.elapsedMs < 0 ||
            sample.elapsedMs > now || now - sample.elapsedMs > MAX_GAP_MS || sample.wallTimeMs <= 0) {
            reset(); return
        }
        if (lastElapsed?.let { sample.elapsedMs <= it } == true) return
        lastElapsed = sample.elapsedMs
        // Idle duration depends on position, not reported GPS speed.
        val start = anchor
        if (start == null || distanceMeters(start.latitude, start.longitude, sample.latitude, sample.longitude) > RADIUS_METERS)
            anchor = sample
    }
    fun shouldEnd(now: Long): Boolean {
        tick(now)
        val start = anchor ?: return false
        val last = lastElapsed ?: return false
        // A fresh fix, not passage of wall time alone, must prove the full hour.
        return last - start.elapsedMs >= IDLE_MS
    }
}

/** Safe for delayed automatic/manual requests that race with a completed end. */
internal fun CareData.endActiveTrip(id: String, now: Long): CareData =
    if (trips.any { it.id == id && it.endedAt == null }) endTrip(id, now) else this

/** Also recovers older active trips whose final arrival was already saved. */
internal fun CareData.closeCompletedTrips(): CareData = copy(trips = trips.map { trip ->
    if (trip.endedAt == null && trip.targets.isNotEmpty() && trip.targets.all { target -> trip.arrivals.any { it.elderId == target.elderId } })
        trip.copy(endedAt = maxOf(trip.startedAt, trip.arrivals.maxOf { it.confirmedAt }))
    else trip
}).also { it.validate() }

internal fun CareData.saveDetectedProgress(id: String, pending: List<Arrival>, idle: Boolean, now: Long): CareData {
    val saved = appendDetectedArrivals(id, pending).closeCompletedTrips()
    return if (idle) saved.endActiveTrip(id, now) else saved
}
