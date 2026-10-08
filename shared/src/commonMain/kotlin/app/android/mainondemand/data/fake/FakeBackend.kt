package app.android.mainondemand.data.fake

import app.android.mainondemand.data.persistence.BackendSnapshot
import app.android.mainondemand.data.persistence.BookingDto
import app.android.mainondemand.data.persistence.KeyValueStore
import app.android.mainondemand.data.persistence.snapshotJson
import app.android.mainondemand.data.persistence.toDto
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.BOOKING_HORIZON_DAYS
import app.android.mainondemand.domain.BookingRules
import app.android.mainondemand.domain.CustomerValidator
import app.android.mainondemand.domain.SERVICE_HOURS
import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingRequest
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.domain.model.Locality
import app.android.mainondemand.domain.model.Professional
import app.android.mainondemand.domain.model.SearchCriteria
import app.android.mainondemand.domain.model.SearchResult
import app.android.mainondemand.domain.model.Slot
import app.android.mainondemand.domain.slotStart
import app.android.mainondemand.domain.toIst
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

enum class ApiCall { SEARCH, PROFESSIONAL, SLOTS, BOOKINGS, CREATE_BOOKING, CANCEL_BOOKING }

/**
 * Stands in for the server: seeded catalogue, simulated latency, injectable faults and one
 * consistent, persisted state. Slot availability is derived from bookings rather than stored,
 * so a cancellation releases its slot by construction.
 */
class FakeBackend(
    private val clock: Clock,
    private val store: KeyValueStore,
    private val faults: FaultController,
    private val random: Random = Random.Default,
    private val latency: suspend (ApiCall) -> Unit = { delay(defaultLatencyMs(it)) },
) {
    private class State(val bookings: MutableList<Booking>, val takenSlots: MutableSet<String>)

    private val mutex = Mutex()
    private var state: State? = null

    private val _availabilityVersion = MutableStateFlow(0)
    val availabilityVersion: StateFlow<Int> = _availabilityVersion.asStateFlow()

    val localities: List<Locality> get() = SeedData.localities

    suspend fun search(criteria: SearchCriteria): List<SearchResult> {
        latency(ApiCall.SEARCH)
        if (faults.consume(Fault.SEARCH_FAILURE)) throw AppException.Network()
        if (faults.consume(Fault.NO_RESULTS)) return emptyList()
        return mutex.withLock {
            val state = loadedState()
            val now = clock.now()
            val hours = criteria.startHour?.let(::listOf) ?: SERVICE_HOURS.toList()
            SeedData.professionals.mapNotNull { pro ->
                val price = pro.prices[criteria.service] ?: return@mapNotNull null
                if (pro.localityId != criteria.localityId) return@mapNotNull null
                if (criteria.minRating != null && pro.rating < criteria.minRating) return@mapNotNull null
                if (criteria.maxPrice != null && price > criteria.maxPrice) return@mapNotNull null
                val open = hours.map { slotStart(criteria.date, it) }.filter { state.isOpen(pro, it, now) }
                if (open.isEmpty()) null else SearchResult(pro, price, open.first(), open.size)
            }.sortedWith(compareByDescending<SearchResult> { it.professional.rating }.thenBy { it.price })
        }
    }

    suspend fun professional(id: String): Professional {
        latency(ApiCall.PROFESSIONAL)
        return findProfessional(id)
    }

    suspend fun slots(professionalId: String, date: LocalDate): List<Slot> {
        latency(ApiCall.SLOTS)
        val pro = findProfessional(professionalId)
        return mutex.withLock {
            val state = loadedState()
            val now = clock.now()
            SERVICE_HOURS.map { slotStart(date, it) }
                .filter { it > now }
                .map { Slot(pro.id, it, state.isOpen(pro, it, now)) }
        }
    }

    suspend fun bookings(): List<Booking> {
        latency(ApiCall.BOOKINGS)
        return mutex.withLock { loadedState().bookings.sortedByDescending { it.start } }
    }

    /** Holds the slot, takes the simulated payment, then confirms. Nothing is created on failure. */
    suspend fun createBooking(request: BookingRequest): Booking {
        latency(ApiCall.CREATE_BOOKING)
        val pro = findProfessional(request.professionalId)
        val price = pro.prices[request.service]
            ?: throw AppException.InvalidRequest("${pro.name} doesn't offer ${request.service.label.lowercase()}.")
        if (!CustomerValidator.validate(request.customer).isValid) {
            throw AppException.InvalidRequest("Customer details are incomplete.")
        }
        return mutex.withLock {
            val state = loadedState()
            val now = clock.now()
            if (faults.consume(Fault.SLOT_CONFLICT)) {
                // Another customer really takes the slot, so every screen sees it as gone.
                state.takenSlots += slotKey(pro.id, request.slotStart)
                commit(state, availabilityChanged = true)
                throw AppException.SlotUnavailable()
            }
            if (!state.isOpen(pro, request.slotStart, now)) {
                // Availability on the client is stale; nudge it to refresh.
                _availabilityVersion.update { it + 1 }
                throw AppException.SlotUnavailable()
            }
            if (faults.consume(Fault.PAYMENT_FAILURE)) throw AppException.PaymentDeclined()

            val booking = Booking(
                id = newBookingId(state),
                professionalId = pro.id,
                professionalName = pro.name,
                localityName = localities.first { it.id == pro.localityId }.name,
                service = request.service,
                start = request.slotStart,
                customer = request.customer.copy(
                    name = request.customer.name.trim(),
                    address = request.customer.address.trim(),
                ),
                amount = price,
                paymentMethod = request.paymentMethod,
                paymentRef = "${request.paymentMethod.name}-${randomCode(10)}",
                status = BookingStatus.CONFIRMED,
                createdAt = now,
            )
            state.bookings += booking
            commit(state, availabilityChanged = true)
            booking
        }
    }

    suspend fun cancelBooking(bookingId: String): Booking {
        latency(ApiCall.CANCEL_BOOKING)
        if (faults.consume(Fault.CANCELLATION_FAILURE)) throw AppException.Network()
        return mutex.withLock {
            val state = loadedState()
            val now = clock.now()
            val index = state.bookings.indexOfFirst { it.id == bookingId }
            if (index < 0) throw AppException.NotFound("We couldn't find booking $bookingId.")
            val booking = state.bookings[index]
            when {
                booking.status == BookingStatus.CANCELLED ->
                    throw AppException.CancellationNotAllowed("This booking is already cancelled.")

                !BookingRules.canCancel(booking, now) ->
                    throw AppException.CancellationNotAllowed("Bookings can't be cancelled once they've started.")
            }
            val cancelled = booking.copy(
                status = BookingStatus.CANCELLED,
                cancelledAt = now,
                refundAmount = BookingRules.refundAmount(booking, now),
            )
            state.bookings[index] = cancelled
            commit(state, availabilityChanged = true)
            cancelled
        }
    }

    private fun findProfessional(id: String): Professional =
        SeedData.professionals.firstOrNull { it.id == id }
            ?: throw AppException.NotFound("This professional is no longer listed.")

    private fun State.isOpen(pro: Professional, start: Instant, now: Instant): Boolean =
        start > now &&
            start < now + BOOKING_HORIZON_DAYS.days &&
            !isBusyElsewhere(pro, start) &&
            slotKey(pro.id, start) !in takenSlots &&
            bookings.none { it.professionalId == pro.id && it.start == start && it.status == BookingStatus.CONFIRMED }

    /** Deterministic "already booked by someone else" pattern, stable across restarts. */
    private fun isBusyElsewhere(pro: Professional, start: Instant): Boolean {
        val local = start.toIst()
        var hash = SeedData.professionals.indexOf(pro) * 73 + local.date.toEpochDays().toInt() * 31 + local.hour * 17
        hash = hash xor (hash ushr 3)
        hash *= 0x45d9f3b
        hash = hash xor (hash ushr 7)
        return hash.mod(4) == 0
    }

    private fun loadedState(): State = state ?: (restore() ?: seed()).also { state = it }

    private fun restore(): State? {
        val raw = store.getString(STATE_KEY) ?: return null
        return try {
            val snapshot = snapshotJson.decodeFromString<BackendSnapshot>(raw)
            State(snapshot.bookings.map(BookingDto::toDomain).toMutableList(), snapshot.takenSlots.toMutableSet())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun seed(): State =
        State(SeedData.bookings(clock.now()).toMutableList(), mutableSetOf()).also(::persist)

    private fun commit(state: State, availabilityChanged: Boolean) {
        persist(state)
        if (availabilityChanged) _availabilityVersion.update { it + 1 }
    }

    private fun persist(state: State) {
        val snapshot = BackendSnapshot(state.bookings.map { it.toDto() }, state.takenSlots.toList())
        store.putString(STATE_KEY, snapshotJson.encodeToString(snapshot))
    }

    private fun newBookingId(state: State): String {
        while (true) {
            val id = "MB-${randomCode(6)}"
            if (state.bookings.none { it.id == id }) return id
        }
    }

    private fun randomCode(length: Int): String =
        buildString { repeat(length) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }

    companion object {
        private const val STATE_KEY = "fake_backend_state_v1"
        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        private fun slotKey(professionalId: String, start: Instant) = "$professionalId@${start.epochSeconds}"

        fun defaultLatencyMs(call: ApiCall): Long = when (call) {
            ApiCall.SEARCH -> 900
            ApiCall.PROFESSIONAL, ApiCall.SLOTS -> 600
            ApiCall.BOOKINGS -> 700
            ApiCall.CREATE_BOOKING -> 1600
            ApiCall.CANCEL_BOOKING -> 1200
        }
    }
}
