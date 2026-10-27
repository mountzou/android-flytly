package com.mountzou.flytly.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.UserManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class LocationAuthorization { SET_UP, GRANTED, DENIED, RESTRICTED }
internal interface DeviceLocationSource { suspend fun current(): LocationFix? }

internal class AndroidDeviceLocationSource(context: Context) : DeviceLocationSource {
    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    @android.annotation.SuppressLint("MissingPermission") // Caller checks again on each foreground request.
    override suspend fun current(): LocationFix? = coroutineScope {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return@coroutineScope null
        // Both are foreground one-shot requests with the same coarse permission.
        // A working platform GNSS provider also supports devices/emulators whose
        // Google network location service is unavailable. First fresh fix wins.
        val results = Channel<LocationFix?>(2)
        suspend fun report(block: suspend () -> LocationFix?) {
            val fix = try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            results.send(fix)
        }
        val requests = listOf(launch { report { googleFix() } }, launch { report { platformFix() } })
        try {
            withTimeoutOrNull(18_000) {
                repeat(2) { results.receive()?.let { return@withTimeoutOrNull it } }
                null
            }
        } finally { requests.forEach { it.cancel() } }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun googleFix(): LocationFix? {
        val cancellation = CancellationTokenSource()
        return try {
            val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setGranularity(Granularity.GRANULARITY_COARSE).setMaxUpdateAgeMillis(300_000).setDurationMillis(15_000).build()
            client.getCurrentLocation(request, cancellation.token).await()?.toFix()
        } finally { cancellation.cancel() }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun platformFix(): LocationFix? {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")) {
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) continue
            try {
                val location = suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(appContext)) { value ->
                            if (continuation.isActive) continuation.resume(value)
                        }
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
                location?.toFix()?.let { return it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A provider may be unavailable or require more precision on older Android. */ }
        }
        return null
    }
    private fun Location.toFix(): LocationFix? = if (!hasAccuracy()) null else
        LocationFix(Coordinate(latitude, longitude), time, accuracy).takeIf { it.isFresh(System.currentTimeMillis()) }
}

internal data class LocationAccess(val authorization: LocationAuthorization, val request: () -> Unit,
                                   val openSettings: () -> Unit, val read: () -> LocationAuthorization)

@Composable
internal fun rememberLocationAccess(): LocationAccess {
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    val preferences = remember(context) { context.getSharedPreferences(LocationSourceOverrides.preferenceNamespace?.let { "location_access_$it" } ?: "location_access", Context.MODE_PRIVATE) }
    fun authorization(): LocationAuthorization {
        val restricted = (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION) == true ||
            context.packageManager.isPermissionRevokedByPolicy(Manifest.permission.ACCESS_COARSE_LOCATION, context.packageName)
        return when {
            restricted -> LocationAuthorization.RESTRICTED
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationAuthorization.GRANTED
            preferences.getBoolean("requested", false) -> LocationAuthorization.DENIED
            else -> LocationAuthorization.SET_UP
        }
    }
    var current by remember { mutableStateOf(authorization()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { current = authorization() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) current = authorization() }
        owner.lifecycle.addObserver(observer)
        current = authorization()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return LocationAccess(current, request = {
        if (authorization() == LocationAuthorization.SET_UP && activity != null) {
            preferences.edit().putBoolean("requested", true).apply()
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }, openSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }, read = { authorization() })
}

/** In-memory fix only. Foreground one-shot requests, cancellation and revocation are explicit. */
internal class CityLocationController(private val source: DeviceLocationSource, private val scope: CoroutineScope) {
    var fix by mutableStateOf<LocationFix?>(null); private set
    var failed by mutableStateOf(false); private set
    private var job: Job? = null
    private var allowed = false
    private var foreground = false
    fun authorization(value: LocationAuthorization) {
        allowed = value == LocationAuthorization.GRANTED
        if (!allowed) { job?.cancel(); job = null; fix = null; failed = false; LocationZoneCache.clearOrigin() }
        else if (foreground && job?.isActive != true) start()
    }
    fun start() {
        foreground = true
        if (!allowed) return
        job?.cancel(); failed = false
        job = scope.launch {
            try {
                val next = source.current()
                if (isActive && foreground && allowed) {
                    if (next != null && next.isFresh(System.currentTimeMillis())) fix = next
                    failed = fix == null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (foreground && allowed) failed = fix == null }
        }
    }
    fun stop() { foreground = false; job?.cancel(); job = null }
}
