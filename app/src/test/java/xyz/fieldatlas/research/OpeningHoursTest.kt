package xyz.fieldatlas.research

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.fieldatlas.research.OpeningHours.Status

class OpeningHoursTest {
    // 2026-10-03 is a Saturday.
    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime = // day 0 = Monday 2026-09-28
        java.time.LocalDate.of(2026, 9, 28).plusDays(day.toLong()).atTime(hour, minute)
    private fun status(hours: String, day: Int, hour: Int, minute: Int = 0) = OpeningHours.status(hours, at(day, hour, minute))

    @Test fun everyDayHours() {
        assertEquals(Status.Open(LocalTime.of(23, 0)), status("Mo-Su 12:00-23:00", 5, 14))
        assertEquals(Status.Closed, status("Mo-Su 12:00-23:00", 5, 23, 30))
        assertEquals(Status.Open(LocalTime.of(23, 0)), status("12:00-23:00", 2, 12))
    }
    @Test fun daysListsAndSplitShifts() {
        val hours = "Mo, We-Su 11:30-15:00, 18:00-22:00"
        assertEquals(Status.Closed, status(hours, 1, 12)) // Tuesday
        assertEquals(Status.Closed, status(hours, 2, 16)) // Wednesday between shifts
        assertEquals(Status.Open(LocalTime.of(22, 0)), status(hours, 2, 19))
        assertEquals(Status.Closed, status("Tu-Sa 18:00-23:00", 0, 19)) // Monday not named
    }
    @Test fun laterRulesOverrideAndOffDays() {
        val hours = "Mo-Sa 09:00-18:00; Sa 10:00-14:00; Su off"
        assertEquals(Status.Closed, status(hours, 5, 9, 30))
        assertEquals(Status.Open(LocalTime.of(14, 0)), status(hours, 5, 11))
        assertEquals(Status.Closed, status(hours, 6, 11))
        assertEquals(Status.Open(LocalTime.of(18, 0)), status("Mo-Fr 09:00-18:00; PH off", 0, 10))
    }
    @Test fun hoursPastMidnight() {
        assertEquals(Status.Open(LocalTime.of(2, 0)), status("Fr-Sa 18:00-02:00", 5, 1)) // Saturday 01:00, from Friday
        assertEquals(Status.Open(null), status("Mo-Su 10:00-24:00", 3, 23))
        assertEquals(Status.Closed, status("Fr-Sa 18:00-02:00", 0, 1)) // Monday 01:00, Sunday not named
    }
    @Test fun alwaysOpenAndUnreadable() {
        assertEquals(Status.Open(null), status("24/7", 3, 3))
        assertEquals(Status.Unknown, OpeningHours.status("sunrise-sunset", at(3, 12)))
        assertEquals(Status.Unknown, OpeningHours.status("Mo-Fr 09:00-17:00 \"by appointment\"", at(3, 12)))
        assertEquals(Status.Unknown, OpeningHours.status(null, at(3, 12)))
    }

    @Test fun holidaysInDayListsAndOpenEndedClosing() {
        // Barlys, Berlin: "Mo-Su,PH 10:00-18:00+" — the weekday hours still apply.
        assertEquals(Status.Open(null), status("Mo-Su,PH 10:00-18:00+", 6, 10, 22))
        // Past an open end it may still be open, so it is not called closed.
        assertEquals(Status.Unknown, status("Mo-Su,PH 10:00-18:00+", 6, 19))
        assertEquals(Status.Closed, status("Mo-Su,PH 10:00-18:00+", 6, 9))
        assertEquals(Status.Open(null), status("Fr 22:00+", 4, 23))
        assertEquals(Status.Unknown, status("Fr 22:00", 4, 23))
        assertEquals(Status.Unknown, status("Fr 22:00+", 5, 1))
        assertEquals(Status.Closed, status("Fr 22:00+; Sa 12:00-14:00", 5, 15))
        // A holiday rule alone is skipped; a holiday listed beside a day keeps that day's rule.
        assertEquals(Status.Open(java.time.LocalTime.of(21, 0)), status("Mo-Su 11:00-21:00; PH off", 2, 12))
        assertEquals(Status.Closed, status("Mo-Sa 09:00-18:00; PH,Su off", 6, 12))
        assertEquals(Status.Unknown, status("Mo-Xx 09:00-18:00", 0, 12))
    }
}
