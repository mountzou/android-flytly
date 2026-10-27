package com.mountzou.flytly.startup

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mountzou.flytly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PREPARING_FLYTLY = "Preparing your Flytly.."

/** Same 32pt masks, 18pt spacing, typography and copy as iOS AtlasLoadingView. */
@Composable
internal fun AtlasLoadingView(modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp)
        .testTag("flytlyLoadingView").semantics(mergeDescendants = true) {
            contentDescription = PREPARING_FLYTLY;liveRegion = LiveRegionMode.Polite
        },horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.Center) {
        MetaballAnimation()
        Spacer(Modifier.height(18.dp))
        Text(PREPARING_FLYTLY,style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun MetaballAnimation() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) { MetaballImageView(context) }
    val animation by produceState<Drawable?>(null,context) {
        value = withContext(Dispatchers.IO) { decodeMetaballs(context) }
    }
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    DisposableEffect(view,owner) {
        view.active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _,_ -> view.active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);view.active = false }
    }
    AndroidView(factory = { view },update = { it.configure(animation,ink) },
        modifier = Modifier.size(32.dp).testTag("metaballAnimation").clearAndSetSemantics {})
}

/** Native WebP playback on API 28+; the same frame sequence on Android 8. */
internal fun decodeMetaballs(context: Context): Drawable {
    if (Build.VERSION.SDK_INT >= 28) {
        return ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources,R.drawable.flytly_metaballs)).apply {
            if (this is AnimatedImageDrawable) repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
    }
    val sprite = BitmapFactory.decodeResource(context.resources,R.drawable.flytly_metaball_frames)
    return AnimationDrawable().apply {
        isOneShot = false
        repeat(96) { i ->
            val frame = Bitmap.createBitmap(sprite,(i%8)*96,(i/8)*96,96,96)
            val duration = Math.round((i+1)*1600.0/96).toInt()-Math.round(i*1600.0/96).toInt()
            addFrame(BitmapDrawable(context.resources,frame),duration)
        }
    }
}

/** Owns playback: stop when hidden/detached/backgrounded, respect system motion setting. */
internal class MetaballImageView(context: Context) : ImageView(context) {
    private var motion: Drawable? = null
    private val still = context.getDrawable(R.drawable.flytly_metaball_reduced)!!
    var active = false
        set(value) { field = value;updatePlayback() }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { updatePlayback() }
    }
    init { scaleType = ScaleType.FIT_XY;importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun configure(animation: Drawable?, ink: Int) {
        if (motion !== animation) { (motion as? Animatable)?.stop();motion = animation }
        colorFilter = PorterDuffColorFilter(ink,PorterDuff.Mode.SRC_IN)
        updatePlayback()
    }
    private fun updatePlayback() {
        val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
        val selected = if (reduced) still else motion ?: still
        if (drawable !== selected) { (drawable as? Animatable)?.stop();setImageDrawable(selected) }
        val animation = selected as? Animatable
        if (active && isAttachedToWindow && windowVisibility == VISIBLE && !reduced) {
            if (animation?.isRunning == false) animation.start()
        } else animation?.stop()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        updatePlayback()
    }
    override fun onDetachedFromWindow() {
        (motion as? Animatable)?.stop()
        context.contentResolver.unregisterContentObserver(observer)
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // ImageView's constructor can dispatch this before subclass fields initialize.
        if (motion != null) updatePlayback()
    }
}
