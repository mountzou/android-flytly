package com.mountzou.flytly.atlas

import android.content.Context
import com.mountzou.flytly.storage.AtomicFileLocks
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

enum class PlaceStatus(val value: String) { VISITED("visited"), WISHLIST("wishlist"), REMOVED("removed") }

/** Local records use the same place keys/status vocabulary as iOS, without claiming a cloud account. */
data class TravelSnapshot(val records: Map<String, PlaceStatus> = emptyMap()) {
    fun status(city: AtlasCity, atlas: AtlasCatalog): PlaceStatus? {
        records["city:${city.id}"]?.let { return it }
        val previous = atlas.visitIDs(city).mapNotNull { records["city:$it"] }
        return listOf(PlaceStatus.VISITED, PlaceStatus.WISHLIST, PlaceStatus.REMOVED).firstOrNull { it in previous }
    }

    fun otherStatus(countryID: String): PlaceStatus? = records["country:$countryID"]

    fun withStatus(city: AtlasCity?, countryID: String, next: PlaceStatus, atlas: AtlasCatalog): TravelSnapshot {
        val updated = records.toMutableMap()
        if (city == null) updated["country:$countryID"] = next
        else atlas.visitIDs(city).forEach { updated["city:$it"] = next }
        return TravelSnapshot(updated)
    }

    fun cities(status: PlaceStatus, atlas: AtlasCatalog): List<AtlasCity> = records.asSequence()
        .filter { (key, _) -> key.startsWith("city:") }
        .mapNotNull { atlas.cityForSavedID(it.key.removePrefix("city:")) }.distinctBy { it.id }
        .filter { status(it, atlas) == status }
        .sortedBy { it.name }.toList()

    fun countryIDs(status: PlaceStatus, atlas: AtlasCatalog): Set<String> =
        records.filter { (key, value) -> key.startsWith("country:") && value == status }
            .keys.mapTo(mutableSetOf()) { it.removePrefix("country:") }
            .union(cities(status, atlas).map { it.countryID }).filterTo(mutableSetOf()) { it in atlas.countriesByID }
}

fun AtlasCountry.travelContinent(): String? = when (continent) {
    "North America", "South America" -> "Americas"
    "Seven seas (open ocean)" -> when (id) {
        "ATF", "SYC", "SHN", "MUS", "IOT" -> "Africa"
        "MDV" -> "Asia"
        "HMD" -> "Oceania"
        "SGS", "CLP" -> "Americas"
        else -> null
    }
    "Europe", "Asia", "Africa", "Oceania", "Antarctica" -> continent
    else -> null
}

class LocalTravelFile(context: Context, namespace: String? = null) {
    private val file = AtomicFile(File(context.filesDir,
        if (namespace == null) "local-travel-places.json" else "test-travel-$namespace.json"))

    private val lock = AtomicFileLocks.forFile(file.baseFile)
    internal fun delete() = synchronized(lock) { file.delete();check(listOf("", ".bak", ".new").none { File(file.baseFile.path + it).exists() }) }

    fun load(): TravelSnapshot = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized TravelSnapshot()
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(json.getInt("version") == 1) { "Unsupported local travel format" }
        val values = json.getJSONObject("records")
        TravelSnapshot(values.keys().asSequence().associateWith { key ->
            require((key.startsWith("city:") || key.startsWith("country:")) && key.substringAfter(':').isNotBlank())
            PlaceStatus.entries.first { it.value == values.getString(key) }
        })
    }

    fun save(snapshot: TravelSnapshot) = synchronized(lock) {
        val values = JSONObject()
        snapshot.records.toSortedMap().forEach { (key, value) -> values.put(key, value.value) }
        val bytes = JSONObject().put("version", 1).put("records", values).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    fun saveIfCurrent(expected: TravelSnapshot, updated: TravelSnapshot) = synchronized(lock) {
        if (load() != expected) throw PlaceChangeConflictException()
        save(updated)
    }
}
