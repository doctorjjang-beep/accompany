package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/** Full-screen editor; persistence and stable identity remain owned by CareApp. */
@Composable
internal fun ElderEditor(elder: Elder, data: CareData, busy: Boolean, message: String,
    dismiss: () -> Unit, save: (String, Set<String>) -> Unit) {
    var name by rememberSaveable(elder.id) { mutableStateOf(elder.name) }
    var selectedIds by rememberSaveable(elder.id) {
        mutableStateOf(data.links.filter { it.elderId == elder.id }.map { it.placeId })
    }
    var attempted by rememberSaveable(elder.id) { mutableStateOf(false) }
    val editing = data.elders.any { it.id == elder.id }
    val ids = selectedIds.toSet().intersect(data.places.map { it.id }.toSet())
    val invalid = attempted && name.isBlank()
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnClickOutside = false, dismissOnBackPress = !busy,
        securePolicy = SecureFlagPolicy.SecureOn
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editing) "어르신 수정" else "어르신 추가",
                        Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(enabled = !busy, onClick = dismiss) { Text("취소") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (editing) "어르신 정보를 수정해 주세요" else "어르신 정보를 입력해 주세요",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("이름을 입력하고 승하차할 장소를 연결해요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                        label = { Text("어르신 이름 · 필수") }, placeholder = { Text("예: 김가람") },
                        isError = invalid,
                        supportingText = if (invalid) ({ Text("어르신 이름을 입력해 주세요.") }) else null,
                        shape = RoundedCornerShape(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("승하차 장소 · 선택", style = MaterialTheme.typography.titleMedium)
                        Text("여러 곳을 선택할 수 있어요. 나중에 연결해도 돼요.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (ids.isEmpty()) "선택한 장소 없음" else "선택한 장소 ${ids.size}곳",
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (data.places.isEmpty()) {
                        Text("등록된 장소가 없어요. 먼저 어르신을 등록한 뒤 승하차장소에서 연결할 수 있어요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        data.places.sortedByKoreanName { it.name }.forEach { place ->
                            val checked = place.id in ids
                            Surface(shape = RoundedCornerShape(14.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp,
                                    if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
                                    .toggleable(value = checked, enabled = !busy, role = Role.Checkbox,
                                        onValueChange = { selectedIds = if (it) (ids + place.id).toList() else (ids - place.id).toList() })
                                    .padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Checkbox(checked = checked, enabled = !busy, onCheckedChange = null)
                                    Column(Modifier.weight(1f)) {
                                        Text(place.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text("등록된 장소", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    Text("이름이 같아도 각각 다른 어르신으로 등록돼요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                        Text(if (name.isBlank()) "이름을 입력하면 저장할 수 있어요."
                            else if (ids.isEmpty()) "장소는 나중에 연결할 수 있어요."
                            else "승하차 장소 ${ids.size}곳을 연결해 저장해요.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(enabled = !busy, onClick = {
                            attempted = true
                            if (name.isNotBlank()) save(name.trim(), ids)
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(if (busy) "저장 중…" else if (editing) "변경사항 저장" else "어르신 등록")
                        }
                    }
                }
            }
        }
    }
}
