package com.mountzou.flytly.location

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.*

internal data class Coordinate(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
}

internal data class LocationFix(val coordinate: Coordinate, val timestampMillis: Long, val accuracyMeters: Float) {
    fun isFresh(now: Long) = accuracyMeters.isFinite() && accuracyMeters >= 0 && abs(now - timestampMillis) < 300_000
}

/** WGS84 inverse geodesic, matching CLLocation's ellipsoidal straight-line distance. */
internal fun distanceMeters(from: Coordinate, to: Coordinate): Double {
    val a = 6378137.0; val f = 1 / 298.257223563; val b = a * (1 - f)
    val u1 = atan((1 - f) * tan(Math.toRadians(from.latitude)))
    val u2 = atan((1 - f) * tan(Math.toRadians(to.latitude)))
    val l = Math.toRadians(to.longitude - from.longitude)
    var lambda = l
    var sigma = 0.0; var sinSigma = 0.0; var cosSigma = 0.0; var cosSqAlpha = 0.0; var cos2SigmaM = 0.0
    var converged = false
    for (i in 0 until 100) {
        sinSigma = hypot(cos(u2) * sin(lambda), cos(u1) * sin(u2) - sin(u1) * cos(u2) * cos(lambda))
        if (sinSigma == 0.0) return 0.0
        cosSigma = sin(u1) * sin(u2) + cos(u1) * cos(u2) * cos(lambda)
        sigma = atan2(sinSigma, cosSigma)
        val sinAlpha = cos(u1) * cos(u2) * sin(lambda) / sinSigma
        cosSqAlpha = 1 - sinAlpha * sinAlpha
        cos2SigmaM = if (cosSqAlpha > 1e-15) cosSigma - 2 * sin(u1) * sin(u2) / cosSqAlpha else 0.0
        val c = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
        val previous = lambda
        lambda = l + (1 - c) * f * sinAlpha * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        if (abs(lambda - previous) < 1e-12) { converged = true; break }
    }
    if (!converged) {
        // Near antipodes Vincenty does not converge; the stable great-circle
        // fallback keeps an approximate intercontinental distance finite.
        val phi1 = Math.toRadians(from.latitude); val phi2 = Math.toRadians(to.latitude)
        val h = sin((phi2 - phi1) / 2).pow(2) + cos(phi1) * cos(phi2) * sin(l / 2).pow(2)
        return 6371008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
    val aa = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
    val bb = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
    val delta = bb * sinSigma * (cos2SigmaM + bb / 4 * (cosSigma * (-1 + 2 * cos2SigmaM.pow(2)) - bb / 6 * cos2SigmaM * (-3 + 4 * sinSigma.pow(2)) * (-3 + 4 * cos2SigmaM.pow(2))))
    return b * aa * (sigma - delta)
}

internal fun distanceText(meters: Double): String {
    require(meters.isFinite())
    val km = max(0.0, meters / 1000)
    if (km < 1) return "< 1 km"
    val step = if (km >= 100) 10.0 else 1.0
    val rounded = floor(km / step + .5) * step
    return "≈ ${NumberFormat.getIntegerInstance(Locale.US).format(rounded)} km"
}

internal fun differenceText(origin: ZoneId, destination: ZoneId, instant: Instant): String {
    val minutes = (destination.rules.getOffset(instant).totalSeconds - origin.rules.getOffset(instant).totalSeconds) / 60
    if (minutes == 0) return "Same time"
    val hours = abs(minutes) / 60; val remainder = abs(minutes) % 60
    val amount = when { remainder == 0 -> "$hours ${if (hours == 1) "hour" else "hours"}"; hours == 0 -> "$remainder min"; else -> "$hours h $remainder min" }
    return "$amount ${if (minutes > 0) "ahead" else "behind"}"
}
