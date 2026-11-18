package com.example.pathreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CareEditorSheet(title: String, busy: Boolean, dismiss: () -> Unit, dismissLabel: String = "취소",
    footer: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val currentBusy by rememberUpdatedState(busy)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !currentBusy })
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, sheetState = state,
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn,
            shouldDismissOnBackPress = !busy),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy, onClick = dismiss) { Text(dismissLabel) }
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
            }
        }
    }
}

@Composable
internal fun CareBlueSection(stronger: Boolean = false, whiteBackground: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = if (whiteBackground) Color.White else lerp(MaterialTheme.colorScheme.surface, Color(0xFF5F94DD), if (stronger) 0.14f else 0.06f),
        border = BorderStroke(1.dp, lerp(MaterialTheme.colorScheme.outlineVariant, Color(0xFF7CA4DA), 0.25f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
