package xyz.fieldatlas.emergency

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.fieldatlas.research.Evidence

@Serializable
data class NumberBook(
    val version: String,
    val default: List<String> = emptyList(),
    @SerialName("default_note") val defaultNote: String = "",
    val countries: Map<String, CountryNumbers>,
    val source: NumberSource,
)

@Serializable
data class CountryNumbers(
    val name: String,
    val police: List<String> = emptyList(),
    val ambulance: List<String> = emptyList(),
    val fire: List<String> = emptyList(),
    val general: List<String> = emptyList(),
    val mobile: List<String> = emptyList(),
    val listed: Map<String, String> = emptyMap(),
    val notes: String = "",
    val region: String = "",
)

@Serializable
data class NumberSource(
    val title: String,
    val url: String,
    val publisher: String,
    val license: String,
    val revision: Long? = null,
    val retrieved: String = "",
)

/**
 * "Which numbers do I call in an emergency in Thailand?" is answered from the bundled table of
 * emergency telephone numbers, word for word and cited, without the model: a small model once
 * said the EU has no single number and gave France 19.
 */
object EmergencyNumbers {
    private val json = Json { ignoreUnknownKeys = true }
    fun book(text: String): NumberBook = json.decodeFromString(NumberBook.serializer(), text)

    data class Answer(val text: String, val evidence: Evidence)

    private val asks = Regex(
        // "Who do I call", "what do you dial", "which number", "the number for an ambulance".
        "(?i)\\b(?:who|what|which)\\s+(?:number\\s+)?(?:do|does|should|can|would|must)\\s+(?:i|we|you|one|people)\\s+(?:call|dial|ring|phone)\\b|" +
            "\\b(?:which|what(?:'s| is)?(?: the)?) (?:emergency |police |ambulance )?number\\b|" +
            "\\bnumbers? (?:for|of|to call for) (?:an? |the )?(?:police|ambulance|fire|rescue|coast ?guard|emergenc)|" +
            "\\b(?:version|equivalent) of (?:911|999|112|000)\\b|\\b(?:911|999|112|000|111) or (?:911|999|112|000|111)\\b|" +
            "\\b(?:emergency|police|ambulance|fire(?: brigade| department| service)?|rescue)\\s+" +
            "(?:phone |telephone |contact )?(?:numbers?|lines?|hotlines?)\\b|" +
            "\\bnumbers? (?:to|should i|do i|i should|i need to) (?:call|dial|know|ring)\\b|" +
            "\\bwhat (?:number|do i|should i) (?:do i |should i )?(?:call|dial|ring)\\b|" +
            "\\bnumbers? (?:for|of) (?:the )?(?:police|ambulance|fire|rescue|emergenc)",
    )
    // "What number should I call to book a taxi in Thailand?" is not an emergency.
    private val emergency = Regex("(?i)\\b(emergenc\\w*|police|ambulance|fire|rescue|coast ?guard|sos|911|112|999|000|111|accident|hurt|injured|sick)\\b")
    private val europe = Regex("(?i)\\b(?:european union|the eu|eu countries|eu member states|(?:in|across|around|throughout) europe)\\b")
    // Everyday names the table writes differently ("United States of America", "Eswatini").
    private val aliases = mapOf(
        "usa" to "US", "u.s." to "US", "u.s.a." to "US", "the us" to "US", "united states" to "US", "uk" to "GB", "u.k." to "GB",
        "britain" to "GB", "great britain" to "GB", "england" to "GB", "scotland" to "GB", "wales" to "GB", "northern ireland" to "GB",
        "holland" to "NL", "the netherlands" to "NL", "czechia" to "CZ", "czech republic" to "CZ", "south korea" to "KR",
        "north korea" to "KP", "dprk" to "KP", "uae" to "AE", "russia" to "RU", "vietnam" to "VN", "viet nam" to "VN",
        "laos" to "LA", "bolivia" to "BO", "venezuela" to "VE", "iran" to "IR", "syria" to "SY", "tanzania" to "TZ",
        "taiwan" to "TW", "china" to "CN", "mainland china" to "CN", "prc" to "CN", "burma" to "MM", "bahamas" to "BS",
        "türkiye" to "TR", "turkiye" to "TR", "swaziland" to "SZ", "drc" to "CD", "dr congo" to "CD",
        "democratic republic of the congo" to "CD", "congo-kinshasa" to "CD", "republic of the congo" to "CG",
        "congo-brazzaville" to "CG", "vatican" to "VA", "côte d'ivoire" to "CI", "cote d'ivoire" to "CI", "macedonia" to "MK",
    )
    // Places inside a country whose names contain another country's: New South Wales is not Wales,
    // New Jersey not Jersey, New Mexico not Mexico. Georgia the state is decided separately.
    private val usStates = listOf("alabama", "alaska", "arizona", "arkansas", "california", "colorado", "connecticut", "delaware",
        "florida", "hawaii", "idaho", "illinois", "indiana", "iowa", "kansas", "kentucky", "louisiana", "maine", "maryland",
        "massachusetts", "michigan", "minnesota", "mississippi", "missouri", "montana", "nebraska", "nevada", "new hampshire",
        "new jersey", "new mexico", "new york", "north carolina", "north dakota", "ohio", "oklahoma", "oregon", "pennsylvania",
        "rhode island", "south carolina", "south dakota", "tennessee", "texas", "utah", "vermont", "virginia", "west virginia",
        "wisconsin", "wyoming", "washington state", "washington dc", "washington, d.c.")
    private val australianStates = listOf("new south wales", "queensland", "tasmania", "western australia", "south australia",
        "northern territory", "australian capital territory")

    fun answer(question: String, book: NumberBook): Answer? {
        if (!asks.containsMatchIn(question) || !emergency.containsMatchIn(question)) return null
        val codes = countries(question, book)
        if (codes.size > 1) {
            val rows = codes.map { book.countries.getValue(it) }
            return Answer(withFooter(rows.joinToString("\n\n") { describe(it) }, book), Evidence("emergency:numbers-${codes.joinToString("-")}",
                "emergency:numbers-${codes.joinToString("-")}:0", "${book.source.title} — ${rows.joinToString(", ") { it.name }}",
                book.source.url, rows.joinToString("\n\n") { sourceText(it) }, 1.0))
        }
        val code = codes.singleOrNull()
        if (code == null) {
            if (!europe.containsMatchIn(question)) return null
            val text = "**112** is the standard emergency number across the European Union [S1]. " +
                "Each country also keeps its own numbers; ask about one country to see them."
            return Answer(withFooter(text, book), Evidence("emergency:numbers-eu", "emergency:numbers-eu:0",
                "${book.source.title} — European Union", book.source.url, book.defaultNote, 1.0))
        }
        val row = book.countries.getValue(code)
        // The country shares its name with a US state, where the number is 911.
        val note = if (code == "GE") "\n\nThis is Georgia the country. In the US state of Georgia, call **911**." else ""
        return Answer(withFooter(describe(row) + note, book), Evidence("emergency:numbers-$code", "emergency:numbers-$code:0",
            "${book.source.title} — ${row.name}", book.source.url, sourceText(row), 1.0))
    }

    // What a place name is to the person asking, read from the words before it: where they are ("in",
    // "travelling to", "visiting"), where they are from ("from", "live in", "I'm American"), or somebody's
    // name ("my friend Chad").
    private enum class Role { HERE, HOME, NAME, OPEN, KNOWN }
    // Where they are from, or leaving: "leaving Spain tomorrow for Croatia" will be in Croatia.
    private val homeBefore = Regex("(?i)\\b(?:from|live in|living in|lives in|home is|home in|back home in|citizen of|resident of|" +
        "native of|born in|grew up in|passport (?:is )?(?:from)?|leaving|left|departing|flying out of|out of)\\s+(?:the\\s+)?$")
    // A name doing what people do: "Kenya passed out", "Jordan fell off a scooter", "Georgia and I are in Peru".
    private val personAfter = Regex("(?i)^\\s*(?:'s\\s+)?(?:passed out|fainted|fell|is (?:hurt|sick|ill|injured|unconscious|not|bleeding|choking|having)|" +
        "was (?:hit|hurt|stung|bitten|in)|has (?:a|been)|needs|got|collapsed|broke|cut|can'?t|won'?t|isn'?t|and i\\b|and me\\b|here\\b|" +
        "just|says|said|told|feels?|seems)")
    private val hereBefore = Regex("(?i)\\b(?:in|to|at|into|visiting|visit|across|around|inside|throughout|within|through|" +
        "travell?ing(?: in| to| through| around)?|going to|heading to|holiday in|vacation in|trip to|staying in|landed in|arrived in|" +
        "currently in|now in|here in|calling from inside)\\s+(?:the\\s+)?$")
    private val personBefore = Regex("(?i)\\b(?:my|our|his|her|their|a)\\s+(?:\\w+\\s+)?(?:friend|brother|sister|son|daughter|mum|mom|dad|" +
        "father|mother|husband|wife|partner|boyfriend|girlfriend|uncle|aunt|cousin|colleague|boss|kid|child|baby|neighbou?r|guide|driver|" +
        "roommate|flatmate|mate|buddy|guy|man|woman|boy|girl)\\s+$|\\b(?:named|called|mr\\.?|mrs\\.?|ms\\.?|dr\\.?)\\s+$")
    private val joinedBefore = Regex("(?i)^\\s*(?:,|or|and|&|/|then|and then|,\\s*(?:or|and|then))\\s*$")
    private val nationalities = mapOf("american" to "US", "australian" to "AU", "british" to "GB", "canadian" to "CA",
        "new zealander" to "NZ", "kiwi" to "NZ", "irish" to "IE", "german" to "DE", "french" to "FR", "dutch" to "NL", "indian" to "IN",
        "chinese" to "CN", "japanese" to "JP", "korean" to "KR", "brazilian" to "BR", "mexican" to "MX", "south african" to "ZA")
    // A country name that is a town in a state: "Lebanon, New Hampshire", "Panama City, Florida", "Peru, IN".
    private val inState = Regex("^(?:\\s+city)?,\\s*(?:" + (usStates + australianStates).joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}])")
    private val stateCode = Regex("^(?:\\s+[Cc]ity)?,\\s*(?:AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|" +
        "NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY|(NSW|QLD|VIC|TAS|SA|NT|ACT))\\b")
    // A country whose number the person already gives ("in the US I'd call 911, what about Thailand?") is not asked about.
    private val givesNumber = Regex("(?i)\\b(?:call|calls|dial|dials|ring|is|it'?s|use|uses|was)\\s+(?:the\\s+)?\\d{2,4}\\b|\\b\\d{2,4}\\s+(?:is|was|works)\\b")
    private val clauseBreak = Regex("[,;.!?—–]|\\bbut\\b|\\bwhat\\b|\\bwhich\\b|\\bhow\\b")
    // "What numbers do I need for each side?": every place named is asked about, the starting one too.
    private val everyPlace = Regex("(?i)\\b(?:each|both|either)\\s+(?:side|sides|country|countries|border|places?)\\b|\\bfor both\\b|\\bboth numbers\\b")

    private fun clauseAround(text: String, range: IntRange): String {
        val start = clauseBreak.findAll(text.substring(0, range.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
        val end = clauseBreak.find(text, range.last + 1)?.range?.first ?: text.length
        return text.substring(start, end)
    }

    // Places in the US whose name is also a country's: "Atlanta, Georgia" is the state.
    private val usPlace = Regex("(?i)\\b(usa|u\\.s\\.a?\\.?|united states|us state|atlanta|savannah|augusta)\\b")

    /** The countries asked about: where the person is, or the countries named plainly; never only their home. */
    private fun countries(question: String, book: NumberBook): List<String> {
        val text = question.lowercase(Locale.ROOT).replace('’', '\'')
        val names = book.countries.entries.map { (code, row) -> row.name.lowercase(Locale.ROOT) to code } + aliases.toList() +
            usStates.map { it to "US" } + australianStates.map { it to "AU" }
        // Every mention, the longest first where names overlap: "South Africa" over "Africa", "Papua New Guinea" over "Guinea".
        val found = (names.filter { it.second in book.countries } + nationalities.toList().map { it.first to it.second })
            .flatMap { (name, code) -> Regex("(?<![\\p{L}])${Regex.escape(name)}(?![\\p{L}])").findAll(text).map { Triple(it.range, code, name) } }
            .sortedByDescending { it.first.last - it.first.first }
        val picked = mutableListOf<Triple<IntRange, String, String>>()
        for (m in found) if (picked.none { it.first.first <= m.first.last && m.first.first <= it.first.last }) picked += m
        picked.sortBy { it.first.first }
        val mentions = mutableListOf<Pair<String, Role>>()
        var previous: Pair<IntRange, Role>? = null
        for ((range, code0, name) in picked) {
            val lead = text.substring(maxOf(0, range.first - 40), range.first).split('.', '?', '!', ';').last()
            val between = previous?.let { text.substring(it.first.last + 1, range.first) }
            // A town named after a country, placed in a state: the state gives the country. A written-out state
            // is a mention of its own; a postal code ("Peru, IN") stands for its country here.
            val code = if (code0 == "US" || code0 == "AU") null else stateCode.takeIf { question.any(Char::isLowerCase) }
                ?.find(question.substring(minOf(question.length, range.last + 1)))
            val townInState = code0 != "US" && code0 != "AU" && (inState.containsMatchIn(text.substring(range.last + 1)) || code != null)
            val role = when {
                name in nationalities -> Role.HOME
                personBefore.containsMatchIn(lead) || personAfter.containsMatchIn(text.substring(range.last + 1)) -> Role.NAME
                givesNumber.containsMatchIn(clauseAround(text, range)) -> Role.KNOWN
                homeBefore.containsMatchIn(lead) -> Role.HOME
                hereBefore.containsMatchIn(lead) -> Role.HERE
                // "in Sweden or Norway": a name joined to the one before takes its role.
                between != null && joinedBefore.matches(between) -> previous!!.second
                else -> Role.OPEN
            }
            // Georgia the country unless the question places it in the US; "Athens, Georgia" could be either.
            val usHere = usPlace.findAll(text).any { u -> picked.none { it.first.first <= u.range.last && u.range.first <= it.first.last && it.second != "US" } }
            val georgianPlace = Regex("(?i)\\b(tbilisi|batumi|kutaisi|svaneti|kazbegi|mestia)\\b").containsMatchIn(text)
            val cityBefore = name == "georgia" && Regex("\\b\\p{Lu}[\\p{L}.]+,\\s*$").containsMatchIn(question.substring(0, range.first))
            if (name == "georgia" && !usHere && !georgianPlace && cityBefore) { previous = range to role; continue }
            if (townInState) {
                code?.let { mentions += (if (it.groupValues[1].isNotEmpty()) "AU" else "US") to role }
                previous = range to role; continue
            }
            val code1 = if (name == "georgia" && usHere) "US" else code0
            if (code1 in book.countries) mentions += code1 to role
            previous = range to role
        }
        val here = mentions.filter { it.second == Role.HERE }.map { it.first }.distinct()
        val open = mentions.filter { it.second == Role.OPEN }.map { it.first }.distinct()
        // "Both" means two places: the ones they are in or going to when there are two ("from Australia to New
        // Zealand and Fiji, numbers for both"), the starting one too when it takes it to make two ("from Germany
        // into Austria, for both countries").
        if (everyPlace.containsMatchIn(text)) {
            val asked = (here + open).distinct()
            if (asked.size > 1) return asked.take(3)
            mentions.filter { it.second != Role.NAME }.map { it.first }.distinct().takeIf { it.size > 1 }?.let { return it.take(3) }
        }
        // Only a number the person already gave: they are checking it ("Is 112 right in Spain?").
        val known = mentions.filter { it.second == Role.KNOWN }.map { it.first }.distinct()
        return (here.ifEmpty { open }.ifEmpty { known }).take(3)
    }

    private fun describe(row: CountryNumbers): String = buildString {
        // "local numbers only" (North Korea) or an empty cell is not a number to dial.
        val listed = listOf("police", "ambulance", "fire").mapNotNull { kind -> row.listed[kind]?.takeIf { v -> v.any(Char::isDigit) }?.let { kind to it } }
        val single = listed.map { it.second }.distinct().singleOrNull()
        when {
            single != null && listed.size == 3 -> append("In **${row.name}**, call **$single** for police, ambulance and fire [S1].")
            listed.isNotEmpty() -> append("In **${row.name}**: " + listed.joinToString(", ") { (kind, number) -> "$kind **$number**" } + " [S1].")
            row.general.isNotEmpty() -> append("In **${row.name}**, the emergency number is **${row.general.joinToString(" or ")}** [S1].")
            else -> append("The saved list gives no national emergency number for ${row.name}" +
                (row.listed.values.firstOrNull(String::isNotBlank)?.let { " (it says: $it)" } ?: "") + " [S1].")
        }
        if (row.mobile.isNotEmpty()) append(" From a mobile phone: **${row.mobile.joinToString(" or ")}** [S1].")
        if (row.notes.isNotBlank()) append("\n\nAlso listed: ${row.notes} [S1]")
    }

    private fun sourceText(row: CountryNumbers): String = buildString {
        append(row.name)
        row.listed.forEach { (kind, number) -> append("\n").append(kind.replaceFirstChar(Char::uppercaseChar)).append(": ").append(number) }
        if (row.mobile.isNotEmpty()) append("\nMobile: ").append(row.mobile.joinToString(", "))
        if (row.notes.isNotBlank()) append("\nNotes: ").append(row.notes)
    }

    private fun withFooter(text: String, book: NumberBook): String {
        val saved = listOfNotNull(book.source.revision?.let { "revision $it" }, book.source.retrieved.take(10).takeIf(String::isNotBlank)?.let { "saved $it" })
        return "$text\n\n_From the Wikipedia article \"${book.source.title}\" by ${book.source.publisher}" +
            (if (saved.isEmpty()) "" else " (${saved.joinToString(", ")})") + ", ${book.source.license}, kept on this phone. " +
            "Numbers can change. No signal? Try the call anyway: an emergency call can go through another carrier's network._"
    }

    /** One plain line for a first-aid answer that also asks for the number: the guide keeps citation [S1]. */
    fun line(question: String, book: NumberBook): String? {
        if (!asks.containsMatchIn(question) && !Regex("(?i)\\b(number|call|dial)\\b").containsMatchIn(question)) return null
        // One line names one country: two places asked about are left to the full answer.
        val row = countries(question, book).singleOrNull()?.let(book.countries::get) ?: return null
        val listed = listOf("police", "ambulance", "fire").mapNotNull { kind -> row.listed[kind]?.takeIf { v -> v.any(Char::isDigit) }?.let { "$kind **$it**" } }
        if (listed.isEmpty()) return null
        return "Emergency numbers saved for **${row.name}**: ${listed.joinToString(", ")}."
    }
}
