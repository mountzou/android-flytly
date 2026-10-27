package com.mountzou.flytly.account

/** No remote/local data is removed until identity confirmation succeeds for the original owner. */
internal suspend fun performAccountDeletion(
    owner: String,
    currentOwner: () -> String?,
    reauthenticate: suspend () -> Unit,
    pauseSync: suspend () -> Unit,
    deleteUser: suspend () -> Unit,
    cleanup: suspend () -> Unit
): Boolean {
    fun checkOwner() { if (currentOwner() != owner) throw AccountOwnerMismatch() }
    checkOwner(); reauthenticate(); checkOwner(); pauseSync(); checkOwner(); deleteUser()
    // Auth deletion is final. Cleanup failure must not offer to delete the account again.
    return try { cleanup(); true } catch (_: Exception) { false }
}

internal class AccountOwnerMismatch : Exception("Choose the same account you’re currently signed in with. Your account has not been deleted.")
internal const val DELETE_ACCOUNT_ERROR = "Couldn’t delete your account. Check your connection and try again."
internal const val DELETE_ACCOUNT_INTERRUPTED = "Your identity confirmation was interrupted. Please try deleting your account again."
internal const val DELETE_ACCOUNT_LOCAL_WARNING = "Your account was deleted, but local travel data couldn’t be removed. Remove the app from this device to clear it."

data class AccountDetails(val name: String?, val email: String?, val providerIDs: List<String>) {
    val loginMethods get() = providerIDs.mapNotNull { when (it) { "apple.com" -> "Apple"; "google.com" -> "Google"; else -> null } }.distinct().sorted().joinToString(", ")
}
