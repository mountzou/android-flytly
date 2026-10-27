package com.mountzou.flytly.account

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/** Auth deletion activates the existing retryable users/{uid} + jevUsage/{uid} cleanup trigger. */
internal class FirebaseAccountDeletion(private val context: Context, private val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    private val project = requireNotNull(db.app.options.projectId)
    private val journal = AccountDeletionJournal(context,project)
    fun pending() = journal.read()

    suspend fun delete(reauthenticate: suspend (FirebaseUser) -> Unit, pauseSync: suspend () -> Unit): Boolean {
        val user = auth.currentUser ?: throw AccountOwnerMismatch()
        val uid = user.uid
        var deleted = false
        var requested = false
        journal.save(uid,DeletionPhase.REAUTHENTICATING)
        try {
            return performAccountDeletion(uid, { auth.currentUser?.uid }, { reauthenticate(user) }, {
                AccountStorageGuard.block(uid); pauseSync()
            }, {
                journal.save(uid,DeletionPhase.REQUESTED)
                requested = true
                user.delete().await()
                deleted = true
                // If this write fails, REQUESTED + a signed-out SDK still permits recovery.
                runCatching { journal.save(uid,DeletionPhase.DELETED) }
            }, {
                withContext(NonCancellable) { finishCleanup(uid) }
            })
        } finally {
            if (!deleted) {
                AccountStorageGuard.unblock(uid)
                // A cancelled/lost network acknowledgement can still finish remotely.
                // Preserve REQUESTED for a non-destructive reload on the next startup.
                if (!requested) journal.clear()
            }
        }
    }

    private suspend fun finishCleanup(uid: String) {
        AccountStorageGuard.block(uid)
        // Repository listeners/writes have been stopped and joined before termination.
        db.terminate().await()
        db.clearPersistence().await()
        withContext(Dispatchers.IO) { removeLocalAccountData(context,uid,project);journal.clear() }
    }

    /** Never reissue a destructive request after process death. Resolve its durable phase instead. */
    suspend fun recover(pending: PendingDeletion): String? {
        if (pending.phase == DeletionPhase.REAUTHENTICATING) {
            try { withTimeout(20_000) { auth.pendingAuthResult?.await() } }
            catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
            catch (_: Exception) { }
            journal.clear()
            return DELETE_ACCOUNT_INTERRUPTED
        }
        if (pending.phase == DeletionPhase.DELETED && auth.currentUser?.uid == pending.uid) auth.signOut()
        var completed = pending.phase == DeletionPhase.DELETED || auth.currentUser?.uid != pending.uid
        if (!completed) {
            try { withTimeout(15_000) { auth.currentUser!!.reload().await() } }
            catch (e: FirebaseAuthInvalidUserException) {
                if (e.errorCode == "ERROR_USER_NOT_FOUND") {
                    if (auth.currentUser?.uid == pending.uid) auth.signOut()
                    completed = true
                } else return DELETE_ACCOUNT_INTERRUPTED
            } catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
            catch (_: Exception) { return DELETE_ACCOUNT_INTERRUPTED }
        }
        if (!completed) { journal.clear();AccountStorageGuard.unblock(pending.uid);return DELETE_ACCOUNT_INTERRUPTED }
        return try { withContext(NonCancellable) { finishCleanup(pending.uid) };null }
        catch (_: Exception) { DELETE_ACCOUNT_LOCAL_WARNING }
    }
}
