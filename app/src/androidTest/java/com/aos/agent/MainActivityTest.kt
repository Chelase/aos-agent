package com.aos.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysBrandAndEngineerButton() {
        composeRule.onNodeWithText("AOSAgent").assertIsDisplayed()
        composeRule.onNodeWithText("进入工程师模式").assertIsDisplayed()
    }

    @Test
    fun engineerMode_canOpenFromHome() {
        composeRule.onNodeWithText("进入工程师模式").performClick()
        composeRule.onNodeWithText("工程师模式").assertIsDisplayed()
        composeRule.onNodeWithText("返回").assertIsDisplayed()
    }
}
