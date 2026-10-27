package com.mountzou.flytly.atlas

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import kotlinx.coroutines.launch

@Composable
internal fun CollectionPage(atlas: AtlasCatalog, travels: TravelSnapshot, status: PlaceStatus,
                            onCountry: (AtlasCountry) -> Unit, onExplore: () -> Unit) {
    val collection = remember(atlas, travels, status) { PlaceCollection(atlas, travels, status) }
    val firstPage = remember(atlas, status) { travelContinents.indexOf(collection.firstContinent).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = firstPage, pageCount = { travelContinents.size })
    val selectedContinent = travelContinents[pager.currentPage]
    val countries = collection.countriesIn(selectedContinent)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visitedCountries = remember(atlas, travels) { travels.countryIDs(PlaceStatus.VISITED, atlas) }
    val wishlist = status == PlaceStatus.WISHLIST
    BoxWithConstraints(Modifier.fillMaxSize().testTag(if (wishlist) "wishlistView" else "myPlacesView")) {
        val width = maxWidth.coerceAtMost(1100.dp)
        val wide = width >= 700.dp
        val cardWidth = if (wide) 280.dp else minOf(340.dp, width * .74f)
        val leadingInset = if (wide) 24.dp else (width - cardWidth) / 2
        val trailingInset = if (wide) width - cardWidth - leadingInset else leadingInset
        val columns = if (density.fontScale >= 1.5f) 1 else if (wide) ((width.value - 34) / 254).toInt().coerceIn(2, 4) else 2
        val cardHeight = 274.dp * density.fontScale.coerceIn(1f, 1.65f)
        Column(Modifier.width(width).fillMaxHeight().align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp)) {
                Text(if (wishlist) "Wishlist" else "My travels", fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(if (wishlist) "The places you want to explore next." else "Your travels, continent by continent.",
                    Modifier.padding(top = 6.dp), fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.weight(1f).testTag("collectionContent"), contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)) {
                item(key = "carousel") {
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(cardHeight).testTag("placesContinentCarousel"),
                        contentPadding = PaddingValues(start = leadingInset, end = trailingInset), pageSize = PageSize.Fixed(cardWidth), pageSpacing = 14.dp,
                        beyondViewportPageCount = 1, snapPosition = if (wide) SnapPosition.Start else SnapPosition.Center,
                        flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1))) { index ->
                        val continent = travelContinents[index]
                        val entries = collection.countriesIn(continent)
                        val active = index == pager.currentPage
                        val ink = MaterialTheme.colorScheme.onSurface
                        val foreground = if (active) placeOnAccent() else ink
                        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(34.dp)).background(if (active) placeAccent() else cardColor())
                            .clickable(role = Role.Tab) { scope.launch { pager.animateScrollToPage(index) } }
                            .testTag("${if (wishlist) "wishlist" else "places"}-continent-$continent")
                            .semantics(mergeDescendants = true) { contentDescription = continent; selected = active; stateDescription = collection.continentSummary(continent) }
                            .padding(24.dp)) {
                            PlaceArtwork(continent, foreground, MaterialTheme.colorScheme.background,
                                Modifier.fillMaxWidth().height(148.dp).padding(top = 8.dp), highlighted = entries.map { it.id }.toSet())
                            Spacer(Modifier.weight(1f))
                            val textColor = foreground.copy(alpha = if (active || entries.isNotEmpty()) 1f else .42f)
                            Text(continent, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1)
                            Text(collection.continentSummary(continent), Modifier.padding(top = 8.dp), fontSize = 15.sp, lineHeight = 20.sp, color = textColor)
                        }
                    }
                }
                if (countries.isNotEmpty()) {
                    if (wishlist) item(key = "summary") {
                        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp).testTag("wishlistSelectedContinent"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(selectedContinent, Modifier.weight(1f).alignByBaseline(), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                            Text(collection.continentSummary(selectedContinent), Modifier.alignByBaseline(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    itemsIndexed(countries.chunked(columns), key = { _, row -> row.joinToString { it.id } }) { rowIndex, row ->
                        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (rowIndex > 0) 14.dp else if (wishlist) 18.dp else 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { country ->
                                CollectionCountryCard(country, collection.countryDetail(country.id), wishlist && country.id in visitedCountries,
                                    Modifier.weight(1f).testTag("${if (wishlist) "wishlist-country" else "my-place"}-${country.id}")) { onCountry(country) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (wishlist) "Where to next?" else "Your travels start here", fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text(if (wishlist) "Find a place you want to explore and save it to your Wishlist." else "Mark the places you’ve visited and watch your travel map come to life.",
                            fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.padding(top = 8.dp).size(64.dp).clip(CircleShape).background(placeAccent())
                            .clickable(role = Role.Button, onClick = onExplore).testTag("collectionAddPlaceButton").semantics { contentDescription = "Add a place" }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_atlas_plus), null, Modifier.size(28.dp), tint = placeOnAccent())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCountryCard(country: AtlasCountry, detail: String, visited: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(cardColor()).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (visited) stateDescription = "Visited" }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceArtwork(country.id, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.background,
            Modifier.fillMaxWidth().height(108.dp).padding(vertical = 8.dp), highlighted = setOf(country.id))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(country.name, Modifier.weight(1f, fill = false), fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            if (visited) VisitedBadge()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(detail, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_atlas_chevron), null, Modifier.size(8.dp, 13.dp))
        }
    }
}
