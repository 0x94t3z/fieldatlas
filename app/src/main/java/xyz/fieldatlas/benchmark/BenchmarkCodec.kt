package xyz.fieldatlas.benchmark

import java.io.File
import java.io.OutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BenchmarkCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }

    fun encode(run: BenchmarkRun): String {
        val normalized = run.copy(
            artifacts = run.artifacts.sortedWith(compareBy({ it.id }, { it.version })),
        )
        return json.encodeToString(normalized) + "\n"
    }

    fun write(run: BenchmarkRun, destination: File) {
        check(destination.createNewFile()) { "Refusing to overwrite benchmark export" }
        destination.outputStream().use { write(run, it) }
    }

    fun write(run: BenchmarkRun, destination: OutputStream) {
        destination.bufferedWriter(Charsets.UTF_8).use { it.write(encode(run)) }
    }
}
