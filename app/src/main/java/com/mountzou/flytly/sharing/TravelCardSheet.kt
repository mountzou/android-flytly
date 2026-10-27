package com.mountzou.flytly.sharing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.caverock.androidsvg.SVG
import com.mountzou.flytly.R
import com.mountzou.flytly.atlas.placeAccent
import com.mountzou.flytly.atlas.placeOnAccent
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TravelCardSheet(snapshot: TravelCardSnapshot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var story by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf<TravelCardExport?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var instagram by remember { mutableStateOf(instagramAvailable(context)) }
    var sharingError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) instagram = instagramAvailable(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(snapshot, attempt) {
        failed = false
        try { export = (SharingTestOverrides.export ?: TravelCardRenderer::export)(context, snapshot) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    fun share(storyImage: Boolean) {
        val ready = export ?: return
        try { context.startActivity(shareImageIntent(context, if (storyImage) ready.storyFile else ready.cardFile)) }
        catch (_: Exception) { sharingError = "Your image couldn’t be shared. Please try again." }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp,4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))) }) {
        Column(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * .86f).dp).testTag("travelShareSheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Your travel card", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    Text("All your visited countries, on one card.", Modifier.padding(top = 6.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onDismiss, Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)).testTag("closeTravelSharePreview")) {
                    Icon(painterResource(R.drawable.ic_atlas_close), "Close preview", Modifier.size(14.dp))
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val availableHeight = (maxHeight - 70.dp).coerceAtLeast(180.dp)
                val availableWidth = maxWidth - 48.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ready = export
                    if (ready != null) {
                        Row(Modifier.widthIn(max = 400.dp).fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)).padding(2.dp).testTag("travelShareFormatPicker")) {
                            listOf(false to "Card", true to "Story").forEach { (isStory, label) ->
                                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                                    .background(if (story == isStory) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(role = Role.Tab) { story = isStory }.semantics { selected = story == isStory }, contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                        val ratio = if (story) 1080f / 1920 else 1080f / 1350
                        val previewWidth = minOf(400.dp, availableWidth, availableHeight * ratio)
                        Image((if (story) ready.story else ready.card).asImageBitmap(), snapshot.summary,
                            Modifier.padding(top = 20.dp).width(previewWidth).aspectRatio(ratio).clip(RoundedCornerShape(24.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .1f), RoundedCornerShape(24.dp))
                                .testTag("travelShareCardPreview").semantics { stateDescription = snapshot.countryNames.joinToString(", ") }, contentScale = ContentScale.Fit)
                    } else if (failed) Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Card unavailable", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("Your travel card couldn’t be created. Please try again.", Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                        TextButton(onClick = { attempt++ }) { Text("Try again") }
                    } else CircularProgressIndicator(Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (export != null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareButton(if (instagram) "Share to Instagram Stories" else "Share story image", true, "shareInstagramStoryButton", instagram = true) {
                    if (instagram) {
                        story = true
                        try { shareToInstagram(context, export!!.storyFile) }
                        catch (_: Exception) { instagram = false; sharingError = "Instagram couldn’t open. You can use Share story image to save the image and add it to your story." }
                    } else share(true)
                }
                if (!instagram) Text("Save the story image, then add it in Instagram.", fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                ShareButton("Share card", false, "shareTravelCardButton") { share(false) }
            }
        }
    }
    sharingError?.let { message -> AlertDialog(onDismissRequest = { sharingError = null }, title = { Text("Sharing unavailable") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { sharingError = null }) { Text("OK") } }) }
}

@Composable
private fun ShareButton(text: String, prominent: Boolean, tag: String, instagram: Boolean = false, onClick: () -> Unit) {
    Surface(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag), shape = RoundedCornerShape(12.dp),
        color = if (prominent) placeAccent() else MaterialTheme.colorScheme.background,
        contentColor = if (prominent) placeOnAccent() else MaterialTheme.colorScheme.onSurface,
        border = if (prominent) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (instagram) InstagramGlyph(Modifier.size(20.dp)) else Icon(painterResource(R.drawable.ic_atlas_share), null, Modifier.size(20.dp))
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InstagramGlyph(modifier: Modifier) {
    val context = LocalContext.current
    val image = remember {
        android.graphics.Bitmap.createBitmap(60,60,android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
            SVG.getFromAsset(context.assets,"sharing/instagram.svg").apply { setDocumentWidth(60f); setDocumentHeight(60f) }.renderToCanvas(android.graphics.Canvas(bitmap))
        }.asImageBitmap()
    }
    Image(image,null,modifier,colorFilter = ColorFilter.tint(LocalContentColor.current))
}
