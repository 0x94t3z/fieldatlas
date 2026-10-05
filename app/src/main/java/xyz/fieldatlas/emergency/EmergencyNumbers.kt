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
        "(?i)\\b(?:emergency|police|ambulance|fire(?: brigade| department| service)?|rescue)\\s+" +
            "(?:phone |telephone |contact )?(?:numbers?|lines?|hotlines?)\\b|" +
            "\\bnumbers? (?:to|should i|do i|i should|i need to) (?:call|dial|know|ring)\\b|" +
            "\\bwhat (?:number|do i|should i) (?:do i |should i )?(?:call|dial|ring)\\b|" +
            "\\bnumbers? (?:for|of) (?:the )?(?:police|ambulance|fire|rescue|emergenc)",
    )
    // "What number should I call to book a taxi in Thailand?" is not an emergency.
    private val emergency = Regex("(?i)\\b(emergenc\\w*|police|ambulance|fire|rescue|sos|911|112|999|000)\\b")
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
    private val usMarker = Regex("(?i)\\b(usa|u\\.s\\.|united states|america|american|us state|atlanta|savannah|augusta, georgia)\\b")

    fun answer(question: String, book: NumberBook): Answer? {
        if (!asks.containsMatchIn(question) || !emergency.containsMatchIn(question)) return null
        val code = country(question, book)
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

    private fun country(question: String, book: NumberBook): String? {
        val text = " " + question.lowercase(Locale.ROOT).replace('’', '\'') + " "
        fun mentions(name: String) = Regex("(?<![\\p{L}])${Regex.escape(name)}(?![\\p{L}])").containsMatchIn(text)
        if (usStates.any(::mentions) || (mentions("georgia") && usMarker.containsMatchIn(question))) return "US".takeIf { it in book.countries }
        if (australianStates.any(::mentions)) return "AU".takeIf { it in book.countries }
        // The longest name wins: "South Africa" over "Africa", "Dominican Republic" over "Dominica".
        val named = book.countries.entries.map { (code, row) -> row.name.lowercase(Locale.ROOT) to code } + aliases.toList()
        return named.filter { (name, code) -> code in book.countries && mentions(name) }.maxByOrNull { it.first.length }?.second
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
        val row = country(question, book)?.let(book.countries::get) ?: return null
        val listed = listOf("police", "ambulance", "fire").mapNotNull { kind -> row.listed[kind]?.takeIf { v -> v.any(Char::isDigit) }?.let { "$kind **$it**" } }
        if (listed.isEmpty()) return null
        return "Emergency numbers saved for **${row.name}**: ${listed.joinToString(", ")}."
    }
}
