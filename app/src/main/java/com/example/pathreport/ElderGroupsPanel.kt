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

@Composable
internal fun ElderGroupsPanel(data: CareData, busy: Boolean, message: String,
    addElder: () -> Unit, editElder: (Elder) -> Unit, deleteElder: (Elder) -> Unit,
    save: ((CareData) -> CareData, () -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ElderGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var memberAction by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<ElderGroup?>(null) }
    val selected = data.groups.find { it.id == selectedId }
    CareTabs(listOf("어르신명단", "그룹관리"), tab) { tab = it; selectedId = null }
    if (tab == 0) {
        Button(onClick = addElder, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ 어르신 추가") }
        Text("전체 ${data.elders.size}명", style = MaterialTheme.typography.bodySmall)
        if (data.elders.isEmpty()) Text("등록된 어르신이 없습니다. 어르신을 추가해 주세요.")
        data.elders.sortedByKoreanName { it.name }.forEach { elder ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Text(data.groups.filter { elder.id in it.elderIds }.joinToString(" · ") { it.name }.ifEmpty { "소속 그룹 없음" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    } else if (selected == null) {
        Button(enabled = !busy, onClick = { creating = true; editing = ElderGroup(name = "") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ 그룹 추가") }
        Text("전체 ${data.groups.size}개 그룹", style = MaterialTheme.typography.bodySmall)
        if (data.groups.isEmpty()) Text("그룹을 만들고 명단에서 구성원을 추가해 주세요.")
        data.groups.sortedByKoreanName { it.name }.forEach { group ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CareSymbol(R.drawable.ic_car)
                        Text(group.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text("${group.elderIds.size}명")
                    }
                    Text(data.elders.filter { it.id in group.elderIds }.sortedByKoreanName { it.name }
                        .joinToString(" · ") { it.name }.ifEmpty { "아직 구성원이 없어요" }, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { selectedId = group.id }, modifier = Modifier.fillMaxWidth()) { Text("구성원 관리") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = !busy, onClick = { creating = false; editing = group }) { Text("이름 변경") }
                        TextButton(enabled = !busy, onClick = { deleting = group }) { Text("그룹 삭제", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    } else {
        androidx.activity.compose.BackHandler { selectedId = null }
        TextButton(onClick = { selectedId = null }) { Text("‹ 그룹 목록") }
        Text(selected.name, style = MaterialTheme.typography.titleLarge)
        Text("구성원 ${selected.elderIds.size}명", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { memberAction = true }, modifier = Modifier.weight(1f)) { Text("+ 어르신 추가") }
            OutlinedButton(enabled = !busy && selected.elderIds.isNotEmpty(), onClick = { memberAction = false }, modifier = Modifier.weight(1f)) { Text("그룹에서 빼기") }
        }
        if (selected.elderIds.isEmpty()) Text("위의 어르신 추가를 눌러 구성원을 등록하세요.")
        data.elders.filter { it.id in selected.elderIds }.sortedByKoreanName { it.name }.forEach { elder ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CareSymbol(R.drawable.ic_people)
                    Text(elder.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    editing?.let { draft -> key(draft.id) {
        var name by rememberSaveable { mutableStateOf(draft.name) }
        val duplicate = data.groups.any { it.id != draft.id && it.name.equals(name.trim(), true) }
        GroupSheet(if (creating) "그룹 추가" else "그룹 이름 변경", busy, message,
            dismiss = { editing = null }, canSave = name.isNotBlank() && !duplicate,
            saveLabel = if (creating) "만들고 구성원 추가" else "이름 저장",
            save = {
                val isNew = creating
                save({ latest ->
                    val base = if (isNew) draft else latest.groups.single { it.id == draft.id }
                    latest.saveGroup(base.copy(name = name))
                }, { editing = null; if (isNew) { selectedId = draft.id; memberAction = true } })
            }) {
            OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
                label = { Text("그룹 이름") }, placeholder = { Text("예: 9631 1차, 오전반") },
                singleLine = true, isError = duplicate, modifier = Modifier.fillMaxWidth())
            if (duplicate) Text("이미 사용 중인 그룹 이름입니다.", color = MaterialTheme.colorScheme.error)
        }
    } }
    if (selected != null) memberAction?.let { adding -> key(selected.id, adding) {
        var ids by remember { mutableStateOf(emptySet<String>()) }
        val candidates = data.elders.filter { (it.id in selected.elderIds) != adding }.sortedByKoreanName { it.name }
        val validIds = ids.intersect(candidates.map { it.id }.toSet())
        GroupSheet(if (adding) "어르신 추가" else "그룹에서 빼기", busy, message,
            dismiss = { memberAction = null }, canSave = validIds.isNotEmpty(),
            saveLabel = "${validIds.size}명 ${if (adding) "추가하기" else "그룹에서 빼기"}",
            save = { save({ latest -> latest.changeGroupMembers(selected.id, validIds, adding) }, { memberAction = null }) }) {
            Text(selected.name, style = MaterialTheme.typography.titleMedium)
            Text(if (adding) "명단에서 추가할 분을 선택하세요. 여러 그룹에 함께 소속할 수 있어요."
                else "이 그룹에서만 빠집니다. 어르신명단과 기존 운행 기록은 유지됩니다.")
            if (candidates.isEmpty()) Text(if (adding) "추가할 어르신이 없습니다. 어르신명단에서 먼저 등록해 주세요." else "선택할 구성원이 없습니다.")
            Row {
                TextButton(enabled = !busy && candidates.isNotEmpty(), onClick = { ids = candidates.map { it.id }.toSet() }) { Text("전체 선택") }
                TextButton(enabled = !busy && ids.isNotEmpty(), onClick = { ids = emptySet() }) { Text("선택 해제") }
            }
            candidates.forEach { elder -> MemberChoice(elder.name, elder.id in validIds, !busy) { checked ->
                ids = if (checked) ids + elder.id else ids - elder.id
            } }
        }
    } }
    deleting?.let { group ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("그룹을 삭제할까요?") },
            text = { Text("${group.name} 그룹만 삭제합니다. 어르신명단, 연결 장소와 기존 운행 기록은 유지됩니다.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                save({ it.copy(groups = it.groups.filterNot { item -> item.id == group.id }) }, { deleting = null })
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
    canSave: Boolean, save: () -> Unit, saveLabel: String = "저장", footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
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
                    Text(if (busy) "저장 중…" else saveLabel)
                }
            }
        }
    }
}
