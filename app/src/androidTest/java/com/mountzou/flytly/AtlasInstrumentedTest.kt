package com.mountzou.flytly

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import org.junit.Before
import org.junit.After
import java.util.UUID
import com.mountzou.flytly.atlas.LocalTravelFile
import com.mountzou.flytly.atlas.PlaceStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.atlas.AtlasRepository
import com.mountzou.flytly.atlas.CityKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.io.File
import android.graphics.Bitmap
import android.graphics.Color
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraPosition

@RunWith(AndroidJUnit4::class)
class AtlasInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val namespace = UUID.randomUUID().toString()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    @Before fun launchIsolatedApp() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra("ui_test_namespace", namespace).putExtra("ui_preview", "atlas"))
        scenario.onActivity { activity = it }
    }
    @After fun closeIsolatedApp() { scenario.close() }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun camera(): CameraPosition {
        fun find(view: View): MapView? {
            if (view is MapView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        var position: CameraPosition? = null
        compose.runOnIdle {
            val mapView = find(activity.findViewById(android.R.id.content))!!
            mapView.getMapAsync { position = it.cameraPosition }
        }
        return position!!
    }

    private fun waitForMap() {
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("258").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Preparing your atlas").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("atlasMap").assertExists()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        val directory = File(context.getExternalFilesDir(null), "ui-parity").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun waitForMapPalette(dark: Boolean) {
        var x = 0; var y = 0
        fun find(view: View): MapView? {
            if (view is MapView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        compose.runOnIdle {
            val view = find(activity.findViewById(android.R.id.content))!!
            view.getMapAsync { map ->
                val point = map.projection.toScreenLocation(LatLng(55.0, -16.0))
                val origin = IntArray(2); view.getLocationOnScreen(origin)
                x = origin[0] + point.x.toInt(); y = origin[1] + point.y.toInt()
            }
        }
        val expected = Color.parseColor(if (dark) "#111111" else "#FBFCFD")
        compose.waitUntil(15_000) {
            val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val color = screen.getPixel(x, y); screen.recycle()
            color == expected
        }
    }

    @Test fun fullScreenNavigationAndSourceArtworkRenderInBothThemes() {
        waitForMap()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Preferences").assertIsDisplayed()
        capture("android-menu-after")
        compose.onNodeWithText("Preferences").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("preference-TravelPaceWalking", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("android-preferences-after")
        compose.onNodeWithText("Travel pace").performClick()
        compose.onNodeWithText("I like a balanced pace").performClick()
        compose.onNodeWithText("I like a balanced pace").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Make the atlas yours.").assertIsDisplayed()
        capture("android-settings-after")
        compose.onNodeWithText("Sources and licenses").performScrollTo().performClick()
        compose.onNodeWithText("Natural Earth").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My travels").performClick()
        compose.onNodeWithText("Your travels start here").assertIsDisplayed()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("art-Europe", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("android-travels-empty-after")
        compose.onNodeWithTag("collectionAddPlaceButton").performClick()
        compose.onNodeWithText("POPULAR COUNTRIES").assertIsDisplayed()
        capture("android-add-place-after")
        compose.onNodeWithText("France").performClick()
        compose.onNodeWithText("SELECT A PLACE").assertIsDisplayed()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("art-FRA", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("flag-FRA", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("android-country-after")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Dark mode").performClick()
        capture("android-menu-dark-after")
        compose.onNodeWithContentDescription("Close menu").performClick()
        waitForMapPalette(true)
        capture("android-home-dark-after")
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithContentDescription("Light mode").performClick()
        compose.onNodeWithContentDescription("Close menu").performClick()
        waitForMapPalette(false)
        capture("android-home-after")
    }

    @Test fun bundledAtlasMatchesReviewedIOSCatalogAndHashes() {
        val atlas = runBlocking { AtlasRepository.load(context) }
        assertEquals(258, atlas.countries.size)
        assertEquals(26_288, atlas.cities.size)
        assertEquals(11_471, atlas.selectableCities.size)
        assertEquals(11_422, atlas.selectableCities.count { it.kind == CityKind.URBAN_CENTER })
        assertEquals(49, atlas.selectableCities.count { it.kind == CityKind.CAPITAL_FALLBACK })
        assertEquals(20, atlas.countries.count { atlas.citiesIn(it.id).isEmpty() })
        assertEquals(8, atlas.citiesIn("GRC").size)
        assertEquals(85, atlas.citiesIn("ESP").size)
        assertEquals("ghsl-r2024a-11345", atlas.cityForSavedID("1159151605")?.id)
        val manifest = JSONObject(context.assets.open("atlas/manifest.json").bufferedReader().use { it.readText() })
        assertEquals("692c178506a3f85addf84289c40001319732f827eabb39b20475f6c734ef09dd",
            manifest.getJSONObject("sourceSHA256").getString("cities.json"))
        for (section in listOf("sourceSHA256", "generatedSHA256")) {
            val hashes = manifest.getJSONObject(section)
            for (name in hashes.keys()) {
                val digest = MessageDigest.getInstance("SHA-256")
                context.assets.open("atlas/$name").use { stream ->
                    val buffer = ByteArray(16_384)
                    while (true) { val read = stream.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
                }
                assertEquals(name, hashes.getString(name), digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
        waitForMap()
    }

    @Test fun greekCitySearchFocusesTheActualUrbanCentre() {
        waitForMap()
        compose.onNodeWithContentDescription("Add a place").performClick()
        compose.onNodeWithTag("placeSearch").performTextReplacement("Αθήνα")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Athens").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Athens").performClick()
        compose.onNodeWithText("Athens").assertIsDisplayed()
        compose.onNodeWithTag("openCountry").assertTextEquals("GREECE")
        compose.onNodeWithTag("placeMapArtwork").performClick()
        val preferences = context.getSharedPreferences("atlas_map_$namespace", Context.MODE_PRIVATE)
        compose.waitUntil(10_000) { preferences.getString("zoom", null)?.toDoubleOrNull() == 7.0 }
        val athens = runBlocking { AtlasRepository.load(context) }.cityForSavedID("ghsl-r2024a-854")!!
        assertEquals(athens.latitude, preferences.getString("latitude", "0")!!.toDouble(), 0.001)
        assertEquals(athens.longitude, preferences.getString("longitude", "0")!!.toDouble(), 0.001)
    }

    @Test fun regionNavigationPreservesZoomAndSurvivesActivityRecreation() {
        waitForMap()
        val preferences = context.getSharedPreferences("atlas_map_$namespace", Context.MODE_PRIVATE)
        val previousZoom = preferences.getString("zoom", null)?.toDoubleOrNull()
        compose.onNodeWithTag("region-ASIA").performClick()
        compose.waitUntil(10_000) { preferences.getString("longitude", null)?.toDoubleOrNull()?.let { kotlin.math.abs(it - 105) < 0.001 } == true }
        val zoom = preferences.getString("zoom", "0")!!.toDouble()
        if (previousZoom != null) assertEquals(previousZoom, zoom, 0.001)
        scenario.recreate()
        scenario.onActivity { activity = it }
        waitForMap()
        val restored = camera()
        assertEquals(32.0, restored.target!!.latitude, 0.001)
        assertEquals(105.0, restored.target!!.longitude, 0.001)
        assertEquals(zoom, restored.zoom, 0.001)
        assertEquals(32.0, preferences.getString("latitude", "0")!!.toDouble(), 0.001)
        assertEquals(105.0, preferences.getString("longitude", "0")!!.toDouble(), 0.001)
        assertEquals(zoom, preferences.getString("zoom", "0")!!.toDouble(), 0.001)
    }
    @Test fun savedPlacesUpdatePersonalCountsAndSurviveRelaunch() {
        waitForMap()
        compose.onNodeWithContentDescription("Add a place").performClick()
        compose.onNodeWithTag("placeSearch").performTextReplacement("Αθήνα")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Athens").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Athens").performClick()
        compose.onNodeWithTag("status-visited").performClick()
        compose.waitUntil(10_000) { LocalTravelFile(context, namespace).load().records.values.contains(PlaceStatus.VISITED) }
        compose.onNodeWithTag("placeMapArtwork").performClick()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("My travels").performClick()
        compose.onNodeWithTag("my-place-GRC").assertTextContains("Greece")
        scenario.close()
        launchIsolatedApp()
        waitForMap()
        val visits = LocalTravelFile(context, namespace).load()
        val atlas = runBlocking { AtlasRepository.load(context) }
        assertEquals(setOf("GRC"), visits.countryIDs(PlaceStatus.VISITED, atlas))
        compose.onAllNodesWithText("1").assertCountEquals(2)
        compose.onNodeWithContentDescription("Add a place").performClick()
        compose.onNodeWithTag("placeSearch").performTextReplacement("Αθήνα")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Athens").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Athens").performClick()
        compose.onNodeWithTag("status-visited").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(10_000) { LocalTravelFile(context, namespace).load().records.values.none { it == PlaceStatus.VISITED } }
        compose.onNodeWithTag("status-wishlist").performClick()
        compose.waitUntil(10_000) { LocalTravelFile(context, namespace).load().records.values.all { it == PlaceStatus.WISHLIST } }
        assertTrue(LocalTravelFile(context, namespace).load().countryIDs(PlaceStatus.VISITED, atlas).isEmpty())
    }

}
