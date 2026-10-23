package xyz.fieldatlas.emergency

import java.util.Locale

/**
 * Checks a first-aid answer the model wrote from an official guide against that guide, on the phone and without a
 * second model call. Add-only, like the figure check for answers from memory: the model's words are kept, and where
 * the guide says otherwise the guide's own sentence is shown under the answer.
 *
 * - A danger sign the person already describes, which the guide lists as a reason to get emergency help at once,
 *   puts "call now" first: a model wrote "call if he loses consciousness" for a confused man after a head injury.
 * - A step the guide forbids ("Do not apply ice") is quoted back when the answer recommends it.
 * - A [S1] on a sentence that shares nothing with the guide is removed, so it cannot look checked.
 * - Labels the model invents in citation brackets ("[General Knowledge]") are removed.
 */
object GuideAnswerCheck {
    data class Result(
        /** The answer with unsupported marks and invented labels removed. */
        val answer: String,
        /** A "call now" line naming the danger sign, or null. */
        val callNow: String?,
        /** Guide sentences the answer goes against, as published. */
        val conflicts: List<String>,
        /** How many [S1] marks were removed for sharing nothing with the guide. */
        val unsupportedMarks: Int,
    )

    fun check(question: String, guide: EmergencyGuide, answer: String): Result {
        val cleaned = withoutInventedLabels(answer)
        val guideText = guide.plainText()
        val guideTerms = supportTerms(guideText).toSet()
        var removed = 0
        val supported = cleaned.lines().joinToString("\n") { line ->
            val lineTerms = supportTerms(line.replace(MARK, "")).distinct()
            // A short step ("Call 911", "Get an AED") is supported when its few words are all in the guide.
            if (!MARK.containsMatchIn(line) || lineTerms.isEmpty()) line
            else if (shared(lineTerms, guideTerms) >= minOf(MIN_SHARED, lineTerms.size)) line
            else { removed += MARK.findAll(line).count(); line.replace(SPACED_MARK, "") }
        }
        val sign = dangerSign(question, guide)
        val callNow = sign?.takeUnless { saysCallNow(supported) }?.let {
            "**Call your local emergency number now.** ${possessive(guide.source.publisher)} guide lists “$it” as a reason to get emergency help right away [S1]."
        }
        val conflicts = conflicts(supported, guide)
        return Result(resolve(supported, guide), callNow, conflicts, removed)
    }

    /**
     * Whether the question describes a sign that puts someone in danger now, for a question no guide matched: such an
     * answer still opens with the call line. It needs a situation ("my", "he", "just"), not a question about the topic.
     */
    fun describesLifeThreat(question: String): Boolean {
        val q = question.lowercase(Locale.ROOT).replace('’', '\'')
        return LIFE_THREAT.containsMatchIn(q) && SITUATION.containsMatchIn(q) && !STUDY.containsMatchIn(q)
    }

    /**
     * Whether a question no guide matched is first aid for someone now: a person with a body sign (the guide matcher's
     * own test), a described life threat, or a decision about emergency medicine at hand ("can he use my inhaler").
     */
    fun firstAidQuestion(question: String): Boolean {
        val q = question.lowercase(Locale.ROOT).replace('’', '\'')
        // "How deep do you push for CPR, just want to know before my course": learning, not someone hurt now (22 Oct device check:
        // the small models gave the no-guide reply instead of an answer).
        if (GENERAL.containsMatchIn(q) && !describesLifeThreat(question)) return false
        return EmergencyGuideMatch.describesFirstAidSituation(question) || describesLifeThreat(question) || swallowedSomething(question) ||
            (EMERGENCY_MEDICINE.containsMatchIn(q) || HEALTH_SIGN.containsMatchIn(q)) && SITUATION.containsMatchIn(q) && !STUDY.containsMatchIn(q) &&
                !EXPLAINING.containsMatchIn(q)
    }

    /** Someone has swallowed medicine, a chemical, a battery or a plant ("took a whole packet of paracetamol"). */
    fun swallowedSomething(question: String): Boolean {
        val q = question.lowercase(Locale.ROOT).replace('’', '\'')
        return (INGESTION.containsMatchIn(q) || DOUBLE_DOSE.containsMatchIn(q) || POISON_PLANT.containsMatchIn(q) || q.contains("overdos") ||
            FOUND_WITH.containsMatchIn(q)) &&
            SITUATION.containsMatchIn(q) && !STUDY.containsMatchIn(q)
    }

    /**
     * The call line a first-aid answer opens with, written by the app: "now" when the question describes a life threat
     * or a swallowed poison, "if this is an emergency" otherwise.
     */
    fun callLine(question: String, guideFound: Boolean = true, guideId: String? = null, cautious: Boolean = false): String =
        if ((describesLifeThreat(question) || swallowedSomething(question) || EmergencyGuideMatch.describesArrest(question)) &&
            !GENERAL.containsMatchIn(question.lowercase(Locale.ROOT).replace('’', '\'')) ||
            // A small model's answer cannot be trusted to say how urgent it is: any first-aid situation opens with "now" unless
            // it is minor or a general question. Sets 53-54: the rules kept missing new wordings of serious signs.
            cautious && (firstAidQuestion(question) || guideId != null && SITUATION.containsMatchIn(question.lowercase(Locale.ROOT))) &&
                !minor(question) && !GENERAL.containsMatchIn(question.lowercase(Locale.ROOT).replace('’', '\'')) &&
                !EXPLAINING.containsMatchIn(question.lowercase(Locale.ROOT)) ||
            // A life-threat guide matched for someone in the situation (set 53: a sting with a hoarse voice and a swelling face
            // opened the anaphylaxis guide with "if this is an emergency").
            guideId in LIFE_THREAT_GUIDES && SITUATION.containsMatchIn(question.lowercase(Locale.ROOT)) && !minor(question) &&
                !GENERAL.containsMatchIn(question.lowercase(Locale.ROOT).replace('’', '\'')) && !EXPLAINING.containsMatchIn(question.lowercase(Locale.ROOT)) ||
            // No guide fits a first-aid situation: "now" unless the message says it is minor. Set 52's ectopic pregnancy,
            // meningitis rash and stroke were each told "if this is an emergency", and none of their signs is a keyword.
            !guideFound && firstAidQuestion(question) && !minor(question) && !GENERAL.containsMatchIn(question.lowercase(Locale.ROOT).replace('’', '\'')) &&
                !EXPLAINING.containsMatchIn(question.lowercase(Locale.ROOT)))
            "**Call your local emergency number now** (112 works on mobile phones in many countries).\n\n"
        else "**If this is an emergency, call your local emergency number first** (112 works on mobile phones in many countries).\n\n"

    /** Says itself that it is small: "doesn't hurt much", "otherwise totally fine", a splinter, a blister, a nettle sting. */
    /** A first-aid question asked to learn ("before my course", "how do you…"), not for someone in trouble now. */
    fun learning(question: String): Boolean {
        val q = question.lowercase(Locale.ROOT).replace('’', '\'')
        return (GENERAL.containsMatchIn(q) || EXPLAINING.containsMatchIn(q)) && !firstAidQuestion(question)
    }

    fun minor(question: String): Boolean {
        val q = question.lowercase(Locale.ROOT).replace('’', '\'')
        return MINOR.containsMatchIn(q) && !SERIOUS.containsMatchIn(q) && !describesLifeThreat(question)
    }

    /** "CDC's", "Wikipedia contributors'". */
    fun possessive(name: String): String = if (name.endsWith("s")) "$name'" else "$name's"

    /** The first danger sign of the guide's call-now lists that the question describes, in the guide's words. */
    internal fun dangerSign(question: String, guide: EmergencyGuide): String? {
        val asked = question.lowercase(Locale.ROOT).replace('’', '\'')
        val askedTokens = words(asked)
        for (section in guide.sections) {
            val lead = (listOf(section.heading) + section.paragraphs.takeLast(1)).joinToString(" ")
            if (section.steps.isEmpty() || !CALL_NOW_LIST.containsMatchIn(lead) || !CONDITION.containsMatchIn(lead)) continue
            for (step in section.steps) for (part in step.split(SIGN_PARTS)) {
                val signTerms = terms(part).filterNot { it in WEAK_SIGN }
                if (signTerms.isEmpty() || signTerms.any { it.length < MIN_SIGN_TERM } && signTerms.size == 1) continue
                if (signTerms.all { term -> askedTokens.withIndex().any { (i, w) -> same(w, term) && !negated(askedTokens, i) } })
                    return part.trim().trimEnd('.', ',', ';').replaceFirstChar { it.lowercase(Locale.ROOT) }
            }
        }
        return null
    }

    /** Guide prohibitions ("Do not …", "Never …", "Avoid …") the answer recommends in a sentence without a negation. */
    /**
     * What may be shown while the answer is still being written: completed lines only, with invented labels removed and
     * any line that goes against the guide already corrected. The line being written stays hidden until it ends.
     */
    fun streamed(partial: String, guide: EmergencyGuide): String {
        val done = partial.substringBeforeLast('\n', "")
        return resolve(withoutInventedLabels(done), guide)
    }

    internal fun conflicts(answer: String, guide: EmergencyGuide): List<String> =
        answer.split(SENTENCE).mapNotNull { forbiddenBy(it, guide) }.distinct()

    /**
     * The answer with every sentence that recommends what the guide forbids replaced by the guide's own sentence, cited:
     * the reader never sees both and has to choose. Used on each completed line while the answer streams, and on the end.
     */
    fun resolve(answer: String, guide: EmergencyGuide): String = answer.lines().joinToString("\n") { line ->
        val prefix = LIST_PREFIX.find(line)?.value.orEmpty()
        val body = line.removePrefix(prefix)
        val sentences = body.split(INLINE_SENTENCE)
        if (sentences.none { forbiddenBy(it, guide) != null }) line
        else prefix + sentences.joinToString(" ") { s -> forbiddenBy(s, guide)?.let { "$it [S1]" } ?: s }
    }

    /** The guide's prohibition a sentence goes against, or null. A sentence with a negation is never read as recommending. */
    private fun forbiddenBy(sentence: String, guide: EmergencyGuide): String? {
        val lower = sentence.lowercase(Locale.ROOT)
        if (sentence.isBlank() || NEGATION.containsMatchIn(lower)) return null
        val said = words(lower)
        return prohibitions(guide).firstOrNull { rule ->
            alternatives(PROHIBITION.replace(rule, "")).any { forbidden -> forbidden.all { t -> said.any { same(it, t) } } }
        }
    }

    private fun prohibitions(guide: EmergencyGuide): List<String> =
        guide.plainText().split(SENTENCE).map { it.trim().removePrefix("• ").replace(Regex("^\\d+\\. "), "") }
            .filter { PROHIBITION.containsMatchIn(it) }

    /**
     * What a prohibition forbids, one alternative at a time: "apply ice or immerse the wound in water" forbids "apply
     * ice" and "immerse … water". An alternative of one word borrows the rule's verb.
     */
    private fun alternatives(rule: String): List<List<String>> {
        val parts = rule.split(ALTERNATIVE).map { terms(it).take(PROHIBITION_TERMS) }.filter { it.isNotEmpty() }
        val verb = parts.firstOrNull()?.firstOrNull() ?: return emptyList()
        return parts.map { if (it.size == 1 && it[0] != verb) listOf(verb) + it else it }.filter { it.size >= MIN_PROHIBITION_TERMS }
    }

    private fun saysCallNow(answer: String): Boolean = answer.lines().filter { it.isNotBlank() }.take(3).any { line ->
        val l = line.lowercase(Locale.ROOT)
        CALL.containsMatchIn(l) && !Regex("\\b(if|when|unless|should)\\b").containsMatchIn(l)
    }

    private fun withoutInventedLabels(text: String): String = INVENTED_LABEL.replace(text, "")

    private fun negated(tokens: List<String>, at: Int): Boolean =
        tokens.subList(maxOf(0, at - NEGATION_WINDOW), at).any { it in NEGATORS || it.endsWith("n't") }

    private fun shared(a: List<String>, b: Set<String>): Int = a.toSet().count { t -> b.any { same(it, t) } }

    /** Two words count as the same when they share their first five letters ("confused", "confusion"), or are equal. */
    private fun same(a: String, b: String): Boolean =
        if (a.length < STEM || b.length < STEM) a == b else a.regionMatches(0, b, 0, STEM)

    private fun words(text: String): List<String> = Regex("[a-z0-9']+").findAll(text).map { it.value }.toList()

    private fun terms(text: String): List<String> = words(text.lowercase(Locale.ROOT)).filter { it.length > 2 && it !in STOP }

    /** Content words for citation support: only function words are left out, and "9-1-1" reads as "911". */
    private fun supportTerms(text: String): List<String> =
        words(text.lowercase(Locale.ROOT).replace("9-1-1", "911")).filter { it.length > 2 && it !in FUNCTION_WORDS }

    private const val STEM = 5
    private const val MIN_SHARED = 2
    private const val MIN_SIGN_TERM = 5
    private const val NEGATION_WINDOW = 3
    private const val PROHIBITION_TERMS = 3
    private const val MIN_PROHIBITION_TERMS = 2
    private val MARK = Regex("\\[S1]")
    private val SPACED_MARK = Regex(" ?\\[S1]")
    /** "[General Knowledge]", "[Common sense]": a label in citation brackets that names no saved source. */
    private val INVENTED_LABEL = Regex(" ?\\[(?!S\\d+])(?=[^\\]\\n]*[A-Za-z])[^\\]\\n\\[]{2,40}](?!\\()")
    private val SENTENCE = Regex("(?<=[.!?])\\s+|\\n+")
    private val INLINE_SENTENCE = Regex("(?<=[.!?])\\s+")
    private val LIST_PREFIX = Regex("^\\s*(?:[-*•]|\\d+\\.)\\s+")
    private val CALL = Regex("\\b(call|dial|phone|ring)\\b.{0,40}\\b(emergency|911|9-1-1|112|999|000|ambulance)\\b|\\bget emergency help\\b")
    private val CALL_NOW_LIST = Regex("(?i)\\bcall (9-?1-?1|911|999|an ambulance|emergency)|emergency (department|room|medical care)|medical help right away")
    private val CONDITION = Regex("(?i)\\bif\\b|danger signs?|when to get help")
    private val ALTERNATIVE = Regex(",\\s*(?:or\\s+)?|\\s+or\\s+")
    private val SIGN_PARTS = Regex(",\\s*(?:or\\s+)?|\\s+or\\s+|;\\s*")
    private val PROHIBITION = Regex("(?i)^(?:do not|don't|never|avoid)\\s+")
    private val NEGATION = Regex("\\b(not|never|no|avoid|without|instead|unless|rather)\\b|n't\\b")
    private val NEGATORS = setOf("no", "not", "never", "without", "nor")
    private val FUNCTION_WORDS = setOf(
        "the", "and", "for", "are", "was", "were", "has", "have", "had", "with", "that", "this", "these", "those", "from",
        "into", "onto", "you", "your", "they", "them", "their", "his", "her", "him", "she", "its", "who", "what", "when",
        "where", "which", "while", "will", "would", "can", "could", "may", "might", "should", "must", "does", "did",
        "been", "being", "than", "then", "there", "here", "also", "very", "any", "all", "each", "such", "just", "not",
    )
    private val STOP = setOf(
        "the", "and", "for", "are", "was", "were", "has", "have", "had", "with", "that", "this", "these", "those", "from",
        "into", "onto", "you", "your", "they", "them", "their", "his", "her", "him", "she", "its", "who", "what", "when",
        "where", "which", "while", "will", "would", "can", "could", "may", "might", "should", "must", "does", "did", "doing",
        "been", "being", "than", "then", "there", "here", "also", "very", "more", "most", "some", "any", "all", "each",
        "other", "one", "two", "such", "only", "just", "out", "off", "over", "under", "again", "until", "after", "before",
        "soon", "away", "get", "gets", "got", "make", "makes", "like", "about", "because", "how", "why", "not", "don't",
        "person", "people", "someone", "anyone", "help", "call", "emergency", "now", "right", "immediately", "medical",
        "care", "doctor", "seek", "go", "goes", "keep", "use", "take", "give",
    )
    /** Words that alone describe no danger ("places" in "cannot recognize people or places"). */
    private val WEAK_SIGN = setOf("places", "things", "time", "times", "day", "days", "hours", "minutes", "listed", "above", "below", "signs", "sign")
    private val LIFE_THREAT = Regex(
        "\\b(not|isn't|isnt|stopped|no longer|can't|cant|cannot|barely|struggling to|trouble) breath|" +
            "\\b(unresponsive|unconscious|not responding|won't wake|wont wake|passed out|collapsed|fainted and)\\b|" +
            "\\b(turning|going|gone|went|is|are|look(?:s|ing)?) (blue|grey|gray)\\b|\\b(lips|face|skin) (?:are |is )?(?:turning |going )?blue\\b|" +
            "\\bchok(e|es|ed|ing)\\b|\\b(seizure|fitting|convuls)|\\bspurting\\b|won't stop bleeding|wont stop bleeding|bleeding (heavily|badly|a lot)|" +
            "\\b(chest pain|crushing pain|pain in (his|her|my) chest)\\b|\\bswallowed\\b.{0,40}\\b(battery|batteries|magnet|bleach|poison|pills|tablets|chemical)|" +
            "\\b(drank|drunk|swallowed) (some )?(bleach|poison|antifreeze|drain cleaner|oven cleaner|lamp oil|kerosene)\\b|\\boverdos|\\banaphyla|" +
            "\\bthroat (is )?(closing|swelling|tight)|\\bdrooping\\b|\\bslurred\\b|\\b(pinned|crushed|trapped) under\\b|\\bdrown|\\belectrocut|" +
            "\\b(struck|hit) by lightning\\b|\\blightning (hit|struck)\\b|" +
            "\\b(lips|face|skin|fingers?)\\b.{0,15}\\b(blue|blueish|bluish|grey|gray)\\b|\\b(can't|cant|cannot) (finish a sentence|speak in (full )?sentences|catch (his|her|my) breath)|\\bgasping\\b|" +
            "\\bwaters (have )?(gone|broken|broke)\\b|\\bin labou?r\\b|\\bbaby'?s head\\b|\\bcrowning\\b|\\bfloppy\\b|\\bagoni[sz]ing\\b|\\bclammy\\b|" +
            "\\bpregnan\\w*\\b.{0,60}\\b(?:bleed\\w*|blood)\\b|\\b(?:bleed\\w*|blood)\\b.{0,60}\\bpregnan\\w*\\b|" +
            "\\b(?:won't|wont|doesn't|not) respond\\w*\\b|\\bcollapsed\\b|" +
            "\\bepi-?pens?\\b|\\bauto-?injector\\b|\\b(allergic reaction|anaphylactic)\\b|\\bthroat (feels |is |getting )?(tight|tightening|closing|swelling)\\b|" +
            "\\bburied (by|under|in)\\b|\\bavalanche\\b|\\bsmoke (in|is|coming|everywhere|filling)\\b|\\b(on fire|in flames|up in flames)\\b|" +
            // NHS "call 999" signs that no bundled guide covers, which set 50 left with "if this is an emergency": legs gone
            // numb after a fall, mottled skin and no wee (sepsis), pear-drop breath or a meter reading HI (ketoacidosis), too
            // breathless to talk, vision changes or sudden swelling in pregnancy, and arm pain with sweating.
            "\\b(can't|cant|cannot) (feel|move) (his|her|my|their) legs\\b|\\blegs (are |have gone |gone |feel )?(numb|dead)\\b|" +
            "\\b(blotchy|mottled)\\b|\\bhasn'?t (peed|weed|urinated|had a wee)\\b|" +
            "\\bbreath smells? (like|of) (pear drops|nail polish|acetone|fruit\\w*)|\\b(meter|monitor|reading|it) (says|reads|shows|just says) hi\\b|" +
            "\\btoo (breathless|out of breath|wheezy) to (talk|speak|eat|walk)\\b|\\bneck muscles\\b|" +
            "\\bpregnan\\w*\\b.{0,150}\\b(flashing lights|blurred vision|blurry vision|(face|hands|feet|ankles)( and \\w+)? (have |has )?(gone |are |is |got )?(really |very |suddenly )?(puffy|swollen))|" +
            "\\b(flashing lights|blurred vision|blurry vision)\\b.{0,150}\\bpregnan|" +
            "\\barm (aches|aching|hurts|pain)\\b.{0,60}\\b(sweat\\w*|clammy|sick)\\b|\\b(sweat\\w*|clammy)\\b.{0,80}\\barm (aches|aching|hurts|pain)\\b|" +
            "\\bpregnan\\w*\\b.{0,150}\\b(shoulder ?tip|tip of (her|my|the) shoulder|(nearly |almost )?fainted|passed out|one side)\\b|" +
            "\\brash\\b.{0,60}\\b(doesn't|doesnt|does not|won't|wont|didn't|didnt) (go away|fade|disappear)|\\bglass test\\b|\\bnon-?blanching\\b|\\bstiff neck\\b|" +
            "\\bstopped (his|her|my|their) insulin\\b|" +
            // A silent chest in an asthma attack, or pale lips: the NHS lists both as life-threatening (22 Oct device check).
            "\\bsilent chest\\b|\\b(can't|cant|cannot) hear (the |her |his |their )?wheez\\w*|\\b(lips|face) (look|looks|are|is|have gone|has gone|gone|going|turning) (pale|grey|gray|blue|white)\\b|" +
            "\\bwaters\\b.{0,12}\\b(went|gone|broke|broken|burst)\\b|\\b(something|cord|loop)\\b.{0,40}\\bhanging (down|out)\\b|\\bcord (is )?(hanging|coming|showing|out)\\b|" +
            "\\belectric (shocks?|shooting|jolts?)\\b.{0,30}\\b(arms?|legs?|hands?|feet|spine|neck|back)\\b|\\b(arms?|legs?|hands?|feet) (both )?(went|gone|go|feel|feeling) (numb|dead|tingl\\w*|like pins)\\b|" +
            // Reduced baby movements late in pregnancy: the NHS says to call the maternity unit straight away.
            "\\b(haven't|havent|have not|hasn't|hasnt|not) (felt|feeling) (the |my |her )?baby move\\b|\\bbaby (hasn't|hasnt|isn't|isnt|is not|has not|stopped) (been )?mov\\w*|\\breduced (baby |fetal |foetal )?movements?\\b|\\b(can't|cant|cannot) (feel|move) (his|her|my|their) arms\\b|" +
            // A fall with neck or back pain and numbness or tingling: a possible spinal injury (set 53, off a horse).
            "\\b(neck|back) (hurts|is (sore|hurting|painful)|pain\\w*)\\b.{0,80}\\b(numb\\w*|tingl\\w*|pins and needles|can't feel|cant feel|can't move|cant move)\\b|" +
            "\\b(numb\\w*|tingl\\w*|pins and needles)\\b.{0,80}\\b(neck|back) (hurts|is (sore|hurting|painful)|pain\\w*)\\b",
    )
    private val INGESTION = Regex("\\b(?:took|taken|swallowed|ingested|ate|eaten|drank|drunk|downed|got into|got hold of|chewed|licked)\\b.{0,50}\\b(?:pills?|tablets?|capsules?|packet|bottle|" +
        "medicine|medication|meds|paracetamol|acetaminophen|tylenol|ibuprofen|aspirin|amlodipine|oxy\\w*|opioids?|fentanyl|heroin|berries|" +
        "mushrooms?|batter(?:y|ies)|bleach|chemicals?|cleaner|detergent|pods?|antifreeze|pill box|" +
        // Garage and household liquids and small swallowed things (set 50: a mouthful of windscreen washer fluid).
        "(?:washer|windscreen|screen|brake|radiator|cleaning) fluid|washer|screen ?wash|coolant|de-?icer|petrol|gasoline|diesel|paraffin|white spirit|turps|lighter fluid|" +
        "nail polish remover|mouthwash|vapes?|e-?liquid|nicotine|magnets?|descaler|limescale remover|toilet (?:cleaner|duck|block))\\b")
    /** Found with an opened medicine or chemical and traces on the mouth or hands. */
    private val FOUND_WITH = Regex("\\bfound (my |our |the |a )?(\\w+ ){0,3}(with|holding|playing with|chewing|sucking)\\b.{0,80}\\b((lid|cap|top) (was )?(off|open)|" +
        "(round|around|on|in) (his|her|their) (mouth|lips|tongue)|empty|missing)\\b")
    /** A poisonous plant eaten by mistake ("picked wild garlic for a salad ... were lily of the valley"). */
    private val POISON_PLANT = Regex("\\b(?:lily of the valley|foxglove|hemlock|deadly nightshade|nightshade|yew|death cap|destroying angel|monkshood|aconite|oleander|castor beans?)\\b")
    /** A medicine taken twice or too much by mistake ("took his evening bisoprolol twice"); not "it took twice as long". */
    private val DOUBLE_DOSE = Regex("\\bdouble (dose|doses|(his|her|their) dose)\\b|\\b(took|taken|had|given|gave (him|her|them)) (his |her |their |the |a )?(\\w+ ){0,3}" +
        "(twice|two doses|an extra dose)\\b(?! as)|\\btoo many (pills|tablets|capsules)\\b|\\bsecond dose by mistake\\b")
    private val MINOR = Regex("\\b(minor|tiny|small|little|a bit|slightly|mild|doesn't hurt (much|too much|a lot)|doesnt hurt (much|too much)|" +
        "not (too |very |that )?(bad|painful|serious|deep)|otherwise (fine|ok|okay|well|happy|totally fine)|totally fine|just (asking|wondering|curious)|" +
        "annoying|itch\\w*|graze\\w*|splinter|blister|bruise\\w*|stubbed|mozzie|mosquito|nettle\\w*|hiccups?|paper cut)\\b")
    /** Asking to learn, not for someone now: "before my course next week", "for their first aid badge", "just want to know". */
    private val GENERAL = Regex("\\b(${EmergencyGuideMatch.TRIP_AHEAD}|just want(ed)? to know|before (my|a|the|our) (course|class|training|test|exam)|for (my|a|the|their|our) (course|class|badge|exam|test|training)|" +
        "first[- ]aid (course|class|badge|kit|box|training)|practi[sc]e|out of interest|trying to understand|in case (it|this|that) (ever )?happens|in general|" +
        "next (week|month|year)|starts? (on )?(solid|solids|school|nursery|weaning)|in case)\\b")
    private val LIFE_THREAT_GUIDES = setOf("cpr", "choking", "severe-bleeding", "shock", "heart-attack", "stroke", "anaphylaxis", "opioid-overdose",
        "drowning", "carbon-monoxide")
    /** "How do you tell a heart attack from a cardiac arrest", "what's the difference": learning, not someone in trouble. */
    private val EXPLAINING = Regex("^\\s*(how do you|how can you|what is|what's|whats|what are|why do|why does|can you get|is it (legal|true|ok|okay|safe|bad)|should you)\\b|\\bdifference between\\b|\\bnotes?\\b|\\bdo you (need|have to)\\b|\\bdoes it (tell|talk|work|say)\\b|\\bhow does (a|an|the|it)\\b")
    /** A sign that is never minor, however the message plays it down ("a bit breathless", "a bit sleepy"). */
    private val SERIOUS = Regex("\\b(breath\\w*|chest|sweat\\w*|clammy|faint\\w*|confus\\w*|drows\\w*|sleepy|mutter\\w*|slurr\\w*|vomit\\w*|being sick|throwing up|" +
        "seiz\\w*|pins and needles|pale|grey|gray|blue|unconscious|collaps\\w*|swallow\\w*|wheez\\w*|rash|stiff neck|pregnan\\w*|" +
        "head|neck|lips|throat)\\b")
    /**
     * A health sign or medicine mishap in someone now that no guide rule names: "words make no sense and his foot is dragging",
     * "taking her weekly methotrexate every day", "out for hours in the frost, muttering" (set 54). Counted as first aid so a
     * small model gives no treatment from memory and the answer opens with the call line.
     */
    private val HEALTH_SIGN = Regex("\\b(sleepy|drowsy|mutter\\w*|muddled|confus\\w*|slurr\\w*|(make|makes|making) no sense|not making sense|dragging|droop\\w*|" +
        "breathless|short of breath|sweaty|clammy|pale|lips (look|are|have gone|gone|going)|wheez\\w*|rattl\\w*|gurgl\\w*|vomit\\w*|being sick|throwing up|" +
        "mouth ulcers?|(every day|daily) (this week|by mistake|instead)|by mistake|too (much|many) (of )?(his|her|their|my)?\\s?(pills|tablets|medicine|meds)|" +
        "patch (in|on) (his|her|their) mouth|sucking on (a|the|it|his|her)|frost|hypotherm\\w*|silent chest|can't hear (the |her |his )?wheeze)\\b")
    private val EMERGENCY_MEDICINE = Regex("\\b(inhaler|salbutamol|albuterol|ventolin|puffer|epi-?pens?|auto-?injector|adrenaline|epinephrine|" +
        "glucagon|glucose (gel|tablets?)|insulin|naloxone|narcan|nitro(glycerin)?|gtn spray|aspirin)\\b")
    private val SITUATION = Regex("\\b(my|our|i|i'm|im|he|she|he's|she's|hes|shes|they|we|someone|somebody|friend|baby|son|daughter|kid|child|toddler|just|now|ago)\\b")
    private val STUDY = Regex("\\b(exam|essay|homework|study|studying|quiz|course|class|lecture|novel|film|movie|story|in general)\\b")
}
