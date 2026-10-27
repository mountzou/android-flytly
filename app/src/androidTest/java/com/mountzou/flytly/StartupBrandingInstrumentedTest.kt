package com.mountzou.flytly

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.mountzou.flytly.startup.*
import com.mountzou.flytly.ui.theme.FlytlyTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Isolated UI and resource tests; no production authentication or account mutation. */
@SdkSuppress(minSdkVersion = 33)
class StartupBrandingInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var darkMode by mutableStateOf(false)
    private fun launch(dark: Boolean = false) {
        darkMode = dark
        scenario = ActivityScenario.launch(Intent(context,MainActivity::class.java)
            .putExtra("ui_preview","atlas").putExtra("ui_test_namespace",UUID.randomUUID().toString()))
        scenario.onActivity { it.setContent { FlytlyTheme(darkMode) { AtlasLoadingView(Modifier.fillMaxSize()) } } }
        compose.onNodeWithTag("flytlyLoadingView").assertExists()
        compose.waitUntil(10_000) { native()?.drawable is AnimatedImageDrawable }
        compose.waitForIdle()
    }
    private fun showLoader(dark: Boolean) {
        scenario.onActivity { darkMode = dark }
        compose.waitUntil(10_000) {
            val pixels = compose.onNodeWithTag("flytlyLoadingView").captureToImage().toPixelMap()
            pixels[4,pixels.height/2].toArgb() == (if (dark) 0xFF111111.toInt() else 0xFFFBFCFD.toInt())
        }
    }
    private fun native(): MetaballImageView? {
        var result: MetaballImageView? = null
        scenario.onActivity { activity ->
            fun find(view: View): MetaballImageView? = when(view) {
                is MetaballImageView -> view
                is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
                else -> null
            }
            result = find(activity.window.decorView)
        }
        return result
    }
    private fun capture(name: String) {
        instrumentation.uiAutomation.waitForIdle(200,2_000)
        val directory = File(context.getExternalFilesDir(null),"startup-parity").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }

    @Test fun lightAndDarkLoaderHaveSourceCopyAndCompactAccessibleAnimation() {
        launch()
        compose.onNodeWithContentDescription(PREPARING_FLYTLY).assertIsDisplayed()
        capture("android-loader-light")
        showLoader(true)
        compose.waitUntil(10_000) { native()?.drawable is AnimatedImageDrawable }
        compose.waitForIdle()
        scenario.onActivity {
            val bars = WindowCompat.getInsetsController(it.window,it.window.decorView)
            assertFalse(bars.isAppearanceLightNavigationBars);assertFalse(bars.isAppearanceLightStatusBars)
        }
        capture("android-loader-dark")
        scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse((native()?.drawable as? Animatable)?.isRunning == true)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { (native()?.drawable as? Animatable)?.isRunning == true }
    }

    @Test fun animatedMasksKeepMovingDuringAMainThreadStall() {
        launch()
        assertTrue(Build.VERSION.SDK_INT >= 28)
        val bounds = Rect()
        val view = native()!!
        scenario.onActivity { view.getGlobalVisibleRect(bounds) }
        val composeBounds = compose.onNodeWithTag("metaballAnimation",useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        bounds.set(composeBounds.left.toInt(),composeBounds.top.toInt(),composeBounds.right.toInt(),composeBounds.bottom.toInt())
        val entered = CountDownLatch(1);val finished = CountDownLatch(1)
        instrumentation.targetContext.mainExecutor.execute {
            entered.countDown();Thread.sleep(3_000);finished.countDown()
        }
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        val fingerprints = mutableSetOf<Int>()
        val timings = mutableListOf<String>()
        repeat(4) {
            Thread.sleep(110)
            // takeScreenshot() synchronizes input transactions with the blocked UI.
            // SurfaceFlinger's shell capture samples the independent render thread directly.
            val screen = instrumentation.uiAutomation.executeShellCommand("screencap -p").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { BitmapFactory.decodeStream(it)!! }
            }
            val pixels = IntArray(bounds.width()*bounds.height())
            screen.getPixels(pixels,0,bounds.width(),bounds.left,bounds.top,bounds.width(),bounds.height())
            timings.add("${bounds.toShortString()}:${pixels.contentHashCode()}:duringStall=${finished.count > 0}")
            if (finished.count > 0) fingerprints.add(pixels.contentHashCode())
            screen.recycle()
        }
        assertTrue(finished.await(3,TimeUnit.SECONDS))
        instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nLOADER_STALL_SAMPLES=$timings\n") })
        assertTrue("Render-thread loader froze while the UI thread was busy",fingerprints.size > 1)
    }

    @Test fun disabledSystemAnimationsSelectAStillMaskAndRestoreTheSetting() {
        launch()
        val previous = Settings.Global.getString(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE)
        fun command(value: String?) {
            val text = if (value == null) "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $value"
            instrumentation.uiAutomation.executeShellCommand(text).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
        try {
            command("0")
            compose.waitUntil(5_000) { native()?.drawable !is Animatable }
            capture("android-loader-reduced-motion")
        } finally { command(previous) }
    }

    @Test fun installedAdaptiveIconsHaveSourceForegroundAndMonochromeInBothThemes() {
        val directory = File(context.getExternalFilesDir(null),"startup-parity").apply { mkdirs() }
        for ((name,mode) in listOf("light" to Configuration.UI_MODE_NIGHT_NO,"dark" to Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(context.resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
            val themed = context.createConfigurationContext(config)
            val icon = themed.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
            assertNotNull(icon.foreground);assertNotNull(icon.monochrome)
            val bitmap = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
            icon.setBounds(0,0,512,512);icon.draw(Canvas(bitmap))
            File(directory,"android-icon-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
