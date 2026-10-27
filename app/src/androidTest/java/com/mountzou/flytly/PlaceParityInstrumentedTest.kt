package com.mountzou.flytly

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mountzou.flytly.atlas.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PlaceParityInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "places-${UUID.randomUUID()}"
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val atlas by lazy { runBlocking { AtlasRepository.load(context) } }
    private val athens get() = atlas.citiesIn("GRC").first { it.name == "Athens" }
    private fun launch(records: Map<String, PlaceStatus> = emptyMap(), theme: String = "light", minimum: Int = 50_000) {
        LocalTravelFile(context, namespace).save(TravelSnapshot(records))
        context.getSharedPreferences("atlas_settings_$namespace", Context.MODE_PRIVATE).edit()
            .putString("appearance", theme).putInt("minimumPopulation", minimum).commit()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("ui_preview", "atlas").putExtra("ui_test_namespace", namespace))
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }
    private fun openCountry(name: String) {
        compose.onNodeWithContentDescription("Add a place").performClick()
        compose.onNodeWithTag("placeSearch").performTextReplacement(name)
        val result = hasClickAction() and SemanticsMatcher("country name is the primary row label") {
            it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.firstOrNull()?.text == name
        }
        compose.waitUntil(15_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(result).performClick()
        compose.onNodeWithTag("placeMapArtwork").assertExists()
        compose.waitUntil(10_000) {
            var visible = false
            scenario.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            !visible
        }
    }
    private fun openCollection(name: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText(name).performClick()
    }
    private fun back() { compose.onNodeWithContentDescription("Back").performClick() }
    private fun capture(name: String, art: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("art-$art", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        // The IME and compositor use real platform time, outside Compose's test
        // animation clock. Let their hide transition finish before image capture.
        android.os.SystemClock.sleep(1_000)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(350, 5_000)
        val bitmap = automation.takeScreenshot()
        val output = File(context.getExternalFilesDir(null), "place-parity").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun countrySuggestionsAndRankIgnoreTheBrowseFilterAndCitySearchFindsSmallerPlaces() {
        launch(minimum = 1_000_000)
        openCountry("Greece")
        val suggested = atlas.citiesIn("GRC").take(5)
        suggested.forEach { compose.onNodeWithTag("suggested-city-${it.id}").assertExists() }
        compose.onNodeWithText("View all cities").performScrollTo().performClick()
        val smaller = atlas.citiesIn("GRC").first { it.population < 1_000_000 }
        compose.onNodeWithTag("city-${smaller.id}").assertDoesNotExist()
        compose.onNodeWithTag("countryCitySearch").performTextReplacement(smaller.name)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("city-${smaller.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("city-${smaller.id}").performClick()
        compose.onNodeWithText("COUNTRY RANK").assertExists()
        compose.onNodeWithText(atlas.populationRank(smaller)!!).assertExists()
        compose.onNodeWithTag("openCountry").performClick()
        compose.onNodeWithText("Places in Greece").assertExists()
        back()
        compose.onNodeWithText(smaller.name).assertExists()
        back()
        compose.onNodeWithTag("countryCitySearch").assertTextContains(smaller.name)
    }
    @Test fun countryOtherStatusDoesNotMarkEveryCityAndSavedCitiesOutsideTopFiveRemainReachable() {
        val saved = atlas.citiesIn("GRC").last()
        launch(mapOf("country:GRC" to PlaceStatus.VISITED, "city:${saved.id}" to PlaceStatus.WISHLIST))
        openCountry("Greece")
        compose.onNodeWithTag("countryCitiesScroll").performScrollToNode(hasTestTag("saved-city-${saved.id}"))
        compose.onNodeWithText("YOUR CITIES").assertExists()
        compose.onNodeWithTag("saved-city-${saved.id}").performClick()
        compose.onNodeWithTag("status-visited").assertIsNotSelected()
        compose.onNodeWithTag("status-wishlist").assertIsSelected()
        compose.onNodeWithTag("openCountry").performClick()
        compose.onNodeWithTag("suggested-city-${athens.id}").performClick()
        compose.onNodeWithTag("status-visited").assertIsNotSelected()
    }
    @Test fun firstContinentSwipingDrilldownAndRecreationRetainSelection() {
        val tokyo = atlas.citiesIn("JPN").first()
        launch(mapOf("city:${tokyo.id}" to PlaceStatus.VISITED))
        openCollection("My travels")
        compose.onNodeWithTag("places-continent-Asia").assertIsSelected()
        compose.onNodeWithTag("placesContinentCarousel").performTouchInput {
            swipe(start = center.copy(x = width * .25f), end = center.copy(x = width * .75f), durationMillis = 500)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("places-continent-Europe").fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Selected) { false } } }
        compose.waitForIdle()
        compose.onNodeWithText("Your travels start here").assertExists()
        compose.onNodeWithTag("my-place-JPN").assertDoesNotExist()
        compose.onNodeWithTag("placesContinentCarousel").performTouchInput {
            swipe(start = center.copy(x = width * .75f), end = center.copy(x = width * .25f), durationMillis = 500)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("places-continent-Asia").fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Selected) { false } } }
        compose.onNodeWithTag("collectionContent").performScrollToNode(hasTestTag("my-place-JPN"))
        compose.onNodeWithTag("my-place-JPN").performScrollTo().performClick()
        back()
        compose.onNodeWithTag("places-continent-Asia").assertIsSelected()
        scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("places-continent-Asia").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("places-continent-Asia").assertIsSelected()
    }
    @Test fun wishlistCountsBothOtherAndListedCitiesAndMarksAnAlreadyVisitedCountry() {
        val saved = atlas.citiesIn("GRC").last()
        launch(mapOf("city:${athens.id}" to PlaceStatus.VISITED, "city:${saved.id}" to PlaceStatus.WISHLIST, "country:GRC" to PlaceStatus.WISHLIST))
        openCollection("Wishlist")
        compose.onNodeWithTag("wishlist-country-GRC").assertTextContains("2 cities saved")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Visited"))
        compose.onAllNodesWithText("1 country, 2 cities saved").onFirst().assertExists()
    }
    @Test fun undoRestoresWishlistAndAStaleUndoPreservesNewerRecords() {
        launch(mapOf("city:${athens.id}" to PlaceStatus.WISHLIST))
        openCountry("Greece")
        compose.onNodeWithTag("suggested-city-${athens.id}").performClick()
        compose.onNodeWithTag("status-visited").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("placeFeedback").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("status-wishlist").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Remove visited status?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("status-visited").assertIsSelected()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(10_000) { LocalTravelFile(context, namespace).load().status(athens, atlas) == PlaceStatus.WISHLIST }
        compose.onNodeWithTag("status-wishlist").assertIsSelected()
        compose.onNodeWithTag("status-wishlist").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("placeFeedback").fetchSemanticsNodes().isNotEmpty() }
        val file = LocalTravelFile(context, namespace)
        val newer = file.load().withStatus(null, "JPN", PlaceStatus.VISITED, atlas)
        file.save(newer)
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Unable to save").assertExists()
        assertEquals(newer, file.load())
    }

    @Test fun captureLightPages() = capturePages("light")
    @Test fun captureDarkPages() = capturePages("dark")
    private fun capturePages(theme: String) {
        val records = buildMap {
            listOf("GRC", "FRA", "DEU", "ITA", "ESP", "PRT", "AUT", "CHE").forEach { put("country:$it", PlaceStatus.VISITED) }
            listOf("BEL", "NLD", "NOR", "SWE", "FIN", "DNK", "IRL", "POL").forEach { put("country:$it", PlaceStatus.WISHLIST) }
        }
        launch(records, theme)
        openCollection("My travels"); capture("android-$theme-travels", "Europe"); back()
        compose.onNodeWithText("Wishlist").performClick(); capture("android-$theme-wishlist", "Europe"); back()
        compose.onNodeWithContentDescription("Close menu").performClick()
        openCountry("Greece"); capture("android-$theme-country", "GRC")
        compose.onNodeWithTag("suggested-city-${athens.id}").performClick(); capture("android-$theme-city", "GRC")
        compose.onNodeWithTag("status-wishlist").performClick()
        compose.waitUntil(10_000) { LocalTravelFile(context, namespace).load().status(athens, atlas) == PlaceStatus.WISHLIST }
        compose.mainClock.advanceTimeBy(4_500)
        compose.onNodeWithTag("placeFeedback").assertDoesNotExist()
        capture("android-$theme-city-saved", "GRC")
        compose.onNodeWithTag("status-visited").performClick()
        compose.waitUntil(10_000) { LocalTravelFile(context, namespace).load().status(athens, atlas) == PlaceStatus.VISITED }
        compose.mainClock.advanceTimeBy(4_500)
        compose.onNodeWithTag("placeFeedback").assertDoesNotExist()
        capture("android-$theme-city-visited", "GRC")
    }
}
