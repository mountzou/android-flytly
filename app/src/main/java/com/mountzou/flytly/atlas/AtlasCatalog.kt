package com.mountzou.flytly.atlas

import java.text.Normalizer
import java.util.Locale

data class AtlasCountry(
    val id: String,
    val name: String,
    val continent: String,
    val aliases: List<String> = emptyList(),
    val searchText: String = (listOf(name) + aliases).joinToString(" ").searchNormalized()
)

enum class CityKind { URBAN_CENTER, CAPITAL_FALLBACK, LEGACY }

data class AtlasCity(
    val id: String,
    val name: String,
    val countryID: String,
    val latitude: Double,
    val longitude: Double,
    val population: Double,
    val isCapital: Boolean,
    val kind: CityKind,
    val aliases: List<String> = emptyList(),
    val urbanCenterID: String? = null,
    val populationYear: Int? = null,
    val searchText: String = (listOf(name) + aliases).joinToString(" ").searchNormalized()
)

data class CountryLabel(
    val countryID: String,
    val latitude: Double,
    val longitude: Double,
    val minimumZoom: Double,
    val maximumZoom: Double = 7.0,
    val name: String = "",
    val rank: Int = 3
)

data class AtlasSearchResults(val countries: List<AtlasCountry>, val cities: List<AtlasCity>)

class AtlasCatalog private constructor(
    countries: List<AtlasCountry>,
    val cities: List<AtlasCity>,
    val labels: Map<String, CountryLabel>,
    private val storage: AtlasCityStorage?
) {
    constructor(countries: List<AtlasCountry>, cities: List<AtlasCity>, labels: Map<String, CountryLabel>) : this(countries, cities, labels, null)
    companion object {
        internal fun indexed(countries: List<AtlasCountry>, labels: Map<String, CountryLabel>, storage: AtlasCityStorage) =
            AtlasCatalog(countries, storage.cities, labels, storage)
    }
    val countries = countries.sortedBy { it.name }
    val countriesByID = this.countries.associateBy { it.id }
    private val citiesByID by lazy { cities.associateBy { it.id } }
    private val predecessors by lazy { cities.filter { it.urbanCenterID != null }.groupBy { it.urbanCenterID!! } }
    private val populationOrder = compareByDescending<AtlasCity> { it.population }
        .thenBy { it.name }.thenBy { it.id }
    private val selectableByCountry by lazy { cities.groupBy { it.countryID }.mapValues { (_, records) ->
        val centres = records.filter { it.kind == CityKind.URBAN_CENTER && it.population > 50_000 }
        (centres.ifEmpty { records.filter { it.kind == CityKind.CAPITAL_FALLBACK } })
            .sortedWith(populationOrder)
    } }
    val selectableCities: List<AtlasCity> by lazy { storage?.selectableCities ?: selectableByCountry.values.flatten().sortedWith(populationOrder) }

    fun citiesIn(countryID: String): List<AtlasCity> = storage?.citiesIn(countryID) ?: selectableByCountry[countryID].orEmpty()

    // The default country list can be filtered; search always covers the full selectable catalog.
    fun browseCities(countryID: String, minimumPopulation: Int): List<AtlasCity> =
        citiesIn(countryID).filter { it.kind == CityKind.CAPITAL_FALLBACK || it.population > minimumPopulation }

    fun cityForSavedID(id: String): AtlasCity? {
        if (storage != null) return storage.resolve(id)
        return citiesByID[id]?.let { city -> city.urbanCenterID?.let(citiesByID::get) ?: city }
    }

    fun visitIDs(city: AtlasCity): Set<String> =
        storage?.visitIDs(city) ?: predecessors[city.id].orEmpty().mapTo(mutableSetOf(city.id)) { it.id }

    fun search(query: String, limit: Int = 80): AtlasSearchResults {
        val normalized = query.searchNormalized()
        val words = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return AtlasSearchResults(countries, emptyList())
        val matchingCountries = countries.filter { country -> words.all { it in country.searchText } }
        if (storage != null && words.size <= 64) return AtlasSearchResults(matchingCountries, storage.search(normalized, words, limit))
        val matchingCities = selectableCities.asSequence().filter { city ->
            val country = countriesByID[city.countryID]
            words.all { it in city.searchText || country?.searchText?.contains(it) == true }
        }.sortedWith(compareBy<AtlasCity> {
            if (it.name.searchNormalized().startsWith(normalized)) 0 else 1
        }.then(populationOrder)).take(limit).toList()
        return AtlasSearchResults(matchingCountries, matchingCities)
    }

    internal fun labelsIn(south: Double, north: Double, west: Double, east: Double, zoom: Double): List<AtlasCity> {
        storage?.let { return it.labelsIn(south, north, west, east, zoom) }
        return selectableCities.sortedWith(compareByDescending<AtlasCity> { it.isCapital }.thenByDescending { it.population })
            .filter { city -> city.latitude in south..north && (east - west >= 360 || if (west <= east) city.longitude in west..east else city.longitude >= west || city.longitude <= east) && zoom >= when {
                city.isCapital -> 5.0; city.population >= 1_000_000 -> 6.0; city.population >= 250_000 -> 7.0; else -> 8.0
            } }
    }
}

private val combiningMarks = Regex("\\p{M}+")
fun String.searchNormalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(combiningMarks, "").lowercase(Locale.ROOT).trim()

enum class AtlasRegion(val title: String, val latitude: Double, val longitude: Double) {
    EUROPE("Europe", 49.0, 12.0), ASIA("Asia", 32.0, 105.0),
    AMERICAS("Americas", 25.0, -90.0), AFRICA("Africa", 2.0, 22.0),
    OCEANIA("Oceania", -25.0, 140.0)
}
