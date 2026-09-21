package xyz.fieldatlas.diagnostics

import java.io.OutputStream

object AuditExporter {
    fun write(snapshot: DiagnosticsSnapshot, destination: OutputStream) {
        destination.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(DiagnosticsProvider.toJson(snapshot))
        }
    }
}
