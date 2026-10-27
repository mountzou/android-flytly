package com.mountzou.flytly.jev

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.mountzou.flytly.preferences.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

internal class DestinationFitClient(
    private val request: suspend (Map<String, String>) -> Any? = { payload ->
        FirebaseFunctions.getInstance("europe-west3").getHttpsCallable("analyzeDestinationFit")
            .withTimeout(60, TimeUnit.SECONDS).call(payload).await().data
    }
) {
    suspend fun analyze(destination: String, profile: PreferencesState): DestinationFit {
        if (!profile.hasLoaded || profile.loadFailed || profile.isLoading || profile.isSaving)
            throw DestinationFitException(FitFailure.PROFILE_UNAVAILABLE)
        if (profile.status != OnboardingStatus.COMPLETED) throw DestinationFitException(FitFailure.PROFILE_INCOMPLETE)
        val values = profile.values
        return try {
            val data = request(mapOf("destination" to destination, "profileDescription" to values.jevProfileDescription()))
            currentCoroutineContext().ensureActive()
            decodeDestinationFit(data, destination, values)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: DestinationFitException) { throw error }
        catch (error: FirebaseFunctionsException) {
            JevTestOverrides.diagnostic?.invoke("callable:${error.code.name}")
            throw DestinationFitException(if (error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) FitFailure.LIMIT_REACHED else FitFailure.UNAVAILABLE)
        } catch (_: Exception) { throw DestinationFitException(FitFailure.UNAVAILABLE) }
    }
}

internal suspend fun analyzeDestination(destination: String, store: TravelPreferencesStore?): DestinationFit {
    if (store == null) {
        val fixture = JevTestOverrides.analyze ?: throw DestinationFitException(FitFailure.PROFILE_UNAVAILABLE)
        return fixture(destination)
    }
    val auth = FirebaseAuth.getInstance()
    val owner = store.uid
    fun checkOwner() { if (auth.currentUser?.uid != owner) throw DestinationFitException(FitFailure.PROFILE_UNAVAILABLE) }
    checkOwner()
    val profile = store.forDestinationAnalysis()
    JevTestOverrides.diagnostic?.invoke("profile:${profile.status?.name}:${profile.hasLoaded}:${profile.isLoading}:${profile.loadFailed}:${profile.isSaving}")
    checkOwner()
    try { FirebaseAppCheck.getInstance().getAppCheckToken(false).await();JevTestOverrides.diagnostic?.invoke("appcheck:ok") }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { JevTestOverrides.diagnostic?.invoke("appcheck:unavailable");throw DestinationFitException(FitFailure.UNAVAILABLE) }
    checkOwner()
    return DestinationFitClient().analyze(destination, profile).also { checkOwner() }
}
