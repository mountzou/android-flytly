package com.mountzou.flytly

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mountzou.flytly.account.*
import com.mountzou.flytly.atlas.*
import com.mountzou.flytly.ui.theme.FlytlyTheme
import org.junit.*
import org.junit.Assert.*

class SettingsAccountAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun largeTextAccountConfirmationAndMissingDetailsRemainReachable() {
        var deletes = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                FlytlyTheme { AccountPage(AccountDetails(null,null,listOf("google.com","apple.com")),false,null,{}, { deletes++ },{}) }
            }
        }
        compose.onAllNodesWithText("Not provided").assertCountEquals(2)
        compose.onNodeWithText("Apple, Google").assertExists()
        compose.onNodeWithTag("deleteAccountButton").performScrollTo().performClick()
        compose.onNodeWithTag("confirmAccountDeletion").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick();assertEquals(0,deletes)
    }
    @Test fun largeTextSettingsKeepsLongPopulationChoicesAndAccountLinkUsable() {
        var population by mutableIntStateOf(50_000)
        var accountOpened = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                FlytlyTheme { SettingsPage(true,true,population,{},{},{population=it},{},"long.account@example.test",{accountOpened=true}) }
            }
        }
        compose.onNodeWithTag("minimumCityPopulation").performScrollTo().performClick()
        compose.onNodeWithTag("minimumCityPopulation-1000000").performScrollTo().performClick()
        assertEquals(1_000_000,population)
        compose.onNodeWithTag("accountSettingsButton").performScrollTo().performClick();assertTrue(accountOpened)
    }
}
