package com.mountzou.flytly

import com.mountzou.flytly.atlas.*
import org.junit.Assert.*
import org.junit.Test

class AtlasCatalogTest {
    private val greece = AtlasCountry("GRC", "Greece", "Europe", listOf("Ελλάδα"))
    private val aland = AtlasCountry("ALD", "Aland", "Europe")
    private fun city(id: String, name: String = id, population: Double = 60_000.0,
                     country: String = "GRC", kind: CityKind = CityKind.URBAN_CENTER,
                     successor: String? = null, aliases: List<String> = emptyList()) =
        AtlasCity(id, name, country, 38.0, 23.7, population, false, kind, aliases, successor)
    private fun catalog(vararg cities: AtlasCity) = AtlasCatalog(listOf(greece, aland), cities.toList(), emptyMap())

    @Test fun thresholdIsStrictAndFallbackOnlyAppliesWithoutEligibleUrbanCentres() {
        val data = catalog(city("threshold", population = 50_000.0), city("eligible", population = 50_000.01),
            city("old-capital", kind = CityKind.CAPITAL_FALLBACK),
            city("mariehamn", country = "ALD", population = 0.0, kind = CityKind.CAPITAL_FALLBACK))
        assertEquals(listOf("eligible"), data.citiesIn("GRC").map { it.id })
        assertEquals(listOf("mariehamn"), data.browseCities("ALD", 1_000_000).map { it.id })
    }

    @Test fun populationOrderHasStableNameAndIdentifierTies() {
        val data = catalog(city("b", "Athens"), city("a", "Athens"), city("c", "Chania"), city("largest", population = 100_000.0))
        assertEquals(listOf("largest", "a", "b", "c"), data.citiesIn("GRC").map { it.id })
    }

    @Test fun legacyLinksResolveWithoutRemovingUnmatchedHistoricalPlaces() {
        val centre = city("ghsl-r2024a-854", "Athens")
        val data = catalog(centre, city("old-piraeus", kind = CityKind.LEGACY, successor = centre.id),
            city("unmatched", kind = CityKind.LEGACY))
        assertEquals(centre, data.cityForSavedID("old-piraeus"))
        assertEquals(setOf(centre.id, "old-piraeus"), data.visitIDs(centre))
        assertEquals("unmatched", data.cityForSavedID("unmatched")?.id)
        assertNull(data.cityForSavedID("unknown"))
        assertEquals(listOf(centre), data.selectableCities)
    }

    @Test fun searchUsesGreekAliasesAndIgnoresBrowsePopulationThreshold() {
        val athens = city("athens", "Athens", population = 3_166_757.0, aliases = listOf("Αθήνα", "Πειραιάς"))
        val chania = city("chania", "Chania", aliases = listOf("Χανιά"))
        val data = catalog(athens, chania, city("legacy", "Athens", kind = CityKind.LEGACY))
        assertEquals(listOf(athens), data.search("  ΑΘΗΝΑ  ").cities)
        assertEquals(listOf(athens), data.search("πειραιας").cities)
        assertEquals(listOf(greece), data.search("ελλαδα").countries)
        assertEquals(listOf(athens), data.browseCities("GRC", 1_000_000))
        assertEquals(listOf(chania), data.search("χανια Greece").cities)
        assertEquals(emptyList<AtlasCity>(), data.search("").cities)
    }
}
