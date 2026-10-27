package com.mountzou.flytly.storage

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** AtomicFile protects against process death, but concurrent readers/writers need their own lock. */
internal object AtomicFileLocks {
    private val locks = ConcurrentHashMap<String, Any>()
    fun forFile(file: File): Any = locks.getOrPut(file.absolutePath) { Any() }
}
