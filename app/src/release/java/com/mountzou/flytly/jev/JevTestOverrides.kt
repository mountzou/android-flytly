package com.mountzou.flytly.jev

internal object JevTestOverrides {
    val analyze: (suspend (String) -> DestinationFit)? = null
    val diagnostic: ((String) -> Unit)? = null
}
