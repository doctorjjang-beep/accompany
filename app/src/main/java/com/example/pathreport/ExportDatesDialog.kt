package com.example.pathreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportDatesDialog(trips: List<Trip>, dismiss: () -> Unit, export: (Set<String>) -> Unit) {
    val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
    val monthFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREA)
    val dayFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)
    val groups = trips.filter { it.endedAt != null }.sortedByDescending { it.startedAt }
        .groupBy { format.format(Date(it.startedAt)) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val chosen = selected.toSet().intersect(groups.keys)
    val chosenTrips = groups.filterKeys { it in chosen }.values.flatten()
    val allSelected = groups.isNotEmpty() && chosen.size == groups.size
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("엑셀로 저장", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = dismiss) { Text("취소") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "intro") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("어떤 날짜를 저장할까요?", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text("선택한 날짜의 등원·하원 기록을 하나의 엑셀 파일로 저장해요.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "selection") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${chosen.size}일 선택", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            TextButton(enabled = groups.isNotEmpty(), onClick = {
                                selected = if (allSelected) emptyList() else groups.keys.toList()
                            }) { Text(if (allSelected) "전체 해제" else "전체 선택") }
                        }
                    }
                    if (groups.isEmpty()) item(key = "empty") { Text("저장할 종료 운행이 없습니다.") }
                    groups.entries.groupBy { it.key.substring(0, 7) }.forEach { (month, entries) ->
                        item(key = "month-$month") {
                            Text(monthFormat.format(Date(entries.first().value.first().startedAt)),
                                Modifier.padding(top = 10.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        entries.forEach { (date, records) ->
                            item(key = date) {
                                val checked = date in chosen
                                Surface(shape = RoundedCornerShape(15.dp),
                                    color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 78.dp)
                                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                                            selected = (if (it) chosen + date else chosen - date).toList()
                                        }).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(dayFormat.format(Date(records.first().startedAt)),
                                                style = MaterialTheme.typography.titleMedium)
                                            Text("등원 ${records.count { it.direction == TripDirection.TO_CENTER }}건 · 하원 ${records.count { it.direction == TripDirection.FROM_CENTER }}건",
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (chosen.isEmpty()) "저장할 날짜를 선택해 주세요" else "${chosen.size}일 · 운행 ${chosenTrips.size}건",
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (chosen.isEmpty()) "날짜 행을 누르면 선택할 수 있어요." else "등원·하원 기록을 엑셀 파일 1개로 저장해요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(enabled = chosen.isNotEmpty(), onClick = {
                            export(chosenTrips.map { it.id }.toSet())
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(15.dp)) {
                            Text(if (chosen.isEmpty()) "날짜를 선택해 주세요" else "${chosen.size}일 기록 저장")
                        }
                    }
                }
            }
        }
    }
}
