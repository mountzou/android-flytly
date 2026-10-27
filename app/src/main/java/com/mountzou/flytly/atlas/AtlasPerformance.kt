package com.mountzou.flytly.atlas

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log

/** Debug-only startup measurements. No account, place selection or credential data. */
internal object AtlasPerformance {
    var enabled = false
    private val marks = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    fun mark(stage: String) { if (enabled && marks.add(stage)) record(stage, Process.getStartElapsedRealtime()) }
    fun record(stage: String, startedAt: Long) {
        duration(stage, SystemClock.elapsedRealtime() - startedAt)
    }
    fun duration(stage: String, milliseconds: Long) {
        if (enabled) Log.i("FlytlyPerformance", "stage=$stage durationMs=$milliseconds processMs=${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()}")
    }
    fun <T> measure(stage: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val cpuStart = SystemClock.currentThreadTimeMillis()
        Trace.beginSection("flytly.$stage")
        return try { block() } finally {
            Trace.endSection(); record(stage, start)
            duration("$stage.cpu", SystemClock.currentThreadTimeMillis() - cpuStart)
        }
    }
}
