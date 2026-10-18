package com.onurguner.ogauto.mirror

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.onurguner.ogauto.core.MirrorGateway
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Mirror-side diagnostics only. "out" means a frame was posted to the car
 * Surface; it does NOT prove Android Auto encoded/transmitted/displayed it.
 * A conservative watchdog rebinds the surface when fresh input keeps arriving
 * but nothing can be posted for 8 seconds (max twice per mirror session).
 */
object MirrorStats {
    private const val TAG = "OgAutoStats"
    private const val INTERVAL_MS = 5_000L
    private const val MAX_BYTES = 512 * 1024L

    private val produced = AtomicInteger()
    private val posted = AtomicInteger()
    private val skipped = AtomicInteger()
    private val failed = AtomicInteger()
    private val lastInputMs = AtomicLong()
    private val lastPostMs = AtomicLong()
    private val maxGapMs = AtomicLong()
    private val modeStartedMs = AtomicLong()
    private val lastRecoveryMs = AtomicLong()
    private val recoveryCount = AtomicInteger()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var file: File? = null
    private var power: PowerManager? = null
    @Volatile private var mode: String = "-"
    @Volatile private var pacedMode = false

    fun start(context: Context) {
        if (thread != null) return
        val dir = File(context.filesDir, "diag").apply { mkdirs() }
        file = File(dir, "mirror.log").also {
            if (it.length() > MAX_BYTES) it.renameTo(File(dir, "mirror.old.log"))
        }
        power = context.getSystemService(PowerManager::class.java)
        reset()
        write("=== start " + Build.MANUFACTURER + " " + Build.MODEL +
            " Android " + Build.VERSION.RELEASE)
        val t = HandlerThread("mirror-stats").apply { start() }
        thread = t
        handler = Handler(t.looper).also { it.postDelayed(tick, INTERVAL_MS) }
    }

    fun stop() {
        pacedMode = false
        handler?.removeCallbacks(tick)
        thread?.quitSafely()
        thread = null
        handler = null
        write("=== stop")
    }

    fun setMode(description: String) {
        mode = description
        pacedMode = description.startsWith("paced")
        modeStartedMs.set(SystemClock.uptimeMillis())
        lastInputMs.set(0)
        lastPostMs.set(0)
        write("mode " + description)
    }

    fun onFrameProduced() {
        lastInputMs.set(SystemClock.uptimeMillis())
        produced.incrementAndGet()
    }

    fun onFrameSkipped() { skipped.incrementAndGet() }
    fun onFrameDrawFailure() { failed.incrementAndGet() }

    fun onFrameDrawn() {
        val now = SystemClock.uptimeMillis()
        val last = lastPostMs.getAndSet(now)
        if (last > 0) {
            val gap = now - last
            maxGapMs.accumulateAndGet(gap) { old, value -> maxOf(old, value) }
        }
        posted.incrementAndGet()
    }

    fun logFile(context: Context): File = File(File(context.filesDir, "diag"), "mirror.log")

    private fun reset() {
        produced.set(0); posted.set(0); skipped.set(0); failed.set(0)
        lastInputMs.set(0); lastPostMs.set(0); maxGapMs.set(0)
        modeStartedMs.set(0); lastRecoveryMs.set(0); recoveryCount.set(0)
        pacedMode = false; mode = "-"
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val p = produced.getAndSet(0)
            val o = posted.getAndSet(0)
            val s = skipped.getAndSet(0)
            val f = failed.getAndSet(0)
            val gap = maxGapMs.getAndSet(0)
            val sinceLast = lastPostMs.get().let { if (it == 0L) -1L else now - it }
            val sinceInput = lastInputMs.get().let { if (it == 0L) -1L else now - it }
            val thermal = if (Build.VERSION.SDK_INT >= 29) {
                thermalName(power?.currentThermalStatus ?: -1)
            } else "?"
            val secs = INTERVAL_MS / 1000f
            write(
                "in=%.1ffps out=%.1ffps skipped=%d failed=%d maxGap=%dms " +
                    "sinceLastPost=%dms sinceInput=%dms thermal=%s mode=%s".format(
                        Locale.US, p / secs, o / secs, s, f, gap,
                        sinceLast, sinceInput, thermal, mode,
                    )
            )
            if (pacedMode && MirrorRecoveryPolicy.shouldRebind(
                    now, modeStartedMs.get(), lastInputMs.get(),
                    lastPostMs.get(), lastRecoveryMs.get(), recoveryCount.get()
                )) {
                lastRecoveryMs.set(now)
                val n = recoveryCount.incrementAndGet()
                write("watchdog: posted frames stalled with live input; rebind " +
                    n + "/" + MirrorRecoveryPolicy.MAX_AUTOMATIC_REBINDS)
                MirrorGateway.requestRefresh()
            }
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
        runCatching { f.appendText(stamp.format(Date()) + " " + line + "\n") }
    }
}
