package com.mountzou.flytly.location

import java.time.ZoneId

internal object LocationZoneCache {
    private data class Entry(val coordinate: Coordinate, val zone: ZoneId, val date: Long)
    private var origin: Entry? = null
    private val destinations = LinkedHashMap<String, Entry>()
    @Synchronized fun origin(at: Coordinate, now: Long = System.currentTimeMillis()): ZoneId? = origin?.takeIf {
        now - it.date in 0 until 900_000 && distanceMeters(it.coordinate, at) < 100
    }?.zone
    @Synchronized fun storeOrigin(at: Coordinate, zone: ZoneId, now: Long = System.currentTimeMillis()) { origin = Entry(at, zone, now) }
    @Synchronized fun clearOrigin() { origin = null }
    @Synchronized fun destination(id: String, at: Coordinate, now: Long = System.currentTimeMillis()): ZoneId? = destinations[id]?.takeIf {
        it.coordinate == at && now - it.date in 0 until 86_400_000
    }?.zone
    @Synchronized fun storeDestination(id: String, at: Coordinate, zone: ZoneId, now: Long = System.currentTimeMillis()) {
        if (id !in destinations && destinations.size >= 256) destinations.remove(destinations.minBy { it.value.date }.key)
        destinations[id] = Entry(at, zone, now)
    }
}
