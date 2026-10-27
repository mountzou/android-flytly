package com.mountzou.flytly

import android.app.Application
import android.content.pm.ApplicationInfo
import com.mountzou.flytly.atlas.AtlasPerformance
import com.mountzou.flytly.atlas.AtlasRepository
import com.google.firebase.auth.FirebaseAuth
import com.mountzou.flytly.auth.installAppAttestation
import kotlinx.coroutines.*

class FlytlyApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        AtlasPerformance.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        installAppAttestation()
        // Public, offline data can load while Compose and the existing session are restored.
        // Signed-out users keep the lightweight login startup; the normal loader handles errors.
        startupScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null && !user.isAnonymous && user.providerData.any { it.providerId in listOf("google.com", "apple.com") }) {
                    AtlasRepository.load(this@FlytlyApplication)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Foreground loading retains the normal retry/error UI. */ }
        }
    }
}
