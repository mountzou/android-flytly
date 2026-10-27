package com.mountzou.flytly.auth

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mountzou.flytly.R
import com.mountzou.flytly.ui.theme.AtlasFont
import com.mountzou.flytly.ui.theme.FlytlyTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val LOGIN_PRIVACY_URL = "https://docs.google.com/document/d/e/2PACX-1vSnviL7l7YvEoQZgqIG3h5nlribVq8udf3T1zmKWQ9ditFUY0GnMdHa1VY8C5qlD7fJyKkDlAx1HV5k/pub"

/** Shares the iOS presentation; provider actions are supplied by the Firebase session. */
@Composable
fun SignInScreen(activity: Activity, storageNamespace: String? = null, themeOverride: String? = null, busy: Boolean = false, error: String? = null, onProvider: (String) -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(storageNamespace?.let { "atlas_settings_$it" } ?: "atlas_settings", Context.MODE_PRIVATE) }
    val appearance = themeOverride ?: preferences.getString("appearance", "light")
    val dark = if (appearance == "system") isSystemInDarkTheme() else appearance == "dark"
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" }
    val uri = LocalUriHandler.current
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    FlytlyTheme(darkTheme = dark) {
        SignInContent(dark, version, busy, error, onProvider, onPrivacy = { uri.openUri(LOGIN_PRIVACY_URL) })
    }
}

// Original font, with its optical instance matched against the iOS headline capture.
private val LoginHeadlineFont = FontFamily(Font(R.font.bricolage_login_headline, FontWeight.SemiBold))

private fun loginType(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = if (size == 36) LoginHeadlineFont else AtlasFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

@Composable
private fun SignInContent(dark: Boolean, version: String, busy: Boolean, error: String?, onProvider: (String) -> Unit, onPrivacy: () -> Unit) {
    val paper = if (dark) Color(0xFF111111) else Color(0xFFFBFCFD)
    val ink = if (dark) Color(0xFFF2F2F2) else Color(0xFF111111)
    val secondary = if (dark) Color(0xFFA3A3A3) else Color(0xFF757575)
    Surface(Modifier.fillMaxSize().testTag("signInScreen"), color = paper, contentColor = ink) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = constraints.maxHeight
            var footerHeight by remember { mutableIntStateOf(0) }
            val minimumHeight = with(LocalDensity.current) { 208.dp.roundToPx() } + footerHeight + (viewport * .05).roundToInt()
            // Keep the exact full-viewport iOS composition when it fits. In the
            // scrolling layout, exclude Android's navigation bar from the visible
            // scroll area so bringing the footer into view cannot place it under it.
            val scrollBottom = if (minimumHeight > viewport) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
            LoginViewportLayout(viewport, Modifier.fillMaxWidth().padding(bottom = scrollBottom).verticalScroll(rememberScrollState())) {
                Image(painterResource(R.drawable.login_launch_logo), null, Modifier.size(160.dp).testTag("loginLogo"),
                    contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ink))
                Column(Modifier.onSizeChanged { footerHeight = it.height }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Your world,\none place at a time.", Modifier.fillMaxWidth().testTag("loginHeadline"),
                            style = loginType(36, 43, FontWeight.SemiBold), textAlign = TextAlign.Center)
                        Text("Sign in to start your travel story.", Modifier.fillMaxWidth(),
                            style = loginType(17, 20), color = secondary, textAlign = TextAlign.Center)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProviderButton("Apple", dark, !busy) { onProvider("Apple") }
                        ProviderButton("Google", dark, !busy) { onProvider("Google") }
                    }
                    if (error != null) Text(error, Modifier.testTag("loginError"), style = loginType(13, 16),
                        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("By signing in, you acknowledge our", style = loginType(13, 16),
                                color = secondary, textAlign = TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.testTag("loginPrivacy").clickable(role = Role.Button, onClick = onPrivacy),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Privacy Policy", style = loginType(13, 16), color = secondary)
                                    Icon(painterResource(R.drawable.ic_login_external_link), null, Modifier.size(12.dp), tint = secondary)
                                }
                                Text("terms.", style = loginType(13, 16), color = secondary)
                            }
                        }
                        Text("Version $version", Modifier.testTag("appVersion"), style = loginType(11, 13), color = secondary)
                    }
                }
            }
        }
    }
}

/** Same layout algorithm as iOS LoginViewportLayout: full viewport, 40% logo anchor,
 * 5% bottom inset, 56pt horizontal inset and scrolling when content no longer fits. */
@Composable
private fun LoginViewportLayout(viewportHeight: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val logoSize = 160.dp.roundToPx()
        val gap = 24.dp.roundToPx()
        val bottom = (viewportHeight * .05).roundToInt()
        val footerWidth = min(420.dp.roundToPx(), max(0, width - 56.dp.roundToPx()))
        val logo = measurables[0].measure(Constraints.fixed(logoSize, logoSize))
        val footer = measurables[1].measure(Constraints(minWidth = footerWidth, maxWidth = footerWidth))
        val height = max(viewportHeight, 24.dp.roundToPx() + logoSize + gap + footer.height + bottom)
        val footerTop = height - bottom - footer.height
        val center = min((viewportHeight * .4).roundToInt(), footerTop - gap - logoSize / 2)
        layout(width, height) {
            logo.placeRelative((width - logo.width) / 2, center - logo.height / 2)
            footer.placeRelative((width - footer.width) / 2, footerTop)
        }
    }
}

@Composable
private fun ProviderButton(provider: String, dark: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val apple = provider == "Apple"
    val background = if (apple) { if (dark) Color.White else Color.Black }
        else if (dark) Color(0xFF131313) else Color.White
    val foreground = if (apple) { if (dark) Color.Black else Color.White }
        else if (dark) Color(0xFFE6E6E6) else Color(0xFF1F1F1F)
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(52.dp).testTag(if (apple) "signInWithApple" else "signInWithGoogle")
        .alpha(if (enabled) 1f else .5f).clip(shape).background(background)
        .then(if (!apple) Modifier.border(1.dp, Color.Gray.copy(alpha = .5f), shape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(if (apple) R.drawable.login_apple_logo else R.drawable.login_google_logo), null,
                    if (apple) Modifier.fillMaxSize() else Modifier.size(18.dp), contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(if (apple) foreground else if (dark) Color.White else Color.Black))
            }
            Box {
                // Both providers reserve the widest title, matching the shared iOS label.
                Text("Sign in with Apple", Modifier.alpha(0f).clearAndSetSemantics {}, style = loginType(17, 20, FontWeight.Medium))
                Text("Sign in with Google", Modifier.alpha(0f).clearAndSetSemantics {}, style = loginType(17, 20, FontWeight.Medium))
                Text("Sign in with $provider", style = loginType(17, 20, FontWeight.Medium), color = foreground, maxLines = 1)
            }
        }
    }
}
