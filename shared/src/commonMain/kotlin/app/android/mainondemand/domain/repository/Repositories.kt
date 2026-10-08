package app.android.mainondemand.domain.repository

import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingDraft
import app.android.mainondemand.domain.model.BookingRequest
import app.android.mainondemand.domain.model.CustomerDetails
import app.android.mainondemand.domain.model.DraftSelection
import app.android.mainondemand.domain.model.Locality
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.domain.model.Professional
import app.android.mainondemand.domain.model.SearchCriteria
import app.android.mainondemand.domain.model.SearchResult
import app.android.mainondemand.domain.model.Slot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/** All suspend functions throw [app.android.mainondemand.domain.AppException] on failure. */
interface ProfessionalRepository {
    val localities: List<Locality>

    /** Bumps whenever slot availability changes (booking, cancellation, conflict). */
    val availabilityVersion: StateFlow<Int>

    suspend fun search(criteria: SearchCriteria): List<SearchResult>
    suspend fun professional(id: String): Professional

    /** Future slots of [professionalId] on [date], taken ones included with `isAvailable = false`. */
    suspend fun slots(professionalId: String, date: LocalDate): List<Slot>
}

interface BookingRepository {
    /** Single source of truth for booking lists and details. Null until first loaded. */
    val bookings: StateFlow<List<Booking>?>

    suspend fun refresh()

    /** Charges the (simulated) payment and confirms the booking, or throws without creating one. */
    suspend fun create(request: BookingRequest): Booking

    /** [bookings] changes only when the cancellation succeeds. */
    suspend fun cancel(bookingId: String): Booking
}

interface DraftRepository {
    val draft: StateFlow<BookingDraft>

    fun select(selection: DraftSelection)
    fun clearSelection()
    fun updateCustomer(customer: CustomerDetails)
    fun setPaymentMethod(method: PaymentMethod)
}
