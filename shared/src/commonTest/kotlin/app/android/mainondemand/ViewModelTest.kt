package app.android.mainondemand

import app.android.mainondemand.data.fake.ApiCall
import app.android.mainondemand.data.fake.Fault
import app.android.mainondemand.data.fake.FaultMode
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.presentation.BookingDetailViewModel
import app.android.mainondemand.presentation.BookingsViewModel
import app.android.mainondemand.presentation.CheckoutFailure
import app.android.mainondemand.presentation.CheckoutViewModel
import app.android.mainondemand.presentation.Load
import app.android.mainondemand.presentation.ProfileViewModel
import app.android.mainondemand.presentation.SearchViewModel
import app.android.mainondemand.presentation.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest : MainDispatcherTest() {

    @Test
    fun aSlowEarlierSearchNeverOverwritesTheLatestCriteria() = runTest {
        val env = TestEnvironment()
        // The first request (cleaning) is slow; the one that replaces it (cooking) is fast.
        env.latencyMs = { if (env.count(ApiCall.SEARCH) == 1) 5_000 else 100 }
        val viewModel = SearchViewModel(env.professionals, env.clock)
        runCurrent()

        viewModel.onServiceSelected(ServiceType.COOKING)
        advanceTimeBy(200)

        val fresh = assertIs<Load.Ready<*>>(viewModel.state.value.results).value
        assertEquals(ServiceType.COOKING, viewModel.state.value.criteria.service)
        assertTrue(viewModel.state.value.results.valueOrNull!!.all { ServiceType.COOKING in it.professional.prices })

        // Well past the moment the slow response would have landed.
        advanceUntilIdle()
        assertEquals(fresh, viewModel.state.value.results.valueOrNull)
        assertEquals(2, env.count(ApiCall.SEARCH))
    }

    @Test
    fun searchFailureShowsAnErrorAndRetryRecovers() = runTest {
        val env = TestEnvironment()
        env.faults.set(Fault.SEARCH_FAILURE, FaultMode.ONCE)
        val viewModel = SearchViewModel(env.professionals, env.clock)
        assertEquals(Load.Loading, viewModel.state.value.results)
        advanceUntilIdle()
        assertIs<Load.Failed>(viewModel.state.value.results)
        val criteria = viewModel.state.value.criteria

        viewModel.onRetry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.results.valueOrNull!!.isNotEmpty())
        assertEquals(criteria, viewModel.state.value.criteria, "criteria survive the failure")
    }

    @Test
    fun noResultsIsAnEmptyContentState() = runTest {
        val env = TestEnvironment()
        env.faults.set(Fault.NO_RESULTS, FaultMode.ALWAYS)
        val viewModel = SearchViewModel(env.professionals, env.clock)
        advanceUntilIdle()
        assertEquals(Load.Ready(emptyList()), viewModel.state.value.results)
    }

    @Test
    fun pickingADayDropsAnHourThatHasAlreadyPassed() = runTest {
        val env = TestEnvironment()
        val viewModel = SearchViewModel(env.professionals, env.clock)
        val today = viewModel.state.value.dates.first()

        viewModel.onDateSelected(env.tomorrow)
        viewModel.onHourSelected(9)
        assertEquals(9, viewModel.state.value.criteria.startHour)

        // It is 10:00 IST "now", so 9 AM today is gone.
        viewModel.onDateSelected(today)
        assertNull(viewModel.state.value.criteria.startHour)
        assertFalse(9 in viewModel.state.value.hours)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest : MainDispatcherTest() {

    @Test
    fun whenTheChosenSlotIsTakenSlotsRefreshAndSelectionIsCleared() = runTest {
        val env = TestEnvironment()
        val slot = env.firstOpenSlot()
        val viewModel = ProfileViewModel(
            "p1", ServiceType.CLEANING, env.tomorrow, slot.start, env.professionals, env.draft, env.clock,
        )
        advanceUntilIdle()
        assertEquals(slot.start, viewModel.state.value.selectedSlot, "the searched time is preselected")
        assertTrue(viewModel.confirmSelection())
        assertEquals(slot.start, env.draft.draft.value.selection?.slotStart)

        // Someone else wins the slot while the customer is paying.
        env.faults.set(Fault.SLOT_CONFLICT, FaultMode.ONCE)
        runCatching { env.bookings.create(env.request(slot)) }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.selectedSlot)
        assertNotNull(state.slotNotice)
        assertFalse(state.canContinue)
        assertFalse(state.slots.valueOrNull!!.first { it.start == slot.start }.isAvailable)

        val other = state.slots.valueOrNull!!.first { it.isAvailable }
        viewModel.onSlotSelected(other)
        assertEquals(other.start, viewModel.state.value.selectedSlot)
        assertNull(viewModel.state.value.slotNotice)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest : MainDispatcherTest() {

    @Test
    fun invalidDetailsBlockPaymentWithoutCallingTheBackend() = runTest {
        val env = TestEnvironment()
        env.selectForCheckout(env.firstOpenSlot())
        val viewModel = CheckoutViewModel(env.draft, env.bookings)
        viewModel.onMobileChanged("98765")

        viewModel.onPay()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.errors.mobile)
        assertEquals(0, env.count(ApiCall.CREATE_BOOKING))

        // Errors clear live once the field is fixed.
        viewModel.onMobileChanged("+91 98765 43210")
        assertTrue(viewModel.state.value.errors.isValid)
        assertEquals("9876543210", viewModel.state.value.customer.mobile)
    }

    @Test
    fun tappingPayRepeatedlySubmitsOnce() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val before = env.bookings.bookings.value!!.size
        env.selectForCheckout(env.firstOpenSlot())
        val viewModel = CheckoutViewModel(env.draft, env.bookings)

        viewModel.onPay()
        viewModel.onPay()
        runCurrent()
        assertTrue(viewModel.state.value.isSubmitting)
        viewModel.onPay()
        advanceUntilIdle()
        // Even after it has succeeded, another tap must not book again.
        viewModel.onPay()
        advanceUntilIdle()

        assertEquals(1, env.count(ApiCall.CREATE_BOOKING))
        assertEquals(before + 1, env.bookings.bookings.value!!.size)
        assertNotNull(viewModel.state.value.confirmedBookingId)
        assertFalse(viewModel.state.value.isSubmitting)
        assertNull(env.draft.draft.value.selection, "the used slot leaves the draft")
        assertEquals(VALID_CUSTOMER, env.draft.draft.value.customer, "details are kept for next time")
    }

    @Test
    fun failedPaymentKeepsTheDraftAndRetrySucceeds() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val before = env.bookings.bookings.value!!
        val slot = env.firstOpenSlot()
        env.selectForCheckout(slot)
        env.faults.set(Fault.PAYMENT_FAILURE, FaultMode.ONCE)
        val viewModel = CheckoutViewModel(env.draft, env.bookings)

        viewModel.onPay()
        advanceUntilIdle()

        val failed = viewModel.state.value
        assertIs<CheckoutFailure.Payment>(failed.failure)
        assertNull(failed.confirmedBookingId)
        assertFalse(failed.isSubmitting)
        assertEquals(VALID_CUSTOMER, failed.customer)
        assertEquals(slot.start, env.draft.draft.value.selection?.slotStart)
        assertEquals(before, env.bookings.bookings.value, "no booking was created")

        viewModel.onPay()
        advanceUntilIdle()

        val bookingId = assertNotNull(viewModel.state.value.confirmedBookingId)
        assertNull(viewModel.state.value.failure)
        assertEquals(BookingStatus.CONFIRMED, env.bookings.bookings.value!!.first { it.id == bookingId }.status)
    }

    @Test
    fun slotConflictSendsTheCustomerBackWithDetailsIntact() = runTest {
        val env = TestEnvironment()
        env.selectForCheckout(env.firstOpenSlot())
        env.faults.set(Fault.SLOT_CONFLICT, FaultMode.ONCE)
        val viewModel = CheckoutViewModel(env.draft, env.bookings)

        viewModel.onPay()
        advanceUntilIdle()

        assertIs<CheckoutFailure.SlotTaken>(viewModel.state.value.failure)
        assertNull(env.draft.draft.value.selection)
        assertEquals(VALID_CUSTOMER, env.draft.draft.value.customer)

        // The stale slot can't be paid for again from this screen.
        viewModel.onPay()
        advanceUntilIdle()
        assertEquals(1, env.count(ApiCall.CREATE_BOOKING))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookingDetailViewModelTest : MainDispatcherTest() {

    @Test
    fun cancellationUpdatesListAndDetailOnlyAfterSuccess() = runTest {
        val env = TestEnvironment()
        val list = BookingsViewModel(env.bookings, env.clock)
        val detail = BookingDetailViewModel(SEEDED_UPCOMING_ID, env.bookings, env.clock)
        advanceUntilIdle()
        assertTrue(detail.state.value.canCancel)
        assertTrue(list.state.value.upcoming!!.any { it.booking.id == SEEDED_UPCOMING_ID })

        detail.onCancelRequested()
        assertEquals(249, detail.state.value.cancelDialog?.refundAmount, "the refund is shown before confirming")
        assertEquals(0, env.count(ApiCall.CANCEL_BOOKING), "nothing happens until the customer confirms")

        detail.onCancelConfirmed()
        detail.onCancelConfirmed()
        runCurrent()
        // In flight: still confirmed everywhere, and the dialog can't be dismissed.
        assertTrue(detail.state.value.cancelDialog!!.isCancelling)
        detail.onCancelDismissed()
        assertNotNull(detail.state.value.cancelDialog)
        assertEquals(BookingStatus.CONFIRMED, detail.state.value.booking.valueOrNull!!.status)
        assertTrue(list.state.value.upcoming!!.any { it.booking.id == SEEDED_UPCOMING_ID })

        advanceUntilIdle()

        assertEquals(1, env.count(ApiCall.CANCEL_BOOKING), "the double tap cancelled once")
        val cancelled = detail.state.value.booking.valueOrNull!!
        assertEquals(BookingStatus.CANCELLED, cancelled.status)
        assertEquals(249, cancelled.refundAmount)
        assertNull(detail.state.value.cancelDialog)
        assertFalse(detail.state.value.canCancel)
        assertTrue(list.state.value.upcoming!!.none { it.booking.id == SEEDED_UPCOMING_ID })
        assertTrue(list.state.value.past.any { it.booking.id == SEEDED_UPCOMING_ID })
    }

    @Test
    fun failedCancellationLeavesTheBookingConfirmedAndCanBeRetried() = runTest {
        val env = TestEnvironment()
        env.faults.set(Fault.CANCELLATION_FAILURE, FaultMode.ONCE)
        val detail = BookingDetailViewModel(SEEDED_UPCOMING_ID, env.bookings, env.clock)
        advanceUntilIdle()

        detail.onCancelRequested()
        detail.onCancelConfirmed()
        advanceUntilIdle()

        val dialog = assertNotNull(detail.state.value.cancelDialog, "the dialog stays open to offer a retry")
        assertNotNull(dialog.error)
        assertFalse(dialog.isCancelling)
        assertEquals(BookingStatus.CONFIRMED, detail.state.value.booking.valueOrNull!!.status)
        assertEquals(BookingStatus.CONFIRMED, env.bookings.bookings.value!!.first { it.id == SEEDED_UPCOMING_ID }.status)

        detail.onCancelConfirmed()
        advanceUntilIdle()
        assertEquals(BookingStatus.CANCELLED, detail.state.value.booking.valueOrNull!!.status)
    }

    @Test
    fun aBookingThatHasStartedCannotBeCancelled() = runTest {
        val env = TestEnvironment()
        val detail = BookingDetailViewModel(SEEDED_UPCOMING_ID, env.bookings, env.clock)
        advanceUntilIdle()
        env.clock.instant = detail.state.value.booking.valueOrNull!!.start

        detail.onCancelRequested()

        assertNull(detail.state.value.cancelDialog)
        assertFalse(detail.state.value.canCancel)
        assertNotNull(detail.state.value.notice)
        assertEquals(0, env.count(ApiCall.CANCEL_BOOKING))
    }
}
