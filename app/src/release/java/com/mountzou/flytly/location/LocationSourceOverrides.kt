package com.mountzou.flytly.location

import android.content.Context

internal object LocationSourceOverrides {
    val factory: ((Context) -> DeviceLocationSource)? = null
    val preferenceNamespace: String? = null
    val timeZone: ((Coordinate) -> java.time.ZoneId?)? = null
}
