package com.onurguner.ogauto.mirror

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight mirror diagnostics. Every 5 s one line goes to logcat and to
 * files/diag/mirror.log: frames the phone produced, frames drawn on the car
 * surface, the longest gap between drawn frames, phone thermal status.
 *
 * Reading it after a freeze: frames still drawn → the phone keeps sending,
 * the stall is in Android Auto / the head unit. Frames stop → the capture
 * side (phone overloaded, projection paused) is the cause.
 */
object MirrorStats {

    private const val TAG = "OgAutoStats"
    private const val INTERVAL_MS = 5_000L
    private const val MAX_BYTES = 512 * 1024L

    private val produced = AtomicInteger()
    private val drawn = AtomicInteger()
    private val lastDrawMs = AtomicLong()
    private val maxGapMs = AtomicLong()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var file: File? = null
    private var power: PowerManager? = null
    @Volatile private var mode: String = "-"

    fun start(context: Context) {
        if (thread != null) return
        val dir = File(context.filesDir, "diag").apply { mkdirs() }
        file = File(dir, "mirror.log").also {
            if (it.length() > MAX_BYTES) it.renameTo(File(dir, "mirror.old.log"))
        }
        power = context.getSystemService(PowerManager::class.java)
        reset()
        write("=== start ${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE}")
        val t = HandlerThread("mirror-stats").apply { start() }
        thread = t
        handler = Handler(t.looper).also { it.postDelayed(tick, INTERVAL_MS) }
    }

    fun stop() {
        handler?.removeCallbacks(tick)
        thread?.quitSafely()
        thread = null
        handler = null
        write("=== stop")
    }

    fun setMode(description: String) {
        mode = description
        write("mode $description")
    }

    fun onFrameProduced() { produced.incrementAndGet() }

    fun onFrameDrawn() {
        val now = SystemClock.uptimeMillis()
        val last = lastDrawMs.getAndSet(now)
        if (last != 0L) {
            val gap = now - last
            if (gap > maxGapMs.get()) maxGapMs.set(gap)
        }
        drawn.incrementAndGet()
    }

    fun logFile(context: Context): File = File(File(context.filesDir, "diag"), "mirror.log")

    private fun reset() {
        produced.set(0); drawn.set(0); maxGapMs.set(0); lastDrawMs.set(0)
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = produced.getAndSet(0)
            val d = drawn.getAndSet(0)
            val gap = maxGapMs.getAndSet(0)
            val sinceLast = lastDrawMs.get().let { if (it == 0L) -1 else SystemClock.uptimeMillis() - it }
            val thermal = if (Build.VERSION.SDK_INT >= 29) thermalName(power?.currentThermalStatus ?: -1) else "?"
            val secs = INTERVAL_MS / 1000f
            write(
                "in=%.1ffps out=%.1ffps maxGap=%dms sinceLastDraw=%dms thermal=%s mode=%s".format(
                    Locale.US, p / secs, d / secs, gap, sinceLast, thermal, mode,
                )
            )
            handler?.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun thermalName(s: Int) = when (s) {
        0 -> "none"; 1 -> "light"; 2 -> "moderate"; 3 -> "severe"
        4 -> "critical"; 5 -> "emergency"; 6 -> "shutdown"; else -> "?"
    }

    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    private fun write(line: String) {
        Log.i(TAG, line)
        val f = file ?: return
        runCatching { f.appendText("${stamp.format(Date())} $line\n") }
    }
}
