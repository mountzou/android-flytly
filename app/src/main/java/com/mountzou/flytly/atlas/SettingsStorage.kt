package com.mountzou.flytly.atlas

import android.content.Context
import android.content.SharedPreferences

/** iOS map switches/theme are app preferences; account records and camera remain owner scoped. */
internal fun atlasSettings(context: Context, namespace: String?, authenticated: Boolean,
                           globalName: String = "atlas_settings"): SharedPreferences {
    if (!authenticated) return context.getSharedPreferences(namespace?.let { "atlas_settings_$it" } ?: globalName,Context.MODE_PRIVATE)
    val global = context.getSharedPreferences(globalName,Context.MODE_PRIVATE)
    if (namespace == null) return global
    val legacy = context.getSharedPreferences("atlas_settings_$namespace",Context.MODE_PRIVATE).all
    val pending = legacy.filter { (key,value) -> !global.contains(key) && when (key) {
        "appearance" -> value in listOf("light","dark","system")
        "countryNames","cityNames" -> value is Boolean
        "minimumPopulation" -> value is Int && value in populationOptions
        else -> false
    } }
    if (pending.isNotEmpty()) global.edit().apply {
        pending.forEach { (key,value) -> when (value) {
            is Boolean -> putBoolean(key,value);is String -> putString(key,value);is Int -> putInt(key,value)
        } }
    }.apply()
    return global
}
