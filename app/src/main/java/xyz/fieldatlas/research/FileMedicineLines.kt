package xyz.fieldatlas.research

import java.util.Locale
import xyz.fieldatlas.attachments.ExtractedAttachment
import xyz.fieldatlas.text.MarkdownLiteral

/**
 * An answer about taking a medicine in the person's own file shows the file's lines on it, word for word: the dose, how
 * often, until when, and what to do about a missed dose. On sealed file questions (sets 31 and 32) the model let "carry
 * on daily" stand for a once-a-week methotrexate sheet, said a scan letter gave no plan where it recommends alendronate
 * 70 mg weekly, and turned "35 days in total" into six weeks.
 *
 * The model's answer is neither changed nor withheld: a review step once rejected most correct file answers. The lines
 * are added under it, and when the question or the answer gives a medicine a different frequency than the file does
 * (daily for a weekly one, or the reverse), the note says so first. Lexical only: it quotes, it does not interpret.
 */
internal object FileMedicineLines {
    /** In place of the leaflet note when the file's own lines are quoted: the person's sheet is the leaflet. */
    const val DOSE_NOTE = "_Amounts of medicine are left out of this explanation; your file's own lines on it are below._"
    private const val MAX_LINES = 6
    private const val MAX_CHARS = 320
    /** Lines under a medicine's own line that may still be about it ("Take 1 tablet every 12 hours", "LAST DOSE: …"). */
    private const val FOLLOWING = 3

    /** An amount of medicine: "15 mg", "1.25 MG", "50,000 UNIT", "16 units", "5 ml", "2 tabs". */
    private val amount = Regex("(?i)(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)*\\s*(?:mg|mcg|µg|micrograms?|milligrams?|units?|iu|ml)(?![\\p{L}])|" +
        "(?<![\\p{L}\\p{N}])\\d+\\s*(?:x\\s*)?(?:tablets?|tabs?|capsules?|caps?|puffs?|drops?|sachets?)\\b")
    /** "Keep to no more than 14 units a week" is alcohol, not a dose. */
    private val alcohol = Regex("(?i)\\b(?:alcohol|units? (?:a|per) (?:week|day)|units of)\\b")
    /** A medical document: a medicine's own unit, or units and millilitres among words that only medicine uses. */
    private val medicineUnit = Regex("(?i)(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)*\\s*(?:mg|mcg|µg|micrograms?|milligrams?|iu)(?![\\p{L}])")
    /** A medicine's own amount, grams included ("Paracetamol 1 g", "Mesalazine 1.6 g"): it starts a new entry in a list. */
    private val doseUnit = Regex("(?i)(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)*\\s*(?:mg|mcg|µg|micrograms?|milligrams?|g|grams?|units?|iu|ml)(?![\\p{L}])")
    /** A medicine named before its amount: "METHOTREXATE 15 mg", "Apixaban 2.5 mg", "oral alendronate 70 mg". */
    private val namedBeforeAmount = Regex("(?i)\\b(\\p{L}[\\p{L}-]{4,})\\s+\\(?\\d+(?:[.,]\\d+)*\\s*(?:mg|mcg|µg|micrograms?|g|units?|iu|ml)\\b")
    /** "What did the doctor say to do": the plan is in a recommendation with an amount. */
    private val asksPlan = Regex("(?i)\\b(?:what (?:did|does|do) (?:the |my |her |his |their )?[\\p{L}]+ (?:say|said|want|recommend|suggest|advise)|" +
        "plan|recommend\\w*|treat\\w*|prescri\\w*)\\b")
    private val recommends = Regex("(?i)\\b(?:recommend\\w*|advis\\w*|suggest\\w*|prescri\\w*|start\\w*|commence\\w*|begin|initiat\\w*|plan)\\b")
    private val missedWords = Regex("(?i)\\b(?:miss\\w*|forg[eo]t\\w*|remember\\w*|double|skip\\w*|late)\\b")
    private val medicineWords = Regex("(?i)\\b(?:tablets?|capsules?|insulin|inhaler|prescri\\w*|pharmac\\w*|medicines?|medications?|" +
        "by mouth|orally|oral|syringe|inject\\w*|patient|doctor|gp|nurse|clinic|hospital|discharge)\\b")

    private val weekly = Regex("(?i)\\b(?:once (?:a|per|every) week|once-? ?weekly|weekly(?! total)|every week|each week|" +
        "(?:every|each|on) (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?|(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)s only)\\b")
    /** Weekly as a question or an answer claims it: "on a thursday" names a day, "on sundays" and "once a week" a frequency. */
    private val saysWeekly = Regex("(?i)\\b(?:once (?:a|per|every) week|once-? ?weekly|weekly|every week|each week|" +
        "every (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|on (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)s|" +
        "(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)s only)\\b")
    private val daily = Regex("(?i)\\b(?:daily|every ?day|each day|(?:once|twice|\\d+ times|two times|three times|four times) (?:a|per|each) day|" +
        "every (?:morning|night|evening)|each (?:morning|night|evening)|nightly|every \\d+ (?:hours|hrs|h)|bd|tds|qds)\\b")
    /** What an instruction about taking it says besides the amount. */
    private val instruction = Regex("(?i)\\b(?:take|taking|give|giving|swallow|inject|use|apply|until|stop\\w*|continue|start\\w*|finish\\w*|" +
        "last dose|miss\\w*|forg[eo]t\\w*|remember|double|do not|don'?t|never|for \\d+ (?:days|weeks|months)|in total|course|increase|reduce)\\b")

    /** A question about taking it: an amount, how often, a missed dose, stopping, what the doctor said to do. */
    private val aboutTaking = Regex("(?i)\\b(?:doses?|dosage|dosing|tablets?|tabs?|pills?|capsules?|meds?|medicines?|medications?|drugs?|" +
        "insulin|inhaler|puffs?|injections?|jabs?|units|ml|prescri\\w*|take|takes|taking|took|give|giving|gave|stop|stopping|finish\\w*|" +
        "miss(?:ed)?|forg[eo]t\\w*|skip\\w*|double|blood thinner|water tablet|steroids?|antibiotics?|painkillers?|treat\\w*|recommend\\w*|" +
        "how (?:much|many|often|long)|what did (?:the |my )?[\\p{L}]+ (?:say|said|want)|" +
        // "Is zorbitrex daily?" asks how often it is taken.
        "daily|weekly|monthly|every ?day|each day|once a (?:day|week|month)|twice a day|every (?:morning|night|evening|week)|nightly|" +
        "what (?:does|do) (?:the |my )?(?:doctor|gp|consultant|nurse|clinic|hospital|specialist|team|letter|sheet) (?:say|want|recommend))\\b")

    /** Everyday words for a kind of medicine, and the names a file uses for them. */
    private val kinds = mapOf(
        "blood thinner" to "apixaban rivaroxaban edoxaban dabigatran warfarin heparin enoxaparin dalteparin tinzaparin clopidogrel " +
            "ticagrelor prasugrel aspirin anticoagulant anticoagulation thromboprophylaxis",
        "water tablet" to "furosemide frusemide bumetanide diuretic bendroflumethiazide indapamide spironolactone torasemide",
        "water pill" to "furosemide frusemide bumetanide diuretic hydrochlorothiazide chlorthalidone spironolactone torsemide",
        "steroid" to "prednisolone prednisone dexamethasone hydrocortisone methylprednisolone",
        "antibiotic" to "amoxicillin augmentin co-amoxiclav doxycycline azithromycin cefalexin cephalexin nitrofurantoin " +
            "flucloxacillin clarithromycin trimethoprim penicillin",
        "painkiller" to "paracetamol acetaminophen ibuprofen codeine naproxen co-codamol tramadol morphine",
        "pain killer" to "paracetamol acetaminophen ibuprofen codeine naproxen co-codamol tramadol morphine",
        "vitamin d" to "colecalciferol cholecalciferol ergocalciferol calciferol",
        // When in the day: "at night" is the evening or bedtime line.
        "night" to "evening bedtime dinner supper tea pm nightly",
        "morning" to "breakfast am mornings",
        "lunch" to "lunchtime midday noon",
        // A forgotten dose is a missed one.
        "forget" to "miss missed missing remember forgotten",
        "forgot" to "miss missed missing remember forgotten",
    )
    private val questionWords = ("the and for are was were what when where which who how why can could should would will does did " +
        "have has had been being with from that this these those there their them they you your yours our ours his her hers its " +
        "mine just only also very really still now then than into onto about after before over under again any some all much many " +
        "more most less least lot lots thing things get got going gonna want wanted need needed meant supposed allowed okay right " +
        "sure worried scared feel feeling think thought know say said tell told mean means ask asked bit kind sort like make made " +
        "take takes taking took give giving gave dose doses dosage tablet tablets tab tabs pill pills capsule capsules caps medicine " +
        "medicines medication medications meds drug drugs day days week weeks daily weekly every each once twice time times today " +
        "tomorrow yesterday tonight next last first new old file sheet letter note notes plan doctor nurse hospital clinic help " +
        "please thanks yes not dont don't cant can't im i'm ive i've its it's whats what's doing done one two three four five").split(' ').toSet()
    private val sentenceEnd = Regex("(?<=[.!?])\\s+")
    private val split = Regex("(?<=\\p{N})(?=\\p{L})|(?<=\\p{L})(?=\\p{N})")

    /** A line of one attachment: [doc] is its place among the attachments, the identity; the name is only shown. */
    private class Line(val doc: Int, val text: String, val index: Int, val words: List<String>)

    private fun words(text: String): List<String> = split.replace(text.lowercase(Locale.ROOT).replace('’', '\''), " ")
        .replace(Regex("(?<=\\d),(?=\\d{3})"), "")
        .split(Regex("[^\\p{L}\\p{N}'-]+")).map { it.trim('\'', '-') }.filter { it.isNotEmpty() }

    private fun stem(word: String) = if (word.length > 4 && word.endsWith("s") && !word.endsWith("ss")) word.dropLast(1) else word

    /** The question's own words for the file, with the file's names for the kinds of medicine it names. */
    private fun questionTerms(question: String): Set<String> {
        val lower = question.lowercase(Locale.ROOT)
        val kindsNamed = kinds.filterKeys { Regex("\\b${Regex.escape(it)}s?\\b").containsMatchIn(lower) }
        // "blood thinner" is read as its medicines, not as "blood" (which a blood test line also has) and "thinner".
        val phraseWords = kindsNamed.keys.filter { ' ' in it }.flatMap { it.split(' ') }.toSet()
        val own = words(question).filter { w -> (w.length >= 3 && w.any(Char::isLetter) || w.length >= 2 && w.all(Char::isDigit)) &&
            w !in questionWords && stem(w) !in phraseWords && w !in phraseWords }
        return (own + kindsNamed.values.flatMap { it.split(' ') }).map(::stem).toSet()
    }

    /** Everyday words that begin longer ones ("for" and "forget", "use" and "usual"), never read as a short form. */
    private val common = questionWords + ("for and but nor yet the you are not all any can may per use see set own out off who " +
        "has had him let put run way too try ago due via end far few big low top war man men age art act add aid " +
        "total maximum minimum around about least usual normal extra").split(' ')

    /**
     * "pred" is prednisolone, "thurs" is Thursday, "nov" is November, "VIT" is vitamin: a shared start of three letters
     * or more, unless the shorter is an everyday word.
     */
    private fun matches(term: String, word: String): Boolean {
        val w = stem(word)
        if (term == w) return true
        if (term.all(Char::isDigit) || w.all(Char::isDigit)) return false
        val (short, long) = if (term.length <= w.length) term to w else w to term
        return short.length >= 3 && short !in common && long.startsWith(short)
    }

    private fun amountIn(text: String) = amount.containsMatchIn(text) && !alcohol.containsMatchIn(text)

    fun note(question: String, answer: String, files: List<ExtractedAttachment>): String? {
        if (files.isEmpty() || !aboutTaking.containsMatchIn(question) && !amountIn(question)) return null
        // Each attachment by its place in the list: two files can share a name ("sheet.txt") and must not share lines.
        val medical = files.withIndex().filter { (_, file) ->
            val text = file.pages.joinToString("\n") { it.text }
            medicineUnit.containsMatchIn(text) || amountIn(text) && medicineWords.containsMatchIn(text)
        }
        if (medical.isEmpty()) return null
        // A line of the file stays whole, so a dose keeps its "until 09/11/2026 inclusive, then STOP"; only a line too
        // long to quote is cut into sentences.
        val lines = medical.flatMap { (doc, file) ->
            file.pages.flatMap { it.text.lines() }.map(String::trim)
                .flatMap { line -> if (line.length <= MAX_CHARS) listOf(line) else line.split(sentenceEnd).map(String::trim) }
                .mapIndexed { index, text -> Line(doc, text, index, words(text)) }
        }
        val byDoc = lines.groupBy { it.doc }
        // The medicines the answer names, as the file names them before an amount ("Apixaban 2.5 mg").
        val answered = lines.flatMap { line -> namedBeforeAmount.findAll(line.text).map { it.groupValues[1].lowercase(Locale.ROOT) }.toList() }
            .filter { it !in common && Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(answer) }
        var terms = questionTerms(question) + answered.map(::stem)
        val planAsked = asksPlan.containsMatchIn(question)
        fun hits(line: Line) = terms.filter { t -> line.words.any { matches(t, it) } }
        val asksMissed = missedWords.containsMatchIn(question)
        fun weightOf(line: Line): Int {
            if (line.text.isEmpty() || line.text.length > MAX_CHARS) return 0
            // "Medicines to take at home:" heads the list and says nothing about any medicine.
            if (line.text.trimEnd().endsWith(":") && !amountIn(line.text)) return 0
            val found = hits(line)
            val named = found.count { it.any(Char::isLetter) }
            val hasAmount = amountIn(line.text)
            // "Recommend oral alendronate 70 mg weekly" answers "what did the doctor say to do" without sharing a word.
            val plan = planAsked && hasAmount && recommends.containsMatchIn(line.text)
            if (named == 0 && !plan) return 0
            val often = weekly.containsMatchIn(line.text) || daily.containsMatchIn(line.text)
            // About taking it: an amount or how often, or a missed-dose rule when one was missed. "Hip precautions (for 6
            // weeks)" shares a word with the question and says nothing about a medicine.
            val aboutTaking = hasAmount || often || asksMissed && missedWords.containsMatchIn(line.text) && instruction.containsMatchIn(line.text)
            if (!aboutTaking && named < 2 && !plan) return 0
            val cues = listOf(hasAmount, often, instruction.containsMatchIn(line.text)).count { it }
            return named * 2 + (found.size - named) + cues + if (plan) 4 else 0
        }
        // A question that names no medicine the file has ("how much of the blood thinner") reads the answer's names too.
        if (lines.none { weightOf(it) > 0 } && answer.isNotBlank()) terms = terms + questionTerms(answer)
        val heads = lines.map { it to weightOf(it) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Line, Int>> { it.second }.thenBy { it.first.doc }.thenBy { it.first.index })
            .map { it.first }.take(MAX_LINES)
        if (heads.isEmpty()) return null

        // Under a medicine's own line, the lines that say how to take it, up to the next medicine or a blank line.
        fun following(head: Line): List<Line> {
            if (!amountIn(head.text)) return emptyList()
            val sameFile = byDoc.getValue(head.doc)
            // A line naming its own medicine and amount ("Zorbitrex 20 mg once weekly") starts the next entry, blank line
            // or not, even when the question names that medicine too: its schedule is not this one's.
            return sameFile.drop(head.index + 1).take(FOLLOWING).takeWhile { line ->
                line.text.isNotEmpty() && !newEntry(line.text) &&
                    !(doseUnit.containsMatchIn(line.text) && !alcohol.containsMatchIn(line.text) && hits(line).isEmpty())
            }.filter { line -> instruction.containsMatchIn(line.text) || weekly.containsMatchIn(line.text) || daily.containsMatchIn(line.text) }
        }
        // A row of a dosing table ("Thursday   5 mg   1 x pink") under its heading ("CURRENT DOSE from Fri 16/10/2026:").
        fun heading(row: Line): Line? {
            if (!amountIn(row.text) || row.text.length > 60) return null
            val sameFile = byDoc.getValue(row.doc)
            return (row.index - 1 downTo maxOf(0, row.index - 8)).map { sameFile[it] }
                .takeWhile { it.text.isNotEmpty() }.firstOrNull { it.text.endsWith(":") }
        }
        val schedules = heads.associateWith { following(it) }
        val chosen = (heads + schedules.values.flatten() + heads.mapNotNull(::heading))
            .distinctBy { it.doc to it.index }
            .sortedWith(compareBy<Line> { it.doc }.thenBy { it.index })
            .let { all -> if (all.size <= MAX_LINES + 2) all else all.filter { it in heads || schedules.values.any { s -> it in s } }.take(MAX_LINES + 2) }

        // Daily for a weekly medicine, or the reverse, said about that medicine in the question or the answer. A medicine
        // is known by the words of the question that name it in its own line ("methotrexate", "folic acid").
        val followers = schedules.values.flatten().toSet()
        // A medicine is known by the question's (or answer's) words in the name its dose line opens with, before the
        // amount: "METHOTREXATE" in "METHOTREXATE 10 mg …", "folic acid" in "Folic acid 5 mg daily". Every medicine line
        // found counts when deciding which medicine a claim is about, so a claim about one never goes to its neighbour;
        // lines without an amount ("Methotrexate is a weekly medicine") name no medicine of their own.
        fun medicineName(head: Line): String? {
            val amountAt = amount.find(head.text)?.range?.first ?: return null
            val opening = words(head.text.substring(0, amountAt)).toSet()
            return hits(head).filter { t -> t.any(Char::isLetter) && !dayOrTime.matches(t) && t !in common && t !in takingIt &&
                opening.any { matches(t, it) } }.sorted().joinToString(" ").ifEmpty { null }
        }
        val medicineOf = schedules.keys.associateWith(::medicineName).filterValues { it != null }.mapValues { it.value!! }
        val checked = medicineOf.filterKeys { doseUnit.containsMatchIn(it.text) && it !in followers }
        val claimed = HashMap<String, MutableSet<Often>>()
        listOf(question, answer).forEach { text -> claimsIn(text, medicineOf.values.toSet()).forEach { (name, said) -> claimed.getOrPut(name) { mutableSetOf() } += said } }
        val conflicts = checked.keys.mapNotNull { head ->
            val said = (listOf(head) + schedules.getValue(head)).map { it.text }
            val fileWeekly = said.any { weekly.containsMatchIn(it) }
            val fileDaily = said.any { daily.containsMatchIn(it) } && !fileWeekly
            val claim = claimed[checked.getValue(head)].orEmpty()
            val name = nameOf(head.text)
            when {
                // Nothing said about this medicine, or both: no mismatch is established.
                claim.size != 1 -> null
                fileWeekly && Often.DAILY in claim -> "your file gives $name once a week, not daily"
                fileDaily && Often.WEEKLY in claim -> "your file gives $name daily, not once a week"
                else -> null
            }
        }.distinctBy { it.lowercase(Locale.ROOT) }
        return buildString {
            if (conflicts.isNotEmpty()) append("**Check how often:** ").append(conflicts.joinToString("; ")).append(".\n\n")
            chosen.groupBy { it.doc }.entries.forEachIndexed { index, (doc, quoted) ->
                if (index > 0) append("\n\n")
                // A name two attachments share is told apart by the file's place among them.
                val name = files[doc].displayName
                val shown = if (files.count { it.displayName == name } > 1) "$name (attached file ${doc + 1})" else name
                append("_From ").append(MarkdownLiteral.encode(shown)).append(", word for word:_")
                quoted.forEach { append("\n- “").append(MarkdownLiteral.encode(it.text)).append("”") }
            }
        }
    }

    private enum class Often { DAILY, WEEKLY }
    /** Days and times of day, which a dose line shares with a question without naming a medicine. */
    private val dayOrTime = Regex("(?i)(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun|" +
        "morning|evening|night|bedtime|breakfast|lunch\\w*|dinner|supper|tea|noon|midday)s?")
    /** Where one claim ends and the next begins: "methotrexate once weekly and folic acid daily". */
    private val clauseBreak = Regex("(?i),|;|\\band\\b|\\bbut\\b|\\bthen\\b|\\balso\\b")
    private val claimSentence = Regex("(?<=[.!?;])\\s+|\\n+")
    /** "Is the methotrexate or the folic acid the daily one?": either could be meant. */
    private val alternative = Regex("(?i)\\b(?:or|either|which|whichever)\\b")
    private val negation = Regex("(?i)\\b(?:not|never|no|don['’]?t|doesn['’]?t|didn['’]?t|isn['’]?t|aren['’]?t|wasn['’]?t|" +
        "shouldn['’]?t|mustn['’]?t|can['’]?t|cannot|won['’]?t|wouldn['’]?t|instead of|rather than)\\b")

    /**
     * How often [text] says each of [medicines] is taken. A "daily" or "once a week" belongs to the one medicine named in
     * its clause, else in its sentence, else in the whole text when that names one. Negated ("I shouldn't take it daily",
     * "not weekly") it is no claim, and one that could be about either of two medicines is left out: a correction is
     * only written when the mismatch is clear.
     */
    private fun claimsIn(text: String, medicines: Set<String>): Map<String, Set<Often>> {
        fun named(part: String): Set<String> {
            val said = words(part)
            return medicines.filter { medicine -> medicine.split(' ').any { name -> said.any { matches(name, it) } } }.toSet()
        }
        val names = medicines.flatMap { it.split(' ') }.toSet()
        val inText = named(text)
        val out = HashMap<String, MutableSet<Often>>()
        for (sentence in text.split(claimSentence)) {
            val inSentence = named(sentence)
            for (clause in sentence.split(clauseBreak)) {
                val inClause = named(clause)
                for ((pattern, often) in listOf(daily to Often.DAILY, saysWeekly to Often.WEEKLY)) {
                    for (match in pattern.findAll(clause)) {
                        if (negation.containsMatchIn(clause.substring(0, match.range.first))) continue
                        if (!aboutTakingIt(clause, match, names)) continue
                        val target = when {
                            inClause.size == 1 -> inClause.single()
                            inClause.size > 1 -> null
                            inSentence.size > 1 -> null
                            inSentence.size == 1 -> inSentence.single()
                            inText.size == 1 && !alternative.containsMatchIn(sentence) -> inText.single()
                            else -> null
                        } ?: continue
                        out.getOrPut(target) { mutableSetOf() } += often
                    }
                }
            }
        }
        return out
    }

    /** Words between a frequency and what it is said of that change nothing: "is just once a week", "the daily one". */
    private val filler = setOf("just", "only", "really", "actually", "still", "now", "then", "also", "always", "usually", "normally",
        "a", "an", "the", "my", "his", "her", "their", "your", "our", "once", "twice")
    /** Taking it, a dose, or the medicine itself as "it" or the subject of "is": what a frequency must be said of. */
    private val takingIt = setOf("take", "takes", "taking", "took", "have", "has", "having", "had", "use", "uses", "using", "used",
        "give", "gives", "giving", "gave", "given", "swallow", "swallows", "inject", "injects", "injecting", "dose", "doses", "dosing",
        "tablet", "tablets", "tab", "tabs", "pill", "pills", "capsule", "capsules", "cap", "caps", "shot", "shots", "injection",
        "injections", "drop", "drops", "puff", "puffs", "unit", "units", "mg", "mcg", "ml", "iu", "g", "it", "them", "this", "that",
        "these", "those", "one", "is", "are", "was", "were", "be", "been", "continue", "continuing", "on")

    /**
     * Whether the frequency [match] in [clause] is said of the medicine: next to its name, a dose, taking it, or "it"
     * ("can I take it daily", "is zorbitrex daily", "20 mg every morning", "carry on daily"). "Should I exercise daily"
     * or "I walk daily" says how often something else is done.
     */
    private fun aboutTakingIt(clause: String, match: MatchResult, names: Set<String>): Boolean {
        fun fits(word: String) = word in takingIt || word.all(Char::isDigit) || names.any { matches(it, word) }
        val before = words(clause.substring(0, match.range.first)).asReversed().dropWhile { it in filler }
        if (before.isNotEmpty()) return fits(before.first())
        // Leading the clause ("daily methotrexate?"): what follows it.
        return words(clause.substring(match.range.last + 1)).dropWhile { it in filler }.firstOrNull()?.let(::fits) == true
    }

    /** A line that names its own medicine before an amount, starting a new entry: "Zorbitrex 20 mg once weekly". */
    private fun newEntry(text: String): Boolean = namedBeforeAmount.findAll(text).any { m ->
        val name = m.groupValues[1].lowercase(Locale.ROOT)
        name !in common && name !in takingIt && !dayOrTime.matches(name) && !instruction.matches(name)
    }

    /** A medicine as its line names it: "METHOTREXATE 15 mg (…) ONCE A WEEK" is "METHOTREXATE". */
    private fun nameOf(line: String): String {
        val before = line.substringBefore(amount.find(line)?.value ?: line).ifBlank { line }
        val name = before.replace(Regex("^[\\s\\-*•\\d.)]+"), "")
            .replace(Regex("(?i)^(?:(?:start|stop|new|continue|changed?|increase|reduce|take|your|the|my|medicine|drug)\\b[\\s:-]*)+"), "")
            .trim(' ', '-', ':', ',', '(')
        return MarkdownLiteral.encode(name.ifBlank { "this medicine" }.take(60))
    }
}
