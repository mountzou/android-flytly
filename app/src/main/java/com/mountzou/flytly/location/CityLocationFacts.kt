package com.mountzou.flytly.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mountzou.flytly.R
import com.mountzou.flytly.atlas.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

private data class Zones(val origin: ZoneId?, val destination: ZoneId?, val loading: Boolean = false)

@Composable
internal fun CityLocationFacts(city: AtlasCity) {
    val access = rememberLocationAccess()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(city.id) { CityLocationController(LocationSourceOverrides.factory?.invoke(context) ?: AndroidDeviceLocationSource(context), scope) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(access.authorization) { controller.authorization(access.authorization) }
    DisposableEffect(controller, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { controller.authorization(access.read()); controller.start() }
            if (event == Lifecycle.Event.ON_STOP) controller.stop()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { controller.authorization(access.read()); controller.start() }
        onDispose { controller.stop(); owner.lifecycle.removeObserver(observer) }
    }
    if (access.authorization != LocationAuthorization.GRANTED) {
        LocationPrompt(city.name, access)
        return
    }
    val fix = controller.fix
    val destination = remember(city.id, city.latitude, city.longitude) { Coordinate(city.latitude, city.longitude) }
    var attempt by remember(city.id) { mutableIntStateOf(0) }
    val zones by produceState(Zones(null, null, true), fix?.coordinate, city.id, attempt) {
        val at = fix?.coordinate
        if (at == null) { value = Zones(null, null, true); return@produceState }
        val cachedOrigin = LocationZoneCache.origin(at)
        val cachedDestination = LocationZoneCache.destination(city.id, destination)
        value = Zones(cachedOrigin, cachedDestination, cachedOrigin == null || cachedDestination == null)
        value = withContext(Dispatchers.IO) {
            try {
                val resolver = LocationSourceOverrides.timeZone ?: if (cachedOrigin == null || cachedDestination == null) OfflineTimeZones.open(context)::zoneAt else null
                val origin = cachedOrigin ?: resolver!!(at)?.also {
                    currentCoroutineContext().ensureActive()
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) LocationZoneCache.storeOrigin(at, it)
                }
                val target = cachedDestination ?: resolver!!(destination)?.also { currentCoroutineContext().ensureActive(); LocationZoneCache.storeDestination(city.id, destination, it) }
                Zones(origin, target)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Zones(cachedOrigin, cachedDestination) }
        }
    }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = Instant.now(); delay(30_000) }
        }
    }
    val distance: @Composable (Modifier) -> Unit = { modifier ->
        LocationFact("DISTANCE FROM YOU", modifier.testTag("cityDistance")) {
            when {
                fix != null -> { FactValue(distanceText(distanceMeters(fix.coordinate, destination))); FactCaption("Straight-line distance") }
                controller.failed -> FactAction("Location unavailable · Try again", controller::start)
                else -> FactCaption("Finding your location…")
            }
        }
    }
    val difference: @Composable (Modifier) -> Unit = { modifier ->
        LocationFact("TIME DIFFERENCE", modifier.testTag("cityTimeDifference")) {
            when {
                controller.failed -> FactAction("Location unavailable · Try again", controller::start)
                zones.origin != null && zones.destination != null -> { FactValue(differenceText(zones.origin!!, zones.destination!!, now)); FactCaption("Compared with your location") }
                fix == null || zones.loading -> FactCaption("Loading…")
                else -> { Text("Unavailable", fontSize = 17.sp); FactAction("Try again", { attempt++ }, small = true) }
            }
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        distance(Modifier.fillMaxWidth()); difference(Modifier.fillMaxWidth())
    } else Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        distance(Modifier.weight(1f)); difference(Modifier.weight(1f))
    }
}

@Composable
private fun LocationPrompt(city: String, access: LocationAccess) {
    Surface(Modifier.fillMaxWidth().testTag("cityLocationPrompt"), shape = RoundedCornerShape(20.dp), color = cardColor()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_atlas_location), null, Modifier.size(20.dp))
                Text("Compare with your location", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            if (access.authorization == LocationAuthorization.RESTRICTED) {
                FactCaption("Location access is restricted on this device. Distance and time difference are unavailable.")
            } else {
                FactCaption("See your distance and time difference from $city.")
                val setup = access.authorization == LocationAuthorization.SET_UP
                Button(if (setup) access.request else access.openSettings, Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .testTag(if (setup) "cityUseLocationButton" else "cityLocationSettingsButton"), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = placeAccent(), contentColor = placeOnAccent())) {
                    Text(if (setup) "Use my location" else "Open Settings", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LocationFact(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) { PlaceCaption(title); content() }
}
@Composable private fun FactValue(text: String) { Text(text, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium) }
@Composable private fun FactCaption(text: String) { Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun FactAction(text: String, action: () -> Unit, small: Boolean = false) {
    Text(text, Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action), fontSize = if (small) 13.sp else 17.sp, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun LocationAccessSettingsCard(modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
    val access = rememberLocationAccess()
    val setup = access.authorization == LocationAuthorization.SET_UP
    val enabled = access.authorization != LocationAuthorization.RESTRICTED
    Surface(modifier.testTag("locationPermissionAction"), color = cardColor(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = if (setup) access.request else access.openSettings)
            .semantics(mergeDescendants = true) { contentDescription = "Location access"; stateDescription = when (access.authorization) { LocationAuthorization.SET_UP -> "Set up"; LocationAuthorization.GRANTED -> "On"; LocationAuthorization.DENIED -> "Off"; LocationAuthorization.RESTRICTED -> "Unavailable" } }
            .padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Location access", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(when (access.authorization) { LocationAuthorization.SET_UP -> "Set up"; LocationAuthorization.GRANTED -> "On"; LocationAuthorization.DENIED -> "Off"; LocationAuthorization.RESTRICTED -> "Unavailable" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled) Icon(painterResource(if (setup) R.drawable.ic_atlas_chevron else R.drawable.ic_atlas_external_link), null, if (setup) Modifier.size(8.dp, 14.dp) else Modifier.size(14.dp))
        }
    }
}
