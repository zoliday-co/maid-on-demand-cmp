package app.android.mainondemand

import app.android.mainondemand.data.FakeBookingRepository
import app.android.mainondemand.data.FakeProfessionalRepository
import app.android.mainondemand.data.PersistentDraftRepository
import app.android.mainondemand.data.fake.ApiCall
import app.android.mainondemand.data.fake.FakeBackend
import app.android.mainondemand.data.fake.FaultController
import app.android.mainondemand.data.persistence.InMemoryKeyValueStore
import app.android.mainondemand.data.persistence.KeyValueStore
import app.android.mainondemand.domain.model.BookingRequest
import app.android.mainondemand.domain.model.CustomerDetails
import app.android.mainondemand.domain.model.DraftSelection
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.domain.model.Slot
import app.android.mainondemand.domain.todayIst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Clock
import kotlin.time.Instant

/** 10:00 IST on Thursday 8 October 2026. */
val TEST_NOW: Instant = Instant.parse("2026-10-08T04:30:00Z")

/** Seeded confirmed booking: Lakshmi Devi (p1), cleaning, tomorrow 10:00 IST. */
const val SEEDED_UPCOMING_ID = "MB-7KQ2XA"

val VALID_CUSTOMER = CustomerDetails(
    name = "Priya Nair",
    mobile = "9812345678",
    address = "14, 3rd Cross, Indiranagar, Bengaluru 560038",
)

class MutableClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** The real fake backend and repositories on virtual time, with every API call recorded. */
class TestEnvironment(
    val store: KeyValueStore = InMemoryKeyValueStore(),
    val clock: MutableClock = MutableClock(TEST_NOW),
) {
    val faults = FaultController()
    val calls = mutableListOf<ApiCall>()

    /** Per-call latency in virtual milliseconds; tests override it to reorder responses. */
    var latencyMs: (ApiCall) -> Long = { 100 }

    private val backend = FakeBackend(
        clock = clock,
        store = store,
        faults = faults,
        random = Random(42),
        latency = { call ->
            calls += call
            delay(latencyMs(call))
        },
    )
    val professionals = FakeProfessionalRepository(backend)
    val bookings = FakeBookingRepository(backend)
    val draft = PersistentDraftRepository(store)

    val tomorrow: LocalDate get() = clock.todayIst().plus(1, DateTimeUnit.DAY)

    fun count(call: ApiCall): Int = calls.count { it == call }

    suspend fun firstOpenSlot(professionalId: String = "p1", date: LocalDate = tomorrow): Slot =
        professionals.slots(professionalId, date).first { it.isAvailable }

    fun request(slot: Slot, service: ServiceType = ServiceType.CLEANING) = BookingRequest(
        professionalId = slot.professionalId,
        service = service,
        slotStart = slot.start,
        customer = VALID_CUSTOMER,
        paymentMethod = PaymentMethod.UPI,
    )

    /** Puts a ready-to-pay draft in place, as the profile screen would. */
    fun selectForCheckout(slot: Slot) {
        draft.select(
            DraftSelection(
                professionalId = slot.professionalId,
                professionalName = "Lakshmi Devi",
                localityName = "Indiranagar",
                service = ServiceType.CLEANING,
                price = 249,
                slotStart = slot.start,
            ),
        )
        draft.updateCustomer(VALID_CUSTOMER)
    }
}

/** ViewModels launch on Dispatchers.Main; point it at the test scheduler `runTest` picks up. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun installMainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}
