package app.android.mainondemand.domain

import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingStatus
import kotlin.time.Instant

enum class BookingPhase(val label: String) {
    UPCOMING("Upcoming"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
}

object BookingRules {

    /** Only confirmed bookings that have not started can be cancelled. */
    fun canCancel(booking: Booking, now: Instant): Boolean =
        booking.status == BookingStatus.CONFIRMED && now < booking.start

    /** Confirmed future bookings get a full refund; nothing else is refundable. */
    fun refundAmount(booking: Booking, now: Instant): Int =
        if (canCancel(booking, now)) booking.amount else 0

    fun phase(booking: Booking, now: Instant): BookingPhase = when {
        booking.status == BookingStatus.CANCELLED -> BookingPhase.CANCELLED
        now < booking.start -> BookingPhase.UPCOMING
        now < booking.end -> BookingPhase.IN_PROGRESS
        else -> BookingPhase.COMPLETED
    }
}
