package com.mountzou.flytly

import android.os.Bundle
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mountzou.flytly.atlas.AtlasScreen
import com.mountzou.flytly.auth.SignInScreen
import com.mountzou.flytly.auth.AuthenticationSession
import com.mountzou.flytly.account.accountNamespace
import com.mountzou.flytly.account.AccountDetails
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import com.mountzou.flytly.startup.AtlasLoadingView
import com.mountzou.flytly.ui.theme.FlytlyTheme
import com.mountzou.flytly.ui.theme.restoreContentSystemBars
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { it.remove();restoreContentSystemBars(this) }
        super.onCreate(savedInstanceState)
        com.mountzou.flytly.atlas.AtlasPerformance.mark("activity.created")
        enableEdgeToEdge()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val namespace = intent.getStringExtra("ui_test_namespace")?.takeIf {
            debuggable &&
                it.matches(Regex("[a-zA-Z0-9-]{1,64}"))
        }
        val atlasPreview = debuggable && intent.getStringExtra("ui_preview") == "atlas"
        val themePreview = intent.getStringExtra("ui_preview_theme")?.takeIf { debuggable && it in listOf("light", "dark") }
        setContent {
            if (atlasPreview) AtlasScreen(lifecycleOwner = this, storageNamespace = namespace)
            else {
                val session: AuthenticationSession = viewModel()
                val auth by session.state.collectAsState()
                if (auth.restored) com.mountzou.flytly.atlas.AtlasPerformance.mark("auth.composed")
                if (!auth.restored) {
                    val preference = remember { getSharedPreferences("atlas_settings",MODE_PRIVATE).getString("appearance","light") }
                    val dark = themePreview?.let { it == "dark" } ?: if (preference == "system") isSystemInDarkTheme() else preference == "dark"
                    SideEffect {
                        WindowCompat.getInsetsController(window,window.decorView).apply {
                            isAppearanceLightStatusBars = !dark
                            isAppearanceLightNavigationBars = !dark
                        }
                    }
                    FlytlyTheme(dark) { AtlasLoadingView(Modifier.fillMaxSize()) }
                }
                else if (auth.user != null && auth.repository != null) key(auth.user!!.uid) {
                    AtlasScreen(this, accountNamespace(auth.user!!.uid), auth.repository,
                        auth.user!!.displayName ?: auth.user!!.email ?: "Flytly account", session::signOut,
                        accountDetails = AccountDetails(auth.user!!.displayName,auth.user!!.email,auth.user!!.providerData.map { it.providerId }),
                        accountBusy = auth.deleting,accountError = auth.accountError,
                        onDeleteAccount = { session.deleteAccount(this) },onClearAccountError = session::clearAccountError)
                } else SignInScreen(this, namespace, themePreview, auth.busy, auth.error) { session.signIn(this, it) }
                DisposableEffect(lifecycle) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_START) session.validateSession()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
            }
        }
    }
}

