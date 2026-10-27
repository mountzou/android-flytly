package com.mountzou.flytly.atlas

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import com.mountzou.flytly.R
import com.mountzou.flytly.account.AccountRepository
import com.mountzou.flytly.account.AccountDetails
import com.mountzou.flytly.account.AccountPage
import com.mountzou.flytly.preferences.PreferencesProfilePage
import com.mountzou.flytly.preferences.PreferencesWizard
import com.mountzou.flytly.ui.theme.FlytlyTheme
import com.mountzou.flytly.startup.AtlasLoadingView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

internal fun atlasNumber(value: Number): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

@Composable
fun AtlasScreen(lifecycleOwner: LifecycleOwner, storageNamespace: String? = null,
                account: AccountRepository? = null, accountName: String? = null, onSignOut: (() -> Unit)? = null,
                accountDetails: AccountDetails? = null, accountBusy: Boolean = false, accountError: String? = null,
                onDeleteAccount: (() -> Unit)? = null, onClearAccountError: () -> Unit = {}) {
    AtlasPerformance.mark("atlas.enter")
    val context = LocalContext.current
    val preferences = remember(storageNamespace,account != null) { atlasSettings(context,storageNamespace,account != null) }
    val travelFile = remember { LocalTravelFile(context, storageNamespace) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("appearance", "light") ?: "light") }
    val dark = if (appearance == "system") isSystemInDarkTheme() else appearance == "dark"
    SideEffect {
        (lifecycleOwner as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    var countryNames by rememberSaveable { mutableStateOf(preferences.getBoolean("countryNames", true)) }
    var cityNames by rememberSaveable { mutableStateOf(preferences.getBoolean("cityNames", true)) }
    var minimumPopulation by rememberSaveable { mutableIntStateOf(preferences.getInt("minimumPopulation", 50_000)) }
    var attempt by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<AtlasCatalog?>(null) }
    var localTravels by remember { mutableStateOf(TravelSnapshot()) }
    val cloud = account?.data?.collectAsState()?.value
    val travels = cloud?.travels ?: localTravels
    val profileState = account?.preferences?.state?.collectAsState()?.value
    var loadError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapAttempt by remember { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf(if (accountError != null) "account" else "home") }
    var entryID by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedCountryID by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCityID by rememberSaveable { mutableStateOf<String?>(null) }
    var mapSelectedCityID by rememberSaveable { mutableStateOf<String?>(null) }
    var regionName by rememberSaveable { mutableStateOf(AtlasRegion.EUROPE.name) }
    var target by remember { mutableStateOf<MapTarget?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var shareSnapshot by remember { mutableStateOf<com.mountzou.flytly.sharing.TravelCardSnapshot?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pageStates = rememberSaveableStateHolder()
    fun navigate(next: String, countryID: String? = selectedCountryID, cityID: String? = selectedCityID) {
        history = history + listOf(route, selectedCountryID.orEmpty(), selectedCityID.orEmpty(), entryID).joinToString("\t")
        selectedCountryID = countryID; selectedCityID = cityID; entryID = java.util.UUID.randomUUID().toString(); route = next
    }
    fun back() {
        focusManager.clearFocus(force = true); keyboard?.hide()
        pageStates.removeState(entryID)
        val previous = history.lastOrNull()?.split("\t")
        entryID = previous?.getOrNull(3) ?: java.util.UUID.randomUUID().toString()
        route = previous?.getOrNull(0) ?: "home"
        selectedCountryID = previous?.getOrNull(1)?.ifEmpty { null }
        selectedCityID = previous?.getOrNull(2)?.ifEmpty { null }
        history = history.dropLast(1)
    }
    fun close() { route = "home"; history = emptyList() }
    fun changeAppearance(value: String) { appearance = value; preferences.edit().putString("appearance", value).apply() }
    BackHandler(route != "home" || accountBusy) { if (!accountBusy) back() }

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val loaded = AtlasRepository.load(context)
            if (account == null) localTravels = withContext(Dispatchers.IO) { travelFile.load() }
            catalog = loaded
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = "Your atlas could not be opened. Your saved places have been kept. Please try again." }
    }

    FlytlyTheme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (accountBusy && cloud?.loaded == false) {
                AtlasPage("Account",{},showBack = false) { AccountPage(accountDetails,true,accountError,onSignOut,onDeleteAccount,onClearAccountError) }
                return@Surface
            }
            if (account != null && profileState?.shouldPresentOnboarding == true) {
                PreferencesWizard(account.preferences)
                return@Surface
            }
            val atlas = catalog
            if (atlas == null || cloud?.loaded == false) {
                if (cloud?.localError != true && loadError == null) {
                    AtlasLoadingView(Modifier.fillMaxSize())
                    return@Surface
                }
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (cloud?.localError == true) {
                        Text(cloud.message)
                        TextButton(onClick = { onSignOut?.invoke() }) { Text("Sign out") }
                    } else {
                        Text(loadError!!)
                        TextButton(onClick = { attempt++ }) { Text("Try again") }
                    }
                }
                return@Surface
            }
            val selectedCountry = selectedCountryID?.let(atlas.countriesByID::get)
            AtlasPerformance.mark("atlas.dataReady")
            val selectedCity = selectedCityID?.let(atlas::cityForSavedID)
            val visitedCountries = remember(travels, atlas) { travels.countryIDs(PlaceStatus.VISITED, atlas) }
            val visitedCities = remember(travels, atlas) { travels.cities(PlaceStatus.VISITED, atlas) }
            AtlasPerformance.mark("atlas.statsReady")
            val continents = remember(visitedCountries) {
                visitedCountries.mapNotNull { atlas.countriesByID[it]?.travelContinent() }.distinct().size
            }
            fun openCountry(country: AtlasCountry) {
                focusManager.clearFocus(force = true); keyboard?.hide()
                navigate("country", country.id, null)
            }
            fun openCity(city: AtlasCity) {
                focusManager.clearFocus(force = true); keyboard?.hide()
                navigate("city", city.countryID, city.id)
            }
            fun showMap() {
                val city = selectedCity.takeIf { route == "city" }
                mapSelectedCityID = city?.id
                if (city != null) target = MapTarget(city.latitude, city.longitude, 7.0)
                else selectedCountry?.id?.let(atlas.labels::get)?.let {
                    target = MapTarget(it.latitude, it.longitude, (it.minimumZoom + 0.7).coerceIn(3.0, 7.0))
                }
                close()
            }
            fun savePlace(next: PlaceStatus, expecting: TravelSnapshot?): Deferred<PlaceChange?> {
                if (saving || selectedCountry == null) return CompletableDeferred(null)
                saving = true
                val before = account?.data?.value?.travels ?: localTravels
                return scope.async {
                    try {
                        if (expecting != null && before != expecting) throw PlaceChangeConflictException()
                        val after = if (account != null) account.setPlace(selectedCity?.let { "city:${it.id}" } ?: "country:${selectedCountry.id}", next, expecting ?: before)
                        else {
                            val updated = before.withStatus(selectedCity, selectedCountry.id, next, atlas)
                            withContext(Dispatchers.IO) { travelFile.saveIfCurrent(before, updated) }
                            localTravels = updated; updated
                        }
                        PlaceChange(before, after)
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (conflict: PlaceChangeConflictException) { saveError = conflict.message; null }
                    catch (_: Exception) { saveError = "This place could not be saved. Please try again."; null }
                    finally { saving = false }
                }
            }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        FootprintStat("YOUR FOOTPRINT", visitedCountries.size, atlas.countries.size, if (visitedCountries.size == 1) "Country" else "Countries", Modifier.weight(.53f))
                        FootprintStat("YOUR REACH", continents, 6, if (continents == 1) "Continent" else "Continents", Modifier.weight(.47f))
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).testTag(if (mapReady) "atlasReady" else "atlasLoading")) {
                        key(mapAttempt) {
                            AtlasPerformance.mark("atlas.beforeMap")
                            AtlasMap(atlas, lifecycleOwner, dark, countryNames, cityNames,
                                null, mapSelectedCityID?.let(atlas::cityForSavedID), target,
                                onCountry = { id -> atlas.countriesByID[id]?.let(::openCountry) },
                                onCity = { id -> atlas.cityForSavedID(id)?.let(::openCity) },
                                onRegion = { regionName = it.name },
                                onReady = { mapReady = true; mapError = null }, onError = { mapError = it },
                                visitedCountryIDs = visitedCountries, visitedCities = visitedCities,
                                storageNamespace = storageNamespace, modifier = Modifier.fillMaxSize())
                        }
                        val paper = MaterialTheme.colorScheme.background
                        Box(Modifier.fillMaxWidth().height(144.dp).background(Brush.verticalGradient(listOf(paper, paper.copy(alpha = 0f)))))
                        RegionStrip(AtlasRegion.valueOf(regionName), Modifier.padding(top = 12.dp)) { region ->
                            regionName = region.name; target = MapTarget(region.latitude, region.longitude)
                        }
                        if (mapError != null) {
                            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(mapError!!)
                                    TextButton(onClick = { mapError = null; mapReady = false; mapAttempt++ }) { Text("Try again") }
                            }
                        }
                    }
                }
                if (route == "home") {
                    FloatingAtlasButton(R.drawable.ic_atlas_menu, "Open menu", false,
                        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 62.dp)) { navigate("menu") }
                    FloatingAtlasButton(R.drawable.ic_atlas_plus, "Add a place", true,
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 62.dp)) { query = ""; navigate("search") }
                } else if (route == "preferences-edit" && account != null) {
                    PreferencesWizard(account.preferences, editing = true, onClose = ::back)
                } else if (route == "menu") {
                    NavigationMenu(dark, { navigate(it) }, ::back, ::changeAppearance)
                } else {
                    val title = when (route) {
                        "search" -> "Add a place"; "country" -> "Places in ${selectedCountry?.name.orEmpty()}"; "city", "other" -> "Place details"; "cities" -> selectedCountry?.name.orEmpty()
                        "travels" -> "My travels"; "wishlist" -> "Wishlist"; "preferences" -> "Preferences"
                        "settings" -> "Settings"; "account" -> "Account"; "credits" -> "Sources and licenses"; else -> ""
                    }
                    AtlasPage(title, ::back, showTitle = route in listOf("country", "city", "other", "cities"), actions = {
                        if (route == "travels" && visitedCountries.isNotEmpty()) {
                            IconButton(onClick = { shareSnapshot = com.mountzou.flytly.sharing.TravelCardSnapshot.capture(atlas, travels) }, modifier = Modifier.testTag("shareTravelsButton")) {
                                Icon(painterResource(R.drawable.ic_atlas_share), "Share your travels", Modifier.size(22.dp))
                            }
                        }
                    }, showBack = !accountBusy) {
                        pageStates.SaveableStateProvider(entryID) {
                        when (route) {
                            "search" -> SearchPage(atlas, query, { query = it }, ::openCountry, ::openCity)
                            "country", "city", "other" -> selectedCountry?.let { country ->
                                PlacePage(atlas, country, if (route == "city") selectedCity else null, route == "other",
                                    travels, saving, ::savePlace, ::openCity, onCountry = { openCountry(country) },
                                    onAllCities = { navigate("cities") }, onShowMap = ::showMap, preferencesStore = account?.preferences)
                            }
                            "cities" -> selectedCountry?.let { CitiesPage(atlas, it, minimumPopulation, travels, ::openCity, onOther = { navigate("other", it.id, null) }) }
                            "travels", "wishlist" -> CollectionPage(atlas, travels,
                                if (route == "travels") PlaceStatus.VISITED else PlaceStatus.WISHLIST,
                                ::openCountry, onExplore = { query = ""; navigate("search") })
                            "settings" -> SettingsPage(countryNames, cityNames, minimumPopulation,
                                onCountries = { countryNames = it; preferences.edit().putBoolean("countryNames", it).apply() },
                                onCities = { cityNames = it; preferences.edit().putBoolean("cityNames", it).apply() },
                                onPopulation = { minimumPopulation = it; preferences.edit().putInt("minimumPopulation", it).apply() },
                                onCredits = { navigate("credits") },accountEmail = accountDetails?.email,onAccount = { navigate("account") })
                            "account" -> AccountPage(accountDetails,accountBusy,accountError,onSignOut,onDeleteAccount,onClearAccountError)
                            "credits" -> CreditsSheet()
                            "preferences" -> if (account == null) PreferencesPage(preferences)
                                else PreferencesProfilePage(account.preferences, onEdit = { navigate("preferences-edit") })
                        }
                        }
                    }
                }
                if (route == "home" && !mapReady && mapError == null) {
                    AtlasLoadingView(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    })
                }
            }
            saveError?.let { message ->
                AlertDialog(onDismissRequest = { saveError = null }, title = { Text("Unable to save") },
                    text = { Text(message) }, confirmButton = { TextButton(onClick = { saveError = null }) { Text("OK") } })
            }
            shareSnapshot?.let { snapshot -> com.mountzou.flytly.sharing.TravelCardSheet(snapshot) { shareSnapshot = null } }
        }
    }
}

@Composable
private fun FootprintStat(caption: String, value: Int, total: Int, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(caption, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 5.dp)) {
            Text(atlasNumber(value), style = MaterialTheme.typography.displayLarge, maxLines = 1)
            Column(Modifier.padding(start = 10.dp, bottom = 18.dp)) {
                Text("OUT OF", fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(atlasNumber(total), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
    }
}

@Composable
private fun RegionStrip(selected: AtlasRegion, modifier: Modifier, onSelect: (AtlasRegion) -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        AtlasRegion.entries.forEach { region ->
            val active = selected == region
            Column(Modifier.weight(1f).testTag("region-${region.name}").clickable { onSelect(region) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(region.title, Modifier.padding(vertical = 14.dp), fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(thickness = if (active) 2.dp else 1.dp,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
            }
        }
    }
}

@Composable
internal fun FloatingAtlasButton(icon: Int, description: String, prominent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier.size(if (prominent) 68.dp else 64.dp), shape = CircleShape,
        color = if (prominent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        contentColor = if (prominent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), description,
            if (icon == R.drawable.ic_atlas_close) Modifier.size(18.dp, 17.dp) else Modifier.size(24.dp)) }
    }
}

@Composable
private fun NavigationMenu(dark: Boolean, onRoute: (String) -> Unit, onClose: () -> Unit, onAppearance: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = .98f)) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.align(Alignment.Center).padding(horizontal = 28.dp).padding(bottom = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf("travels" to "My travels", "wishlist" to "Wishlist", "preferences" to "Preferences", "settings" to "Settings").forEach { (route, title) ->
                    Text(title, Modifier.clickable { onRoute(route) }.heightIn(min = 52.dp).padding(vertical = 3.dp),
                        fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(28.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(false to R.drawable.ic_atlas_sun, true to R.drawable.ic_atlas_moon).forEach { (isDark, icon) ->
                        Box(Modifier.size(52.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark == isDark) .12f else 0f))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark == isDark) .55f else .15f), CircleShape)
                            .clickable { onAppearance(if (isDark) "dark" else "light") }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(icon), if (isDark) "Dark mode" else "Light mode", Modifier.size(23.dp))
                        }
                    }
                }
            }
            FloatingAtlasButton(R.drawable.ic_atlas_close, "Close menu", false,
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 62.dp), onClose)
        }
    }
}

@Composable
internal fun AtlasIconButton(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(icon), description,
        if (icon == R.drawable.ic_atlas_close) Modifier.size(18.dp, 17.dp) else Modifier.size(22.dp)) }
}
