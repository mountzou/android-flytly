package com.mountzou.flytly.preferences

import com.mountzou.flytly.account.preferenceFields

enum class OnboardingStatus { PENDING, COMPLETED, SKIPPED }

fun onboardingStatus(profile: Map<String, Any>): OnboardingStatus? = when {
    profile["skipped"] == true -> OnboardingStatus.SKIPPED
    profile["completed"] == true -> OnboardingStatus.COMPLETED
    else -> when (profile["onboardingStatus"]) {
        "pending" -> OnboardingStatus.PENDING
        "completed" -> OnboardingStatus.COMPLETED
        "skipped" -> OnboardingStatus.SKIPPED
        else -> null
    }
}

data class PreferenceQuestion(val field: String, val title: String, val question: String, val options: List<String>, val artwork: String)
val preferenceQuestions = listOf(
    PreferenceQuestion("travelPace", "Travel pace", "How much do you like to fit into each travel day?", listOf("I like taking things slowly", "I like a balanced pace", "I like fitting a lot into each day"), "TravelPaceWalking"),
    PreferenceQuestion("natureAccess", "Nature", "How much nature do you want around you?", listOf("Mostly urban", "Some nature nearby", "Mostly nature"), "TravelNatureCloud"),
    PreferenceQuestion("cultureHistory", "Culture", "How important is culture to your trip?", listOf("Culture is not a priority", "I enjoy some culture", "I travel for culture"), "TravelCultureStatue"),
    PreferenceQuestion("foodImportance", "Food", "How important is local cuisine to your trip?", listOf("Food is not a priority", "I enjoy local cuisine", "Food is a main reason to visit"), "TravelFoodCarrot"),
    PreferenceQuestion("crowdTolerance", "Crowds", "How comfortable are you with busy, highly visited places?", listOf("I prefer quieter places", "Crowds are fine sometimes", "I enjoy lively, busy places"), "TravelCrowdsMegaphone"),
    PreferenceQuestion("mobilityWithoutCar", "Mobility", "How important is easy mobility within a city?", listOf("I can adapt to most transport options", "I prefer cities that are easy to get around", "I choose cities with excellent mobility"), "TravelMobilityTrain"),
    PreferenceQuestion("weatherImportance", "Weather", "How much does weather affect your choice of destination?", listOf("Weather does not affect my choice", "Weather matters to my choice", "Weather is a key part of my choice"), "TravelWeatherUmbrella"),
    PreferenceQuestion("budgetStyle", "Budget", "What level of destination cost suits you?", listOf("Lower-cost destinations", "Mid-range destinations", "Higher-cost destinations"), "TravelBudgetPig")
)
val mustHaveOptions = listOf("Visa-free or easy entry", "Direct flights or simple connections", "Good access to healthcare")
val mustAvoidOptions = listOf("Safety concerns", "Political instability", "Extreme isolation", "Limited emergency services")

/** Editing is a draft. Nothing is written until Save or Skip receives a server acknowledgement. */
data class TravelPreferences(
    val levels: List<Int> = List(8) { 2 },
    val mustHave: Set<String> = emptySet(),
    val mustAvoid: Set<String> = emptySet()
) {
    init { require(levels.size == 8 && levels.all { it in 1..3 }) }
    fun choosing(index: Int, level: Int) = copy(levels = levels.toMutableList().also { it[index] = level })
    fun payload(skipped: Boolean): Map<String, Any> = preferenceFields.zip(levels).toMap() + mapOf(
        "mustHave" to mustHave.sorted(), "mustAvoid" to mustAvoid.sorted(),
        "onboardingStatus" to if (skipped) "skipped" else "completed", "completed" to true, "skipped" to skipped)
    companion object {
        fun from(profile: Map<String, Any>) = TravelPreferences(
            preferenceFields.map { (profile[it] as? Number)?.toInt()?.takeIf { n -> n in 1..3 } ?: 2 },
            (profile["mustHave"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            (profile["mustAvoid"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty())
    }
}

data class PreferencesState(
    val values: TravelPreferences = TravelPreferences(),
    val status: OnboardingStatus? = null,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveGeneration: Long = 0
) {
    val shouldPresentOnboarding get() = status == OnboardingStatus.PENDING
}
