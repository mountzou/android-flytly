package com.mountzou.flytly

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.*
import com.mountzou.flytly.account.*
import com.mountzou.flytly.atlas.PlaceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Runs only against the local demo project. Never changes the production Firebase session. */
@RunWith(AndroidJUnit4::class)
class FirebaseAccountInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun app(name: String): FirebaseApp {
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-flytly-android")
            .setApplicationId("1:1234567890:android:demo").setApiKey("fake-api-key").build(), name)
        FirebaseAuth.getInstance(app).useEmulator("127.0.0.1", 9099)
        FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator("127.0.0.1", 8080)
        }
        return app
    }
    private suspend fun login(auth: FirebaseAuth, sub: String, apple: Boolean = false): String {
        val token = JSONObject().put("sub", sub).put("email", "$sub@example.test").put("email_verified", true).toString()
        val credential = if (apple) OAuthProvider.newCredentialBuilder("apple.com").setIdToken(token).build()
            else GoogleAuthProvider.getCredential(token, null)
        return auth.signInWithCredential(credential).await().user!!.uid
    }
    private suspend fun ready(repo: AccountRepository) = withTimeout(30_000) { repo.data.first { it.loaded && it.message.startsWith("Synced") } }
    private suspend fun settled(repo: AccountRepository) = withTimeout(30_000) { repo.data.first { it.loaded && it.pendingCount == 0 && it.message.startsWith("Synced") } }
    private fun record(key: String, status: String) = mapOf("version" to 1, "kind" to key.substringBefore(':'),
        "placeID" to key.substringAfter(':'), "status" to status, "updatedAt" to FieldValue.serverTimestamp())

    @Test fun existingAccountLoadsIOSSchemaAndSyncsPlacesAndProfile() = runBlocking<Unit> {
        val app = app("existing-${UUID.randomUUID()}"); val auth = FirebaseAuth.getInstance(app); val db = FirebaseFirestore.getInstance(app)
        val uid = login(auth, "existing-${UUID.randomUUID()}")
        val user = db.collection("users").document(uid)
        user.collection("travelPlaces").document("country:GRC").set(record("country:GRC", "visited")).await()
        user.collection("profile").document("travelPreferences").set(mapOf("travelPace" to 3, "mustHave" to listOf("Future iOS option"), "onboardingStatus" to "completed")).await()
        val repo = AccountRepository(context, uid, auth, db)
        try {
            val initial = ready(repo)
            assertEquals(PlaceStatus.VISITED, initial.travels.records["country:GRC"])
            assertEquals(3, initial.profile["travelPace"])
            repo.setPlace("country:GRC", PlaceStatus.REMOVED)
            repo.setPlace("city:android-test", PlaceStatus.WISHLIST)
            repo.setPreference("foodImportance", 1)
            settled(repo)
            assertEquals("removed", user.collection("travelPlaces").document("country:GRC").get(Source.SERVER).await().getString("status"))
            val profile = user.collection("profile").document("travelPreferences").get(Source.SERVER).await()
            assertEquals(3L, profile.getLong("travelPace")); assertEquals(1L, profile.getLong("foodImportance"))
            assertEquals(listOf("Future iOS option"), profile.get("mustHave"))
            // A change from another client is reflected without relaunching.
            user.collection("travelPlaces").document("city:android-test").set(record("city:android-test", "removed")).await()
            withTimeout(30_000) { repo.data.first { it.travels.records["city:android-test"] == PlaceStatus.REMOVED } }
        } finally { repo.close(); auth.signOut(); db.terminate().await() }
    }

    @Test fun offlineOutboxSurvivesRepositoryRecreationAndReplaysOnce() = runBlocking<Unit> {
        val app = app("offline-${UUID.randomUUID()}"); val auth = FirebaseAuth.getInstance(app); val db = FirebaseFirestore.getInstance(app)
        val uid = login(auth, "offline-${UUID.randomUUID()}", apple = true)
        var repo = AccountRepository(context, uid, auth, db)
        try {
            ready(repo); db.disableNetwork().await()
            repo.setPlace("country:GRC", PlaceStatus.WISHLIST)
            assertEquals(1, repo.data.value.pendingCount)
            repo.close()
            repo = AccountRepository(context, uid, auth, db)
            withTimeout(10_000) { repo.data.first { it.loaded } }
            assertEquals(PlaceStatus.WISHLIST, repo.data.value.travels.records["country:GRC"])
            db.enableNetwork().await(); repo.retry(); settled(repo)
            val user = db.collection("users").document(uid)
            assertEquals("wishlist", user.collection("travelPlaces").document("country:GRC").get(Source.SERVER).await().getString("status"))
            val receipts = user.collection("travelSyncSources").get(Source.SERVER).await()
            assertEquals(1, receipts.size()); assertEquals(1L, receipts.documents.single().getLong("lastSequence"))
        } finally { repo.close(); auth.signOut(); db.terminate().await() }
    }

    @Test fun changingAccountsIsolatesDataAndRulesRejectOtherOwners() = runBlocking<Unit> {
        val app = app("isolation-${UUID.randomUUID()}"); val auth = FirebaseAuth.getInstance(app); val db = FirebaseFirestore.getInstance(app)
        val subject = "user-a-${UUID.randomUUID()}"; val a = login(auth, subject)
        val first = AccountRepository(context, a, auth, db)
        ready(first); first.setPlace("country:GRC", PlaceStatus.VISITED); settled(first)
        first.close(); auth.signOut()
        assertNull(auth.currentUser)
        val b = login(auth, "user-b-${UUID.randomUUID()}")
        val second = AccountRepository(context, b, auth, db)
        try {
            assertTrue(ready(second).travels.records.isEmpty())
            try {
                first.setPlace("country:USA", PlaceStatus.VISITED); fail("Closed account accepted an edit")
            } catch (_: IllegalStateException) { }
            try {
                db.collection("users").document(a).collection("travelPlaces").get(Source.SERVER).await()
                fail("Cross-account read was allowed")
            } catch (error: FirebaseFirestoreException) { assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code) }
            second.close(); auth.signOut()
            assertEquals(a, login(auth, subject))
            val restored = AccountRepository(context, a, auth, db)
            try { assertEquals(PlaceStatus.VISITED, ready(restored).travels.records["country:GRC"]) } finally { restored.close() }
        } finally { second.close(); auth.signOut(); db.terminate().await() }
    }
    @Test fun retriedUploadWithExistingReceiptDoesNotResurrectRemoteRemoval() = runBlocking<Unit> {
        val app = app("receipt-${UUID.randomUUID()}"); val auth = FirebaseAuth.getInstance(app); val db = FirebaseFirestore.getInstance(app)
        val uid = login(auth, "receipt-${UUID.randomUUID()}")
        val cache = AccountCache().edit("country:GRC", "visited")
        AccountCacheFile(context, uid, "demo-flytly-android").save(cache)
        val user = db.collection("users").document(uid)
        val receipt = user.collection("travelSyncSources").document(cache.sourceID)
        receipt.set(mapOf("version" to 1, "migrationComplete" to true, "lastSequence" to 0L, "updatedAt" to FieldValue.serverTimestamp())).await()
        receipt.update(mapOf("lastSequence" to 1L, "updatedAt" to FieldValue.serverTimestamp())).await()
        user.collection("travelPlaces").document("country:GRC").set(record("country:GRC", "removed")).await()
        val repo = AccountRepository(context, uid, auth, db)
        try {
            settled(repo)
            assertEquals(PlaceStatus.REMOVED, repo.data.value.travels.records["country:GRC"])
            assertEquals("removed", user.collection("travelPlaces").document("country:GRC").get(Source.SERVER).await().getString("status"))
        } finally { repo.close(); auth.signOut(); db.terminate().await() }
    }

    @Test fun concurrentCacheReadersCannotInterruptPendingWrites() = runBlocking<Unit> {
        val uid = "file-${UUID.randomUUID()}"
        val writerFile = AccountCacheFile(context, uid, "demo-flytly-android")
        val readerFile = AccountCacheFile(context, uid, "demo-flytly-android")
        var state = AccountCache().edit("country:GRC", "visited")
        writerFile.save(state)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val writer = launch(Dispatchers.IO) {
            try {
                repeat(40) { index ->
                    state = state.edit("country:GRC", if (index == 39) "removed" else "wishlist")
                    writerFile.save(state)
                }
            } finally { finished.set(true) }
        }
        val reader = launch(Dispatchers.IO) {
            do { assertTrue(readerFile.load().pending.isNotEmpty()) } while (!finished.get())
        }
        writer.join(); reader.join()
        val result = readerFile.load()
        assertEquals(41L, result.nextSequence)
        assertEquals(PlaceStatus.REMOVED, result.visiblePlaces().records["country:GRC"])
    }

}

/** Invoke twice in separate instrumentation processes, with -e session_phase save / restore. */
@RunWith(AndroidJUnit4::class)
class FirebaseSessionPersistenceInstrumentedTest {
    @Test fun sessionSurvivesAColdProcessStart() = runBlocking<Unit> {
        val phase = InstrumentationRegistry.getArguments().getString("session_phase")
        org.junit.Assume.assumeTrue(phase in listOf("save", "restore"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-flytly-android")
            .setApplicationId("1:1234567890:android:session").setApiKey("fake-api-key-session").build(), "cold-session-test")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        val prefs = context.getSharedPreferences("firebase-session-test", 0)
        if (phase == "save") {
            val token = JSONObject().put("sub", "cold-${UUID.randomUUID()}").put("email", "cold-session@example.test").put("email_verified", true).toString()
            val uid = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!.uid
            assertTrue(prefs.edit().putString("expectedUID", uid).commit())
            delay(1_000)
        } else {
            assertNotNull(auth.currentUser)
            assertEquals(prefs.getString("expectedUID", null), auth.currentUser?.uid)
            assertTrue(auth.currentUser!!.providerData.any { it.providerId == "google.com" })
            auth.currentUser!!.reload().await()
            auth.signOut(); assertNull(auth.currentUser)
        }
    }
}
