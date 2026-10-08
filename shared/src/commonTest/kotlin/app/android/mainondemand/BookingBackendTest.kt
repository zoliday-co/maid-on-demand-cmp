package app.android.mainondemand

import app.android.mainondemand.data.fake.Fault
import app.android.mainondemand.data.fake.FaultMode
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.domain.model.SearchCriteria
import app.android.mainondemand.domain.model.ServiceType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Repository + fake backend behaviour: the rules every screen relies on. */
class BookingBackendTest {

    @Test
    fun seedCoversTheAssignmentMinimums() = runTest {
        val env = TestEnvironment()
        assertTrue(env.professionals.localities.size >= 2)

        val found = env.professionals.localities.flatMap { locality ->
            ServiceType.entries.flatMap { service ->
                env.professionals.search(SearchCriteria(locality.id, service, env.tomorrow))
            }
        }
        assertTrue(found.map { it.professional.id }.distinct().size >= 6, "at least six professionals")
        assertEquals(ServiceType.entries.toSet(), found.flatMap { it.professional.services }.toSet())

        env.bookings.refresh()
        val statuses = env.bookings.bookings.value!!.map { it.status }
        assertTrue(BookingStatus.CONFIRMED in statuses && BookingStatus.CANCELLED in statuses)
    }

    @Test
    fun searchAppliesLocalityServiceRatingPriceAndTime() = runTest {
        val env = TestEnvironment()
        val base = SearchCriteria("indiranagar", ServiceType.CLEANING, env.tomorrow)

        val all = env.professionals.search(base)
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.professional.localityId == "indiranagar" && ServiceType.CLEANING in it.professional.prices })

        val filtered = env.professionals.search(base.copy(minRating = 4.5, maxPrice = 300))
        assertTrue(filtered.all { it.professional.rating >= 4.5 && it.price <= 300 })
        assertTrue(filtered.size < all.size)

        // The seeded booking occupies Lakshmi Devi (p1) tomorrow at 10:00.
        val atTen = env.professionals.search(base.copy(startHour = 10))
        assertTrue(atTen.none { it.professional.id == "p1" })
    }

    @Test
    fun successfulBookingIsConfirmedAndTakesTheSlot() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val before = env.bookings.bookings.value!!.size
        val slot = env.firstOpenSlot()

        val booking = env.bookings.create(env.request(slot))

        assertTrue(booking.id.matches(Regex("MB-[A-Z2-9]{6}")))
        assertEquals(BookingStatus.CONFIRMED, booking.status)
        assertEquals(249, booking.amount)
        assertEquals(60.minutes, booking.end - booking.start)
        assertEquals(before + 1, env.bookings.bookings.value!!.size)
        assertFalse(env.professionals.slots("p1", env.tomorrow).first { it.start == slot.start }.isAvailable)
    }

    @Test
    fun failedPaymentCreatesNoBookingAndLeavesTheSlotOpen() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val before = env.bookings.bookings.value
        val slot = env.firstOpenSlot()
        env.faults.set(Fault.PAYMENT_FAILURE, FaultMode.ONCE)

        assertFailsWith<AppException.PaymentDeclined> { env.bookings.create(env.request(slot)) }

        assertEquals(before, env.bookings.bookings.value)
        env.bookings.refresh()
        assertEquals(before, env.bookings.bookings.value)
        assertTrue(env.professionals.slots("p1", env.tomorrow).first { it.start == slot.start }.isAvailable)

        // "Next call" faults disarm themselves, so the retry goes through.
        assertEquals(BookingStatus.CONFIRMED, env.bookings.create(env.request(slot)).status)
    }

    @Test
    fun slotConflictRejectsTheBookingAndMarksTheSlotTaken() = runTest {
        val env = TestEnvironment()
        val slot = env.firstOpenSlot()
        val versionBefore = env.professionals.availabilityVersion.value
        env.faults.set(Fault.SLOT_CONFLICT, FaultMode.ONCE)

        assertFailsWith<AppException.SlotUnavailable> { env.bookings.create(env.request(slot)) }

        assertFalse(env.professionals.slots("p1", env.tomorrow).first { it.start == slot.start }.isAvailable)
        assertTrue(env.professionals.availabilityVersion.value > versionBefore, "screens are told to refresh")
        // The slot is genuinely gone, not just for one call.
        assertFailsWith<AppException.SlotUnavailable> { env.bookings.create(env.request(slot)) }
    }

    @Test
    fun theSameSlotCannotBeBookedTwice() = runTest {
        val env = TestEnvironment()
        val slot = env.firstOpenSlot()
        env.bookings.create(env.request(slot))
        assertFailsWith<AppException.SlotUnavailable> { env.bookings.create(env.request(slot)) }
    }

    @Test
    fun pastSlotsCannotBeBooked() = runTest {
        val env = TestEnvironment()
        val slot = env.firstOpenSlot()
        env.clock.instant = slot.start
        assertFailsWith<AppException.SlotUnavailable> { env.bookings.create(env.request(slot)) }
    }

    @Test
    fun cancellingAFutureBookingRefundsInFullAndReleasesTheSlot() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val slot = env.firstOpenSlot()
        val booking = env.bookings.create(env.request(slot))

        val cancelled = env.bookings.cancel(booking.id)

        assertEquals(BookingStatus.CANCELLED, cancelled.status)
        assertEquals(booking.amount, cancelled.refundAmount)
        assertNotNull(cancelled.cancelledAt)
        assertEquals(cancelled, env.bookings.bookings.value!!.first { it.id == booking.id })
        assertTrue(env.professionals.slots("p1", env.tomorrow).first { it.start == slot.start }.isAvailable)
        assertFailsWith<AppException.CancellationNotAllowed> { env.bookings.cancel(booking.id) }
    }

    @Test
    fun cancellationAtOrAfterStartIsRejected() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val seeded = env.bookings.bookings.value!!.first { it.id == SEEDED_UPCOMING_ID }

        env.clock.instant = seeded.start
        assertFailsWith<AppException.CancellationNotAllowed> { env.bookings.cancel(seeded.id) }

        val after = env.bookings.bookings.value!!.first { it.id == seeded.id }
        assertEquals(BookingStatus.CONFIRMED, after.status)
        assertNull(after.refundAmount)
    }

    @Test
    fun failedCancellationChangesNothing() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val before = env.bookings.bookings.value
        env.faults.set(Fault.CANCELLATION_FAILURE, FaultMode.ONCE)

        assertFailsWith<AppException.Network> { env.bookings.cancel(SEEDED_UPCOMING_ID) }

        assertEquals(before, env.bookings.bookings.value)
        env.bookings.refresh()
        assertEquals(before, env.bookings.bookings.value)
    }

    @Test
    fun bookingsAndDraftDetailsSurviveARestart() = runTest {
        val first = TestEnvironment()
        val booking = first.bookings.create(first.request(first.firstOpenSlot()))
        first.bookings.cancel(SEEDED_UPCOMING_ID)
        first.draft.updateCustomer(VALID_CUSTOMER)

        // A new process: fresh objects, same storage.
        val second = TestEnvironment(store = first.store)
        second.bookings.refresh()
        val restored = second.bookings.bookings.value!!

        assertEquals(booking, restored.first { it.id == booking.id })
        assertEquals(BookingStatus.CANCELLED, restored.first { it.id == SEEDED_UPCOMING_ID }.status)
        assertEquals(VALID_CUSTOMER, second.draft.draft.value.customer)
        assertNull(second.draft.draft.value.selection, "a half-chosen slot is not restored")
    }
}
