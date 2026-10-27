package com.mountzou.flytly.sharing

import android.content.Context
internal object SharingTestOverrides { var export: (suspend (Context, TravelCardSnapshot) -> TravelCardExport)? = null }
