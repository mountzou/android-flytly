package com.mountzou.flytly

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SignInInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun launch(theme: String = "light") {
        scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra("ui_test_namespace", UUID.randomUUID().toString())
            .putExtra("ui_preview_theme", theme))
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("signInScreen").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun close() { scenario?.close() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val directory = File(context.getExternalFilesDir(null), "login-parity").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun signedOutEntryDoesNotOpenTheAtlas() {
        launch()
        compose.onNodeWithTag("signInWithApple").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("signInWithGoogle").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("atlasMap").assertDoesNotExist()
        compose.onNodeWithText("Sign-in isn’t connected yet. This screen is a UI preview.").assertDoesNotExist()
        scenario!!.recreate()
        compose.onNodeWithTag("signInWithApple").assertIsDisplayed()
        compose.onNodeWithTag("atlasMap").assertDoesNotExist()
    }

    @Test fun loginMatchesSourceInLightAndDark() {
        for (theme in listOf("light", "dark")) {
            launch(theme)
            compose.onNodeWithTag("loginLogo").assertIsDisplayed()
            compose.onNodeWithText("Your world,\none place at a time.").assertIsDisplayed()
            compose.onNodeWithTag("signInWithApple").assertIsDisplayed()
            compose.onNodeWithTag("signInWithGoogle").assertIsDisplayed()
            compose.onNodeWithTag("loginPrivacy").assertIsDisplayed()
            compose.onNodeWithTag("appVersion").assertIsDisplayed()
            capture("android-login-$theme")
        }
    }

    @Test fun compactViewportCanScrollToEveryControl() {
        launch()
        compose.onNodeWithTag("signInWithApple").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("signInWithGoogle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("loginPrivacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("appVersion").performScrollTo().assertIsDisplayed()
        capture("android-login-compact-footer")
        compose.onNodeWithTag("loginLogo").performScrollTo().assertIsDisplayed()
    }
}
