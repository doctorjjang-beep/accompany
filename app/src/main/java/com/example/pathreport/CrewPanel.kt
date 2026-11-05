package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable
internal fun CrewSummary(crew: TripCrew?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("운전자: ${crew.driverLabel()}", style = MaterialTheme.typography.bodyMedium)
        Text("동승자: ${crew.companionsLabel()}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CrewPanel(data: CareData, busy: Boolean, message: String,
    save: ((CareData) -> CareData, () -> Unit) -> Unit) {
    var choosingDriver by remember { mutableStateOf<Boolean?>(null) }
    val crew = data.crewSnapshot()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("운행 담당자", style = MaterialTheme.typography.titleLarge)
            Text("변경하기 전까지 마지막 선택이 유지됩니다.", style = MaterialTheme.typography.bodySmall)
            listOf(true, false).forEach { driver ->
                TextButton(enabled = !busy, onClick = { choosingDriver = driver }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CareSymbol(if (driver) R.drawable.ic_car else R.drawable.ic_people)
                        Column(Modifier.weight(1f)) {
                            Text(if (driver) "운전자 · 1명" else "동승자 · 여러 명 선택 가능", style = MaterialTheme.typography.bodySmall)
                            Text(if (driver) crew.driver?.name ?: "운전자를 선택해 주세요"
                                else crew.companions.joinToString(" · ") { it.name }.ifEmpty { "동승자를 추가해 주세요" },
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Text("변경")
                    }
                }
            }
        }
    }
    choosingDriver?.let { driver -> key(driver) {
        var ids by remember { mutableStateOf(if (driver) listOfNotNull(data.crewSelection.driverId).toSet() else data.crewSelection.companionIds.toSet()) }
        var deleting by remember { mutableStateOf<StaffMember?>(null) }
        var name by rememberSaveable { mutableStateOf("") }
        val duplicate = data.staff.any { it.name.equals(name.trim(), true) }
        val available = data.staff.filter { driver || it.id != data.crewSelection.driverId }
        val validIds = ids.intersect(available.map { it.id }.toSet())
        Dialog(onDismissRequest = { if (!busy) choosingDriver = null }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
            deleting?.let { person ->
                AlertDialog(onDismissRequest = { if (!busy) deleting = null },
                    title = { Text("직원을 삭제할까요?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${person.name}님을 운전자·동승자 공용 명단에서 삭제합니다. 현재 선택에서도 해제됩니다. 과거 운행 기록과 엑셀에 표시될 당시 담당자 이름은 유지됩니다.")
                            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    confirmButton = { TextButton(enabled = !busy, onClick = {
                        save({ it.withoutStaff(person.id) }, { ids = ids - person.id; deleting = null })
                    }) { Text("직원 삭제", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("취소") } })
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.safeDrawingPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (driver) "운전자 선택" else "동승자 선택", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(enabled = !busy, onClick = { choosingDriver = null }) { Text("취소") }
                    }
                    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (driver) "운전할 직원 1명을 선택해 주세요." else "함께 탑승할 직원을 선택해 주세요. 운전자는 제외됩니다.")
                        available.sortedByKoreanName { it.name }.forEach { person ->
                            Surface(shape = MaterialTheme.shapes.medium, color = if (person.id in validIds) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).toggleable(person.id in validIds, enabled = !busy,
                                    role = if (driver) Role.RadioButton else Role.Checkbox,
                                    onValueChange = { checked -> ids = if (driver) setOf(person.id) else if (checked) ids + person.id else ids - person.id })
                                    .padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (driver) RadioButton(selected = person.id in validIds, onClick = null, enabled = !busy)
                                    else Checkbox(checked = person.id in validIds, onCheckedChange = null, enabled = !busy)
                                    Spacer(Modifier.width(12.dp))
                                    Text(person.name, Modifier.weight(1f))
                                }
                                TextButton(enabled = !busy, onClick = { deleting = person }) {
                                    Text("삭제", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                                }
                            }
                        }
                        if (available.isEmpty()) Text("선택할 직원이 없습니다. 아래에서 이름을 등록해 주세요.")
                        HorizontalDivider()
                        OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("등록할 직원 이름") },
                            singleLine = true, enabled = !busy, isError = duplicate, modifier = Modifier.fillMaxWidth())
                        if (duplicate) Text("같은 이름이 있습니다. 동명이인은 구분할 수 있는 이름으로 등록해 주세요.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = !busy && name.isNotBlank() && !duplicate, onClick = {
                            val person = StaffMember(name = name.trim())
                            save({ it.withStaff(person) }, { ids = if (driver) setOf(person.id) else ids + person.id; name = "" })
                        }, modifier = Modifier.fillMaxWidth()) { Text("이름 등록") }
                        TextButton(enabled = !busy, onClick = { ids = emptySet() }) { Text(if (driver) "선택 해제" else "동승자 없음") }
                    }
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                        save({ latest ->
                            val selected = validIds.filter { id -> latest.staff.any { it.id == id } }
                            val next = if (driver) {
                                val id = selected.singleOrNull()
                                latest.crewSelection.copy(driverId = id, companionIds = latest.crewSelection.companionIds.filterNot { it == id })
                            } else latest.crewSelection.copy(companionIds = selected.filterNot { it == latest.crewSelection.driverId })
                            latest.selectCrew(next)
                        }, { choosingDriver = null })
                    }) { Text(if (busy) "저장 중…" else "선택 완료 · ${validIds.size}명") }
                }
            }
        }
    } }
}
