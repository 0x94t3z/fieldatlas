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
        /** How long the trip worked out takes, stated stops included, so a clock time in the question can be carried to its end. */
        val durationMinutes: Int? = null,
    )

    // A number starts where no digit, comma or dot comes before it: without that, a long run of digits is
    // searched again from every digit in it, which froze the question box on a pasted number.
    private const val NUM = "(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    private fun num(text: String) = text.replace(",", "").toDouble()
    private const val SYMBOLS = "[$€£¥₹]"
    private const val PCT = "(?:%|per\\s?cent\\b)"
    // Every number in the question, thousands separators and decimals included; "6-bed" is not one.
    private val numberToken = Regex("(?<![\\d.,])\\d+(?:[.,]\\d+)*(?![\\d-]|[.,]\\d)")

    // Number forms these calculations cannot read for certain: a decimal comma ("1,5 litres"), thousands
    // split by spaces ("1 000 km") or by dots ("1.200 km"). Money with its currency named is read by
    // exchange(), which knows how each currency is written; anything else is left to research.
    private val spacedThousands = Regex("(?<![\\d.,])\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?![\\d.,])")
    private val commaDecimal = Regex("(?<![\\d.,])\\d+,\\d{1,2}(?![\\d,])")
    private val dotThousands = Regex("(?<![\\d.,])[1-9]\\d{0,2}(?:\\.\\d{3})+(?![\\d.,])")
    // Counts written as words are quantities as much as digits: "two beers at 120 lira each".
    private val quantityWord = Regex("(?i)\\b(?:two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|both|couple|dozen|pair|twice|double|triple|" +
        "half|quarter|quarters|third|thirds)\\b")
    // A range has no single answer ("tip 15-20%", "5-6 hours"); temperatures convert both ends instead.
    private val numberRange = Regex("(?<![\\d.,:/])\\d+(?:\\.\\d+)?\\s*(?:%\\s*)?(?:-|–|—|to)\\s*\\d+(?:\\.\\d+)?(?![\\d:/])(?!\\s*(?:°|degrees?|[cf]\\b|celsius|fahrenheit))")
    // A year, or a number inside a code ("BA117", "gate B12", "T2"), counts nothing.
    // A word with a capital in it ("iPhone 15", "Route 66", "Terminal 2"), or a word that numbers things
    // ("bus number 22", "gate 12", "platform 3", "room 210", "seat 14"), makes the number an identifier.
    private val namedBefore = Regex("(?<![.!?]\\s|^)\\b\\p{L}*\\p{Lu}\\p{L}*\\s$|(?i)\\b(?:number|no\\.?|#|gate|platform|terminal|" +
        "route|line|bus|tram|train|flight|room|seat|row|car|coach|track|pier|exit|level|floor|zone|stand|bay|counter|door)\\s*$")
    // Letters glued after a number are usually its unit ("80m", "10.5km", "3.5l"), which makes it a quantity;
    // a capital ("gate 12B") or an ordinal ("3rd") does not.
    private fun countsNothing(q: String, d: MatchResult): Boolean =
        Regex("^(?:19|20)\\d{2}$").matches(d.value) ||
            namedBefore.containsMatchIn(q.substring(maxOf(0, d.range.first - 20), d.range.first)) ||
            d.range.first > 0 && q[d.range.first - 1].isLetter() ||
            Regex("^(?:\\p{Lu}(?!\\p{L})|st\\b|nd\\b|rd\\b|th\\b)").containsMatchIn(q.substring(d.range.last + 1))

    // Anything beyond the arithmetic ("is it dangerous", "should I") is left to research. "How long should it
    // take" asks for the time, not for advice.
    private val judgement = Regex(
        "(?i)\\b(dangerous|danger|safe|safely|risky|risk|ok(?:ay)?|(?<!long |time )should|recommend|better|worse|why|explain|compare|" +
            "healthy|enough|too (?:hot|cold|much|far|long)|worth|legal|allowed|can i (?:bring|take|carry|pack|use))\\b",
    )

    fun solve(question: String, today: LocalDate = LocalDate.now()): Result? {
        if (spacedThousands.containsMatchIn(question)) return null
        if (commaDecimal.containsMatchIn(question) || dotThousands.containsMatchIn(question))
            return exchange(question)?.let { judged(question, listOf(it to MONEY + EXCHANGE)) }
        // A trip whose length is worked out here: a clock time beside it is its start or end, so the plain
        // time-plus-span sums stand aside and the worked-out length is carried to the clock instead.
        val timed = listOfNotNull(naismith(question)?.let { it to HIKE }, travelTime(question)?.let { it to TRAVEL })
        val chained = timed.singleOrNull()?.let { (r, w) -> chained(question, r.first)?.let { c -> (c to r.second) to (w + SHIFT + BREAKS) } }
        val plainTimes = if (timed.isEmpty()) listOfNotNull(duration(question)?.let { it to DURATION }, shift(question)?.let { it to SHIFT }) else emptyList()
        val families = listOfNotNull(
            fuel(question)?.let { it to FUEL }, fuelRange(question)?.let { it to FUEL + RANGE }, tripFuel(question)?.let { it to FUEL + TRIP }) +
            (chained?.let { listOf(it) } ?: timed) + listOfNotNull(
            (currency(question) ?: exchange(question))?.let { it to MONEY + EXCHANGE }, water(question)?.let { it to WATER },
            (charges(question)?.let { it to CHARGES } ?: battery(question)?.let { it to BATTERY }),
            temperature(question)?.let { it to TEMPERATURE }, (statedClock(question) ?: clock(question, today))?.let { it to CLOCK + UTC_WORDS }, arrival(question)?.let { it to ARRIVAL },
            recipe(question)?.let { it to RECIPE }, boiling(question)?.let { it to BOILING }) + plainTimes + listOfNotNull(
            perUnit(question)?.let { it to PER_UNIT }, provisions(question)?.let { it to PROVISIONS },
            units(question)?.let { it to UNITS }, percent(question)?.let { it to PERCENT })
        return judged(question, families)
    }

    /** The calculations that read the question, judged as a whole answer, a lead, or nothing. */
    private fun judged(question: String, families: List<Pair<Pair<Result, Set<String>>, Set<String>>>): Result? {
        if (families.isEmpty()) return null
        val parts = families.map { it.first }
        val covered = parts.flatMap { it.second }.toSet()
        val leftover = judgement.findAll(question).map { it.value.lowercase() }.filterNot { it in covered }.toList()
        // A number no calculation read ("until the end of day 2" when the water sum took one day) means the
        // sum is not the whole answer.
        val used = parts.flatMap { it.first.used }
        val stray = numberToken.findAll(question).any { d -> used.none { d.range.first in it } && !countsNothing(question, d) } ||
            quantityWord.findAll(question).any { w -> used.none { w.range.first in it } }
        // A quantity no calculation read ("two beers at 120 lira each", "split between the four of us") means
        // the sum shown would be the wrong one: nothing is shown and research answers instead.
        if (stray) return null
        if (numberRange.containsMatchIn(question) && families.none { it.second === TEMPERATURE }) return null
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
        "seven", "eight", "nine", "ten", "eleven", "twelve", "whole", "just", "only", "using", "use", "uses", "used", "rule", "every",
        "value", "values", "result", "answer", "quick", "quickly", "im", "it's", "i'm", "i've", "we're", "they", "their", "he", "she",
        // Negations lose their "'t" when the words are read: "won't", "don't", "isn't".
        "not", "no", "won", "don", "doesn", "didn", "isn", "aren", "wasn", "weren", "haven", "hasn", "wouldn", "shouldn", "couldn")
    private val FUEL = setOf("car", "rental", "vehicle", "camper", "campervan", "motorhome", "truck", "van", "motorbike", "consumption", "economy", "efficiency",
        "fuel", "petrol", "gas", "gasoline", "diesel", "mileage", "liters", "litres", "liter", "litre", "km", "kilometers",
        "kilometres", "miles", "mile", "gallon", "gallons", "us", "uk", "imperial", "mpg")
    private val RANGE = setOf("tank", "holds", "full", "drive", "driving", "trip", "journey", "range", "reach", "go", "travel", "cover", "refuel")
    private val HIKE = setOf("hike", "hiking", "walk", "walking", "trek", "trekking", "trail", "route", "climb", "climbing", "ascent",
        "gain", "elevation", "naismith", "naismith's", "km", "kilometres", "kilometers", "metres", "meters", "ft", "feet", "miles",
        "mile", "hours", "hour", "minutes", "minute", "time", "of", "plus")
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
    /** How many charges a bank gives a phone: only when both capacities and the efficiency were read. */
    private val CHARGES = BATTERY + setOf("i", "phone", "full", "charge", "charges", "charging", "recharge", "recharges", "times",
        "efficient", "efficiency")
    private val TEMPERATURE = setOf("outside", "outdoors", "today", "weather", "temperature", "degrees", "celsius", "fahrenheit",
        "centigrade")
    private val CLOCK = setOf("time", "when", "now", "am", "pm", "there", "here", "o'clock", "difference")
    private val UTC_WORDS = setOf("utc", "gmt", "that", "is", "in", "and", "set", "for", "call", "meeting", "video")
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
        "am", "pm", "set", "off", "back", "return", "ready", "doors", "open", "opens", "day", "which", "night", "on")
    private val PER_UNIT = setOf("night", "nights", "day", "days", "person", "people", "head", "ticket", "tickets", "hour", "hours",
        "week", "weeks", "room", "rooms", "costs", "cost", "price", "pay", "discount", "off", "tax", "vat", "service", "charge",
        "surcharge", "fee", "commission", "hostel", "hotel", "guesthouse", "dorm", "bed", "apartment", "rental", "car", "bike",
        "scooter", "tour", "class", "lesson", "stay", "staying", "book", "booking", "euros", "euro", "dollars", "dollar", "plus",
        "minus", "applied", "spend", "spent", "altogether", "overall", "it", "us", "of", "percent", "cent", "city", "tourist", "resort",
        "cleaning", "levy", "supplement", "tent", "tents", "pitch", "campsite", "camping", "added", "then", "rate", "discounted", "price",
        "total", "get", "we", "our", "for", "and", "the", "a", "an", "in", "with", "is", "what", "how", "much", "two", "three", "four")
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
        "much", "many", "how", "if", "my", "i",
        // A second offer set against the first: "how much more at the bank", "which is the better deal".
        "more", "extra", "difference", "which", "versus", "vs", "town", "street", "city", "other", "second", "deal", "option",
        "instead", "compared", "would", "save", "saving", "savings", "cheaper", "or")
    // Stops stated along a trip whose length is worked out: "two 10-minute stops", "stop 45 minutes for lunch".
    private val BREAKS = setOf("stop", "stops", "stopping", "stopped", "break", "breaks", "rest", "rests", "resting", "pause", "pauses",
        "lunch", "breakfast", "dinner", "coffee", "snack", "picnic", "halt", "halts", "minute", "minutes", "min", "mins", "hour", "hours",
        "plus", "while", "moving", "including", "with", "and", "if", "start", "starts", "starting", "set", "off", "leave", "leaves",
        "leaving", "train", "bus", "car", "trip", "ride", "drive", "get", "in", "on", "we", "arrive", "finish", "end", "there", "back",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "day", "averages", "average", "speed")
    private val PROVISIONS = setOf("people", "person", "persons", "mornings", "morning", "days", "day", "evenings", "evening", "nights",
        "night", "breakfast", "breakfasts", "dinner", "dinners", "lunch", "lunches", "meals", "meal", "everyone", "everybody", "each",
        "every", "boxes", "box", "bags", "bag", "packs", "pack", "packets", "packet", "cartons", "carton", "bottles", "bottle", "cans",
        "can", "tins", "tin", "jars", "jar", "loaves", "loaf", "trays", "tray", "crates", "crate", "rolls", "roll", "tubs", "tub",
        "buy", "bring", "need", "cooking", "cook", "cooks", "group", "trip", "camp", "camping", "hut", "hike", "trek", "come", "comes",
        "sold", "makes", "make", "has", "have", "eats", "eat", "drinks", "drink", "of", "in", "enough", "supplies", "provisions",
        "shopping", "shop", "food", "groceries", "total", "altogether", "all", "many", "much", "do", "we", "us", "for", "and", "the",
        "how", "take", "carry", "want", "wants", "gets", "get", "uses", "use", "goes", "through", "per", "head", "apiece", "weekend",
        "week", "our", "my", "friends", "kids", "children", "adults", "hikers", "guests", "campers", "us")
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

    /* ---------- clock times, spans and the day they fall on ---------- */
    /** Minutes since midnight for a [clockTime] match, or null for an impossible "13 pm". */
    private fun minutesOf(t: MatchResult): Int? = if (t.groupValues[3].isNotEmpty()) {
        val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
        if (h !in 1..12 || m > 59) null else ((h % 12) + if (t.groupValues[3].lowercase().startsWith("p")) 12 else 0) * 60 + m
    } else t.groupValues[4].toInt() * 60 + t.groupValues[5].toInt()
    private fun clockOf(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    /** Minutes in a [span] match: "2 h 35", "2 hours and 35 minutes", "30 minutes". */
    private fun spanMinutes(s: MatchResult): Int {
        val g = s.groupValues
        return when {
            g[1].isNotEmpty() -> (num(g[1]) * 60 + g[2].toInt()).roundToInt()
            g[3].isNotEmpty() -> (num(g[3]) * 60 + num(g[4].ifBlank { "0" })).roundToInt()
            else -> num(g[5]).roundToInt()
        }
    }
    private val weekdays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val weekdayAt = Regex("(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
    /** The weekday named in the clause that holds the clock time ("leaves at 10:00 on Tuesday"), so the answer can name its day. */
    private fun weekdayOf(q: String, time: IntRange): Int? {
        val clause = clauses.findAll(q).firstOrNull { time.first in it.range } ?: return null
        return weekdayAt.findAll(clause.value).map { weekdays.indexOf(it.groupValues[1].lowercase()) }.distinct().singleOrNull()
    }
    /** " the next day", or " on Wednesday" when the start's weekday is known: how far from the start's day the result falls. */
    private fun dayLabel(days: Int, weekday: Int?): String = when {
        weekday != null -> " on ${weekdays[Math.floorMod(weekday + days, 7)].replaceFirstChar(Char::uppercaseChar)}"
        days == 1 -> " the next day"; days > 1 -> " $days days later"; days == -1 -> " the day before"; days < -1 -> " ${-days} days before"
        else -> ""
    }
    // A span is a stop when a break word sits near it in its clause: "two 10-minute stops", "stop 45 minutes for
    // lunch", "a 20-minute break". "Then a 30-minute bus ride" is another leg, not a stop.
    private val breakWord = Regex("(?i)\\b(?:stops?|stopping|stopped|breaks?|rests?|resting|pauses?|lunch|breakfast|dinner|coffee|snack|picnic|halts?)\\b")
    private val breakCount = Regex("(?i)\\b(?:(two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s+|(a|an|one)\\s+)$")
    /** Each stop the question states, as the text it was read from (count included) and its minutes. */
    private fun breaks(q: String): List<Pair<IntRange, Int>> = span.findAll(q).mapNotNull { s ->
        val clause = clauses.findAll(q).firstOrNull { s.range.first in it.range } ?: return@mapNotNull null
        val from = maxOf(clause.range.first, s.range.first - 30); val to = minOf(clause.range.last, s.range.last + 30)
        if (!breakWord.containsMatchIn(q.substring(from, to + 1))) return@mapNotNull null
        val prefix = breakCount.find(q.substring(clause.range.first, s.range.first))
        val twice = Regex("(?i)\\btwice\\b").find(clause.value)
        val times = prefix?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.let { count(it) } ?: if (twice != null) 2 else 1
        val start = minOf(prefix?.let { clause.range.first + it.range.first } ?: s.range.first,
            if (prefix == null && twice != null) clause.range.first + twice.range.first else s.range.first)
        (start..s.range.last) to spanMinutes(s) * times
    }.toList()
    private fun stopsNote(stops: List<Pair<IntRange, Int>>): String {
        val total = stops.sumOf { it.second }
        val detail = if (stops.size == 1) "" else " (${stops.joinToString(" + ") { hours(it.second / 60.0) }})"
        return "${hours(total / 60.0)} of stops$detail"
    }
    // The clock time's role, from the role word nearest before it in its clause.
    private val chainStart = Regex("(?i)\\b(?:start\\w*|leav\\w+|depart\\w*|set(?:s|ting)? off|begin\\w*|head\\w* (?:out|off))\\b")
    private val chainEnd = Regex("(?i)\\b(?:arriv\\w+|finish\\w*|end|ends|reach\\w*|be (?:back|there|home|at)|get (?:back|there|home|in)|by|before|deadline|close\\w*)\\b")
    /**
     * A trip whose length was worked out, carried to the one clock time the question gives: "leaves at 8:15 ...
     * what time do we get in?" adds the length to a start, and "must be back by 17:00 ... when do we start?"
     * counts it back from an end. Every span must be in the working (a stated stop, a rule's "1 hour per 5 km").
     */
    private fun chained(q: String, base: Result): Result? {
        val minutes = base.durationMinutes ?: return null
        if (!asksWhen.containsMatchIn(q)) return null
        val t = clockTime.findAll(q).singleOrNull() ?: return null
        if (span.findAll(q).any { s -> base.used.none { s.range.first in it } }) return null
        val start = minutesOf(t) ?: return null
        // The time's role from the nearest role word before it in its clause: "we start the hike ... at 8:00"
        // is a start, "must be back at the car by 17:00" an end.
        val clauseStart = clauses.findAll(q).firstOrNull { t.range.first in it.range }?.range?.first ?: 0
        val before = q.substring(clauseStart, t.range.first)
        val starts = chainStart.findAll(before).lastOrNull()?.range?.first ?: -1
        val ends = chainEnd.findAll(before).lastOrNull()?.range?.first ?: -1
        val asks = asked.findAll(q).toList()
        val asksStart = asks.any { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() }
        val asksEnd = asks.any { it.groupValues[2].isNotEmpty() || it.groupValues[4].isNotEmpty() }
        val back = when {
            starts > ends && asksEnd && !asksStart -> false
            ends > starts && asksStart && !asksEnd -> true
            else -> return null
        }
        val end = start + if (back) -minutes else minutes
        val day = Math.floorDiv(end, 24 * 60); val at = Math.floorMod(end, 24 * 60)
        val label = dayLabel(day, weekdayOf(q, t.range))
        val length = hours(minutes / 60.0)
        // Without stated stops the length carried is the moving time; the base text's allowance for breaks is an estimate.
        val moving = if (base.text.contains("10-minute breaks each hour")) " without stops" else ""
        val line = if (back) "To finish by ${clockOf(start)}, start by **${clockOf(at)}$label**: ${clockOf(start)} minus $length$moving."
            else "${clockOf(start)} plus $length is **${clockOf(at)}$label**$moving."
        return base.copy(text = "${base.text}\n\n$line", facts = "${base.facts} ${clockOf(start)} ${if (back) "−" else "+"} $length = ${clockOf(at)}$label.",
            used = base.used + listOf(t.range))
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
            val imperial = Regex("(?i)imperial|\\buk\\b|british").containsMatchIn(m.value + q.substring(m.range.last + 1).take(14))
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
    // The rule's own constants when the question states them: "1 hour per 5 km plus 1 hour per 600 m of ascent",
    // "1 hour for every 4 km and 1 hour for every 450 m", "30 minutes per 300 m of climb".
    private const val RULE_TIME = "((?:1|one|an?)\\s*(?:hours?|hrs?|h)|$NUM\\s*(?:hours?|hrs?|h|minutes?|mins?))"
    private const val RULE_PER = "\\s+(?:per|for\\s+(?:every|each)|every|each|for)\\s+"
    private val ruleDistance = Regex("(?i)\\b$RULE_TIME$RULE_PER$NUM\\s*(km|kilometres?|kilometers?|mi|miles?)\\b")
    private val ruleClimb = Regex("(?i)\\b$RULE_TIME$RULE_PER$NUM\\s*(m|metres?|meters?|ft|feet)\\b" +
        "(?:\\s+(?:of\\s+)?(?:total\\s+)?(?:ascent|climb(?:ing)?|elevation(?:\\s+gain)?|gain|height|up|uphill|vertical))?")
    private fun ruleMinutes(m: MatchResult): Double {
        val phrase = m.groupValues[1]
        val value = m.groupValues[2].ifBlank { "1" }.let(::num)
        return if (Regex("(?i)min").containsMatchIn(phrase)) value else value * 60
    }
    private fun naismith(q: String): Pair<Result, Set<String>>? {
        val hiking = Regex("(?i)\\b(naismith|hike|hiking|walk|walking|trek|trail)\\b").containsMatchIn(q) &&
            (Regex("(?i)\\bhow long|how many hours|how much time|naismith|time (?:will|would|does|do) (?:it|i|we) (?:take|need)").containsMatchIn(q) ||
                // "We start at 7:30; when do we finish?" asks for the walk's length too.
                (asksWhen.containsMatchIn(q) && clockTime.containsMatchIn(q)))
        // "I walk at 5 km/h": a stated speed is used as given (travelTime), not Naismith's 5 km/h.
        if (!hiking || speed.containsMatchIn(q)) return null
        val ruleD = ruleDistance.find(q); val ruleC = ruleClimb.find(q)
        val ruleRanges = listOfNotNull(ruleD?.range, ruleC?.range)
        // The rule's "5 km" and "600 m of ascent" are its constants, not the walk.
        val d = distanceOnly.findAll(q).firstOrNull { m -> ruleRanges.none { m.range.first in it } } ?: return null
        val km = num(d.groupValues[1]) * (if (d.groupValues[2].lowercase().startsWith("mi")) 1.609344 else 1.0)
        val up = (ascent.findAll(q) + ascentAfter.findAll(q)).firstOrNull { m -> ruleRanges.none { m.range.first in it } }
        // "11 km, it's pretty hilly": the rule needs the climb, and a flat-ground time would be too short.
        if (up == null && Regex("(?i)\\b(?:hilly|hills?|steep\\w*|mountain\\w*|uphill|climb\\w*|ascents?|elevation|up and down|undulating|scrambl\\w*)\\b").containsMatchIn(q)) return null
        val metres = up?.let { num(it.groupValues[1]) * (if (it.groupValues[2].lowercase().startsWith("f")) 0.3048 else 1.0) } ?: 0.0
        val kmPerHour = ruleD?.let { num(it.groupValues[3]) * (if (it.groupValues[4].lowercase().startsWith("mi")) 1.609344 else 1.0) / (ruleMinutes(it) / 60) } ?: 5.0
        val climbPerHour = ruleC?.let { num(it.groupValues[3]) * (if (it.groupValues[4].lowercase().startsWith("f")) 0.3048 else 1.0) / (ruleMinutes(it) / 60) } ?: 600.0
        if (km <= 0 || kmPerHour <= 0 || climbPerHour <= 0) return null
        val walk = km / kmPerHour; val climb = metres / climbPerHour
        val stops = breaks(q)
        val stopMinutes = stops.sumOf { it.second }
        val total = ((walk + climb) * 60).roundToInt() + stopMinutes
        val text = buildString {
            append("About **${hours(walk + climb)}** of moving time by Naismith's rule (1 hour per ${fmt(kmPerHour)} km, plus 1 hour per ${fmt(climbPerHour, 0)} m of ascent).\n\n")
            append("Working: ${fmt(km)} km ÷ ${fmt(kmPerHour)} km/h = ${hours(walk)}")
            if (metres > 0) append(", plus ${fmt(metres, 0)} m ÷ ${fmt(climbPerHour, 0)} m/h = ${hours(climb)}")
            append(".\n\n")
            append(if (stops.isEmpty()) "Add time for breaks. " else "With ${stopsNote(stops)}: **${hours(total / 60.0)}**. ")
            append("The rule assumes a reasonably fit walker on good paths; steep descents, rough ground, heavy packs and heat all slow you down.")
        }
        val facts = "Naismith time = ${hours(walk + climb)}" + (if (stops.isEmpty()) "." else ", ${hours(total / 60.0)} with stops.")
        val heights = altitude.findAll(q).filter { m -> Regex("(?i)altitude|elevation|above sea level").containsMatchIn(m.value) &&
            (up == null || m.range.first !in up.range) }.map { it.range }.toList()
        return Result(text, true, facts, used = listOfNotNull(d.range, up?.range) + ruleRanges + stops.map { it.first } + heights,
            durationMinutes = total) to emptySet()
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
    private const val LOOSE = "(?<![\\d.,])(\\d(?:[\\d.,]*\\d)?)"
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
    // Money whose rate against a major currency runs into the thousands ("25.400 dong to the dollar"),
    // and money named the same in countries whose rates run from under 1 to over 1,000 (the Mexican peso
    // is about 18, the Colombian about 4,000), where a rate like "18.245" cannot be read either way.
    private val thousandsRate = setOf("dong", "rupiah", "won", "riel", "kip", "guarani", "sum")
    // Names shared by currencies on very different scales: the Kyrgyz som is about 87 to the dollar, the Uzbek
    // som about 12,000; the Iranian rial tens of thousands, the Omani rial 0.385 and the Yemeni about 250.
    private val eitherRate = setOf("peso", "shilling", "dinar", "franc", "lira", "pound", "rupee", "colon", "som", "rial")
    private fun parseAmount(text: String, currency: String, isRate: Boolean = false): Double? {
        val dots = text.count { it == '.' }; val commas = text.count { it == ',' }
        if (dots == 0 && commas == 0) return text.toDouble()
        // "0.921" is never a thousands figure.
        if (text.startsWith("0.") && dots == 1 && commas == 0) return text.toDouble()
        // A rate is often quoted to three or four places ("18.245 pesos"); an amount of money never is.
        if (isRate && dots == 1 && commas == 0 && text.substringAfter('.').length == 3) return when (canon(currency)) {
            in thousandsRate -> text.replace(".", "").toDouble()
            in eitherRate -> null
            else -> text.toDouble()
        }
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
    // The fee taken off is the money changer's: a hotel's "10% fee" in another sentence is not it.
    private val changing = Regex("(?i)\\b(?:exchange|office|bureau|kiosk|booth|counter|bank|atm|changer|desk|card|app|rate|commission|convert\\w*|chang\\w+|money|cash|currency|gives?|offers?|quotes?)\\b")
    // Who makes an offer, for naming it: "the airport kiosk", "my bank in town".
    private const val PLACE_WORD = "(?:exchange|post|kiosk|booth|counter|bank|atm|airport|hotel|office|bureau|changer|desk|app|card)"
    private val offerPlace = Regex("(?i)\\b((?:$PLACE_WORD\\s+)*$PLACE_WORD)\\b")
    // A second rate given bare: "my bank in town gives 0.86 with no fee", "bank 0.86 no fee". Not a percentage, not
    // an amount of money, and said to be what the place gives, so "opens at 9" is not a rate.
    private val bareNumber = Regex("(?<![\\d.,])\\d(?:[\\d.,]*\\d)?(?!\\d|[.,]\\d|\\s*(?:%|per\\s?cent|am|pm|a\\.m|p\\.m|o'clock|:\\d))")
    private val givesRate = Regex("(?i)(?:\\b(?:gives?|offers?|quotes?|pays?|does|rate(?:\\s+(?:is|of))?|is|=)\\s+(?:(?:you|me|us|only|about|around|just|a\\s+rate\\s+of)\\s+)*|\\b$PLACE_WORD\\s+)$")
    private val comparisonAsk = Regex("(?i)\\bhow much (?:more|less|extra|better|worse)\\b|\\bdifference\\b|\\bwhich (?:\\w+\\s+){0,3}?(?:better|best|more|most|cheaper|worse|worst)\\b|" +
        "\\bbetter (?:deal|rate|offer|option|off)\\b|\\b(?:save|saves|saving)\\b|\\bcheaper\\b|\\bwhich (?:should|do|would) (?:i|we)\\b|\\b(?:is|are) (?:it|the \\w+) (?:worth|better)\\b")
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
        val perBase = parseAmount(if (per != null) per.groupValues[1] else eq!!.groupValues[2], local, isRate = true) ?: return null
        if (perBase <= 0 || canon(base) == canon(local)) return null
        // The amount changed sits outside the rate and is in one of its two currencies; a fee's money is not it.
        val flatFees = flatFee.findAll(q).toList()
        val pctFees = pctFee.findAll(q).toList()
        var amounts = Regex("(?i)(?<![\\d.,])$AMOUNT").findAll(q)
            .filter { it.range.first !in r.range && it.range.last !in r.range && flatFees.none { f -> it.range.first in f.range } }.toList()
        val symbolCurrency = mapOf("$" to "dollar", "€" to "euro", "£" to "pound", "¥" to "yen", "₹" to "rupee")
        fun clauseOf(at: Int) = clauses.findAll(q).firstOrNull { at in it.range }
        // "The bank offers 4.1 zloty": a second rate named with its money is an offer, not the amount changed, when
        // it is in the money received, near the quoted rate and in a clause about changing money.
        val offered = amounts.filter { m ->
            val g = m.groupValues
            val cur = symbolCurrency[g[1].ifBlank { g[4] }] ?: g[5]
            canon(cur) == canon(local) && changing.containsMatchIn(clauseOf(m.range.first)?.value.orEmpty()) &&
                parseAmount(g[2].ifBlank { g[3] }, cur, isRate = true)?.let { it >= perBase * 0.2 && it <= perBase * 5 } == true
        }
        val offeredRate = if (amounts.size == 2 && offered.size == 1) offered.single() else null
        if (offeredRate != null) amounts = amounts.filter { it !== offeredRate }
        if (amounts.size != 1) return null
        val a = amounts.single().groupValues
        val amountCurrency = symbolCurrency[a[1].ifBlank { a[4] }] ?: a[5].ifBlank { return null }
        val amount = parseAmount(a[2].ifBlank { a[3] }, amountCurrency) ?: return null
        if (amount <= 0) return null
        val forward = when (canon(amountCurrency)) { canon(base) -> true; canon(local) -> false; else -> return null }
        // A second offer: one bare number in a clause with exchange words, within 0.2-5 times the quoted rate so a
        // head count or a year is not read as a rate. Two such numbers leave the question to research.
        val taken = listOf(r.range, amounts.single().range) + flatFees.map { it.range } + pctFees.map { it.range } + listOfNotNull(offeredRate?.range)
        val second = bareNumber.findAll(q).filter { n -> taken.none { n.range.first in it } && changing.containsMatchIn(clauseOf(n.range.first)?.value.orEmpty()) &&
                givesRate.containsMatchIn(q.substring(0, n.range.first)) }
            .mapNotNull { n -> parseAmount(n.value, local, isRate = true)?.takeIf { it >= perBase * 0.2 && it <= perBase * 5 }?.let { n.range to it } }.toList() +
            listOfNotNull(offeredRate?.let { m -> parseAmount(m.groupValues[2].ifBlank { m.groupValues[3] }, local, isRate = true)?.let { m.range to it } })
        if (second.size > 1) return null
        val other = second.singleOrNull()
        // Each fee belongs to the rate nearest before it in its sentence; a fee in a sentence with no rate is the
        // changer's only if that sentence speaks of changing money.
        val rates = listOfNotNull(r.range.first to 0, other?.let { it.first.first to 1 })
        fun ownerOf(f: IntRange): Int? {
            val sentence = clauseOf(f.first) ?: return null
            val here = rates.filter { it.first in sentence.range }
            if (here.isEmpty()) return if (changing.containsMatchIn(sentence.value) || Regex("(?i)commission").containsMatchIn(q.substring(f))) 0 else null
            return (here.filter { it.first < f.first }.maxByOrNull { it.first } ?: here.minBy { it.first }).second
        }
        val owners = (pctFees.map { it.range } + flatFees.map { it.range }).associateWith { ownerOf(it) ?: return null }
        val waived = noFee.findAll(q).map { it.range }.toList()
        val feeMentions = feeWord.findAll(q).filter { f -> waived.none { f.range.first in it } }.toList()
        // Every fee named must have its size.
        if (feeMentions.any { f -> owners.keys.none { f.range.first in it } }) return null
        val baseName = plural(base.replace(Regex("(?i)^u\\.?s\\.?(?=\\s)"), "US"))
        val localPlural = plural(localName)
        fun inBase(v: Double) = "${money(v)} $baseName"
        fun inLocal(v: Double) = "${money(v)} $localPlural"
        fun out(v: Double) = if (forward) inLocal(v) else inBase(v)
        val used = mutableListOf(r.range, amounts.single().range)
        /** What one offer nets, and its working: the rate, then the one fee it names taken off. Null where the fee cannot be placed. */
        fun offer(rate: Double, pct: MatchResult?, flat: MatchResult?, lead: String): Pair<Double, String>? {
            // Only one kind of fee at a time: how a percentage and a flat fee combine is the office's rule, not arithmetic.
            if (pct != null && flat != null) return null
            val before = if (forward) amount * rate else amount / rate
            val text = StringBuilder(lead)
            text.append(if (forward) "${fmt(amount, 2)} $baseName × ${fmt(rate, 4)} = **${inLocal(before)}**"
                else "${fmt(amount, 2)} $localPlural ÷ ${fmt(rate, 4)} = **${inBase(before)}**")
            var after = before
            if (pct != null) {
                used += pct.range
                val p = num(pct.groupValues[1].ifBlank { pct.groupValues[2] }.ifBlank { pct.groupValues[3] })
                if (p <= 0 || p >= 100) return null
                after = before * (1 - p / 100)
                text.append(" before the commission.\n\nAfter the ${fmt(p)}% commission: ${money(before)} × ${fmt(1 - p / 100, 2)} = **${out(after)}**.")
            } else if (flat != null) {
                used += flat.range
                val fa = Regex("(?i)$AMOUNT").find(flat.value)?.groupValues ?: return null
                val feeCurrency = symbolCurrency[fa[1].ifBlank { fa[4] }] ?: fa[5].ifBlank { return null }
                val fee = parseAmount(fa[2].ifBlank { fa[3] }, feeCurrency) ?: return null
                // A fee in the money handed over comes off before the rate; one in the money received, after.
                val feeInSource = when (canon(feeCurrency)) { canon(amountCurrency) -> true; canon(if (forward) local else base) -> false; else -> return null }
                if (fee <= 0 || (feeInSource && fee >= amount) || (!feeInSource && fee >= before)) return null
                text.append(" before the fee.\n\n")
                if (feeInSource) {
                    after = if (forward) (amount - fee) * rate else (amount - fee) / rate
                    text.append("Less the ${money(fee)} ${if (forward) baseName else localPlural} fee first: ${fmt(amount - fee, 2)} ${if (forward) "×" else "÷"} ${fmt(rate, 4)} = **${out(after)}**.")
                } else {
                    after = before - fee
                    text.append("Less the ${money(fee)} ${if (forward) localPlural else baseName} fee: ${money(before)} − ${money(fee)} = **${out(after)}**.")
                }
            } else text.append(".")
            return after to text.toString()
        }
        // Two fees of one kind on one offer cannot both be its fee.
        for (owner in 0..1) if (pctFees.count { owners[it.range] == owner } > 1 || flatFees.count { owners[it.range] == owner } > 1) return null
        fun feesOf(owner: Int) = pctFees.singleOrNull { owners[it.range] == owner } to flatFees.singleOrNull { owners[it.range] == owner }
        val (firstPct, firstFlat) = feesOf(0)
        val (net, firstText) = offer(perBase, firstPct, firstFlat, "") ?: return null
        val text = StringBuilder(firstText)
        var facts = "${fmt(amount, 2)} ${if (forward) baseName else localPlural} = ${out(net)}" + (if (firstPct != null || firstFlat != null) " after the fee" else "") + "."
        if (other != null) {
            used += other.first
            val (secondPct, secondFlat) = feesOf(1)
            // The place named nearest before the rate: "the airport kiosk gives 0.84", "my bank in town gives 0.86".
            fun placeOf(at: Int, fallback: String) = clauseOf(at)?.let { c ->
                val found = offerPlace.findAll(c.value).toList()
                (found.lastOrNull { c.range.first + it.range.first < at } ?: found.firstOrNull())?.groupValues?.get(1)?.lowercase()
            } ?: fallback
            val firstPlace = placeOf(r.range.first, "first offer"); val secondPlace = placeOf(other.first.first, "second offer")
            val secondWaived = clauseOf(other.first.first)?.let { c -> noFee.containsMatchIn(c.value) } ?: false
            val lead = "\n\nAt the $secondPlace's ${fmt(other.second, 4)}${if (secondPct == null && secondFlat == null && secondWaived) " with no fee" else ""}: "
            val (otherNet, secondText) = offer(other.second, secondPct, secondFlat, lead) ?: return null
            text.append(secondText)
            val gap = otherNet - net
            val name = if (forward) localPlural else baseName
            text.append("\n\n")
            text.append(when {
                gap > 0.005 -> "The $secondPlace gives **${money(gap)} $name more** than the $firstPlace (${money(otherNet)} against ${money(net)})."
                gap < -0.005 -> "The $firstPlace gives **${money(-gap)} $name more** than the $secondPlace (${money(net)} against ${money(otherNet)})."
                else -> "Both come to the same: ${money(net)} $name."
            })
            facts = "${fmt(amount, 2)} ${if (forward) baseName else localPlural} = ${out(net)} at the $firstPlace, ${out(otherNet)} at the $secondPlace" +
                (if (kotlin.math.abs(gap) > 0.005) ": ${money(kotlin.math.abs(gap))} $name more at the ${if (gap > 0) secondPlace else firstPlace}." else ".")
        }
        text.append("\n\nThis uses the rate${if (other != null) "s" else ""} you gave; check the office's buy rate and any minimum fee before handing over the cash.")
        // Which offer is the better one is answered by the comparison; the words that ask it are covered.
        val covered = if (other != null && comparisonAsk.containsMatchIn(q)) setOf("better", "worse", "should", "compare") else emptySet()
        return Result(text.toString(), true, facts, used = used) to covered
    }

    /* ---------- water for a group ---------- */
    private val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "a" to 1, "an" to 1)
    private fun count(token: String): Int? = token.lowercase().toIntOrNull() ?: words[token.lowercase()]
    private val ordinals = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7,
        "eighth" to 8, "ninth" to 9, "tenth" to 10)
    private const val N = "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
    private val groupSize = listOf(
        Regex("(?i)\\b$N\\s+(?:of us|people|persons|hikers|adults|travell?ers|friends|walkers|climbers|campers|guests|students|scouts|cyclists|runners|kids|children|teenagers)\\b"),
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
        if (!asksWhen.containsMatchIn(q) && !Regex("(?i)\\btime (?:is it|will it be|there)\\b").containsMatchIn(q)) return null
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
        val fromPlace = Regex("\\b[Ff]rom\\s+$PLACE\\s*(?:\\([^)]*\\)\\s*)?,?\\s*(?:at\\s+)?$").find(before)?.range?.first ?: -1
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
        // "Leaves at 10:00 on Tuesday": the day named with the time is carried across the zones to the landing.
        val dayNote = dayLabel(day, weekdayOf(q, t.range))
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

    /**
     * "A 10,000 mAh power bank, a 4,500 mAh phone, 65% efficient: how many full charges?" The model
     * once multiplied the two capacities. Usable capacity is the bank's times the efficiency the
     * question states (losses in conversion and heat), divided by the phone's.
     */
    private val bankWords = Regex("(?i)\\b(?:power ?banks?|battery (?:pack|bank)s?|portable (?:charger|battery)|charger|pack|bank|anker|powercore)\\b")
    private val phoneWords = Regex("(?i)\\b(?:\\w*phones?|handset|mobile|tablet|ipad|device|watch|earbuds|camera|laptop|galaxy|pixel)\\b")
    /** The battery word nearest a capacity, and how far away it is: the bank is not always the bigger one. */
    private fun batteryRole(q: String, range: IntRange): Pair<String, Int>? {
        fun distance(m: MatchResult) = minOf(kotlin.math.abs(m.range.first - range.last), kotlin.math.abs(range.first - m.range.last))
        val bank = bankWords.findAll(q).minOfOrNull(::distance)
        val phone = phoneWords.findAll(q).minOfOrNull(::distance)
        return when {
            bank != null && (phone == null || bank < phone) && bank <= 45 -> "bank" to bank
            phone != null && (bank == null || phone < bank) && phone <= 45 -> "phone" to phone
            else -> null
        }
    }

    // The efficiency is the percentage bound to its word: "80% efficient", "efficiency of 80%". A loss
    // is its complement: "35% is lost", "30% conversion loss". "My phone is at 40%" is neither.
    private val efficiencyLoss = Regex("(?i)$NUM\\s*%\\s*(?:efficiency|conversion|energy|power|charging)\\s+loss(?:es)?\\b")
    private val efficiencyPct = Regex("(?i)$NUM\\s*%[\\s-]*(?:charging\\s+|conversion\\s+)?efficien\\w*|" +
        "\\befficien\\w*(?:\\s+rating)?\\s*[~=(:]?\\s*(?:(?:is|of|at|about|around|only|roughly|just|rated)\\s+)*$NUM\\s*%")
    // A loss counts only as a charging loss: "35% is lost to heat", "a 30% loss". "My phone loses 30%
    // overnight" is the phone's drain, not the bank's efficiency.
    private const val LOSS_CAUSE = "(?:to|in|as|during)\\s+(?:\\w+\\s+){0,2}(?:heat|conversion|charging|transfer|cables?|wiring)"
    private val lossPct = Regex("(?i)$NUM\\s*%\\s*(?:of (?:it|the (?:charge|energy|power))\\s+)?(?:(?:is|are|gets?)\\s+)?(?:lost|wasted)\\s+$LOSS_CAUSE|" +
        "\\b(?:loses?|losing|wastes?|wasted)\\s+(?:(?:about|around|roughly)\\s+)?$NUM\\s*%\\s*$LOSS_CAUSE")
    // What this shortcut does not model, where a number would mislead: a bank only partly charged,
    // several banks, a top-up between two levels, a second loss on top of the efficiency, a range.
    private val chargeConditions = Regex("(?i)\\b(?:two|three|four|2|3|4|both|pair of)\\s+(?:\\S+\\s+){0,3}(?:power ?banks?|banks?)\\b|" +
        "\\b(?:only|half|partly|partially)\\s+(?:\\S+\\s+)?charged\\b|$NUM\\s*(?:%|percent)\\s*(?:charged|full)\\b|" +
        "\\bfrom\\s+$NUM\\s*(?:%|percent)?\\s*(?:to|-|–)\\s*$NUM|\\btop(?:ping)?[- ]?ups?\\b|\\bwireless\\b|" +
        "\\b(?:another|an extra|additional)\\s+$NUM\\s*%|\\b(?:cable|wire|adapter)\\s+(?:also\\s+)?(?:loses?|wastes?)\\b|" +
        "$NUM\\s*%\\s*(?:to|-|–)\\s*$NUM\\s*%")

    private fun charges(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bhow many\\b[^.?!]{0,30}\\b(?:charges?|recharges?|times)\\b|\\bhow many times can i (?:re)?charge\\b").containsMatchIn(q)) return null
        // Stated voltages or watt-hours need an energy calculation, which this shortcut does not do.
        if (Regex("(?i)$NUM\\s*(?:v|volts?|wh|watt[- ]?hours?)\\b").containsMatchIn(q)) return null
        // A top-up "from 20%" is not a full charge; this shortcut counts full charges only.
        if (Regex("(?i)\\bfrom\\s+$NUM\\s*%").containsMatchIn(q) || chargeConditions.containsMatchIn(q)) return null
        val capacities = Regex("(?i)$NUM\\s*mah\\b").findAll(q).toList()
        if (capacities.size != 2) return null
        // "4.500 mAh" and "4 500 mAh" write thousands in other ways; no phone or bank holds under 300 mAh.
        if (capacities.any { m -> num(m.groupValues[1]) < 300 || m.range.first >= 2 && q[m.range.first - 1] == ' ' && q[m.range.first - 2].isDigit() }) return null
        val (r0, r1) = capacities.map { batteryRole(q, it.range) }
        // Only two batteries named apart decide which is which. Otherwise ("my 10000 mAh phone
        // charger … my 3000 mAh phone", or one named far from its number) the usual case stands, the
        // larger as the bank, and the answer says so.
        val named = r0 != null && r1 != null && r0.first != r1.first
        val bankIndex = when {
            named -> if (r0!!.first == "bank") 0 else 1
            else -> if (num(capacities[0].groupValues[1]) >= num(capacities[1].groupValues[1])) 0 else 1
        }
        val assumed = !named
        val bank = num(capacities[bankIndex].groupValues[1])
        val phone = num(capacities[1 - bankIndex].groupValues[1])
        if (phone <= 0 || bank <= 0) return null
        fun value(m: MatchResult) = num(m.groupValues.drop(1).first(String::isNotEmpty)) / 100
        // "75% efficiency loss" is a loss, never the efficiency, even when its complement is out of range.
        val losses = efficiencyLoss.findAll(q).toList()
        val (pct, efficiency) = sequenceOf(
            losses.asSequence().map { it to 1 - value(it) },
            efficiencyPct.findAll(q).filter { m -> losses.none { it.range.first <= m.range.last && m.range.first <= it.range.last } }.map { it to value(it) },
            lossPct.findAll(q).map { it to 1 - value(it) },
        ).flatten().firstOrNull { it.second in 0.3..1.0 } ?: return null
        val result = bank * efficiency / phone
        val whole = kotlin.math.floor(result + 1e-9).toInt()
        val text = "${fmt(bank, 0)} mAh × ${fmt(efficiency * 100, 0)}% efficiency = ${fmt(bank * efficiency, 0)} mAh usable; " +
            "${fmt(bank * efficiency, 0)} ÷ ${fmt(phone, 0)} mAh = **${fmt(result, 2)}**, so **" +
            (when (whole) { 0 -> "less than one full charge"; 1 -> "one full charge"; else -> "$whole full charges" }) + "**" +
            (if (result - whole < 0.05) "." else if (whole == 0) ": about ${fmt(result * 100, 0)}% of the battery." else " and about ${fmt((result - whole) * 100, 0)}% of another.") +
            " This assumes both batteries use the same voltage" +
            (if (assumed) " and that the larger one, ${fmt(bank, 0)} mAh, is the power bank." else ".")
        val used = capacities.map { it.range } + listOf(pct.range)
        return Result(text, true, "${fmt(result, 2)} charges.", used = used) to
            emptySet()
    }

    /* ---------- temperature ---------- */    /* ---------- temperature ---------- */
    // A sign is a minus only where no number comes before it and it touches its number: "30-35°C" is a range,
    // "-5°C", "−5 °C", "–5°C" and "minus 5 C" are below zero, and "tomorrow - 30C" is a dash, not a minus.
    private val temp = Regex("(?i)((?:(?<![\\p{L}\\p{N}.,])[-−–](?=\\d)|\\bminus\\s+)?)$NUM\\s*(?:°|degrees?)?\\s*(f|fahrenheit|c|celsius|centigrade)\\b")
    private val rangeStart = Regex("(?i)((?:(?<![\\p{L}\\p{N}.,])[-−–](?=\\d)|\\bminus\\s+)?)$NUM\\s*(?:°\\s*[cf]?\\s*)?(?:-|–|—|to|and)\\s*$")
    /** The temperature is the weather's, not a body's, an oven's or a fridge's. */
    private fun isWeather(q: String): Boolean =
        Regex("(?i)\\b(outside|outdoors|weather|forecast|heat ?wave|today|tomorrow|tonight|walk\\w*|hik\\w*|run|running|jog\\w*|" +
            "cycl\\w*|bik\\w*|climb\\w*|camp\\w*|beach|sun|shade|trek\\w*|work outside|working outside|commute)\\b").containsMatchIn(q) &&
            !Regex("(?i)\\b(fever|body|oven|cook\\w*|bak\\w*|water|bath|shower|pool|sauna|freezer|fridge|refrigerator|engine|cpu|laptop|phone|battery)\\b").containsMatchIn(q)

    private fun signed(sign: String, digits: String) = num(digits) * (if (sign.isBlank()) 1 else -1)
    private fun temperature(q: String): Pair<Result, Set<String>>? {
        val m = temp.find(q) ?: return null
        // A bare "C" or "F" counts only beside weather words, so "5 c" in other text is not a temperature.
        if (!Regex("(?i)celsius|fahrenheit|°|degrees?|\\bin [cf]\\b").containsMatchIn(q) &&
            !Regex("(?i)\\b(outside|outdoors|weather|temperature|hot|cold|warm|freezing|heat)\\b").containsMatchIn(q)) return null
        val value = signed(m.groupValues[1], m.groupValues[2])
        val fromF = m.groupValues[3].lowercase().startsWith("f")
        // "20-25°C", "between 30 and 35 C": both ends are converted; one end alone would be a wrong answer.
        val low = rangeStart.find(q.substring(0, m.range.first))
        if (low != null) {
            val from = signed(low.groupValues[1], low.groupValues[2])
            fun conv(v: Double) = if (fromF) (v - 32) * 5 / 9 else v * 9 / 5 + 32
            val unit = if (fromF) "°F" else "°C"; val other = if (fromF) "°C" else "°F"
            val text = "${fmt(from)}–${fmt(value)}$unit is **${fmt(conv(from))}–${fmt(conv(value))}$other**."
            val c = listOf(from, value).map { if (fromF) (it - 32) * 5 / 9 else it }
            val air = isWeather(q)
            val guide = if (!air) null else when { c.max() >= 32 -> "heat-illness"; c.min() <= -10 -> "extreme-cold"; else -> null }
            return Result(text, true, "${fmt(from)}–${fmt(value)}$unit = ${fmt(conv(from))}–${fmt(conv(value))}$other.",
                guideId = guide, used = listOf(low.range.first..m.range.last)) to emptySet()
        }
        val text = if (fromF) "${fmt(value)}°F is **${fmt((value - 32) * 5 / 9)}°C**.\n\nWorking: (${fmt(value)} − 32) × 5 ÷ 9 = ${fmt((value - 32) * 5 / 9, 2)}."
            else "${fmt(value)}°C is **${fmt(value * 9 / 5 + 32)}°F**.\n\nWorking: ${fmt(value)} × 9 ÷ 5 + 32 = ${fmt(value * 9 / 5 + 32, 2)}."
        val celsius = if (fromF) (value - 32) * 5 / 9 else value
        val fact = if (fromF) "${fmt(value)}°F = ${fmt(celsius)}°C." else "${fmt(value)}°C = ${fmt(value * 9 / 5 + 32)}°F."
        // Heat and cold guides are about the weather: a 104°F fever, a 0°F freezer or 75°C for cooking
        // chicken are not heatstroke or frostbite.
        val air = isWeather(q)
        val guide = if (!air) null else when { celsius >= 32 -> "heat-illness"; celsius <= -10 -> "extreme-cold"; else -> null }
        // "Very hot" describes weather. A body at 34°C is cold, so a temperature not of the air gets no word.
        val feel = if (!air) "" else when { celsius >= 32 -> ", very hot"; celsius >= 27 -> ", hot"; celsius <= -10 -> ", extreme cold"; celsius <= 0 -> ", freezing cold"; else -> "" }
        val stated = if (fromF) "${fmt(value)}°F (${fmt(celsius)}°C$feel)" else "${fmt(value)}°C (${fmt(value * 9 / 5 + 32)}°F$feel)"
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
        // Stops the question states replace the usual allowance; a daylight count is not a stop.
        val stops = breaks(q).filter { s -> left == null || s.first.first !in left.range }
        val withBreaks = if (stops.isEmpty()) need * 7 / 6 else need + stops.sumOf { it.second } / 60.0
        val text = buildString {
            append("${fmt(km)} km ÷ ${fmt(kmh)} km/h = **${hours(need)}** of moving time.")
            // Ten minutes' rest an hour is the usual allowance for walkers and drivers alike.
            append(if (stops.isEmpty()) " With 10-minute breaks each hour it takes about **${hours(withBreaks)}**."
                else " With ${stopsNote(stops)} it takes **${hours(withBreaks)}**.")
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
        // The time the trip takes: with stated stops, the moving time plus the stops; otherwise the moving time
        // alone, since the usual allowance is an estimate the question did not give.
        return Result(text, true, "${fmt(km)} km at ${fmt(kmh)} km/h = ${hours(need)}.", used = listOfNotNull(v.range, d.range, left?.range) + stops.map { it.first },
            durationMinutes = (withBreaks.takeIf { stops.isNotEmpty() } ?: need).times(60).roundToInt()) to emptySet()
    }

    /* ---------- the time in another city ---------- */
    private val clockTime = Regex("(?i)\\b(\\d{1,2})(?:[.:](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b")
    // A full stop ends the name: "in London. What time" is London.
    // A lowercase particle stays inside the name: "Santiago de Compostela" is in Spain, not Santiago in Chile.
    private val cityAfterIn = Regex("\\b(?:in|at)\\s+((?:[A-Z][\\p{L}'-]+)(?:\\s+(?:(?:de|del|da|do|dos|das|di|du|la|le|el|al|of|upon|am|an|der|den|y)\\s+)?[A-Z][\\p{L}'-]+){0,3})")
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
        // Countries on one clock. Countries with several (the US, Australia, Spain with the Canaries) are left out.
        "samoa" to "Pacific/Apia", "american samoa" to "Pacific/Pago_Pago", "south korea" to "Asia/Seoul", "korea" to "Asia/Seoul",
        "japan" to "Asia/Tokyo", "thailand" to "Asia/Bangkok", "vietnam" to "Asia/Ho_Chi_Minh", "philippines" to "Asia/Manila",
        "india" to "Asia/Kolkata", "china" to "Asia/Shanghai", "taiwan" to "Asia/Taipei", "nepal" to "Asia/Kathmandu",
        "sri lanka" to "Asia/Colombo", "israel" to "Asia/Jerusalem", "turkey" to "Europe/Istanbul", "greece" to "Europe/Athens",
        "egypt" to "Africa/Cairo", "kenya" to "Africa/Nairobi", "south africa" to "Africa/Johannesburg", "morocco" to "Africa/Casablanca",
        "uk" to "Europe/London", "united kingdom" to "Europe/London", "england" to "Europe/London", "scotland" to "Europe/London",
        "wales" to "Europe/London", "ireland" to "Europe/Dublin", "france" to "Europe/Paris", "germany" to "Europe/Berlin",
        "italy" to "Europe/Rome", "netherlands" to "Europe/Amsterdam", "belgium" to "Europe/Brussels", "switzerland" to "Europe/Zurich",
        "austria" to "Europe/Vienna", "poland" to "Europe/Warsaw", "czechia" to "Europe/Prague", "czech republic" to "Europe/Prague",
        "hungary" to "Europe/Budapest", "sweden" to "Europe/Stockholm", "norway" to "Europe/Oslo", "denmark" to "Europe/Copenhagen",
        "finland" to "Europe/Helsinki", "iceland" to "Atlantic/Reykjavik", "peru" to "America/Lima", "colombia" to "America/Bogota",
        "argentina" to "America/Argentina/Buenos_Aires",
    )
    // Zone names that are not one place: old region aliases ("Australia/South" is Adelaide, "Pacific/Samoa"
    // is American Samoa) and names several places share (Victoria, Córdoba). The sum would be wrong as often
    // as right, so these are left to research unless an alias names the place.
    private val notOnePlace = setOf("north", "south", "east", "west", "central", "eastern", "western", "pacific", "mountain",
        "atlantic", "samoa", "victoria", "cordoba", "córdoba", "georgia", "general", "universal", "zulu", "acre", "canary", "yukon")
    private val zonesByCity: Map<String, String> by lazy {
        ZoneId.getAvailableZoneIds().filter { Regex("^(Europe|Asia|America|Africa|Australia|Pacific|Atlantic|Indian)/[^/]+$").matches(it) }
            .associateBy { it.substringAfterLast('/').replace('_', ' ').lowercase() }.filterKeys { it !in notOnePlace } + zoneAliases
    }
    // "New York City" and "Lisbon airport" name the place before the generic word; "South Korea" and "Santiago
    // de Compostela" are names in full, never "South" or "Santiago".
    private val placeSuffix = setOf("city", "town", "centre", "center", "downtown", "airport", "area", "proper", "time")
    private fun zoneFor(phrase: String): Pair<String, ZoneId>? {
        val words = phrase.trimEnd('.', ',').split(Regex("\\s+"))
        for (n in words.size downTo 1) {
            if (words.drop(n).any { it.lowercase() !in placeSuffix }) continue
            val name = words.take(n).joinToString(" ")
            zonesByCity[name.lowercase()]?.let { return name to ZoneId.of(it) }
        }
        return null
    }
    /**
     * A clock time converted with the offsets the question states for each place: "a call at 10:00 in São Paulo
     * (UTC-3), what time is that in Dubai (UTC+4) and in Mumbai (UTC+5:30)?". The stated offsets are the
     * premise, so no time-zone table is consulted; every offset must belong to a named place.
     */
    private fun statedClock(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bwhat (?:local )?time\\b|\\btime (?:is it|there|will it be)\\b").containsMatchIn(q)) return null
        if (Regex("(?i)\\b(fly|flying|flight|land|lands|landing|arrive|arrives|arrival|depart\\w*|leave|leaves|take off|takes off)\\b").containsMatchIn(q)) return null
        val offsets = offsetAt.findAll(q).filter { it.groupValues[1].isNotEmpty() }.toList()
        // The "5:30" of "UTC+5:30" is part of an offset, not a time of day.
        val t = clockTime.findAll(q).filter { c -> offsets.none { c.range.first in it.range } }.toList().singleOrNull() ?: return null
        if (offsets.size !in 2..4) return null
        val bound = offsets.map { m ->
            val place = placeOfOffset.find(q.substring(maxOf(0, m.range.first - 40), m.range.first))?.groupValues?.get(1) ?: return null
            Triple(place, offsetMinutes(m), m)
        }
        if (bound.map { it.first.lowercase() }.distinct().size != bound.size || bound.any { it.first.lowercase() in setOf("home", "here", "there") }) return null
        // "10:00 in São Paulo (UTC-3)": the time belongs to the place whose offset sits nearest it.
        val source = bound.minBy { kotlin.math.abs(it.third.range.first - t.range.last) }
        val start = if (t.groupValues[3].isNotEmpty()) {
            val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
            if (h !in 1..12 || m > 59) return null
            (h % 12 + if (t.groupValues[3].lowercase().startsWith("p")) 12 else 0) * 60 + m
        } else t.groupValues[4].toInt() * 60 + t.groupValues[5].toInt()
        fun clockOf(minutes: Int) = String.format(Locale.US, "%02d:%02d", Math.floorMod(minutes, 1440) / 60, Math.floorMod(minutes, 1440) % 60)
        val targets = bound.filter { it !== source }.map { (place, offset, _) ->
            val there = start - source.second + offset
            val day = when (Math.floorDiv(there, 1440)) { 1 -> " the next day"; -1 -> " the previous day"; else -> "" }
            "**${clockOf(there)} in $place**$day (${utc(offset)})"
        }
        val text = "${clockOf(start)} in ${source.first} (${utc(source.second)}) is ${targets.joinToString(" and ")}.\n\n" +
            "Worked from the offsets in your question; a place on daylight saving time can be an hour off them."
        return Result(text, true, "${clockOf(start)} in ${source.first} = " + targets.joinToString("; ") { it.replace("**", "") } + ".",
            used = listOf(t.range) + offsets.map { it.range }) to emptySet()
    }

    private fun clock(q: String, today: LocalDate): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bwhat (?:local )?time\\b|\\b(?:what|which) day\\b|\\btime (?:is it|there|difference)\\b").containsMatchIn(q)) return null
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
        // "Leaves Paris at 10:00, lands in New York at 12:30", "lands at 15:35 local": two clocks, and the gap needs their zones.
        if (travels.containsMatchIn(q) && (namesPlace(q) || Regex("(?i)\\blocal\\b|\\btheir time\\b|\\bdestination time\\b").containsMatchIn(q))) return null
        val found = clockTime.findAll(q).toList()
        val times = found.mapNotNull { t ->
            if (t.groupValues[3].isNotEmpty()) {
                val h = t.groupValues[1].toInt(); val m = t.groupValues[2].ifBlank { "0" }.toInt()
                if (h !in 1..12 || m > 59) null else ((h % 12) + if (t.groupValues[3].lowercase().startsWith("p")) 12 else 0) * 60 + m
            } else t.groupValues[4].toInt() * 60 + t.groupValues[5].toInt()
        }.toList()
        if (times.size != 2 || found.size != 2) return null
        // "Until sunset at 18:30 if it's 14:15 now": the time that is now is where the gap starts.
        val nowAt = found.indexOfFirst { t ->
            val around = q.substring(maxOf(0, t.range.first - 12), minOf(q.length, t.range.last + 8))
            Regex("(?i)\\b(?:it(?:'s| is)|now(?: it(?:'s| is))?|the time is|currently)\\s*$").containsMatchIn(q.substring(maxOf(0, t.range.first - 14), t.range.first)) ||
                Regex("(?i)^\\s*(?:now|right now|at the moment)\\b").containsMatchIn(q.substring(t.range.last + 1).take(14)) && around.isNotEmpty()
        }
        // With the time now given, the words say which way the gap runs: "how long ago", "since" count up to now;
        // "until", "left" count on from now. Without either, a gap over midnight is a guess.
        val back = Regex("(?i)\\b(?:ago|since|elapsed|passed|been)\\b").containsMatchIn(q)
        val ahead = Regex("(?i)\\b(?:until|till|(?:time|hours?|minutes?|mins?|long|much) (?:is |are )?left|before|to go|remain\\w*|away|wait\\w*)\\b").containsMatchIn(q)
        val (from, to) = when {
            nowAt < 0 -> times[0] to times[1]
            back && !ahead -> times[1 - nowAt] to times[nowAt]
            else -> times[nowAt] to times[1 - nowAt]
        }
        var minutes = to - from
        val overnight = minutes <= 0
        if (overnight && nowAt >= 0 && back == ahead) return null
        // Which time comes first is not said and the gap would cross midnight: that is a guess, not a sum.
        // "Opens at 9 pm and closes at 2 am" says it.
        val startWord = Regex("(?i)\\b(?:opens?|starts?|begins?|leaves?|departs?|from|sets? off)\\W+(?:\\w+\\W+){0,2}$")
        val endWord = Regex("(?i)\\b(?:closes?|ends?|finish\\w*|arriv\\w*|lands?|until|till|to|gets? (?:in|there|back))\\W+(?:\\w+\\W+){0,2}$")
        val ordered = startWord.containsMatchIn(q.substring(maxOf(0, found[0].range.first - 30), found[0].range.first)) &&
            endWord.containsMatchIn(q.substring(maxOf(found[0].range.last + 1, found[1].range.first - 30), found[1].range.first))
        if (overnight && nowAt < 0 && !ordered) return null
        // "From 11:30 to 2:15" without am or pm: on a 12-hour clock that is 2 h 45 over noon, not 14 h 45 overnight.
        val twelveHourDial = found.all { t -> t.groupValues[3].isEmpty() && !t.groupValues[4].startsWith("0") && t.groupValues[4].toIntOrNull() in 1..12 }
        // "Overnight" does not settle it: 10:45 overnight to 1:20 may be 22:45, which the 24-hour reading is not.
        if (overnight && twelveHourDial && (to + 12 * 60 - from) in 1 until 12 * 60) return null
        if (overnight) minutes += 24 * 60
        fun clock(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        val length = hours(minutes / 60.0)
        val text = "From ${clock(from)} to ${clock(to)}${if (overnight) " the next day" else ""} is **$length**."
        return Result(text, true, "${clock(from)} to ${clock(to)} = $length.", used = found.map { it.range }) to emptySet()
    }

    /* ---------- a clock time plus or minus a duration ---------- */
    // "2 h 35" or "9 hours 30" (minutes after the hours, without their unit), "2 hours and 35 minutes", "30 minutes",
    // "10-minute" and "2-hour" as in "two 10-minute stops".
    private val span = Regex("(?i)\\b$NUM\\s*(?:hours?|hrs?|h)\\s*(?:and\\s+)?([0-5]\\d)\\b(?!-|\\s*(?:%|[a-z]))|" +
        "\\b$NUM\\s*-?\\s*(?:hours?|hrs?|h)\\b(?:\\s*(?:and\\s*)?$NUM\\s*(?:minutes?|mins?|m)\\b)?|\\b$NUM\\s*-?\\s*(?:minutes?|mins?)\\b")
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
    // "When", "what time", "what local time", "what day and local time", "which day".
    private val asksWhen = Regex("(?i)\\bwhen\\b|\\bwhat (?:local )?time\\b|\\b(?:what|which) day\\b")
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
    private val asked = Regex("(?i)\\b(?:when|what (?:local )?time|(?:what|which) day)\\b[^.?!]*?\\b(?:($ASK_START)|($ASK_END))\\b" +
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
            val later = e.groupValues[1].lowercase() in setOf("after", "later")
            // The words that name the event in the time's own clause or in the question asked.
            val eventWords = when (event) {
                null, "" -> null
                "departure", "take-off", "takeoff" -> Regex("(?i)\\b(?:depart\\w*|leav\\w*|take[- ]?off|takes off|flight leaves|plane leaves)\\b")
                else -> Regex("(?i)\\b${Regex.escape(event.removeSuffix("s"))}\\w*\\b")
            }
            // The words that lead up to the time, in its sentence: "Boarding starts at" names boarding.
            val timeClause = before.split('.', '?', '!').last()
            val own = when {
                // "30 minutes early" is early for the time itself.
                e.groupValues[1].startsWith("early", ignoreCase = true) && e.groupValues[2].lowercase() !in setOf("than", "for") -> true
                // "before my 9:40 train": the time belongs to the event named.
                t.range.first >= from && q.substring(from, t.range.first).none { it in ".?!" } -> true
                event == null -> false
                event.isEmpty() || event in setOf("it", "that", "this", "then", "time") -> true
                // The time is the event's only when its own clause names the event: "boarding starts at
                // 13:20" is boarding's time, not the departure's.
                else -> eventWords!!.containsMatchIn(timeClause)
            }
            when {
                own -> !later
                // "Boarding starts at 13:20, 40 minutes before departure. When does it leave?": the event
                // asked about comes 40 minutes after the time given.
                eventWords != null && asked.findAll(q).any { eventWords.containsMatchIn(it.value) } -> later
                else -> return null
            }
        } ?: run {
            // "Immigration takes 1 hour" after a landing is another step, not the flight: counting it back
            // from the landing would answer a different question.
            val spanClause = clauses.findAll(q).firstOrNull { s.range.first in it.range }?.value.orEmpty()
            val journey = Regex("(?i)\\b(?:trip|journey|flight|fly\\w*|drive|driving|ride|walk\\w*|hike|hiking|train|bus|coach|ferry|boat|" +
                "crossing|transfer|route|way there|leaves?|departs?|travel\\w*)\\b")
            val otherStep = Regex("(?i)\\b(?:immigration|customs|passport control|security|check[- ]?in|baggage|bags|luggage|queue|meeting|" +
                "lunch|dinner|breakfast|tour|show|class|appointment|visit|exam|game|match|concert|film|movie|shopping|layover|connection)\\b")
            // The part of the sentence that says how long, after the time: "and immigration takes about 1 hour".
            val stepPart = if (s.range.first > t.range.last) q.substring(t.range.last + 1, s.range.last + 1) else spanClause
            if (otherStep.containsMatchIn(stepPart) && !journey.containsMatchIn(stepPart.replace(otherStep, " "))) {
                // A step after the time ends later ("when can I leave the airport?", "when do we finish?"); asked
                // when to set off or arrive, it could come before or after, so nothing is guessed.
                val askedAfter = Regex("(?i)\\b(?:finish\\w*|done|end|over|out|through|leave the|exit|get out|be free|free)\\b")
                if (!asked.findAll(q).any { askedAfter.containsMatchIn(it.value) } &&
                    !Regex("(?i)\\bwhen\\b[^.?!]*\\b(?:finish\\w*|done|over|out|through|leave the|exit)\\b").containsMatchIn(q)) return null
                return@run false
            }
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
        val dayNote = dayLabel(day, weekdayOf(q, t.range))
        val text = "${clock(start)} ${if (back) "minus" else "plus"} $length is **${clock(at)}$dayNote**."
        return Result(text, true, "${clock(start)} ${if (back) "−" else "+"} $length = ${clock(at)}$dayNote.", used = listOf(t.range, s.range)) to emptySet()
    }

    /* ---------- a price per night, day or person, times how many ---------- */
    private const val UNIT = "(?:night|day|person|head|ticket|hour|week|room|adult|bed|guest|child|kid|tent|pitch|car|vehicle|bike|dog|stay|booking)"
    // "€180 a night", "18 euros per person per night", "€3.50 per person per night": a price and every unit it is per.
    private val unitPrice = Regex("(?i)(?:($SYMBOLS)\\s*$NUM|$NUM\\s*(?:($SYMBOLS)|((?:$CURRENCIES)s?)\\b)?)((?:\\s*(?:a|an|per|each|every|/)\\s*${UNIT}s?\\b)+)")
    private val unitInPrice = Regex("(?i)\\b($UNIT)s?\\b")
    private val adjustment = Regex("(?i)$NUM\\s*$PCT\\s*(discount|off|tax|vat|service(?: charge)?|surcharge|fee|commission)|" +
        "(discount|tax|vat|service charge|surcharge|fee|commission)\\s+(?:of\\s+)?$NUM\\s*$PCT")
    // A charge named without the percentage it adds or takes off ("plus tax", "minus a discount") cannot be applied.
    private val charge = Regex("(?i)\\b(?:tax(?:es)?|vat|fees?|service|(?:sur)?charges?|commissions?|discount(?:s|ed)?|levy|supplement)\\b")
    // The name of a flat charge, for the working: "city tax of €3.50 per person per night".
    private val chargeName = Regex("(?i)\\b((?:city|tourist|resort|cleaning|service|booking|local|bed|visitor)?\\s*(?:tax|fee|charge|surcharge|levy|supplement))\\b")
    private val unitWords = Regex("(?i)\\b(\\d+(?:\\.\\d+)?|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+" +
        "(nights?|days?|people|persons?|heads?|tickets?|hours?|weeks?|rooms?|beds?|adults?|children|kids|guests?|tents?|pitches|cars?|vehicles?|bikes?|dogs?)\\b")
    // People counted without a unit word: "3 of us", "the two of us", "both of us", "for two", "we are 3",
    // "my son and me".
    private const val COUNT = "(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
    private val headCount = Regex("(?i)\\b(?:$COUNT\\s+of\\s+(?:us|you|them)|both|couple|(?:family|group|party|team)\\s+of\\s+$COUNT|" +
        "we(?:['’]re|\\s+are)\\s+$COUNT|my\\s+(?:wife|husband|partner|(?:boy|girl)?friends?|family|kids?|child(?:ren)?|parents|" +
        "sons?|daughters?|brothers?|sisters?|mum|mom|dad|mother|father)|and\\s+(?:me|i)|" +
        "for\\s+$COUNT\\b(?![.,]\\d|\\s*(?:%|$SYMBOLS))(?!\\s+(?:more|extra|additional|full|whole|percent|per\\s?cent|nights?|days?|" +
        "people|persons?|heads?|tickets?|hours?|weeks?|months?|rooms?|beds?|adults?|children|kids|guests?|tents?|cars?|bikes?|dogs?|(?:$CURRENCIES)s?)\\b))\\b")
    // A second rate on the price ("€18 a night per person", "€15 a day each") multiplies by a count the question
    // may not give.
    private val secondRate = Regex("(?i)(?:\\b(?:per|each|every)|/)\\s*(?:night|day|person|head|ticket|hour|week|room|bed|adult|guest|" +
        "child|kid|car|tent|vehicle|bike|dog|pitch|stay|booking)s?\\b|\\b(?:each|apiece|pp)\\b")
    // Anything this sum does not handle leaves the question to research: a tax already included, a
    // second price, another currency.
    private val beyondSum = Regex("(?i)\\b(?:includ\\w*|incl\\.?|inclusive|already|after (?:a |the )?\\d+|after (?:a |the )?(?:discount|tax)|" +
        "convert\\w*|in (?:dollars|euros|pounds|yen|usd|eur|gbp)|exchange rate|on (?:everything|the total|the whole|all of it|top of (?:that|everything|the total)))\\b")
    // "Plus a city tax of €3.50 per person per night": a further price is added to the bill when a word says so;
    // "or €280 per week" offers another price instead.
    private val additive = Regex("(?i)\\b(?:plus|and|with|add\\w*|extra|additional|on top|also|then)\\b")
    /** The unit a count or a price names, with "people" and "heads" as persons, "kids" as children. */
    private fun unitKey(word: String): String = when (val w = word.lowercase()) {
        "people", "persons", "person", "head", "heads" -> "person"
        "children", "child" -> "child"; "kids", "kid" -> "kid"; "pitches" -> "pitch"
        else -> w.removeSuffix("s")
    }
    private fun unitPlural(unit: String, n: Int): String = if (n == 1) unit else when (unit) {
        "person" -> "people"; "child" -> "children" ; else -> unit + "s"
    }
    private fun perUnit(q: String): Pair<Result, Set<String>>? {
        val prices = unitPrice.findAll(q).toList()
        val p = prices.firstOrNull() ?: return null
        if (beyondSum.containsMatchIn(q)) return null
        fun priceOf(m: MatchResult) = num(m.groupValues[2].ifBlank { m.groupValues[3] })
        fun currencyOf(m: MatchResult) = m.groupValues[1].ifBlank { m.groupValues[4] }.ifBlank { m.groupValues[5] }
        fun unitsOf(m: MatchResult) = unitInPrice.findAll(m.groupValues[6]).map { unitKey(it.groupValues[1]) }.distinct().toList()
        val price = priceOf(p); val currencyName = currencyOf(p)
        // In "60 euros plus 10 per person" the bare 10 is not the whole price: only a price in money is multiplied.
        if (currencyName.isBlank() || price <= 0) return null
        fun amount(v: Double) = if (currencyName.length == 1) "$currencyName${money(v)}" else "${money(v)} $currencyName"
        // Every count in the question, each of a different unit: "3 people for 4 nights", or "3 of us" and "4 nights".
        val counts = mutableMapOf<String, Pair<Int, IntRange>>()
        for (c in unitWords.findAll(q).filter { c -> prices.none { c.range.first in it.range } }) {
            val token = c.groupValues[1]
            if ('.' in token) return null
            val n = count(token) ?: return null
            if (counts.put(unitKey(c.groupValues[2]), n to c.range) != null) return null
        }
        for (h in headCount.findAll(q).filter { h -> prices.none { h.range.first in it.range } }) {
            val n = Regex("(?i)\\b$COUNT\\b").find(h.value)?.let { count(it.value) } ?: if (Regex("(?i)\\b(?:both|couple)\\b").containsMatchIn(h.value)) 2 else return null
            if (counts.put("person", n to h.range) != null) return null
        }
        // "Each" or "per person" outside every price is a multiplier the question does not give.
        if (secondRate.findAll(q).any { s -> prices.none { s.range.first in it.range } }) return null
        // Each price has one amount of money: a bare "plus 2 euros" or a toll is a sum this one cannot place.
        val amounts = Regex("(?i)$SYMBOLS\\s*$NUM|$NUM\\s*(?:$SYMBOLS|(?:$CURRENCIES)s?\\b)").findAll(q).count()
        if (amounts != prices.size) return null
        fun sentenceStart(at: Int) = clauses.findAll(q).firstOrNull { at in it.range }?.range?.first ?: 0
        // The further prices are added to the bill; each must say so and be in the same money.
        for (extra in prices.drop(1)) {
            val lead = q.substring(sentenceStart(extra.range.first), extra.range.first)
            if (currencyOf(extra).isBlank() || currencyOf(extra).lowercase().removeSuffix("s") != currencyName.lowercase().removeSuffix("s")) return null
            if (!additive.containsMatchIn(lead) || Regex("(?i)\\bor\\s+(?:a\\s+|an\\s+|the\\s+)?(?:\\w+\\s+){0,2}$").containsMatchIn(lead)) return null
        }
        val used = mutableListOf<IntRange>()
        val consumed = mutableSetOf<String>()
        /** The price times the count of every unit it names, with a room count on a nightly price as a multiplier too. */
        fun multiplied(m: MatchResult): Pair<Double, String>? {
            var total = priceOf(m); val working = StringBuilder(amount(priceOf(m)))
            val units = unitsOf(m).toMutableList()
            // A nightly room price is per room; a per-person charge is not.
            if (("night" in units || "day" in units) && "room" !in units && "person" !in units && "room" in counts) units += "room"
            for (u in units) {
                // "A cleaning fee of $40 per stay" is charged once.
                if (u == "stay" || u == "booking") continue
                val (n, at) = counts[u] ?: return null
                consumed += u; used += at
                total *= n; working.append(" × $n ${unitPlural(u, n)}")
            }
            return total to working.toString()
        }
        val (base, baseWorking) = multiplied(p) ?: return null
        used += p.range
        val adjustments = adjustment.findAll(q).toList()
        if (Regex("(?i)%|per\\s?cent\\b").findAll(q).count() != adjustments.size) return null
        // A percentage after a flat charge might be meant on the charge too: not guessed.
        if (adjustments.any { a -> prices.drop(1).any { it.range.first < a.range.first } }) return null
        // Every charge word is a percentage applied, names a flat charge added, or refers to the discount step.
        val discounted = adjustments.any { it.value.lowercase().let { v -> "discount" in v || "off" in v } }
        for (c in charge.findAll(q)) {
            val inAdjustment = adjustments.any { c.range.first in it.range }
            val namesExtra = prices.drop(1).any { e -> (c.range.first in sentenceStart(e.range.first) until e.range.first && e.range.first - c.range.first <= 40) ||
                (c.range.first > e.range.last && c.range.first - e.range.last <= 25 && q.substring(e.range.last + 1, c.range.first).none { it in ".?!;" }) }
            val refersToStep = discounted && c.value.lowercase() == "discounted"
            // "The campsite charges 12 euros": the verb before a price names no charge of its own.
            val verb = c.value.lowercase().startsWith("charge") && prices.any { it.range.first - c.range.last in 1..3 }
            if (!inAdjustment && !namesExtra && !refersToStep && !verb) return null
        }
        used += adjustments.map { it.range }
        var total = base
        val text = StringBuilder("$baseWorking = **${amount(base)}**.")
        for (a in adjustments) {
            val percent = num(a.groupValues[1].ifBlank { a.groupValues[4] })
            val kind = a.groupValues[2].ifBlank { a.groupValues[3] }.lowercase()
            if (percent <= 0 || percent >= 100) return null
            val down = kind == "discount" || kind == "off"
            val after = total * (1 + (if (down) -percent else percent) / 100)
            text.append("\n\nWith the ${fmt(percent)}% ${if (kind == "off") "discount" else kind}: ${amount(total)} × ${fmt(1 + (if (down) -percent else percent) / 100, 2)} = **${amount(after)}**.")
            total = after
        }
        val extras = prices.drop(1).map { e ->
            val (sum, working) = multiplied(e) ?: return null
            used += e.range
            // The charge's name is the one nearest the price: "plus 7% tax and a €2 per night city tax" names the city tax.
            val before = chargeName.findAll(q.substring(sentenceStart(e.range.first), e.range.first).takeLast(40)).lastOrNull()
            val after = chargeName.find(q.substring(e.range.last + 1).take(25).substringBefore('.'))
            val name = (after ?: before)?.groupValues?.get(1)?.trim()?.replaceFirstChar(Char::uppercaseChar) ?: "Plus"
            text.append("\n\n$name: $working = **${amount(sum)}**.")
            sum
        }
        if (extras.isNotEmpty()) {
            val all = total + extras.sum()
            text.append("\n\nTotal: ${amount(total)} + ${extras.joinToString(" + ") { amount(it) }} = **${amount(all)}**.")
            total = all
        }
        // Every count was multiplied by something, and every number is a price, a count or a percentage applied.
        if (counts.keys.any { it !in consumed }) return null
        if (numberToken.findAll(q).any { d -> used.none { d.range.first in it } }) return null
        return Result(text.toString(), true, "$baseWorking${if (extras.isNotEmpty()) " plus the extras" else ""} = ${amount(total)} in all.", used = used) to emptySet()
    }

    /* ---------- provisions for a group over the days ---------- */
    // "Everyone has 2 eggs", "each person gets 3 slices", "2 cups of coffee each": what one person has.
    private val perHead = Regex("(?i)\\b(?:everyone|everybody|each(?:\\s+(?:person|of us|one|hiker|guest|adult|camper|kid|child))?|every\\s+(?:one|person|hiker|guest)|per\\s+(?:person|head)|a\\s+head|apiece|all of us)\\b")
    private val perDayCue = Regex("(?i)\\b(?:each|every|per|a)\\s+(?:morning|day|evening|night|meal|breakfast|dinner|lunch)\\b")
    private val mealDays = Regex("(?i)\\b$N\\s+(?:full\\s+)?(mornings?|days?|evenings?|nights?|breakfasts?|dinners?|lunch(?:es)?|meals?)\\b")
    private const val PACK = "(box|boxes|bag|bags|pack|packs|packet|packets|carton|cartons|tray|trays|crate|crates|bottle|bottles|can|cans|tin|tins|jar|jars|roll|rolls|sachet|sachets|tube|tubes|loaf|loaves|case|cases|tub|tubs|pouch|pouches|six-?packs?)"
    private const val MEASURE = "(g|grams?|kg|kilos?|ml|l|litres?|liters?)"
    // "2 eggs", "2 cups of coffee", "150 g of pasta", "3 slices of bread": a count or measure and what it is of.
    private val item = Regex("(?i)\\b$NUM\\s*(?:$MEASURE|(?:(?!of\\b)[a-z]+\\s+)?(cups?|slices?|pieces?|bars?|portions?|servings?|scoops?|spoons?|sachets?|rolls?|[a-z]+s))\\b(?:\\s+of\\s+([a-z]+))?")
    // "Eggs come in boxes of 12", "beer is sold in packs of 6".
    private val packOf = Regex("(?i)\\b([a-z]+)\\s+(?:come|comes|are|is|sold|packed|bought|available)?\\s*(?:in|by the)\\s+$PACK\\s+of\\s+$NUM\\s*$MEASURE?\\b")
    // "A 250 g bag of coffee makes 30 cups", "a loaf has 20 slices", "one pack holds 10".
    private val packMakes = Regex("(?i)\\b(?:a|an|one|each|every)\\s+(?:$NUM\\s*$MEASURE\\s+)?$PACK\\s+(?:of\\s+([a-z]+)\\s+)?(?:makes|gives|yields|serves|holds|contains|has|brews|does|is enough for|is good for|lasts)\\s+(?:about\\s+|around\\s+)?$NUM\\s*([a-z]+)?")
    // "500 g packets of pasta", "1.5 litre bottles of water".
    private val packSized = Regex("(?i)\\b$NUM\\s*$MEASURE\\s+$PACK(?:\\s+of\\s+([a-z]+))?\\b")
    private fun grams(value: Double, unit: String) = when (unit.lowercase().take(2)) { "kg", "ki" -> value * 1000; "l", "li" -> value * 1000; else -> value }
    private fun packPlural(pack: String, n: Int): String {
        val one = pack.lowercase().let { when { it == "loaves" -> "loaf"; it == "boxes" -> "box"; it == "pouches" -> "pouch"; it.endsWith("s") && !it.endsWith("ss") -> it.dropLast(1); else -> it } }
        if (n == 1) return one
        return when { one == "loaf" -> "loaves"; one == "box" -> "box" + "es"; one == "pouch" -> "pouches"; else -> one + "s" }
    }
    private fun same(a: String?, b: String?) = a != null && b != null && a.lowercase().removeSuffix("es").removeSuffix("s") == b.lowercase().removeSuffix("es").removeSuffix("s")
    private class Item(val range: IntRange, val perPerson: Double, val measure: String?, val noun: String)
    private class Pack(val range: IntRange, val pack: String, val size: Double, val measure: String?, val of: String?)
    private fun provisions(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bhow (?:many|much)\\b").containsMatchIn(q) || !perHead.containsMatchIn(q)) return null
        // Money per person is a bill, not a shopping list; drinking water over the days is water()'s sum.
        if (Regex("(?i)$SYMBOLS|\\b(?:$CURRENCIES)s?\\b").containsMatchIn(q)) return null
        if (Regex("(?i)$NUM\\s*(?:l|liters?|litres?)(?:\\s+of water)?\\s+(?:per|a|each)\\s+(?:person\\s+)?(?:per\\s+|a\\s+|each\\s+)?day").containsMatchIn(q)) return null
        val groups = groupSize.flatMap { it.findAll(q) }
        val people = groups.map { count(it.groupValues[1]) ?: 2 }.distinct().singleOrNull() ?: return null
        val group = groups.minBy { it.range.first }
        val packs = (packOf.findAll(q).map { m -> Pack(m.range, m.groupValues[2], num(m.groupValues[3]), m.groupValues[4].ifBlank { null }, m.groupValues[1]) } +
            packMakes.findAll(q).map { m -> Pack(m.range, m.groupValues[3], num(m.groupValues[5]), m.groupValues[6].ifBlank { null }, m.groupValues[4].ifBlank { null }) } +
            packSized.findAll(q).map { m -> Pack(m.range, m.groupValues[3], num(m.groupValues[1]), m.groupValues[2], m.groupValues[4].ifBlank { null }) })
            .toList().let { all -> all.filter { p -> all.none { o -> o !== p && o.range.first <= p.range.first && o.range.last >= p.range.last && o.range != p.range } } }
        val days = mealDays.findAll(q).filter { d -> packs.none { d.range.first in it.range } && !perDayCue.containsMatchIn(d.value) }.toList()
        if (days.size > 1) return null
        val dayCount = days.singleOrNull()?.let { count(it.groupValues[1]) } ?: if (perDayCue.containsMatchIn(q)) return null else 1
        // What each person has: the counts in the sentences that say "everyone", "each", "per person".
        val headSentences = clauses.findAll(q).filter { perHead.containsMatchIn(it.value) }.toList()
        val taken = packs.map { it.range } + days.map { it.range } + listOf(group.range)
        val items = item.findAll(q).filter { m -> headSentences.any { m.range.first in it.range } && taken.none { m.range.first in it } }
            .map { m ->
                val g = m.groupValues
                // "150 g of pasta" and "2 cups of coffee" are measured; "2 eggs" is a count of the thing itself.
                when {
                    g[2].isNotBlank() -> Item(m.range, num(g[1]), g[2], g[4].ifBlank { g[2] })
                    g[4].isNotBlank() -> Item(m.range, num(g[1]), g[3], g[4])
                    else -> Item(m.range, num(g[1]), null, g[3])
                }
            }.toList()
        if (items.isEmpty() || items.any { it.perPerson <= 0 }) return null
        // Each item's pack: the one that names it, its measure, or the only pack left for the only item left.
        val paired = mutableMapOf<Item, Pack>()
        for (i in items) packs.firstOrNull { p -> same(p.of, i.noun) || same(p.of, i.measure) || (i.measure == null && same(p.measure, i.noun)) }?.let { paired[i] = it }
        for (i in items.filter { it !in paired }) packs.firstOrNull { p -> p !in paired.values && p.measure != null && same(p.measure, i.measure) }?.let { paired[i] = it }
        val leftItems = items.filter { it !in paired }; val leftPacks = packs.filter { it !in paired.values }
        if (leftItems.size == 1 && leftPacks.size == 1) paired[leftItems.single()] = leftPacks.single()
        else if (leftPacks.isNotEmpty()) return null
        val dayWord = days.singleOrNull()?.groupValues?.get(2)?.lowercase()?.let { if (dayCount == 1) it.removeSuffix("s") else if (it.endsWith("s")) it else it + "s" }
        val text = StringBuilder(); val facts = mutableListOf<String>()
        for (i in items) {
            val total = people * i.perPerson * dayCount
            val measured = Regex("(?i)^$MEASURE$")
            val measure = i.measure?.let { m -> if (measured.matches(m)) m else unitPlural(m.removeSuffix("s"), total.roundToInt()) }
            val what = if (measure != null) "$measure of ${i.noun}" else unitPlural(i.noun.removeSuffix("s"), total.roundToInt())
            val each = " ${fmt(i.perPerson)}" + (i.measure?.let { " $it" } ?: "")
            text.append("${i.noun.replaceFirstChar(Char::uppercaseChar)}: $people people ×$each" + (if (dayCount > 1) " × $dayCount $dayWord" else "") + " = **${fmt(total)} $what**")
            val pack = paired[i]
            if (pack != null) {
                // Grams against grams, cups against cups: a pack's measure must be the item's.
                val itemMeasured = i.measure != null && measured.matches(i.measure)
                val packMeasured = pack.measure != null && measured.matches(pack.measure)
                if (itemMeasured != packMeasured) return null
                fun dimension(unit: String) = if (unit.lowercase().startsWith("g") || unit.lowercase().startsWith("k")) "mass" else "volume"
                if (itemMeasured && dimension(i.measure!!) != dimension(pack.measure!!)) return null
                if (!itemMeasured && i.measure != null && pack.measure != null && !same(i.measure, pack.measure)) return null
                val need = if (itemMeasured) grams(total, i.measure!!) else total
                val size = if (packMeasured) grams(pack.size, pack.measure!!) else pack.size
                if (size <= 0) return null
                val n = kotlin.math.ceil(need / size - 1e-9).toInt()
                val exact = need / size
                text.append("; ${fmt(need)} ÷ ${fmt(size)} = ${fmt(exact, 2)}" + (if (exact == n.toDouble()) ", so **$n ${packPlural(pack.pack, n)}**." else ", which rounds up to **$n ${packPlural(pack.pack, n)}**."))
                facts += "${fmt(total)} $what = $n ${packPlural(pack.pack, n)}"
            } else { text.append("."); facts += "${fmt(total)} $what" }
            text.append("\n\n")
        }
        text.append("Buy a little extra for breakages and seconds.")
        val used = listOf(group.range) + days.map { it.range } + items.map { it.range } + paired.values.map { it.range }
        return Result(text.toString().trim(), true, facts.joinToString("; ") + ".", used = used) to emptySet()
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
