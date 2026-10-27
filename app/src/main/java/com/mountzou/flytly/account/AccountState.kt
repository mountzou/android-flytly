package com.mountzou.flytly.account

import com.mountzou.flytly.atlas.PlaceStatus
import com.mountzou.flytly.atlas.TravelSnapshot
import com.mountzou.flytly.atlas.PlaceChangeConflictException
import java.security.MessageDigest
import java.util.UUID

val preferenceFields = listOf("travelPace", "natureAccess", "cultureHistory", "foodImportance", "crowdTolerance", "mobilityWithoutCar", "weatherImportance", "budgetStyle")
internal val profileFields = preferenceFields + listOf("mustHave", "mustAvoid", "onboardingStatus", "completed", "skipped")

fun accountNamespace(uid: String): String = "account-" + MessageDigest.getInstance("SHA-256")
    .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }

internal data class PendingChange(val sequence: Long, val key: String, val value: Any)

/** The outbox and visible cache are persisted together before acknowledging an edit to the UI. */
internal data class AccountCache(
    val sourceID: String = UUID.randomUUID().toString(),
    val nextSequence: Long = 0,
    val places: Map<String, PlaceStatus> = emptyMap(),
    val profile: Map<String, Any> = emptyMap(),
    val pending: Map<String, PendingChange> = emptyMap()
) {
    fun edit(key: String, value: Any): AccountCache {
        val sequence = nextSequence + 1
        require(sequence <= 9_007_199_254_740_991L)
        return copy(nextSequence = sequence, pending = pending + (key to PendingChange(sequence, key, value)))
    }
    fun editPlace(key: String, status: PlaceStatus, expecting: TravelSnapshot?): AccountCache {
        if (expecting != null && visiblePlaces() != expecting) throw PlaceChangeConflictException()
        return edit(key, status.value)
    }
    fun acknowledge(sequence: Long) = copy(pending = pending.filterValues { it.sequence > sequence })
    fun visiblePlaces() = TravelSnapshot(places + pending.values.filter { !it.key.startsWith("profile:") }
        .associate { it.key to PlaceStatus.entries.first { status -> status.value == it.value } })
    fun visibleProfile() = profile + pending.values.filter { it.key.startsWith("profile:") }
        .associate { it.key.removePrefix("profile:") to it.value }
}

data class AccountData(
    val loaded: Boolean = false,
    val travels: TravelSnapshot = TravelSnapshot(),
    val profile: Map<String, Any> = emptyMap(),
    val pendingCount: Int = 0,
    val message: String = "Loading your account…",
    val localError: Boolean = false
)
