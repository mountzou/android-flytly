package com.mountzou.flytly.atlas

import android.content.SharedPreferences
import com.mountzou.flytly.account.preferenceFields
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AtlasPage(title: String, onBack: () -> Unit, showTitle: Boolean = false, actions: (@Composable () -> Unit)? = null, showBack: Boolean = true, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                if (showTitle || title == "Add a place" || title == "Sources & licenses") Text(title, Modifier.align(Alignment.Center).padding(horizontal = 64.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (showBack) Box(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
                    AtlasIconButton(if (title == "Add a place") R.drawable.ic_atlas_close else R.drawable.ic_preferences_back, "Back") { onBack() }
                }
                actions?.let { Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) { it() } }
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
internal fun SearchPage(atlas: AtlasCatalog, query: String, onQuery: (String) -> Unit,
                        onCountry: (AtlasCountry) -> Unit, onCity: (AtlasCity) -> Unit) {
    var results by remember(atlas) { mutableStateOf(AtlasSearchResults(emptyList(), emptyList())) }
    LaunchedEffect(query) { results = withContext(Dispatchers.Default) { atlas.search(query, 30) } }
    val popular = remember(atlas) { listOf("FRA", "ESP", "USA", "TUR", "ITA", "MEX", "CHN", "GBR").mapNotNull(atlas.countriesByID::get) }
    Column(Modifier.fillMaxSize().imePadding()) {
        TextField(query, onQuery, singleLine = true, placeholder = { Text("Search countries or cities", fontSize = 16.sp) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp)) },
            trailingIcon = { if (query.isNotEmpty()) AtlasIconButton(R.drawable.ic_close, "Clear search") { onQuery("") } },
            shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(focusedContainerColor = cardColor(), unfocusedContainerColor = cardColor(),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("placeSearch"))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 30.dp)) {
            item { PageSection(if (query.isBlank()) "POPULAR COUNTRIES" else "COUNTRIES") }
            items(if (query.isBlank()) popular else results.countries.take(8), key = { it.id }) { country ->
                PagePlaceRow(country.name, country.travelContinent().orEmpty().uppercase()) { onCountry(country) }
            }
            if (query.isNotBlank() && results.cities.isNotEmpty()) {
                item { PageSection("CITIES") }
                items(results.cities, key = { it.id }) { city ->
                    PagePlaceRow(city.name, atlas.countriesByID[city.countryID]?.name.orEmpty()) { onCity(city) }
                }
            }
            if (query.isNotBlank() && results.countries.isEmpty() && results.cities.isEmpty()) item {
                Text("No places found", Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium)
                Text("Try another country or city name.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun cardColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)

@Composable
internal fun PageHeader(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(description, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PageSection(text: String) {
    Text(text, Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 10.dp), fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PagePlaceRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
}

@Composable
internal fun PreferencesPage(preferences: SharedPreferences, profile: Map<String, Any>? = null,
                             onProfile: ((String, Any) -> Unit)? = null) {
    val questions = remember { listOf(
        "Travel pace" to listOf("I like taking things slowly", "I like a balanced pace", "I like fitting a lot into each day"),
        "Nature" to listOf("Mostly urban", "Some nature nearby", "Mostly nature"),
        "Culture" to listOf("Culture is not a priority", "I enjoy some culture", "I travel for culture"),
        "Food" to listOf("Food is not a priority", "I enjoy local cuisine", "Food is a main reason to visit"),
        "Crowds" to listOf("I prefer quieter places", "Crowds are fine sometimes", "I enjoy lively, busy places"),
        "Mobility" to listOf("I can adapt to most transport options", "I prefer cities that are easy to get around", "I choose cities with excellent mobility"),
        "Weather" to listOf("Weather does not affect my choice", "Weather matters to my choice", "Weather is a key part of my choice"),
        "Budget" to listOf("Lower-cost destinations", "Mid-range destinations", "Higher-cost destinations")
    ) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { PageHeader("Preferences", "Your preferences for finding destinations that fit you.") }
        item { PageSection("TRAVEL PREFERENCES") }
        items(questions.indices.chunked(2)) { indexes ->
            Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                indexes.forEach { index ->
                    val (title, answers) = questions[index]
                    var localChoice by rememberSaveable { mutableIntStateOf(preferences.getInt("profile-$index", -1)) }
                    val choice = if (profile != null) ((profile[preferenceFields[index]] as? Number)?.toInt() ?: 0) - 1 else localChoice
                    var picker by rememberSaveable { mutableStateOf(false) }
                    val names = listOf("TravelPaceWalking", "TravelNatureCloud", "TravelCultureStatue", "TravelFoodCarrot", "TravelCrowdsMegaphone", "TravelMobilityTrain", "TravelWeatherUmbrella", "TravelBudgetPig")
                    Column(Modifier.weight(1f).heightIn(min = 156.dp).clip(RoundedCornerShape(18.dp))
                        .background(cardColor()).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(18.dp))
                        .clickable { picker = true }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(9.dp)) {
                                PreferenceArtwork(names[index], Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.weight(1f)); Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(answers.getOrNull(choice) ?: "Not selected", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text(title) },
                        text = { Column {
                            answers.forEachIndexed { answer, text ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    if (onProfile != null) onProfile(preferenceFields[index], answer + 1)
                                    else { localChoice = answer; preferences.edit().putInt("profile-$index", answer).apply() }
                                    picker = false
                                }.padding(vertical = 12.dp)) {
                                    Text(text, Modifier.weight(1f)); if (choice == answer) Text("✓")
                                }
                            }
                        } }, confirmButton = { TextButton(onClick = { picker = false }) { Text("Done") } })
                }
            }
        }
        item { ProfileMultiSelect("MUST HAVE", listOf("Visa-free or easy entry", "Direct flights or simple connections", "Good access to healthcare"), preferences, profile, "mustHave", onProfile) }
        item { ProfileMultiSelect("MUST AVOID", listOf("Safety concerns", "Political instability", "Extreme isolation", "Limited emergency services"), preferences, profile, "mustAvoid", onProfile) }
        item { Text(if (profile == null) "Preferences are saved on this device." else "Preferences sync with your Flytly account.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ProfileMultiSelect(title: String, options: List<String>, preferences: SharedPreferences,
                               profile: Map<String, Any>?, field: String, onProfile: ((String, Any) -> Unit)?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var localSelected by remember { mutableStateOf(options.filter { preferences.getBoolean("profile-$it", false) }.toSet()) }
    val selected = if (profile != null) (profile[field] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty() else localSelected
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(18.dp)).background(cardColor())) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (selected.isEmpty()) "Nothing selected" else selected.sorted().joinToString(" · "),
                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (expanded) options.forEach { label ->
            Row(Modifier.fillMaxWidth().clickable {
                val next = if (label in selected) selected - label else selected + label
                if (onProfile != null) onProfile(field, next.sorted())
                else { localSelected = next; preferences.edit().putBoolean("profile-$label", label in next).apply() }
            }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (label in selected) Text("✓", Modifier.padding(start = 12.dp))
            }
        }
    }
}
