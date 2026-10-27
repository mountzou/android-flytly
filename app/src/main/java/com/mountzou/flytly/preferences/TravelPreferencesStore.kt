package com.mountzou.flytly.preferences

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.mountzou.flytly.account.AccountCacheFile
import com.mountzou.flytly.account.profileFields
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/** Owns one account's confirmed profile; pending markers are never promoted by a local enqueue. */
class TravelPreferencesStore(context: Context, val uid: String, private val auth: FirebaseAuth, db: FirebaseFirestore, private val beforeServerAccess: suspend () -> Unit = {}) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = PreferencesCacheFile(context, uid, db.app.options.projectId!!)
    private val legacyFile = AccountCacheFile(context, uid, db.app.options.projectId!!)
    private val document = db.collection("users").document(uid).collection("profile").document("travelPreferences")
    private val mutableState = MutableStateFlow(PreferencesState(isLoading = true))
    val state = mutableState.asStateFlow()
    private val writes = Mutex()
    @Volatile private var active = true
    private var draftDirty = false
    @Volatile private var revision = 0L
    private var loading: Job? = null
    private var registration: Job? = null
    private var savingTask: Deferred<Boolean>? = null
    private var listener: ListenerRegistration? = null
    private var confirmed: Map<String, Any> = emptyMap()

    init {
        loading = scope.launch {
            val startupRevision = revision
            try {
                val cached = withContext(Dispatchers.IO) { file.load() ?: legacyFile.load().visibleProfile() }
                owner()
                if (revision == startupRevision) {
                    confirmed = cached
                    val status = onboardingStatus(cached)
                    mutableState.value = PreferencesState(TravelPreferences.from(cached), status,
                        hasLoaded = status == OnboardingStatus.PENDING, isLoading = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Preserve the cache; a server read can recover it without resetting the account. */ }
            registration?.join()
            loadRemote()
            if (!active || !isActive) return@launch
            listener = document.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!active || state.value.isSaving || draftDirty) return@addSnapshotListener
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) load()
            }
        }
    }
    private fun owner() { check(active && auth.currentUser?.uid == uid) { "Account changed" } }
    fun markDraftChanged() { if (state.value.isSaving) return; draftDirty = true; revision++ }
    fun discardDraft() { if (state.value.isSaving) return; draftDirty = false; revision++; clearError(); load() }
    fun clearError() { mutableState.value = state.value.copy(error = null) }
    fun load() {
        if (!active || state.value.isSaving || draftDirty || loading?.isActive == true) return
        loading = scope.launch { loadRemote() }
    }
    /** Wait for the confirmed profile, including an in-flight startup read, before a paid request. */
    internal suspend fun forDestinationAnalysis(): PreferencesState {
        if (!state.value.hasLoaded || state.value.loadFailed || state.value.isLoading) {
            load()
            try { withTimeout(30_000) { state.first { it.hasLoaded && !it.isLoading } } }
            catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                throw com.mountzou.flytly.jev.DestinationFitException(com.mountzou.flytly.jev.FitFailure.PROFILE_UNAVAILABLE)
            }
        }
        owner()
        return state.value
    }
    private suspend fun loadRemote() {
        if (state.value.isSaving || draftDirty) return
        val requestedRevision = revision
        mutableState.value = state.value.copy(isLoading = true, loadFailed = false, error = null)
        try {
            owner()
            beforeServerAccess()
            owner()
            val snapshot = document.get(Source.SERVER).await()
            owner()
            if (requestedRevision != revision || draftDirty || state.value.isSaving) return
            val fields = snapshot.data.orEmpty().filterKeys { it in profileFields }
            val status = onboardingStatus(fields) ?: OnboardingStatus.PENDING
            val normalized = if (onboardingStatus(fields) == null) fields + ("onboardingStatus" to "pending") else fields
            withContext(Dispatchers.IO) { file.save(normalized) }
            owner()
            if (requestedRevision != revision || draftDirty) return
            confirmed = normalized
            mutableState.value = PreferencesState(TravelPreferences.from(normalized), status, hasLoaded = true, saveGeneration = state.value.saveGeneration)
            if (onboardingStatus(fields) == null) queuePendingMarker()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (!active || requestedRevision != revision || draftDirty) return
            val pending = state.value.status == OnboardingStatus.PENDING
            mutableState.value = state.value.copy(hasLoaded = true, isLoading = false, loadFailed = !pending,
                error = if (pending) null else "Couldn’t load your travel preferences. Check your connection and try again.")
        } finally {
            if (active) mutableState.value = state.value.copy(isLoading = false)
        }
    }
    /** Signup can finish after the auth listener created this store. Never reset an existing completed profile. */
    fun registerNewAccount() {
        if (!active || state.value.isSaving || state.value.status in listOf(OnboardingStatus.COMPLETED, OnboardingStatus.SKIPPED)) return
        revision++
        registration = scope.launch {
            try {
                owner()
                val pending = confirmed + ("onboardingStatus" to "pending")
                withContext(Dispatchers.IO) { file.save(pending) }
                owner(); confirmed = pending
                mutableState.value = state.value.copy(status = OnboardingStatus.PENDING, hasLoaded = true,
                    isLoading = false, loadFailed = false, error = null)
                queuePendingMarker()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (active) mutableState.value = state.value.copy(isLoading = false, loadFailed = true,
                    error = "Couldn’t load your travel preferences. Check your connection and try again.")
            }
        }
    }

    private fun queuePendingMarker() {
        scope.launch {
            try {
                document.firestore.runTransaction { transaction ->
                    owner()
                    val snapshot = transaction.get(document)
                    // A late signup marker must not undo completion on another device.
                    if (onboardingStatus(snapshot.data.orEmpty()) == null) {
                        transaction.set(document, mapOf("onboardingStatus" to "pending"), SetOptions.merge())
                    }
                }.await()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Pending is durable locally; retry after the next successful read. */ }
        }
    }
    suspend fun save(values: TravelPreferences, skipped: Boolean = false): Boolean {
        owner()
        if (state.value.isSaving || savingTask?.isActive == true) return false
        // A rotation cancels the UI caller, not the account-owned save operation.
        val task = scope.async { performSave(values, skipped) }
        savingTask = task
        return task.await()
    }
    private suspend fun performSave(values: TravelPreferences, skipped: Boolean): Boolean {
        return writes.withLock {
            owner()
            revision++
            val saveRevision = revision
            mutableState.value = state.value.copy(isSaving = true, error = null)
            val payload = values.payload(skipped)
            try {
                withTimeout(25_000) {
                    beforeServerAccess()
                    owner()
                    document.firestore.runTransaction { transaction ->
                        owner(); check(revision == saveRevision)
                        // Transactions fail offline rather than silently enqueuing a completed marker.
                        transaction.get(document)
                        transaction.set(document, payload + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    }.await()
                }
                owner()
                check(saveRevision == revision)
                val saved = confirmed + payload
                withContext(Dispatchers.IO) { file.save(saved) }
                owner(); confirmed = saved; draftDirty = false
                mutableState.value = PreferencesState(values, if (skipped) OnboardingStatus.SKIPPED else OnboardingStatus.COMPLETED, hasLoaded = true, saveGeneration = state.value.saveGeneration + 1)
                true
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                if (active && saveRevision == revision) {
                    revision++
                    mutableState.value = state.value.copy(isSaving = false,
                        error = "Couldn’t save your travel preferences. Check your connection and try again.")
                }
                false
            } catch (_: Exception) {
                if (active && saveRevision == revision) {
                    revision++
                    mutableState.value = state.value.copy(isSaving = false,
                        error = "Couldn’t save your travel preferences. Check your connection and try again.")
                }
                false
            }
        }
    }
    override fun close() { active = false; revision++; listener?.remove(); scope.cancel(); mutableState.value = PreferencesState() }
    internal suspend fun closeAndJoin() { close();scope.coroutineContext[Job]?.join() }
}
