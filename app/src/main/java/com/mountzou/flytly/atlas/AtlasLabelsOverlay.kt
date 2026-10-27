package com.mountzou.flytly.atlas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.mountzou.flytly.R
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.ceil

/** Native Android shaping supplies system fallbacks for every script in the source catalog.
 * Mirrors the iOS overlay: zoom thresholds, capital priority, collision checks and a 24-city cap.
 * No glyph URL or online font lookup is involved.
 */
internal class AtlasLabelsOverlay(context: Context, private val catalog: AtlasCatalog) : View(context) {
    var map: MapLibreMap? = null
    var countryNames = true
    var cityNames = true
    var dark = false
        set(value) { field = value; updatePaints(); invalidate() }
    var selectedCity: AtlasCity? = null
    var visitedCities: List<AtlasCity> = emptyList()
    var visitedCountryIDs: Set<String> = emptySet()
    data class Hit(val countryID: String, val cityID: String? = null)
    private val hits = mutableListOf<Pair<RectF, Hit>>()
    private val occupied = mutableListOf<RectF>()
    private val density = resources.displayMetrics.density
    private val countryPaint = paint(10.5f)
    private val cityPaint = paint(11f)
    private val layouts = LruCache<String, StaticLayout>(256)
    private val countryCandidates = catalog.labels.values.sortedBy { it.rank }
        .map { it to LatLng(it.latitude, it.longitude) }
    private data class LabelWindow(val south: Double, val north: Double, val west: Double, val east: Double, val bucket: Int)
    private var labelWindow: LabelWindow? = null
    private var cityCandidates: List<AtlasCity> = emptyList()

    // A padded viewport avoids querying on every animation frame; no worldwide city decoding.
    private fun candidates(visible: LatLngBounds, zoom: Double): List<AtlasCity> {
        val bucket = zoom.toInt().coerceAtMost(8)
        val south = visible.latitudeSouth; val north = visible.latitudeNorth
        val west = visible.longitudeWest; val east = visible.longitudeEast
        val previous = labelWindow
        val covered = previous != null && previous.bucket == bucket && south >= previous.south && north <= previous.north &&
            (previous.east - previous.west >= 360 || west <= east && west >= previous.west && east <= previous.east)
        if (!covered) {
            val latitudePadding = (north - south) * .25
            val longitudePadding = if (east >= west) (east - west) * .25 else 180.0
            val allLongitudes = west - longitudePadding < -180 || east + longitudePadding > 180 || west > east
            val window = LabelWindow((south - latitudePadding).coerceAtLeast(-90.0), (north + latitudePadding).coerceAtMost(90.0),
                if (allLongitudes) -180.0 else west - longitudePadding, if (allLongitudes) 180.0 else east + longitudePadding, bucket)
            cityCandidates = catalog.labelsIn(window.south, window.north, window.west, window.east, bucket.toDouble())
            labelWindow = window
        }
        return cityCandidates
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        updatePaints()
    }

    private fun paint(size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
        typeface = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_medium)
    }

    fun hitAt(x: Float, y: Float): Hit? = hits.asReversed().firstOrNull { it.first.contains(x, y) }?.second

    private fun updatePaints() {
        countryPaint.color = Color.parseColor(if (dark) "#B8B8B8" else "#606267")
        cityPaint.color = Color.parseColor(if (dark) "#F2F2F2" else "#111111")
        val paper = Color.parseColor(if (dark) "#111111" else "#FBFCFD")
        countryPaint.setShadowLayer(1.2f * density, 0f, 0f, paper)
        cityPaint.setShadowLayer(1.5f * density, 0f, 0f, paper)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hits.clear()
        occupied.clear()
        val map = map ?: return
        if (width == 0 || height == 0 || map.style == null) return
        val projection = map.projection
        // Calibrated against the iOS viewport (MapKit's expanded rendering surface
        // and 256-point label convention). The native camera keeps its own scale.
        val zoom = map.cameraPosition.zoom + 2
        val visible = projection.visibleRegion.latLngBounds
        occupied += RectF(0f, height - 160 * density, 110 * density, height.toFloat())
        occupied += RectF(width - 92 * density, height - 180 * density, width.toFloat(), height.toFloat())

        selectedCity?.let { city ->
            place(canvas, city.name, projection.toScreenLocation(LatLng(city.latitude, city.longitude)),
                isCity = true, hit = Hit(city.countryID, city.id), fitCountry = false)
        }
        visitedCities.filter { it.id != selectedCity?.id }.forEach { city ->
            val coordinate = LatLng(city.latitude, city.longitude)
            if (visible.contains(coordinate)) place(canvas, city.name, projection.toScreenLocation(coordinate),
                true, Hit(city.countryID, city.id), false)
        }
        if (countryNames) countryCandidates.forEach { (label, coordinate) ->
            if (zoom in label.minimumZoom..label.maximumZoom && visible.contains(coordinate)) {
                val name = label.name.ifEmpty { catalog.countriesByID[label.countryID]?.name.orEmpty() }
                place(canvas, name, projection.toScreenLocation(coordinate), isCity = false,
                    hit = Hit(label.countryID), fitCountry = false)
            }
        }
        if (cityNames && zoom >= 5) {
            val areaPerLabel = if (zoom < 6) 28_000 else if (zoom < 7) 22_000 else 16_000
            val limit = ((width / density) * (height / density) / areaPerLabel).toInt().coerceIn(0, 24)
            var count = 0
            for (city in candidates(visible, zoom)) {
                if (count >= limit) break
                val coordinate = LatLng(city.latitude, city.longitude)
                if (city.id != selectedCity?.id && visible.contains(coordinate)) {
                    if (place(canvas, city.name, projection.toScreenLocation(coordinate), true,
                            Hit(city.countryID, city.id), false)) count++
                }
            }
        }
    }

    private fun place(canvas: Canvas, name: String, point: PointF, isCity: Boolean, hit: Hit, fitCountry: Boolean): Boolean {
        if (!isCity) {
            val visited = hit.countryID in visitedCountryIDs
            countryPaint.color = Color.parseColor(if (visited) "#FFFFFF" else if (dark) "#B8B8B8" else "#606267")
            countryPaint.setShadowLayer(1.2f * density, 0f, density,
                Color.parseColor(if (visited) "#111111" else if (dark) "#111111" else "#FBFCFD"))
        }
        val key = "${if (isCity) "city" else "country"}:$name"
        val text = layouts[key] ?: run {
            val paint = if (isCity) cityPaint else countryPaint
            val width = ceil(paint.measureText(name).coerceAtMost(140 * density)).toInt().coerceAtLeast(1)
            StaticLayout.Builder.obtain(name, 0, name.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(2)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build().also { layouts.put(key, it) }
        }
        val top = if (isCity) point.y + 6 * density else point.y - text.height / 2f
        val rect = RectF(point.x - text.width / 2f, top, point.x + text.width / 2f, top + text.height)
        if (rect.left < 12 * density || rect.right > width - 12 * density || rect.top < 70 * density || rect.bottom > height - 40 * density) return false
        val collision = RectF(rect).apply { inset(-4 * density, -3 * density) }
        if (occupied.any { RectF.intersects(it, collision) }) return false
        if (fitCountry && !fits(rect, hit.countryID)) return false
        val save = canvas.save()
        canvas.translate(rect.left, rect.top)
        text.draw(canvas)
        canvas.restoreToCount(save)
        occupied += RectF(rect)
        hits += RectF(rect).apply { inset(-5 * density, -5 * density) } to hit
        return true
    }

    private fun fits(rect: RectF, countryID: String): Boolean {
        val map = map ?: return false
        val pad = 2 * density
        val points = listOf(PointF(rect.left - pad, rect.top - pad), PointF(rect.right + pad, rect.top - pad),
            PointF(rect.left - pad, rect.bottom + pad), PointF(rect.right + pad, rect.bottom + pad))
        return points.all { point ->
            map.queryRenderedFeatures(point, "country-fill").any { feature ->
                feature.hasProperty("id") && feature.getStringProperty("id") == countryID
            }
        }
    }
}
