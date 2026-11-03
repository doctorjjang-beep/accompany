package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ElderGroupsPanel(data: CareData, busy: Boolean, message: String,
    editElder: (Elder) -> Unit, deleteElder: (Elder) -> Unit,
    save: ((CareData) -> CareData, () -> Unit) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ElderGroup?>(null) }
    var membership by remember { mutableStateOf<Elder?>(null) }
    var deleting by remember { mutableStateOf<ElderGroup?>(null) }
    val group = data.groups.find { it.id == filter }
    val unassigned = data.elders.filter { elder -> data.groups.none { elder.id in it.elderIds } }
    val shown = when {
        filter == "" -> unassigned
        group != null -> data.elders.filter { it.id in group.elderIds }
        else -> data.elders
    }
    Text("그룹별 빠른 선택", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter == null || (filter != "" && group == null),
            onClick = { filter = null }, label = { Text("전체 ${data.elders.size}") })
        data.groups.sortedByKoreanName { it.name }.forEach { item ->
            FilterChip(selected = filter == item.id, onClick = { filter = item.id },
                label = { Text("${item.name} ${item.elderIds.size}") })
        }
        FilterChip(selected = filter == "", onClick = { filter = "" }, label = { Text("미지정 ${unassigned.size}") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { editing = ElderGroup(name = "") }) { Text("+ 그룹 만들기") }
        if (group != null) TextButton(enabled = !busy, onClick = { editing = group }) { Text("그룹 편집") }
    }
    if (shown.isEmpty()) Text(if (data.elders.isEmpty()) "어르신을 추가하고 장소를 연결해 주세요." else "이 그룹에 등록된 어르신이 없습니다.")
    shown.sortedByKoreanName { it.name }.forEach { elder ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CareSymbol(R.drawable.ic_people)
                    Column(Modifier.weight(1f)) {
                        Text(elder.name, style = MaterialTheme.typography.titleLarge)
                        Text("연결 장소: " + data.links.filter { it.elderId == elder.id }.mapNotNull { link ->
                            data.places.find { it.id == link.placeId }?.name }.joinToString(", ").ifEmpty { "없음" },
                            style = MaterialTheme.typography.bodySmall)
                    }
                    PlaceCardActions(enabled = !busy, edit = { editElder(elder) }, delete = { deleteElder(elder) },
                        editDescription = "수정 · 장소 연결", deleteDescription = "어르신 삭제", compact = true, horizontal = true)
                }
                TextButton(enabled = !busy, onClick = { membership = elder }) {
                    Text("그룹: " + data.groups.filter { elder.id in it.elderIds }.joinToString(", ") { it.name }.ifEmpty { "미지정" } + " · 변경")
                }
            }
        }
    }
    editing?.let { draft -> key(draft.id) {
        var name by rememberSaveable { mutableStateOf(draft.name) }
        var ids by remember { mutableStateOf(draft.elderIds.toSet()) }
        val duplicate = data.groups.any { it.id != draft.id && it.name.equals(name.trim(), true) }
        GroupSheet("그룹 편집", busy, message, dismiss = { editing = null },
            canSave = name.isNotBlank() && !duplicate,
            save = { save({ it.saveGroup(draft.copy(name = name, elderIds = ids.toList())) }, { editing = null; filter = draft.id }) },
            footer = {
                if (data.groups.any { it.id == draft.id }) TextButton(enabled = !busy, onClick = { deleting = draft }) { Text("그룹 삭제") }
            }) {
            OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
                label = { Text("그룹 이름") }, placeholder = { Text("예: 1호차, 오전반") },
                singleLine = true, isError = duplicate, modifier = Modifier.fillMaxWidth())
            if (duplicate) Text("이미 사용 중인 그룹 이름입니다.", color = MaterialTheme.colorScheme.error)
            Text("구성원 ${ids.count { id -> data.elders.any { it.id == id } }}명 · 여러 그룹에 함께 소속할 수 있어요.")
            Row {
                TextButton(enabled = !busy, onClick = { ids = data.elders.map { it.id }.toSet() }) { Text("전체 선택") }
                TextButton(enabled = !busy, onClick = { ids = emptySet() }) { Text("선택 해제") }
            }
            if (data.elders.isEmpty()) Text("그룹을 먼저 저장하고, 어르신을 등록한 뒤 추가할 수 있어요.")
            data.elders.sortedByKoreanName { it.name }.forEach { elder ->
                MemberChoice(elder.name, elder.id in ids, !busy) { checked -> ids = if (checked) ids + elder.id else ids - elder.id }
            }
        }
    } }
    membership?.let { elder -> key(elder.id) {
        var ids by remember { mutableStateOf(data.groups.filter { elder.id in it.elderIds }.map { it.id }.toSet()) }
        GroupSheet("${elder.name} · 그룹 선택", busy, message, dismiss = { membership = null },
            canSave = true, save = { save({ it.setElderGroups(elder.id, ids) }, { membership = null }) }) {
            Text("여러 그룹을 선택할 수 있어요. 모두 해제하면 미지정으로 표시됩니다.")
            if (data.groups.isEmpty()) Text("명단 화면에서 ‘그룹 만들기’를 눌러 그룹을 먼저 만들어 주세요.")
            data.groups.sortedByKoreanName { it.name }.forEach { group ->
                MemberChoice(group.name, group.id in ids, !busy) { checked -> ids = if (checked) ids + group.id else ids - group.id }
            }
        }
    } }
    deleting?.let { group ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("그룹을 삭제할까요?") },
            text = { Text("${group.name} 그룹만 삭제합니다. 어르신, 연결 장소와 기존 운행 기록은 유지됩니다.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                save({ it.copy(groups = it.groups.filterNot { item -> item.id == group.id }) },
                    { deleting = null; editing = null; filter = null })
            }) { Text("그룹 삭제") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("취소") } })
    }
}

@Composable
private fun MemberChoice(name: String, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = change)
            .padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun GroupSheet(title: String, busy: Boolean, message: String, dismiss: () -> Unit,
    canSave: Boolean, save: () -> Unit, footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(enabled = !busy, onClick = dismiss) { Text("취소") }
                }
                if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                footer()
                Button(enabled = !busy && canSave, onClick = save, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "저장 중…" else "저장")
                }
            }
        }
    }
}
