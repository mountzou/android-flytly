package com.mountzou.flytly.atlas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.caverock.androidsvg.SVG
import com.mountzou.flytly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val artworkCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

@Composable
internal fun AtlasArtwork(id: String, dark: Boolean, modifier: Modifier = Modifier,
                          highlighted: Set<String> = emptySet(), description: String? = null) {
    val context = LocalContext.current
    val key = "art:$id:$dark:${highlighted.sorted().joinToString(",")}"
    val bitmap by produceState<ImageBitmap?>(artworkCache[key], key) {
        value = withContext(Dispatchers.IO) {
            artworkCache[key] ?: runCatching {
                var xml = context.assets.open("atlas/art/$id.svg").bufferedReader().use { it.readText() }
                for (country in highlighted) xml = xml.replace("id=\"$country\" fill=\"#DADEE3\"", "id=\"$country\" fill=\"#2D2C2F\"")
                xml = xml.replace("#DADEE3", if (dark) "#303030" else "#DADEE3")
                    .replace("#2D2C2F", if (dark) "#AAAAAA" else "#2D2C2F")
                val image = Bitmap.createBitmap(800, 440, Bitmap.Config.ARGB_8888)
                SVG.getFromString(xml).apply { setDocumentWidth(800f); setDocumentHeight(440f) }
                    .renderToCanvas(Canvas(image), RectF(0f, 0f, 800f, 440f))
                image.asImageBitmap().also { artworkCache.put(key, it) }
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, description, modifier.testTag("art-$id"), contentScale = ContentScale.Fit) }
}

@Composable
internal fun CountryFlag(countryID: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, countryID) {
        value = withContext(Dispatchers.IO) {
            artworkCache["flag:$countryID"] ?: runCatching {
                val mapping = JSONObject(context.assets.open("atlas/country-flags.json").bufferedReader().use { it.readText() })
                val code = mapping.optString(countryID)
                if (code.isEmpty()) return@withContext null
                val image = Bitmap.createBitmap(168, 126, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image).apply { drawColor(android.graphics.Color.WHITE) }
                SVG.getFromAsset(context.assets, "atlas/flags/$code.svg").apply { setDocumentWidth(168f); setDocumentHeight(126f) }
                    .renderToCanvas(canvas, RectF(0f, 0f, 168f, 126f))
                image.asImageBitmap().also { artworkCache.put("flag:$countryID", it) }
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(image, null, modifier.testTag("flag-$countryID"), contentScale = ContentScale.Fit)
    else Icon(painterResource(R.drawable.ic_world), null, modifier)
}

@Composable
internal fun PreferenceArtwork(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(artworkCache[name], name) {
        value = withContext(Dispatchers.IO) {
            artworkCache[name] ?: runCatching {
                val image = Bitmap.createBitmap(84, 84, Bitmap.Config.ARGB_8888)
                SVG.getFromAsset(context.assets, "atlas/preferences/$name.svg")
                    .apply { setDocumentWidth(84f); setDocumentHeight(84f) }.renderToCanvas(Canvas(image))
                image.asImageBitmap().also { artworkCache.put(name, it) }
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, null, modifier.testTag("preference-$name"), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface)) }
}
