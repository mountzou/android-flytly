package com.mountzou.flytly

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.atlas.*
import com.mountzou.flytly.jev.*
import com.mountzou.flytly.preferences.*
import com.mountzou.flytly.sharing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

internal fun referenceFit(destination: String) = DestinationFit(destination,82,fitQuestions.mapIndexed { index,(id,title,question) ->
    FitDimension(id,title,listOf(90,75,50,85,40,65,80,55)[index],.9,preferenceQuestions[question].options[1],question)
},.9,"jev-latest")

class JevSharingInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val namespace = "jev-${UUID.randomUUID()}"
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val atlas by lazy { runBlocking { AtlasRepository.load(context) } }
    private val output get() = File(context.getExternalFilesDir(null),"jev-sharing").apply { mkdirs() }
    private val records = listOf("GRC","FRA","DEU","ITA","ESP","PRT","AUT","CHE").associate { "country:$it" to PlaceStatus.VISITED }
    private fun launch(theme: String = "light", data: Map<String,PlaceStatus> = records) {
        LocalTravelFile(context,namespace).save(TravelSnapshot(data))
        context.getSharedPreferences("atlas_settings_$namespace",Context.MODE_PRIVATE).edit().putString("appearance",theme).commit()
        scenario = ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("ui_preview","atlas").putExtra("ui_test_namespace",namespace))
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun close() {
        if (::scenario.isInitialized) scenario.close()
        JevTestOverrides.analyze = null; SharingTestOverrides.export = null
    }
    private fun city() {
        compose.onNodeWithContentDescription("Add a place").performClick()
        compose.onNodeWithTag("placeSearch").performTextReplacement("Greece")
        val result = hasClickAction() and SemanticsMatcher("primary label Greece") { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.firstOrNull()?.text == "Greece" }
        compose.waitUntil(15_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(result).performClick()
        compose.onNodeWithTag("jevAnalysisButton").assertDoesNotExist()
        val athens = atlas.citiesIn("GRC").first { it.name == "Athens" }
        compose.onNodeWithTag("suggested-city-${athens.id}").performClick()
    }
    private fun travels() {
        compose.onNodeWithContentDescription("Open menu").performClick(); compose.onNodeWithText("My travels").performClick()
    }
    private fun waitSheet() = compose.waitUntil(15_000) { compose.onAllNodesWithTag("jevFitSheet").fetchSemanticsNodes().isNotEmpty() }
    private fun waitCard() = compose.waitUntil(20_000) { compose.onAllNodesWithTag("travelShareCardPreview").fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun analysisDisablesDuplicateRequestsAndShowsAllNinePagesWithCapturedPreferences() {
        val response = CompletableDeferred<DestinationFit>(); var requests = 0
        JevTestOverrides.analyze = { requests++; response.await() }
        launch(); city()
        compose.onNodeWithTag("jevAnalysisButton").performClick()
        compose.onNodeWithTag("jevAnalysisButton").assertIsNotEnabled()
        assertEquals(1,requests)
        response.complete(referenceFit("Athens")); waitSheet(); capture("android-light-jev-overall")
        compose.onNodeWithTag("jevFitLabel").assertTextEquals("Excellent")
        compose.onNodeWithText("A strong match for your travel preferences.").assertExists()
        repeat(8) { index ->
            compose.onNodeWithTag("jevCarousel").performTouchInput { swipeLeft(durationMillis = 350) }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Carousel page ${index+2} of 9").fetchSemanticsNodes().isNotEmpty() }
            val dimension = referenceFit("Athens").dimensions[index]
            compose.onNodeWithTag("jevFitLabel").assertTextEquals(fitLabel(dimension.score))
            compose.onNodeWithText(fitDescription(dimension.score,dimension.selectedPreference)).assertExists()
            if (index == 0) capture("android-light-jev-food")
        }
        assertEquals(1,requests); assertEquals(TravelSnapshot(records),LocalTravelFile(context,namespace).load())
    }
    @Test fun modelErrorKeepsVisitsAndExplicitRetrySucceeds() {
        var requests = 0
        JevTestOverrides.analyze = { if (++requests == 1) throw DestinationFitException(FitFailure.LIMIT_REACHED) else referenceFit(it) }
        launch();city();compose.onNodeWithTag("jevAnalysisButton").performClick()
        compose.onNodeWithText("Model usage limit").assertExists()
        capture("android-jev-limit")
        compose.onNodeWithText("Our AI models have reached their maximum capacity. Try again in a few minutes.").assertExists()
        compose.onNodeWithText("OK").performClick();compose.onNodeWithTag("jevAnalysisButton").assertIsEnabled().performClick();waitSheet()
        assertEquals(2,requests); assertEquals(TravelSnapshot(records),LocalTravelFile(context,namespace).load())
    }
    @Test fun leavingCityCancelsPresentationAndDoesNotRequestAgain() {
        var cancelled = false; var requests = 0
        JevTestOverrides.analyze = { requests++;try { awaitCancellation() } finally { cancelled = true } }
        launch();city();compose.onNodeWithTag("jevAnalysisButton").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { cancelled };assertEquals(1,requests)
        compose.onNodeWithTag("jevFitSheet").assertDoesNotExist()
    }
    @Test fun darkAnalysisUsesTheSameResultAndArtwork() {
        JevTestOverrides.analyze = { referenceFit(it) }
        launch("dark");city();compose.onNodeWithTag("jevAnalysisButton").performClick();waitSheet();capture("android-dark-jev-overall")
        compose.onNodeWithTag("jevCarousel").performTouchInput { swipeLeft(durationMillis = 350) }
        compose.onNodeWithContentDescription("Carousel page 2 of 9").assertExists();capture("android-dark-jev-food")
    }
    @Test fun lightSharingShowsBothFormatsAndHandsReadablePNGToTheSystemChooser() {
        launch();travels();compose.onNodeWithTag("shareTravelsButton").performClick();waitCard();capture("android-light-share-card")
        compose.onNodeWithContentDescription("8 countries visited across 1 continent").assertExists()
        compose.onNodeWithText("Story").performClick();capture("android-light-share-story")
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER),null,true)
        try {
            compose.onNodeWithTag("shareInstagramStoryButton").assertTextContains("Share story image").performClick()
            compose.waitUntil(5_000) { monitor.hits == 1 }
            compose.onNodeWithTag("shareTravelCardButton").performClick()
            compose.waitUntil(5_000) { monitor.hits == 2 }
        } finally { instrumentation.removeMonitor(monitor) }
        compose.onNodeWithContentDescription("Close preview").performClick()
        compose.onNodeWithTag("myPlacesView").assertExists()
        assertEquals(TravelSnapshot(records),LocalTravelFile(context,namespace).load())
    }
    @Test fun darkSharingKeepsTheExportLightAndRetriesRenderingFailure() {
        var attempts = 0
        SharingTestOverrides.export = { context, snapshot -> if (++attempts == 1) error("Fixture rendering failure") else TravelCardRenderer.export(context,snapshot) }
        launch("dark");travels();compose.onNodeWithTag("shareTravelsButton").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Card unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Try again").performClick();waitCard();capture("android-dark-share-card")
        compose.onNodeWithText("Story").performClick();capture("android-dark-share-story")
        assertEquals(2,attempts)
    }
    @Test fun exportUsesAllContinentsAndNarrowReadOnlyURIsAndFixedResolutions() = runBlocking {
        val snapshot = TravelCardSnapshot.capture(atlas,TravelSnapshot(records + ("country:JPN" to PlaceStatus.VISITED)))
        assertEquals(9,snapshot.countryIDs.size);assertEquals(2,snapshot.continentCount)
        val export = TravelCardRenderer.export(context,snapshot)
        try {
            for ((file,w,h) in listOf(Triple(export.cardFile,1080,1350),Triple(export.storyFile,1080,1920))) {
                val bitmap = BitmapFactory.decodeFile(file.path)
                assertEquals(w,bitmap.width);assertEquals(h,bitmap.height);assertEquals(255,android.graphics.Color.alpha(bitmap.getPixel(0,0)));bitmap.recycle()
                val chooser = shareImageIntent(context,file)
                assertEquals(Intent.ACTION_CHOOSER,chooser.action)
                @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND,send.action);assertEquals("image/png",send.type)
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
                assertEquals(0,send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val uri = send.clipData!!.getItemAt(0).uri
                assertEquals("content",uri.scheme)
                assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.readBytes().contentEquals(file.readBytes()) })
            }
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context,"${context.packageName}.travelcards",File(context.filesDir,"account-private.json")) }
            assertFalse(instagramAvailable(context));assertNull(instagramAppID("000"));assertNull(instagramAppID("app-id"));assertEquals("1234",instagramAppID(" 1234 "))
            val paired = TravelCardRenderer.export(context,TravelCardSnapshot.capture(atlas,TravelSnapshot(records)))
            paired.cardFile.copyTo(File(output,"android-export-card.png"),overwrite = true)
            paired.storyFile.copyTo(File(output,"android-export-story.png"),overwrite = true)
            paired.card.recycle();paired.story.recycle()
        } finally { export.card.recycle();export.story.recycle() }
    }
    @Test fun emptyCollectionAndWishlistDoNotOfferTravelsSharing() {
        launch(data = emptyMap());travels();compose.onNodeWithTag("shareTravelsButton").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick();compose.onNodeWithText("Wishlist").performClick()
        compose.onNodeWithTag("shareTravelsButton").assertDoesNotExist()
    }
}
