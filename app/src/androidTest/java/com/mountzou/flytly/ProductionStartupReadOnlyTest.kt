package com.mountzou.flytly

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.google.firebase.auth.FirebaseAuth
import com.mountzou.flytly.atlas.AtlasRepository
import com.mountzou.flytly.auth.AuthenticationSession
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Opt-in normal startup check. Never changes providers, preferences or travel records. */
@RunWith(AndroidJUnit4::class)
class ProductionStartupReadOnlyTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun restoredAccountCanOpenItsAtlas() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("production_read_only") == "true")
        assertNotNull(FirebaseAuth.getInstance().currentUser)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var session: AuthenticationSession
            scenario.onActivity { session = ViewModelProvider(it)[AuthenticationSession::class.java] }
            withTimeoutOrNull(20_000) {
                while (session.state.value.repository?.data?.value?.loaded != true) delay(100)
            }
            val auth = session.state.value
            val data = auth.repository?.data?.value
            val preferences = auth.repository?.preferences?.state?.value
            val catalogLoaded = withTimeoutOrNull(20_000) { AtlasRepository.load(context); true } == true
            val report = JSONObject().put("authRestored", auth.restored).put("signedIn", auth.user != null)
                .put("repositoryPresent", auth.repository != null).put("accountLoaded", data?.loaded)
                .put("localError", data?.localError).put("preferencesLoaded", preferences?.hasLoaded)
                .put("preferencesLoading", preferences?.isLoading).put("preferencesStatus", preferences?.status?.name)
                .put("atlasCatalogLoaded", catalogLoaded)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nFLYTLY_STARTUP_AUDIT=$report\n") })
            assertTrue("Account must load during a normal restored-session startup.", data?.loaded == true)
            assertTrue(catalogLoaded)
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("atlasMap").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
