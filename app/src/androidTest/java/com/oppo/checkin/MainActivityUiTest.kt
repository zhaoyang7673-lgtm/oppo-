package com.oppo.checkin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oppo.checkin.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardDisplaysChineseTitleAndNavigationTabs() {
        composeRule.onNodeWithText("OPPO 每日签到助手").assertIsDisplayed()
        composeRule.onNodeWithText("概览").assertIsDisplayed()
        composeRule.onNodeWithText("执行记录").assertIsDisplayed()
        composeRule.onNodeWithText("设置与指南").assertIsDisplayed()
    }

    @Test
    fun clickingAccessibilitySetupShowsPrivacyDisclosureFirst() {
        composeRule.onNodeWithText("前往设置").performClick()
        composeRule.onNodeWithText("无障碍服务隐私与用途声明").assertIsDisplayed()
        composeRule.onNodeWithText("同意并前往设置").assertIsDisplayed()
    }
}
