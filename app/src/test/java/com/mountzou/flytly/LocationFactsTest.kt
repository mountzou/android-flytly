package com.mountzou.flytly

import com.mountzou.flytly.location.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.DeflaterOutputStream

class LocationFactsTest {
    private val paris = Coordinate(48.8566, 2.3522)
    private val athens = Coordinate(37.98, 23.72)
    @Test fun ellipsoidalDistanceRoundingAndInputValidation() {
        val distance = distanceMeters(Coordinate(51.5074, -.1278), paris)
        assertEquals(343_923.0, distance, 1_500.0)
        assertEquals("≈ 340 km", distanceText(distance))
        assertEquals("< 1 km", distanceText(999.0))
        assertEquals("< 1 km", distanceText(-1.0))
        assertEquals("≈ 2 km", distanceText(1500.0))
        assertEquals("≈ 2,100 km", distanceText(2_103_000.0))
        assertEquals(0.0, distanceMeters(paris, paris), 0.0)
        assertTrue(distanceMeters(Coordinate(0.0, 0.0), Coordinate(0.0, 180.0)).isFinite())
        assertThrows(IllegalArgumentException::class.java) { Coordinate(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Coordinate(91.0, 0.0) }
    }
    @Test fun differenceDirectionFractionalZonesAndDifferentDstDates() {
        fun difference(origin: String, destination: String, date: String = "2026-01-15T12:00:00Z") = differenceText(ZoneId.of(origin), ZoneId.of(destination), Instant.parse(date))
        assertEquals("Same time", difference("Europe/Paris", "Europe/Paris"))
        assertEquals("1 hour behind", difference("Europe/Athens", "Europe/Paris"))
        assertEquals("1 hour ahead", difference("Europe/Paris", "Europe/Athens"))
        assertEquals("3 h 45 min ahead", difference("Europe/Athens", "Asia/Kathmandu"))
        assertEquals("3 h 30 min ahead", difference("Europe/Athens", "Asia/Kolkata"))
        assertEquals("15 min behind", difference("Asia/Kathmandu", "Asia/Kolkata"))
        assertEquals("6 hours behind", difference("Europe/Paris", "America/New_York"))
        assertEquals("5 hours behind", difference("Europe/Paris", "America/New_York", "2026-03-15T12:00:00Z"))
        assertEquals("6 hours behind", difference("Europe/Paris", "America/New_York", "2026-07-15T12:00:00Z"))
    }
    @Test fun cachesKeepZoneIdentitiesExpireAndRejectMovementOrChangedCityCoordinates() {
        val zone = ZoneId.of("Europe/Athens")
        LocationZoneCache.clearOrigin()
        LocationZoneCache.storeOrigin(athens, zone, 1000)
        assertEquals(zone, LocationZoneCache.origin(athens, 1001))
        assertNull(LocationZoneCache.origin(paris, 1001))
        assertNull(LocationZoneCache.origin(athens, 901000))
        LocationZoneCache.storeDestination("test-athens", athens, zone, 1000)
        assertNull(LocationZoneCache.destination("test-athens", paris, 1001))
        assertNull(LocationZoneCache.destination("test-athens", athens, 86_401_000))
        val cached = LocationZoneCache.destination("test-athens", athens, 1001)!!
        assertEquals(7200, cached.rules.getOffset(Instant.parse("2026-01-15T12:00:00Z")).totalSeconds)
        assertEquals(10800, cached.rules.getOffset(Instant.parse("2026-07-15T12:00:00Z")).totalSeconds)
        LocationZoneCache.clearOrigin()
    }
    @Test fun fixesRejectStaleFutureAndInvalidAccuracy() {
        assertTrue(LocationFix(paris, 1_000_000, 1000f).isFresh(1_000_001))
        assertFalse(LocationFix(paris, 1_000_000, -1f).isFresh(1_000_001))
        assertFalse(LocationFix(paris, 1_000_000, Float.NaN).isFresh(1_000_001))
        assertFalse(LocationFix(paris, 1_000_000, 10f).isFresh(1_300_000))
        assertFalse(LocationFix(paris, 2_000_000, 10f).isFresh(1_000_000))
    }
    @Test fun foregroundPausePreservesFixAndRevocationClearsItAndCancelsRequest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var waiting = false; var cancelled = false
        val source = object : DeviceLocationSource {
            var calls = 0
            override suspend fun current(): LocationFix? {
                calls++
                if (calls == 1) return LocationFix(paris, System.currentTimeMillis(), 1000f)
                waiting = true
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val controller = CityLocationController(source, scope)
        controller.start(); assertEquals(0, source.calls)
        controller.authorization(LocationAuthorization.GRANTED)
        assertNotNull(controller.fix)
        controller.stop(); assertNotNull(controller.fix)
        controller.start(); assertTrue(waiting)
        controller.authorization(LocationAuthorization.DENIED)
        assertTrue(cancelled); assertNull(controller.fix); assertFalse(controller.failed)
        scope.cancel()
    }
    @Test fun binaryPolygonsRespectHolesAndRejectOutsidePoints() {
        fun ring(vararg points: Pair<Double, Double>) = points.toList()
        val rings = listOf(ring(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0, 0.0 to 0.0),
            ring(4.0 to 4.0, 6.0 to 4.0, 6.0 to 6.0, 4.0 to 6.0, 4.0 to 4.0))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(DeflaterOutputStream(bytes)).use { out ->
            out.writeInt(rings.size)
            rings.forEach { r ->
                out.writeInt(r.size); var x = 0L; var y = 0L
                r.forEach { (lon, lat) -> val xx = (lon * 10_000_000).toLong(); val yy = (lat * 10_000_000).toLong(); out.writeLong(xx - x); out.writeLong(yy - y); x = xx; y = yy }
            }
        }
        assertTrue(OfflineTimeZones.contains(bytes.toByteArray(), Coordinate(2.0, 2.0)))
        assertFalse(OfflineTimeZones.contains(bytes.toByteArray(), Coordinate(5.0, 5.0)))
        assertFalse(OfflineTimeZones.contains(bytes.toByteArray(), Coordinate(11.0, 5.0)))
        assertTrue(OfflineTimeZones.contains(bytes.toByteArray(), Coordinate(0.0, 2.0)))
    }
}
