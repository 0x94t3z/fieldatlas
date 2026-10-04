package xyz.fieldatlas.emergency

import java.util.Locale
import xyz.fieldatlas.research.Evidence

/**
 * Finds the bundled guide for a question that asks what to do about an emergency ("someone is
 * choking", "how to treat a snake bite"). The guide becomes the model's only evidence, so the
 * answer can reason about the person's situation while every step comes from the guide.
 *
 * A topic word alone is not enough ("history of the 2004 tsunami" is research, not first aid);
 * the question must also read as a request for help or safety steps, or be just a few words.
 */
object EmergencyGuideMatch {
    private val intent = Regex(
        "(?i)\\b(how (do|to|can|should)|what (do|to|should|can) (i|we|you)|what to do|treat|treatment|first aid|help|stop|" +
            "prevent|survive|safe|safety|emergency|signs?|symptoms?|someone|somebody|" +
            "my (friend|child|kid|son|daughter|wife|husband|partner|dad|mom|mother|father|baby)|bitten|stung|burned|burnt|bleeding|" +
            "choking|lost|stranded|injured|hurt|unconscious|not breathing|can'?t breathe|collapsed|prepare|should i|do i|is it safe|purify|disinfect|boil)\\b",
    )
    /** Words people use that the sources' keywords miss. */
    private val synonyms = mapOf(
        "cpr" to listOf("cardiopulmonary", "no pulse", "collapsed"),
        "choking" to listOf("choke", "something stuck in throat", "can't breathe", "cant breathe"),
        "severe-bleeding" to listOf("bleed", "bleeding badly"),
        "burns" to listOf("burned", "burnt", "scalded", "hot pan", "hot stove", "boiling water", "hot oil", "burned hand", "burned my", "burned her", "burned his"),
        "fractures-sprains" to listOf("broken leg", "broken arm", "broken ankle", "broken wrist", "sprained"),
        "lost" to listOf("i'm lost", "i am lost", "we're lost", "we are lost", "got lost", "lost in the woods", "lost hiking", "lost on a hike"),
        "safe-drinking-water" to listOf("drink", "drinkable", "stream water", "river water", "lake water", "purify water", "disinfect water", "boil water"),
        "signaling" to listOf("signal for help", "rescue signal", "attract rescuers", "call for help without signal"),
        "heat-illness" to listOf("heatstroke", "overheated"),
        "altitude-sickness" to listOf("mountain sickness", "high altitude"),
        "animal-bites" to listOf("bitten by a dog", "monkey bite", "bat bite"),
    )

    /** Single words specific enough to name the emergency on their own. */
    private val strongWords = setOf(
        "cpr", "choking", "heimlich", "bleeding", "hemorrhage", "haemorrhage", "laceration", "scald", "scalded", "fracture",
        "sprain", "sprained", "dislocation", "concussion", "anaphylaxis", "epinephrine", "epipen", "seizure", "convulsion",
        "poisoning", "overdose", "burned", "burnt", "snakebite", "rattlesnake", "copperhead", "cottonmouth", "antivenom", "scorpion", "hornet",
        "lyme", "rabies", "drowning", "hypothermia", "frostbite", "hyperthermia", "heatstroke", "dehydration", "sunburn",
        "lightning", "earthquake", "quake", "aftershock", "tsunami", "flood", "wildfire", "bushfire", "hurricane", "typhoon",
        "cyclone", "tornado", "blackout", "blizzard", "defibrillator", "resuscitation", "giardia", "mayday", "unresponsive",
        "stranded", "cardiopulmonary",
    )

    /** Everyday words ("snake", "cut", "lost", "water") count only alongside one of these. */
    private val danger = Regex(
        "(?i)\\b(first aid|treat|treatment|treating|injured|injury|injuries|hurt|hurts|wound|wounded|pain|painful|emergency|" +
            "someone|somebody|bleeding|bled|swollen|swelling|hospital|ambulance|survive|surviving|survival|safety|safe|" +
            "what should i do|what do i do|what to do|help|signs?|symptoms?|bitten|bit me|stung|got (lost|bit|stung|burned|burnt)|" +
            "lost (in|on|while)|stuck|stranded|rescue|unconscious|not breathing|dizzy|faint|fainted)\\b",
    )

    fun match(question: String, guides: List<EmergencyGuide>): EmergencyGuide? {
        val text = " " + normalize(question) + " "
        val words = text.trim().split(' ').filter(String::isNotBlank)
        if (words.isEmpty() || words.size > 30) return null
        val dangerous = danger.containsMatchIn(question)
        val scored = guides.map { guide ->
            val terms = (guide.keywords + guide.title + synonyms[guide.id].orEmpty())
                .map(::normalize).filter(String::isNotBlank).distinct()
            // The guide's own subject outranks a related keyword ("earthquake" is also a tsunami keyword).
            val own = setOf(normalize(guide.title), normalize(guide.id.replace('-', ' ')))
            var score = 0
            for (term in terms) {
                if (" $term " !in text && !(term.length >= 5 && " ${term}s " in text)) continue
                val phrase = ' ' in term
                score += when {
                    phrase -> term.split(' ').size + 1
                    term in strongWords -> 2
                    dangerous -> 1
                    else -> 0
                } + if (term in own && (' ' in term || term in strongWords || dangerous)) 1 else 0
            }
            guide to score
        }.filter { it.second > 0 }
        if (scored.isEmpty()) return null
        val best = scored.maxWith(compareBy<Pair<EmergencyGuide, Int>> { it.second }.thenBy { if (it.first.urgent) 1 else 0 })
        return best.first.takeIf { words.size <= 4 || dangerous || intent.containsMatchIn(question) }
    }

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('’', '\'')
        .replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim()

    private val actionHeading = Regex("(?i)(first aid|what to do|what not to do|^if |\\bif you|treat|steps|how to|during|after|stay|seek|call|do not|don't|warning|help|care|strategy|thrusts?|blows|heimlich|boil|disinfect|warm|cool|rescue|signal|protect|evacuate|get to|move to|drop)")
    private val avoidHeading = Regex("(?i)(not to do|do not|don't|avoid)")
    private val signsHeading = Regex("(?i)(signs?|symptoms?|look out|warning signs|recogni[sz]e)")
    private val asksSigns = Regex("(?i)\\b(signs?|symptoms?|how do i know|recogni[sz]e|look like)\\b")

    /**
     * The guide split into citable sections (a heading with its follow-on blocks), in reading
     * order. Each keeps the published wording; list items keep their markers and nesting.
     */
    fun sections(guide: EmergencyGuide): List<Evidence> {
        val merged = mutableListOf<Pair<String, StringBuilder>>()
        for (section in guide.sections) {
            val body = buildString {
                section.paragraphs.forEach { append(it).append('\n') }
                section.steps.forEachIndexed { i, step ->
                    append("  ".repeat(section.stepLevel(i) - 1)).append(if (section.numbered) "${i + 1}. " else "- ").append(step).append('\n')
                }
            }
            val last = merged.lastOrNull()
            when {
                section.heading.isBlank() && last != null -> last.second.append(body)
                // A heading with nothing under it ("First Aid") introduces the next section.
                last != null && last.second.isBlank() -> merged[merged.lastIndex] = "${last.first}: ${section.heading}" to StringBuilder(body)
                else -> merged += section.heading to StringBuilder(body)
            }
        }
        return merged.filter { it.second.isNotBlank() || it.first.isNotBlank() }.mapIndexedNotNull { index, (heading, body) ->
            val text = listOf(heading, body.toString().trim()).filter(String::isNotBlank).joinToString("\n")
            if (text.isBlank()) return@mapIndexedNotNull null
            Evidence(
                documentId = "emergency:${guide.id}",
                chunkId = "emergency:${guide.id}:$index",
                title = if (heading.isBlank()) guide.title else "${guide.title}: $heading",
                source = guide.source.url,
                text = text,
                score = 1.0,
                matchedBy = "emergency guide",
            )
        }
    }

    /**
     * Sections for the model within [maxTokens]: what-to-do sections first (or the signs, when
     * the question asks how to recognise the emergency), then the rest, returned in reading order.
     * The best section is always kept, so even a slow phone gets the core steps.
     */
    fun select(question: String, guide: EmergencyGuide, maxTokens: Int): List<Evidence> {
        val all = sections(guide)
        if (all.isEmpty()) return all
        val ranked = xyz.fieldatlas.research.Bm25.rank(all, xyz.fieldatlas.research.QueryTerms.of(question))
        val wantsSigns = asksSigns.containsMatchIn(question)
        fun priority(e: Evidence): Double {
            val heading = e.title.substringAfter(": ", "")
            val signs = signsHeading.containsMatchIn(heading)
            // Guides state the overall procedure early ("General strategy"), details later.
            val position = e.chunkId.substringAfterLast(':').toInt()
            return e.score + 3.0 * (1.0 - position.toDouble() / all.size) +
                // The main steps come before the "what not to do" list when only one fits.
                (if (actionHeading.containsMatchIn(heading)) (if (avoidHeading.containsMatchIn(heading)) 2.0 else 3.0) else 0.0) +
                (if (e.text.contains("\n- ") || e.text.contains("\n1. ")) 1.0 else 0.0) +
                (if (signs) (if (wantsSigns) 4.0 else -1.0) else 0.0)
        }
        val order = ranked.sortedByDescending(::priority)
        val kept = mutableListOf<Evidence>()
        var used = 0
        for (section in order) {
            val cost = xyz.fieldatlas.research.TokenEstimate.of(section.text) + 20
            if (kept.isNotEmpty() && used + cost > maxTokens) continue
            kept += section
            used += cost
        }
        // Most important first: a small model leans on the first record it reads.
        return kept
    }

    /**
     * The key section exactly as published, shown by the app under the model's answer, so the
     * official wording is always on screen even if the model condenses or garbles a step.
     */
    fun officialSteps(section: Evidence, citation: Int, publisher: String): String = buildString {
        append("**Official steps from ").append(publisher).append(", as published** [S").append(citation).append("]\n\n")
        // sections() puts the heading on the first line; show it as a caption, then the body.
        val heading = section.title.substringAfter(": ", "")
        val lines = section.text.lines()
        val body = if (heading.isNotBlank() && lines.firstOrNull() == heading) lines.drop(1) else lines
        if (body !== lines) append("_").append(heading).append("_\n\n")
        for (line in body.filter(String::isNotBlank)) {
            val item = line.trimStart().let { it.startsWith("- ") || Regex("^\\d+\\. ").containsMatchIn(it) }
            append(line).append(if (item) "\n" else "\n\n")
        }
    }.trimEnd()

    /** Written by the app after the model's answer, never by the model. */
    fun safetyNote(guide: EmergencyGuide): String = buildString {
        append("If this is an emergency, call your local emergency number first (112 works on mobile phones in many countries). ")
        append("Steps are from ${guide.source.publisher}; tap a citation to read the guide.")
        if (guide.region == "US") append(" The guide was written for the United States; numbers and some advice (such as local snakes) may differ where you are.")
    }

    /** The guide as a citable source; its text is the published wording. */
    fun evidence(guide: EmergencyGuide) = Evidence(
        documentId = "emergency:${guide.id}",
        chunkId = "emergency:${guide.id}:0",
        title = guide.title,
        source = guide.source.url,
        text = guide.plainText(),
        score = 1.0,
        matchedBy = "emergency guide",
    )

    /**
     * The guide as published, used only when the model cannot run (still loading, failed to
     * load, or failed mid-answer). Only formatting is added; every sentence is the source's own.
     */
    fun answer(guide: EmergencyGuide): String = buildString {
        append("**If this is an emergency, call your local emergency number first** (112 works on mobile phones in many countries).\n\n")
        append("_The answer model isn't available right now, so this is the guide as published._\n\n")
        append("_${guide.title}: shown as published by ${guide.source.publisher} [S1]")
        if (guide.region == "US") append(". This guide was written for the United States; numbers and some advice (such as local snakes) may differ where you are")
        append("._\n\n")
        for (section in guide.sections) {
            if (section.heading.isNotBlank()) append("#".repeat((section.level + 2).coerceAtMost(4))).append(' ').append(section.heading).append("\n\n")
            for (paragraph in section.paragraphs) append(paragraph).append("\n\n")
            section.steps.forEachIndexed { i, step ->
                append("  ".repeat(section.stepLevel(i) - 1))
                append(if (section.numbered) "${i + 1}. " else "- ").append(step).append('\n')
            }
            if (section.steps.isNotEmpty()) append('\n')
        }
    }.trimEnd()
}
