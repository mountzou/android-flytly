package com.mountzou.flytly

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.atlas.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AtlasSqlInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun indexedQueriesPreserveSearchOrderingCountriesAndLabelPriority() {
        val indexed = AtlasSqlReader.open(context)
        val source = LegacyAtlasJsonReader.read(context.assets)
        for (query in listOf("", "Αθήνα", "Χανιά Greece", "Paris", "France", "Tokyo", "会津若松", "%", "' OR 1=1")) {
            val expected = source.search(query, 30); val actual = indexed.search(query, 30)
            assertEquals(query, expected.countries.map { it.id }, actual.countries.map { it.id })
            assertEquals(query, expected.cities.map { it.id }, actual.cities.map { it.id })
        }
        for (country in source.countries) {
            assertEquals(country.id, source.citiesIn(country.id).map { it.id }, indexed.citiesIn(country.id).map { it.id })
            assertEquals(country.id, source.browseCities(country.id, 1_000_000).map { it.id }, indexed.browseCities(country.id, 1_000_000).map { it.id })
        }
        for (region in listOf(doubleArrayOf(30.0, 60.0, -15.0, 35.0), doubleArrayOf(-50.0, 50.0, 170.0, -170.0))) for (zoom in listOf(5.0, 6.0, 7.0, 8.0)) {
            val (s,n,w,e) = region
            assertEquals(source.labelsIn(s,n,w,e,zoom).map { it.id }, indexed.labelsIn(s,n,w,e,zoom).map { it.id })
        }
        assertNull(indexed.cityForSavedID("unknown-city"))
        val city = indexed.cityForSavedID("1159151605")!!
        assertEquals(source.visitIDs(source.cityForSavedID("1159151605")!!), indexed.visitIDs(city))
    }

    @Test fun corruptPublicCacheIsRebuiltWithoutTouchingAccountData() {
        val directory = File(context.cacheDir, "atlas-sql-tests/${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
        val manifest = JSONObject(context.assets.open("atlas/manifest.json").bufferedReader().use { it.readText() })
        val expected = manifest.getJSONObject("generatedSHA256").getString("catalog.sqlite")
        val cached = File(directory, "atlas-public/catalog-$expected.sqlite").apply { parentFile!!.mkdirs(); writeText("corrupt derived cache") }
        val catalog = AtlasSqlReader.open(isolated)
        assertEquals(258, catalog.countries.size)
        assertEquals(26_288, catalog.cities.size)
        assertEquals("ghsl-r2024a-11345", catalog.cityForSavedID("1159151605")!!.id)
        assertEquals(expected, MessageDigest.getInstance("SHA-256").digest(cached.readBytes()).joinToString("") { "%02x".format(it) })
    }
}
