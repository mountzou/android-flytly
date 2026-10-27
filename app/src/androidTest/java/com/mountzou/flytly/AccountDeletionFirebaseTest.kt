package com.mountzou.flytly

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.*
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import com.mountzou.flytly.account.*
import com.mountzou.flytly.atlas.*
import com.mountzou.flytly.preferences.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Secondary SDK instances, loopback emulators, disposable users. Never delete a production identity. */
class AccountDeletionFirebaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val project = "demo-flytly-lifecycle"
    private fun app(persistent: Boolean = false): FirebaseApp {
        val app = FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:1234567890:android:account-test").setApiKey("fake-api-key-${UUID.randomUUID()}").build(),"deletion-${UUID.randomUUID()}")
        FirebaseAuth.getInstance(app).useEmulator("127.0.0.1",19099)
        FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(
                if (persistent) PersistentCacheSettings.newBuilder().build() else MemoryCacheSettings.newBuilder().build()).build()
            useEmulator("127.0.0.1",18080)
        }
        return app
    }
    private fun credential(subject: String, apple: Boolean = false): AuthCredential {
        val token = JSONObject().put("sub",subject).put("email","$subject@example.test").put("email_verified",true).toString()
        return if (apple) OAuthProvider.newCredentialBuilder("apple.com").setIdToken(token).build() else GoogleAuthProvider.getCredential(token,null)
    }
    private suspend fun seed(db: FirebaseFirestore,uid: String) {
        db.collection("users").document(uid).collection("profile").document("travelPreferences").set(TravelPreferences().payload(false)).await()
        db.collection("users").document(uid).collection("travelPlaces").document("country:GRC").set(mapOf("version" to 1,"kind" to "country","placeID" to "GRC","status" to "visited","updatedAt" to FieldValue.serverTimestamp())).await()
    }
    @Test fun googleReauthenticationDeletesOnlyTheConfirmedAccountAndItsLocalData() = runBlocking<Unit> {
        val productionOwner = FirebaseAuth.getInstance().currentUser?.uid
        val app = app(persistent = true);val auth = FirebaseAuth.getInstance(app);val db = FirebaseFirestore.getInstance(app)
        val subject = "google-delete-${UUID.randomUUID()}";val originalCredential = credential(subject)
        val uid = auth.signInWithCredential(originalCredential).await().user!!.uid
        assertEquals(project,auth.app.options.projectId);seed(db,uid)
        val otherUid = "local-control-${UUID.randomUUID()}"
        val control = AccountCache(places = mapOf("country:JPN" to PlaceStatus.WISHLIST))
        AccountCacheFile(context,otherUid,project).save(control)
        PreferencesCacheFile(context,otherUid,project).save(TravelPreferences(listOf(3,3,3,3,3,3,3,3)).payload(false))
        val namespace = accountNamespace(uid)
        context.getSharedPreferences("atlas_settings_$namespace",Context.MODE_PRIVATE).edit().putString("appearance","dark").commit()
        context.getSharedPreferences("atlas_map_$namespace",Context.MODE_PRIVATE).edit().putString("latitude","38").commit()
        LocalTravelFile(context,namespace).save(TravelSnapshot(mapOf("country:GRC" to PlaceStatus.VISITED)))
        val repo = AccountRepository(context,uid,auth,db)
        try {
            withTimeout(30_000) { repo.data.first { it.loaded && it.message.startsWith("Synced") } }
            assertEquals(1,db.collection("users").document(uid).collection("travelPlaces").get(Source.CACHE).await().size())
            var confirmed = false
            val success = FirebaseAccountDeletion(context,auth,db).delete({ user -> user.reauthenticate(originalCredential).await();confirmed=true },{ assertTrue(confirmed);repo.closeAndJoin() })
            assertTrue(success);assertNull(auth.currentUser)
            val removed = AccountCacheFile(context,uid,project).load()
            assertTrue(removed.places.isEmpty());assertTrue(removed.profile.isEmpty());assertTrue(removed.pending.isEmpty())
            assertFalse(File(context.noBackupFilesDir,"${accountNamespace("$project:$uid")}.json").exists())
            assertNull(PreferencesCacheFile(context,uid,project).load())
            assertTrue(LocalTravelFile(context,namespace).load().records.isEmpty())
            assertTrue(context.getSharedPreferences("atlas_settings_$namespace",Context.MODE_PRIVATE).all.isEmpty())
            assertFalse(AccountStorageGuard.allows(namespace));assertTrue(AccountStorageGuard.allows(accountNamespace(otherUid)));assertTrue(AccountStorageGuard.allows(null))
            assertEquals(control,AccountCacheFile(context,otherUid,project).load())
            assertEquals(List(8) { 3 },TravelPreferences.from(PreferencesCacheFile(context,otherUid,project).load()!!).levels)
            assertNull(AccountDeletionJournal(context,project).read())
            // Reopen the SDK after terminate/clearPersistence: the on-disk cache must be empty.
            val reopened = FirebaseFirestore.getInstance(app)
            assertTrue(reopened.collection("users").document(uid).collection("travelPlaces").get(Source.CACHE).await().isEmpty)
            assertTrue(reopened.collection("users").document(uid).collection("profile").get(Source.CACHE).await().isEmpty)
            reopened.terminate().await()
            assertTrue("Production session must stay unchanged",productionOwner == FirebaseAuth.getInstance().currentUser?.uid)
            val file = File(context.getExternalFilesDir(null),"settings-account").apply { mkdirs() }
            // Only a demo-project UID is handed to the loopback Admin cleanup test.
            File(file,"deletion-fixture.json").writeText(JSONObject().put("project",project).put("deletedUID",uid).put("provider","google.com").toString())
        } finally { repo.close();app.delete() }
    }
    @Test fun appleCredentialReauthenticationAndDeletionUseTheSameFirebaseUser() = runBlocking<Unit> {
        val app = app();val auth = FirebaseAuth.getInstance(app);val db = FirebaseFirestore.getInstance(app)
        val token = credential("apple-delete-${UUID.randomUUID()}",apple = true)
        val uid = auth.signInWithCredential(token).await().user!!.uid
        seed(db,uid)
        try {
            var reauthenticatedUid: String? = null
            assertTrue(FirebaseAccountDeletion(context,auth,db).delete({ user -> user.reauthenticate(token).await();reauthenticatedUid=user.uid },{}))
            assertEquals(uid,reauthenticatedUid);assertNull(auth.currentUser)
        } finally { app.delete() }
    }
    @Test fun anotherGoogleIdentityIsRejectedWithoutRemovingProfileOrLocalRecords() = runBlocking<Unit> {
        val app = app();val auth = FirebaseAuth.getInstance(app);val db = FirebaseFirestore.getInstance(app)
        val uid = auth.signInWithCredential(credential("owner-${UUID.randomUUID()}")).await().user!!.uid
        seed(db,uid);val cached = AccountCache(places = mapOf("country:GRC" to PlaceStatus.VISITED));AccountCacheFile(context,uid,project).save(cached)
        try {
            var paused = false
            try { FirebaseAccountDeletion(context,auth,db).delete({ it.reauthenticate(credential("other-${UUID.randomUUID()}")).await() },{paused=true});fail("Other identity accepted") }
            catch (_: FirebaseAuthException) { }
            assertFalse(paused);assertEquals(uid,auth.currentUser?.uid);assertEquals(cached,AccountCacheFile(context,uid,project).load())
            assertTrue(db.collection("users").document(uid).collection("profile").document("travelPreferences").get(Source.SERVER).await().exists())
        } finally { auth.signOut();db.terminate().await();app.delete() }
    }
    @Test fun interruptedRequestDoesNotAutomaticallyDeleteAnExistingAccount() = runBlocking<Unit> {
        val app = app();val auth = FirebaseAuth.getInstance(app);val db = FirebaseFirestore.getInstance(app)
        val uid = auth.signInWithCredential(credential("recovery-${UUID.randomUUID()}")).await().user!!.uid
        seed(db,uid);val cached = AccountCache(places = mapOf("country:GRC" to PlaceStatus.VISITED));AccountCacheFile(context,uid,project).save(cached)
        val journal = AccountDeletionJournal(context,project)
        try {
            journal.save(uid,DeletionPhase.REQUESTED)
            assertEquals(DELETE_ACCOUNT_INTERRUPTED,FirebaseAccountDeletion(context,auth,db).recover(journal.read()!!))
            assertEquals(uid,auth.currentUser?.uid);assertEquals(cached,AccountCacheFile(context,uid,project).load());assertNull(journal.read())
            journal.save(uid,DeletionPhase.REAUTHENTICATING)
            assertEquals(DELETE_ACCOUNT_INTERRUPTED,FirebaseAccountDeletion(context,auth,db).recover(journal.read()!!))
            assertEquals(uid,auth.currentUser?.uid)
        } finally { journal.clear();auth.signOut();db.terminate().await();app.delete() }
    }
    @Test fun completedDeletionRecoveryClearsOnlyTheDeletedOwnersFiles() = runBlocking<Unit> {
        val app = app();val auth = FirebaseAuth.getInstance(app);val db = FirebaseFirestore.getInstance(app)
        val uid = "deleted-local-${UUID.randomUUID()}";val other = "retained-local-${UUID.randomUUID()}"
        AccountCacheFile(context,uid,project).save(AccountCache(places = mapOf("country:GRC" to PlaceStatus.VISITED)))
        val control = AccountCache(places = mapOf("country:JPN" to PlaceStatus.WISHLIST));AccountCacheFile(context,other,project).save(control)
        val journal = AccountDeletionJournal(context,project);journal.save(uid,DeletionPhase.DELETED)
        try {
            assertNull(FirebaseAccountDeletion(context,auth,db).recover(journal.read()!!))
            assertTrue(AccountCacheFile(context,uid,project).load().places.isEmpty());assertEquals(control,AccountCacheFile(context,other,project).load());assertNull(journal.read())
        } finally { app.delete() }
    }
}
