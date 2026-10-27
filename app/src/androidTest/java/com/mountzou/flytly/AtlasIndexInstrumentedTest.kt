package com.mountzou.flytly

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.atlas.AtlasRepository
import com.mountzou.flytly.atlas.LegacyAtlasJsonReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtlasIndexInstrumentedTest {
    @Test fun everyDecodedFieldMatchesTheOriginalIOSJSON() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = context.assets
        val indexed = AtlasRepository.load(context)
        val source = LegacyAtlasJsonReader.read(assets)
        assertEquals(source.countries, indexed.countries)
        assertEquals(source.cities.size, indexed.cities.size)
        source.cities.forEachIndexed { index, expected ->
            assertEquals("City ${expected.id}", expected, indexed.cities[index])
        }
        assertEquals(source.labels, indexed.labels)
        assertEquals(source.selectableCities.map { it.id }, indexed.selectableCities.map { it.id })
        source.cities.forEach { city ->
            assertEquals(source.cityForSavedID(city.id), indexed.cityForSavedID(city.id))
        }
    }
}
