package com.mountzou.flytly

import com.mountzou.flytly.jev.*
import com.mountzou.flytly.preferences.*
import com.mountzou.flytly.atlas.*
import com.mountzou.flytly.sharing.TravelCardSnapshot
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DestinationFitTest {
    private val profile = PreferencesState(TravelPreferences(), OnboardingStatus.COMPLETED, hasLoaded = true)
    private fun response(score: Double = 1.6): Map<String, Any> = mapOf("model" to "jev-latest", "answers" to
        (fitQuestions.map { it.first } + "overall_match").associateWith { mapOf("score" to score, "confidence" to .9) })
    @Test fun requestWaitsForAConfirmedCompletedProfileAndDoesNotUseDefaultsOnFailures() = runBlocking {
        var requests = 0
        val client = DestinationFitClient { requests++; response() }
        for (state in listOf(profile.copy(hasLoaded = false), profile.copy(isLoading = true), profile.copy(loadFailed = true), profile.copy(isSaving = true),
            profile.copy(status = OnboardingStatus.SKIPPED), profile.copy(status = OnboardingStatus.PENDING))) {
            try { client.analyze("Athens",state); fail("Unconfirmed profile requested analysis") } catch (_: DestinationFitException) { }
        }
        assertEquals(0, requests)
        client.analyze("Athens",profile); assertEquals(1, requests)
    }
    @Test fun callablePayloadMatchesTheIOSContractAndKeepsTheCapturedLabels() = runBlocking {
        var payload: Map<String,String>? = null
        val values = TravelPreferences(listOf(1,3,1,3,1,3,1,3), setOf("Direct flights or simple connections", "Good access to healthcare"), setOf("Safety concerns"))
        val result = DestinationFitClient { payload = it; response(1.3) }.analyze("Athens",profile.copy(values = values))
        assertEquals(setOf("destination","profileDescription"),payload!!.keys)
        assertEquals("Athens",payload!!["destination"])
        assertEquals("""
            User travel preferences:
            travel pace: I like taking things slowly; nature access: mostly nature;
            culture and history: culture is not a priority; food and local cuisine: food is a main reason to visit;
            crowd tolerance: prefers quieter places; mobility without a car: I choose cities with excellent mobility;
            weather importance: weather does not affect my choice; budget approach: higher-cost destinations.
            Must have: Direct flights or simple connections, Good access to healthcare.
            Must avoid: Safety concerns.
        """.trimIndent(),payload!!["profileDescription"])
        assertEquals(65,result.matchScore)
        assertEquals(8,result.dimensions.size)
        assertEquals("Food is a main reason to visit",result.dimensions.first().selectedPreference)
        assertEquals("none specified",TravelPreferences().jevProfileDescription().substringAfter("Must have: ").substringBefore('.'))
    }
    @Test fun scoreThresholdsAndSegmentFillsMatchIOS() {
        assertEquals(listOf("Low","Mixed","Good","Excellent"),listOf(44,45,65,80).map(::fitLabel))
        assertEquals(listOf(1f,1f,1f,0f),(0..3).map { fitStepFill(80,it) })
        assertEquals(.5f,fitStepFill(90,3))
        assertEquals(0f,fitStepFill(-1,0)); assertEquals(1f,fitStepFill(101,3))
        assertEquals("A strong match for your travel preferences.",fitDescription(80))
        assertEquals("The destination partly fits this preference: Mostly urban.",fitDescription(45,"Mostly urban"))
    }
    @Test fun decoderRejectsMalformedAndNonFiniteScoresAndAcceptsMissingOptionalDimensions() {
        for (data in listOf(null, mapOf("answers" to emptyMap<String,Any>()), response(Double.NaN),response(-.1),response(2.1))) {
            assertThrows(DestinationFitException::class.java) { decodeDestinationFit(data,"Athens",TravelPreferences()) }
        }
        val minimal = mapOf("answers" to mapOf("overall_match" to mapOf("score" to 1)))
        assertEquals(50,decodeDestinationFit(minimal,"Athens",TravelPreferences()).matchScore)
        assertTrue(decodeDestinationFit(minimal,"Athens",TravelPreferences()).dimensions.isEmpty())
    }
    @Test fun cancellationPropagatesWithoutTurningIntoAModelLimit() = runBlocking {
        val client = DestinationFitClient { throw CancellationException("Screen closed") }
        try { client.analyze("Athens",profile); fail("Cancellation was swallowed") } catch (_: CancellationException) { }
    }
    @Test fun shareSnapshotIncludesAllContinentsAndOnlyDeduplicatedVisitedCountries() {
        val city = AtlasCity("athens","Athens","GRC",38.0,23.0,1e6,true,CityKind.URBAN_CENTER)
        val atlas = AtlasCatalog(listOf(AtlasCountry("GRC","Greece","Europe"),AtlasCountry("JPN","Japan","Asia"),AtlasCountry("USA","United States","North America")),listOf(city),emptyMap())
        val data = TravelSnapshot(mapOf("city:athens" to PlaceStatus.VISITED,"country:GRC" to PlaceStatus.VISITED,"country:JPN" to PlaceStatus.VISITED,"country:USA" to PlaceStatus.WISHLIST))
        val share = TravelCardSnapshot.capture(atlas,data)
        assertEquals(setOf("GRC","JPN"),share.countryIDs); assertEquals(2,share.continentCount)
        assertEquals("2 countries visited across 2 continents",share.summary)
        assertEquals(listOf("Greece","Japan"),share.countryNames)
    }
}
