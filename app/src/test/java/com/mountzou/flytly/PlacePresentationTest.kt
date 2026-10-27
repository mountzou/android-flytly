package com.mountzou.flytly

import com.mountzou.flytly.atlas.*
import com.mountzou.flytly.account.AccountCache
import org.junit.Assert.*
import org.junit.Test

class PlacePresentationTest {
    private val tokyo = AtlasCity("tokyo", "Tokyo", "JPN", 35.0, 139.0, 10_000_000.0, true, CityKind.URBAN_CENTER)
    private val athens = AtlasCity("athens", "Athens", "GRC", 38.0, 23.0, 3_000_000.0, true, CityKind.URBAN_CENTER)
    private val tied = athens.copy(id = "equal", name = "Equal estimate")
    private val chania = athens.copy(id = "chania", name = "Chania", population = 70_000.0)
    private val legacy = athens.copy(id = "legacy", kind = CityKind.LEGACY, urbanCenterID = "athens")
    private val atlas = AtlasCatalog(listOf(AtlasCountry("JPN", "Japan", "Asia"), AtlasCountry("GRC", "Greece", "Europe")), listOf(tokyo, athens, tied, chania, legacy), emptyMap())

    @Test fun firstAvailableContinentFollowsSavedPlacesAndCountryBucketsCountAsOneCity() {
        val data = TravelSnapshot(mapOf("city:tokyo" to PlaceStatus.VISITED, "country:JPN" to PlaceStatus.VISITED))
        val collection = PlaceCollection(atlas, data, PlaceStatus.VISITED)
        assertEquals("Asia", collection.firstContinent)
        assertEquals("1 country visited", collection.continentSummary("Asia"))
        assertEquals("2 cities visited", collection.countryDetail("JPN"))
        assertEquals("Not visited yet", collection.continentSummary("Europe"))
    }
    @Test fun wishlistSummaryDeduplicatesLegacyIDsAndIncludesOtherWithoutCountingVisits() {
        val data = TravelSnapshot(mapOf("city:athens" to PlaceStatus.WISHLIST, "city:legacy" to PlaceStatus.WISHLIST,
            "country:GRC" to PlaceStatus.WISHLIST, "city:chania" to PlaceStatus.VISITED))
        val collection = PlaceCollection(atlas, data, PlaceStatus.WISHLIST)
        assertEquals("1 country, 2 cities saved", collection.continentSummary("Europe"))
        assertEquals("2 cities saved", collection.countryDetail("GRC"))
        assertEquals("0 cities saved", collection.continentSummary("Asia"))
    }
    @Test fun recordedCitiesCombineBothCollectionsInNameOrderWithoutLegacyDuplicates() {
        val data = TravelSnapshot(mapOf("city:legacy" to PlaceStatus.VISITED, "city:athens" to PlaceStatus.VISITED, "city:chania" to PlaceStatus.WISHLIST))
        assertEquals(listOf("athens", "chania"), atlas.recordedCities("GRC", data).map { it.id })
    }
    @Test fun tiedPopulationRanksUseTheFullCountryCatalog() {
        assertEquals("1st", atlas.populationRank(athens))
        assertEquals("1st", atlas.populationRank(tied))
        assertEquals("3rd", atlas.populationRank(chania))
        assertNull(atlas.populationRank(legacy))
        assertEquals("3 million", populationDescription(athens.population))
        assertEquals("70,000", populationDescription(chania.population))
    }
    @Test fun staleUndoCannotOverwriteNewerLocalOrRemotePlaces() {
        val original = AccountCache(places = mapOf("city:chania" to PlaceStatus.WISHLIST))
        val changed = original.editPlace("city:chania", PlaceStatus.VISITED, original.visiblePlaces())
        val expected = changed.visiblePlaces()
        val newer = changed.copy(places = changed.places + ("city:tokyo" to PlaceStatus.VISITED))
        assertThrows(PlaceChangeConflictException::class.java) { newer.editPlace("city:chania", PlaceStatus.WISHLIST, expected) }
        val restored = changed.editPlace("city:chania", PlaceStatus.WISHLIST, expected)
        assertEquals(PlaceStatus.WISHLIST, restored.visiblePlaces().records["city:chania"])
    }
}
