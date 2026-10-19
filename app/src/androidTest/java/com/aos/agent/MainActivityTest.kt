package com.aos.agent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun homeScreen_displaysBrandAndNavigation() {
        composeRule.onNodeWithText("AOSAgent").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("工程").assertIsDisplayed()
        composeRule.onNodeWithText("面板").assertIsDisplayed()
    }

    @Test fun homeScreen_defaultsToChineseAndShowsRealDevice() {
        composeRule.onNodeWithText("车辆与设备").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("型号").assertExists()
    }

    @Test fun homeScreen_exposesLanguageSwitch() {
        composeRule.onNodeWithContentDescription("切换界面语言").performScrollTo().assertIsDisplayed()
    }

    @Test fun engineerMode_canOpenAndReturn() {
        composeRule.onNodeWithText("工程").performClick()
        composeRule.onNodeWithText("工程师模式").assertIsDisplayed()
        composeRule.onNodeWithText("只读诊断").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("AOSAgent").assertIsDisplayed()
    }
}
