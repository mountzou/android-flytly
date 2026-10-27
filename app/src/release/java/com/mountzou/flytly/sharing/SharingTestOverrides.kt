package com.mountzou.flytly.sharing

import android.content.Context
internal object SharingTestOverrides { val export: (suspend (Context, TravelCardSnapshot) -> TravelCardExport)? = null }
