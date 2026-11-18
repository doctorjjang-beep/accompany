package com.example.pathreport

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
internal fun QuickElderRegistration(busy: Boolean, message: String,
    save: ((CareData) -> CareData, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Personal input stays in memory; never include it in saved-instance state.
    var name by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            locating = false
            feedback = "정확한 위치 권한을 허용한 뒤 다시 눌러 주세요."
        } else {
            val enteredName = name.trim()
            scope.launch {
                try {
                    val location = currentCoordinates(context)
                    val age = SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000L
                    if (!location.hasAccuracy() || !location.accuracy.isFinite() ||
                        location.accuracy !in 0f..30f || age !in 0L..15_000L) {
                        feedback = "GPS 위치가 아직 정확하지 않습니다. 잠시 후 다시 눌러 주세요."
                    } else {
                        save({ it.registerElderAtLocation(enteredName, location.latitude, location.longitude) }, {
                            name = ""
                            feedback = "$enteredName · $enteredName 승하차장소 등록 완료"
                        })
                    }
                } catch (_: TimeoutCancellationException) {
                    feedback = "현재 위치를 찾지 못했습니다. GPS 수신이 잘 되는 곳에서 다시 시도해 주세요."
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) {
                    feedback = "위치를 확인하지 못했습니다. 정확한 위치 권한과 기기의 GPS 설정을 확인해 주세요."
                } finally { locating = false }
            }
        }
    }
    OutlinedTextField(value = name, onValueChange = { name = it; feedback = "" },
        enabled = !busy && !locating, label = { Text("어르신 이름") }, singleLine = true,
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && !locating && name.isNotBlank(), onClick = {
        feedback = ""
        locating = true
        permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(if (locating) "현재 위치 확인 중…" else if (busy) "저장 중…" else "현 위치 바로 등록")
    }
    Text("어르신의 이름을 입력하고 버튼을 누르면 현재위치로 어르신 승하차 장소가 자동 설정됩니다.",
        style = MaterialTheme.typography.bodySmall)
    if (feedback.isNotEmpty()) Text(feedback)
    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
}
