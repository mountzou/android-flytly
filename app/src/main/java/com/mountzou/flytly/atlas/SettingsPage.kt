package com.mountzou.flytly.atlas

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import com.mountzou.flytly.location.LocationAccessSettingsCard

internal const val PRIVACY_POLICY_URL = "https://docs.google.com/document/d/e/2PACX-1vSnviL7l7YvEoQZgqIG3h5nlribVq8udf3T1zmKWQ9ditFUY0GnMdHa1VY8C5qlD7fJyKkDlAx1HV5k/pub"
internal val populationOptions = listOf(50_000,100_000,250_000,500_000,1_000_000)

@Composable
internal fun SettingsPage(countries: Boolean, cities: Boolean, minimum: Int, onCountries: (Boolean) -> Unit,
                          onCities: (Boolean) -> Unit, onPopulation: (Int) -> Unit, onCredits: () -> Unit,
                          accountEmail: String?, onAccount: () -> Unit) {
    var populationPicker by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("settingsView")) {
        val wide = maxWidth >= 700.dp && LocalDensity.current.fontScale < 1.5f
        Column(Modifier.widthIn(max = 1100.dp).fillMaxSize().align(Alignment.TopCenter)) {
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)) {
                Text("Settings",fontSize = 34.sp,lineHeight = 41.sp,fontWeight = FontWeight.Bold,modifier = Modifier.semantics { heading() })
                Text("Make the atlas yours.",Modifier.padding(top = 6.dp,bottom = 18.dp),fontSize = 15.sp,lineHeight = 20.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("settingsScroll").padding(horizontal = 24.dp).padding(top = 12.dp,bottom = 32.dp),verticalArrangement = Arrangement.spacedBy(28.dp)) {
                SettingsGroup("MAP APPEARANCE","Choose how much detail appears on your map.") {
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        SettingsTile(Modifier.weight(1f),vertical = 16) { SettingsToggle("Show country names","showCountryNames",countries,onCountries) }
                        SettingsTile(Modifier.weight(1f),vertical = 16) { SettingsToggle("Show city names","showCityNames",cities,onCities) }
                    } else SettingsTile(vertical = 16) {
                        SettingsToggle("Show country names","showCountryNames",countries,onCountries)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp),color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                        SettingsToggle("Show city names","showCityNames",cities,onCities)
                    }
                }
                val population: @Composable () -> Unit = {
                    SettingsLink("Minimum population","Over ${atlasNumber(minimum)}","minimumCityPopulation",onClick = { populationPicker = true })
                }
                val account: @Composable () -> Unit = {
                    SettingsLink("Account",accountEmail ?: "Manage your profile and sign-in.","accountSettingsButton",subtitle = true,onClick = onAccount)
                }
                if (!wide) SettingsGroup("BROWSING CITIES","Browse larger cities. Search for smaller ones.") { population() }
                SettingsGroup("PRIVACY") {
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(32.dp),verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { LocationAccessSettingsCard(Modifier.fillMaxWidth()) }
                        Box(Modifier.weight(1f)) { SettingsLink("Privacy policy",null,"privacyPolicyButton",external = true) { uri.openUri(PRIVACY_POLICY_URL) } }
                    } else {
                        LocationAccessSettingsCard(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        SettingsLink("Privacy policy",null,"privacyPolicyButton",external = true) { uri.openUri(PRIVACY_POLICY_URL) }
                    }
                }
                if (wide) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row { Box(Modifier.weight(1f)) { SettingsHeading("BROWSING CITIES","Browse larger cities. Search for smaller ones.") };Spacer(Modifier.weight(1f)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp),verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { population() };Box(Modifier.weight(1f)) { account() }
                    }
                } else account()
                SettingsGroup("ABOUT") { SettingsLink("Sources and licenses",null,"dataCreditsButton",onClick = onCredits) }
            }
        }
    }
    if (populationPicker) PopulationSheet(minimum,{ onPopulation(it);populationPicker = false }) { populationPicker = false }
}

@Composable private fun SettingsGroup(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { SettingsHeading(title,description);Column(content = content) }
}
@Composable private fun SettingsHeading(title: String, description: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title,fontSize = 12.sp,lineHeight = 16.sp,letterSpacing = 1.6.sp,color = MaterialTheme.colorScheme.onSurfaceVariant,modifier = Modifier.semantics { heading() })
        description?.let { Text(it,fontSize = 13.sp,lineHeight = 18.sp,color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun SettingsTile(modifier: Modifier = Modifier, vertical: Int = 20, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(cardColor(),RoundedCornerShape(16.dp)).padding(horizontal = 20.dp,vertical = vertical.dp),content = content)
}
@Composable private fun SettingsToggle(label: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(checked,role = Role.Switch,onValueChange = onChange)
        .testTag(tag).semantics { contentDescription = label },verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),fontSize = 17.sp,lineHeight = 24.sp)
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        val track = if (checked) { if (dark) androidx.compose.ui.graphics.Color(0xFF8C8C8C) else placeAccent() }
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
        Canvas(Modifier.size(64.dp,28.dp).clearAndSetSemantics {}) {
            drawRoundRect(track,cornerRadius = CornerRadius(14.dp.toPx()))
            drawRoundRect(androidx.compose.ui.graphics.Color.White,topLeft = Offset((if (checked) 24.dp else 2.dp).toPx(),2.dp.toPx()),
                size = Size(38.dp.toPx(),24.dp.toPx()),cornerRadius = CornerRadius(12.dp.toPx()))
        }
    }
}
@Composable private fun SettingsLink(label: String, value: String?, tag: String, external: Boolean = false, subtitle: Boolean = false, onClick: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stackedValue = large || (tag == "minimumCityPopulation" && maxWidth < 370.dp)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor()).clickable(role = Role.Button,onClick = onClick).testTag(tag)
        .semantics(mergeDescendants = true) { contentDescription = if (tag == "minimumCityPopulation") "Minimum city population" else label;value?.let { stateDescription = it } }
        .padding(20.dp).heightIn(min = if (tag == "minimumCityPopulation") 44.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (subtitle || (stackedValue && value != null)) Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(if (subtitle) 8.dp else 6.dp)) {
            Text(label,fontSize = 17.sp,lineHeight = 24.sp,fontWeight = if (subtitle) FontWeight.SemiBold else FontWeight.Normal)
            value?.let { Text(it,fontSize = 13.sp,lineHeight = 18.sp,color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Text(label,Modifier.weight(1f),fontSize = 17.sp,lineHeight = 24.sp,fontWeight = if (value == null) FontWeight.SemiBold else FontWeight.Normal)
            value?.let { Text(it,fontSize = 17.sp,lineHeight = 24.sp,color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(painterResource(if (external) R.drawable.ic_atlas_external_link else R.drawable.ic_atlas_chevron),null,if (external) Modifier.size(16.dp) else Modifier.size(8.dp,14.dp))
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PopulationSheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp,4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).testTag("populationChoiceSheet")) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Minimum population",Modifier.align(Alignment.Center).padding(horizontal = 72.dp),fontSize = 17.sp,lineHeight = 22.sp,fontWeight = FontWeight.SemiBold)
                TextButton(onDismiss,Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) { Text("Done",fontSize = 17.sp) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(24.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.fillMaxWidth().background(cardColor(),RoundedCornerShape(16.dp)).padding(horizontal = 20.dp)) {
                    populationOptions.forEachIndexed { index,value ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onSelect(value) }.testTag("minimumCityPopulation-$value")
                            .semantics(mergeDescendants = true) { this.selected = selected == value }.heightIn(min = 64.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Over ${atlasNumber(value)}",Modifier.weight(1f),fontSize = 17.sp,lineHeight = 24.sp)
                            if (selected == value) Icon(painterResource(R.drawable.ic_preferences_check),null,Modifier.size(18.dp))
                        }
                        if (index != populationOptions.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                    }
                }
                Text("Filters the city list within each country. Search still includes smaller cities. Some capitals are always included.",fontSize = 13.sp,lineHeight = 18.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
