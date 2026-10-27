package com.mountzou.flytly.atlas

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.net.URI

data class MapTarget(val latitude: Double? = null, val longitude: Double? = null, val zoom: Double? = null,
                     val zoomDelta: Double = 0.0, val requestID: Long = System.nanoTime())

@Composable
fun AtlasMap(
    catalog: AtlasCatalog,
    lifecycleOwner: LifecycleOwner,
    dark: Boolean,
    countryNames: Boolean,
    cityNames: Boolean,
    selectedCountryID: String?,
    selectedCity: AtlasCity?,
    target: MapTarget?,
    onCountry: (String) -> Unit,
    onCity: (String) -> Unit,
    onRegion: (AtlasRegion) -> Unit,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    visitedCountryIDs: Set<String> = emptySet(),
    visitedCities: List<AtlasCity> = emptyList(),
    storageNamespace: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val countryCallback = rememberUpdatedState(onCountry)
    val cityCallback = rememberUpdatedState(onCity)
    val regionCallback = rememberUpdatedState(onRegion)
    val readyCallback = rememberUpdatedState(onReady)
    val errorCallback = rememberUpdatedState(onError)
    val controller = remember(context, catalog) {
        AtlasMapController(context, catalog, dark, storageNamespace,
            { countryCallback.value(it) }, { cityCallback.value(it) },
            { regionCallback.value(it) }, { readyCallback.value() }, { errorCallback.value(it) })
    }
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> controller.lifecycle(event) }
        val memory = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { controller.onLowMemory() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(memory)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memory)
            controller.destroy()
        }
    }
    AndroidView(
        factory = { controller.view },
        modifier = modifier.testTag("atlasMap"),
        update = { controller.update(dark, countryNames, cityNames, selectedCountryID, selectedCity, target, visitedCountryIDs, visitedCities) }
    )
}

/** A local GeoJSON style: no basemap, tile service, glyph server, token, or network dependency. */
private class AtlasMapController(
    context: Context,
    private val catalog: AtlasCatalog,
    private var dark: Boolean,
    storageNamespace: String?,
    private val onCountry: (String) -> Unit,
    private val onCity: (String) -> Unit,
    private val onRegion: (AtlasRegion) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val startedAt = android.os.SystemClock.elapsedRealtime()
    private var recordedFirstFrame = false
    private val namespace = storageNamespace
    private val preferences = context.getSharedPreferences(storageNamespace?.let { "atlas_map_$it" } ?: "atlas_map", Context.MODE_PRIVATE)
    private var visitedCountryIDs = emptySet<String>()
    private var visitedCities = emptyList<AtlasCity>()
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var countryNames = true
    private var cityNames = true
    private var countryID: String? = null
    private var city: AtlasCity? = null
    private var pendingTarget: MapTarget? = null
    private var appliedTarget: MapTarget? = null
    private val cityLayers = mutableListOf<String>()
    private val countryLayer = "country-fill"

    private val mapView: MapView
    private val labels = AtlasLabelsOverlay(context, catalog)
    val view: FrameLayout

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context, MapLibreMapOptions.createFromAttributes(context).apply {
            attributionEnabled(false)
            logoEnabled(false)
            compassEnabled(false)
            rotateGesturesEnabled(false)
            tiltGesturesEnabled(false)
            textureMode(true)
        }).apply {
            contentDescription = "Interactive world atlas. Search places to explore the map accessibly."
            onCreate(null)
        }
        view = FrameLayout(context).apply {
            addView(mapView, FrameLayout.LayoutParams(-1, -1))
            addView(labels, FrameLayout.LayoutParams(-1, -1))
        }
        mapView.addOnDidFailLoadingMapListener { if (!destroyed) onError("The atlas map could not be displayed.") }
        mapView.addOnDidFinishRenderingMapListener { fully ->
            if (!destroyed && fully && style != null) {
                if (!recordedFirstFrame) {
                    recordedFirstFrame = true
                    AtlasPerformance.record("map.firstFrame", startedAt)
                    AtlasPerformance.record("startup.firstFrame", android.os.Process.getStartElapsedRealtime())
                }
                labels.invalidate(); onReady()
            }
        }
        mapView.getMapAsync { nativeMap ->
            if (!destroyed) {
                map = nativeMap
                labels.map = nativeMap
                nativeMap.setMinZoomPreference(0.0)
                nativeMap.setMaxZoomPreference(12.0)
                nativeMap.cameraPosition = savedCamera()
                nativeMap.addOnMapClickListener { coordinate -> selectAt(nativeMap.projection.toScreenLocation(coordinate)) }
                nativeMap.addOnCameraMoveListener { labels.invalidate() }
                nativeMap.addOnCameraIdleListener { saveCamera(); labels.invalidate() }
                nativeMap.setStyle(buildStyle()) { loaded ->
                    if (!destroyed) {
                        style = loaded
                        AtlasPerformance.record("map.style", startedAt)
                        loaded.setTransition(TransitionOptions(0, 0))
                        applySelection()
                        applyVisits()
                        applyVisibility()
                        applyPalette()
                        applyTarget()
                    }
                }
            }
        }
        AtlasPerformance.record("map.construct", startedAt)
    }

    private fun buildStyle(): Style.Builder {
        val builder = Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[]}""")
            .withSource(GeoJsonSource("countries", URI("asset://atlas/countries.geojson")))
            .withSource(GeoJsonSource("selected-city", FeatureCollection.fromFeatures(emptyArray<Feature>())))
            .withSource(GeoJsonSource("visited-cities", FeatureCollection.fromFeatures(emptyArray<Feature>())))
            .withLayer(BackgroundLayer("paper").withProperties(backgroundColor(paper())))
            .withLayer(FillLayer(countryLayer, "countries").withProperties(fillColor(land()), fillAntialias(true)))
            .withLayer(FillLayer("country-visited", "countries").withFilter(eq(get("id"), literal("")))
                .withProperties(fillColor(visited())))
            .withLayer(FillLayer("country-selection", "countries").withFilter(eq(get("id"), literal("")))
                .withProperties(fillColor(selection())))
            .withLayer(LineLayer("borders", "countries").withProperties(lineColor(border()), lineWidth(0.65f)))
        cityLayers += "visited-dots"
        builder.withLayer(CircleLayer("visited-dots", "visited-cities").withProperties(
            circleRadius(4f), circleColor(paper()), circleStrokeColor(ink()), circleStrokeWidth(1.5f)))
        return builder.withLayer(CircleLayer("city-selection", "selected-city").withProperties(
            circleRadius(5f), circleColor(ink()), circleStrokeColor(paper()), circleStrokeWidth(2f)))
    }

    fun update(dark: Boolean, countryNames: Boolean, cityNames: Boolean,
               countryID: String?, city: AtlasCity?, target: MapTarget?, visitedCountryIDs: Set<String>, visitedCities: List<AtlasCity>) {
        if (this.visitedCountryIDs != visitedCountryIDs || this.visitedCities != visitedCities) {
            this.visitedCountryIDs = visitedCountryIDs; this.visitedCities = visitedCities; applyVisits()
        }
        if (this.dark != dark) { this.dark = dark; applyPalette() }
        if (this.countryNames != countryNames || this.cityNames != cityNames) {
            this.countryNames = countryNames; this.cityNames = cityNames; applyVisibility()
        }
        if (this.countryID != countryID || this.city?.id != city?.id) {
            this.countryID = countryID; this.city = city; applySelection()
        }
        pendingTarget = target
        applyTarget()
    }

    private fun applySelection() {
        style?.getLayerAs<FillLayer>("country-selection")?.setFilter(eq(get("id"), literal(countryID ?: "")))
        labels.selectedCity = city
        labels.invalidate()
        val features = city?.let { arrayOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) }
            ?: emptyArray()
        style?.getSourceAs<GeoJsonSource>("selected-city")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun applyVisits() {
        style?.getLayerAs<FillLayer>("country-visited")?.setFilter(
            if (visitedCountryIDs.isEmpty()) eq(get("id"), literal(""))
            else any(*visitedCountryIDs.map { eq(get("id"), literal(it)) }.toTypedArray()))
        val features = visitedCities.map { city ->
            Feature.fromGeometry(Point.fromLngLat(city.longitude, city.latitude)).apply { addStringProperty("id", city.id) }
        }
        style?.getSourceAs<GeoJsonSource>("visited-cities")?.setGeoJson(FeatureCollection.fromFeatures(features))
        labels.visitedCities = visitedCities
        labels.visitedCountryIDs = visitedCountryIDs
        labels.invalidate()
    }

    private fun applyVisibility() {
        labels.countryNames = countryNames
        labels.cityNames = cityNames
        labels.invalidate()
    }

    private fun applyPalette() {
        val style = style ?: return
        style.getLayerAs<BackgroundLayer>("paper")?.setProperties(backgroundColor(paper()))
        style.getLayerAs<FillLayer>(countryLayer)?.setProperties(fillColor(land()))
        style.getLayerAs<FillLayer>("country-visited")?.setProperties(fillColor(visited()))
        style.getLayerAs<FillLayer>("country-selection")?.setProperties(fillColor(selection()))
        style.getLayerAs<LineLayer>("borders")?.setProperties(lineColor(border()))
        labels.dark = dark
        labels.invalidate()
        cityLayers.forEach { id ->
            style.getLayerAs<CircleLayer>(id)?.setProperties(circleColor(paper()), circleStrokeColor(ink()))
        }
        style.getLayerAs<CircleLayer>("city-selection")?.setProperties(circleColor(ink()), circleStrokeColor(paper()))
        view.setBackgroundColor(paper())
    }

    private fun selectAt(point: PointF): Boolean {
        val nativeMap = map ?: return false
        if (style == null) return false
        labels.hitAt(point.x, point.y)?.let { hit ->
            if (hit.cityID != null) onCity(hit.cityID) else onCountry(hit.countryID)
            return true
        }
        val tolerance = 8 * view.resources.displayMetrics.density
        val cityFeature = nativeMap.queryRenderedFeatures(RectF(point.x - tolerance, point.y - tolerance,
            point.x + tolerance, point.y + tolerance), *cityLayers.toTypedArray()).firstOrNull()
        if (cityFeature != null && cityFeature.hasProperty("id")) {
            onCity(cityFeature.getStringProperty("id")); return true
        }
        val country = nativeMap.queryRenderedFeatures(point, countryLayer).firstOrNull()
        if (country != null && country.hasProperty("id")) {
            onCountry(country.getStringProperty("id")); return true
        }
        return false
    }

    private fun applyTarget() {
        val target = pendingTarget ?: return
        val nativeMap = map ?: return
        if (style == null || target == appliedTarget) return
        appliedTarget = target
        val current = nativeMap.cameraPosition
        val coordinate = if (target.latitude != null && target.longitude != null) LatLng(target.latitude, target.longitude)
            else current.target ?: LatLng(49.0, 12.0)
        val camera = CameraPosition.Builder().target(coordinate)
            .zoom((target.zoom ?: (current.zoom + target.zoomDelta)).coerceIn(0.0, 12.0)).bearing(0.0).tilt(0.0).build()
        nativeMap.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 650)
    }

    private fun savedCamera(): CameraPosition {
        val latitude = preferences.getString("latitude", null)?.toDoubleOrNull()
        val longitude = preferences.getString("longitude", null)?.toDoubleOrNull()
        val zoom = preferences.getString("zoom", null)?.toDoubleOrNull()
        val valid = latitude != null && latitude.isFinite() && latitude in -85.051129..85.051129 &&
            longitude != null && longitude.isFinite() && longitude in -180.0..180.0 &&
            zoom != null && zoom.isFinite() && zoom in 0.0..12.0
        return CameraPosition.Builder().target(if (valid) LatLng(latitude!!, longitude!!) else LatLng(49.0, 12.0))
            .zoom(if (valid) zoom!! else 1.95).bearing(0.0).tilt(0.0).build()
    }

    private fun saveCamera() {
        if (!com.mountzou.flytly.account.AccountStorageGuard.allows(namespace)) return
        if (destroyed || style == null) return
        val camera = map?.cameraPosition ?: return
        val coordinate = camera.target ?: return
        val longitude = ((coordinate.longitude + 180) % 360 + 360) % 360 - 180
        preferences.edit().putString("latitude", coordinate.latitude.toString())
            .putString("longitude", longitude.toString()).putString("zoom", camera.zoom.toString()).apply()
        val feature = map?.queryRenderedFeatures(map!!.projection.toScreenLocation(coordinate), countryLayer)?.firstOrNull()
        val country = feature?.takeIf { it.hasProperty("id") }?.getStringProperty("id")?.let(catalog.countriesByID::get)
        val region = when (country?.continent) {
            "Europe" -> AtlasRegion.EUROPE
            "Asia" -> AtlasRegion.ASIA
            "North America", "South America" -> AtlasRegion.AMERICAS
            "Africa" -> AtlasRegion.AFRICA
            "Oceania" -> AtlasRegion.OCEANIA
            else -> return
        }
        onRegion(region)
    }

    fun lifecycle(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
            Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
            Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
            Lifecycle.Event.ON_STOP -> if (started) { saveCamera(); mapView.onStop(); started = false }
            else -> Unit
        }
    }

    fun onLowMemory() { if (!destroyed) mapView.onLowMemory() }

    fun destroy() {
        lifecycle(Lifecycle.Event.ON_PAUSE)
        lifecycle(Lifecycle.Event.ON_STOP)
        destroyed = true
        labels.map = null
        mapView.onDestroy()
    }

    private fun paper() = Color.parseColor(if (dark) "#111111" else "#FBFCFD")
    private fun land() = Color.parseColor(if (dark) "#303030" else "#DADEE3")
    private fun border() = Color.parseColor(if (dark) "#525252" else "#F9F9F9")
    private fun ink() = Color.parseColor(if (dark) "#F2F2F2" else "#111111")
    private fun label() = Color.parseColor(if (dark) "#B8B8B8" else "#606267")
    private fun selection() = Color.parseColor(if (dark) "#666666" else "#A9ADB3")
    private fun visited() = Color.parseColor(if (dark) "#666666" else "#2D2C2F")
}
