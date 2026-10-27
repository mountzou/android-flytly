package com.mountzou.flytly.account

import android.content.Context
import android.util.AtomicFile
import com.mountzou.flytly.atlas.LocalTravelFile
import com.mountzou.flytly.preferences.PreferencesCacheFile
import com.mountzou.flytly.storage.AtomicFileLocks
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Stops late map lifecycle callbacks from recreating a deleted account's preferences. */
internal object AccountStorageGuard {
    private val deleting = ConcurrentHashMap.newKeySet<String>()
    fun block(uid: String) { deleting.add(accountNamespace(uid)) }
    fun unblock(uid: String) { deleting.remove(accountNamespace(uid)) }
    fun allows(namespace: String?) = namespace == null || namespace !in deleting
}

internal fun removeLocalAccountData(context: Context, uid: String, project: String) {
    AccountCacheFile(context,uid,project).delete()
    PreferencesCacheFile(context,uid,project).delete()
    val namespace = accountNamespace(uid)
    LocalTravelFile(context,namespace).delete()
    for (name in listOf("atlas_settings_$namespace", "atlas_map_$namespace")) {
        check(context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit())
        check(context.deleteSharedPreferences(name))
    }
}

internal enum class DeletionPhase { REAUTHENTICATING, REQUESTED, DELETED }
internal data class PendingDeletion(val uid: String, val phase: DeletionPhase)

/** Only owner/phase are durable; provider credentials are never persisted here. */
internal class AccountDeletionJournal(context: Context, private val project: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir,"deletion-${accountNamespace(project)}.json"))
    private val lock = AtomicFileLocks.forFile(file.baseFile)
    fun read(): PendingDeletion? = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return@synchronized null
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        check(json.getString("project") == project)
        PendingDeletion(json.getString("uid").also { require(it.isNotBlank() && it.length <= 128) }, DeletionPhase.valueOf(json.getString("phase")))
    }
    fun save(uid: String, phase: DeletionPhase) = synchronized(lock) {
        val bytes = JSONObject().put("project",project).put("uid",uid).put("phase",phase.name).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(bytes);file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream);throw e }
    }
    fun clear() = synchronized(lock) { file.delete() }
}
