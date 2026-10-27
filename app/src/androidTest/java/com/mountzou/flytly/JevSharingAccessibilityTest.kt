package com.mountzou.flytly

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mountzou.flytly.jev.DestinationFitSheet
import com.mountzou.flytly.sharing.*
import com.mountzou.flytly.ui.theme.FlytlyTheme
import org.junit.*

class JevSharingAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() { SharingTestOverrides.export = null }
    @Test fun largeTextKeepsFitExplanationAndCarouselReachable() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,1.8f)) {
                FlytlyTheme { DestinationFitSheet(referenceFit("Athens")) {} }
            }
        }
        compose.onNodeWithText("A strong match for your travel preferences.").assertExists()
        compose.onNodeWithTag("jevCarousel").performScrollTo().performTouchInput { swipeLeft(durationMillis = 350) }
        compose.onNodeWithText("The destination fits this preference well: I enjoy local cuisine.").assertExists()
        compose.onNodeWithTag("jevPageIndicator").performScrollTo().assertIsDisplayed()
    }
    @Test fun largeTextSharingKeepsBothActionsAndCloseReachable() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,1.8f)) {
                FlytlyTheme { TravelCardSheet(TravelCardSnapshot(setOf("GRC"),listOf("Greece"),1)) {} }
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("shareTravelCardButton").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shareTravelCardButton").assertIsDisplayed()
        compose.onNodeWithTag("shareInstagramStoryButton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close preview").assertIsDisplayed()
    }
    @Test fun shortLandscapeFitSheetScrollsToThePageIndicator() {
        compose.setContent {
            val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { screenHeightDp = 390;screenWidthDp = 844 }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                FlytlyTheme { DestinationFitSheet(referenceFit("Athens")) {} }
            }
        }
        compose.onNodeWithTag("jevPageIndicator").performScrollTo().assertIsDisplayed()
    }
}
