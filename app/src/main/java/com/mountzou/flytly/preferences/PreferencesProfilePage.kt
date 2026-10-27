package com.mountzou.flytly.preferences

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import com.mountzou.flytly.atlas.PageHeader
import com.mountzou.flytly.atlas.PreferenceArtwork
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreferencesProfilePage(store: TravelPreferencesStore, onEdit: () -> Unit) {
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().testTag("travelProfileView")) {
        PageHeader("Preferences", "Your preferences for finding destinations that fit you.")
        if ((!state.hasLoaded || state.isLoading) && !state.loadFailed) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Loading your preferences…", Modifier.padding(top = 14.dp), style = preferenceType(17, 21))
            }
        } else if (state.loadFailed) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Preferences unavailable", style = preferenceType(24, 29, FontWeight.SemiBold))
                Text("Your saved preferences could not be loaded.", style = preferenceType(17, 21), color = MaterialTheme.colorScheme.onSurfaceVariant)
                PreferenceAction("Try again", true, "retryPreferences", onClick = store::load)
            }
        } else if (state.status != OnboardingStatus.COMPLETED) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewportHeight = maxHeight
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Image(painterResource(R.drawable.preferences_empty_profile), null,
                        Modifier.fillMaxWidth().padding(horizontal = 48.dp).height(min(230f, (viewportHeight.value + 100f) * .30f).dp),
                        colorFilter = ColorFilter.tint(ink))
                    Text(if (state.status == OnboardingStatus.PENDING) "Complete your preferences" else "Your preferences are not set up yet",
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 22.dp), style = preferenceType(24, 29, FontWeight.SemiBold), textAlign = TextAlign.Center)
                    Text(if (state.status == OnboardingStatus.PENDING) "Complete your preferences to improve your destination matches." else "Add a few preferences to make destination matches more useful.",
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp), style = preferenceType(17, 21), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(1f))
                    PreferenceAction("Set up your preferences", true, "setUpTravelProfileButton",
                        Modifier.padding(horizontal = 24.dp).padding(bottom = (viewportHeight + 100.dp) * .07f), onEdit)
                }
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("TRAVEL PREFERENCES", style = preferenceType(12, 15), letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preferenceQuestions.indices.chunked(2).forEach { indexes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        indexes.forEach { index ->
                            val question = preferenceQuestions[index]
                            Column(Modifier.weight(1f).heightIn(min = 156.dp).clip(RoundedCornerShape(18.dp))
                                .background(ink.copy(alpha = .045f)).border(1.dp, ink.copy(alpha = .06f), RoundedCornerShape(18.dp))
                                .testTag("travelPreferenceCard-${question.field}").clickable { selectedIndex = index }.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(9.dp)) {
                                        PreferenceArtwork(question.artwork, Modifier.fillMaxSize())
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Icon(painterResource(R.drawable.ic_preferences_back), null, Modifier.padding(top = 5.dp).size(13.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(question.title, style = preferenceType(17, 21, FontWeight.SemiBold))
                                Text(question.options[state.values.levels[index] - 1], style = preferenceType(13, 16), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                            }
                        }
                    }
                }
            }
            PreferenceMultiSelect("MUST HAVE", "mustHave", mustHaveOptions, state.values.mustHave,
                expanded == "mustHave", !state.isSaving, { expanded = if (expanded == "mustHave") null else "mustHave" },
                { next -> scope.launch { store.save(state.values.copy(mustHave = next)) } }, profileMode = true)
            PreferenceMultiSelect("MUST AVOID", "mustAvoid", mustAvoidOptions, state.values.mustAvoid,
                expanded == "mustAvoid", !state.isSaving, { expanded = if (expanded == "mustAvoid") null else "mustAvoid" },
                { next -> scope.launch { store.save(state.values.copy(mustAvoid = next)) } }, profileMode = true)
        }
    }
    selectedIndex?.let { index ->
        val question = preferenceQuestions[index]
        ModalBottomSheet(onDismissRequest = { selectedIndex = null }, containerColor = MaterialTheme.colorScheme.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = .06f)).padding(10.dp)) { PreferenceArtwork(question.artwork, Modifier.fillMaxSize()) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(question.title, style = preferenceType(26, 32, FontWeight.SemiBold))
                        Text("Edit one preference", style = preferenceType(13, 16), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(question.question, style = preferenceType(20, 24), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    question.options.forEachIndexed { answer, text ->
                        val chosen = state.values.levels[index] == answer + 1
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = if (chosen) .12f else .045f))
                            .testTag("travelPreferenceOption-${question.field}-${answer + 1}")
                            .clickable(enabled = !state.isSaving) { scope.launch { store.save(state.values.choosing(index, answer + 1)) } }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text, Modifier.weight(1f), style = preferenceType(17, 21, FontWeight.SemiBold))
                            Spacer(Modifier.width(8.dp))
                            if (chosen) Box(Modifier.size(10.dp).clip(CircleShape).background(ink))
                        }
                    }
                }
            }
        }
    }
    if (!state.loadFailed) state.error?.let { message ->
        AlertDialog(onDismissRequest = store::clearError, title = { Text("Preferences") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = store::clearError) { Text("OK") } })
    }
}
