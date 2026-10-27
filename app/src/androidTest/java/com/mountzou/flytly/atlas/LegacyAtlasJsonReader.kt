package com.mountzou.flytly.atlas

import android.content.res.AssetManager
import android.util.JsonReader
import android.util.JsonToken

/** Original runtime reader retained only as an independent lossless parity oracle. */
internal object LegacyAtlasJsonReader {
    private fun reader(assets: AssetManager, name: String) =
        JsonReader(assets.open("atlas/$name").bufferedReader())

    fun read(assets: AssetManager): AtlasCatalog {
        val countries = mutableListOf<AtlasCountry>()
        reader(assets, "countries.geojson").use { json ->
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() == "features") {
                    json.beginArray()
                    while (json.hasNext()) {
                        json.beginObject()
                        while (json.hasNext()) {
                            if (json.nextName() == "properties") {
                                var id = ""; var name = ""; var continent = ""
                                var aliases = emptyList<String>()
                                json.beginObject()
                                while (json.hasNext()) when (json.nextName()) {
                                    "id" -> id = json.nextString()
                                    "name" -> name = json.nextString()
                                    "continent" -> continent = json.nextString()
                                    "aliases" -> aliases = json.strings()
                                    else -> json.skipValue()
                                }
                                json.endObject()
                                require(id.isNotEmpty() && name.isNotEmpty()) { "Invalid country metadata" }
                                countries += AtlasCountry(id, name, continent, aliases)
                            } else json.skipValue()
                        }
                        json.endObject()
                    }
                    json.endArray()
                } else json.skipValue()
            }
            json.endObject()
        }
        val cities = mutableListOf<AtlasCity>()
        reader(assets, "cities.json").use { json ->
            json.beginArray()
            while (json.hasNext()) {
                var id = ""; var name = ""; var country = ""
                var latitude = Double.NaN; var longitude = Double.NaN; var population = 0.0
                var capital = false; var kind = CityKind.LEGACY
                var aliases = emptyList<String>(); var successor: String? = null; var year: Int? = null
                json.beginObject()
                while (json.hasNext()) when (json.nextName()) {
                    "id" -> id = json.nextString()
                    "name" -> name = json.nextString()
                    "countryID" -> country = json.nextString()
                    "latitude" -> latitude = json.nextDouble()
                    "longitude" -> longitude = json.nextDouble()
                    "population" -> population = json.nextDouble()
                    "isCapital" -> capital = json.nextBoolean()
                    "aliases" -> aliases = json.strings()
                    "urbanCenterID" -> if (json.peek() == JsonToken.NULL) json.nextNull() else successor = json.nextString()
                    "populationYear" -> if (json.peek() == JsonToken.NULL) json.nextNull() else year = json.nextInt()
                    "catalogKind" -> kind = when (val value = json.nextString()) {
                        "urbanCenter" -> CityKind.URBAN_CENTER
                        "capitalFallback" -> CityKind.CAPITAL_FALLBACK
                        "legacy" -> CityKind.LEGACY
                        else -> error("Unknown city catalog kind: $value")
                    }
                    else -> json.skipValue()
                }
                json.endObject()
                require(id.isNotEmpty() && name.isNotEmpty() && latitude.isFinite() && longitude.isFinite()) {
                    "Invalid city metadata"
                }
                cities += AtlasCity(id, name, country, latitude, longitude, population, capital, kind, aliases, successor, year)
            }
            json.endArray()
        }
        val labels = mutableMapOf<String, CountryLabel>()
        reader(assets, "country-labels.json").use { json ->
            json.beginArray()
            while (json.hasNext()) {
                var id = ""; var latitude = 0.0; var longitude = 0.0; var minimum = 3.0; var maximum = 7.0
                var name = ""; var rank = 3
                json.beginObject()
                while (json.hasNext()) when (json.nextName()) {
                    "countryID" -> id = json.nextString()
                    "latitude" -> latitude = json.nextDouble()
                    "longitude" -> longitude = json.nextDouble()
                    "minimumZoom" -> minimum = json.nextDouble()
                    "maximumZoom" -> maximum = json.nextDouble()
                    "name" -> name = json.nextString()
                    "rank" -> rank = json.nextInt()
                    else -> json.skipValue()
                }
                json.endObject()
                labels[id] = CountryLabel(id, latitude, longitude, minimum, maximum, name, rank)
            }
            json.endArray()
        }
        require(countries.size == countries.distinctBy { it.id }.size && countries.isNotEmpty())
        require(cities.size == cities.distinctBy { it.id }.size && cities.isNotEmpty())
        val countryIDs = countries.mapTo(mutableSetOf()) { it.id }
        require(cities.all { it.countryID in countryIDs }) { "City has an unknown country" }
        return AtlasCatalog(countries, cities, labels)
    }

    private fun JsonReader.strings(): List<String> = buildList {
        beginArray()
        while (hasNext()) add(nextString())
        endArray()
    }
}
