package xyz.fieldatlas.desktop

import java.io.File
import kotlinx.coroutines.runBlocking
import xyz.fieldatlas.research.FtsRetriever
import xyz.fieldatlas.research.KnowledgeDatabase
import xyz.fieldatlas.research.LeviathanNative

/**
 * Times Leviathan and the keyword index on the phone, without the installed app: run by
 * tools/android/run_leviathan_timing.py with app_process. Each argument is PACK=PATH; every pack
 * gets the queries listed for it, the first search on a freshly opened pack counted as cold.
 */
object AndroidLeviathanTiming {
    private val queries = mapOf(
        "wikivoyage-guides" to listOf("train tickets sleeper class india", "tipping baksheesh egypt", "tourist sim card istanbul",
            "money atm cash argentina"),
        "wikivoyage-places" to listOf("castle royal mile edinburgh", "beer pub prague", "vegan restaurant lisbon", "museum berlin"),
        "osm-vegan-places" to listOf("vegan restaurant", "vegan bakery", "vegan cafe"),
        "osm-essentials" to listOf("pharmacy", "atm cash", "hospital emergency", "drinking water toilet"),
        "crypto-ethereum" to listOf("blob transactions rollups", "mnemonic seed words wallet", "sha256 preimage resistance",
            "proof of stake validator withdrawal"),
        "world-knowledge-biology" to listOf("telomere ageing", "rapamycin lifespan mice", "photosynthesis chloroplast"),
    )
    // Place packs are also searched within one destination, as the app does for a named city.
    private val cities = mapOf(
        "wikivoyage-places" to listOf("lisbon", "berlin", "edinburgh", "prague"),
        "osm-vegan-places" to listOf("lisbon", "berlin", "bangkok", "tokyo"),
        "osm-essentials" to listOf("bangkok", "lisbon", "nairobi", "berlin"),
    )

    @JvmStatic fun main(args: Array<String>) = runBlocking {
        check(LeviathanNative.available) { "Native engine did not load" }
        for (arg in args) {
            val (pack, path) = arg.split('=', limit = 2)
            val file = File(path)
            val opened = System.nanoTime()
            KnowledgeDatabase.open(file).use { db ->
                val openMs = (System.nanoTime() - opened) / 1e6
                var first = true
                for (query in queries.getValue(pack)) {
                    val started = System.nanoTime()
                    val native = db.leviathanCandidates(query, 20) ?: error("$pack fell back to keyword search")
                    val nativeMs = (System.nanoTime() - started) / 1e6
                    val keywordStarted = System.nanoTime()
                    val keyword = FtsRetriever(db).search(query, 20) { }
                    val keywordMs = (System.nanoTime() - keywordStarted) / 1e6
                    println("""{"pack":"$pack","query":"$query","cold":$first,"openMs":${"%.1f".format(openMs)},""" +
                        """"leviathanMs":${"%.1f".format(nativeMs)},"leviathanRows":${native.size},""" +
                        """"keywordMs":${"%.1f".format(keywordMs)},"keywordRows":${keyword.size}}""")
                    first = false
                }
                for (city in cities[pack].orEmpty()) {
                    val query = if (pack == "osm-essentials") "pharmacy" else "restaurant"
                    val started = System.nanoTime()
                    val rows = db.leviathanCandidates(query, 20, listOf(city)) ?: error("$pack has no places filter")
                    println("""{"pack":"$pack","query":"$query","city":"$city","leviathanMs":${"%.1f".format((System.nanoTime() - started) / 1e6)},""" +
                        """"leviathanRows":${rows.size}}""")
                }
            }
        }
    }
}
