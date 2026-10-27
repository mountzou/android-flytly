package com.mountzou.flytly

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.preferences.*
import com.mountzou.flytly.ui.theme.FlytlyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferencesWizardInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun setup() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() } }
    @Test fun allNineStepsKeepDraftChoicesAndExplicitlySaveOnlyAtTheEnd() {
        setup()
        var saved: TravelPreferences? = null
        compose.setContent {
            var draft by remember { mutableStateOf(TravelPreferences()) }
            FlytlyTheme(false) { PreferencesWizardContent(draft, false, false, { draft = it }, { skip -> assertFalse(skip); saved = draft }) }
        }
        preferenceQuestions.forEachIndexed { index, question ->
            compose.onNodeWithTag("preferenceStepTitle").assertTextEquals(question.title)
            compose.onNodeWithTag("preferenceOption-${question.field}-2").assertIsSelected()
            compose.onNodeWithTag("preferenceOption-${question.field}-1").performScrollTo().performClick()
            assertNull(saved)
            compose.onNodeWithTag("nextPreferenceStep").performScrollTo().performClick()
        }
        compose.onNodeWithTag("preferenceStepTitle").assertTextEquals("Must have and avoid")
        compose.onNodeWithText("MUST HAVE").performClick()
        compose.onNodeWithTag("mustHave-${mustHaveOptions[0]}").performScrollTo().performClick()
        compose.onNodeWithText("MUST AVOID").performScrollTo().performClick()
        compose.onNodeWithTag("mustAvoid-${mustAvoidOptions[0]}").performScrollTo().performClick()
        compose.onNodeWithTag("savePreferences").performScrollTo().performClick()
        assertEquals(List(8) { 1 }, saved!!.levels)
        assertEquals(setOf(mustHaveOptions[0]), saved!!.mustHave)
        assertEquals(setOf(mustAvoidOptions[0]), saved!!.mustAvoid)
    }
    @Test fun skipUsesTheCurrentDraftAndSavingDisablesTheActions() {
        setup()
        val saving = mutableStateOf(false)
        var skipped: TravelPreferences? = null
        compose.setContent {
            var draft by remember { mutableStateOf(TravelPreferences()) }
            FlytlyTheme(false) { PreferencesWizardContent(draft, saving.value, false, { draft = it }, { skip -> assertTrue(skip); skipped = draft; saving.value = true }) }
        }
        compose.onNodeWithTag("preferenceOption-travelPace-3").performClick()
        compose.onNodeWithTag("skipPreferences").performClick()
        assertEquals(3, skipped!!.levels[0])
        compose.onNodeWithTag("skipPreferences").assertIsNotEnabled()
        compose.onNodeWithTag("nextPreferenceStep").assertIsNotEnabled()
    }
    @Test fun compactLargeTextKeepsNextAndAllOptionsReachable() {
        setup()
        compose.setContent {
            var draft by remember { mutableStateOf(TravelPreferences()) }
            CompositionLocalProvider(LocalDensity provides Density(2.625f, 1.6f)) {
                FlytlyTheme(false) { Box(Modifier.requiredSize(320.dp, 450.dp)) {
                    PreferencesWizardContent(draft, false, false, { draft = it }, {}, initialStep = 5, includeSystemInsets = false)
                } }
            }
        }
        compose.onNodeWithTag("preferenceOption-mobilityWithoutCar-3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nextPreferenceStep").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("preferenceStepTitle").performScrollTo().assertTextEquals("Weather")
    }
    @Test fun recreatedCompositionKeepsTheStepAndUnsavedDraft() {
        setup()
        val restoration = StateRestorationTester(compose)
        var skipped: TravelPreferences? = null
        restoration.setContent {
            var draft by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = PreferencesDraftSaver) { mutableStateOf(TravelPreferences()) }
            FlytlyTheme(false) { PreferencesWizardContent(draft, false, false, { draft = it }, { skipped = draft }) }
        }
        compose.onNodeWithTag("preferenceOption-travelPace-3").performClick()
        compose.onNodeWithTag("nextPreferenceStep").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("preferenceStepTitle").assertTextEquals("Nature")
        compose.onNodeWithTag("skipPreferences").performClick()
        assertEquals(3, skipped!!.levels[0])
    }
    @Test fun capturesTheSameStatesAsTheOriginalSwiftUIView() {
        setup()
        data class Case(val dark: Boolean, val name: String, val step: Int, val selected: Boolean = false, val expanded: String? = null)
        val scenario = mutableStateOf(Case(false, "first", 0))
        compose.setContent {
            val current by scenario
            key(current) {
                var draft by remember { mutableStateOf(TravelPreferences(mustHave = if (current.selected) setOf(mustHaveOptions[1]) else emptySet(), mustAvoid = if (current.selected) setOf(mustAvoidOptions[0]) else emptySet())) }
                FlytlyTheme(current.dark) { Box(Modifier.requiredSize(390.dp, 780.dp)) {
                    PreferencesWizardContent(draft, false, false, { draft = it }, {}, initialStep = current.step, initialExpanded = current.expanded, includeSystemInsets = false)
                } }
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "onboarding-parity").apply { mkdirs() }
        for (dark in listOf(false, true)) for ((name, step) in listOf("first" to 0, "mobility" to 5, "final" to 8, "expanded" to 8)) {
            compose.runOnIdle { scenario.value = Case(dark, name, step, name == "expanded", if (name == "expanded") "mustHave" else null) }
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("travelPreferencesOnboarding").captureToImage().asAndroidBitmap()
            File(directory, "android-${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
