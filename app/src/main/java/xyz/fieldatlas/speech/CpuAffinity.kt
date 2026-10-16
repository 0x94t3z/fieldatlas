package xyz.fieldatlas.speech

import java.io.File

/**
 * The phone's fastest cores, and a way to keep a thread on them. On a phone with big and little cores (the Redmi
 * 13C has two Cortex-A75 and six A55) the reading voice makes speech about twice as fast on the big ones, but
 * Android tends to run its threads on the little ones. Pinning the thread that loads the voice is enough: the
 * engine's own threads, started from it, inherit the same cores.
 */
internal object CpuAffinity {
    private val loaded: Boolean by lazy {
        try { System.loadLibrary("fieldatlas_leviathan"); true } catch (_: UnsatisfiedLinkError) { false }
    }

    private external fun pinCurrentThread(mask: Long): Boolean

    /** Cores at the highest top speed, as a bit mask; 0 when the phone does not say, or all its cores are alike. */
    fun fastCoreMask(cpus: File = File("/sys/devices/system/cpu")): Long {
        val speeds = (0 until 64).mapNotNull { core ->
            runCatching { File(cpus, "cpu$core/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()?.let { core to it }
        }
        if (speeds.size < 2) return 0
        val top = speeds.maxOf { it.second }
        if (speeds.all { it.second == top }) return 0
        return speeds.filter { it.second == top }.fold(0L) { mask, (core, _) -> mask or (1L shl core) }
    }

    /** Keeps the calling thread on [mask]'s cores; false (and nothing changed) where that is not possible. */
    fun pin(mask: Long): Boolean = mask != 0L && loaded && runCatching { pinCurrentThread(mask) }.getOrDefault(false)
}
