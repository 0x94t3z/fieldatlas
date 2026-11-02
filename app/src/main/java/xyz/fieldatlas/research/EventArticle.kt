package xyz.fieldatlas.research

/**
 * The article about one year's event that a question asks a figure of without naming it: "how many electoral votes did
 * Trump have in 2016" is answered by "2016 United States presidential election", whose title shares only the year
 * with the question. Keyword search found a Michigan 2024 article and a forecaster's page instead, and the 4B, reading
 * them, gave Trump 30 votes and Clinton the win (2 Nov). The article is found by its title (the year and the kind of
 * event) and by its lead naming the question's own words ("Trump"); the shortest such title is the national one, not
 * a state's or a district's.
 */
object EventArticle {
    data class Ask(val year: String, val kind: String, val terms: List<String>)

    private val yearPattern = Regex("(?<![\\p{N}.,/-])(1[6-9][0-9]{2}|20[0-9]{2})(?![\\p{N}])")

    /** The question's words for an event, and the word its articles carry in their titles. */
    private val kinds: List<Pair<Regex, String>> = listOf(
        Regex("(?i)\\b(?:elect\\w*|votes?|voted|voters?|ballots?|seats?|landslide|polls?)\\b") to "election",
        Regex("(?i)\\breferendum\\b") to "referendum",
        Regex("(?i)\\b(?:olympics?|olympic games|medals?)\\b") to "Olympics",
        Regex("(?i)\\bworld cup\\b") to "World Cup",
        Regex("(?i)\\bchampions league\\b") to "Champions League",
        Regex("(?i)\\bryder cup\\b") to "Ryder Cup",
        Regex("(?i)\\bwimbledon\\b") to "Wimbledon",
        Regex("(?i)\\beurovision\\b") to "Eurovision",
        Regex("(?i)\\bworld series\\b") to "World Series",
        Regex("(?i)\\bstanley cup\\b") to "Stanley Cup",
        Regex("(?i)\\bnba finals?\\b") to "NBA Finals",
        Regex("(?i)\\btour de france\\b") to "Tour de France",
        Regex("(?i)\\bgrand prix\\b") to "Grand Prix",
        Regex("(?i)\\bearthquake\\b") to "earthquake",
        Regex("(?i)\\bhurricane\\b") to "hurricane",
        Regex("(?i)\\bcensus\\b") to "census",
    )

    /** Words that ask, count or name the event itself, not what it is about. */
    private val asking = setOf("how", "many", "much", "did", "does", "do", "was", "were", "who", "what", "which", "when", "win", "won",
        "wins", "get", "got", "have", "had", "has", "take", "took", "total", "number", "year", "the", "and", "for", "final", "finals",
        "game", "games", "cup", "world", "series", "grand", "prix", "tour", "france")

    /** The event asked about, or null when the question names no single year or no kind of event. */
    fun of(question: String): Ask? {
        val years = yearPattern.findAll(question).map { it.groupValues[1] }.distinct().toList()
        val year = years.singleOrNull() ?: return null
        val (pattern, kind) = kinds.firstOrNull { (pattern, _) -> pattern.containsMatchIn(question) } ?: return null
        // The event's own name is no word of the question's: "who won the 1999 champions league final" names nothing else,
        // and the pack, lacking that final, offered the 1999 African Champions League final in its place (2 Nov).
        val named = kind.lowercase().split(' ').toSet()
        val terms = FtsQuery.from(question)?.terms.orEmpty().filter { term ->
            term != year && term.length >= 3 && term !in asking && term !in named && !pattern.matches(term) && !term.all(Char::isDigit)
        }
        if (terms.isEmpty()) return null
        return Ask(year, kind, terms)
    }

    /**
     * [item] is that year's article of that kind: its title names the year and the kind as a whole word, so "2016 Alabama
     * elections", a list of a state's contests, is not "the 2016 election".
     */
    fun covers(item: Evidence, ask: Ask): Boolean {
        val title = item.title.substringBefore(" — ")
        return Regex("(?<![\\p{N}])${ask.year}(?![\\p{N}])").containsMatchIn(title) &&
            Regex("(?i)(?<![\\p{L}])${Regex.escape(ask.kind)}(?![\\p{L}])").containsMatchIn(title)
    }

    /**
     * The lead of an article on that year's event whose text names every one of the question's own words. A part of
     * another candidate ("… presidential election in Utah" beside "… presidential election") gives way to it; of the
     * rest, the one naming the words most often: the national election's lead says "Trump" six times, a state senate
     * race's once. A tie goes to the shorter title.
     */
    fun pick(found: List<Evidence>, ask: Ask): Evidence? {
        val fitting = found.filter { item ->
            item.title.endsWith(" — Overview") && covers(item, ask) &&
                ask.terms.all { term -> item.text.contains(term, ignoreCase = true) }
        }
        val titles = fitting.map { it.title.substringBefore(" — ") }.toSet()
        return fitting.filterNot { item ->
            val title = item.title.substringBefore(" — ")
            titles.any { other -> other != title && title.startsWith("$other ") }
        }.sortedWith(compareBy({ item -> -ask.terms.sumOf { term -> Regex("(?i)" + Regex.escape(term)).findAll(item.text).count() } },
            { it.title.length })).firstOrNull()
    }
}
