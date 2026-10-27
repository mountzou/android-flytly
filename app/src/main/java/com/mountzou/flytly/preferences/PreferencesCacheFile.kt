package com.mountzou.flytly.preferences

import android.content.Context
import android.util.AtomicFile
import com.mountzou.flytly.account.accountNamespace
import com.mountzou.flytly.account.profileFields
import com.mountzou.flytly.storage.AtomicFileLocks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class PreferencesCacheFile(context: Context, uid: String, project: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "${accountNamespace("$project:$uid")}-preferences.json"))
    private val lock = AtomicFileLocks.forFile(file.baseFile)
    fun delete() = synchronized(lock) { file.delete();check(listOf("", ".bak", ".new").none { File(file.baseFile.path + it).exists() }) }
    fun load(): Map<String, Any>? = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(json.getInt("version") == 1)
        val profile = json.getJSONObject("profile")
        profile.keys().asSequence().filter { it in profileFields }.associateWith { key ->
            when (val value = profile.get(key)) {
                is JSONArray -> (0 until value.length()).map { value.getString(it) }
                else -> value
            }
        }
    }
    fun save(profile: Map<String, Any>) = synchronized(lock) {
        val bytes = JSONObject().put("version", 1).put("profile", JSONObject(profile)).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
