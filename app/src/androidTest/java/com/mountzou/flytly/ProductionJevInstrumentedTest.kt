package com.mountzou.flytly

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.mountzou.flytly.atlas.AtlasRepository
import com.mountzou.flytly.auth.AuthenticationSession
import com.mountzou.flytly.jev.JevTestOverrides
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

/** Explicit one-request opt-in. Consumes normal JEV quota; never edits profile/travel documents. */
class ProductionJevInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun signedInCityRequestsOneRealAnalysisUsingItsConfirmedProfile() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("production_jev_once") == "true")
        val context = instrumentation.targetContext
        val diagnostics = java.util.Collections.synchronizedList(mutableListOf<String>())
        JevTestOverrides.diagnostic = { diagnostics.add(it) }
        val auth = FirebaseAuth.getInstance();val uid = requireNotNull(auth.currentUser).uid
        val user = FirebaseFirestore.getInstance().collection("users").document(uid)
        suspend fun records() = user.collection("travelPlaces").get(Source.SERVER).await().documents.associate { it.id to it.data }
        val before = records()
        val profileBefore = user.collection("profile").document("travelPreferences").get(Source.SERVER).await().data
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            lateinit var session: AuthenticationSession
            scenario.onActivity { session = ViewModelProvider(it)[AuthenticationSession::class.java] }
            withTimeout(45_000) { while (session.state.value.repository?.preferences?.state?.value?.hasLoaded != true) delay(100) }
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("atlasReady").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Add a place").performClick();compose.onNodeWithTag("placeSearch").performTextReplacement("Greece")
            val country = hasClickAction() and SemanticsMatcher("primary Greece") { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.firstOrNull()?.text == "Greece" }
            compose.waitUntil(10_000) { compose.onAllNodes(country).fetchSemanticsNodes().isNotEmpty() };compose.onNode(country).performClick()
            val city = AtlasRepository.load(context).citiesIn("GRC").first { it.name == "Athens" }
            compose.onNodeWithTag("suggested-city-${city.id}").performClick()
            val started = android.os.SystemClock.elapsedRealtime()
            compose.onNodeWithTag("jevAnalysisButton").performClick()
            compose.waitUntil(80_000) { compose.onAllNodesWithTag("jevFitSheet").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Model usage limit").fetchSemanticsNodes().isNotEmpty() }
            val success = compose.onAllNodesWithTag("jevFitSheet").fetchSemanticsNodes().isNotEmpty()
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            val after = records();val profileAfter = user.collection("profile").document("travelPreferences").get(Source.SERVER).await().data
            val report = JSONObject().put("realCallableSucceeded",success).put("elapsedMs",elapsed).put("travelRecordsUnchanged",before == after).put("profileUnchanged",profileBefore == profileAfter).put("accountUnchanged",auth.currentUser?.uid == uid).put("stages",org.json.JSONArray(diagnostics.toList()))
            instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nFLYTLY_JEV_LIVE=$report\n") })
            val output = File(context.getExternalFilesDir(null),"jev-sharing").apply { mkdirs() }
            File(output,"live-result.json").writeText(report.toString(2))
            assertEquals(before,after);assertEquals(profileBefore,profileAfter);assertEquals(uid,auth.currentUser?.uid)
            assertTrue("The real callable did not return a destination fit; no automatic retry was made.",success)
        }
    }
    @After fun clearDiagnostics() { JevTestOverrides.diagnostic = null }
}
