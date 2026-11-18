package com.example.pathreport

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/** Regression: process-state restoration must not resurrect unsaved personal input. */
class PrivateInputStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickRegistrationDoesNotRestoreUnsavedName() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { Column { QuickElderRegistration(false, "", save = { _, _ -> }) } }
        }
        compose.onNode(hasSetTextAction()).performTextInput("가상 입력")
        compose.onNodeWithText("현 위치 바로 등록").assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextEquals("어르신 이름", "")
        compose.onNodeWithText("현 위치 바로 등록").assertIsNotEnabled()
    }

    @Test fun elderEditRestoresCommittedNameInsteadOfUnsavedChanges() {
        val elder = Elder("test-elder", "가상 원본")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                ElderEditor(elder, CareData(elders = listOf(elder)), false, "",
                    dismiss = {}, save = { _, _ -> })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("가상 수정")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains("가상 원본")
        compose.onNodeWithText("가상 수정").assertDoesNotExist()
    }
}