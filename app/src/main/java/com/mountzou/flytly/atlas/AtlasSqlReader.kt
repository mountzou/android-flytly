package com.mountzou.flytly.atlas

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import android.util.LruCache
import com.mountzou.flytly.storage.AtomicFileLocks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Immutable, APK-derived public data. This database never contains account records. */
internal class AtlasSqlReader private constructor(private val db: SQLiteDatabase) : AtlasCityStorage {
    private val cache = LruCache<String, AtlasCity>(512)
    private fun count(key: String) = db.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use { require(it.moveToFirst()); it.getString(0).toInt() }
    override val cities: List<AtlasCity> = object : AbstractList<AtlasCity>() {
        override val size = count("cities")
        override fun get(index: Int): AtlasCity {
            if (index !in 0 until size) throw IndexOutOfBoundsException()
            return query("SELECT $COLUMNS FROM cities WHERE ordinal=?", arrayOf(index.toString())).single()
        }
    }
    override val selectableCities: List<AtlasCity> = object : AbstractList<AtlasCity>() {
        override val size = count("selectable")
        override fun get(index: Int): AtlasCity {
            if (index !in 0 until size) throw IndexOutOfBoundsException()
            return query("SELECT $COLUMNS FROM cities WHERE selectable_order=?", arrayOf(index.toString())).single()
        }
    }
    override fun citiesIn(countryID: String) = query("SELECT $COLUMNS FROM cities WHERE country_id=? AND selectable_order IS NOT NULL ORDER BY selectable_order", arrayOf(countryID))
    private fun city(id: String): AtlasCity? = cache[id] ?: query("SELECT $COLUMNS FROM cities WHERE id=?", arrayOf(id)).firstOrNull()
    override fun resolve(id: String): AtlasCity? = city(id)?.let { original -> original.urbanCenterID?.let(::city) ?: original }
    override fun visitIDs(city: AtlasCity): Set<String> = buildSet {
        add(city.id)
        db.rawQuery("SELECT id FROM cities WHERE successor=? ORDER BY ordinal", arrayOf(city.id)).use { while (it.moveToNext()) add(it.getString(0)) }
    }
    override fun search(normalized: String, words: List<String>, limit: Int): List<AtlasCity> {
        require(limit >= 0)
        val clauses = words.joinToString(" AND ") { "(instr(cities.search_text,?)>0 OR instr(countries.search_text,?)>0)" }
        val args = words.flatMap { listOf(it, it) } + listOf(normalized, limit.toString())
        return query("SELECT ${COLUMNS.split(',').joinToString(",") { "cities.$it" }} FROM cities JOIN countries ON countries.id=cities.country_id WHERE selectable_order IS NOT NULL AND $clauses ORDER BY CASE WHEN instr(name_key,?)=1 THEN 0 ELSE 1 END,selectable_order LIMIT ?", args.toTypedArray())
    }
    override fun labelsIn(south: Double, north: Double, west: Double, east: Double, zoom: Double): List<AtlasCity> {
        val args = mutableListOf(zoom.toString(), south.toString(), north.toString())
        val longitude = if (east - west >= 360) "" else {
            args += west.toString(); args += east.toString()
            if (west <= east) " AND longitude BETWEEN ? AND ?" else " AND (longitude>=? OR longitude<=?)"
        }
        return query("SELECT $COLUMNS FROM cities WHERE selectable_order IS NOT NULL AND minimum_zoom<=? AND latitude BETWEEN ? AND ?$longitude ORDER BY label_order", args.toTypedArray())
    }
    private fun query(sql: String, arguments: Array<String>): List<AtlasCity> = db.rawQuery(sql, arguments).use { cursor ->
        buildList { while (cursor.moveToNext()) add(row(cursor)) }
    }
    private fun row(c: Cursor): AtlasCity {
        val id = c.getString(1)
        cache[id]?.let { return it }
        val city = AtlasCity(id, c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getDouble(6), c.getInt(7) == 1,
            when (c.getInt(8)) { 0 -> CityKind.URBAN_CENTER; 1 -> CityKind.CAPITAL_FALLBACK; 2 -> CityKind.LEGACY; else -> error("Invalid atlas city kind") },
            Aliases(c.getString(9), c.getInt(10)), if (c.isNull(11)) null else c.getString(11), if (c.isNull(12)) null else c.getInt(12), c.getString(13))
        cache.put(id, city)
        return city
    }

    private class Aliases(json: String, override val size: Int) : AbstractList<String>() {
        private val values by lazy(LazyThreadSafetyMode.PUBLICATION) { JSONArray(json).also { require(it.length() == size) } }
        override fun get(index: Int): String { if (index !in 0 until size) throw IndexOutOfBoundsException(); return values.getString(index) }
    }

    companion object {
        private const val COLUMNS = "ordinal,id,name,country_id,latitude,longitude,population,capital,kind,aliases,alias_count,successor,year,search_text"
        internal fun open(context: Context): AtlasCatalog {
            val manifest = JSONObject(context.assets.open("atlas/manifest.json").bufferedReader().use { it.readText() })
            require(manifest.getInt("catalogIndexFormat") == 3)
            val expected = manifest.getJSONObject("generatedSHA256").getString("catalog.sqlite")
            require(expected.matches(Regex("[a-f0-9]{64}")))
            val file = File(context.noBackupFilesDir, "atlas-public/catalog-$expected.sqlite")
            file.parentFile!!.mkdirs()
            val atomic = AtomicFile(file)
            synchronized(AtomicFileLocks.forFile(file)) {
                val valid = runCatching { atomic.openRead().use { digest(it) } == expected }.getOrDefault(false)
                if (!valid) {
                    val output = atomic.startWrite()
                    try {
                        val hash = MessageDigest.getInstance("SHA-256")
                        context.assets.open("atlas/catalog.sqlite").use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) { val size = input.read(buffer); if (size < 0) break; hash.update(buffer, 0, size); output.write(buffer, 0, size) }
                        }
                        require(hash.digest().joinToString("") { "%02x".format(it) } == expected)
                        atomic.finishWrite(output)
                    } catch (error: Exception) { atomic.failWrite(output); throw error }
                }
            }
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            try {
                require(db.version == 3)
                val storage = AtlasSqlReader(db)
                val countries = db.rawQuery("SELECT id,name,continent,aliases,alias_count,search_text FROM countries", null).use { c ->
                    buildList { while (c.moveToNext()) add(AtlasCountry(c.getString(0), c.getString(1), c.getString(2), Aliases(c.getString(3), c.getInt(4)), c.getString(5))) }
                }
                val labels = db.rawQuery("SELECT country_id,latitude,longitude,minimum_zoom,maximum_zoom,name,rank FROM labels ORDER BY ordinal", null).use { c ->
                    buildMap { while (c.moveToNext()) { val id = c.getString(0); put(id, CountryLabel(id, c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getString(5), c.getInt(6))) } }
                }
                require(countries.size == storage.count("countries") && labels.size == storage.count("labels"))
                return AtlasCatalog.indexed(countries, labels, storage)
            } catch (error: Exception) { db.close(); throw error }
        }
        private fun digest(stream: java.io.InputStream): String {
            val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024)
            while (true) { val size = stream.read(buffer); if (size < 0) break; hash.update(buffer, 0, size) }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
