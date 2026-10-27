package com.mountzou.flytly

import android.content.*
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.account.*
import com.mountzou.flytly.atlas.*
import com.mountzou.flytly.location.LocationSourceOverrides
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

open class SettingsAccountTestHost {
    @get:Rule val compose = createEmptyComposeRule()
    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val context get() = instrumentation.targetContext
    protected val namespace = "settings-${UUID.randomUUID()}"
    protected lateinit var scenario: ActivityScenario<MainActivity>
    protected var busy by mutableStateOf(false)
    protected var accountError by mutableStateOf<String?>(null)
    protected var deletes = 0
    protected var logouts = 0
    protected fun launch(theme: String = "light") {
        LocationSourceOverrides.preferenceNamespace = namespace
        context.getSharedPreferences("atlas_settings_$namespace",Context.MODE_PRIVATE).edit().putString("appearance",theme).commit()
        LocalTravelFile(context,namespace).save(TravelSnapshot(mapOf("country:GRC" to PlaceStatus.VISITED)))
        scenario = ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("ui_preview","atlas").putExtra("ui_test_namespace",namespace))
        scenario.onActivity { activity -> activity.setContent {
            AtlasScreen(activity,namespace,accountDetails = AccountDetails("Alex Traveler","alex@example.test",listOf("google.com")),
                accountBusy = busy,accountError = accountError,onSignOut = { logouts++ },onDeleteAccount = { deletes++;busy = true },onClearAccountError = { accountError = null })
        } }
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() }
    }
    protected fun settings() { compose.onNodeWithContentDescription("Open menu").performClick();compose.onNodeWithText("Settings").performClick() }
    protected fun account() {
        compose.onNodeWithTag("settingsScroll").performScrollToNode(hasTestTag("accountSettingsButton"))
        compose.onNodeWithTag("accountSettingsButton").performClick()
    }
    protected fun capture(name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(context.getExternalFilesDir(null),"settings-account").apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close();LocationSourceOverrides.preferenceNamespace = null }
}

class SettingsAccountInstrumentedTest : SettingsAccountTestHost() {
    @Test fun switchesAndPopulationPersistAndPrivacyOpensTheOriginalURL() {
        launch();settings()
        compose.onNodeWithTag("showCountryNames").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("showCityNames").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("minimumCityPopulation").performScrollTo().performClick()
        compose.onNodeWithTag("minimumCityPopulation-250000").performClick()
        compose.onNodeWithTag("populationChoiceSheet").assertDoesNotExist()
        compose.onNodeWithTag("minimumCityPopulation").assertTextContains("Over 250,000")
        compose.onNodeWithTag("minimumCityPopulation").performClick()
        compose.onNodeWithTag("minimumCityPopulation-250000").assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("https") },null,true)
        try {
            compose.onNodeWithTag("privacyPolicyButton").performScrollTo().performClick()
            compose.waitUntil(5_000) { monitor.hits == 1 }
        } finally { instrumentation.removeMonitor(monitor) }
        compose.onNodeWithText("Sync now").assertDoesNotExist();compose.onNodeWithText("Show world").assertDoesNotExist()
        val saved = context.getSharedPreferences("atlas_settings_$namespace",Context.MODE_PRIVATE)
        assertFalse(saved.getBoolean("countryNames",true));assertFalse(saved.getBoolean("cityNames",true));assertEquals(250_000,saved.getInt("minimumPopulation",0))
        scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("settingsView").fetchSemanticsNodes().isNotEmpty() }
        // Recreated MainActivity uses its normal preview root, not the test's setContent identity fixture.
        if (compose.onAllNodesWithTag("settingsView").fetchSemanticsNodes().isEmpty()) settings()
        compose.onNodeWithTag("showCountryNames").assertIsOff();compose.onNodeWithTag("showCityNames").assertIsOff()
        compose.onNodeWithTag("minimumCityPopulation").performScrollTo().assertTextContains("Over 250,000")
    }
    @Test fun accountShowsIdentityAndCancelledConfirmationChangesNothing() {
        launch();settings();account()
        listOf("NAME","EMAIL","LOGIN METHOD","Alex Traveler","alex@example.test","Google").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag("deleteAccountButton").performScrollTo().performClick()
        compose.onNodeWithText("Delete your account?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,deletes);assertEquals(0,logouts)
        assertEquals(PlaceStatus.VISITED,LocalTravelFile(context,namespace).load().records["country:GRC"])
        compose.onNodeWithTag("signOutButton").performClick();assertEquals(1,logouts)
    }
    @Test fun deletionBusyStateBlocksDuplicateRequestsAndBackAndErrorsAllowRetry() {
        launch();settings();account()
        compose.onNodeWithTag("deleteAccountButton").performScrollTo().performClick();compose.onNodeWithTag("confirmAccountDeletion").performClick()
        compose.onNodeWithTag("deleteAccountButton").assertIsNotEnabled();compose.onNodeWithTag("signOutButton").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist();compose.onNodeWithTag("accountBusy").performScrollTo().assertIsDisplayed()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() };compose.onNodeWithTag("accountView").assertExists();assertEquals(1,deletes)
        compose.runOnIdle { busy = false;accountError = DELETE_ACCOUNT_ERROR }
        compose.onNodeWithTag("accountError").performScrollTo().assertTextEquals(DELETE_ACCOUNT_ERROR)
        capture("android-account-error")
        compose.onNodeWithTag("deleteAccountButton").performScrollTo().performClick();compose.onNodeWithTag("accountError").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick();assertEquals(1,deletes)
    }
    @Test fun sourcesAndNestedBackReturnToSettings() {
        launch();settings()
        compose.onNodeWithTag("dataCreditsButton").performScrollTo().performClick()
        compose.onNodeWithText("Sources and licenses").assertExists();compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("settingsView").assertExists();account();compose.onNodeWithContentDescription("Back").performClick();compose.onNodeWithTag("settingsView").assertExists()
    }
    @Test fun captureLightReference() = captureReference("light")
    @Test fun captureDarkReference() = captureReference("dark")
    private fun captureReference(theme: String) {
        launch(theme);settings();capture("android-$theme-settings-top")
        compose.onNodeWithTag("minimumCityPopulation").performScrollTo().performClick();capture("android-$theme-population")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("dataCreditsButton").performScrollTo();capture("android-$theme-settings-bottom")
        account();capture("android-$theme-account")
        compose.onNodeWithTag("deleteAccountButton").performScrollTo().performClick();capture("android-$theme-delete-confirmation")
        compose.onNodeWithText("Cancel").performClick()
    }
}

class SettingsAccountWideInstrumentedTest : SettingsAccountTestHost() {
    @Test fun tabletSettingsUsesPairedCardsAndAccountRemainsReachable() {
        launch();settings()
        compose.onNodeWithTag("privacyPolicyButton").assertIsDisplayed()
        val privacy = compose.onNodeWithTag("privacyPolicyButton").fetchSemanticsNode().boundsInRoot
        val location = compose.onNodeWithTag("locationPermissionAction").fetchSemanticsNode().boundsInRoot
        assertTrue(privacy.left > location.right)
        capture("android-wide-settings");account();capture("android-wide-account")
        compose.onNodeWithTag("deleteAccountButton").performScrollTo().assertIsDisplayed()
    }
}
