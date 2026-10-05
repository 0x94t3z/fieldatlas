package xyz.fieldatlas.research

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Whether a place is open at a given moment by its OpenStreetMap `opening_hours` value, for the
 * forms map data mostly uses: weekday ranges and lists, several time ranges, times past
 * midnight, "off", "24/7" and open ends such as "18:00+".
 *
 * The value is read as a sequence of tokens and then as rules. Following the OpenStreetMap
 * specification (https://wiki.openstreetmap.org/wiki/Key:opening_hours/specification), when
 * several rules name the same weekday the last one decides it, and a weekday no rule names is
 * closed. Anything outside these forms (sunrise, week numbers, months, comments) is reported as
 * [Status.Unknown]: a wrong "closed" would send a traveller away from an open place.
 */
object OpeningHours {
    sealed interface Status {
        /** [until] is null when the place stays open past the end of the day's listed hours. */
        data class Open(val until: LocalTime?) : Status
        data object Closed : Status
        data object Unknown : Status
    }

    private val dayCodes = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    /** Minutes after the day's midnight; [to] may exceed 1440 for hours that run into the next day. */
    private data class Interval(val from: Int, val to: Int, val openEnded: Boolean)

    /**
     * [days] null means every day; an empty [intervals] means closed on those days. An [additional]
     * rule (joined by a comma) adds its hours to what earlier rules gave those days, as the
     * specification's "additional rule" does: "Mo-Fr 08:00-13:00; PH off, Mo-Fr 14:00-18:30" is
     * open in the morning and the afternoon.
     */
    private class Rule(val days: Set<Int>?, val intervals: List<Interval>, val additional: Boolean)

    private sealed interface Token {
        data class Day(val code: String) : Token
        data class Time(val minutes: Int, val plus: Boolean) : Token
        data object Dash : Token
        data object Comma : Token
        data object Semicolon : Token
        data object Off : Token
        data object Always : Token
    }

    private val lexer = Regex("\\s*(?:(24/7)|(Mo|Tu|We|Th|Fr|Sa|Su|PH|SH)\\b|(\\d{1,2}):(\\d{2})(\\+?)|(-)|(,)|(;)|(off|closed)\\b)", RegexOption.IGNORE_CASE)

    /** The tokens of [text], or null at the first thing this reader does not understand. */
    private fun tokens(text: String): List<Token>? {
        val out = ArrayList<Token>()
        var at = 0
        while (at < text.length) {
            if (text[at].isWhitespace()) { at++; continue }
            val m = lexer.find(text, at)?.takeIf { it.range.first == at } ?: return null
            val g = m.groups
            out += when {
                g[1] != null -> Token.Always
                // "mo" -> "Mo"; holiday codes stay upper case ("ph" -> "PH").
                g[2] != null -> Token.Day(g[2]!!.value.let { v -> if (v.equals("PH", true) || v.equals("SH", true)) v.uppercase() else v[0].uppercaseChar() + v.substring(1).lowercase() })
                g[3] != null -> {
                    val hour = g[3]!!.value.toInt(); val minute = g[4]!!.value.toInt()
                    if (hour > 24 || minute > 59) return null
                    Token.Time(hour * 60 + minute, g[5]!!.value == "+")
                }
                g[6] != null -> Token.Dash
                g[7] != null -> Token.Comma
                g[8] != null -> Token.Semicolon
                else -> Token.Off
            }
            at = m.range.last + 1
        }
        return out
    }

    // A rule ends at ";", or at a comma between a time (or "off") and the next day list: "Mo-Fr
    // 08:00-20:00, Sa 09:00-19:00". A comma inside a day list ("Mo, We-Su") or between two times
    // stays inside the rule.
    private val ruleEnd = Regex(";|(?<=[0-9+]|off|closed)\\s*,\\s*(?=(?:Mo|Tu|We|Th|Fr|Sa|Su|PH|SH)\\b)", RegexOption.IGNORE_CASE)
    // A rule for school or public holidays only ("PH off", "PH open", 'PH 10:00-14:00 "Bank Holidays"')
    // needs a holiday calendar; it is set aside whatever follows, so it cannot spoil the others.
    // Only a comma joins weekdays to it ("PH,Su off"); "PH Su off" is still a holiday rule.
    private val holidayOnly = Regex("^\\s*(?:PH|SH)(?:\\s*,\\s*(?:PH|SH))*(?!\\s*,\\s*(?:Mo|Tu|We|Th|Fr|Sa|Su)\\b)(?:\\s|$)", RegexOption.IGNORE_CASE)

    /** The rules in order, or null when a rule is one this reader does not understand. */
    private fun rules(text: String): List<Rule>? {
        val rules = ArrayList<Rule>()
        var start = 0
        var additional = false
        val ends = ruleEnd.findAll(text).toList() + null
        for (end in ends) {
            val part = text.substring(start, end?.range?.first ?: text.length)
            if (part.isNotBlank() && !holidayOnly.containsMatchIn(part)) rules += rule(part, additional) ?: return null
            if (end != null) { additional = end.value.trim() != ";"; start = end.range.last + 1 }
        }
        return rules.takeIf { it.isNotEmpty() }
    }

    /** One rule: optional weekdays, then "off", "24/7" or time ranges. */
    private fun rule(text: String, additional: Boolean): Rule? {
        val tokens = tokens(text.trim()) ?: return null
        var i = 0
        // Weekdays first, if any: "Mo-Fr", "Mo, We-Su", "Sa,PH".
        var days: MutableSet<Int>? = null
        while (i < tokens.size && tokens[i] is Token.Day) {
            val first = (tokens[i] as Token.Day).code
            var last = first
            if (i + 2 < tokens.size && tokens[i + 1] == Token.Dash && tokens[i + 2] is Token.Day) {
                last = (tokens[i + 2] as Token.Day).code
                i += 2
            }
            i++
            if (days == null) days = mutableSetOf()
            // A holiday beside weekdays ("Mo-Su,PH") needs a calendar; the weekdays still count.
            if (first != "PH" && first != "SH" && last != "PH" && last != "SH") {
                val from = dayCodes.indexOf(first); val to = dayCodes.indexOf(last)
                if (from < 0 || to < 0) return null
                var d = from
                while (true) { days += d; if (d == to) break; d = (d + 1) % 7 }
            } else if (first != last) return null // a range between a weekday and a holiday ("Mo-PH") has no meaning
            if (i < tokens.size && tokens[i] == Token.Comma && i + 1 < tokens.size && tokens[i + 1] is Token.Day) i++ else break
        }
        // Then what applies on them: "off", "24/7", or one or more time ranges.
        val intervals = ArrayList<Interval>()
        when (tokens.getOrNull(i)) {
            Token.Off -> i++
            Token.Always -> { intervals += Interval(0, 1440, openEnded = false); i++ }
            is Token.Time -> while (true) {
                val start = tokens[i] as Token.Time
                if (start.plus) {
                    intervals += Interval(start.minutes, 1440, openEnded = true); i++
                } else {
                    if (tokens.getOrNull(i + 1) != Token.Dash) return null // a lone time is not a range
                    val end = tokens.getOrNull(i + 2) as? Token.Time ?: return null
                    val to = if (end.minutes <= start.minutes) end.minutes + 1440 else end.minutes
                    intervals += Interval(start.minutes, to, openEnded = end.plus); i += 3
                }
                if (tokens.getOrNull(i) == Token.Comma && tokens.getOrNull(i + 1) is Token.Time) i++ else break
            }
            else -> return null
        }
        if (i != tokens.size) return null
        // Only holidays named ("PH,SH 10:00-12:00"): nothing to say about ordinary days.
        if (days != null && days.isEmpty()) return Rule(emptySet(), intervals, additional)
        return Rule(days, intervals, additional)
    }

    /** What the rules say for one weekday (0 = Monday): the last rule naming it, or null if none does. */
    private fun on(rules: List<Rule>, day: Int): List<Interval>? {
        var hours: List<Interval>? = null
        for (rule in rules) {
            if (rule.days != null && day !in rule.days) continue
            // A normal rule, or "off", replaces what came before for its days; an additional one adds.
            hours = if (rule.additional && rule.intervals.isNotEmpty() && hours != null) hours + rule.intervals else rule.intervals
        }
        return hours
    }

    fun status(hours: String?, now: LocalDateTime): Status {
        if (hours.isNullOrBlank()) return Status.Unknown
        val rules = rules(hours) ?: return Status.Unknown
        val day = now.dayOfWeek.ordinal
        val minute = now.hour * 60 + now.minute
        val today = on(rules, day).orEmpty()
        val yesterday = on(rules, (day + 6) % 7).orEmpty()

        today.firstOrNull { minute >= it.from && minute < it.to }?.let { return Status.Open(closing(it, it.to)) }
        // Hours that began yesterday and run past midnight: "Fr 18:00-02:00" at 01:00 on Saturday.
        yesterday.firstOrNull { it.to > 1440 && minute < it.to - 1440 }?.let { return Status.Open(closing(it, it.to - 1440)) }
        // "18:00+" says it is open until at least 18:00; after that nobody knows.
        if (today.any { it.openEnded && minute >= it.from }) return Status.Unknown
        if (yesterday.any { it.openEnded && it.to >= 1440 } && today.none { minute >= it.from }) return Status.Unknown
        return Status.Closed
    }

    private fun closing(interval: Interval, minutes: Int): LocalTime? =
        if (interval.openEnded || minutes >= 1440) null else LocalTime.of(minutes / 60, minutes % 60)

    fun dayName(day: DayOfWeek) = dayCodes[day.ordinal]
}
