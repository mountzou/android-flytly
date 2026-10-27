package com.mountzou.flytly.atlas

/** Public atlas data can stay indexed on disk; fixtures retain the in-memory implementation. */
internal interface AtlasCityStorage {
    val cities: List<AtlasCity>
    val selectableCities: List<AtlasCity>
    fun citiesIn(countryID: String): List<AtlasCity>
    fun resolve(id: String): AtlasCity?
    fun visitIDs(city: AtlasCity): Set<String>
    fun search(normalized: String, words: List<String>, limit: Int): List<AtlasCity>
    fun labelsIn(south: Double, north: Double, west: Double, east: Double, zoom: Double): List<AtlasCity>
}
