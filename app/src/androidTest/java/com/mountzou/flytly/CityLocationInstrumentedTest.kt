package com.mountzou.flytly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.location.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.ZoneId
import java.util.UUID
import java.util.TimeZone

internal abstract class LocationUiTestHost {
    @get:Rule val compose = createEmptyComposeRule()
    protected val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "location-${UUID.randomUUID()}"
    protected lateinit var scenario: ActivityScenario<MainActivity>
    protected var calls = 0
    protected var cancelled = 0
    protected var result: LocationFix? = parisFix()
    protected var pending = false
    private lateinit var oldZone: TimeZone
    protected fun parisFix() = LocationFix(Coordinate(48.8566, 2.3522), System.currentTimeMillis(), 1000f)
    @Before fun prepare() {
        oldZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        LocationSourceOverrides.preferenceNamespace = namespace
        LocationSourceOverrides.factory = { object : DeviceLocationSource {
            override suspend fun current(): LocationFix? {
                calls++
                if (pending) try { awaitCancellation() } finally { cancelled++ }
                return result
            }
        } }
        LocationZoneCache.clearOrigin()
    }
    @After fun finish() {
        if (::scenario.isInitialized) scenario.close()
        LocationSourceOverrides.factory = null; LocationSourceOverrides.preferenceNamespace = null; LocationSourceOverrides.timeZone = null
        LocationZoneCache.clearOrigin(); TimeZone.setDefault(oldZone)
    }
    protected fun launch(denied: Boolean = false, dark: Boolean = false) {
        context.getSharedPreferences("location_access_$namespace", Context.MODE_PRIVATE).edit().putBoolean("requested", denied).commit()
        context.getSharedPreferences("atlas_settings_$namespace", Context.MODE_PRIVATE).edit().putString("appearance", if (dark) "dark" else "light").commit()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("ui_preview", "atlas").putExtra("ui_test_namespace", namespace))
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Home must not request location", 0, calls)
    }
    protected fun city(name: String = "Athens") {
        compose.onNodeWithContentDescription("Add a place").performClick()
        compose.onNodeWithTag("placeSearch").performTextReplacement(name)
        val result = hasClickAction() and SemanticsMatcher("primary result is $name") { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.firstOrNull()?.text == name }
        compose.waitUntil(15_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(result).onFirst().performClick()
    }
    protected fun awaitFacts() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("1 hour ahead").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cityDistance").performScrollTo().assertTextContains("≈ 2,100 km")
        compose.onNodeWithTag("cityTimeDifference").assertTextContains("Compared with your location")
    }
    protected fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(1000)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "location-parity").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) };bitmap.recycle()
    }
    protected fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    protected fun assertNoPlaceEdits() { assertTrue(com.mountzou.flytly.atlas.LocalTravelFile(context, namespace).load().records.isEmpty()) }
}

internal class CityLocationPromptInstrumentedTest : LocationUiTestHost() {
    @Test fun promptIsContextualAndSettingsShowsSetUp() {
        assertFalse(granted());launch();city()
        compose.onNodeWithTag("cityLocationPrompt").performScrollTo()
        compose.onNodeWithText("See your distance and time difference from Athens.").assertIsDisplayed()
        compose.onNodeWithTag("cityUseLocationButton").assertExists()
        assertEquals(0, calls);capture("android-light-prompt")
        compose.onNodeWithContentDescription("Back").performClick();compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open menu").performClick();compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("locationPermissionAction").performScrollTo()
        compose.onNodeWithText("Set up").assertExists()
    }
    @Test fun deniedPermissionOffersSystemSettingsAndDoesNotReadLocation() {
        assertFalse(granted());launch(denied = true, dark = true);city()
        compose.onNodeWithTag("cityLocationPrompt").performScrollTo()
        compose.onNodeWithTag("cityUseLocationButton").assertDoesNotExist()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { addDataScheme("package") }, null, true)
        try {
            compose.onNodeWithTag("cityLocationSettingsButton").performScrollTo().performClick()
            compose.waitUntil(5_000) { monitor.hits > 0 }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(0, calls);capture("android-dark-denied")
    }
}

internal class CityLocationGrantedInstrumentedTest : LocationUiTestHost() {
    @Test fun geographicOriginOverridesDeviceClockAndFactsSurviveForegroundRefresh() {
        assertTrue(granted());launch();city();awaitFacts();capture("android-light-facts")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitFacts();assertTrue(calls >= 2)
    }
    @Test fun failedLocationCanRetryWithoutChangingVisits() {
        assertTrue(granted());result = null;launch();city()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Location unavailable · Try again").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Location unavailable · Try again").onFirst().performScrollTo()
        capture("android-location-unavailable")
        result = parisFix()
        compose.onAllNodesWithText("Location unavailable · Try again").onFirst().performClick()
        awaitFacts();assertNoPlaceEdits()
    }
    @Test fun backgroundCancelsAnOutstandingFixAndForegroundRetries() {
        assertTrue(granted());pending = true;launch();city()
        compose.waitUntil(10_000) { calls > 0 }
        compose.onNodeWithText("Finding your location…").assertExists()
        scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(cancelled > 0)
        pending = false;scenario.moveToState(Lifecycle.State.RESUMED);awaitFacts()
    }
    @Test fun unresolvedTimeZoneHasRetryAndAnotherCityDoesNotReuseAthens() {
        assertTrue(granted())
        LocationSourceOverrides.timeZone = { null }
        launch(dark = true);city()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cityTimeDifference").performScrollTo()
        capture("android-timezone-unavailable")
        LocationSourceOverrides.timeZone = null
        compose.onNodeWithText("Try again").performClick();awaitFacts();capture("android-dark-facts")
        compose.onNodeWithTag("placeMapArtwork").performScrollTo().performClick()
        city("New York City")
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("behind", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cityTimeDifference").performScrollTo().assertTextContains("behind", substring = true)
    }
}

class OfflineTimeZoneInstrumentedTest {
    @Test fun realGeographicBoundariesResolveRepresentativeWorldRegions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reader = OfflineTimeZones.open(context)
        val examples = listOf(
            Triple(37.98, 23.72, "Europe/Athens"), Triple(48.8566, 2.3522, "Europe/Paris"),
            Triple(40.7128, -74.0060, "America/New_York"), Triple(51.5074, -.1278, "Europe/London"),
            Triple(27.7172, 85.3240, "Asia/Kathmandu"), Triple(22.5726, 88.3639, "Asia/Kolkata"),
            Triple(-34.9285, 138.6007, "Australia/Adelaide"), Triple(-43.95, -176.55, "Pacific/Chatham"),
            Triple(31.2304, 121.4737, "Asia/Shanghai"), Triple(35.6762, 139.6503, "Asia/Tokyo"),
            Triple(-33.8688, 151.2093, "Australia/Sydney"), Triple(21.3069, -157.8583, "Pacific/Honolulu"))
        val started = android.os.SystemClock.elapsedRealtime()
        examples.forEach { (lat, lon, zone) -> assertEquals("$lat,$lon", ZoneId.of(zone), reader.zoneAt(Coordinate(lat, lon))) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "\nOffline timezone lookup: ${examples.size} fixture points, ${android.os.SystemClock.elapsedRealtime() - started} ms\n") })
    }
}

class NativeLocationServiceInstrumentedTest {
    @Test fun foregroundApproximateServiceCompletesOrReportsUnavailable() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("ui_preview", "atlas").putExtra("ui_test_namespace", "native-location-${UUID.randomUUID()}")).use {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val controller = CityLocationController(AndroidDeviceLocationSource(context), scope)
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                withContext(Dispatchers.Main) { controller.authorization(LocationAuthorization.GRANTED); controller.start() }
                withTimeout(20_000) { while (controller.fix == null && !controller.failed) delay(50) }
                val fix = controller.fix
                if (fix != null) assertTrue(fix.isFresh(System.currentTimeMillis()))
                instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putString("stream", "\nNative approximate location service: ${if (fix == null) "unavailable" else "valid fresh fix"}, ${android.os.SystemClock.elapsedRealtime() - started} ms; no coordinates recorded\n")
                })
            } finally { scope.cancel() }
        }
    }
}

internal class NativeCityLocationFlowInstrumentedTest : LocationUiTestHost() {
    @Test fun emulatorPositionFlowsThroughNativeProviderAndOfflineZonesIntoTheCityPage() {
        assertTrue(granted())
        LocationSourceOverrides.factory = null
        launch();city();awaitFacts();assertNoPlaceEdits()
        capture("android-native-provider-facts")
    }
}
