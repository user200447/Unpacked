package com.jassemsendi.unpacked.ui.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormatBuilder
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Every time on screen goes through here, so all of them read in the device's zone and one style.

private fun DateTimeFormatBuilder.WithDateTime.dateToMinute() {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
}

private val minuteFormat = LocalDateTime.Format { dateToMinute() }

private val secondFormat =
    LocalDateTime.Format {
        dateToMinute()
        char(':')
        second()
    }

/** To the minute, such as "2026-10-27 10:12": enough for a list. */
fun Instant.toDeviceMinute(timeZone: TimeZone): String = toLocalDateTime(timeZone).format(minuteFormat)

/** To the second, such as "2026-10-27 10:12:05": what a record's own time is shown as. */
fun Instant.toDeviceSecond(timeZone: TimeZone): String = toLocalDateTime(timeZone).format(secondFormat)
