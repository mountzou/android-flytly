package com.mountzou.flytly.atlas

import java.text.NumberFormat
import java.util.Locale

internal val travelContinents = listOf("Europe", "Asia", "Americas", "Africa", "Oceania", "Antarctica")

/** Same aggregation rules as iOS PlacesCollection, including the unlisted-place bucket. */
internal class PlaceCollection(atlas: AtlasCatalog, travels: TravelSnapshot, val status: PlaceStatus) {
    val cities = travels.cities(status, atlas)
    val countries = travels.countryIDs(status, atlas).mapNotNull(atlas.countriesByID::get).sortedBy { it.name }
    private val otherCountries = travels.records.filter { (key, value) -> key.startsWith("country:") && value == status }
        .keys.map { it.removePrefix("country:") }.toSet()
    val firstContinent get() = travelContinents.firstOrNull { countriesIn(it).isNotEmpty() }
    fun countriesIn(continent: String) = countries.filter { it.travelContinent() == continent }
    fun cityCount(countryID: String) = cities.count { it.countryID == countryID } + if (countryID in otherCountries) 1 else 0
    fun countryDetail(countryID: String): String {
        val count = cityCount(countryID)
        return "$count ${if (count == 1) "city" else "cities"} ${if (status == PlaceStatus.VISITED) "visited" else "saved"}"
    }
    fun continentSummary(continent: String): String {
        val countries = countriesIn(continent)
        if (status == PlaceStatus.VISITED) return if (countries.isEmpty()) "Not visited yet"
            else "${countries.size} ${if (countries.size == 1) "country" else "countries"} visited"
        val count = countries.sumOf { cityCount(it.id) }
        val countryText = "${countries.size} ${if (countries.size == 1) "country" else "countries"}"
        val cityText = "$count ${if (count == 1) "city" else "cities"}"
        return (if (countries.isEmpty()) cityText else if (count == 0) countryText else "$countryText, $cityText") + " saved"
    }
}

internal fun AtlasCatalog.recordedCities(countryID: String, travels: TravelSnapshot) =
    (travels.cities(PlaceStatus.VISITED, this) + travels.cities(PlaceStatus.WISHLIST, this))
        .filter { it.countryID == countryID }.distinctBy { it.id }.sortedWith(compareBy<AtlasCity> { it.name }.thenBy { it.id })

internal fun AtlasCatalog.populationRank(city: AtlasCity): String? {
    val available = citiesIn(city.countryID)
    if (available.none { it.id == city.id }) return null
    val rank = 1 + available.count { it.population > city.population }
    val suffix = if (rank % 100 in 11..13) "th" else when (rank % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return NumberFormat.getIntegerInstance(Locale.US).format(rank) + suffix
}

internal fun populationDescription(population: Double): String {
    val millions = population >= 1_000_000
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = if (millions) 1 else 0 }
    return formatter.format(if (millions) population / 1_000_000 else population) + if (millions) " million" else ""
}

internal data class PlaceChange(val before: TravelSnapshot, val after: TravelSnapshot)
internal class PlaceChangeConflictException : IllegalStateException("Your places have changed. Please try again; this Undo is no longer available.")
