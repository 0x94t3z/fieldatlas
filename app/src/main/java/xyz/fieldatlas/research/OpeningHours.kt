package xyz.fieldatlas.research

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reads the common forms of OpenStreetMap `opening_hours` ("Mo-Fr 08:00-18:00; Sa 09:00-14:00",
 * "Mo, We-Su 11:30-15:00, 18:00-22:00", "Fr-Sa 18:00-02:00", "24/7") and says whether a place
 * is open at a given moment by those recorded hours. Anything it cannot read fully (holidays
 * only, "sunrise", week numbers, comments) gives [Status.Unknown] rather than a guess.
 */
object OpeningHours {
    sealed interface Status {
        /** [until] is null when the place stays open past the end of the day's listed hours. */
        data class Open(val until: LocalTime?) : Status
        data object Closed : Status
        data object Unknown : Status
    }

    private val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private const val DAY = "(?:Mo|Tu|We|Th|Fr|Sa|Su|PH|SH)"
    private val dayPart = Regex("^($DAY(?:-$DAY)?(?:\\s*,\\s*$DAY(?:-$DAY)?)*)\\s+(.+)$")
    private val ruleBreak = Regex(";|(?<=\\d)\\s*,\\s*(?=$DAY\\b)")
    private val range = Regex("^(\\d{1,2}):(\\d{2})(?:-(\\d{1,2}):(\\d{2}))?(\\+?)$")

    /**
     * Minutes from midnight; [end] may pass 1440. [openEnd] is OpenStreetMap's "18:00+": open
     * until at least [end], so later than that is unknown rather than closed.
     */
    private data class Span(val start: Int, val end: Int, val openEnd: Boolean = false)

    /** The spans for each weekday (index 0 = Monday), or null when the text cannot be read. */
    private fun parse(hours: String): Array<List<Span>?>? {
        val text = hours.trim()
        if (text == "24/7") return Array(7) { listOf(Span(0, 1440)) }
        val week = arrayOfNulls<List<Span>>(7)
        // Rules end at ";", or at a comma between a time and the next day ("Mo-Fr 08:00-20:00, Sa
        // 09:00-19:00"); a comma inside a day list ("Mo, We-Su") or between times stays put.
        for (raw in text.split(ruleBreak).map(String::trim).filter(String::isNotEmpty)) {
            val (dayText, timeText) = dayPart.matchEntire(raw)?.destructured?.let { (d, t) -> d to t } ?: (null to raw)
            val selected = if (dayText == null) (0..6).toList() else daysOf(dayText) ?: return null
            // A holiday-only rule ("PH off") cannot be applied without a holiday calendar.
            if (selected.isEmpty()) continue
            val spans = when (timeText.trim().lowercase()) {
                "off", "closed" -> emptyList()
                else -> timeText.split(',').map(String::trim).map { part ->
                    val m = range.matchEntire(part) ?: return null
                    val (h1, m1, h2, m2, plus) = m.destructured
                    // "18:00" alone is a time, not a range; only "18:00+" may omit the end.
                    if (h2.isEmpty() && plus.isEmpty()) return null
                    if (h1.toInt() > 24 || m1.toInt() > 59 || (h2.isNotEmpty() && (h2.toInt() > 24 || m2.toInt() > 59))) return null
                    val start = h1.toInt() * 60 + m1.toInt()
                    var end = if (h2.isEmpty()) 1440 else h2.toInt() * 60 + m2.toInt() // "22:00+": open from 22:00, end unknown
                    if (end <= start && h2.isNotEmpty()) end += 1440 // past midnight
                    Span(start, end, openEnd = plus.isNotEmpty())
                }
            }
            // A later rule replaces earlier ones for the days it names, as in OpenStreetMap.
            selected.forEach { week[it] = spans }
        }
        return if (week.all { it == null }) null else week
    }

    // Holiday entries in a day list ("Mo-Su,PH") need a holiday calendar; the weekdays still apply.
    private fun daysOf(text: String): List<Int>? = text.split(',').map(String::trim).filterNot { it == "PH" || it == "SH" }.flatMap { item ->
        val parts = item.split('-')
        val from = days.indexOf(parts[0]).takeIf { it >= 0 } ?: return null
        val to = if (parts.size == 2) days.indexOf(parts[1]).takeIf { it >= 0 } ?: return null else from
        if (from <= to) (from..to).toList() else (from..6).toList() + (0..to).toList()
    }

    fun status(hours: String?, now: LocalDateTime): Status {
        if (hours.isNullOrBlank()) return Status.Unknown
        val week = parse(hours) ?: return Status.Unknown
        val today = now.dayOfWeek.ordinal
        val yesterday = (today + 6) % 7
        val minute = now.hour * 60 + now.minute
        week[today]?.firstOrNull { minute >= it.start && minute < it.end }?.let { return Status.Open(if (it.openEnd) null else endOf(it.end)) }
        // Last night's hours that run past midnight ("Fr 18:00-02:00" on Saturday at 01:00).
        week[yesterday]?.firstOrNull { it.end > 1440 && minute < it.end - 1440 }?.let { return Status.Open(if (it.openEnd) null else endOf(it.end - 1440)) }
        if (week[today]?.any { it.openEnd && minute >= it.start } == true) return Status.Unknown
        // "Fr 22:00+" may run into Saturday morning; before Saturday's own hours begin, nobody knows.
        if (week[yesterday]?.any { it.openEnd && it.end >= 1440 } == true &&
            week[today].orEmpty().none { minute >= it.start }) return Status.Unknown
        // In OpenStreetMap a day no rule names is closed ("Mo-Fr 09:00-17:00" means closed at weekends).
        return Status.Closed
    }

    private fun endOf(minutes: Int): LocalTime? = if (minutes >= 1440) null else LocalTime.of(minutes / 60, minutes % 60)

    fun dayName(day: DayOfWeek) = days[day.ordinal]
}
