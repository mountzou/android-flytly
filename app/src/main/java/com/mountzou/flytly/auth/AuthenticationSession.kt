package com.mountzou.flytly.auth

import android.app.Activity
import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.mountzou.flytly.R
import com.mountzou.flytly.account.AccountRepository
import com.mountzou.flytly.account.*
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal data class AuthState(val restored: Boolean = false, val user: FirebaseUser? = null,
    val repository: AccountRepository? = null, val busy: Boolean = false, val error: String? = null,
    val deleting: Boolean = false, val accountError: String? = null)

/** Firebase is the only authority for login and session restoration. */
internal class AuthenticationSession(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val credentials by lazy { CredentialManager.create(application) }
    private val mutableState = MutableStateFlow(AuthState())
    val state = mutableState.asStateFlow()
    private var pendingNewAccountUID: String? = null
    private val project = requireNotNull(FirebaseApp.getInstance().options.projectId)
    private val deletionRecovery = runCatching { AccountDeletionJournal(application,project).read() }
    private val pendingDeletion = deletionRecovery.getOrNull()
    private var recoveringDeletion = pendingDeletion != null
    private val listener = FirebaseAuth.AuthStateListener { firebase ->
        if (!recoveringDeletion && !state.value.deleting) accept(firebase.currentUser)
    }
    private fun accept(user: FirebaseUser?) {
        if (user != null && !allowed(user)) { auth.signOut(); return }
        val previous = mutableState.value
        if (previous.user?.uid != user?.uid) previous.repository?.close()
        val repository = if (user == null) null else previous.repository?.takeIf { previous.user?.uid == user.uid }
            ?: AccountRepository(getApplication(), user.uid, auth, FirebaseFirestore.getInstance())
        mutableState.value = previous.copy(restored = true, user = user, repository = repository)
        com.mountzou.flytly.atlas.AtlasPerformance.mark("auth.restored")
        if (user != null && pendingNewAccountUID == user.uid) {
            repository?.preferences?.registerNewAccount(); pendingNewAccountUID = null
        }
    }
    init {
        auth.addAuthStateListener(listener)
        if (pendingDeletion != null) viewModelScope.launch {
            val message = try { FirebaseAccountDeletion(getApplication(),auth,FirebaseFirestore.getInstance()).recover(pendingDeletion) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { DELETE_ACCOUNT_INTERRUPTED }
            recoveringDeletion = false
            mutableState.value = state.value.copy(accountError = message, error = message.takeIf { auth.currentUser == null })
            accept(auth.currentUser)
        } else {
          validateSession()
          if (deletionRecovery.isFailure) mutableState.value = state.value.copy(accountError = DELETE_ACCOUNT_INTERRUPTED)
          else auth.pendingAuthResult?.let { pending ->
            mutableState.value = mutableState.value.copy(busy = true)
            viewModelScope.launch { perform { pending.await() } }
          }
        }
    }
    fun signIn(activity: Activity, provider: String) {
        if (state.value.busy || state.value.user != null) return
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            perform {
                if (provider == "Apple") {
                    val apple = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()
                    (auth.pendingAuthResult ?: auth.startActivityForSignInWithProvider(activity, apple)).await()
                } else {
                    val request = GetCredentialRequest.Builder().addCredentialOption(
                        GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()).build()
                    val result = credentials.getCredential(activity, request).credential
                    check(result is CustomCredential && result.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                    val google = GoogleIdTokenCredential.createFrom(result.data)
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
                }
            }
        }
    }
    private suspend fun perform(block: suspend () -> AuthResult) {
        try {
            val result = block()
            if (result.additionalUserInfo?.isNewUser == true) {
                val uid = result.user?.uid
                if (uid != null && state.value.user?.uid == uid) state.value.repository?.preferences?.registerNewAccount()
                else pendingNewAccountUID = uid
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: GetCredentialCancellationException) { /* Closing the provider is a normal exit. */ }
        catch (_: NoCredentialException) {
            mutableState.value = state.value.copy(error = "No Google account is available. Add a Google account in your device’s Settings, then try again.")
        }
        catch (error: Exception) {
            val code = (error as? FirebaseAuthException)?.errorCode
            val message = when (code) {
                "ERROR_WEB_CONTEXT_CANCELED", "ERROR_CANCELED" -> null
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
                    "An account already exists with this email. Sign in using the provider you originally chose."
                "ERROR_NETWORK_REQUEST_FAILED" -> "Could not connect. Check your internet connection and try again."
                "ERROR_USER_DISABLED" -> "This account is disabled. Please contact support."
                else -> "Sign-in could not be completed. Please try again."
            }
            mutableState.value = state.value.copy(error = message)
        } finally { mutableState.value = state.value.copy(busy = false) }
    }
    fun validateSession() {
        if (state.value.busy || recoveringDeletion) return
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try { user.reload().await() }
            catch (error: FirebaseAuthInvalidUserException) {
                if (auth.currentUser?.uid == user.uid) signOut()
            } catch (_: Exception) { /* Retain a valid persisted session when offline. */ }
        }
    }
    fun signOut() {
        if (state.value.busy) return
        pendingNewAccountUID = null
        mutableState.value.repository?.close()
        auth.signOut()
        mutableState.value = AuthState(restored = true)
        viewModelScope.launch { try { credentials.clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) { } }
    }
    fun clearAccountError() { mutableState.value = state.value.copy(accountError = null) }

    fun deleteAccount(activity: Activity) {
        val user = state.value.user ?: return
        if (state.value.busy || auth.currentUser?.uid != user.uid) return
        val oldRepository = state.value.repository
        mutableState.value = state.value.copy(busy = true,deleting = true,accountError = null,error = null)
        viewModelScope.launch {
            var deleted = false
            var paused = false
            var cleanupComplete = false
            var errorMessage: String? = null
            try {
                cleanupComplete = FirebaseAccountDeletion(getApplication(),auth,FirebaseFirestore.getInstance()).delete(
                    reauthenticate = { original -> reauthenticateForDeletion(activity,original) },
                    pauseSync = { paused = true;oldRepository?.closeAndJoin() })
                deleted = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: GetCredentialCancellationException) { /* A provider cancellation changes no data. */ }
            catch (error: Exception) {
                errorMessage = when {
                    error is AccountOwnerMismatch || (error as? FirebaseAuthException)?.errorCode == "ERROR_USER_MISMATCH" -> AccountOwnerMismatch().message
                    (error as? FirebaseAuthException)?.errorCode in listOf("ERROR_WEB_CONTEXT_CANCELED","ERROR_CANCELED") -> null
                    else -> DELETE_ACCOUNT_ERROR
                }
            } finally {
                if (deleted) {
                    pendingNewAccountUID = null
                    try { credentials.clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) { }
                    mutableState.value = AuthState(restored = true,error = if (cleanupComplete) null else DELETE_ACCOUNT_LOCAL_WARNING)
                    // A different externally selected account is accepted only after cleanup has finished.
                    if (auth.currentUser != null) accept(auth.currentUser)
                } else {
                    mutableState.value = state.value.copy(busy = false,deleting = false,accountError = errorMessage,repository = if (paused) null else oldRepository)
                    if (currentCoroutineContext().isActive) accept(auth.currentUser)
                }
            }
        }
    }

    private suspend fun reauthenticateForDeletion(activity: Activity, user: FirebaseUser) {
        if (auth.currentUser?.uid != user.uid) throw AccountOwnerMismatch()
        if (user.providerData.any { it.providerId == "apple.com" }) {
            val provider = OAuthProvider.newBuilder("apple.com",auth).setScopes(listOf("email","name")).build()
            val result = (auth.pendingAuthResult ?: user.startActivityForReauthenticateWithProvider(activity,provider)).await()
            if (result.user?.uid != user.uid || auth.currentUser?.uid != user.uid) throw AccountOwnerMismatch()
            val accessToken = (result.credential as? OAuthCredential)?.accessToken?.takeIf { it.isNotBlank() }
                ?: error("Apple did not supply an access token")
            auth.revokeAccessToken(accessToken).await()
        } else if (user.providerData.any { it.providerId == "google.com" }) {
            val request = GetCredentialRequest.Builder().addCredentialOption(
                GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()).build()
            val result = credentials.getCredential(activity,request).credential
            check(result is CustomCredential && result.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token = GoogleIdTokenCredential.createFrom(result.data)
            // Reauthenticate the captured user. A different provider identity must never sign in here.
            user.reauthenticate(GoogleAuthProvider.getCredential(token.idToken,null)).await()
        } else error("Unsupported account provider")
        if (auth.currentUser?.uid != user.uid) throw AccountOwnerMismatch()
    }
    override fun onCleared() { auth.removeAuthStateListener(listener); state.value.repository?.close() }
    private fun allowed(user: FirebaseUser) = !user.isAnonymous && user.providerData.any { it.providerId in listOf("google.com", "apple.com") }
}
