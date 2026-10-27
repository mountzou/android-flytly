package com.mountzou.flytly.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import kotlinx.coroutines.launch
import kotlin.math.max

private val PreferencesFont = FontFamily(
    Font(R.font.bricolage_preferences_regular, FontWeight.Normal),
    Font(R.font.bricolage_preferences_medium, FontWeight.Medium),
    Font(R.font.bricolage_login_headline, FontWeight.SemiBold))

internal fun preferenceType(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = PreferencesFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    // CoreText and Android shape the same font with slightly different advance widths.
    // Calibrated against the original SwiftUI screenshots, without inserting line breaks.
    letterSpacing = (if (weight == FontWeight.Medium) 0f else if (size >= 26) .10f else if (weight == FontWeight.SemiBold) .18f else .15f).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))

internal val PreferencesDraftSaver = listSaver<TravelPreferences, Any>(
    save = { listOf(it.levels, it.mustHave.toList(), it.mustAvoid.toList()) },
    restore = { row -> TravelPreferences((row[0] as List<*>).map { it as Int },
        (row[1] as List<*>).filterIsInstance<String>().toSet(), (row[2] as List<*>).filterIsInstance<String>().toSet()) })

@Composable
internal fun PreferencesWizard(store: TravelPreferencesStore, editing: Boolean = false, onClose: () -> Unit = {}) {
    val state by store.state.collectAsState()
    var draft by rememberSaveable(stateSaver = PreferencesDraftSaver) { mutableStateOf(state.values) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val initialSaveGeneration by rememberSaveable { mutableLongStateOf(state.saveGeneration) }
    var finished by remember { mutableStateOf(false) }
    fun finish() { if (!finished) { finished = true; onClose() } }
    LaunchedEffect(Unit) { if (dirty) store.markDraftChanged() }
    LaunchedEffect(state.saveGeneration) {
        if (editing && state.saveGeneration > initialSaveGeneration && !state.isSaving) finish()
    }
    LaunchedEffect(state.values) { if (!dirty) draft = state.values }
    fun update(next: TravelPreferences) { dirty = true; store.markDraftChanged(); draft = next }
    PreferencesWizardContent(draft, state.isSaving, editing, ::update,
        onSave = { skipped -> scope.launch { if (store.save(draft, skipped)) { dirty = false; finish() } } },
        onCancel = { if (editing && !state.isSaving) { store.discardDraft(); onClose() } })
    state.error?.let { message ->
        AlertDialog(onDismissRequest = store::clearError, title = { Text("Preferences") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = store::clearError) { Text("OK") } })
    }
}

/** Direct port of TravelPreferencesOnboardingView: eight questions, then must-have/avoid. */
@Composable
internal fun PreferencesWizardContent(
    draft: TravelPreferences, saving: Boolean, editing: Boolean,
    onChange: (TravelPreferences) -> Unit, onSave: (Boolean) -> Unit, onCancel: () -> Unit = {},
    initialStep: Int = 0, initialExpanded: String? = null, includeSystemInsets: Boolean = true
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }
    val ink = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    BackHandler { if (editing && !saving) onCancel() }
    Surface(Modifier.fillMaxSize().testTag("travelPreferencesOnboarding"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().then(if (includeSystemInsets) Modifier.safeDrawingPadding() else Modifier)) {
            if (editing) Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, enabled = !saving, modifier = Modifier.testTag("cancelPreferencesEdit")) {
                    Icon(painterResource(R.drawable.ic_preferences_back), "Back", Modifier.size(22.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "EDIT PREFERENCES" else "PREFERENCES", style = preferenceType(12, 15),
                    letterSpacing = 1.6.sp, color = secondary)
                Spacer(Modifier.weight(1f))
                if (!editing) Text("Skip", Modifier.testTag("skipPreferences").alpha(if (saving) .5f else 1f)
                    .clickable(enabled = !saving, role = Role.Button) { onSave(true) }, style = preferenceType(17, 21, FontWeight.SemiBold))
            }
            Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp).fillMaxWidth().height(4.dp)
                .clip(CircleShape).background(ink.copy(alpha = .12f)).testTag("preferencesProgress")) {
                Box(Modifier.fillMaxWidth((step + 1) / 9f).fillMaxHeight().clip(CircleShape).background(ink))
            }
            if (step < preferenceQuestions.size) {
                val question = preferenceQuestions[step]
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val viewport = constraints.maxHeight
                    QuestionLayout(viewport, Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(question.title, Modifier.testTag("preferenceStepTitle"), style = preferenceType(34, 41, FontWeight.SemiBold))
                        Text(question.question, style = preferenceType(20, 24), color = secondary)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            question.options.forEachIndexed { index, label ->
                                val chosen = draft.levels[step] == index + 1
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(ink.copy(alpha = if (chosen) .12f else .045f))
                                    .semantics { selected = chosen }.testTag("preferenceOption-${question.field}-${index + 1}")
                                    .clickable(enabled = !saving, role = Role.RadioButton) { onChange(draft.choosing(step, index + 1)) }
                                    .padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, Modifier.weight(1f), style = preferenceType(17, 21, FontWeight.SemiBold)
                                        .copy(lineBreak = if (chosen) LineBreak.Simple else LineBreak.Heading))
                                    Spacer(Modifier.width(16.dp))
                                    if (chosen) Icon(painterResource(R.drawable.ic_preferences_check), null, Modifier.padding(start = 8.dp).size(17.dp))
                                }
                            }
                        }
                        PreferenceAction(if (step == 7) "Continue" else "Next", !saving, "nextPreferenceStep") { step++ }
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Spacer(Modifier.height(18.dp))
                    Text("Must have and avoid", Modifier.testTag("preferenceStepTitle"), style = preferenceType(34, 41, FontWeight.SemiBold))
                    Text("Choose the things that can strongly shape your destination choice.", style = preferenceType(20, 24), color = secondary)
                    PreferenceMultiSelect("MUST HAVE", "mustHave", mustHaveOptions, draft.mustHave,
                        expanded == "mustHave", !saving, { expanded = if (expanded == "mustHave") null else "mustHave" },
                        { onChange(draft.copy(mustHave = it)) })
                    PreferenceMultiSelect("MUST AVOID", "mustAvoid", mustAvoidOptions, draft.mustAvoid,
                        expanded == "mustAvoid", !saving, { expanded = if (expanded == "mustAvoid") null else "mustAvoid" },
                        { onChange(draft.copy(mustAvoid = it)) })
                    PreferenceAction(if (editing) "Save changes" else "Save preferences", !saving, "savePreferences",
                        Modifier.padding(top = 12.dp)) { onSave(false) }
                }
            }
        }
    }
}

@Composable
internal fun PreferenceAction(label: String, enabled: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background == Color(0xFF111111)
    Box(modifier.fillMaxWidth().height(52.dp).testTag(tag).alpha(if (enabled) 1f else .5f)
        .clip(RoundedCornerShape(12.dp)).background(if (dark) Color.White else Color.Black)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = preferenceType(17, 21, FontWeight.Medium), color = if (dark) Color.Black else Color.White, maxLines = 1)
    }
}

/** SwiftUI's two equal flexible spacers, five 28pt gaps and 24pt padding, with a compact scroll fallback. */
@Composable
private fun QuestionLayout(viewport: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { children, constraints ->
        val padding = 24.dp.roundToPx(); val gap = 28.dp.roundToPx(); val minimumSpacer = 8.dp.roundToPx()
        val width = (constraints.maxWidth - padding * 2).coerceAtLeast(0)
        val measured = children.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        val base = padding * 2 + gap * 5 + measured.sumOf { it.height }
        val height = max(viewport, base + minimumSpacer * 2)
        val spacer = (height - base) / 2
        layout(constraints.maxWidth, height) {
            var y = padding + spacer + gap
            measured.take(3).forEach { child -> child.placeRelative(padding, y); y += child.height + gap }
            measured.last().placeRelative(padding, height - padding - measured.last().height)
        }
    }
}

@Composable
internal fun PreferenceMultiSelect(title: String, field: String, options: List<String>, selected: Set<String>,
                                   expanded: Boolean, enabled: Boolean, onExpand: () -> Unit,
                                   onChange: (Set<String>) -> Unit, profileMode: Boolean = false) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().testTag("${field}MultiSelect").clip(shape).background(ink.copy(alpha = .045f))
        .border(1.dp, ink.copy(alpha = if (expanded) .2f else .06f), shape).animateContentSize(tween(360))) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onExpand)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = preferenceType(12, 15), letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(if (expanded) R.drawable.ic_preferences_up else R.drawable.ic_preferences_down), null,
                Modifier.size(if (profileMode) 44.dp else 28.dp, if (profileMode) 36.dp else 28.dp).padding(8.dp))
        }
        if (selected.isEmpty()) Text(if (profileMode) "Nothing selected" else "Choose options",
            Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onExpand)
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp), style = preferenceType(17, 21, FontWeight.SemiBold))
        else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.sorted().forEach { label ->
                Row(Modifier.clip(CircleShape).background(ink.copy(alpha = .08f)).testTag("chip-$field-$label")
                    .clickable(enabled = enabled, role = Role.Button) { if (profileMode) onExpand() else onChange(selected - label) }
                    .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = preferenceType(13, 16))
                    if (!profileMode) Icon(painterResource(R.drawable.ic_preferences_remove), "Remove $label", Modifier.size(10.dp))
                }
            }
        }
        if (expanded) {
            HorizontalDivider(color = ink.copy(alpha = .1f))
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { label ->
                    Row(Modifier.fillMaxWidth().testTag("$field-$label").semantics { this.selected = label in selected }
                        .clickable(enabled = enabled, role = Role.Checkbox) { onChange(if (label in selected) selected - label else selected + label) }
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, Modifier.weight(1f), style = preferenceType(17, 21, FontWeight.SemiBold))
                        Spacer(Modifier.width(8.dp))
                        Icon(painterResource(R.drawable.ic_preferences_check), null, Modifier.size(13.dp).alpha(if (label in selected) 1f else 0f))
                    }
                }
            }
        }
    }
}
