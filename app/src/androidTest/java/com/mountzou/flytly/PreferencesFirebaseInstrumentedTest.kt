package com.mountzou.flytly

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import com.mountzou.flytly.preferences.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PreferencesFirebaseInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Client(val app: FirebaseApp, val auth: FirebaseAuth, val db: FirebaseFirestore)
    private fun client(port: Int = 8080): Client {
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-flytly-android")
            .setApplicationId("1:1234567890:android:preferences").setApiKey("fake-api-key-preferences").build(), "preferences-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        val db = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator("127.0.0.1", port)
        }
        return Client(app, auth, db)
    }
    private suspend fun login(client: Client, subject: String? = null): String {
        val sub = subject ?: "preferences-${UUID.randomUUID()}"
        val token = JSONObject().put("sub", sub).put("email", "$sub@example.test").put("email_verified", true).toString()
        return client.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!.uid
    }
    private fun document(client: Client, uid: String) = client.db.collection("users").document(uid).collection("profile").document("travelPreferences")
    private suspend fun ready(store: TravelPreferencesStore) = withTimeout(20_000) { store.state.first { it.hasLoaded && !it.isLoading } }
    private suspend fun finish(client: Client) { client.auth.signOut(); client.db.enableNetwork().await(); client.db.terminate().await() }

    @Test fun lateSignupMarkerPresentsImmediatelyAndSkipUsesAcknowledgedIOSPayload() = runBlocking<Unit> {
        val client = client(); val uid = login(client)
        val store = TravelPreferencesStore(context, uid, client.auth, client.db)
        try {
            store.registerNewAccount()
            val initial = ready(store)
            assertTrue(initial.shouldPresentOnboarding)
            assertEquals(List(8) { 2 }, initial.values.levels)
            val draft = initial.values.choosing(0, 3).copy(mustHave = setOf(mustHaveOptions[1]))
            assertTrue(store.save(draft, skipped = true))
            val saved = document(client, uid).get(Source.SERVER).await()
            assertEquals("skipped", saved.getString("onboardingStatus"))
            assertEquals(true, saved.getBoolean("completed")); assertEquals(true, saved.getBoolean("skipped"))
            assertEquals(3L, saved.getLong("travelPace"))
            assertEquals(listOf(mustHaveOptions[1]), saved.get("mustHave"))
            assertFalse(store.state.value.shouldPresentOnboarding)
            store.close()
            val restored = TravelPreferencesStore(context, uid, client.auth, client.db)
            try { assertEquals(OnboardingStatus.SKIPPED, ready(restored).status); assertFalse(restored.state.value.shouldPresentOnboarding) }
            finally { restored.close() }
        } finally { store.close(); finish(client) }
    }
    @Test fun missingLegacyProfileStartsPendingAndSavePersistsAllNineSteps() = runBlocking<Unit> {
        val client = client(); val uid = login(client)
        val store = TravelPreferencesStore(context, uid, client.auth, client.db)
        try {
            assertEquals(OnboardingStatus.PENDING, ready(store).status)
            val draft = TravelPreferences(listOf(1, 2, 3, 1, 2, 3, 1, 2), setOf(mustHaveOptions[2], "Future option"), setOf(mustAvoidOptions[0]))
            store.markDraftChanged()
            assertTrue(store.save(draft))
            assertEquals(OnboardingStatus.COMPLETED, store.state.value.status)
            val server = document(client, uid).get(Source.SERVER).await()
            assertEquals(draft, TravelPreferences.from(server.data!!))
            assertEquals(false, server.getBoolean("skipped")); assertEquals(true, server.getBoolean("completed"))
        } finally { store.close(); finish(client) }
    }
    @Test fun offlineSaveKeepsPendingAndDraftCanBeRetried() = runBlocking<Unit> {
        val client = client(); val subject = "offline-${UUID.randomUUID()}"; val uid = login(client, subject)
        var store = TravelPreferencesStore(context, uid, client.auth, client.db)
        val unreachable = client(port = 8088)
        try {
            ready(store); store.close(); assertEquals(uid, login(unreachable, subject))
            store = TravelPreferencesStore(context, uid, unreachable.auth, unreachable.db)
            ready(store)
            val draft = TravelPreferences().choosing(7, 3)
            val uiCaller = launch { store.save(draft) }
            withTimeout(5_000) { store.state.first { it.isSaving } }
            uiCaller.cancelAndJoin()
            // The account-owned operation must finish/fail even after its UI caller disappears.
            withTimeout(30_000) { store.state.first { !it.isSaving } }
            assertEquals(OnboardingStatus.PENDING, store.state.value.status)
            assertTrue(store.state.value.shouldPresentOnboarding)
            assertFalse(store.state.value.isSaving); assertNotNull(store.state.value.error)
            store.close()
            store = TravelPreferencesStore(context, uid, client.auth, client.db)
            ready(store)
            assertTrue(store.save(draft)); assertFalse(store.state.value.shouldPresentOnboarding)
            assertEquals(3L, document(client, uid).get(Source.SERVER).await().getLong("budgetStyle"))
        } finally { store.close(); finish(unreachable); finish(client) }
    }
    @Test fun failedEditAndFailedReloadNeverResetAnExistingCompletedProfile() = runBlocking<Unit> {
        val client = client(); val subject = "failed-edit-${UUID.randomUUID()}"; val uid = login(client, subject)
        var store = TravelPreferencesStore(context, uid, client.auth, client.db)
        val unreachable = client(port = 8088)
        try {
            ready(store); assertTrue(store.save(TravelPreferences().choosing(0, 3))); store.close()
            assertEquals(uid, login(unreachable, subject))
            store = TravelPreferencesStore(context, uid, unreachable.auth, unreachable.db)
            val failed = ready(store)
            assertTrue(failed.loadFailed); assertEquals(OnboardingStatus.COMPLETED, failed.status)
            assertFalse(failed.shouldPresentOnboarding); assertEquals(3, failed.values.levels[0])
            assertFalse(store.save(TravelPreferences().choosing(0, 1)))
            assertEquals(OnboardingStatus.COMPLETED, store.state.value.status)
            assertEquals(3, store.state.value.values.levels[0]); assertFalse(store.state.value.shouldPresentOnboarding)
        } finally { store.close(); finish(unreachable); finish(client) }
    }
    @Test fun cachedPendingIsUsableOfflineButAccountsNeverShareItsProgress() = runBlocking<Unit> {
        val client = client(); val a = login(client)
        var store = TravelPreferencesStore(context, a, client.auth, client.db)
        try {
            ready(store); store.close(); client.db.disableNetwork().await()
            store = TravelPreferencesStore(context, a, client.auth, client.db)
            val offline = ready(store)
            assertTrue(offline.shouldPresentOnboarding); assertFalse(offline.loadFailed)
            client.db.enableNetwork().await()
            assertTrue(store.save(TravelPreferences().choosing(0, 3)))
            store.close(); client.auth.signOut()
            val b = login(client)
            store = TravelPreferencesStore(context, b, client.auth, client.db)
            val other = ready(store)
            assertEquals(OnboardingStatus.PENDING, other.status); assertEquals(2, other.values.levels[0])
        } finally { store.close(); finish(client) }
    }
    @Test fun remoteCompletionReconcilesUntouchedDraftButCannotOverwriteAnEditedDraft() = runBlocking<Unit> {
        val client = client(); val uid = login(client)
        val store = TravelPreferencesStore(context, uid, client.auth, client.db)
        try {
            ready(store)
            store.markDraftChanged()
            document(client, uid).set(TravelPreferences().choosing(0, 3).payload(false)).await()
            delay(350)
            assertEquals(OnboardingStatus.PENDING, store.state.value.status)
            store.discardDraft()
            val remote = withTimeout(15_000) { store.state.first { it.status == OnboardingStatus.COMPLETED && !it.isLoading } }
            assertEquals(3, remote.values.levels[0]); assertFalse(remote.shouldPresentOnboarding)
            store.registerNewAccount(); delay(150)
            assertEquals(OnboardingStatus.COMPLETED, store.state.value.status)
        } finally { store.close(); finish(client) }
    }
}
