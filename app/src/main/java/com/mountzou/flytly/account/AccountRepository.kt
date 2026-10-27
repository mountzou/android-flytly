package com.mountzou.flytly.account

import android.content.Context
import com.mountzou.flytly.preferences.TravelPreferencesStore
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import com.mountzou.flytly.atlas.PlaceStatus
import java.io.Closeable

/** One owner, one durable outbox, one synchronizer. All remote paths include that owner's UID. */
class AccountRepository(context: Context, val uid: String, private val auth: FirebaseAuth, private val db: FirebaseFirestore) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = AccountCacheFile(context, uid, db.app.options.projectId!!)
    private val mutex = Mutex()
    private var cache = AccountCache()
    private var active = true
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val mutableData = MutableStateFlow(AccountData())
    val data = mutableData.asStateFlow()
    private val user = db.collection("users").document(uid)
    private val places = user.collection("travelPlaces")
    private val profile = user.collection("profile").document("travelPreferences")
    private val listeners = mutableListOf<ListenerRegistration>()
    val preferences = TravelPreferencesStore(context, uid, auth, db, beforeServerAccess = { awaitOlderProfileChanges() })

    private suspend fun awaitOlderProfileChanges() {
        withTimeout(25_000) {
            val local = data.first { it.loaded || it.localError }
            check(!local.localError)
            while (true) {
                owner()
                if (mutex.withLock { cache.pending.keys.none { it.startsWith("profile:") } }) return@withTimeout
                retry()
                delay(100)
            }
        }
    }

    init {
        scope.launch {
            try {
                cache = withContext(Dispatchers.IO) { file.load() }
                owner(); publish("Connecting to your account…")
                com.mountzou.flytly.atlas.AtlasPerformance.mark("account.cacheReady")
                listeners += places.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                    if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) retry()
                }
                listeners += profile.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                    if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) retry()
                }
                synchronize()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (active) mutableData.value = AccountData(localError = true, message = "Your account cache could not be opened. It has been preserved. Please sign out and try again.")
            }
        }
    }
    private fun owner() { check(active && auth.currentUser?.uid == uid) { "Account changed" } }
    private fun publish(message: String) {
        owner()
        mutableData.value = AccountData(true, cache.visiblePlaces(), cache.visibleProfile(), cache.pending.size, message)
    }
    private suspend fun persist(next: AccountCache) {
        withContext(Dispatchers.IO) { file.save(next) }
        owner(); cache = next
    }
    suspend fun setPlace(key: String, status: PlaceStatus, expecting: com.mountzou.flytly.atlas.TravelSnapshot? = null): com.mountzou.flytly.atlas.TravelSnapshot {
        validateChange(key, status.value)
        val result = mutex.withLock {
            owner(); check(data.value.loaded)
            persist(cache.editPlace(key, status, expecting)); publish("Changes saved on this device. Syncing…")
            cache.visiblePlaces()
        }
        retry()
        return result
    }
    suspend fun setPreference(field: String, value: Any) = edit("profile:$field", value)
    private suspend fun edit(key: String, value: Any) {
        validateChange(key, value)
        mutex.withLock {
            owner(); check(data.value.loaded)
            persist(cache.edit(key, value)); publish("Changes saved on this device. Syncing…")
        }
        retry()
    }
    fun retry() { if (active) wake.trySend(Unit) }
    private suspend fun synchronize() {
        var delayMs = 2_000L
        while (scope.isActive && active) {
            try {
                owner()
                // A server fetch is authoritative; Firestore's cached snapshots never erase an outbox.
                refresh()
                val batch = mutex.withLock { cache.pending.values.sortedBy { it.sequence }.take(200) }
                if (batch.isNotEmpty()) {
                    val receipt = user.collection("travelSyncSources").document(cache.sourceID)
                    db.runTransaction { tx ->
                        owner()
                        val existing = tx.get(receipt)
                        if (!existing.exists()) tx.set(receipt, mapOf("version" to 1, "migrationComplete" to true,
                            "lastSequence" to 0L, "updatedAt" to FieldValue.serverTimestamp()))
                        else check(existing.getLong("version") == 1L && existing.getBoolean("migrationComplete") == true)
                    }.await()
                    owner()
                    val ack = db.runTransaction { tx ->
                        owner()
                        val source = tx.get(receipt)
                        check(source.getBoolean("migrationComplete") == true)
                        val previous = source.getLong("lastSequence") ?: error("Invalid sync receipt")
                        val fresh = batch.filter { it.sequence > previous }
                        val patch = fresh.filter { it.key.startsWith("profile:") }.associate { it.key.removePrefix("profile:") to it.value }
                        // Read all documents before any writes, including the profile when completing onboarding.
                        val previousProfile = if (patch.isNotEmpty()) tx.get(profile).data.orEmpty() else emptyMap()
                        fresh.filter { !it.key.startsWith("profile:") }.forEach { change ->
                            tx.set(places.document(change.key), mapOf("version" to 1, "kind" to change.key.substringBefore(':'),
                                "placeID" to change.key.substringAfter(':'), "status" to change.value, "updatedAt" to FieldValue.serverTimestamp()))
                        }
                        if (patch.isNotEmpty()) {
                            val onboarding = if (previousProfile.isEmpty() && "onboardingStatus" !in patch)
                                mapOf("onboardingStatus" to "pending") else emptyMap()
                            tx.set(profile, patch + onboarding + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                        }
                        val last = maxOf(previous, batch.maxOf { it.sequence })
                        tx.update(receipt, mapOf("lastSequence" to last, "updatedAt" to FieldValue.serverTimestamp()))
                        last
                    }.await()
                    owner()
                    // Read back after commit before removing pending intent; an old listener cannot roll it back.
                    val remote = fetch()
                    mutex.withLock {
                        owner(); persist(cache.acknowledge(ack).copy(places = remote.first, profile = remote.second)); publish("Synced with your Flytly account.")
                    }
                    if (cache.pending.isNotEmpty()) retry()
                } else mutex.withLock { publish("Synced with your Flytly account.") }
                delayMs = 2_000
                wake.receive()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!active || auth.currentUser?.uid != uid) return
                mutex.withLock { publish(if (cache.pending.isEmpty()) "Unable to sync. Showing places saved on this device. Retrying…"
                    else "Changes saved on this device. Waiting to sync…") }
                withTimeoutOrNull(delayMs) { wake.receive() }
                delayMs = (delayMs * 2).coerceAtMost(60_000)
            }
        }
    }
    private suspend fun fetch(): Pair<Map<String, PlaceStatus>, Map<String, Any>> {
        owner()
        val remote = places.get(Source.SERVER).await()
        val document = profile.get(Source.SERVER).await()
        owner()
        val parsed = remote.documents.associate { doc ->
            val raw = doc.data ?: error("Invalid place")
            validatePlaceKey(doc.id)
            require(raw.keys == setOf("version", "kind", "placeID", "status", "updatedAt") && doc.getLong("version") == 1L)
            require(doc.id == "${doc.getString("kind")}:${doc.getString("placeID")}" && raw["updatedAt"] is Timestamp)
            doc.id to PlaceStatus.entries.first { it.value == doc.getString("status") }
        }
        val fields = document.data.orEmpty().filterKeys { it in profileFields }.mapValues { (field, value) ->
            validateChange("profile:$field", value)
            if (field in preferenceFields) (value as Number).toInt() else value
        }
        return parsed to fields
    }
    private suspend fun refresh() {
        val remote = fetch()
        mutex.withLock { owner(); persist(cache.copy(places = remote.first, profile = remote.second)); publish("Syncing your account…") }
    }
    override fun close() {
        active = false
        preferences.close()
        listeners.forEach { it.remove() }; listeners.clear()
        scope.cancel(); wake.close()
        mutableData.value = AccountData()
    }
    internal suspend fun closeAndJoin() {
        close()
        scope.coroutineContext[Job]?.join()
        preferences.closeAndJoin()
    }
}
