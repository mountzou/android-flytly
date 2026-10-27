package com.mountzou.flytly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.content.ContextWrapper
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.mountzou.flytly.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color.White,
    secondary = Color(0xFF757575), onSecondary = Color.White,
    background = Color(0xFFFBFCFD), onBackground = Color(0xFF111111),
    surface = Color(0xFFFBFCFD), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEAECF0), onSurfaceVariant = Color(0xFF757575),
    outline = Color(0xFFADB0B5), surfaceTint = Color(0xFF111111)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF111111),
    secondary = Color(0xFFA3A3A3), onSecondary = Color(0xFF111111),
    background = Color(0xFF111111), onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF111111), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF666666), surfaceTint = Color(0xFFF2F2F2)
)

@Composable
fun FlytlyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        var context = view.context
        while (context is ContextWrapper && context !is Activity && context.baseContext !== context) context = context.baseContext
        (context as? Activity)?.let { activity ->
            activity.window.decorView.setTag(R.id.flytly_content_dark,darkTheme)
            applyContentSystemBars(activity,darkTheme)
        }
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}

private fun applyContentSystemBars(activity: Activity, dark: Boolean) {
    val window = activity.window
    if (activity is ComponentActivity) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style,navigationBarStyle = style)
    }
    WindowCompat.getInsetsController(window,window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

/** SplashScreen compat restores post-splash theme flags; keep the rendered app's contrast. */
internal fun restoreContentSystemBars(activity: Activity) {
    (activity.window.decorView.getTag(R.id.flytly_content_dark) as? Boolean)?.let { applyContentSystemBars(activity,it) }
}
