package com.mountzou.flytly.sharing

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import com.mountzou.flytly.R
import com.mountzou.flytly.atlas.*
import java.io.File
import java.util.UUID

/** Captured when sharing starts: all visited countries, no identity, preferences or GPS. */
internal data class TravelCardSnapshot(val countryIDs: Set<String>, val countryNames: List<String>, val continentCount: Int) {
    val summary get() = "${countryIDs.size} ${if (countryIDs.size == 1) "country" else "countries"} visited across $continentCount ${if (continentCount == 1) "continent" else "continents"}"
    companion object {
        fun capture(atlas: AtlasCatalog, travels: TravelSnapshot): TravelCardSnapshot {
            val countries = PlaceCollection(atlas, travels, PlaceStatus.VISITED).countries
            return TravelCardSnapshot(countries.mapTo(linkedSetOf()) { it.id }, countries.map { it.name }, countries.mapNotNull { it.travelContinent() }.toSet().size)
        }
    }
}
internal data class TravelCardExport(val card: Bitmap, val story: Bitmap, val cardFile: File, val storyFile: File)

internal object TravelCardRenderer {
    private val paper = Color.rgb(251, 252, 253)
    private val ink = Color.rgb(17, 17, 17)
    suspend fun export(context: Context, snapshot: TravelCardSnapshot): TravelCardExport = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Keep recent exports alive after dismissal for recipients still reading their granted URI.
        val root = File(context.cacheDir, "travel-cards").apply { mkdirs() }
        root.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 7 * 86_400_000L }?.forEach { it.deleteRecursively() }
        val folder = File(root, UUID.randomUUID().toString()).apply { check(mkdirs()) }
        val card = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val story = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        try {
            Canvas(card).apply {
                drawColor(paper); scale(3f, 3f)
                val normal = ResourcesCompat.getFont(context, R.font.bricolage_grotesque)!!
                val medium = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_medium)!!
                val bold = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_bold)!!
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                fun text(value: String, x: Float, top: Float, size: Float, typeface: Typeface = normal, color: Int = ink) {
                    // iOS freezes exports at DynamicType.medium (16/17 of the default body size).
                    paint.color = color; paint.textSize = size * 16f / 17f; paint.typeface = typeface; paint.style = Paint.Style.FILL
                    drawText(value, x, top - paint.fontMetrics.ascent, paint)
                }
                text("My travels", 28f, 28f, 34f, bold)
                val count = atlasNumber(snapshot.countryIDs.size)
                text(count, 28f, 69f, 80f, bold)
                paint.typeface = bold; paint.textSize = 80f * 16f / 17f
                val left = 28 + paint.measureText(count) + 12
                val secondary = Color.rgb(117, 117, 117)
                text(if (snapshot.countryIDs.size == 1) "country" else "countries", left, 101f, 22f, medium)
                text("visited", left, 126f, 17f, normal, secondary)
                var xml = context.assets.open("sharing/world.svg").bufferedReader().use { it.readText() }
                // Visited paths last, matching the iOS small-island visibility rule.
                val paths = Regex("<path[^>]+/>").findAll(xml).map { it.value }.toList()
                val ordered = paths.sortedBy { path -> snapshot.countryIDs.any { "id=\"$it\"" in path } }
                xml = xml.substringBefore("<path") + ordered.joinToString("") { path ->
                    val visited = snapshot.countryIDs.any { "id=\"$it\"" in path }
                    path.replace("fill=\"#DFDFE0\"", "fill=\"${if (visited) "#242424" else "#DFE0E1"}\" stroke=\"${if (visited) "#111111" else "#FBFCFD"}\" stroke-width=\"${if (visited) ".55" else ".4"}\"")
                } + "</svg>"
                SVG.getFromString(xml).apply { setDocumentWidth(304f); setDocumentHeight(176f) }
                    .renderToCanvas(this, RectF(28f, 170f, 332f, 346f))
                paint.color = ink; drawCircle(31f, 356.5f, 3f, paint)
                text("Visited countries", 40f, 350f, 11f)
                val continentText = "${snapshot.continentCount} of 6 continents"
                paint.textSize = 11f * 16f / 17f; paint.typeface = normal
                val continentWidth = paint.measureText(continentText)
                text(continentText, 332f - continentWidth, 350f, 11f, normal, secondary)
                paint.color = Color.rgb(223,224,225); paint.strokeWidth = 1f
                drawLine(28f, 383f, 332f, 383f, paint)
                val logo = BitmapFactory.decodeResource(context.resources, R.drawable.login_launch_logo)
                paint.colorFilter = PorterDuffColorFilter(ink,PorterDuff.Mode.SRC_IN)
                drawBitmap(logo, null, RectF(28f, 396f, 54f, 422f), paint)
                paint.colorFilter = null;logo.recycle()
                text("flytly", 62f, 396f, 22f, bold)
                paint.textSize = 11f * 16f / 17f; paint.typeface = normal
                val tagline = "A world of memories."
                val tagWidth = paint.measureText(tagline)
                text(tagline, 332f - tagWidth, 403f, 11f, normal, secondary)
            }
            Canvas(story).apply {
                drawColor(paper); scale(3f,3f)
                val rect = RectF(16f,115f,344f,525f)
                save(); clipPath(Path().apply { addRoundRect(rect,20f,20f,Path.Direction.CW) })
                drawBitmap(card, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); restore()
                drawRoundRect(rect,20f,20f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(223,224,225); style = Paint.Style.STROKE; strokeWidth = 1f })
            }
            val cardFile = File(folder, "Flytly-travels.png")
            val storyFile = File(folder, "Flytly-travels-story.png")
            for ((bitmap, file) in listOf(card to cardFile, story to storyFile)) file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            TravelCardExport(card, story, cardFile, storyFile)
        } catch (error: Throwable) { card.recycle(); story.recycle(); folder.deleteRecursively(); throw error }
    }
}
