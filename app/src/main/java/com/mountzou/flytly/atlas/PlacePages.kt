package com.mountzou.flytly.atlas

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal typealias SavePlace = (PlaceStatus, TravelSnapshot?) -> Deferred<PlaceChange?>
private data class PlaceFeedback(val message: String, val change: PlaceChange, val removesVisit: Boolean)

@Composable
internal fun PlacePage(atlas: AtlasCatalog, country: AtlasCountry, city: AtlasCity?, other: Boolean,
                       travels: TravelSnapshot, saving: Boolean, onStatus: SavePlace,
                       onCity: (AtlasCity) -> Unit, onCountry: () -> Unit, onAllCities: () -> Unit,
                       onShowMap: () -> Unit, preferencesStore: com.mountzou.flytly.preferences.TravelPreferencesStore? = null) {
    val status = if (city != null) travels.status(city, atlas) else travels.otherStatus(country.id)
    val isCountry = city == null && !other
    val name = if (other) "Other / Not listed" else city?.name ?: country.name
    val (analyzing, analyze) = com.mountzou.flytly.jev.rememberDestinationAnalysis(name, preferencesStore)
    val suggestions = remember(atlas, country.id) { atlas.citiesIn(country.id).take(5) }
    val otherCities = remember(atlas, country.id, travels) {
        atlas.recordedCities(country.id, travels).filter { saved -> suggestions.none { it.id == saved.id } }
    }
    var feedback by remember(country.id, city?.id, other) { mutableStateOf<PlaceFeedback?>(null) }
    var removal by remember { mutableStateOf(false) }
    var undoRemoval by remember { mutableStateOf<PlaceFeedback?>(null) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val fontScale = LocalDensity.current.fontScale
    fun change(next: PlaceStatus, expected: TravelSnapshot? = null, undo: Boolean = false) {
        val request = onStatus(next, expected)
        scope.launch {
            request.await()?.let { result ->
                if (undo) feedback = null
                else {
                    val message = when (next) {
                        PlaceStatus.VISITED -> "$name marked as visited"
                        PlaceStatus.WISHLIST -> "$name saved to Wishlist"
                        PlaceStatus.REMOVED -> "$name removed from ${if (status == PlaceStatus.VISITED) "My travels" else "Wishlist"}"
                    }
                    feedback = PlaceFeedback(message, result, next == PlaceStatus.VISITED)
                    if (next == PlaceStatus.VISITED) view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }
    }
    fun undo(item: PlaceFeedback) {
        val previous = if (city != null) item.change.before.status(city, atlas) else item.change.before.otherStatus(country.id)
        change(previous ?: PlaceStatus.REMOVED, item.change.after, undo = true)
    }
    LaunchedEffect(feedback) { if (feedback != null) { delay(if (fontScale >= 1.5f) 8_000 else 4_000); feedback = null } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = maxWidth
        val wide = pageWidth >= 700.dp && fontScale < 1.5f
        val overview: @Composable () -> Unit = {
            PlaceOverview(country, city, name, isCountry, onCountry, onShowMap)
        }
        val information: @Composable () -> Unit = {
            PlaceStatusActions(name, city != null, status, saving, onVisited = {
                if (status == PlaceStatus.VISITED) removal = true else change(PlaceStatus.VISITED)
            }, onWishlist = { change(if (status == PlaceStatus.WISHLIST) PlaceStatus.REMOVED else PlaceStatus.WISHLIST) }, analyzing = analyzing, onAnalyze = analyze)
            if (city != null && city.kind == CityKind.URBAN_CENTER && city.population.isFinite() && city.population > 0 && city.populationYear != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 28.dp)) {
                    PlaceFact("POPULATION", populationDescription(city.population), "based on GHSL ${city.populationYear} estimate", Modifier.weight(1f))
                    atlas.populationRank(city)?.let { rank -> PlaceFact("COUNTRY RANK", rank, "largest city in ${country.name}", Modifier.weight(1f)) }
                }
            }
            if (city != null) Box(Modifier.padding(top = 28.dp)) { com.mountzou.flytly.location.CityLocationFacts(city) }
        }
        val countryList: @Composable () -> Unit = {
            LazyColumn(Modifier.fillMaxSize().testTag("countryCitiesScroll"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(suggestions, key = { it.id }) { item ->
                    PlaceDiscoveryRow(item.name, travels.status(item, atlas), compact = true, tag = "suggested-city-${item.id}") { onCity(item) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                }
                item {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onAllCities).testTag("addCitiesButton").padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("View all cities", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Icon(painterResource(R.drawable.ic_atlas_arrow_right), null, Modifier.size(22.dp))
                    }
                }
                if (otherCities.isNotEmpty()) {
                    item { PlaceCaption("YOUR CITIES", Modifier.padding(top = 10.dp, bottom = 8.dp)) }
                    items(otherCities, key = { "saved:${it.id}" }) { item ->
                        PlaceDiscoveryRow(item.name, travels.status(item, atlas), tag = "saved-city-${item.id}") { onCity(item) }
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().widthIn(max = 1100.dp).align(Alignment.TopCenter).padding(horizontal = 24.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.width(minOf(340.dp, (pageWidth.coerceAtMost(1100.dp) - 48.dp) * .36f)).verticalScroll(rememberScrollState())) { overview() }
                Column(Modifier.weight(1f)) {
                    if (isCountry) {
                        if (suggestions.isNotEmpty()) PlaceCaption("SELECT A PLACE", Modifier.padding(bottom = 14.dp))
                        Box(Modifier.weight(1f)) { countryList() }
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { information() }
                }
            }
        } else if (isCountry && maxHeight >= 620.dp && fontScale < 1.5f) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp)) {
                    overview()
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                }
                if (suggestions.isNotEmpty()) PlaceCaption("SELECT A PLACE", Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp))
                Box(Modifier.weight(1f).padding(horizontal = 24.dp)) { countryList() }
            }
        } else if (isCountry) {
            // Let the entire page scroll when a compact/large-font header would hide the list.
            LazyColumn(Modifier.fillMaxSize().testTag("countryCitiesScroll"), contentPadding = PaddingValues(24.dp)) {
                item { overview(); Spacer(Modifier.height(28.dp)); HorizontalDivider(); Spacer(Modifier.height(24.dp)) }
                if (suggestions.isNotEmpty()) item { PlaceCaption("SELECT A PLACE", Modifier.padding(bottom = 14.dp)) }
                items(suggestions, key = { it.id }) { item ->
                    PlaceDiscoveryRow(item.name, travels.status(item, atlas), compact = true, tag = "suggested-city-${item.id}") { onCity(item) }; HorizontalDivider()
                }
                item { TextButton(onAllCities, contentPadding = PaddingValues(vertical = 16.dp)) { Text("View all cities") } }
                if (otherCities.isNotEmpty()) item { PlaceCaption("YOUR CITIES", Modifier.padding(top = 10.dp)) }
                items(otherCities, key = { "saved:${it.id}" }) { item -> PlaceDiscoveryRow(item.name, travels.status(item, atlas), tag = "saved-city-${item.id}") { onCity(item) } }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("cityDetailsScroll").padding(24.dp)) {
                overview(); Spacer(Modifier.height(28.dp)); information()
            }
        }
        feedback?.let { item ->
            Surface(Modifier.align(Alignment.TopCenter).padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }.testTag("placeFeedback"), shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp) {
                Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.message, Modifier.weight(1f), fontSize = 16.sp)
                    TextButton(onClick = { if (item.removesVisit) { undoRemoval = item; removal = true } else undo(item) }, enabled = !saving) { Text("Undo") }
                }
            }
        }
    }
    if (removal) AlertDialog(onDismissRequest = { removal = false; undoRemoval = null }, title = { Text("Remove visited status?") },
        text = { Text("$name will no longer be marked as visited. Your visited totals will update automatically.") },
        confirmButton = { TextButton(onClick = { removal = false; val pending = undoRemoval; undoRemoval = null; if (pending != null) undo(pending) else change(PlaceStatus.REMOVED) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removal = false; undoRemoval = null }) { Text("Cancel") } })
}

@Composable
private fun PlaceOverview(country: AtlasCountry, city: AtlasCity?, name: String, isCountry: Boolean, onCountry: () -> Unit, onShowMap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.fillMaxWidth().height(193.dp).clip(RoundedCornerShape(28.dp)).background(cardColor())
            .clickable(onClickLabel = "Show on map", onClick = onShowMap).testTag("placeMapArtwork").padding(24.dp)) {
            PlaceArtwork(country.id, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.background,
                Modifier.fillMaxSize(), highlighted = setOf(country.id), city = city, overview = true,
                description = city?.let { "${it.name}, location in ${country.name}" } ?: "Map of ${country.name}")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                if (isCountry) PlaceCaption(country.travelContinent().orEmpty().uppercase())
                else PlaceCaption(country.name.uppercase(), Modifier.clickable(onClickLabel = "Opens the country", onClick = onCountry).testTag("openCountry"))
            }
            if (isCountry) CountryFlag(country.id, Modifier.size(56.dp, 42.dp).clip(RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun PlaceFact(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceCaption(title)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 31.sp)
        Text(caption, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PlaceCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.sp, letterSpacing = 1.6.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun placeAccent() = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color.White else Color.Black

@Composable
internal fun placeOnAccent() = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color.Black else Color.White

@Composable
private fun PlaceStatusActions(name: String, isCity: Boolean, status: PlaceStatus?, saving: Boolean, onVisited: () -> Unit, onWishlist: () -> Unit, analyzing: Boolean, onAnalyze: () -> Unit) {
    val visited = status == PlaceStatus.VISITED
    val saved = status == PlaceStatus.WISHLIST
    val ink = placeAccent()
    val paper = placeOnAccent()
    val visitedButton: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(ink)
            .clickable(enabled = !saving, role = Role.Button, onClick = onVisited).testTag("status-visited")
            .semantics { stateDescription = if (visited) "Visited" else "Not visited"; selected = visited }
            .animateContentSize(tween(240)), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (visited) 48.dp else 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                if (!visited || !isCity) Image(painterResource(R.drawable.flytly_visited_airplane), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(paper))
                Text(if (visited && isCity) "I’ve been to $name" else "Visited", Modifier.weight(1f, fill = false), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = paper,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (visited) Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(22.dp).background(paper, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_preferences_check), null, Modifier.size(14.dp), tint = ink)
            }
        }
    }
    val wishlistButton: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f))
            .clickable(enabled = !saving, role = Role.Button, onClick = onWishlist).testTag("status-wishlist")
            .semantics { contentDescription = "Wishlist"; stateDescription = if (saved) "Saved to Wishlist" else "Not saved"; selected = saved }
            .padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Icon(painterResource(if (saved) R.drawable.ic_atlas_bookmark else R.drawable.ic_atlas_bookmark_outline), null, Modifier.size(15.dp, 20.dp))
            if (!saved) Text("Wishlist", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visitedButton(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!visited) wishlistButton(if (saved) Modifier.width(52.dp) else Modifier.weight(1f))
            com.mountzou.flytly.jev.DestinationAnalysisButton(analyzing, onAnalyze)
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        visitedButton(Modifier.weight(1f))
        if (!visited) wishlistButton(if (saved) Modifier.width(52.dp) else Modifier.weight(1f))
        com.mountzou.flytly.jev.DestinationAnalysisButton(analyzing, onAnalyze)
    }
}

@Composable
internal fun VisitedBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.flytly_visited_airplane), null, Modifier.size(17.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surface))
    }
}

@Composable
internal fun PlaceDiscoveryRow(name: String, status: PlaceStatus?, compact: Boolean = false, chevron: Boolean = true, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 60.dp else 84.dp).clickable(onClick = onClick).testTag(tag)
        .semantics(mergeDescendants = true) { stateDescription = when (status) { PlaceStatus.VISITED -> "Visited"; PlaceStatus.WISHLIST -> "Saved to Wishlist"; else -> "" } }
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, Modifier.weight(1f, fill = false), fontSize = if (compact) 18.sp else 23.sp, lineHeight = if (compact) 24.sp else 29.sp, fontWeight = FontWeight.Medium)
            when (status) {
                PlaceStatus.VISITED -> VisitedBadge()
                PlaceStatus.WISHLIST -> Icon(painterResource(R.drawable.ic_atlas_bookmark), null, Modifier.size(15.dp, 20.dp))
                else -> Unit
            }
        }
        if (chevron) Icon(painterResource(R.drawable.ic_atlas_chevron), null, Modifier.size(8.dp, 14.dp))
    }
}

@Composable
internal fun CitiesPage(atlas: AtlasCatalog, country: AtlasCountry, minimum: Int, travels: TravelSnapshot, onCity: (AtlasCity) -> Unit, onOther: () -> Unit) {
    var query by rememberSaveable(country.id) { mutableStateOf("") }
    val available = remember(atlas, country.id) { atlas.citiesIn(country.id) }
    val searchable = remember(atlas, country.id, travels) { (available + atlas.recordedCities(country.id, travels)).distinctBy { it.id } }
    var results by remember { mutableStateOf(emptyList<AtlasCity>()) }
    LaunchedEffect(query, searchable, minimum) {
        results = withContext(Dispatchers.Default) {
            val key = query.searchNormalized()
            if (key.isEmpty()) atlas.browseCities(country.id, minimum)
            else {
                val words = key.split(Regex("\\s+")).filter { it.isNotEmpty() }
                searchable.filter { city -> words.all { it in city.searchText } }
                    .sortedWith(compareBy<AtlasCity> { !it.name.searchNormalized().startsWith(key) }.thenByDescending { it.population }.thenBy { it.name }.thenBy { it.id }).take(100)
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TextField(query, { query = it }, placeholder = { Text("Search cities", fontSize = 16.sp) }, singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), "Clear search") } },
            shape = RoundedCornerShape(12.dp), colors = TextFieldDefaults.colors(focusedContainerColor = cardColor(), unfocusedContainerColor = cardColor(),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("countryCitySearch"))
        PlaceCaption("ALL CITIES IN ${country.name.uppercase()}", Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        LazyColumn(Modifier.weight(1f).testTag("allCitiesList"), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
            if (results.isEmpty()) item {
                PlaceDiscoveryRow("Other / Not listed", travels.otherStatus(country.id), chevron = false, tag = "otherPlace") { onOther() }
                Text(if (available.isEmpty()) "No cities in this atlas" else "No matching cities", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineMedium)
                Text(if (available.isEmpty()) "Choose Other / Not listed to add a place in ${country.name}." else if (query.isEmpty()) "Search by name, or lower the population limit in Settings." else "Choose Other / Not listed, or try another city name.",
                    Modifier.padding(top = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyMedium)
            }
            items(results, key = { it.id }) { city ->
                PlaceDiscoveryRow(city.name, travels.status(city, atlas), chevron = false, tag = "city-${city.id}") { onCity(city) }; HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
            }
            if (results.size == 100 && query.isNotBlank()) item { Text("Keep typing to narrow your results.", fontSize = 13.sp) }
            if (results.isNotEmpty() && (query.isBlank() || results.size < 4)) item {
                PlaceDiscoveryRow("Other / Not listed", travels.otherStatus(country.id), chevron = false, tag = "otherPlace") { onOther() }
            }
        }
    }
}
