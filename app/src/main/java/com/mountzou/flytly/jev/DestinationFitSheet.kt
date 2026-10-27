package com.mountzou.flytly.jev

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import com.mountzou.flytly.atlas.PreferenceArtwork
import com.mountzou.flytly.preferences.*
import kotlinx.coroutines.*

@Composable
internal fun rememberDestinationAnalysis(destination: String, store: TravelPreferencesStore?): Pair<Boolean, () -> Unit> {
    val scope = rememberCoroutineScope()
    var loading by remember(destination, store) { mutableStateOf(false) }
    var fit by remember(destination, store) { mutableStateOf<DestinationFit?>(null) }
    var error by remember(destination, store) { mutableStateOf(false) }
    fit?.let { DestinationFitSheet(it) { fit = null } }
    if (error) AlertDialog(onDismissRequest = { error = false }, title = { Text("Model usage limit") },
        text = { Text("Our AI models have reached their maximum capacity. Try again in a few minutes.") },
        confirmButton = { TextButton(onClick = { error = false }) { Text("OK") } })
    return loading to {
        if (!loading) {
            loading = true
            scope.launch {
                try { fit = analyzeDestination(destination, store) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
                finally { loading = false }
            }
        }
    }
}

@Composable
internal fun DestinationAnalysisButton(loading: Boolean, onClick: () -> Unit) {
    Surface(onClick, Modifier.size(52.dp).testTag("jevAnalysisButton"), enabled = !loading,
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(if (loading) R.drawable.ic_atlas_hourglass else R.drawable.ic_atlas_sparkles),
                "Analyze destination fit", Modifier.size(20.dp).semantics { stateDescription = if (loading) "Analyzing" else "Uses JEV to compare this destination with your travel preferences" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DestinationFitSheet(fit: DestinationFit, onDismiss: () -> Unit) {
    val pager = rememberPagerState { fit.dimensions.size + 1 }
    val dimension = fit.dimensions.getOrNull(pager.currentPage - 1)
    val score = dimension?.score ?: fit.matchScore
    val large = LocalDensity.current.fontScale >= 1.4f
    val configuration = LocalConfiguration.current
    val scrollable = large || configuration.screenHeightDp < 600
    val height = if (scrollable) (configuration.screenHeightDp * .85f).dp else 450.dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = height - 14.dp).height(height - 14.dp).testTag("jevFitSheet")
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fitLabel(score), Modifier.padding(top = 18.dp).testTag("jevFitLabel"), fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).then(if (large) Modifier.heightIn(min = 56.dp) else Modifier.height(56.dp)), contentAlignment = Alignment.Center) {
                Text(fitDescription(score, dimension?.selectedPreference), fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(if (scrollable) Modifier.height(10.dp) else Modifier.weight(1f))
            HorizontalPager(pager, Modifier.fillMaxWidth().height(if (large) 320.dp else 260.dp).testTag("jevCarousel")) { index ->
                if (index == 0) Column(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "Overall match, ${fitLabel(fit.matchScore)}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                    MatchStateIcon(fit.matchScore, Modifier.size(180.dp))
                    FitRatingBar(fit.matchScore)
                    Spacer(Modifier.height(24.dp))
                } else {
                    val item = fit.dimensions[index - 1]
                    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).clearAndSetSemantics { contentDescription = "${item.title}, ${fitLabel(item.score)} fit" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.height(100.dp), contentAlignment = Alignment.Center) { PreferenceArtwork(preferenceQuestions[item.questionIndex].artwork, Modifier.size(75.6.dp)) }
                        Text(item.title, Modifier.heightIn(min = 34.dp).padding(top = 6.dp), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.padding(top = 14.dp)) { FitRatingBar(item.score) }
                    }
                }
            }
            Row(Modifier.semantics { contentDescription = "Carousel page ${pager.currentPage + 1} of ${pager.pageCount}" }.testTag("jevPageIndicator"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(pager.pageCount) { index -> Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (index == pager.currentPage) 1f else .12f))) }
            }
            Spacer(if (scrollable) Modifier.height(20.dp) else Modifier.weight(1f))
        }
    }
}

@Composable
private fun FitRatingBar(score: Int) {
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Low", "Mixed", "Good", "Excellent").forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.heightIn(min = 18.dp))
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fitStepFill(score, index)).background(MaterialTheme.colorScheme.onSurface))
                }
            }
        }
    }
}

@Composable
private fun MatchStateIcon(score: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val fill = when { score >= 80 -> Color(.39f,.53f,.42f); score >= 65 -> Color(.60f,.62f,.34f); score >= 45 -> Color(.76f,.63f,.27f); else -> Color(.68f,.34f,.31f) }
        val blob = Path().apply {
            moveTo(w*.18f,h*.44f)
            cubicTo(w*.12f,h*.25f,w*.21f,h*.13f,w*.33f,h*.14f)
            cubicTo(w*.45f,h*.10f,w*.55f,h*.18f,w*.65f,h*.13f)
            cubicTo(w*.78f,h*.08f,w*.90f,h*.27f,w*.84f,h*.45f)
            cubicTo(w*.86f,h*.64f,w*.76f,h*.80f,w*.69f,h*.79f)
            cubicTo(w*.59f,h*.80f,w*.47f,h*.90f,w*.36f,h*.84f)
            cubicTo(w*.20f,h*.77f,w*.10f,h*.60f,w*.18f,h*.44f); close()
        }
        drawPath(blob, fill)
        val ink = Color.Black.copy(alpha = .92f)
        for (x in listOf(.37f,.63f)) drawCircle(ink, 2.4.dp.toPx(), androidx.compose.ui.geometry.Offset(w*x,h*.41f))
        val mouth = Path().apply {
            if (score >= 80) {
                moveTo(w*.31f,h*.55f); quadraticTo(w*.37f,h*.68f,w*.43f,h*.55f)
                moveTo(w*.57f,h*.55f); quadraticTo(w*.63f,h*.68f,w*.69f,h*.55f)
            } else if (score >= 45) { moveTo(w*.35f,h*.61f); lineTo(w*.65f,h*.61f) }
            else { moveTo(w*.31f,h*.68f); quadraticTo(w*.50f,h*.55f,w*.69f,h*.68f) }
        }
        drawPath(mouth, ink, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}
