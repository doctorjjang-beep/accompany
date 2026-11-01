package com.example.pathreport

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ElderEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun blankNameIsRejectedAndRegistrationWithoutPlacesWorks() {
        var saved: Pair<String, Set<String>>? = null
        compose.setContent { MaterialTheme {
            ElderEditor(Elder("test", ""), CareData(), false, "", {}, { n, ids -> saved = n to ids })
        } }
        compose.onNodeWithText("어르신 등록").performClick()
        compose.onNodeWithText("어르신 이름을 입력해 주세요.").assertExists()
        compose.runOnIdle { assertNull(saved) }
        compose.onNode(hasSetTextAction()).performTextInput("  가상 어르신  ")
        compose.onNodeWithText("어르신 등록").performClick()
        compose.runOnIdle { assertEquals("가상 어르신" to emptySet<String>(), saved) }
    }
    @Test fun editingRetainsLinksAndWholePlaceRowTogglesSelection() {
        val elder = Elder("test", "가상 어르신")
        val place = StopPlace("place", "가상 정류장", latitude = 37.5, longitude = 127.0)
        val data = CareData(listOf(place), listOf(elder), listOf(PlaceElder(place.id, elder.id)))
        var saved: Set<String>? = null
        compose.setContent { MaterialTheme { ElderEditor(elder, data, false, "", {}, { _, ids -> saved = ids }) } }
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText("가상 정류장").performScrollTo().performClick()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("변경사항 저장").performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), saved) }
    }
}
