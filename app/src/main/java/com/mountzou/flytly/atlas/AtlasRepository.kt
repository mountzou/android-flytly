package com.mountzou.flytly.atlas

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Open the public index once per process; city records stay queryable without eager decoding. */
object AtlasRepository {
    private val mutex = Mutex()
    private var cached: AtlasCatalog? = null

    suspend fun load(context: Context): AtlasCatalog = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            AtlasPerformance.measure("catalog.total") {
                AtlasSqlReader.open(context.applicationContext)
            }
        }.also { cached = it }
    }
}
