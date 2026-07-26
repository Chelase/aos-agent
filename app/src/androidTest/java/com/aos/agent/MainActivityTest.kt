package com.aos.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysBrandAndQuickActions() {
        composeRule.onNodeWithText("AOSAgent").assertIsDisplayed()
        composeRule.onNodeWithText("智能副驾").assertIsDisplayed()
        composeRule.onNodeWithText("工程师模式").assertIsDisplayed()
        composeRule.onNodeWithText("终端").assertIsDisplayed()
    }

    @Test
    fun homeScreen_defaultsToChinese() {
        // 无论车机系统语言为何，应用默认中文
        composeRule.onNodeWithText("快捷操作").assertIsDisplayed()
        composeRule.onNodeWithText("状态一览").assertIsDisplayed()
    }

    @Test
    fun homeScreen_exposesLanguageSwitch() {
        composeRule.onNodeWithText("语言").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切换界面语言").assertIsDisplayed()
    }

    @Test
    fun engineerMode_canOpenAndReturn() {
        composeRule.onNodeWithText("工程师模式").performClick()
        composeRule.onNodeWithText("只读诊断").assertIsDisplayed()
        composeRule.onNodeWithText("厂商").assertIsDisplayed()

        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("快捷操作").assertIsDisplayed()
    }
}
