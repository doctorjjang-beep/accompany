package com.example.pathreport

import java.util.UUID

data class ElderGroup(val id: String = UUID.randomUUID().toString(), val name: String,
    val elderIds: List<String> = emptyList())

internal fun CareData.saveGroup(group: ElderGroup): CareData {
    val name = group.name.trim()
    require(name.isNotEmpty())
    require(groups.none { it.id != group.id && it.name.equals(name, ignoreCase = true) })
    val members = group.elderIds.distinct().filter { id -> elders.any { it.id == id } }
    return copy(groups = groups.filterNot { it.id == group.id } + group.copy(name = name, elderIds = members))
}

internal fun CareData.setElderGroups(elderId: String, groupIds: Set<String>): CareData {
    require(elders.any { it.id == elderId })
    return copy(groups = groups.map { group -> group.copy(elderIds =
        group.elderIds.filterNot { it == elderId } + if (group.id in groupIds) listOf(elderId) else emptyList()) })
}

internal fun CareData.eligibleTripPlaces(): Map<String, List<StopPlace>> = elders.associate { elder ->
    elder.id to places.filter { place -> place.latitude != null && place.longitude != null &&
        validCoordinates(place.latitude, place.longitude) && PlaceElder(place.id, elder.id) in links }
}

/** Add members without losing a manually selected stop or duplicating overlapping groups. */
internal fun CareData.addTripMembers(choices: Map<String, String>, memberIds: List<String>): Map<String, String> {
    val eligible = eligibleTripPlaces()
    val result = choices.filter { (elder, place) -> eligible[elder]?.any { it.id == place } == true }.toMutableMap()
    memberIds.distinct().forEach { elder ->
        if (elder !in result) eligible[elder]?.firstOrNull()?.let { result[elder] = it.id }
    }
    return result
}

/** Apply only the requested membership delta to the latest saved group. */
internal fun CareData.changeGroupMembers(groupId: String, elderIds: Set<String>, adding: Boolean): CareData {
    val group = groups.single { it.id == groupId }
    val validIds = elderIds.intersect(elders.map { it.id }.toSet())
    val members = if (adding) (group.elderIds + validIds).distinct() else group.elderIds.filterNot { it in validIds }
    return copy(groups = groups.map { if (it.id == groupId) it.copy(elderIds = members) else it })
}
