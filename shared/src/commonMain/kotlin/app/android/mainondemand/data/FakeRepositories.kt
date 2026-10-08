package app.android.mainondemand.data

import app.android.mainondemand.data.fake.FakeBackend
import app.android.mainondemand.data.persistence.DraftSnapshot
import app.android.mainondemand.data.persistence.KeyValueStore
import app.android.mainondemand.data.persistence.snapshotJson
import app.android.mainondemand.data.persistence.toDto
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
import app.android.mainondemand.domain.repository.BookingRepository
import app.android.mainondemand.domain.repository.DraftRepository
import app.android.mainondemand.domain.repository.ProfessionalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException

class FakeProfessionalRepository(private val backend: FakeBackend) : ProfessionalRepository {
    override val localities: List<Locality> get() = backend.localities
    override val availabilityVersion: StateFlow<Int> get() = backend.availabilityVersion

    override suspend fun search(criteria: SearchCriteria): List<SearchResult> = backend.search(criteria)
    override suspend fun professional(id: String): Professional = backend.professional(id)
    override suspend fun slots(professionalId: String, date: LocalDate): List<Slot> =
        backend.slots(professionalId, date)
}

class FakeBookingRepository(private val backend: FakeBackend) : BookingRepository {
    private val _bookings = MutableStateFlow<List<Booking>?>(null)
    override val bookings: StateFlow<List<Booking>?> = _bookings.asStateFlow()

    override suspend fun refresh() {
        _bookings.value = backend.bookings()
    }

    override suspend fun create(request: BookingRequest): Booking {
        val booking = backend.createBooking(request)
        // If the list was never loaded, leave it null so the next refresh fetches everything.
        _bookings.update { current -> current?.let { (it + booking).sortedByDescending(Booking::start) } }
        return booking
    }

    override suspend fun cancel(bookingId: String): Booking {
        val cancelled = backend.cancelBooking(bookingId)
        _bookings.update { current -> current?.map { if (it.id == cancelled.id) cancelled else it } }
        return cancelled
    }
}

/** Keeps the draft in memory for the session and the customer's details across restarts. */
class PersistentDraftRepository(private val store: KeyValueStore) : DraftRepository {
    private val _draft = MutableStateFlow(restore())
    override val draft: StateFlow<BookingDraft> = _draft.asStateFlow()

    override fun select(selection: DraftSelection) = _draft.update { it.copy(selection = selection) }

    override fun clearSelection() = _draft.update { it.copy(selection = null) }

    override fun updateCustomer(customer: CustomerDetails) {
        _draft.update { it.copy(customer = customer) }
        persist()
    }

    override fun setPaymentMethod(method: PaymentMethod) {
        _draft.update { it.copy(paymentMethod = method) }
        persist()
    }

    private fun restore(): BookingDraft {
        val raw = store.getString(DRAFT_KEY) ?: return BookingDraft()
        return try {
            val snapshot = snapshotJson.decodeFromString<DraftSnapshot>(raw)
            BookingDraft(
                customer = snapshot.customer.toDomain(),
                paymentMethod = PaymentMethod.valueOf(snapshot.paymentMethod),
            )
        } catch (_: SerializationException) {
            BookingDraft()
        } catch (_: IllegalArgumentException) {
            BookingDraft()
        }
    }

    private fun persist() {
        val draft = _draft.value
        store.putString(
            DRAFT_KEY,
            snapshotJson.encodeToString(DraftSnapshot(draft.customer.toDto(), draft.paymentMethod.name)),
        )
    }

    private companion object {
        const val DRAFT_KEY = "booking_draft_v1"
    }
}
