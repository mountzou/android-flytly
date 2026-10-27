package com.mountzou.flytly

import com.mountzou.flytly.preferences.*
import com.mountzou.flytly.account.preferenceFields
import org.junit.Assert.*
import org.junit.Test

class TravelPreferencesTest {
    @Test fun skippedFlagWinsOverCompletedExactlyAsOnIOS() {
        assertEquals(OnboardingStatus.SKIPPED, onboardingStatus(mapOf("completed" to true, "skipped" to true, "onboardingStatus" to "completed")))
        assertEquals(OnboardingStatus.COMPLETED, onboardingStatus(mapOf("completed" to true, "onboardingStatus" to "pending")))
    }
    @Test fun legacyStatusOnlyProfilesAreRecognized() {
        assertEquals(OnboardingStatus.SKIPPED, onboardingStatus(mapOf("onboardingStatus" to "skipped")))
        assertEquals(OnboardingStatus.COMPLETED, onboardingStatus(mapOf("onboardingStatus" to "completed")))
        assertEquals(OnboardingStatus.PENDING, onboardingStatus(mapOf("onboardingStatus" to "pending")))
        assertNull(onboardingStatus(mapOf("travelPace" to 2)))
    }
    @Test fun allEightQuestionsUseTheIOSKeysAndBalancedDefaults() {
        assertEquals(preferenceFields, preferenceQuestions.map { it.field })
        assertEquals(List(8) { 2 }, TravelPreferences.from(emptyMap()).levels)
    }
    @Test fun skipSavesTheCurrentDraftAndTheIOSCompletionFlags() {
        val values = TravelPreferences().choosing(0, 3).copy(mustHave = setOf("Future iOS option", "Direct flights or simple connections"))
        val payload = values.payload(skipped = true)
        assertEquals(3, payload["travelPace"])
        assertEquals("skipped", payload["onboardingStatus"])
        assertEquals(true, payload["completed"])
        assertEquals(true, payload["skipped"])
        assertEquals(listOf("Direct flights or simple connections", "Future iOS option"), payload["mustHave"])
    }
    @Test fun completedDraftHasAnExplicitStatusAndDoesNotMutateItsInitialSnapshot() {
        val before = TravelPreferences.from(mapOf("travelPace" to 1L, "mustAvoid" to listOf("Future option")))
        val edited = before.choosing(0, 3)
        assertEquals(1, before.levels[0]); assertEquals(3, edited.payload(false)["travelPace"])
        assertEquals("completed", edited.payload(false)["onboardingStatus"])
        assertEquals(false, edited.payload(false)["skipped"])
        assertEquals(setOf("Future option"), edited.mustAvoid)
    }
}
