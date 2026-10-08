package app.android.mainondemand.ui

import app.android.mainondemand.domain.model.BOOKING_DURATION
import app.android.mainondemand.domain.toIst
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import kotlin.time.Instant

private fun String.titleCase() = lowercase().replaceFirstChar { it.uppercase() }

fun LocalDate.weekdayShort(): String = dayOfWeek.name.take(3).titleCase()

fun LocalDate.weekdayLong(): String = dayOfWeek.name.titleCase()

fun LocalDate.monthShort(): String = month.name.take(3).titleCase()

/** "Thu, 9 Oct" */
fun LocalDate.formatShort(): String = "${weekdayShort()}, $day ${monthShort()}"

/** "Thursday 9 October" — for screen readers. */
fun LocalDate.formatSpoken(): String = "${weekdayLong()} $day ${month.name.titleCase()}"

/** "8 AM", "12 PM", "7 PM" */
fun formatHour(hour: Int): String = "${to12Hour(hour)} ${meridiem(hour)}"

/** "10:00 AM" in IST. */
fun Instant.formatIstTime(): String {
    val local = toIst()
    return "${to12Hour(local.hour)}:${local.minute.toString().padStart(2, '0')} ${meridiem(local.hour)}"
}

/** "Thu, 9 Oct" in IST. */
fun Instant.formatIstDate(): String = toIst().date.formatShort()

/** "10:00 AM – 11:00 AM IST" for a 60-minute visit starting at this instant. */
fun Instant.formatIstSlot(): String = "${formatIstTime()} – ${(this + BOOKING_DURATION).formatIstTime()} IST"

/** "Thu, 9 Oct · 10:00 AM IST" */
fun Instant.formatIstDateTime(): String = "${formatIstDate()} · ${formatIstTime()} IST"

fun Double.formatRating(): String {
    val tenths = (this * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

private fun to12Hour(hour: Int): Int = when (val h = hour % 12) {
    0 -> 12
    else -> h
}

private fun meridiem(hour: Int): String = if (hour < 12) "AM" else "PM"
