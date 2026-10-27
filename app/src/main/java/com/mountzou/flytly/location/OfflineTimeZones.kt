package com.mountzou.flytly.location

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import com.mountzou.flytly.storage.AtomicFileLocks
import org.json.JSONObject
import java.io.DataInputStream
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import java.util.zip.InflaterInputStream
import kotlin.math.*

/** Lazy immutable public dataset. Never persists or transmits a user's coordinate. */
internal class OfflineTimeZones private constructor(private val database: SQLiteDatabase) {
    fun zoneAt(coordinate: Coordinate): ZoneId? {
        val x = floor((coordinate.longitude + 180) / 5).toInt().coerceIn(0, 71)
        val y = floor((coordinate.latitude + 90) / 5).toInt().coerceIn(0, 35)
        val lon = coordinate.longitude.toString(); val lat = coordinate.latitude.toString()
        val found = mutableSetOf<String>()
        database.rawQuery("SELECT p.zone,p.rings FROM cells c JOIN polygons p ON p.id=c.polygon WHERE c.cell=? AND p.west<=? AND p.east>=? AND p.south<=? AND p.north>=?",
            arrayOf((y * 72 + x).toString(), lon, lon, lat, lat)).use { cursor ->
            while (cursor.moveToNext()) if (contains(cursor.getBlob(1), coordinate)) found += cursor.getString(0)
        }
        // Disputed overlaps/boundaries must not silently choose an arbitrary clock.
        if (found.size != 1) return null
        val zones = found.mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }.distinct()
        return zones.singleOrNull()
    }

    companion object {
        @Volatile private var instance: OfflineTimeZones? = null
        fun open(context: Context): OfflineTimeZones = instance ?: synchronized(this) {
            instance ?: run {
                val manifest = JSONObject(context.assets.open("timezones/manifest.json").bufferedReader().use { it.readText() })
                require(manifest.getString("format") == "2")
                val expected = manifest.getString("databaseSHA256")
                val file = AtomicFile(File(context.noBackupFilesDir, "timezones-${expected.take(16)}.sqlite"))
                synchronized(AtomicFileLocks.forFile(file.baseFile)) {
                    if (!file.baseFile.exists()) {
                        val out = file.startWrite()
                        try {
                            val digest = MessageDigest.getInstance("SHA-256")
                            context.assets.open("timezones/boundaries.sqlite").use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) { val count = input.read(buffer); if (count < 0) break; out.write(buffer, 0, count); digest.update(buffer, 0, count) }
                            }
                            require(digest.digest().joinToString("") { "%02x".format(it) } == expected)
                            file.finishWrite(out)
                        } catch (error: Exception) { file.failWrite(out); throw error }
                    }
                }
                val db = SQLiteDatabase.openDatabase(file.baseFile.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                OfflineTimeZones(db).also { instance = it }
            }
        }

        internal fun contains(compressed: ByteArray, coordinate: Coordinate): Boolean =
            DataInputStream(BufferedInputStream(InflaterInputStream(compressed.inputStream()))).use { input ->
                val rings = input.readInt(); require(rings in 1..1_000_000)
                for (ring in 0 until rings) {
                    val count = input.readInt(); require(count in 4..100_000_000)
                    var xInt = input.readLong(); var yInt = input.readLong()
                    var px = xInt / 10_000_000.0; var py = yInt / 10_000_000.0
                    var inside = false; var boundary = false
                    repeat(count - 1) {
                        xInt += input.readLong(); yInt += input.readLong()
                        val x = xInt / 10_000_000.0; val y = yInt / 10_000_000.0
                        val cross = (coordinate.longitude - px) * (y - py) - (coordinate.latitude - py) * (x - px)
                        if (abs(cross) < 1e-12 && coordinate.longitude >= min(px, x) && coordinate.longitude <= max(px, x) && coordinate.latitude >= min(py, y) && coordinate.latitude <= max(py, y)) boundary = true
                        if ((y > coordinate.latitude) != (py > coordinate.latitude) && coordinate.longitude < (px - x) * (coordinate.latitude - y) / (py - y) + x) inside = !inside
                        px = x; py = y
                    }
                    if (ring == 0 && !(inside || boundary)) return@use false
                    if (ring > 0 && (inside || boundary)) return@use false
                }
                true
            }
    }
}
