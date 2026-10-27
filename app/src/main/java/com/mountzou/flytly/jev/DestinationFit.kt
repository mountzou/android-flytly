package com.mountzou.flytly.jev

import com.mountzou.flytly.preferences.*
import kotlin.math.roundToInt

internal data class FitDimension(val id: String, val title: String, val score: Int,
                                 val confidence: Double, val selectedPreference: String, val questionIndex: Int)
internal data class DestinationFit(val destination: String, val matchScore: Int,
                                   val dimensions: List<FitDimension>, val confidence: Double, val model: String)
internal enum class FitFailure(val description: String) {
    PROFILE_INCOMPLETE("Complete your preferences before analyzing a destination."),
    PROFILE_UNAVAILABLE("Your travel preferences could not be loaded. Please try again."),
    INVALID_RESPONSE("JEV returned an unexpected response."),
    LIMIT_REACHED("You have reached the destination analysis limit. Please try again later."),
    UNAVAILABLE("The destination analysis is temporarily unavailable.")
}
internal class DestinationFitException(val failure: FitFailure) : Exception(failure.description)

// The callable receives exactly the same profile description as the iOS client.
internal fun TravelPreferences.jevProfileDescription(): String {
    val labels = listOf(
        preferenceQuestions[0].options,
        listOf("mostly urban", "some nature nearby", "mostly nature"),
        listOf("culture is not a priority", "I enjoy some culture", "I travel for culture"),
        listOf("food is not a priority", "I enjoy local cuisine", "food is a main reason to visit"),
        listOf("prefers quieter places", "comfortable with some crowds", "enjoys lively busy places"),
        preferenceQuestions[5].options,
        listOf("weather does not affect my choice", "weather matters to my choice", "weather is a key part of my choice"),
        listOf("lower-cost destinations", "mid-range destinations", "higher-cost destinations")
    ).mapIndexed { i, options -> options[levels[i] - 1] }
    return """
        User travel preferences:
        travel pace: ${labels[0]}; nature access: ${labels[1]};
        culture and history: ${labels[2]}; food and local cuisine: ${labels[3]};
        crowd tolerance: ${labels[4]}; mobility without a car: ${labels[5]};
        weather importance: ${labels[6]}; budget approach: ${labels[7]}.
        Must have: ${mustHave.sorted().joinToString(", ").ifEmpty { "none specified" }}.
        Must avoid: ${mustAvoid.sorted().joinToString(", ").ifEmpty { "none specified" }}.
    """.trimIndent()
}

internal val fitQuestions = listOf(
    Triple("food_fit", "Food", 3), Triple("mobility_fit", "Mobility", 5),
    Triple("climate_fit", "Climate", 6), Triple("culture_fit", "Culture", 2),
    Triple("crowd_fit", "Crowds", 4), Triple("budget_fit", "Budget", 7),
    Triple("nature_fit", "Nature", 1), Triple("pace_fit", "Travel pace", 0)
)

internal fun decodeDestinationFit(data: Any?, destination: String, preferences: TravelPreferences): DestinationFit {
    fun invalid(): Nothing = throw DestinationFitException(FitFailure.INVALID_RESPONSE)
    val root = data as? Map<*, *> ?: invalid()
    val answers = root["answers"] as? Map<*, *> ?: invalid()
    fun score(answer: Map<*, *>): Int {
        val value = (answer["score"] as? Number)?.toDouble() ?: invalid()
        if (!value.isFinite() || value !in 0.0..2.0) invalid()
        return (value * 50).roundToInt()
    }
    fun confidence(answer: Map<*, *>) = (answer["confidence"] as? Number)?.toDouble()
        ?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: 0.0
    val overall = answers["overall_match"] as? Map<*, *> ?: invalid()
    val dimensions = fitQuestions.mapNotNull { (id, title, index) ->
        val answer = answers[id] as? Map<*, *> ?: return@mapNotNull null
        FitDimension(id, title, score(answer), confidence(answer),
            preferenceQuestions[index].options[preferences.levels[index] - 1], index)
    }
    return DestinationFit(destination, score(overall), dimensions, confidence(overall), root["model"] as? String ?: "jev-latest")
}

internal fun fitLabel(score: Int) = when {
    score >= 80 -> "Excellent"; score >= 65 -> "Good"; score >= 45 -> "Mixed"; else -> "Low"
}
internal fun fitDescription(score: Int, preference: String? = null): String = if (preference != null) when {
    score >= 80 -> "The destination fits this preference well: $preference."
    score >= 65 -> "The destination is a good fit for this preference: $preference."
    score >= 45 -> "The destination partly fits this preference: $preference."
    else -> "The destination may not fit this preference: $preference."
} else when {
    score >= 80 -> "A strong match for your travel preferences."
    score >= 65 -> "Many of your preferences line up here."
    score >= 45 -> "Some parts of this destination fit you."
    else -> "This destination may miss your priorities."
}
internal fun fitStepFill(score: Int, index: Int): Float {
    val boundaries = listOf(0, 45, 65, 80, 100)
    return ((score.coerceIn(0, 100) - boundaries[index]).toFloat() / (boundaries[index + 1] - boundaries[index])).coerceIn(0f, 1f)
}
