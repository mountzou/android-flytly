package com.mountzou.flytly.jev

// In-process instrumentation only; no launch intent can inject a paid analysis response.
internal object JevTestOverrides {
    var analyze: (suspend (String) -> DestinationFit)? = null
    var diagnostic: ((String) -> Unit)? = null
}
