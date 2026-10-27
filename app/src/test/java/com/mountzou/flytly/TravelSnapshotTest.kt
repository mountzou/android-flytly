package com.mountzou.flytly

import com.mountzou.flytly.atlas.*
import org.junit.Assert.*
import org.junit.Test

class TravelSnapshotTest {
    private val athens = AtlasCity("new-athens", "Athens", "GRC", 37.98, 23.72, 3_000_000.0, true, CityKind.URBAN_CENTER)
    private val previous = AtlasCity("old-athens", "Athens", "GRC", 37.98, 23.72, 3_000_000.0, true, CityKind.LEGACY, urbanCenterID = athens.id)
    private val thessaloniki = AtlasCity("thessaloniki", "Thessaloniki", "GRC", 40.6, 22.9, 800_000.0, false, CityKind.URBAN_CENTER)
    private val france = AtlasCountry("FRA", "France", "Europe")
    private val atlas = AtlasCatalog(listOf(AtlasCountry("GRC", "Greece", "Europe"), france), listOf(athens, previous, thessaloniki), emptyMap())

    @Test fun legacyRecordsCountTheSuccessorOnce() {
        val records = TravelSnapshot(mapOf("city:old-athens" to PlaceStatus.VISITED, "city:new-athens" to PlaceStatus.VISITED))
        assertEquals(listOf(athens), records.cities(PlaceStatus.VISITED, atlas))
        assertEquals(setOf("GRC"), records.countryIDs(PlaceStatus.VISITED, atlas))
    }
    @Test fun canonicalRemovalMasksAnOldVisit() {
        val records = TravelSnapshot(mapOf("city:old-athens" to PlaceStatus.VISITED, "city:new-athens" to PlaceStatus.REMOVED))
        assertEquals(PlaceStatus.REMOVED, records.status(athens, atlas))
        assertTrue(records.countryIDs(PlaceStatus.VISITED, atlas).isEmpty())
    }
    @Test fun switchingStatusUpdatesAllKnownIDsAndRemainsExclusive() {
        val records = TravelSnapshot(mapOf("city:old-athens" to PlaceStatus.VISITED))
            .withStatus(athens, "GRC", PlaceStatus.WISHLIST, atlas)
        assertEquals(PlaceStatus.WISHLIST, records.records["city:old-athens"])
        assertEquals(PlaceStatus.WISHLIST, records.records["city:new-athens"])
        assertTrue(records.cities(PlaceStatus.VISITED, atlas).isEmpty())
        assertEquals(setOf("GRC"), records.countryIDs(PlaceStatus.WISHLIST, atlas))
    }
    @Test fun countryCanContainVisitedAndWishlistedPlacesIndependently() {
        val records = TravelSnapshot().withStatus(athens, "GRC", PlaceStatus.VISITED, atlas)
            .withStatus(thessaloniki, "GRC", PlaceStatus.WISHLIST, atlas)
        assertEquals(setOf("GRC"), records.countryIDs(PlaceStatus.VISITED, atlas))
        assertEquals(setOf("GRC"), records.countryIDs(PlaceStatus.WISHLIST, atlas))
    }
    @Test fun otherPlaceAndCityVisitsDeduplicateCountries() {
        val records = TravelSnapshot().withStatus(null, "GRC", PlaceStatus.VISITED, atlas)
            .withStatus(athens, "GRC", PlaceStatus.VISITED, atlas).withStatus(null, "FRA", PlaceStatus.WISHLIST, atlas)
        assertEquals(setOf("GRC"), records.countryIDs(PlaceStatus.VISITED, atlas))
        assertEquals(setOf("FRA"), records.countryIDs(PlaceStatus.WISHLIST, atlas))
    }
    @Test fun continentCountsFollowTheSixIOSRegions() {
        assertEquals("Americas", AtlasCountry("USA", "United States", "North America").travelContinent())
        assertEquals("Americas", AtlasCountry("CHL", "Chile", "South America").travelContinent())
        assertEquals("Africa", AtlasCountry("MUS", "Mauritius", "Seven seas (open ocean)").travelContinent())
        assertEquals("Antarctica", AtlasCountry("ATA", "Antarctica", "Antarctica").travelContinent())
    }
}
