package com.example.pathreport

internal fun CareData.registerElderAtLocation(name: String, latitude: Double, longitude: Double): CareData {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty())
    require(validCoordinates(latitude, longitude))
    val elder = Elder(name = cleanName)
    val place = StopPlace(name = "$cleanName 승하차장소", latitude = latitude, longitude = longitude)
    return copy(elders = elders + elder, places = places + place,
        links = links + PlaceElder(place.id, elder.id)).also { it.validate() }
}
