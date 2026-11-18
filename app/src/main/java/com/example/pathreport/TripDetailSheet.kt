package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TripDetailSheet(trip: Trip, busy: Boolean, exporting: Boolean,
    dismiss: () -> Unit, delete: (String?) -> Unit) {
    var menuId by remember(trip.id) { mutableStateOf<String?>(null) }
    val canDelete = !busy && !exporting && trip.endedAt != null
    val time = SimpleDateFormat("HH:mm", Locale.KOREA)
    val fullTime = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA)
    val day = SimpleDateFormat("yyyy.MM.dd E요일", Locale.KOREA)
    CareEditorSheet(title = "${trip.direction.label} 운행 상세", busy = busy,
        dismiss = dismiss, dismissLabel = "닫기", footer = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (trip.endedAt != null) TextButton(enabled = canDelete, onClick = { delete(null) }) {
                    Text("이 운행 전체 삭제", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error)
                }
                Button(enabled = !busy, onClick = dismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("확인") }
            }
        }) {
        Text("${day.format(Date(trip.startedAt))} · ${if (trip.endedAt == null) "운행 중" else "종료된 운행"}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CareBlueSection {
            Text("운전자: ${trip.crew.driverLabel()} · 동승자: ${trip.crew.companionsLabel()}",
                style = MaterialTheme.typography.bodySmall)
            Text("시작: ${fullTime.format(Date(trip.startedAt))}\n종료: ${trip.endedAt?.let { fullTime.format(Date(it)) } ?: "진행 중"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("어르신별 도착 시간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("기록 ${trip.arrivals.size}명 · 미기록 ${trip.targets.size - trip.arrivals.size}명",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                trip.targets.sortedByKoreanName { it.elderName }.forEachIndexed { index, target ->
                    key(target.elderId) {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val arrival = trip.arrivals.find { it.elderId == target.elderId }
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(target.elderName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                                    fontWeight = FontWeight.Bold)
                                Text(target.place.name, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(arrival?.let { time.format(Date(it.estimatedAt)) } ?: "미기록",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = if (arrival != null) 24.sp else 18.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (arrival != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (arrival != null) "도착 추정" else "도착 시간 없음",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (trip.endedAt != null) Box {
                                IconButton(enabled = canDelete, onClick = { menuId = target.elderId }) {
                                    Icon(painterResource(R.drawable.ic_more_vert), "${target.elderName} 기록 메뉴",
                                        modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = menuId == target.elderId, onDismissRequest = { menuId = null }) {
                                    DropdownMenuItem(text = { Text("이 기록 삭제", color = MaterialTheme.colorScheme.error) },
                                        enabled = canDelete, onClick = { menuId = null; delete(target.elderId) })
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("운행 시작 당시의 대상·장소입니다. GPS로 추정한 도착 시간이며 실제 승하차 확인은 아닙니다.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}