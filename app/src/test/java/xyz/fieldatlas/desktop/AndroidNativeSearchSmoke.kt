package xyz.fieldatlas.desktop

import java.io.File
import kotlinx.coroutines.*
import xyz.fieldatlas.research.*

/** Isolated shell smoke check: no installed-app storage, model or History access. */
object AndroidNativeSearchSmoke {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        check(args.size in 1..3)
        check(LeviathanNative.available) { "Native engine did not load" }
        val fixture = File(args[0])
        repeat(5) {
            KnowledgeDatabase.open(fixture).use { db ->
                val native = db.leviathanCandidates("Alto pump", 5) ?: error("Native path fell back")
                check(native.any { "F-27" in it.text })
                val legacy = FtsRetriever(db).search("Alto pump", 5) { }
                check(legacy.any { "F-27" in it.text })
            }
        }
        val merged = MultiKnowledgeRetriever({ listOf(fixture) }).search("Alto pump", 5) { }
        check(merged.any { it.matchedBy?.contains("Leviathan") == true })
        check(merged.any { "F-27" in it.text })
        println("PASS: persistent JNI open/search/close, full-source hydration, SQL fallback index, app routing")
        if (args.size >= 2) {
            MultiKnowledgeRetriever({ listOf(File(args[1])) }).use { retriever ->
                val rows = retriever.search("water", 5) { }
                check(rows.any { it.matchedBy?.contains("Leviathan") == true }) {
                    "Converted article-section pack bypassed native candidate search"
                }
            }
            val session = LeviathanSession(File(args[1]))
            session.use {
                check(it.search("water", 5).isNotEmpty())
                repeat(10) {
                    val task = launch(Dispatchers.IO) { session.search("water earth history", 50) }
                    delay(30)
                    val start = System.nanoTime()
                    task.cancelAndJoin()
                    check((System.nanoTime() - start) / 1_000_000 < 5_000) { "Cancellation exceeded 5s" }
                    check(session.search("lunar eclipse", 5).isNotEmpty())
                }
            }
            println("PASS: ten cancellation/reuse cycles on real corpus")
        }
        if (args.size == 3) {
            KnowledgeDatabase.open(File(args[2])).use { db ->
                check(db.leviathanCandidates("Alto pump", 5) == null)
                check(FtsRetriever(db).search("Alto pump", 5) { }.any { "F-27" in it.text })
            }
            println("PASS: unsupported native schema falls back without losing source data")
        }
    }
}
