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
 * Exact arithmetic for the calculations travellers ask about: fuel economy and range, hiking time by
 * Naismith's rule, walking or driving time against the time left, a bill at a given exchange rate
 * with a tip, water for a group, a power bank's watt-hours, temperatures, the time in another city,
 * a recipe for more people, where water boils at altitude and common unit conversions. A small model gets these wrong (7.5 L/100 km
 * once came out as 0.61 mpg), so the app works them out and shows the working.
 */
object Calculators {
    /** [complete]: the calculation is the whole answer. Otherwise the numbers lead and research continues. */
    /** [guideId]: the bundled guide the rest of the question must be answered from (extreme heat or cold). */
    data class Result(
        val text: String, val complete: Boolean, val facts: String, val followUp: String = "",
        val guideId: String? = null,
        /** Written by the app for an extreme temperature, shown above the official guide. */
        val riskNote: String? = null,
    )

    private const val NUM = "(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    private fun num(text: String) = text.replace(",", "").toDouble()

    // Anything beyond the arithmetic ("is it dangerous", "should I") is left to research.
    private val judgement = Regex(
        "(?i)\\b(dangerous|danger|safe|safely|risky|risk|ok(?:ay)?|should|recommend|better|worse|why|explain|compare|" +
            "healthy|enough|too (?:hot|cold|much|far|long)|worth|legal|allowed|can i (?:bring|take|carry|pack|use))\\b",
    )

    fun solve(question: String, today: LocalDate = LocalDate.now()): Result? {
        val families = listOfNotNull(
            fuel(question)?.let { it to FUEL }, fuelRange(question)?.let { it to FUEL + RANGE },
            naismith(question)?.let { it to HIKE }, travelTime(question)?.let { it to TRAVEL },
            currency(question)?.let { it to MONEY }, water(question)?.let { it to WATER }, battery(question)?.let { it to BATTERY },
            temperature(question)?.let { it to TEMPERATURE }, clock(question, today)?.let { it to CLOCK },
            recipe(question)?.let { it to RECIPE }, boiling(question)?.let { it to BOILING }, duration(question)?.let { it to DURATION },
            units(question)?.let { it to UNITS }, percent(question)?.let { it to PERCENT })
        if (families.isEmpty()) return null
        val parts = families.map { it.first }
        val covered = parts.flatMap { it.second }.toSet()
        val leftover = judgement.findAll(question).map { it.value.lowercase() }.filterNot { it in covered }.toList()
        // Words the calculation does not account for mean something else is asked too: "the best time
        // to hike the 43 km Inca Trail", "my baby has a fever of 39°C". Then the numbers lead and
        // research answers the question; only a question that is all arithmetic is answered here.
        val known = GENERIC + families.flatMap { it.second } +
            words(parts.joinToString(" ") { it.first.text }) + money.findAll(question).map { it.value.lowercase() }
        val unasked = words(question).filterNot { it in known || judgement.matches(it) }
        val complete = leftover.isEmpty() && unasked.isEmpty()
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

    // The words a calculation question is made of, beyond the numbers, units and names in its answer.
    private val GENERIC = setOf("what", "how", "many", "much", "long", "far", "is", "are", "was", "that", "this", "it", "its", "in",
        "into", "to", "convert", "converted", "calculate", "work", "out", "about", "roughly", "approximately", "around",
        "need", "needs", "have", "has", "had", "get", "gets", "will", "would", "take", "takes", "does", "do", "did", "can",
        "could", "total", "each", "per", "and", "or", "with", "for", "from", "by", "the", "an", "my", "our", "we", "us", "me",
        "you", "your", "there", "be", "if", "at", "on", "of", "so", "then", "please", "tell", "show", "give", "number",
        "amount", "equivalent", "exactly", "round", "rounded", "all", "both", "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve", "whole", "just", "only", "using", "use", "uses", "used", "rule",
        "value", "values", "result", "answer", "quick", "quickly", "im", "it's", "i'm", "i've", "we're", "they", "their", "he", "she")
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
        "trip", "carry", "weigh", "weight", "drink", "drinks", "camping", "camp", "trekking", "group", "family", "hikers", "adults", "travellers", "travelers", "us")
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
        val wantsPer100 = Regex("(?i)(?:l|liters?|litres?)\\s*(?:per|/)\\s*100").containsMatchIn(q)
        per100.find(q)?.takeIf { wantsMpg }?.let { m ->
            val l = num(m.groupValues[1]); if (l <= 0) return null
            val us = 235.215 / l; val uk = 282.481 / l
            return Result("${fmt(l)} L/100 km is about **${fmt(us)} miles per US gallon** (${fmt(uk)} mpg in UK imperial gallons).\n\n" +
                "Working: 235.215 ÷ ${fmt(l)} = ${fmt(us, 2)}, since 100 km is 62.137 miles and a US gallon is 3.785 litres.",
                true, "${fmt(l)} L/100 km = ${fmt(us)} mpg (US).") to emptySet()
        }
        mpg.find(q)?.takeIf { wantsPer100 }?.let { m ->
            val v = num(m.groupValues[1]); if (v <= 0) return null
            val imperial = Regex("(?i)imperial|uk|british").containsMatchIn(m.value)
            val l = (if (imperial) 282.481 else 235.215) / v
            return Result("${fmt(v)} mpg (${if (imperial) "UK imperial" else "US"} gallons) is about **${fmt(l)} L/100 km**.\n\n" +
                "Working: ${if (imperial) "282.481" else "235.215"} ÷ ${fmt(v)} = ${fmt(l, 2)}.", true, "${fmt(v)} mpg = ${fmt(l)} L/100 km.") to emptySet()
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
        return Result(text, true, "Naismith time = ${hours(walk + climb)}.") to emptySet()
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
                true, "${fmt(have, 2)} ${base}s = ${money(out)} $localName.") to emptySet()
        }
        val localAmount = num(amount.groupValues[1])
        val converted = localAmount / perBase
        val baseName = if (base.lowercase().endsWith("dollar")) "${base.removeSuffix("dollar").trim().uppercase().ifBlank { "" }} dollars".trim()
            else base + "s"
        val t = tip.find(q)?.let { num(it.groupValues[1].ifBlank { it.groupValues[2] }) }
        val text = buildString {
            append("${fmt(localAmount, 2)} $localName ÷ ${fmt(perBase, 4)} = **${money(converted)} $baseName**.")
            if (t != null) {
                val total = converted * (1 + t / 100)
                append("\n\nWith a ${fmt(t)}% tip: ${money(converted)} × ${fmt(1 + t / 100, 2)} = **${money(total)} $baseName** ")
                append("(${fmt(localAmount * (1 + t / 100), 2)} $localName).")
            }
            append("\n\nThis uses the rate you gave; card and exchange fees are extra.")
        }
        return Result(text, true, "${fmt(localAmount, 2)} $localName = ${money(converted)} $baseName.") to emptySet()
    }

    /* ---------- water for a group ---------- */
    private val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "a" to 1, "an" to 1)
    private fun count(token: String): Int? = token.lowercase().toIntOrNull() ?: words[token.lowercase()]
    private fun water(q: String): Pair<Result, Set<String>>? {
        val perDay = Regex("(?i)$NUM\\s*(?:l|liters?|litres?)(?:\\s+of water)?\\s+(?:per|a|each)\\s+(?:person\\s+)?(?:per\\s+|a\\s+|each\\s+)?day").find(q) ?: return null
        val n = "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
        val people = listOf(
            Regex("(?i)\\b$n\\s+(?:of us|people|persons|hikers|adults|travell?ers)\\b"),
            Regex("(?i)\\b(?:family|group|party|team) of $n\\b"),
            Regex("(?i)\\bwe(?:'re| are) $n\\b"),
        ).firstNotNullOfOrNull { it.find(q)?.let { m -> count(m.groupValues[1]) } } ?: 1
        // "3 litres a day for 5 days": the day count is not the "a day" of the rate.
        val days = Regex("(?i)\\b$n\\s+(?:full\\s+)?days?\\b").findAll(q).firstOrNull { it.range.first !in perDay.range }
            ?.let { count(it.groupValues[1]) } ?: 1
        val litres = num(perDay.groupValues[1]) * people * days
        val text = "$people ${if (people == 1) "person" else "people"} × $days ${if (days == 1) "day" else "days"} × ${fmt(num(perDay.groupValues[1]))} L = **${fmt(litres)} litres**, " +
            "which weighs about **${fmt(litres)} kg** (${fmt(litres * 2.2046, 0)} lb): a litre of water weighs about 1 kg, plus the containers." +
            (if (people > 1) "\n\nSplit evenly, that is ${fmt(litres / people)} L (${fmt(litres / people)} kg) each." else "")
        return Result(text, true, "${fmt(litres)} L of water, about ${fmt(litres)} kg.") to emptySet()
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
        return Result(text, true, "${fmt(wh, 2)} Wh.") to
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
        return Result(text, true, fact, followUp = rest, guideId = guide, riskNote = risk) to emptySet()
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
        val text = buildString {
            append("${fmt(litres)} L ÷ ${fmt(per)} L/100 km × 100 = **${fmt(range, 0)} km** on a full tank.")
            trip.find(q)?.let { m ->
                val km = num(m.groupValues[1]) * (if (m.groupValues[2].lowercase().startsWith("mi")) 1.609344 else 1.0)
                val needed = km * per / 100
                append("\n\n")
                append(if (km <= range) "So **yes**, ${fmt(km, 0)} km fits, using about ${fmt(needed)} L and leaving about ${fmt(litres - needed)} L."
                    else "So **no**: ${fmt(km, 0)} km needs about ${fmt(needed)} L, ${fmt(needed - litres)} L more than the tank holds, so plan a fuel stop.")
            }
            append("\n\nReal consumption rises with speed, load, hills and air conditioning, so keep a reserve.")
        }
        return Result(text, true, "Range on ${fmt(litres)} L at ${fmt(per)} L/100 km = ${fmt(range, 0)} km.") to emptySet()
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
        val text = buildString {
            append("${fmt(km)} km ÷ ${fmt(kmh)} km/h = **${hours(need)}** of moving time.")
            // Ten minutes' rest an hour is the usual allowance for walkers and drivers alike.
            val withBreaks = need * 7 / 6
            append(" With 10-minute breaks each hour it takes about **${hours(withBreaks)}**.")
            timeLeft.find(q)?.let { m ->
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
        return Result(text, true, "${fmt(km)} km at ${fmt(kmh)} km/h = ${hours(need)}.") to emptySet()
    }

    /* ---------- the time in another city ---------- */
    private val clockTime = Regex("(?i)\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b")
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
        return Result(text, true, "${show(start)} in $fromName = ${show(there)}$day in $toName.") to emptySet()
    }

    /* ---------- the time between two clock times ---------- */
    private fun duration(q: String): Pair<Result, Set<String>>? {
        if (!Regex("(?i)\\bhow (?:many|much|long)\\b|\\bdaylight\\b|\\bduration\\b|\\btime between\\b").containsMatchIn(q)) return null
        // "What time is it in Tokyo when…" converts a time; it does not measure one.
        if (Regex("(?i)\\bwhat time\\b|\\btime (?:is it|there)\\b").containsMatchIn(q)) return null
        val times = clockTime.findAll(q).mapNotNull { t ->
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
        return Result(text, true, "${clock(times[0])} to ${clock(times[1])} = $length.") to emptySet()
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
        return Result(text, true, "${fmt(amount)} ${m.groupValues[2]} for $from = ${fmt(scaled)} ${m.groupValues[2]} for $to.") to emptySet()
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
        return Result(text, true, "Water boils at about ${fmt(celsius)}°C at ${fmt(metres, 0)} m.") to emptySet()
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
                "${fmt(ft, 0)} ft ${fmt(inches)} in = ${fmt(cm, 1)} cm.") to emptySet()
        }
        // Longest names first, and no match inside a longer unit: "60 mph in km/h" is not "km".
        val names = unitTable.flatMap { it.names.split('|') }.sortedByDescending { it.length }.joinToString("|")
        val m = Regex("(?i)$NUM\\s*($names)(?![\\w/])\\s+(?:is |are )?(?:in|to|into|as|in terms of)\\s+($names)(?![\\w/])").find(q) ?: return null
        val from = unitFor(m.groupValues[2]) ?: return null; val to = unitFor(m.groupValues[3]) ?: return null
        if (from.kind != to.kind || from == to) return null
        val value = num(m.groupValues[1]); val out = value * from.toBase / to.toBase
        return Result("${fmt(value)} ${from.label} is **${fmt(out, 2)} ${to.label}**.", true, "${fmt(value)} ${from.label} = ${fmt(out, 2)} ${to.label}.") to emptySet()
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
        return Result(text, true, "${fmt(p)}% of ${fmt(base, 2)} = ${fmt(part, 2)}.") to emptySet()
    }
}
