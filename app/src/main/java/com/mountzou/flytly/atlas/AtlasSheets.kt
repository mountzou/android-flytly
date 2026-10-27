package com.mountzou.flytly.atlas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CreditsSheet() {
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Box(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)) {
            com.mountzou.flytly.account.AccountPageHeader("Sources and licenses","The sources behind your atlas.")
        } }
        item { Credit("COUNTRY OUTLINES", "Natural Earth",
            "Country and territory outlines and label positions use the reviewed Natural Earth 1:10 million dataset. Public domain. Labels and country associations are adapted for this atlas; boundaries are generalized for display.") }
        item { Credit("CITIES & POPULATION", "GHSL · European Commission, JRC",
            "GHS-UCDB R2024A V1.2, with 2025 population estimates. Urban centres strictly above 50,000 people are selected, combined with alternative place names, and assigned to the atlas’s map units. © European Union, 1995–2025. CC BY 4.0. This independent adaptation is not endorsed by the European Commission.") }
        item { Credit("PLACE NAMES & CAPITALS", "GeoNames",
            "Alternative names and reviewed capital or administrative-seat fallbacks. Snapshot: 8 September 2026. CC BY 4.0. Records have been selected, combined and adapted. Legacy IDs are retained for compatibility with the iOS catalog.") }
        item { Credit("TYPE & FLAG SOURCES", "Bricolage Grotesque · flag-icons",
            "Bricolage Grotesque by Atelier Triay is bundled under SIL OFL 1.1. Grayscale flag SVG sources by Panayiotis Lipiridis and contributors (© 2013) are included under MIT. Unicode CLDR mapping and full notices are bundled with the atlas.") }
        item { Credit("MAP RENDERER", "MapLibre Native",
            "Open-source native map rendering. The map uses bundled GeoJSON and local font glyphs, with no online basemap or tile subscription. MapLibre Native is licensed under BSD 2-Clause; dependency notices are distributed with the SDK.") }
        item { Credit("VECTOR ARTWORK RENDERER", "AndroidSVG", "AndroidSVG 1.4 by Paul LeBeau renders the original flag SVGs and geometry-derived illustrations. Apache License 2.0; the full license is bundled with the atlas.") }
        item { Credit("LOADING ANIMATION", "beUI · Saurabh Chauhan", "The monochrome metaball loader is adapted from beUI's Loader motion reference, matching the Flytly iOS implementation. © 2026 Saurabh Chauhan. MIT License; the complete notice is bundled in atlas/licenses/beUI.txt.") }
        item { Credit("TIME ZONE BOUNDARIES", "OpenStreetMap · timezone-boundary-builder",
            "Offline geographic lookup uses timezone-boundary-builder 2026e, derived from © OpenStreetMap contributors under ODbL 1.0. All boundary vertices are retained at sub-centimetre coordinate precision. The complete derivative database, source notice and license are bundled in assets/timezones. Android supplies IANA timezone and daylight-saving rules. Your location is not sent to a timezone service.") }
        item { TextButton(onClick = { uri.openUri("https://github.com/evansiroky/timezone-boundary-builder/releases/tag/2026e") }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Time zone boundary source") } }
        item { TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by/4.0/") }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Read CC BY 4.0") } }
        item { TextButton(onClick = { uri.openUri("https://www.naturalearthdata.com/about/terms-of-use/") }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Natural Earth terms") } }
        item { TextButton(onClick = { uri.openUri("https://www.geonames.org/") }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("GeoNames") } }
    }
}

@Composable
private fun Credit(category: String, title: String, body: String) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
      PlaceCaption(category)
      Column(Modifier.fillMaxWidth().background(cardColor(),RoundedCornerShape(20.dp)).padding(20.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title,fontSize = 23.sp,lineHeight = 28.sp,fontWeight = FontWeight.Medium)
        Text(body,fontSize = 13.sp,lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
}
