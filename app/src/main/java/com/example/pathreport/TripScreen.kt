package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun tripTime(time: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA).format(Date(time))

@Composable
fun TripScreen(data: CareData, busy: Boolean, tracking: TrackingInfo = TrackingInfo(), requestLocation: () -> Unit = {}, requestNotifications: () -> Unit = {}, endTrip: ((String) -> Unit)? = null, exportTrips: (Set<String>) -> Unit = {}, exporting: Boolean = false, saveCrew: ((CareData) -> CareData, () -> Unit) -> Unit, message: String = "", save: ((CareData) -> CareData) -> Unit) {
    var choosingExportDates by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var selecting by remember { mutableStateOf<TripDirection?>(null) }
    var ending by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val active = data.trips.singleOrNull { it.endedAt == null }
    var trackingDetailsExpanded by rememberSaveable(active?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CareTabs(listOf("GPS 운행 기록", "운행 내역"), selectedTab) { selectedTab = it }
        if (selectedTab == 0) {
            if (active == null) {
                TripStartTiles(enabled = !busy) { selecting = it }
                CrewPanel(data, busy, message, saveCrew)
                Text("모든 어르신의 기록 완료 또는 GPS로 1시간 무이동이 확인되면 자동 종료됩니다.", style = MaterialTheme.typography.bodySmall)
                if (tracking.phase == TrackingPhase.COMPLETE && tracking.tripId != null && data.trips.any { it.id == tracking.tripId && it.endedAt != null })
                    Text(tracking.message, style = MaterialTheme.typography.bodyMedium)
            } else {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${active.direction.label} 운행 진행 중", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("시작: ${tripTime(active.startedAt)} · 대상 ${active.targets.size}명")
                    CrewSummary(active.crew)
                    Text(tracking.message)
                    Text("기록 완료 ${active.arrivals.size}명 · 미기록 ${active.targets.size - active.arrivals.size}명")
                    if (tracking.pending.isNotEmpty()) Text("저장 대기 ${tracking.pending.size}명 · 저장 실패 시 자동으로 다시 시도합니다.")
                    if (tracking.phase != TrackingPhase.COMPLETE && tracking.phase != TrackingPhase.ENDING && (tracking.needsPermission || !tracking.serviceAlive))
                        Button(enabled = !busy && tracking.phase != TrackingPhase.STARTING, onClick = requestLocation) {
                            Text(if (tracking.needsPermission) "정확한 위치 권한 허용" else "GPS 감지 시작·재개")
                        }
                    if (!tracking.notificationsAllowed) {
                        Text("운행 알림이 꺼져 있습니다. 감지 상태를 알림에서 보려면 허용해 주세요.")
                        TextButton(enabled = !busy, onClick = requestNotifications) { Text("운행 알림 허용·설정") }
                    }
                    TextButton(enabled = !busy, onClick = { detailId = active.id }) { Text("대상·장소 확인") }
                    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), enabled = !busy, onClick = { ending = active.id }) { Text("운행 종료") }
                } }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = { trackingDetailsExpanded = !trackingDetailsExpanded },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(if (trackingDetailsExpanded) "운행 감지 안내 접기 ▲" else "운행 감지 안내 펼치기 ▼")
                    }
                    if (trackingDetailsExpanded) {
                        Text("장소 판정 반경 ${StopPolicy.RADIUS_METERS.toInt()}m · 현재 GPS 오차 ${tracking.accuracyMeters?.let { "약 ${it}m" } ?: if (!tracking.serviceAlive) "감지 중지" else "수신 대기"}")
                        Text("GPS 오차 허용 기준: ${StopPolicy.MAX_ACCURACY_METERS.toInt()}m 이하", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("운행 감지 서비스는 화면 꺼짐·앱 잠금 상태에서도 동작합니다. 감지가 중단되면 앱에서 재개해 주세요.")
                    }
                }
            }
        } else {
            val completedTrips = data.trips.filter { it.endedAt != null }.sortedByDescending { it.startedAt }
            val dayFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
            val headingFormat = SimpleDateFormat("yy.MM.dd일", Locale.KOREA)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
            val tripsByDate = completedTrips.groupBy { dayFormat.format(Date(it.startedAt)) }
            Button(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), enabled = !busy && !exporting && completedTrips.isNotEmpty(), onClick = { choosingExportDates = true }) {
                Text(if (exporting) "엑셀 저장 중…" else "엑셀로 저장 (.xlsx)")
            }
            Text("선택한 날짜의 종료 내역을 어르신별로 저장합니다. 담당자 조합별 시트의 맨 윗줄에 이름을 표시합니다. 같은 담당자의 등원 첫 도착·하원 마지막 도착을 합치며, 미기록은 빈칸입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (completedTrips.isEmpty()) Text("종료된 운행이 없습니다.")
            tripsByDate.values.forEach { trips ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        headingFormat.format(Date(trips.first().startedAt)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    trips.forEach { trip ->
                        Card(enabled = !busy, onClick = { detailId = trip.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${trip.direction.label} · ${timeFormat.format(Date(trip.startedAt))}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("기록 ${trip.arrivals.size}명 · 미기록 ${trip.targets.size - trip.arrivals.size}명 · 종료", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosingExportDates) ExportDatesDialog(data.trips, dismiss = { choosingExportDates = false }, export = { ids ->
        choosingExportDates = false
        exportTrips(ids)
    })
    selecting?.let { selectedDirection -> StartTripDialog(data, busy, selectedDirection, dismiss = { selecting = null }, start = { direction, choices ->
        save { it.startTrip(direction, choices, System.currentTimeMillis()) }
        // Selection stays available on write failure; successful save exposes the active trip.
    }) }
    LaunchedEffect(active?.id) { if (active != null) selecting = null }
    ending?.let { id -> AlertDialog(onDismissRequest = { if (!busy) ending = null },
        title = { Text("운행을 종료할까요?") }, text = {
            val endingTrip = data.trips.find { it.id == id }
            val completedIds = endingTrip?.arrivals.orEmpty().map { it.elderId }.toSet() + tracking.pending.map { it.elderId }
            val missing = endingTrip?.targets.orEmpty().filterNot { it.elderId in completedIds }
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("미기록 ${missing.size}명입니다.")
                missing.forEach { Text("${it.elderName} · ${it.place.name}") }
                Text("대기 중인 도착 기록과 종료 시각을 저장하고 GPS 감지를 멈춥니다.")
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { if (endTrip != null) endTrip(id) else save { it.appendDetectedArrivals(id, tracking.pending).endActiveTrip(id, System.currentTimeMillis()) } }) { Text("종료하고 저장") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { ending = null }) { Text("취소") } }) }
    LaunchedEffect(data.trips) { if (data.trips.any { it.id == ending && it.endedAt != null }) ending = null }
    val deleteTarget = deleting
    val deleteTrip = data.trips.find { it.id == deleteTarget?.first }
    LaunchedEffect(data.trips, deleting) {
        if (deleteTarget != null && (deleteTrip == null ||
                (deleteTarget.second != null && deleteTrip.targets.none { it.elderId == deleteTarget.second }))) {
            deleting = null
        }
    }
    if (deleteTarget != null && deleteTrip != null) {
        val elder = deleteTrip.targets.find { it.elderId == deleteTarget.second }
        AlertDialog(onDismissRequest = { if (!busy) deleting = null },
            title = { Text("운행 기록을 삭제할까요?") },
            text = { Text("${tripTime(deleteTrip.startedAt)} ${deleteTrip.direction.label}\n" +
                (if (elder == null) "이 운행의 전체 ${deleteTrip.targets.size}명 기록" else "${elder.elderName}님의 이 운행 기록") +
                "을 삭제합니다. 복구할 수 없습니다.\n등록된 어르신과 장소는 유지됩니다. 이미 내보낸 엑셀 파일은 삭제되지 않습니다.") },
            confirmButton = { TextButton(enabled = !busy && !exporting && deleteTrip.endedAt != null,
                onClick = { save { it.deleteTripHistory(deleteTarget.first, deleteTarget.second) } }) { Text("삭제") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("취소") } })
    }
    data.trips.find { it.id == detailId && deleting == null }?.let { trip ->
        TripDetailSheet(trip, busy, exporting, dismiss = { detailId = null },
            delete = { elderId -> deleting = trip.id to elderId })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTripDialog(data: CareData, busy: Boolean, direction: TripDirection, dismiss: () -> Unit,
    start: (TripDirection, Map<String, String>) -> Unit) {
    var choices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showPrivacy by remember { mutableStateOf(false) }
    val eligible = data.eligibleTripPlaces()
    LaunchedEffect(eligible) { choices = data.addTripMembers(choices, emptyList()) }
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !currentBusy })
    ModalBottomSheet(
        onDismissRequest = { if (!busy) dismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(
            securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
            shouldDismissOnBackPress = !busy
        ),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${direction.label} 운행 시작", style = MaterialTheme.typography.titleLarge)
                        Text("함께 이동할 어르신을 선택하세요", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !busy, onClick = dismiss) { Text("닫기") }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val crew = data.crewSnapshot()
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        color = lerp(MaterialTheme.colorScheme.surface, Color(0xFF87BCEB), 0.14f),
                        border = BorderStroke(1.dp, lerp(MaterialTheme.colorScheme.outlineVariant, Color(0xFF7CA4DA), 0.3f))) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("운행 담당자", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("운전자: ${crew.driverLabel()} · 동승자: ${crew.companionsLabel()}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (data.groups.isNotEmpty()) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                            color = lerp(MaterialTheme.colorScheme.surface, Color(0xFF739CE0), 0.16f),
                            border = BorderStroke(1.dp, lerp(MaterialTheme.colorScheme.outlineVariant, Color(0xFF7CA4DA), 0.3f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("그룹별 빠른 선택", style = MaterialTheme.typography.titleMedium)
                        Text("그룹을 누르면 구성원이 함께 선택됩니다.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            data.groups.sortedByKoreanName { it.name }.forEach { group ->
                                val available = group.elderIds.filter { !eligible[it].isNullOrEmpty() }
                                val selectedAll = available.isNotEmpty() && available.all { it in choices }
                                val skipped = group.elderIds.size - available.size
                                FilterChip(
                                    selected = selectedAll,
                                    enabled = !busy && available.isNotEmpty(),
                                    onClick = { choices = data.addTripMembers(choices, group.elderIds) },
                                    label = {
                                        Text("${group.name} · ${available.size}명" +
                                            (if (selectedAll) " 선택됨" else "") +
                                            (if (skipped > 0) " · ${skipped}명 제외" else ""), maxLines = 1)
                                    }
                                )
                            }
                        }
                        if (data.groups.any { group -> group.elderIds.any { eligible[it].isNullOrEmpty() } })
                            Text("좌표가 있는 연결 장소가 없는 어르신은 그룹 선택에서 제외됩니다.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        }
                    }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !busy && eligible.any { (id, places) -> places.isNotEmpty() && id !in choices },
                        onClick = {
                            choices = eligible.filterValues { it.isNotEmpty() }.mapValues { (id, places) ->
                                choices[id]?.takeIf { selected -> places.any { it.id == selected } } ?: places.first().id
                            }
                        }) { Text("전체선택") }
                    TextButton(enabled = !busy && choices.isNotEmpty(), onClick = { choices = emptyMap() }) {
                        Text("전체선택 해제")
                    }
                }
                    Text("선택 가능 ${eligible.count { it.value.isNotEmpty() }}명",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (data.elders.isEmpty()) Text("어르신을 등록한 뒤 좌표가 있는 장소와 연결해 주세요.")
                    val orderedElders = data.elders.sortedForTripSelection(choices.keys)
                    orderedElders.forEachIndexed { index, elder ->
                        val places = eligible.getValue(elder.id)
                        val selected = elder.id in choices
                        if (index == 0 || selected != (orderedElders[index - 1].id in choices)) {
                            Text(if (selected) "선택한 어르신 · ${choices.size}명"
                                else "선택하지 않은 어르신 · ${data.elders.size - choices.size}명",
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            color = lerp(MaterialTheme.colorScheme.surface, Color(0xFF6B9FE5), if (selected) 0.26f else 0.06f),
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                else lerp(MaterialTheme.colorScheme.outlineVariant, Color(0xFF7CA4DA), 0.2f))
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth().toggleable(value = selected,
                                    enabled = !busy && places.isNotEmpty(), role = Role.Checkbox,
                                    onValueChange = { checked -> choices = if (checked) choices + (elder.id to places.first().id) else choices - elder.id })
                                    .padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(enabled = !busy && places.isNotEmpty(), checked = selected, onCheckedChange = null)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${elder.name} 어르신", style = MaterialTheme.typography.titleMedium)
                                        if (places.size == 1) Text(places.single().name,
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (places.size != 1) Text(when {
                                            places.isEmpty() -> "좌표가 있는 연결 장소가 없어 선택할 수 없어요."
                                            selected -> "아래에서 운행 장소를 확인해 주세요."
                                            else -> "연결 장소 ${places.size}곳"
                                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (selected && places.size > 1) {
                                    Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp)
                                        .then(Modifier.selectableGroup())) {
                                        places.sortedByKoreanName { it.name }.forEach { place ->
                                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                .selectable(selected = choices[elder.id] == place.id, enabled = !busy,
                                                    role = Role.RadioButton, onClick = { choices = choices + (elder.id to place.id) }),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = choices[elder.id] == place.id, enabled = !busy, onClick = null)
                                                Text(place.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showPrivacy = !showPrivacy }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showPrivacy) "운행 기록 및 개인정보 안내 접기" else "운행 기록 및 개인정보 안내 펼치기")
                    }
                    if (showPrivacy) Text(
                        "시작 시 대상과 장소를 고정하여 암호화 저장합니다. 정확한 위치 권한을 허용하면 대상 장소의 도착을 감지하여 기록합니다. 화면을 꺼도 기록하며 운행 종료를 누르거나 모든 대상 기록이 완료되면 종료합니다. 정확한 GPS가 유지된 상태에서 50m 범위 안에 1시간 머물고 이동 속도가 감지되지 않아도 자동 종료합니다. GPS 수신 중단·정확도 저하·서비스 재시작 시 무이동 확인은 다시 시작합니다. GPS 도착 추정은 실제 승하차 확인이 아닙니다.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("선택 ${choices.size}명 · ${direction.label}", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = dismiss) { Text("취소") }
                    Button(modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                        enabled = !busy && choices.isNotEmpty(), onClick = { start(direction, choices) }) {
                        Text(if (busy) "저장 중…" else "${choices.size}명으로 ${direction.label} 시작")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
    }
}
