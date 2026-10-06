package xyz.fieldatlas.research

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Exact arithmetic for the calculations travellers ask about: fuel economy and range, what a trip's fuel
 * costs and each person's share, hiking time by Naismith's rule, walking or driving time against the time
 * left, a bill at a given exchange rate with a tip, money changed at a quoted rate less the commission,
 * water for a group over the days, a power bank's watt-hours, temperatures, the time in another city,
 * when a flight lands in another time zone, a recipe for more people, where water boils at altitude and
 * common unit conversions. A small model gets these wrong (7.5 L/100 km once came out as 0.61 mpg), so
 * the app works them out and shows the working.
 */
object Calculators {
    /** [complete]: the calculation is the whole answer. Otherwise the numbers lead and research continues. */
    /** [guideId]: the bundled guide the rest of the question must be answered from (extreme heat or cold). */
    data class Result(
        val text: String, val complete: Boolean, val facts: String, val followUp: String = "",
        val guideId: String? = null,
        /** Written by the app for an extreme temperature, shown above the official guide. */
        val riskNote: String? = null,
        /** Where in the question the numbers this calculation read sit; a number outside them was left out. */
        val used: List<IntRange> = emptyList(),
        /** A value the question did not give was assumed and said so: the numbers lead, research answers. */
        val partial: Boolean = false,
    )

    private const val NUM = "(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    private fun num(text: String) = text.replace(",", "").toDouble()
    private const val SYMBOLS = "[$€£¥₹]"
    private const val PCT = "(?:%|per\\s?cent\\b)"
    // Every number in the question, thousands separators and decimals included; "6-bed" is not one.
    private val numberToken = Regex("(?<![\\d.,])\\d+(?:[.,]\\d+)*(?![\\d-]|[.,]\\d)")

    // Anything beyond the arithmetic ("is it dangerous", "should I") is left to research.
    private val judgement = Regex(
        "(?i)\\b(dangerous|danger|safe|safely|risky|risk|ok(?:ay)?|should|recommend|better|worse|why|explain|compare|" +
            "healthy|enough|too (?:hot|cold|much|far|long)|worth|legal|allowed|can i (?:bring|take|carry|pack|use))\\b",
    )

    fun solve(question: String, today: LocalDate = LocalDate.now()): Result? {
        val families = listOfNotNull(
            fuel(question)?.let { it to FUEL }, fuelRange(question)?.let { it to FUEL + RANGE }, tripFuel(question)?.let { it to FUEL + TRIP },
            naismith(question)?.let { it to HIKE }, travelTime(question)?.let { it to TRAVEL },
            (currency(question) ?: exchange(question))?.let { it to MONEY + EXCHANGE }, water(question)?.let { it to WATER },
            battery(question)?.let { it to BATTERY },
            temperature(question)?.let { it to TEMPERATURE }, clock(question, today)?.let { it to CLOCK }, arrival(question)?.let { it to ARRIVAL },
            recipe(question)?.let { it to RECIPE }, boiling(question)?.let { it to BOILING }, duration(question)?.let { it to DURATION },
            shift(question)?.let { it to SHIFT }, perUnit(question)?.let { it to PER_UNIT },
            units(question)?.let { it to UNITS }, percent(question)?.let { it to PERCENT })
        if (families.isEmpty()) return null
        val parts = families.map { it.first }
        val covered = parts.flatMap { it.second }.toSet()
        val leftover = judgement.findAll(question).map { it.value.lowercase() }.filterNot { it in covered }.toList()
        // A number no calculation read ("until the end of day 2" when the water sum took one day) means the
        // sum is not the whole answer.
        val used = parts.flatMap { it.first.used }
        val stray = numberToken.findAll(question).any { d -> used.none { d.range.first in it } }
        // Words the calculation does not account for mean something else is asked too: "the best time
        // to hike the 43 km Inca Trail", "my baby has a fever of 39°C". Then the numbers lead and
        // research answers the question; only a question that is all arithmetic is answered here.
        // The words are judged in the clauses that ask: "Road trip is 840 km" sets the scene for "how much
        // does each person pay?", and its "road" asks nothing. The asking clause must also name what was
        // worked out ("in euros", "what does it weigh", a number used), so "My baby has a fever of 39°C.
        // What do I do?" is not answered with a conversion.
        val familyWords = families.flatMap { it.second }.toSet()
        val answerWords = words(parts.joinToString(" ") { it.first.text }).toSet()
        val known = GENERIC + familyWords + answerWords + money.findAll(question).map { it.value.lowercase() }
        val asking = askingClauses(question)
        val unasked = asking.flatMap { c -> words(c.value).filterNot { it in known || judgement.matches(it) } }
        val names = (familyWords + answerWords) - GENERIC
        val named = asking.any { c ->
            words(c.value).any { it in names } || refersBack.containsMatchIn(c.value) || quantityAsk.containsMatchIn(c.value) ||
                numberToken.findAll(c.value).any { d -> used.any { (c.range.first + d.range.first) in it } }
        }
        val complete = leftover.isEmpty() && unasked.isEmpty() && !stray && named && parts.none { it.first.partial }
        val text = parts.joinToString("\n\n") { it.first.text }
        val facts = parts.joinToString(" ") { it.first.facts }
        // The question research continues with: values stated in plain words where a calculator
        // has them ("104°F (40°C, very hot)"), so retrieval and guide matching see what they mean.
        val followUp = parts.mapNotNull { it.first.followUp.takeIf(String::isNotBlank) }.firstOrNull()
            ?: "$question ($facts)"
        // "Is 40°C dangerous for a long walk?" is a safety question: answer it from the official guide,
        // never from the model's guess (it once called 40°C "generally manageable").
        val safety = if (complete) null else parts.firstOrNull { it.first.guideId != null }?.first
        return Result(text, complete = complete, facts = facts, followUp = followUp,
            guideId = safety?.guideId, riskNote = safety?.riskNote)
    }

    private fun words(text: String): List<String> = Regex("[\\p{L}]+(?:'[\\p{L}]+)?").findAll(text.lowercase(Locale.ROOT))
        .map { it.value.removeSuffix("'s").removeSuffix("'t") }.filter { it.length >= 2 }.toList()
    // A country's name before its money ("Thai baht", "Polish zloty") belongs to the amount.
    private val money = Regex("(?i)\\b[a-z]+(?=\\s+(?:$CURRENCIES)s?\\b)")

    // A clause asks when it ends in "?" or opens with a question word or a request ("Convert 10 kg to
    // pounds"); "How much for 9 nights" in the middle of a run-on question counts too. A question with
    // no such clause asks as a whole.
    // A full stop between digits ("6.5 litres") does not end a clause.
    private val clauses = Regex("(?:[^.?!;]|(?<=\\d)\\.(?=\\d))+[.?!;]*")
    private val asks = Regex("(?i)^\\W*(?:(?:and|so|then|but|now|also|ok|okay)\\s+)?(?:what|how|when|where|which|who|why|is|are|am|was|were|can|could|should|" +
        "do|does|did|will|would|shall|may|might|convert|calculate|work out|tell|give|show|please|roughly|about)\\b|\\?|" +
        "\\bhow (?:much|many|long|far|heavy|fast|late|early)\\b|\\bwhat(?:'s| is| are| time| does| do| will| would)\\b|" +
        "\\bwhen (?:do|does|did|will|would|must|should|can|could|is|are|to)\\b|\\bconvert\\w*\\b|\\bin (?:total|all)\\b")
    private fun askingClauses(q: String): List<MatchResult> {
        val all = clauses.findAll(q).toList()
        return all.filter { asks.containsMatchIn(it.value) }.ifEmpty { all }
    }
    // "What is that?" after a value asks about the value, and "how much each?" asks for the amount worked out.
    private val quantityAsk = Regex("(?i)\\bhow (?:much|many|long|far|heavy|late|early)\\b|\\bwhat time\\b|\\bwhen\\b|\\b(?:in )?total\\b|\\bin all\\b|\\baltogether\\b")
    private val refersBack = Regex("(?i)\\b(?:what(?:'s| is| are| about)|how (?:much|many|long) (?:is|are|does|do|would|will))\\s+(?:that|this|it|they|those|these)\\b")

    // The words a calculation question is made of, beyond the numbers, units and names in its answer.
    private val GENERIC = setOf("what", "how", "many", "much", "long", "far", "is", "are", "was", "that", "this", "it", "its", "in",
        "into", "to", "convert", "converted", "calculate", "work", "out", "about", "roughly", "approximately", "around",
        "need", "needs", "have", "has", "had", "get", "gets", "got", "will", "would", "take", "takes", "does", "do", "did", "can",
        "could", "total", "each", "per", "and", "or", "with", "for", "from", "by", "the", "an", "my", "our", "we", "us", "me",
        "you", "your", "there", "be", "if", "at", "on", "of", "so", "then", "please", "tell", "show", "give", "number",
        "amount", "equivalent", "exactly", "round", "rounded", "all", "both", "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve", "whole", "just", "only", "using", "use", "uses", "used", "rule",
        "value", "values", "result", "answer", "quick", "quickly", "im", "it's", "i'm", "i've", "we're", "they", "their", "he", "she",
        // Negations lose their "'t" when the words are read: "won't", "don't", "isn't".
        "not", "no", "won", "don", "doesn", "didn", "isn", "aren", "wasn", "weren", "haven", "hasn", "wouldn", "shouldn", "couldn")
    private val FUEL = setOf("car", "rental", "vehicle", "camper", "campervan", "motorhome", "truck", "van", "motorbike", "consumption", "economy", "efficiency",
        "fuel", "petrol", "gas", "gasoline", "diesel", "mileage", "liters", "litres", "liter", "litre", "km", "kilometers",
        "kilometres", "miles", "mile", "gallon", "gallons", "us", "uk", "imperial", "mpg")
    private val RANGE = setOf("tank", "holds", "full", "drive", "driving", "trip", "journey", "range", "reach", "go", "travel", "cover", "refuel")
    private val HIKE = setOf("hike", "hiking", "walk", "walking", "trek", "trekking", "trail", "route", "climb", "climbing", "ascent",
        "gain", "elevation", "naismith", "naismith's", "km", "kilometres", "kilometers", "metres", "meters", "ft", "feet", "miles",
        "mile", "hours", "time", "of")
    private val TRAVEL = setOf("walk", "walking", "drive", "driving", "cycle", "cycling", "ride", "riding", "run", "running", "hike",
        "hiking", "speed", "average", "km", "kilometres", "kilometers", "miles", "mile", "mph", "hours", "hour", "daylight", "left",
        "dark", "before", "sunset", "nightfall", "make", "reach", "arrive", "time", "light")
    private val MONEY = setOf("bill", "dinner", "lunch", "breakfast", "meal", "taxi", "fare", "price", "cost", "costs", "ticket",
        "tickets", "sandwich", "coffee", "beer", "hotel", "room", "pay", "paid", "tip", "service", "charge", "rate", "exchange",
        "worth", "buys", "equals", "dollars", "dollar", "euros", "euro", "money")
    private val WATER = setOf("water", "liters", "litres", "liter", "litre", "person", "people", "day", "days", "hiking", "hike",
        "trip", "carry", "carrying", "weigh", "weighs", "weight", "drink", "drinks", "drinking", "camping", "camp", "trekking", "trek", "group", "family",
        "hikers", "adults", "travellers", "travelers", "us", "nights", "night", "hours", "week", "weeks", "overnight", "until", "till", "end", "start",
        "reach", "any", "next", "refill", "source", "stream", "river", "tap", "hut", "village", "along", "way", "route", "trail",
        "pack", "bring", "between", "kg", "kilos", "kilograms", "pounds", "lb", "lbs", "heavy", "second", "third", "fourth", "fifth",
        "everyone", "each", "every", "friends", "team", "party")
    private val BATTERY = setOf("power", "bank", "powerbank", "battery", "carry", "mah", "volts", "wh", "watt", "hours", "watt-hours",
        "plane", "flight", "fly", "flying", "airline", "carry-on", "cabin", "luggage", "bag", "it", "allowed")
    private val TEMPERATURE = setOf("outside", "outdoors", "today", "weather", "temperature", "degrees", "celsius", "fahrenheit",
        "centigrade")
    private val CLOCK = setOf("time", "when", "now", "am", "pm", "there", "here", "o'clock", "difference")
    private val RECIPE = setOf("recipe", "people", "persons", "servings", "portions", "guests", "serves", "feeds", "makes", "cups",
        "cup", "grams", "tablespoons", "teaspoons", "ounces", "pounds", "ml", "kg", "scale", "instead")
    private val BOILING = setOf("water", "boil", "boils", "boiling", "point", "temperature", "altitude", "elevation", "metres",
        "meters", "ft", "feet", "above", "sea", "level", "high", "matter", "cooking", "cook", "pasta", "rice", "food")
    private val UNITS = setOf("km", "kms", "kilometers", "kilometres", "miles", "mile", "metres", "meters", "feet", "foot", "ft",
        "inches", "inch", "cm", "centimetres", "centimeters", "kg", "kilos", "kilograms", "pounds", "lb", "lbs", "grams", "ounces",
        "oz", "liters", "litres", "gallons", "gallon", "mph", "kph", "tall", "height", "distance", "weight")
    private val DURATION = setOf("sunrise", "sunset", "daylight", "between", "from", "until", "till", "hours", "hour", "minutes",
        "time", "duration", "opens", "closes", "starts", "ends", "leaves", "arrives", "departs", "lands", "flight", "shift", "am", "pm")
    private val PERCENT = setOf("percent", "tip", "service", "charge", "discount", "off")
    private val SHIFT = setOf("when", "time", "must", "should", "latest", "leave", "leaves", "leaving", "depart", "departs",
        "departure", "departing", "arrive", "arrives", "arrival", "arriving", "land", "lands", "landing", "flight", "train", "bus",
        "ferry", "plane", "boarding", "board", "boards", "closes", "close", "gate", "check", "starts", "start", "begins", "begin",
        "finish", "finishes", "end", "ends", "before", "after", "later", "earlier", "trip", "journey", "ride", "drive", "walk",
        "hike", "lasts", "minutes", "minute", "mins", "hours", "hour", "hrs", "reach", "there", "done", "meet", "meeting", "tour",
        "am", "pm", "set", "off", "back", "return", "ready", "doors", "open", "opens")
    private val PER_UNIT = setOf("night", "nights", "day", "days", "person", "people", "head", "ticket", "tickets", "hour", "hours",
        "week", "weeks", "room", "rooms", "costs", "cost", "price", "pay", "discount", "off", "tax", "vat", "service", "charge",
        "surcharge", "fee", "commission", "hostel", "hotel", "guesthouse", "dorm", "bed", "apartment", "rental", "car", "bike",
        "scooter", "tour", "class", "lesson", "stay", "staying", "book", "booking", "euros", "euro", "dollars", "dollar", "plus",
        "minus", "applied", "spend", "spent", "altogether", "overall", "it", "us", "of", "percent", "cent")
    private val TRIP = setOf("road", "trip", "drive", "driving", "journey", "route", "way", "there", "road-trip", "roadtrip", "car",
        "rental", "hire", "rented", "hired", "van", "camper", "petrol", "fuel", "diesel", "gas", "gasoline", "pump", "price", "costs",
        "cost", "pay", "pays", "paying", "paid", "owe", "owes", "split", "splitting", "share", "shared", "sharing", "divide", "divided",
        "between", "among", "amongst", "ways", "person", "people", "head", "each", "apiece", "friends", "passengers", "us", "litres",
        "liters", "litre", "liter", "gallon", "gallons", "km", "kilometres", "kilometers", "miles", "mile", "mpg", "uses", "burns",
        "averages", "does", "gets", "returns", "consumption", "economy", "efficiency", "distance", "far", "whole", "entire", "total", "bill",
        "euros", "euro", "dollars", "dollar", "pounds", "pound", "money", "cheap", "expensive", "budget", "if", "when", "we", "much", "many", "need")
    private val EXCHANGE = setOf("exchange", "office", "bureau", "kiosk", "booth", "counter", "bank", "atm", "airport", "money",
        "changer", "changers", "cash", "notes", "change", "changing", "changed", "converting", "convert", "swap", "swapping", "sell",
        "selling", "buy", "buying", "rate", "rates", "gives", "give", "offers", "offer", "offering", "quotes", "quoted", "quote", "pays",
        "paying", "takes", "take", "charges", "charge", "charging", "keeps", "deducts", "fee", "fees", "commission", "spread", "markup",
        "flat", "fixed", "left", "over", "leftover", "remaining", "spare", "back", "home", "hand", "handing", "receive", "walk", "away",
        "end", "pocket", "worth", "local", "currency", "dollars", "dollar", "euros", "euro", "pounds", "pound", "per", "one", "against",
        "before", "after", "flying", "trip", "cost", "costs", "after", "net", "minus", "plus", "less", "percent", "cent", "got", "get",
        "much", "many", "how", "if", "my", "i")
    private val ARRIVAL = setOf("flight", "flights", "fly", "flying", "flies", "plane", "airline", "leaves", "leave", "leaving", "departs",
        "depart", "departure", "departing", "takes", "take", "took", "taking", "lasts", "lasting", "long", "local", "time", "times",
        "utc", "gmt", "zone", "zones", "timezone", "timezones", "hours", "hour", "hrs", "hr", "min", "mins", "minutes", "minute",
        "ahead", "behind", "later", "earlier", "difference", "land", "lands", "landing", "touch", "down", "arrive", "arrives",
        "arriving", "arrival", "reach", "get", "in", "when", "right", "now", "currently", "there", "home", "here", "clock", "airport",
        "train", "bus", "ferry", "journey", "trip", "overnight", "nonstop", "non-stop", "direct", "duration", "offset", "on", "to",
        "from", "what", "tomorrow", "today", "tonight", "am", "pm", "boarding", "off", "takeoff", "take-off", "destination", "be",
        "it", "my", "we", "our", "i", "is", "at", "day", "night", "red-eye", "redeye", "according", "schedule", "scheduled", "which", "that", "about", "roughly")

    private fun fmt(value: Double, decimals: Int = 1): String {
        val rounded = if (decimals == 0) value.roundToLong().toDouble() else value
        return String.format(Locale.US, "%,.${decimals}f", rounded).let {
            if (decimals > 0) it.trimEnd('0').trimEnd('.') else it
        }
    }
    private fun money(value: Double) = String.format(Locale.US, "%,.2f", value)
    private fun hours(totalHours: Double): String {
        val minutes = (totalHours * 60).roundToInt()
        val h = minutes / 60; val m = minutes % 60
        return if (h == 0) "$m min" else if (m == 0) "$h h" else "$h h $m min"
    }

    /* ---------- fuel economy ---------- */
    private val per100 = Regex("(?i)$NUM\\s*(?:l|liters?|litres?)\\s*(?:per|/)\\s*100\\s*(?:km|kilometers?|kilometres?)")
    private val mpg = Regex("(?i)$NUM\\s*(?:mpg|miles per (?:us |u\\.s\\. |imperial |uk )?gallon)")
    private fun fuel(q: String): Pair<Result, Set<String>>? {
        val wantsMpg = Regex("(?i)\\b(mpg|miles per (?:us |u\\.s\\. |imperial |uk )?gallon)\\b").containsMatchIn(q)
        val wantsPer100 = Regex("(?i)(?:l|liters?|litres?)\\s*(?:per|/)\\s*100").find(q)
        per100.find(q)?.takeIf { wantsMpg }?.let { m ->
            val l = num(m.groupValues[1]); if (l <= 0) return null
            val us = 235.215 / l; val uk = 282.481 / l
            return Result("${fmt(l)} L/100 km is about **${fmt(us)} miles per US gallon** (${fmt(uk)} mpg in UK imperial gallons).\n\n" +
                "Working: 235.215 ÷ ${fmt(l)} = ${fmt(us, 2)}, since 100 km is 62.137 miles and a US gallon is 3.785 litres.",
                true, "${fmt(l)} L/100 km = ${fmt(us)} mpg (US).", used = listOf(m.range)) to emptySet()
        }
        mpg.find(q)?.takeIf { wantsPer100 != null }?.let { m ->
            val v = num(m.groupValues[1]); if (v <= 0) return null
            val imperial = Regex("(?i)imperial|uk|british").containsMatchIn(m.value)
            val l = (if (imperial) 282.481 else 235.215) / v
            return Result("${fmt(v)} mpg (${if (imperial) "UK imperial" else "US"} gallons) is about **${fmt(l)} L/100 km**.\n\n" +
                "Working: ${if (imperial) "282.481" else "235.215"} ÷ ${fmt(v)} = ${fmt(l, 2)}.", true, "${fmt(v)} mpg = ${fmt(l)} L/100 km.",
                used = listOfNotNull(m.range, wantsPer100?.range)) to emptySet()
        }
        return null
    }

    /* ---------- hiking time ---------- */
    private val distance = Regex("(?i)$NUM\\s*(km|kilometers?|kilometres?|mi|miles?)\\b")
    // Height climbed, not height above the sea: "2,400 m elevation" is where the lake is.
    private val ascent = Regex("(?i)$NUM\\s*(m|meters?|metres?|ft|feet)\\b(?:\\s+of)?(?:\\s+(?:total|of))?\\s*(?:climb(?:ing)?|ascent|elevation gain|gain|uphill|vertical)")
    private val ascentAfter = Regex("(?i)(?:climb(?:ing)?|ascent|elevation gain|gain)\\s+(?:of\\s+)?$NUM\\s*(m|meters?|metres?|ft|feet)\\b")
    private fun naismith(q: String): Pair<Result, Set<String>>? {
        val hiking = Regex("(?i)\\b(naismith|hike|hiking|walk|walking|trek|trail)\\b").containsMatchIn(q) &&
            Regex("(?i)\\bhow long|how many hours|how much time|naismith|time (?:will|would|does|do) (?:it|i|we) (?:take|need)").containsMatchIn(q)
        // "I walk at 5 km/h": a stated speed is used as given (travelTime), not Naismith's 5 km/h.
        if (!hiking || speed.containsMatchIn(q)) return null
        val d = distanceOnly.find(q) ?: return null
        val km = num(d.groupValues[1]) * (if (d.groupValues[2].lowercase().startsWith("mi")) 1.609344 else 1.0)
        val up = ascent.find(q) ?: ascentAfter.find(q)
        val metres = up?.let { num(it.groupValues[1]) * (if (it.groupValues[2].lowercase().startsWith("f")) 0.3048 else 1.0) } ?: 0.0
        val walk = km / 5.0; val climb = metres / 600.0
        val text = buildString {
            append("About **${hours(walk + climb)}** of moving time by Naismith's rule (1 hour per 5 km, plus 1 hour per 600 m of ascent).\n\n")
            append("Working: ${fmt(km)} km ÷ 5 km/h = ${hours(walk)}")
            if (metres > 0) append(", plus ${fmt(metres, 0)} m ÷ 600 m/h = ${hours(climb)}")
            append(".\n\nAdd time for breaks. The rule assumes a reasonably fit walker on good paths; steep descents, rough ground, heavy packs and heat all slow you down.")
        }
        return Result(text, true, "Naismith time = ${hours(walk + climb)}.", used = listOfNotNull(d.range, up?.range)) to emptySet()
    }

    /* ---------- a bill at a given exchange rate ---------- */
    private val rate = Regex(
        "(?i)\\b(?:1|one)\\s+((?:us |u\\.s\\. )?[a-z]+?)s?\\s+(?:is|=|equals|buys|is worth|gets(?: you)?|costs)\\s+(?:about\\s+)?$NUM\\s*((?:[a-z]+\\s+)?[a-z]+)",
    )
    private const val CURRENCIES = "dollar|usd|euro|eur|pound|gbp|yen|jpy|baht|thb|peso|rupee|rupiah|idr|ringgit|myr|dong|vnd|won|krw|yuan|rmb|cny|" +
        "franc|chf|krona|kronor|krone|kroner|zloty|forint|lira|lire|real|reai|rand|shekel|dirham|riyal|rial|lari|sol|sole|" +
        "colon|colone|dinar|koruna|leu|lei|lev|hryvnia|ruble|rouble|taka|kip|riel|kyat|tugrik|tenge|som|sum|manat|dram|" +
        "birr|shilling|naira|cedi|kwacha|quetzal|lempira|cordoba|boliviano|guarani|sgd|aud|cad|nzd|hkd|twd|php"
    private val currencyWord = Regex(
        "dollar|usd|euro|eur|pound|gbp|yen|jpy|baht|thb|peso|rupee|rupiah|idr|ringgit|myr|dong|vnd|won|krw|yuan|rmb|cny|" +
            "franc|chf|krona|kronor|krone|kroner|zloty|forint|lira|lire|real|reai|rand|shekel|dirham|riyal|rial|lari|sol|sole|" +
            "colon|colone|dinar|koruna|leu|lei|lev|hryvnia|ruble|rouble|taka|kip|riel|kyat|tugrik|tenge|som|sum|manat|dram|" +
            "birr|shilling|naira|cedi|kwacha|quetzal|lempira|cordoba|boliviano|guarani|nuevo sol|sgd|aud|cad|nzd|hkd|twd|php",
    )
    private val tip = Regex("(?i)$NUM\\s*%\\s*(?:tip|service(?: charge)?|gratuity)|(?:tip|service charge|gratuity)\\s+(?:of\\s+)?$NUM\\s*%")
    private fun currency(q: String): Pair<Result, Set<String>>? {
        val r = rate.find(q) ?: return null
        // A commission or fee changes the answer, and "17.100 rupiah" is not 17.1: exchange() reads both.
        if (feeWord.containsMatchIn(q) || (pctFee.containsMatchIn(q) && !tipWords.containsMatchIn(q)) || dotted.containsMatchIn(q)) return null
        val base = r.groupValues[1].trim(); val perBase = num(r.groupValues[2])
        // "4.3 Polish zloty" names the money last; in "36.5 baht and" the word after it is not money.
        val words = r.groupValues[3].split(Regex("\\s+"))
        val (local, localName) = when {
            currencyWord.matches(words.last().lowercase().trimEnd('s')) -> words.last().lowercase().trimEnd('s') to r.groupValues[3]
            currencyWord.matches(words.first().lowercase().trimEnd('s')) -> words.first().lowercase().trimEnd('s') to words.first()
            else -> return null
        }
        // "One day is 24 hours" is not an exchange rate: both sides must name money.
        if (perBase <= 0 || !currencyWord.matches(base.lowercase().substringAfterLast(' ')) || !currencyWord.matches(local)) return null
        val amount = Regex("(?i)$NUM\\s*(?:[a-z]+\\s+)?${Regex.escape(local)}").findAll(q)
            .firstOrNull { it.range.last < r.range.first || it.range.first > r.range.last }
        if (amount == null) {
            // "I have 150 euros and 1 euro is 4.3 zloty": an amount in the first currency converts forward.
            val held = Regex("(?i)$NUM\\s*${Regex.escape(base.lowercase().substringAfterLast(' '))}s?\\b").findAll(q)
                .firstOrNull { it.range.first !in r.range } ?: return null
            val have = num(held.groupValues[1]); val out = have * perBase
            return Result("${fmt(have, 2)} ${base}s × ${fmt(perBase, 4)} = **${money(out)} $localName**.\n\n" +
                "This uses the rate you gave; exchange offices and card fees give you somewhat less.",
                true, "${fmt(have, 2)} ${base}s = ${money(out)} $localName.", used = listOf(r.range, held.range)) to emptySet()
        }
        val localAmount = num(amount.groupValues[1])
        val converted = localAmount / perBase
        val baseName = if (base.lowercase().endsWith("dollar")) "${base.removeSuffix("dollar").trim().uppercase().ifBlank { "" }} dollars".trim()
            else base + "s"
        val tipped = tip.find(q)
        val t = tipped?.let { num(it.groupValues[1].ifBlank { it.groupValues[2] }) }
        val text = buildString {
            append("${fmt(localAmount, 2)} $localName ÷ ${fmt(perBase, 4)} = **${money(converted)} $baseName**.")
            if (t != null) {
                val total = converted * (1 + t / 100)
                append("\n\nWith a ${fmt(t)}% tip: ${money(converted)} × ${fmt(1 + t / 100, 2)} = **${money(total)} $baseName** ")
                append("(${fmt(localAmount * (1 + t / 100), 2)} $localName).")
            }
            append("\n\nThis uses the rate you gave; card and exchange fees are extra.")
        }
        return Result(text, true, "${fmt(localAmount, 2)} $localName = ${money(converted)} $baseName.",
            used = listOfNotNull(r.range, amount.range, tipped?.range)) to emptySet()
    }

    /* ---------- changing money at a quoted rate, less the commission ---------- */
    // "25,400 dong per US dollar", "0.84 pounds per euro", "38 lira to the euro": so much local money for one of the base.
    private const val LOOSE = "(\\d(?:[\\d.,]*\\d)?)"
    private val ratePer = Regex("(?i)(?<![\\d.,])$LOOSE\\s*((?:[a-z]+\\s+)?[a-z]+?)s?\\s+(?:(?:per|a|each)\\s+(?:(?:one|1)\\s+)?|(?:to|for|against)\\s+(?:the|one|1|a|an)\\s+)" +
        "((?:us |u\\.s\\. |uk |australian |canadian |singapore |new zealand |hong kong |taiwan |swiss |swedish |norwegian |danish |turkish |mexican |philippine )?[a-z]+)\\b")
    // "1 euro is 38 lira", "1 USD = 25,400 VND", "one pound buys 1.25 dollars".
    private val rateIs = Regex("(?i)\\b(?:1|one)\\s+((?:us |u\\.s\\. |uk )?[a-z]+?)s?\\s*(?:is|=|equals|buys|is worth|gets(?: you)?|will get(?: you)?|costs|fetches)\\s+" +
        "(?:about\\s+|around\\s+|roughly\\s+)?$LOOSE\\s*((?:[a-z]+\\s+)?[a-z]+)")
    private val feeWord = Regex("(?i)\\b(?:commissions?|fees?|spread|markup|mark-up|surcharges?)\\b")
    private val tipWords = Regex("(?i)\\b(?:tips?|gratuity|service charge)\\b")
    private val dotted = Regex("(?<![\\d.,])\\d{1,3}(?:\\.\\d{3})+(?![\\d.])")
    // The fee the office keeps: "3% commission", "a fee of 2%", "charges 4%", "takes 3 percent".
    private val pctFee = Regex("(?i)$NUM\\s*$PCT\\s*(?:commission|fee|charge|spread|markup|mark-up|cut|surcharge)|" +
        "(?:commission|fee|charge|spread|markup|mark-up|cut|surcharge)\\s+(?:of\\s+)?$NUM\\s*$PCT|" +
        "(?:takes?|taking|charges?|charging|keeps?|keeping|deducts?|skims?)\\s+(?:a\\s+|an\\s+|about\\s+)?$NUM\\s*$PCT" +
        "(?:\\s+(?:commission|fee|charge|spread|markup|mark-up|cut|surcharge))?")
    // A flat fee in money: "a £3 fee", "fee of 5 euros", "charges 50,000 dong", "plus a flat 2 euro commission".
    private const val AMOUNT = "(?:($SYMBOLS)\\s*$LOOSE|$LOOSE\\s*(?:($SYMBOLS)|((?:[a-z]+\\s+)?(?:$CURRENCIES))s?\\b))"
    private val flatFee = Regex("(?i)(?:commission|fee|charge|surcharge)\\s+of\\s+(?:a\\s+)?$AMOUNT|" +
        "(?:charges?|takes?|deducts?|keeps?|plus|minus|less)\\s+(?:a\\s+|an\\s+)?(?:flat\\s+|fixed\\s+)?$AMOUNT\\s*(?:flat\\s+|fixed\\s+)?(?:fee|commission|charge|surcharge)?|" +
        "$AMOUNT\\s+(?:flat\\s+|fixed\\s+)?(?:fee|commission|charge|surcharge)\\b")
    private val noFee = Regex("(?i)\\b(?:no|without|zero|free of)\\s+(?:any\\s+)?(?:fees?|commissions?|charges?)|\\b(?:fee|commission)-free\\b")
    // ISO codes and plurals that name the same money as the word, so "US dollars" matches a rate in USD.
    private val currencyCodes = mapOf("usd" to "dollar", "sgd" to "dollar", "aud" to "dollar", "cad" to "dollar", "nzd" to "dollar", "hkd" to "dollar",
        "twd" to "dollar", "eur" to "euro", "gbp" to "pound", "egp" to "pound", "jpy" to "yen", "thb" to "baht", "idr" to "rupiah", "myr" to "ringgit",
        "vnd" to "dong", "krw" to "won", "cny" to "yuan", "rmb" to "yuan", "chf" to "franc", "inr" to "rupee", "lkr" to "rupee", "npr" to "rupee",
        "pkr" to "rupee", "pln" to "zloty", "huf" to "forint", "czk" to "koruna", "php" to "peso", "mxn" to "peso", "brl" to "real", "zar" to "rand",
        "sek" to "krona", "nok" to "krone", "dkk" to "krone", "ils" to "shekel", "aed" to "dirham", "mad" to "dirham", "kronor" to "krona",
        "kroner" to "krone", "lire" to "lira", "reai" to "real", "lei" to "leu", "sole" to "sol", "colone" to "colon", "rouble" to "ruble")
    private fun canon(word: String): String { val w = word.lowercase().substringAfterLast(' ').trimEnd('s'); return currencyCodes[w] ?: w }
    private fun isCurrency(word: String) = currencyWord.matches(word.lowercase().substringAfterLast(' ').trimEnd('s'))
    // Money written with dots between the thousands: "25.400 dong", "2.000.000 rupiah".
    private val dotGrouped = setOf("dong", "rupiah", "euro", "real", "peso", "lira", "krone", "leu", "dinar", "colon", "guarani", "boliviano")
    /**
     * Reads a money figure as written: "2,000,000", "25,400.50", "1,89" (a decimal comma), "2.000.000". A single
     * dot before exactly three digits is a thousands separator only for money that is written that way; for
     * anything else ("1.500 dollars") the reading is uncertain and nothing is returned.
     */
    private fun parseAmount(text: String, currency: String): Double? {
        val dots = text.count { it == '.' }; val commas = text.count { it == ',' }
        if (dots == 0 && commas == 0) return text.toDouble()
        val groupedBy = { sep: Char -> text.split(sep).drop(1).all { it.length == 3 } }
        return when {
            dots > 0 && commas > 0 -> {
                val decimal = if (text.lastIndexOf('.') > text.lastIndexOf(',')) '.' else ','
                val grouping = if (decimal == '.') ',' else '.'
                val whole = text.substringBeforeLast(decimal); val frac = text.substringAfterLast(decimal)
                if (frac.length > 2 || frac.contains(grouping) || !whole.split(grouping).drop(1).all { it.length == 3 }) null
                else (whole.replace(grouping.toString(), "") + "." + frac).toDouble()
            }
            commas > 0 -> when {
                groupedBy(',') -> text.replace(",", "").toDouble()
                commas == 1 && text.substringAfter(',').length <= 2 -> text.replace(',', '.').toDouble()
                else -> null
            }
            groupedBy('.') && (dots > 1 || canon(currency) in dotGrouped) -> text.replace(".", "").toDouble()
            dots == 1 && !groupedBy('.') -> text.toDouble()
            else -> null
        }
    }
    private fun plural(name: String): String {
        val last = name.substringAfterLast(' ')
        val invariant = setOf("lira", "dong", "baht", "yen", "rupiah", "won", "yuan", "zloty", "rand", "ringgit", "kip", "riel", "lari", "som", "sum",
            "kyat", "birr", "lei", "leu", "lev", "renminbi", "forint")
        val plural = when {
            last.length == 3 && last.uppercase() == last -> last
            last.lowercase() in invariant || last.endsWith("s") -> last
            last.lowercase() == "krona" -> "kronor"; last.lowercase() == "krone" -> "kroner"; last.lowercase() == "real" -> "reais"
            last.lowercase() == "sol" -> "soles"; last.lowercase() == "colon" -> "colones"
            else -> last + "s"
        }
        return name.substringBeforeLast(' ', "").let { if (it.isEmpty()) plural else "$it $plural" }
    }
    private fun exchange(q: String): Pair<Result, Set<String>>? {
        // A tip is added to a bill, not taken from the money changed: that is currency()'s sum.
        if (tipWords.containsMatchIn(q)) return null
        val per = ratePer.find(q)?.takeIf { isCurrency(it.groupValues[2]) && isCurrency(it.groupValues[3]) }
        val eq = if (per != null) null else rateIs.find(q)?.takeIf { isCurrency(it.groupValues[1]) }
        val r = per ?: eq ?: return null
        val base = (if (per != null) per.groupValues[3] else eq!!.groupValues[1]).trim()
        // "4.3 Polish zloty" names the money last; in "36.5 baht and" the word after it is not money.
        val localWords = (if (per != null) per.groupValues[2] else eq!!.groupValues[3]).trim().split(Regex("\\s+"))
        val localName = when {
            isCurrency(localWords.last()) -> if (localWords.size > 1 && localWords.first().first().isUpperCase()) localWords.joinToString(" ") else localWords.last()
            isCurrency(localWords.first()) -> localWords.first()
            else -> return null
        }
        val local = localName.substringAfterLast(' ')
        val perBase = parseAmount(if (per != null) per.groupValues[1] else eq!!.groupValues[2], local) ?: return null
        if (perBase <= 0 || canon(base) == canon(local)) return null
        // The amount changed sits outside the rate and is in one of its two currencies; a fee's money is not it.
        val feeAt = flatFee.find(q)
        val fees = pctFee.findAll(q).toList()
        val amounts = Regex("(?i)(?<![\\d.,])$AMOUNT").findAll(q)
            .filter { it.range.first !in r.range && it.range.last !in r.range && (feeAt == null || it.range.first !in feeAt.range) }.toList()
        if (amounts.size != 1) return null
        val a = amounts.single().groupValues
        val symbolCurrency = mapOf("$" to "dollar", "€" to "euro", "£" to "pound", "¥" to "yen", "₹" to "rupee")
        val amountCurrency = symbolCurrency[a[1].ifBlank { a[4] }] ?: a[5].ifBlank { return null }
        val amount = parseAmount(a[2].ifBlank { a[3] }, amountCurrency) ?: return null
        if (amount <= 0) return null
        val forward = when (canon(amountCurrency)) { canon(base) -> true; canon(local) -> false; else -> return null }
        // Every fee named must have its size, and only one kind at a time: how a percentage and a flat fee
        // combine is the office's rule, not arithmetic.
        if (fees.size > 1 || (fees.isNotEmpty() && feeAt != null)) return null
        // The fee taken off is the money changer's: a hotel's "10% fee" in another sentence is not it.
        val changing = Regex("(?i)\\b(?:exchange|office|bureau|kiosk|booth|counter|bank|atm|changer|rate|commission|convert\\w*|chang\\w+|money|cash|currency)\\b")
        fun clauseOf(at: Int) = clauses.findAll(q).firstOrNull { at in it.range }?.value.orEmpty()
        val rateClause = clauseOf(r.range.first)
        for (f in fees.map { it.range } + listOfNotNull(feeAt?.range)) {
            val c = clauseOf(f.first)
            if (c != rateClause && !changing.containsMatchIn(c) && !Regex("(?i)commission").containsMatchIn(q.substring(f))) return null
        }
        val waived = noFee.findAll(q).map { it.range }.toList()
        val feeMentions = feeWord.findAll(q).filter { f -> waived.none { f.range.first in it } }.toList()
        if (feeMentions.any { f -> fees.none { f.range.first in it.range } && (feeAt == null || f.range.first !in feeAt.range) }) return null
        val baseName = plural(base.replace(Regex("(?i)^u\\.?s\\.?(?=\\s)"), "US"))
        val localPlural = plural(localName)
        fun inBase(v: Double) = "${money(v)} $baseName"
        fun inLocal(v: Double) = "${money(v)} $localPlural"
        fun out(v: Double) = if (forward) inLocal(v) else inBase(v)
        val before = if (forward) amount * perBase else amount / perBase
        val text = StringBuilder()
        text.append(if (forward) "${fmt(amount, 2)} $baseName × ${fmt(perBase, 4)} = **${inLocal(before)}**"
            else "${fmt(amount, 2)} $localPlural ÷ ${fmt(perBase, 4)} = **${inBase(before)}**")
        var after = before
        val used = mutableListOf(r.range, amounts.single().range)
        if (fees.isNotEmpty()) {
            val f = fees.single(); used += f.range
            val pct = num(f.groupValues[1].ifBlank { f.groupValues[2] }.ifBlank { f.groupValues[3] })
            if (pct <= 0 || pct >= 100) return null
            after = before * (1 - pct / 100)
            text.append(" before the commission.\n\nAfter the ${fmt(pct)}% commission: ${money(before)} × ${fmt(1 - pct / 100, 2)} = **${out(after)}**.")
        } else if (feeAt != null) {
            used += feeAt.range
            val fa = Regex("(?i)$AMOUNT").find(feeAt.value)?.groupValues ?: return null
            val feeCurrency = symbolCurrency[fa[1].ifBlank { fa[4] }] ?: fa[5].ifBlank { return null }
            val fee = parseAmount(fa[2].ifBlank { fa[3] }, feeCurrency) ?: return null
            // A fee in the money handed over comes off before the rate; one in the money received, after.
            val feeInSource = when (canon(feeCurrency)) { canon(amountCurrency) -> true; canon(if (forward) local else base) -> false; else -> return null }
            if (fee <= 0 || (feeInSource && fee >= amount) || (!feeInSource && fee >= before)) return null
            text.append(" before the fee.\n\n")
            if (feeInSource) {
                after = if (forward) (amount - fee) * perBase else (amount - fee) / perBase
                text.append("Less the ${money(fee)} ${if (forward) baseName else localPlural} fee first: ${fmt(amount - fee, 2)} ${if (forward) "×" else "÷"} ${fmt(perBase, 4)} = **${out(after)}**.")
            } else {
                after = before - fee
                text.append("Less the ${money(fee)} ${if (forward) localPlural else baseName} fee: ${money(before)} − ${money(fee)} = **${out(after)}**.")
            }
        } else text.append(".")
        text.append("\n\nThis uses the rate you gave; check the office's buy rate and any minimum fee before handing over the cash.")
        val facts = "${fmt(amount, 2)} ${if (forward) baseName else localPlural} = ${out(after)}" + (if (after != before) " after the fee" else "") + "."
        return Result(text.toString(), true, facts, used = used) to emptySet()
    }

    /* ---------- water for a group ---------- */
    private val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "a" to 1, "an" to 1)
    private fun count(token: String): Int? = token.lowercase().toIntOrNull() ?: words[token.lowercase()]
    private val ordinals = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7,
        "eighth" to 8, "ninth" to 9, "tenth" to 10)
    private const val N = "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
    private val groupSize = listOf(
        Regex("(?i)\\b$N\\s+(?:of us|people|persons|hikers|adults|travell?ers|friends|walkers|climbers)\\b"),
        Regex("(?i)\\b(?:family|group|party|team) of $N\\b"),
        Regex("(?i)\\bwe(?:'re| are) $N\\b"),
        Regex("(?i)\\b(both|the two) of us\\b"),
    )
    // "For 5 days", "a 3-day trek", "until (the end of) day 2", "until the third day", "48 hours", "4 nights".
    private val dayCount = Regex("(?i)\\b$N\\s+(?:full\\s+|whole\\s+|more\\s+|long\\s+)?days?\\b")
    private val dayHyphen = Regex("(?i)\\b$N-day\\b")
    private val untilDay = Regex("(?i)\\b(?:until|till|before|by|to|through|on)\\s+(?:the\\s+)?(?:end\\s+of\\s+|evening\\s+of\\s+|morning\\s+of\\s+)?(?:the\\s+)?" +
        "(?:day\\s+(\\d+)|(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|\\d+(?:st|nd|rd|th))\\s+day)\\b")
    private val hourCount = Regex("(?i)\\b$NUM\\s*(?:hours?|hrs?)\\b")
    private val nightCount = Regex("(?i)\\b$N\\s+nights?\\b")
    private val weekCount = Regex("(?i)\\b(?:$N|a)\\s+weeks?\\b")
    // Words that say the trip lasts longer than a day, or that more than one person drinks, when no count says how many.
    private val impliesDays = Regex("(?i)\\b(?:days|nights?|overnight|trek\\w*|expedition|until|till|weekend|weeks?|multi-day|day\\s+\\d)\\b")
    // Something the plain sum leaves out: a child drinking half, water for cooking, a dog, a refill on the way.
    private val waterTwist = Regex("(?i)\\b(?:half|double|twice|extra|additional|cooking|cook\\w*|dogs?|children|child|kids?|bab(?:y|ies)|refill\\w*|filter\\w*|purif\\w*|spring|well)\\b")
    private val impliesPeople = Regex("(?i)\\b(?:we|us|our|group|people|everyone|everybody|each|every|family|friends|team|party|hikers|all of)\\b")
    private fun water(q: String): Pair<Result, Set<String>>? {
        val perDay = Regex("(?i)$NUM\\s*(?:l|liters?|litres?)(?:\\s+of water)?\\s+(?:per|a|each)\\s+(?:person\\s+)?(?:per\\s+|a\\s+|each\\s+)?day").find(q) ?: return null
        // "We are 4 ... one of us is a child": two counts are not one group, and the sum is not guessed.
        val groups = groupSize.flatMap { it.findAll(q) }
        if (groups.map { count(it.groupValues[1]) ?: 2 }.distinct().size > 1) return null
        val group = groups.minByOrNull { it.range.first }
        val people = group?.let { count(it.groupValues[1]) ?: 2 }
        // "3 litres a day for 5 days": the day count is not the "a day" of the rate.
        val counted = dayCount.findAll(q).firstOrNull { it.range.first !in perDay.range } ?: dayHyphen.find(q)
        val until = untilDay.find(q); val hours = hourCount.find(q); val nights = nightCount.find(q); val weeks = weekCount.find(q)
        val (days, daysAt, dayNote) = when {
            counted != null -> Triple(count(counted.groupValues[1])?.toDouble(), counted, "")
            weeks != null -> Triple((count(weeks.groupValues[1].ifBlank { "1" }) ?: 1) * 7.0, weeks, " (${count(weeks.groupValues[1].ifBlank { "1" }) ?: 1} ${if (weeks.groupValues[1].isBlank() || count(weeks.groupValues[1]) == 1) "week" else "weeks"})")
            until != null -> Triple((until.groupValues[1].toIntOrNull() ?: ordinals[until.groupValues[2].lowercase()]
                ?: until.groupValues[2].filter(Char::isDigit).toIntOrNull())?.toDouble(), until, "")
            hours != null -> Triple(num(hours.groupValues[1]) / 24, hours, " (${fmt(num(hours.groupValues[1]), 0)} hours)")
            nights != null -> Triple(count(nights.groupValues[1])?.toDouble(), nights, "")
            else -> Triple(null, null, "")
        }
        val outside = q.replaceRange(perDay.range, " ".repeat(perDay.range.count()))
        // A multiplier the question implies but does not give is not guessed silently: the sum is shown for
        // one, says so, and research answers the rest.
        val assumptions = mutableListOf<String>()
        if (people == null && impliesPeople.containsMatchIn(outside)) assumptions += "one person, as the question does not say how many of you there are"
        if (days == null && impliesDays.containsMatchIn(outside)) assumptions += "one day, as the question does not say how many days you are out"
        if (waterTwist.containsMatchIn(q)) assumptions += "only drinking water at the rate given"
        if (nights != null && counted == null && weeks == null && until == null && hours == null) assumptions += "${fmt(days!!, 0)} nights as ${fmt(days, 0)} days of drinking water"
        val n = people ?: 1; val d = days ?: 1.0
        if (n <= 0 || d <= 0) return null
        val perPerson = num(perDay.groupValues[1])
        val litres = perPerson * n * d
        val text = buildString {
            append("$n ${if (n == 1) "person" else "people"} × ${fmt(d)} ${if (d == 1.0) "day" else "days"}$dayNote × ${fmt(perPerson)} L = **${fmt(litres)} litres**, ")
            append("which weighs about **${fmt(litres)} kg** (${fmt(litres * 2.2046, 0)} lb): a litre of water weighs about 1 kg, plus the containers.")
            if (n > 1) append("\n\nSplit evenly, that is ${fmt(litres / n)} L (${fmt(litres / n)} kg) each.")
            if (assumptions.isNotEmpty()) append("\n\nThis counts ${assumptions.joinToString(" and ")}.")
        }
        return Result(text, true, "${fmt(litres)} L of water, about ${fmt(litres)} kg.",
            used = listOfNotNull(perDay.range, group?.range, daysAt?.range), partial = assumptions.isNotEmpty()) to emptySet()
    }

    /* ---------- the fuel a trip burns and what it costs ---------- */
    private val kmPerLitre = Regex("(?i)$NUM\\s*(?:km|kilomet(?:er|re)s?)\\s*(?:per|a|/)\\s*(?:l|liter|litre)\\b")
    // "1.89 euros a litre", "$3.40 per gallon", "€1.65/L", "petrol is 1.70 a litre".
    private val fuelPrice = Regex("(?i)(?:($SYMBOLS)\\s*$NUM|$NUM\\s*(?:($SYMBOLS)|((?:$CURRENCIES)s?)\\b)?)\\s*(?:per|a|each|/)\\s*(l|liter|litre|gal|gallon)\\b")
    private val fuelWord = Regex("(?i)\\b(?:petrol|fuel|diesel|gas|gasoline|unleaded|pump)\\b")
    private val shareAsked = Regex("(?i)\\b(?:each|per (?:person|head)|a head|apiece|split\\w*|shar\\w+|divid\\w+|between|among|amongst|my share|chip in)\\b")
    // "3 of us", "the two of us", "between four people", "split 3 ways", "we are 4", "a car of five".
    private val shareCount = Regex("(?i)\\b(?:(?:the\\s+)?$COUNT\\s+of\\s+us|$COUNT\\s+(?:people|persons|friends|passengers|travell?ers|adults|drivers|mates|ways)|" +
        "(?:between|among|amongst|across)\\s+(?:the\\s+)?$COUNT\\b|(?:family|group|party|car(?:load)?)\\s+of\\s+$COUNT|we(?:'re|\\s+are)\\s+$COUNT\\b|(?:both|the two) of us)\\b")
    // A distance that is only half the driving: not doubled on a guess.
    private val eachWay = Regex("(?i)\\b(?:each way|one way|return|round[- ]trip|there and back|both ways|and back)\\b")
    private fun tripFuel(q: String): Pair<Result, Set<String>>? {
        val price = fuelPrice.find(q) ?: return null
        val use = per100.find(q); val mpgUse = mpg.find(q); val kpl = kmPerLitre.find(q)
        val consumption = listOfNotNull(use, mpgUse, kpl).singleOrNull() ?: return null
        val taken = listOf(price.range, consumption.range)
        val distances = distanceOnly.findAll(q).filter { d -> taken.none { d.range.first in it } }.toList()
        if (distances.size != 1 || eachWay.containsMatchIn(q)) return null
        val d = distances.single()
        val inMiles = d.groupValues[2].lowercase().startsWith("mi")
        val km = num(d.groupValues[1]) * (if (inMiles) 1.609344 else 1.0)
        // Fuel economy quoted in Britain is in imperial gallons, so British money marks them too.
        val imperial = Regex("(?i)\\b(?:imperial|uk|british)\\b|£|\\bpence\\b|\\bpounds?\\b").containsMatchIn(q)
        val gallon = if (imperial) 4.54609 else 3.785411784
        val rate = num(consumption.groupValues[1])
        if (km <= 0 || rate <= 0) return null
        val litres = when {
            use != null -> km * rate / 100
            kpl != null -> km / rate
            else -> km / 1.609344 / rate * gallon
        }
        val perGallon = price.groupValues[6].lowercase().startsWith("gal")
        val unitPrice = num(price.groupValues[2].ifBlank { price.groupValues[3] })
        val symbol = price.groupValues[1].ifBlank { price.groupValues[4] }
        val currencyName = symbol.ifBlank { price.groupValues[5] }
        // A bare "1.65 a litre" is a fuel price only where fuel is spoken of.
        if (unitPrice <= 0 || (currencyName.isBlank() && !fuelWord.containsMatchIn(q))) return null
        fun amount(v: Double) = when { symbol.isNotBlank() -> "$symbol${money(v)}"; currencyName.isNotBlank() -> "${money(v)} $currencyName"; else -> money(v) }
        val volume = if (perGallon) litres / gallon else litres
        val cost = volume * unitPrice
        val share = shareCount.find(q)
        val people = share?.let { s -> Regex("(?i)\\b$COUNT\\b").find(s.value)?.let { count(it.value) } ?: 2 }
        if (people != null && people < 2) return null
        val wantsShare = shareAsked.containsMatchIn(q)
        val gallonName = if (imperial) "imperial gallons" else "US gallons"
        val text = buildString {
            append(when {
                use != null -> "${fmt(km)} km × ${fmt(rate)} L/100 km = **${fmt(litres, 2)} litres** of fuel."
                kpl != null -> "${fmt(km)} km ÷ ${fmt(rate)} km/L = **${fmt(litres, 2)} litres** of fuel."
                else -> "${fmt(km / 1.609344)} miles ÷ ${fmt(rate)} mpg = **${fmt(litres / gallon, 2)} $gallonName** (${fmt(litres, 2)} litres) of fuel."
            })
            append("\n\n")
            append(if (perGallon) "${fmt(volume, 2)} gallons × ${amount(unitPrice)} = **${amount(cost)}**." else "${fmt(litres, 2)} L × ${amount(unitPrice)} = **${amount(cost)}**.")
            if (people != null) append("\n\nSplit $people ways: ${amount(cost)} ÷ $people = **${amount(cost / people)} each**.")
            else if (wantsShare) append("\n\nThe question does not say how many of you share the cost: divide the total by your number.")
            append("\n\nReal consumption rises with speed, load, hills and air conditioning, so budget a little more.")
        }
        val facts = "${fmt(litres, 2)} L of fuel for ${fmt(km)} km, costing ${amount(cost)}" + (if (people != null) ", ${amount(cost / people)} each" else "") + "."
        return Result(text, true, facts, used = listOfNotNull(price.range, consumption.range, d.range, share?.range),
            partial = people == null && wantsShare) to emptySet()
    }

    /* ---------- landing time across time zones ---------- */
    // "UTC+8", "GMT-5", "UTC−4", "UTC+5:30", and a bare "GMT" or "UTC" for zero.
    // "[.:]", not "[:.]": Android's ICU reads "[:" as the start of a POSIX class and the pattern fails to compile.
    private val offsetAt = Regex("(?i)\\b(?:utc|gmt)(?:\\s*([+\\-−–])\\s*(\\d{1,2})(?:[.:]?([0-5]\\d))?)?(?!\\d|:)")
    private const val PLACE = "(\\p{Lu}[\\p{L}'-]+(?:\\s+\\p{Lu}[\\p{L}'-]+){0,2}|home|here|there)"
    // The place an offset belongs to: "Singapore is UTC+8", "London (UTC+1)", "home, which is on GMT".
    private val placeOfOffset = Regex("$PLACE(?:\\s*(?:,|\\(|:|=|-|–|which|that|is|are|was|now|currently|right|on|at|in|runs|uses|sits|being|still|time|zone|the|local|clock|and|also|while|already))*\\s*$")
    private val origin = Regex("\\b(?:[Ll]eaves?|[Ll]eaving|[Ll]eft|[Dd]eparts?|[Dd]eparting|[Dd]eparted|[Ff]rom|[Oo]ut of|[Tt]akes? off (?:from|in))\\s+$PLACE")
    private val destination = Regex("\\b(?:[Tt]o|[Ii]nto|[Ii]n|[Aa]t|[Rr]each(?:es|ing)?|[Tt]owards?)\\s+$PLACE")
    // "London is 7 hours behind", "Tokyo is 8 hours ahead of us", "it is 5 and a half hours ahead".
    private val zoneGap = Regex("(?i)\\b(\\d{1,2}(?:\\.5)?|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen)\\s*(?:hours?|hrs?|h)\\b" +
        "(\\s+and\\s+a\\s+half)?(?:\\s+(\\d{2})\\s*(?:min(?:ute)?s?)?)?\\s+(ahead|behind|earlier|later|in front)\\b(?:\\s+(?:of|than))?(?:\\s+$PLACE)?")
    private val gapSubject = Regex("$PLACE\\s*(?:(?:is|are|'s|runs|sits|being|will be|would be|time is)\\s*)?(?:(?:about|roughly|around|currently|now|only|just)\\s*)*$")
    private val secondLeg = Regex("(?i)\\b(?:layover|stop-?over|connection|connecting|transfer|change (?:planes|trains)|wait)\\b")
    private val asksLanding = Regex("(?i)\\b(?:land|lands|landing|arrive|arrives|arriving|arrival|get (?:in|there)|touch down|reach|be there|time (?:is it|will it be|there))\\b")
    private val asksTakeoff = Regex("(?i)\\b(?:leave|leaves|depart|departs|departure|take off|takes off|set off|board|boarding)\\b")
    private fun offsetMinutes(m: MatchResult): Int {
        if (m.groupValues[1].isEmpty()) return 0
        val sign = if (m.groupValues[1] == "+") 1 else -1
        return sign * (m.groupValues[2].toInt() * 60 + (m.groupValues[3].ifBlank { "0" }).toInt())
    }
    private fun utc(minutes: Int) = if (minutes == 0) "UTC" else "UTC${if (minutes < 0) "−" else "+"}${kotlin.math.abs(minutes) / 60}" +
        (kotlin.math.abs(minutes) % 60).let { if (it == 0) "" else ":%02d".format(it) }
    private fun arrival(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bwhen\\b|\\bwhat time\\b|\\btime (?:is it|will it be|there)\\b").containsMatchIn(q)) return null
        val gap = zoneGap.find(q)
        // "2 hours ahead" is the gap between the zones, not the flight's length.
        val times = clockTime.findAll(q).toList(); val spans = span.findAll(q).filter { gap == null || it.range.first !in gap.range }.toList()
        if (times.size != 1 || spans.size != 1) return null
        val t = times.single(); val s = spans.single()
        // A second leg or a layover the sum would leave out is not guessed at; the hour gap between the zones is not one.
        val blank = { text: String, r: IntRange -> text.replaceRange(r, " ".repeat(r.count())) }
        if (otherDuration.containsMatchIn(gap?.let { blank(blank(q, s.range), it.range) } ?: blank(q, s.range)) || secondLeg.containsMatchIn(q)) return null
        val clauseStart = { at: Int -> q.lastIndexOfAny(charArrayOf('.', '?', '!', ';'), at) + 1 }
        val lower = { p: String -> p.lowercase().trimEnd('.', ',') }
        val origins = origin.findAll(q).map { lower(it.groupValues[1]) }.toSet()
        val destinations = destination.findAll(q).map { lower(it.groupValues[1]) }.filterNot { it in origins }.toSet()
        // "Home" is where the trip goes when it goes home, and where it starts when it leaves home.
        val homeIsDestination = Regex("(?i)\\b(?:fly|flying|flights?|go|going|get|getting|head|heading|travel\\w*|back|return\\w*)\\s+(?:back\\s+)?home\\b|" +
            "\\bat home when\\b|\\bhome time\\b").containsMatchIn(q)
        val homeIsOrigin = Regex("(?i)\\b(?:leave|leaving|left|depart\\w*|from)\\s+home\\b").containsMatchIn(q)
        fun role(place: String): Boolean? = when {
            place in origins || place == "here" || (place == "home" && homeIsOrigin && !homeIsDestination) -> false
            place in destinations || place == "there" || (place == "home" && homeIsDestination && !homeIsOrigin) -> true
            else -> null
        }
        var depName: String? = null; var arrName: String? = null
        var depOffset: Int? = null; var arrOffset: Int? = null
        val used = mutableListOf(t.range, s.range)
        val offsets = offsetAt.findAll(q).toList()
        val asking = askingClauses(q).joinToString(" ") { it.value }
        // "23:50 Singapore time" puts the departure in Singapore; "When do I land, London time?" asks about London.
        val afterTime = q.substring(t.range.last + 1)
        fun placeRole(place: String): Boolean? = role(lower(place)) ?: when {
            Regex("(?i)^\\s*(?:local\\s+)?${Regex.escape(place)}\\s+time\\b").containsMatchIn(afterTime) -> false
            Regex("(?i)\\b${Regex.escape(place)}\\b").containsMatchIn(asking) -> true
            else -> null
        }
        if (offsets.size == 2) {
            val placed = offsets.map { o ->
                val before = q.substring(clauseStart(o.range.first), o.range.first)
                val place = placeOfOffset.find(before)?.groupValues?.get(1)
                // "From UTC+8 to UTC+1", or the offset right after the departure time: "leaves at 23:50 (UTC+8)".
                val byWord = when {
                    Regex("(?i)\\bfrom\\s*\\(?\\s*$").containsMatchIn(before) -> false
                    Regex("(?i)\\b(?:to|into)\\s*\\(?\\s*$").containsMatchIn(before) -> true
                    place == null && t.range.last < o.range.first && q.substring(t.range.last + 1, o.range.first).let { it.length <= 14 && it.none { c -> c in ".?!;" } } -> false
                    else -> null
                }
                Triple(o, place, place?.let { placeRole(it) } ?: byWord)
            }
            val roles = placed.map { it.third }
            val (dep, arr) = when {
                roles.count { it == false } == 1 && roles.count { it == true } == 1 -> placed.first { it.third == false } to placed.first { it.third == true }
                roles.count { it == false } == 1 && roles.count { it == null } == 1 -> placed.first { it.third == false } to placed.first { it.third == null }
                roles.count { it == true } == 1 && roles.count { it == null } == 1 -> placed.first { it.third == null } to placed.first { it.third == true }
                else -> return null
            }
            depOffset = offsetMinutes(dep.first); arrOffset = offsetMinutes(arr.first)
            depName = dep.second; arrName = arr.second
            used += dep.first.range; used += arr.first.range
        } else if (offsets.isEmpty() && gap != null) {
            val g = gap.groupValues
            val hours = g[1].toDoubleOrNull() ?: words[g[1].lowercase()]?.toDouble() ?: mapOf("thirteen" to 13.0, "fourteen" to 14.0)[g[1].lowercase()] ?: return null
            val minutes = (hours * 60 + (if (g[2].isNotEmpty()) 30 else 0) + (g[3].ifBlank { "0" }).toInt()).roundToInt()
            val subject = gapSubject.find(q.substring(clauseStart(gap.range.first), gap.range.first))?.groupValues?.get(1)
            val other = g[5].ifBlank { null }
            val subjectIsDestination = subject?.let { role(lower(it)) } ?: other?.let { role(lower(it)) }?.not() ?: return null
            val behind = g[4].lowercase() in setOf("behind", "earlier")
            // The destination's clock against the departure's: behind means earlier.
            val diff = if (subjectIsDestination == behind) -minutes else minutes
            depName = (if (subjectIsDestination) other else subject)?.takeIf { lower(it) !in setOf("here", "there", "home") }
            arrName = (if (subjectIsDestination) subject else other)?.takeIf { lower(it) !in setOf("here", "there", "home") }
            depOffset = 0; arrOffset = diff
            used += gap.range
        } else return null
        if (depName == null) depName = origins.firstOrNull()?.let { n -> origin.findAll(q).first { lower(it.groupValues[1]) == n }.groupValues[1] }
        if (arrName == null) arrName = destinations.firstOrNull()?.let { n -> destination.findAll(q).first { lower(it.groupValues[1]) == n }.groupValues[1] }
        val start = if (t.groupValues[3].isNotEmpty()) {
            val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
            if (h !in 1..12 || m > 59) return null
            ((h % 12) + if (t.groupValues[3].lowercase().startsWith("p")) 12 else 0) * 60 + m
        } else t.groupValues[4].toInt() * 60 + t.groupValues[5].toInt()
        val g = s.groupValues
        val duration = when {
            g[1].isNotEmpty() -> (num(g[1]) * 60 + g[2].toInt()).roundToInt()
            g[3].isNotEmpty() -> (num(g[3]) * 60 + num(g[4].ifBlank { "0" })).roundToInt()
            else -> num(g[5]).roundToInt()
        }
        if (duration <= 0 || duration > 48 * 60) return null
        // The time's role from the role word nearest it, and what is asked from the asking clause.
        val before = q.substring(0, t.range.first)
        // "Flight from Dubai at 02:15": leaving a place at a time is a start.
        val fromPlace = Regex("\\b[Ff]rom\\s+$PLACE\\s*,?\\s*(?:at\\s+)?$").find(before)?.range?.first ?: -1
        val starts = maxOf(startsAt.findAll(before).lastOrNull()?.range?.first ?: -1, fromPlace)
        val ends = endsAt.findAll(before).lastOrNull()?.range?.first ?: -1
        val wantsLanding = asksLanding.containsMatchIn(asking); val wantsTakeoff = asksTakeoff.containsMatchIn(asking)
        val forward = when {
            starts > ends && wantsLanding && !wantsTakeoff -> true
            ends > starts && wantsTakeoff && !wantsLanding -> false
            else -> return null
        }
        val diff = arrOffset!! - depOffset!!
        val end = if (forward) start + duration + diff else start - duration - diff
        val day = Math.floorDiv(end, 24 * 60); val at = Math.floorMod(end, 24 * 60)
        fun clock(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        val dayNote = when { day == 1 -> " the next day"; day > 1 -> " $day days later"; day == -1 -> " the day before"; day < -1 -> " ${-day} days before"; else -> "" }
        val depLabel = depName?.let { "$it time" } ?: "departure time (${utc(depOffset)})"
        val arrLabel = arrName?.let { "$it time" } ?: "arrival time (${utc(arrOffset)})"
        val gapText = when {
            diff == 0 -> "on the same clock as"
            diff < 0 -> "${hours(-diff / 60.0)} behind"
            else -> "${hours(diff / 60.0)} ahead of"
        }
        val zones = if (offsets.isNotEmpty()) " (${utc(arrOffset)} against ${utc(depOffset)})" else ""
        val length = hours(duration / 60.0)
        val text = if (forward) {
            val same = start + duration
            "${clock(start)} $depLabel plus $length is ${clock(Math.floorMod(same, 24 * 60))} $depLabel${if (same >= 24 * 60) " the next day" else ""}. " +
                "${(arrName ?: "your destination").replaceFirstChar(Char::uppercaseChar)} is $gapText ${depName ?: "your departure point"}$zones, so you land at **${clock(at)}$dayNote**, $arrLabel."
        } else {
            "To land at ${clock(start)} $arrLabel after $length, with ${arrName ?: "your destination"} $gapText ${depName ?: "your departure point"}$zones, " +
                "you leave at **${clock(at)}$dayNote**, $depLabel."
        }
        val facts = if (forward) "Landing at ${clock(at)}$dayNote $arrLabel." else "Leaving at ${clock(at)}$dayNote $depLabel."
        return Result(text, true, facts, used = used) to emptySet()
    }

    /* ---------- a power bank's energy ---------- */
    private fun battery(q: String): Pair<Result, Set<String>>? {
        val mah = Regex("(?i)$NUM\\s*mah\\b").find(q) ?: return null
        // Capacity in mAh is rated at the cell's voltage (3.6-3.85 V); "20 V USB-C output" is not it.
        val stated = Regex("(?i)$NUM\\s*v(?:olts?)?\\b").findAll(q).map { num(it.groupValues[1]) }.toList()
        val volts = stated.firstOrNull { it in 3.0..4.5 }
        val v = volts ?: 3.7
        val wh = num(mah.groupValues[1]) * v / 1000
        val flying = Regex("(?i)\\b(plane|flight|fly|flying|airline|airport|carry[- ]on|cabin|luggage|baggage)\\b").containsMatchIn(q)
        val used = listOf(mah.range) + Regex("(?i)$NUM\\s*v(?:olts?)?\\b").findAll(q).map { it.range }
        val text = buildString {
            append("${fmt(num(mah.groupValues[1]), 0)} mAh × ${fmt(v, 2)} V ÷ 1,000 = **${fmt(wh, 2)} Wh**.")
            if (volts == null) append(if (stated.isEmpty()) " (No voltage was given, so this uses 3.7 V, the usual rating for lithium-ion cells.)"
                else " (The ${fmt(stated.first())} V in the question is a charging voltage; capacity in mAh is rated at the cell's 3.7 V, so this uses 3.7 V.)")
            if (flying) {
                append("\n\n")
                append(when {
                    wh <= 100 -> "That is under 100 Wh, so under the IATA rules most airlines follow it can fly in your **carry-on** bag without approval."
                    wh <= 160 -> "That is between 100 and 160 Wh, so under the IATA rules it needs the **airline's approval** and must stay in your carry-on."
                    else -> "That is over 160 Wh, which the IATA rules do **not allow** on passenger planes."
                })
                append(" Power banks must never go in checked baggage. Airlines can be stricter, for example limiting how many you carry or not letting you charge them in flight, so ask at check-in if unsure.")
            }
        }
        return Result(text, true, "${fmt(wh, 2)} Wh.", used = used) to
            (if (flying) setOf("allowed", "ok", "okay", "legal", "can i bring", "can i take", "can i carry", "can i pack") else emptySet())
    }

    /* ---------- temperature ---------- */
    private val temp = Regex("(?i)(-?$NUM)\\s*(?:°|degrees?)?\\s*(f|fahrenheit|c|celsius|centigrade)\\b")
    private fun temperature(q: String): Pair<Result, Set<String>>? {
        val m = temp.find(q) ?: return null
        // A bare "C" or "F" counts only beside weather words, so "5 c" in other text is not a temperature.
        if (!Regex("(?i)celsius|fahrenheit|°|degrees?|\\bin [cf]\\b").containsMatchIn(q) &&
            !Regex("(?i)\\b(outside|outdoors|weather|temperature|hot|cold|warm|freezing|heat)\\b").containsMatchIn(q)) return null
        val value = num(m.groupValues[1].removePrefix("-")) * (if (m.groupValues[1].startsWith("-")) -1 else 1)
        val fromF = m.groupValues[3].lowercase().startsWith("f")
        val text = if (fromF) "${fmt(value)}°F is **${fmt((value - 32) * 5 / 9)}°C**.\n\nWorking: (${fmt(value)} − 32) × 5 ÷ 9 = ${fmt((value - 32) * 5 / 9, 2)}."
            else "${fmt(value)}°C is **${fmt(value * 9 / 5 + 32)}°F**.\n\nWorking: ${fmt(value)} × 9 ÷ 5 + 32 = ${fmt(value * 9 / 5 + 32, 2)}."
        val celsius = if (fromF) (value - 32) * 5 / 9 else value
        val fact = if (fromF) "${fmt(value)}°F = ${fmt(celsius)}°C." else "${fmt(value)}°C = ${fmt(value * 9 / 5 + 32)}°F."
        val feel = when { celsius >= 32 -> ", very hot"; celsius >= 27 -> ", hot"; celsius <= -10 -> ", extreme cold"; celsius <= 0 -> ", freezing cold"; else -> "" }
        val stated = if (fromF) "${fmt(value)}°F (${fmt(celsius)}°C$feel)" else "${fmt(value)}°C (${fmt(value * 9 / 5 + 32)}°F$feel)"
        // Heat and cold guides are about the weather: a 104°F fever, a 0°F freezer or 75°C for cooking
        // chicken are not heatstroke or frostbite.
        val air = Regex("(?i)\\b(outside|outdoors|weather|forecast|heat ?wave|today|tomorrow|tonight|walk\\w*|hik\\w*|run|running|jog\\w*|" +
            "cycl\\w*|bik\\w*|climb\\w*|camp\\w*|beach|sun|shade|trek\\w*|work outside|working outside|commute)\\b").containsMatchIn(q) &&
            !Regex("(?i)\\b(fever|body|oven|cook\\w*|bak\\w*|water|bath|shower|pool|sauna|freezer|fridge|refrigerator|engine|cpu|laptop|phone|battery)\\b").containsMatchIn(q)
        val guide = if (!air) null else when { celsius >= 32 -> "heat-illness"; celsius <= -10 -> "extreme-cold"; else -> null }
        val f = if (fromF) value else value * 9 / 5 + 32
        val pair = "${fmt(celsius)}°C (${fmt(f)}°F)"
        // The National Weather Service rates a heat index of 90-103°F "extreme caution" and above 103°F
        // "danger"; the heat index (temperature adjusted for humidity) is at least the air temperature
        // in humid weather. Conservative wording: the risks are possible, more so in sun or humidity.
        val risk = if (!air) null else when {
            celsius >= 39.5 -> "At $pair, a long walk or other hard activity risks heat exhaustion and heat stroke, more so in sun or humidity. " +
                "The US National Weather Service rates a heat index (temperature adjusted for humidity) above 103°F (39°C) as \"danger\". " +
                "Avoid the hottest hours, rest in shade and drink water regularly. The guide below gives the warning signs and first aid."
            celsius >= 32 -> "At $pair, a long walk can bring on heat exhaustion, more so in sun or humidity. " +
                "The US National Weather Service rates a heat index (temperature adjusted for humidity) of 90-103°F (32-39°C) as \"extreme caution\". " +
                "Avoid the hottest hours, rest in shade and drink water regularly. The guide below gives the warning signs and first aid."
            celsius <= -10 -> "At $pair, exposed skin can get frostbite and a long time outside can lead to hypothermia, more so in wind or wet clothing. " +
                "Wear layers, cover your skin and limit your time outside. The guide below gives the warning signs and what to do."
            else -> null
        }
        // The conversion is answered above; asking it again would make a guide-only answer refuse.
        val asked = Regex("(?i)\\b(?:what(?:'s| is) (?:that|this|it)|how (?:much|many) (?:is )?(?:that|this|it)|convert (?:that|this|it))\\s+(?:in|to|into)\\s+" +
            "(?:degrees\\s+)?(?:celsius|fahrenheit|centigrade|°?[cf])\\b[,?.]?\\s*(?:and\\s+)?")
        val rest = asked.replace(q.replaceRange(m.range, stated), "").trim().replaceFirstChar(Char::uppercaseChar)
            .replace(Regex("(?<=[.?!]\\s)\\p{Ll}")) { it.value.uppercase() }
        return Result(text, true, fact, followUp = rest, guideId = guide, riskNote = risk, used = listOf(m.range)) to emptySet()
    }

    /* ---------- fuel range on one tank ---------- */
    private val tank = Regex("(?i)(?:tank\\s+(?:holds|takes|is|of)\\s+(?:about\\s+)?$NUM\\s*(?:l|liters?|litres?)\\b|$NUM\\s*(?:l|liters?|litres?)\\s+(?:fuel\\s+)?tank)")
    private val trip = Regex("(?i)\\b(?:drive|driving|go|travel|trip|journey|cover|do)\\s+(?:of\\s+|for\\s+|about\\s+)?$NUM\\s*(km|kilometers?|kilometres?|mi|miles?)\\b")
    private fun fuelRange(q: String): Pair<Result, Set<String>>? {
        val use = per100.find(q) ?: return null
        val t = tank.find(q) ?: return null
        val per = num(use.groupValues[1]); val litres = num(t.groupValues[1].ifBlank { t.groupValues[2] })
        if (per <= 0 || litres <= 0) return null
        val range = litres / per * 100
        val journey = trip.find(q)
        val text = buildString {
            append("${fmt(litres)} L ÷ ${fmt(per)} L/100 km × 100 = **${fmt(range, 0)} km** on a full tank.")
            journey?.let { m ->
                val km = num(m.groupValues[1]) * (if (m.groupValues[2].lowercase().startsWith("mi")) 1.609344 else 1.0)
                val needed = km * per / 100
                append("\n\n")
                append(if (km <= range) "So **yes**, ${fmt(km, 0)} km fits, using about ${fmt(needed)} L and leaving about ${fmt(litres - needed)} L."
                    else "So **no**: ${fmt(km, 0)} km needs about ${fmt(needed)} L, ${fmt(needed - litres)} L more than the tank holds, so plan a fuel stop.")
            }
            append("\n\nReal consumption rises with speed, load, hills and air conditioning, so keep a reserve.")
        }
        return Result(text, true, "Range on ${fmt(litres)} L at ${fmt(per)} L/100 km = ${fmt(range, 0)} km.",
            used = listOfNotNull(use.range, t.range, journey?.range)) to emptySet()
    }

    /* ---------- walking or driving time against the time left ---------- */
    private val speed = Regex("(?i)$NUM\\s*(km/h|kph|kmh|km an hour|kilomet(?:er|re)s? (?:per|an) hour|mph|miles? (?:per|an) hour)")
    private val distanceOnly = Regex("(?i)$NUM\\s*(km|kilometers?|kilometres?|mi|miles?)\\b(?!\\s*(?:/|per|an)\\s*h)")
    private val timeLeft = Regex("(?i)$NUM\\s*(?:hours?|hrs?|h)\\s+(?:of\\s+)?(?:day\\s*light|light|left|available|before (?:dark|sunset|nightfall)|until (?:dark|sunset))")
    private fun travelTime(q: String): Pair<Result, Set<String>>? {
        val v = speed.find(q) ?: return null
        val d = distanceOnly.findAll(q).firstOrNull { it.range.first != v.range.first } ?: return null
        val mph = v.groupValues[2].lowercase().let { it.startsWith("mph") || it.startsWith("mile") }
        val kmh = num(v.groupValues[1]) * (if (mph) 1.609344 else 1.0)
        val km = num(d.groupValues[1]) * (if (d.groupValues[2].lowercase().startsWith("mi")) 1.609344 else 1.0)
        if (kmh <= 0) return null
        val need = km / kmh
        val left = timeLeft.find(q)
        val text = buildString {
            append("${fmt(km)} km ÷ ${fmt(kmh)} km/h = **${hours(need)}** of moving time.")
            // Ten minutes' rest an hour is the usual allowance for walkers and drivers alike.
            val withBreaks = need * 7 / 6
            append(" With 10-minute breaks each hour it takes about **${hours(withBreaks)}**.")
            left?.let { m ->
                val have = num(m.groupValues[1])
                append("\n\n")
                append(when {
                    withBreaks <= have -> "With ${hours(have)} left, **yes**: even with breaks you have ${hours(have - withBreaks)} to spare."
                    need <= have -> "With ${hours(have)} left, **only just**: without stops you have ${hours(have - need)} to spare, " +
                        "but with breaks you would be ${hours(withBreaks - have)} short. Cut the breaks short or plan for the last part in the dark."
                    else -> "With ${hours(have)} left, **no**: even without stops you would be ${hours(need - have)} short."
                })
                append(" Steep or rough ground, heat and tiredness slow you down further.")
            }
        }
        return Result(text, true, "${fmt(km)} km at ${fmt(kmh)} km/h = ${hours(need)}.", used = listOfNotNull(v.range, d.range, left?.range)) to emptySet()
    }

    /* ---------- the time in another city ---------- */
    private val clockTime = Regex("(?i)\\b(\\d{1,2})(?:[.:](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b")
    // A full stop ends the name: "in London. What time" is London.
    private val cityAfterIn = Regex("\\b(?:in|at)\\s+((?:[A-Z][\\p{L}'-]+)(?:\\s+[A-Z][\\p{L}'-]+){0,2})")
    // Cities the time-zone database does not name directly.
    private val zoneAliases = mapOf(
        "delhi" to "Asia/Kolkata", "new delhi" to "Asia/Kolkata", "mumbai" to "Asia/Kolkata", "bangalore" to "Asia/Kolkata", "bengaluru" to "Asia/Kolkata",
        "beijing" to "Asia/Shanghai", "shenzhen" to "Asia/Shanghai", "guangzhou" to "Asia/Shanghai", "hanoi" to "Asia/Ho_Chi_Minh",
        "saigon" to "Asia/Ho_Chi_Minh", "kyoto" to "Asia/Tokyo", "osaka" to "Asia/Tokyo", "bali" to "Asia/Makassar", "denpasar" to "Asia/Makassar",
        "san francisco" to "America/Los_Angeles", "seattle" to "America/Los_Angeles", "las vegas" to "America/Los_Angeles",
        "washington" to "America/New_York", "boston" to "America/New_York", "miami" to "America/New_York", "atlanta" to "America/New_York",
        "houston" to "America/Chicago", "dallas" to "America/Chicago", "austin" to "America/Chicago", "rio de janeiro" to "America/Sao_Paulo",
        "rio" to "America/Sao_Paulo", "barcelona" to "Europe/Madrid", "milan" to "Europe/Rome", "munich" to "Europe/Berlin",
        "geneva" to "Europe/Zurich", "edinburgh" to "Europe/London", "manchester" to "Europe/London", "cape town" to "Africa/Johannesburg",
        "melbourne" to "Australia/Melbourne", "sydney" to "Australia/Sydney", "auckland" to "Pacific/Auckland", "dubai" to "Asia/Dubai",
        "hong kong" to "Asia/Hong_Kong", "singapore" to "Asia/Singapore", "seoul" to "Asia/Seoul", "taipei" to "Asia/Taipei",
        "ho chi minh city" to "Asia/Ho_Chi_Minh", "mexico city" to "America/Mexico_City", "montreal" to "America/Toronto", "vancouver" to "America/Vancouver",
    )
    private val zonesByCity: Map<String, String> by lazy {
        ZoneId.getAvailableZoneIds().filter { Regex("^(Europe|Asia|America|Africa|Australia|Pacific|Atlantic|Indian)/[^/]+$").matches(it) }
            .associateBy { it.substringAfterLast('/').replace('_', ' ').lowercase() } + zoneAliases
    }
    private fun zoneFor(phrase: String): Pair<String, ZoneId>? {
        val words = phrase.trimEnd('.', ',').split(Regex("\\s+"))
        // "New York City" tries "New York City", then "New York", then "New".
        for (n in words.size downTo 1) {
            val name = words.take(n).joinToString(" ")
            zonesByCity[name.lowercase()]?.let { return name to ZoneId.of(it) }
        }
        return null
    }
    private fun clock(q: String, today: LocalDate): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bwhat time\\b|\\btime (?:is it|there|difference)\\b").containsMatchIn(q)) return null
        // An arrival time needs the flight's length, which a clock conversion would silently leave out.
        if (Regex("(?i)\\b(fly|flying|flight|land|lands|landing|arrive|arrives|arrival|depart\\w*|leave|leaves|take off|takes off)\\b").containsMatchIn(q)) return null
        val t = clockTime.find(q) ?: return null
        val mentions = cityAfterIn.findAll(q).mapNotNull { m -> zoneFor(m.groupValues[1])?.let { m.range.first to it } }
            .distinctBy { it.second.second }.toList()
        if (mentions.size != 2) return null
        // The stated time belongs to the city named nearest it: "9:00 in Paris", "in London it is 3 pm".
        val source = mentions.minBy { kotlin.math.abs(it.first - t.range.first) }
        val (fromName, from) = source.second; val (toName, to) = mentions.first { it !== source }.second
        val time = if (t.groupValues[3].isNotEmpty()) {
            val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
            if (h !in 1..12 || m > 59) return null
            val pm = t.groupValues[3].lowercase().startsWith("p")
            LocalTime.of((h % 12) + (if (pm) 12 else 0), m)
        } else LocalTime.of(t.groupValues[4].toInt(), t.groupValues[5].toInt())
        val start = ZonedDateTime.of(today, time, from); val there = start.withZoneSameInstant(to)
        fun show(z: ZonedDateTime) = String.format(Locale.US, "%d:%02d %s", (z.hour + 11) % 12 + 1, z.minute, if (z.hour < 12) "am" else "pm")
        fun utc(z: ZonedDateTime): String {
            val minutes = z.offset.totalSeconds / 60
            val sign = if (minutes < 0) "−" else "+"
            return "UTC$sign${kotlin.math.abs(minutes) / 60}" + (kotlin.math.abs(minutes) % 60).let { if (it == 0) "" else ":%02d".format(it) }
        }
        val day = when (there.toLocalDate().compareTo(start.toLocalDate())) { 1 -> " the next day"; -1 -> " the previous day"; else -> "" }
        val text = "${show(start)} in $fromName is **${show(there)} in $toName**$day.\n\n" +
            "Today $fromName is on ${utc(start)} and $toName on ${utc(there)}. The difference changes by an hour when either place starts or ends daylight saving time."
        return Result(text, true, "${show(start)} in $fromName = ${show(there)}$day in $toName.", used = listOf(t.range)) to emptySet()
    }

    /* ---------- the time between two clock times ---------- */
    private fun duration(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bhow (?:many|much|long)\\b|\\bdaylight\\b|\\bduration\\b|\\btime between\\b").containsMatchIn(q)) return null
        // "What time is it in Tokyo when…" converts a time; it does not measure one.
        if (Regex("(?i)\\bwhat time\\b|\\btime (?:is it|there)\\b").containsMatchIn(q)) return null
        val found = clockTime.findAll(q).toList()
        val times = found.mapNotNull { t ->
            if (t.groupValues[3].isNotEmpty()) {
                val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
                if (h !in 1..12 || m > 59) null else ((h % 12) + if (t.groupValues[3].lowercase().startsWith("p")) 12 else 0) * 60 + m
            } else t.groupValues[4].toInt() * 60 + t.groupValues[5].toInt()
        }.toList()
        if (times.size != 2) return null
        var minutes = times[1] - times[0]
        val overnight = minutes <= 0
        if (overnight) minutes += 24 * 60
        fun clock(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        val length = hours(minutes / 60.0)
        val text = "From ${clock(times[0])} to ${clock(times[1])}${if (overnight) " the next day" else ""} is **$length**."
        return Result(text, true, "${clock(times[0])} to ${clock(times[1])} = $length.", used = found.map { it.range }) to emptySet()
    }

    /* ---------- a clock time plus or minus a duration ---------- */
    // "2 h 35" or "9 hours 30" (minutes after the hours, without their unit), "2 hours and 35 minutes", "30 minutes".
    private val span = Regex("(?i)\\b$NUM\\s*(?:hours?|hrs?|h)\\s*(?:and\\s+)?([0-5]\\d)\\b(?!-|\\s*(?:%|[a-z]))|" +
        "\\b$NUM\\s*(?:hours?|hrs?|h)\\b(?:\\s*(?:and\\s*)?$NUM\\s*(?:minutes?|mins?|m)\\b)?|\\b$NUM\\s*(?:minutes?|mins?)\\b")
    // A duration the sum would leave out: "a 30-minute bus", "an hour", "half an hour".
    private val otherDuration = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s*-\\s*(?:minute|hour)s?\\b|\\b(?:an|one|half an|a quarter of an|a half) hour\\b|" +
        "\\bhalf(?:-| )hour\\b|\\b(?:a|one) (?:few|couple of) (?:minutes|hours)\\b|\\bthen\\b|\\bplus\\b")
    // "30 minutes before", "3 hours later": the direction the duration runs from the event it names.
    private val relative = Regex("(?i)^\\W{0,3}(?:\\w+\\W+){0,2}?(before|early|earlier|prior|ahead|until|till|after|later)\\b" +
        "(?:\\s+(to|of|than|for)\\b)?")
    // The one word a "before" may end on and still mean the time's own event: "before.", "before it",
    // "before departure", "before the flight" when the flight is what leaves at the time.
    private val namedEvent = Regex("(?i)^\\s*(?:(?:the|my|our|your)\\s+)?([\\p{L}-]*)\\s*(?:[.?!,;:]|$)")
    // "Have 3 hours" or "spend 3 hours there" is time spent at a place, not a trip that starts or ends at the time.
    private val dwell = Regex("(?i)\\b(?:have|has|had|got|spend|spending|stay|staying|wait|waiting)\\s+" +
        "(?:(?:about|around|only|just|another|a|an|for)\\s+)?$")
    private val asksWhen = Regex("(?i)\\bwhen\\b|\\bwhat time\\b")
    // "Leaves Paris at 10:00", "land in New York", "the bus to Chiang Mai": a trip between places can cross
    // a time zone.
    private val travels = Regex("(?i)\\b(?:fly|flying|flies|flight|flights|plane|land|lands|landing|arrive|arrives|arriving|arrival|" +
        "reach|reaches|gets? (?:to|in)|train|bus|coach|ferry|boat|drive|driving|journey|trip|travel\\w*|cross\\w*|border)\\b")
    private val placeAfter = Regex("\\b(?:[Ii]n|[Aa]t|[Tt]o|[Ff]rom|[Ii]nto|[Vv]ia)\\s+(?:the\\s+)?\\p{Lu}")
    private val placeWords = Regex("(?i)\\b(?:in|at|to|from|into|via|leaves?|leaving|departs?|departing)\\s+(?:the\\s+)?" +
        "(?=([\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,2}))")
    private val capitalised = Regex("\\b(?=(\\p{Lu}[\\p{L}'-]+(?:\\s+\\p{Lu}[\\p{L}'-]+){0,2}))")
    private fun namesPlace(q: String) = placeAfter.containsMatchIn(q) ||
        (placeWords.findAll(q) + capitalised.findAll(q)).any { zoneFor(it.groupValues[1]) != null }
    // The clock time's role, from the role word nearest it: "leaves at 9:40" is a start, while in "leave to
    // arrive by 9:00" or "boarding closes at 13:50" the time is an end. The question then asks for the other one.
    private val startsAt = Regex("(?i)\\b(?:leave|leaves|leaving|depart|departs|departing|departure|start|starts|starting|begin|begins|" +
        "set(?:s|ting)? off|take(?:s)? off|board(?:s|ing)?|open(?:s)?)\\b(?=[^.?!]{0,25}$)")
    private val endsAt = Regex("(?i)\\b(?:arrive|arrives|arriving|arrival|land|lands|landing|reach|be (?:there|back|home|at [\\p{L} ]{1,20}?)|" +
        "get (?:there|back|home)|finish|finishes|end|ends|close|closes|closing|deadline|by)\\b(?=[^.?!]{0,25}$)")
    // What is asked, from the first role verb after "when" or "what time": "When do I leave to arrive by 9:00?"
    // asks for a start, and "leave and arrive" for both, which one sum cannot give.
    private const val ASK_START = "leave|depart|set off|start|begin|go(?!\\s+(?:back|home))|head out|wake|get up"
    private const val ASK_END = "arrive|arrival|get (?:there|in|to|back|home)|reach|finish|end|land|be (?:there|back|done|home)|done|over|return|back"
    private val asked = Regex("(?i)\\b(?:when|what time)\\b[^.?!]*?\\b(?:($ASK_START)|($ASK_END))\\b" +
        "(?:\\s*,?\\s*(?:and|or)\\s+(?:[\\p{L}']+\\s+){0,2}?(?:($ASK_START)|($ASK_END))\\b)?")
    // "When do I leave" and "when to leave" ask; "When we leave at 9:40, ..." only gives the time's role.
    private val auxiliary = Regex("(?i)\\b(?:do|does|did|will|would|should|must|can|could|shall|need|needs|have|has|had|got|ought|" +
        "am|is|are|was|were|to)\\b")
    private fun shift(q: String): Pair<Result, Set<String>>? {
        if (!asksWhen.containsMatchIn(q)) return null
        // Another place's clock needs its time zone, which a plain sum leaves out.
        if (Regex("(?i)\\b(utc|gmt|time ?zones?|local time|jet ?lag)\\b").containsMatchIn(q)) return null
        if (travels.containsMatchIn(q) && namesPlace(q)) return null
        val times = clockTime.findAll(q).toList()
        val spans = span.findAll(q).toList()
        if (times.size != 1 || spans.size != 1) return null
        val t = times.single(); val s = spans.single()
        // A second leg the sum would leave out is not guessed at, nor a number the span did not read ("2 hours 5").
        if (otherDuration.containsMatchIn(q.removeRange(s.range))) return null
        val after = q.substring(s.range.last + 1)
        if (Regex("^\\s*(?:and\\s+)?\\d").containsMatchIn(after) || dwell.containsMatchIn(q.substring(0, s.range.first))) return null
        val start = if (t.groupValues[3].isNotEmpty()) {
            val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
            if (h !in 1..12 || m > 59) return null
            ((h % 12) + if (t.groupValues[3].lowercase().startsWith("p")) 12 else 0) * 60 + m
        } else t.groupValues[4].toInt() * 60 + t.groupValues[5].toInt()
        val g = s.groupValues
        val minutes = when {
            g[1].isNotEmpty() -> (num(g[1]) * 60 + g[2].toInt()).roundToInt()
            g[3].isNotEmpty() -> (num(g[3]) * 60 + num(g[4].ifBlank { "0" })).roundToInt()
            else -> num(g[5]).roundToInt()
        }
        if (minutes <= 0 || minutes > 48 * 60) return null
        val before = q.substring(0, t.range.first)
        val starts = startsAt.findAll(before).lastOrNull()?.range?.first ?: -1
        val ends = endsAt.findAll(before).lastOrNull()?.range?.first ?: -1
        val timeStarts = if (starts == ends) null else starts > ends
        // "30 minutes before" counts back from the time and "3 hours later" forward, but "3 hours before
        // sunset" or "before we get there" names another event, which the sum cannot place. Otherwise the
        // time's role decides, and a question whose roles are unclear is left to research.
        val back = relative.find(after)?.let { e ->
            val from = s.range.last + 1 + e.range.last + 1
            val event = namedEvent.find(after.substring(e.range.last + 1))?.groupValues?.get(1)?.lowercase()
            val own = when {
                // "30 minutes early" is early for the time itself.
                e.groupValues[1].startsWith("early", ignoreCase = true) && e.groupValues[2].lowercase() !in setOf("than", "for") -> true
                // "before my 9:40 train": the time belongs to the event named.
                t.range.first >= from && q.substring(from, t.range.first).none { it in ".?!" } -> true
                event == null -> false
                event.isEmpty() || event in setOf("it", "that", "this", "then", "time") -> true
                event in setOf("departure", "take-off", "takeoff") -> timeStarts == true
                else -> Regex("(?i)\\b${Regex.escape(event)}\\b").containsMatchIn(before.split('.', '?', '!').last())
            }
            if (!own) return null
            e.groupValues[1].lowercase() !in setOf("after", "later")
        } ?: run {
            val asks = asked.findAll(q).filterNot { m ->
                m.range.last < t.range.first && !auxiliary.containsMatchIn(m.value) &&
                    q.substring(m.range.last + 1, t.range.first).none { it in ".?!" }
            }.toList()
            val asksStart = asks.any { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() }
            val asksEnd = asks.any { it.groupValues[2].isNotEmpty() || it.groupValues[4].isNotEmpty() }
            when {
                asksStart == asksEnd -> return null
                timeStarts == true && asksEnd -> false
                timeStarts == false && asksStart -> true
                // "The ferry leaves at 7:15 ... When do we leave?": the time is not the trip's own.
                else -> return null
            }
        }
        val end = start + if (back) -minutes else minutes
        val day = Math.floorDiv(end, 24 * 60)
        val at = Math.floorMod(end, 24 * 60)
        fun clock(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        val length = hours(minutes / 60.0)
        val dayNote = when { day > 0 -> " the next day"; day < 0 -> " the day before"; else -> "" }
        val text = "${clock(start)} ${if (back) "minus" else "plus"} $length is **${clock(at)}$dayNote**."
        return Result(text, true, "${clock(start)} ${if (back) "−" else "+"} $length = ${clock(at)}$dayNote.", used = listOf(t.range, s.range)) to emptySet()
    }

    /* ---------- a price per night, day or person, times how many ---------- */
    private val unitPrice = Regex("(?i)(?:($SYMBOLS)\\s*$NUM|$NUM\\s*(?:($SYMBOLS)|((?:$CURRENCIES)s?)\\b)?)\\s*(?:a|an|per|each|every|/)\\s*" +
        "(night|day|person|head|ticket|hour|week|room)\\b")
    private val adjustment = Regex("(?i)$NUM\\s*$PCT\\s*(discount|off|tax|vat|service(?: charge)?|surcharge|fee|commission)|" +
        "(discount|tax|vat|service charge|surcharge|fee|commission)\\s+(?:of\\s+)?$NUM\\s*$PCT")
    // A charge named without the percentage it adds or takes off ("plus tax", "minus a discount") cannot be applied.
    private val charge = Regex("(?i)\\b(?:tax(?:es)?|vat|fees?|service|(?:sur)?charges?|commissions?|discount(?:s|ed)?)\\b")
    private val unitWords = Regex("(?i)\\b(?:\\d+(?:\\.\\d+)?|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+" +
        "(nights?|days?|people|persons?|heads?|tickets?|hours?|weeks?|rooms?|beds?|adults?|children|kids|guests?)\\b")
    // People counted without a unit word: "3 of us", "the two of us", "both of us", "for two", "we are 3",
    // "my son and me".
    private const val COUNT = "(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
    private val headCount = Regex("(?i)\\b(?:$COUNT\\s+of\\s+(?:us|you|them)|both|couple|(?:family|group|party|team)\\s+of\\s+$COUNT|" +
        "we(?:['’]re|\\s+are)\\s+$COUNT|my\\s+(?:wife|husband|partner|(?:boy|girl)?friends?|family|kids?|child(?:ren)?|parents|" +
        "sons?|daughters?|brothers?|sisters?|mum|mom|dad|mother|father)|and\\s+(?:me|i)|" +
        "for\\s+$COUNT\\b(?![.,]\\d|\\s*(?:%|$SYMBOLS))(?!\\s+(?:more|extra|additional|full|whole|percent|per\\s?cent|nights?|days?|" +
        "people|persons?|heads?|tickets?|hours?|weeks?|months?|rooms?|beds?|adults?|children|kids|guests?|(?:$CURRENCIES)s?)\\b))\\b")
    // A second rate on the price ("€18 a night per person", "€15 a day each") multiplies by a count the question
    // may not give.
    private val secondRate = Regex("(?i)(?:\\b(?:per|each|every)|/)\\s*(?:night|day|person|head|ticket|hour|week|room|bed|adult|guest|" +
        "child|kid|car|tent|vehicle)s?\\b|\\b(?:each|apiece|pp)\\b")
    // Anything this sum does not handle leaves the question to research: a tax already included, a
    // second price, another currency.
    private val beyondSum = Regex("(?i)\\b(?:includ\\w*|incl\\.?|inclusive|already|after (?:a |the )?\\d+|after (?:a |the )?(?:discount|tax)|" +
        "convert\\w*|in (?:dollars|euros|pounds|yen|usd|eur|gbp)|exchange rate)\\b")
    private fun perUnit(q: String): Pair<Result, Set<String>>? {
        val p = unitPrice.find(q) ?: return null
        if (beyondSum.containsMatchIn(q)) return null
        val price = num(p.groupValues[2].ifBlank { p.groupValues[3] })
        val currencyName = p.groupValues[1].ifBlank { p.groupValues[4] }.ifBlank { p.groupValues[5] }
        // In "60 euros plus 10 per person" the bare 10 is not the whole price: only a price in money is multiplied.
        if (currencyName.isBlank()) return null
        val unit = p.groupValues[6].lowercase()
        val plural = if (unit == "person") "people" else unit + "s"
        // Every count in the question must be of this unit: "3 people for 4 nights", or "3 of us" and "4
        // nights", is two multipliers.
        val counted = unitWords.findAll(q).filter { it.range.first !in p.range }.toList()
        if (counted.size != 1 || headCount.containsMatchIn(q)) return null
        if (secondRate.findAll(q).any { it.range.first !in p.range }) return null
        val word = counted.single().groupValues[1].lowercase()
        if (word != unit && word != plural && word != unit + "s") return null
        val token = counted.single().value.substringBefore(' ')
        if ('.' in token) return null
        val n = count(token) ?: return null
        // A second amount of money ("plus 2 euros a night") or a percentage this sum cannot place.
        val amounts = Regex("(?i)$SYMBOLS\\s*$NUM|$NUM\\s*(?:$SYMBOLS|(?:$CURRENCIES)s?\\b)").findAll(q).count()
        if (amounts != 1 || price <= 0) return null
        val adjustments = adjustment.findAll(q).take(2).toList()
        if (Regex("(?i)%|per\\s?cent\\b").findAll(q).count() != adjustments.size) return null
        if (charge.findAll(q).any { c -> adjustments.none { c.range.first in it.range } }) return null
        // Every number is the price, the count or a percentage applied: "plus 10 a day" is not dropped.
        val used = listOf(p.range, counted.single().range) + adjustments.map { it.range }
        if (numberToken.findAll(q).any { d -> used.none { d.range.first in it } }) return null
        fun amount(v: Double) = if (currencyName.length == 1) "$currencyName${money(v)}" else "${money(v)} $currencyName"
        var total = price * n
        val text = StringBuilder("${amount(price)} × $n $plural = **${amount(total)}**.")
        for (a in adjustments) {
            val percent = num(a.groupValues[1].ifBlank { a.groupValues[4] })
            val kind = a.groupValues[2].ifBlank { a.groupValues[3] }.lowercase()
            if (percent <= 0 || percent >= 100) return null
            val down = kind == "discount" || kind == "off"
            val after = total * (1 + (if (down) -percent else percent) / 100)
            text.append("\n\nWith the ${fmt(percent)}% ${if (kind == "off") "discount" else kind}: ${amount(total)} × ${fmt(1 + (if (down) -percent else percent) / 100, 2)} = **${amount(after)}**.")
            total = after
        }
        return Result(text.toString(), true, "${amount(price)} × $n $plural = ${amount(total)} in all.", used = used) to emptySet()
    }

    /* ---------- a recipe for more people ---------- */
    private val servings = Regex("(?i)\\bfor\\s+(\\d+|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+(?:people|persons|servings|portions|guests)\\b")
    private val ingredient = Regex("(?i)$NUM\\s*(g|grams?|kg|ml|millilit(?:er|re)s?|l|lit(?:er|re)s?|cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lb|pounds?)\\s+(?:of\\s+)?([a-z]+(?:\\s+[a-z]+)?)")
    private fun recipe(q: String): Pair<Result, Set<String>>? {
        val mentions = servings.findAll(q).toList()
        val m = ingredient.find(q) ?: return null
        if (mentions.size < 2) return null
        // The recipe's own count sits in the clause with the amount or the word "recipe"; the other is
        // the one asked for. "How much rice for 10 people if the recipe for 4 uses 300 g?" is 4 -> 10.
        val clauses = Regex("(?i)[^.?!;]+?(?=[.?!;]|\\bif\\b|\\bwhen\\b|$)").findAll(q).toList()
        fun clauseOf(r: IntRange) = clauses.firstOrNull { r.first in it.range }?.value.orEmpty()
        val isSource = { s: MatchResult -> val c = clauseOf(s.range)
            Regex("(?i)\\b(recipe|serves|feeds|makes|calls for|uses|needs|requires|takes)\\b").containsMatchIn(c) || m.value in c }
        val source = mentions.firstOrNull(isSource) ?: mentions[0]
        val target = mentions.firstOrNull { it !== source && !isSource(it) } ?: mentions.firstOrNull { it !== source } ?: return null
        val from = count(source.groupValues[1]) ?: return null; val to = count(target.groupValues[1]) ?: return null
        if (from <= 0 || from == to) return null
        val amount = num(m.groupValues[1]); val scaled = amount * to / from
        val what = m.groupValues[3].split(' ').takeWhile { it.lowercase() !in setOf("how", "for", "if", "and", "to", "do", "what", "is") }
            .joinToString(" ").ifBlank { m.groupValues[3].substringBefore(' ') }
        val factor = to.toDouble() / from
        val text = "${fmt(amount)} ${m.groupValues[2]} × $to ÷ $from = **${fmt(scaled)} ${m.groupValues[2]} of $what** for $to people.\n\n" +
            "Scale every other ingredient by the same factor (× ${fmt(factor, 2)}); cooking times change less than amounts."
        return Result(text, true, "${fmt(amount)} ${m.groupValues[2]} for $from = ${fmt(scaled)} ${m.groupValues[2]} for $to.",
            used = listOf(source.range, target.range, m.range)) to emptySet()
    }

    /* ---------- where water boils at altitude ---------- */
    private val altitude = Regex("(?i)$NUM\\s*(m|meters?|metres?|ft|feet)\\b(?:\\s+(?:of\\s+)?(?:altitude|elevation|above sea level|high|up))?")
    private fun boiling(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bboil").containsMatchIn(q) || !Regex("(?i)altitude|elevation|above sea level|\\bat\\s+$NUM\\s*(?:m|meters?|metres?|ft|feet)\\b|mountain|high").containsMatchIn(q)) return null
        val a = altitude.find(q) ?: return null
        val metres = num(a.groupValues[1]) * (if (a.groupValues[2].lowercase().startsWith("f")) 0.3048 else 1.0)
        if (metres < 0 || metres > 9000) return null
        // Standard-atmosphere pressure at that height, then the Antoine equation for water (1-100 °C).
        val kpa = 101.325 * (1 - 2.25577e-5 * metres).pow(5.25588)
        val celsius = 1730.63 / (8.07131 - log10(kpa * 7.50062)) - 233.426
        val text = "At ${fmt(metres, 0)} m water boils at about **${fmt(celsius)}°C** (${fmt(celsius * 9 / 5 + 32)}°F) instead of 100°C, " +
            "because the air pressure is about ${fmt(kpa, 0)} kPa instead of 101 kPa.\n\n" +
            "Food in boiling water cooks more slowly at that temperature: pasta, rice and beans need noticeably longer, so taste " +
            "for doneness, keep the lid on, and use a pressure cooker for beans. Boiling still makes water safe to drink; " +
            "above 2,000 m, health agencies advise boiling for 3 minutes instead of 1."
        return Result(text, true, "Water boils at about ${fmt(celsius)}°C at ${fmt(metres, 0)} m.", used = listOf(a.range)) to emptySet()
    }

    /* ---------- unit conversions ---------- */
    private data class Unit(val names: String, val toBase: Double, val kind: String, val label: String)
    private val unitTable = listOf(
        Unit("km|kms|kilometers?|kilometres?", 1000.0, "length", "km"), Unit("mi|miles?", 1609.344, "length", "miles"),
        Unit("m|meters?|metres?", 1.0, "length", "m"), Unit("ft|feet|foot", 0.3048, "length", "ft"),
        Unit("cm|centimeters?|centimetres?", 0.01, "length", "cm"), Unit("in|inch|inches", 0.0254, "length", "in"),
        Unit("kg|kgs|kilograms?|kilos?", 1.0, "mass", "kg"), Unit("lb|lbs|pounds?", 0.45359237, "mass", "lb"),
        Unit("g|grams?", 0.001, "mass", "g"), Unit("oz|ounces?", 0.028349523125, "mass", "oz"),
        Unit("l|liters?|litres?", 1.0, "volume", "L"), Unit("gal|gallons?|us gallons?", 3.785411784, "volume", "US gallons"),
        Unit("km/h|kph|kmh", 1.0, "speed", "km/h"), Unit("mph|miles per hour", 1.609344, "speed", "mph"),
    )
    private fun unitFor(token: String) = unitTable.firstOrNull { Regex("(?i)^(?:${it.names})$").matches(token.trim()) }
    private val feetInches = Regex("(?i)$NUM\\s*(?:ft|feet|foot|')\\s*(?:and\\s+)?$NUM\\s*(?:in|inch|inches|\"|'')(?![a-z])")
    private fun units(q: String): Pair<Result, Set<String>>? {
        // "5 feet 10 inches" is one height: converting only the inches would be wrong.
        feetInches.find(q)?.let { h ->
            val ft = num(h.groupValues[1]); val inches = num(h.groupValues[2])
            val cm = (ft * 12 + inches) * 2.54
            return Result("${fmt(ft, 0)} ft ${fmt(inches)} in is **${fmt(cm, 1)} cm** (${fmt(cm / 100, 2)} m).\n\n" +
                "Working: (${fmt(ft, 0)} × 12 + ${fmt(inches)}) in × 2.54 = ${fmt(cm, 2)} cm.", true,
                "${fmt(ft, 0)} ft ${fmt(inches)} in = ${fmt(cm, 1)} cm.", used = listOf(h.range)) to emptySet()
        }
        // Longest names first, and no match inside a longer unit: "60 mph in km/h" is not "km".
        val names = unitTable.flatMap { it.names.split('|') }.sortedByDescending { it.length }.joinToString("|")
        val m = Regex("(?i)$NUM\\s*($names)(?![\\w/])\\s+(?:is |are )?(?:in|to|into|as|in terms of)\\s+($names)(?![\\w/])").find(q) ?: return null
        val from = unitFor(m.groupValues[2]) ?: return null; val to = unitFor(m.groupValues[3]) ?: return null
        if (from.kind != to.kind || from == to) return null
        val value = num(m.groupValues[1]); val out = value * from.toBase / to.toBase
        return Result("${fmt(value)} ${from.label} is **${fmt(out, 2)} ${to.label}**.", true, "${fmt(value)} ${from.label} = ${fmt(out, 2)} ${to.label}.",
            used = listOf(m.range)) to emptySet()
    }

    /* ---------- percentages ---------- */
    private fun percent(q: String): Pair<Result, Set<String>>? {
        if (rate.containsMatchIn(q)) return null // a tip on a converted bill is handled with the bill
        val m = Regex("(?i)$NUM\\s*%\\s*(?:of|tip on|on)\\s*(?:a\\s+)?[$€£¥]?\\s*$NUM").find(q) ?: return null
        // "70% of 2023 visitors" counts visitors; only an amount, or one in money, is a sum.
        val next = Regex("^\\s*([\\p{L}]+)").find(q.substring(m.range.last + 1))?.groupValues?.get(1)
        if (next != null && !currencyWord.containsMatchIn(next.lowercase()) && next.lowercase() !in setOf("and", "with", "plus", "is", "what", "how")) return null
        val p = num(m.groupValues[1]); val base = num(m.groupValues[2]); val part = base * p / 100
        val tipping = Regex("(?i)tip|service|gratuity").containsMatchIn(q)
        val text = "${fmt(p)}% of ${fmt(base, 2)} is **${fmt(part, 2)}**." + if (tipping) " With the tip, the total is **${fmt(base + part, 2)}**." else ""
        return Result(text, true, "${fmt(p)}% of ${fmt(base, 2)} = ${fmt(part, 2)}.", used = listOf(m.range)) to emptySet()
    }
}
