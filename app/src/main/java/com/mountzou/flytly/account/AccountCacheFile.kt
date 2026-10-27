package com.mountzou.flytly.account

import android.content.Context
import com.mountzou.flytly.storage.AtomicFileLocks
import android.util.AtomicFile
import com.mountzou.flytly.atlas.PlaceStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class AccountCacheFile(context: Context, uid: String, projectID: String) {
    // Excluded from Android backups. Never reuse unscoped pre-authentication data.
    private val file = AtomicFile(File(context.noBackupFilesDir, "${accountNamespace("$projectID:$uid")}.json"))
    private val lock = AtomicFileLocks.forFile(file.baseFile)
    fun delete() = synchronized(lock) {
        file.delete()
        check(listOf("", ".bak", ".new").none { File(file.baseFile.path + it).exists() })
    }
    fun load(): AccountCache = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized AccountCache()
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(json.getInt("version") == 1)
        val places = json.getJSONObject("places")
        val profile = json.getJSONObject("profile")
        val pending = json.getJSONArray("pending")
        AccountCache(json.getString("sourceID").also { require(UUID_PATTERN.matches(it)) }, json.getLong("nextSequence"),
            places.keys().asSequence().associateWith { key -> validatePlaceKey(key); PlaceStatus.entries.first { it.value == places.getString(key) } },
            profile.keys().asSequence().associateWith { jsonValue(profile.get(it)) },
            (0 until pending.length()).map { pending.getJSONObject(it) }.associate { row ->
                val key = row.getString("key")
                val value = jsonValue(row.get("value"))
                validateChange(key, value)
                key to PendingChange(row.getLong("sequence"), key, value)
            }).also { cache -> require(cache.nextSequence >= 0 && cache.pending.values.all { it.sequence in 1..cache.nextSequence }) }
    }
    fun save(cache: AccountCache) = synchronized(lock) {
        val pending = JSONArray()
        cache.pending.values.sortedBy { it.sequence }.forEach { pending.put(JSONObject().put("sequence", it.sequence).put("key", it.key).put("value", JSONObject.wrap(it.value))) }
        val json = JSONObject().put("version", 1).put("sourceID", cache.sourceID).put("nextSequence", cache.nextSequence)
            .put("places", JSONObject(cache.places.mapValues { it.value.value })).put("profile", JSONObject(cache.profile)).put("pending", pending)
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun jsonValue(value: Any): Any = when (value) {
        is JSONArray -> (0 until value.length()).map { value.getString(it) }
        else -> value
    }
    companion object { private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") }
}

internal fun validatePlaceKey(key: String) {
    val kind = key.substringBefore(':'); val id = key.substringAfter(':', "")
    require(kind in listOf("city", "country") && id.isNotBlank() && id.toByteArray().size <= 200 && '/' !in id)
}
internal fun validateChange(key: String, value: Any) {
    if (!key.startsWith("profile:")) { validatePlaceKey(key); require(PlaceStatus.entries.any { it.value == value }); return }
    when (val field = key.removePrefix("profile:")) {
        in preferenceFields -> require(value is Number && value.toDouble() in listOf(1.0, 2.0, 3.0))
        "mustHave", "mustAvoid" -> require(value is List<*> && value.all { it is String })
        "onboardingStatus" -> require(value in listOf("pending", "completed", "skipped"))
        "completed", "skipped" -> require(value is Boolean)
        else -> error("Unsupported profile field: $field")
    }
}
