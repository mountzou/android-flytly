package com.mountzou.flytly.location

import android.content.Context

/** Instrumentation-only seams; no intent/broadcast can configure them. */
internal object LocationSourceOverrides {
    var factory: ((Context) -> DeviceLocationSource)? = null
    var preferenceNamespace: String? = null
    var timeZone: ((Coordinate) -> java.time.ZoneId?)? = null
}
