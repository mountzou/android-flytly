package com.mountzou.flytly.atlas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

private val placeArtCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** The same source paths, largest-landmass framing, opacity and projected marker as iOS. */
@Composable
internal fun PlaceArtwork(id: String, foreground: Color, paper: Color, modifier: Modifier = Modifier,
                          highlighted: Set<String> = emptySet(), city: AtlasCity? = null,
                          overview: Boolean = false, description: String? = null) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val key = "$id:${foreground.toArgb()}:${paper.toArgb()}:$size:$density:${highlighted.sorted()}:${city?.id}:$overview"
    val bitmap by produceState<ImageBitmap?>(placeArtCache[key], key) {
        if (size.width <= 0 || size.height <= 0) return@produceState
        value = withContext(Dispatchers.IO) {
            placeArtCache[key] ?: run {
                var xml = context.assets.open("atlas/art/$id.svg").bufferedReader().use { it.readText() }
                val color = String.format(java.util.Locale.ROOT, "#%06X", foreground.toArgb() and 0xFFFFFF)
                xml = Regex("<path id=\"([^\"]+)\" fill=\"#[A-Fa-f0-9]+\"").replace(xml) { match ->
                    "<path id=\"${match.groupValues[1]}\" fill=\"$color\" fill-opacity=\"${if (match.groupValues[1] in highlighted) .9 else .16}\""
                }
                val svg = SVG.getFromString(xml)
                val bounds = RectF(svg.documentViewBox)
                val isCountry = id !in travelContinents
                if (isCountry) {
                    // prepare_atlas_art.py adds a uniform 6% gutter; source views frame
                    // the original largest polygon, then add per-axis gutters for overviews.
                    val gutter = max(bounds.width(), bounds.height()) / 1.12f * .06f
                    bounds.inset(gutter, gutter)
                }
                val marker = city?.let {
                    val latitude = Math.toRadians(it.latitude.coerceIn(-85.051129, 85.051129))
                    ((it.longitude + 180) / 360).toFloat() to ((1 - ln(tan(PI / 4 + latitude / 2)) / PI) / 2).toFloat()
                }
                marker?.let { (x,y) -> bounds.union(x, y) }
                if (overview) bounds.inset(-bounds.width() * .06f, -bounds.height() * .06f)
                svg.setDocumentViewBox(bounds.left, bounds.top, bounds.width(), bounds.height())
                svg.setDocumentWidth(size.width.toFloat()); svg.setDocumentHeight(size.height.toFloat())
                val image = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image)
                svg.renderToCanvas(canvas, RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()))
                marker?.let { (x,y) ->
                    val scale = min(size.width / bounds.width(), size.height / bounds.height())
                    val px = (size.width - bounds.width() * scale) / 2 + (x - bounds.left) * scale
                    val py = (size.height - bounds.height() * scale) / 2 + (y - bounds.top) * scale
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color = paper.toArgb(); canvas.drawCircle(px, py, 9 * density, paint)
                    paint.color = foreground.toArgb(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
                    canvas.drawCircle(px, py, 9 * density, paint)
                    paint.style = Paint.Style.FILL; canvas.drawCircle(px, py, 5 * density, paint)
                }
                image.asImageBitmap().also { placeArtCache.put(key, it) }
            }
        }
    }
    Box(modifier.onSizeChanged { size = it }) {
        bitmap?.let { Image(it, description, Modifier.matchParentSize().testTag("art-$id"), contentScale = ContentScale.FillBounds) }
    }
}
