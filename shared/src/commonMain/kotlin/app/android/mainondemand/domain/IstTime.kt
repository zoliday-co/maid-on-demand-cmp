package app.android.mainondemand.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** All booking times are scheduled and displayed in IST, whatever the device zone is. */
val IST: TimeZone = TimeZone.of("Asia/Kolkata")

/** First and last bookable start hour (IST). The last visit ends at 20:00. */
val SERVICE_HOURS: IntRange = 8..19

/** How many days ahead, including today, can be booked. */
const val BOOKING_HORIZON_DAYS = 14

fun Instant.toIst(): LocalDateTime = toLocalDateTime(IST)

fun Clock.todayIst(): LocalDate = now().toIst().date

fun slotStart(date: LocalDate, hour: Int): Instant = LocalDateTime(date, LocalTime(hour, 0)).toInstant(IST)

fun bookableDates(today: LocalDate): List<LocalDate> =
    List(BOOKING_HORIZON_DAYS) { today.plus(it, DateTimeUnit.DAY) }

/** Start hours on [date] that are still in the future. */
fun futureHours(date: LocalDate, now: Instant): List<Int> =
    SERVICE_HOURS.filter { slotStart(date, it) > now }
