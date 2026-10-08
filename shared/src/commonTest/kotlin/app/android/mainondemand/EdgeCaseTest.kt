package app.android.mainondemand

import androidx.lifecycle.ViewModel
import app.android.mainondemand.data.fake.ApiCall
import app.android.mainondemand.data.fake.Fault
import app.android.mainondemand.data.fake.FaultMode
import app.android.mainondemand.data.persistence.InMemoryKeyValueStore
import app.android.mainondemand.di.AppContainer
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.BookingPhase
import app.android.mainondemand.domain.BookingRules
import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingRequest
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.domain.model.DraftSelection
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.domain.model.SearchCriteria
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.domain.repository.BookingRepository
import app.android.mainondemand.presentation.BookingDetailViewModel
import app.android.mainondemand.presentation.BookingsViewModel
import app.android.mainondemand.presentation.CheckoutFailure
import app.android.mainondemand.presentation.CheckoutViewModel
import app.android.mainondemand.presentation.FaultLabViewModel
import app.android.mainondemand.presentation.Load
import app.android.mainondemand.presentation.ProfileViewModel
import app.android.mainondemand.presentation.SearchViewModel
import app.android.mainondemand.presentation.SplashViewModel
import app.android.mainondemand.presentation.valueOrNull
import app.android.mainondemand.ui.formatHour
import app.android.mainondemand.ui.formatIstDateTime
import app.android.mainondemand.ui.formatSpoken
import app.android.mainondemand.ui.navigation.HomeTab
import app.android.mainondemand.ui.navigation.Navigator
import app.android.mainondemand.ui.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BookingRulesTest {

    @Test
    fun phaseFollowsTheClockAndCancellation() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val booking = env.bookings.bookings.value!!.first { it.id == SEEDED_UPCOMING_ID }

        assertEquals(BookingPhase.UPCOMING, BookingRules.phase(booking, booking.start - 1.minutes))
        assertEquals(BookingPhase.IN_PROGRESS, BookingRules.phase(booking, booking.start))
        assertEquals(BookingPhase.IN_PROGRESS, BookingRules.phase(booking, booking.end - 1.minutes))
        assertEquals(BookingPhase.COMPLETED, BookingRules.phase(booking, booking.end))
        val cancelled = booking.copy(status = BookingStatus.CANCELLED)
        assertEquals(BookingPhase.CANCELLED, BookingRules.phase(cancelled, booking.start - 1.minutes))
    }

    @Test
    fun refundIsFullBeforeStartAndZeroOtherwise() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        val booking = env.bookings.bookings.value!!.first { it.id == SEEDED_UPCOMING_ID }

        assertEquals(booking.amount, BookingRules.refundAmount(booking, booking.start - 1.minutes))
        assertEquals(0, BookingRules.refundAmount(booking, booking.start))
        assertEquals(0, BookingRules.refundAmount(booking, booking.start + 2.hours))
        assertFalse(BookingRules.canCancel(booking.copy(status = BookingStatus.CANCELLED), booking.start - 1.hours))
    }

    @Test
    fun spokenAndClockFormats() {
        assertEquals("12 AM", formatHour(0))
        assertEquals("8 AM", formatHour(8))
        assertEquals("12 PM", formatHour(12))
        assertEquals("7 PM", formatHour(19))
        assertEquals("Thu, 8 Oct · 10:00 AM IST", TEST_NOW.formatIstDateTime())
        assertEquals("Thursday 8 October", LocalDate(2026, 10, 8).formatSpoken())
    }
}

class BackendEdgeCaseTest {
    private val stateKey = "fake_backend_state_v1"
    private val draftKey = "booking_draft_v1"

    @Test
    fun unknownIdsAreReportedAsNotFound() = runTest {
        val env = TestEnvironment()
        assertFailsWith<AppException.NotFound> { env.professionals.professional("nobody") }
        assertFailsWith<AppException.NotFound> { env.professionals.slots("nobody", env.tomorrow) }
        assertFailsWith<AppException.NotFound> { env.bookings.cancel("MB-NOPE00") }
    }

    @Test
    fun theBackendRevalidatesRequests() = runTest {
        val env = TestEnvironment()
        val slot = env.firstOpenSlot()

        // Lakshmi Devi (p1) does not cook.
        assertFailsWith<AppException.InvalidRequest> { env.bookings.create(env.request(slot, ServiceType.COOKING)) }
        assertFailsWith<AppException.InvalidRequest> {
            env.bookings.create(env.request(slot).copy(customer = VALID_CUSTOMER.copy(mobile = "123")))
        }
        env.bookings.refresh()
        assertTrue(env.bookings.bookings.value!!.none { it.start == slot.start && it.professionalId == "p1" })
    }

    @Test
    fun namesAndAddressesAreTrimmedOnTheBooking() = runTest {
        val env = TestEnvironment()
        val request = env.request(env.firstOpenSlot())
            .copy(customer = VALID_CUSTOMER.copy(name = "  Priya Nair ", address = " ${VALID_CUSTOMER.address}  "))
        assertEquals(VALID_CUSTOMER, env.bookings.create(request).customer)
    }

    @Test
    fun anAlwaysFaultKeepsFiringUntilReset() = runTest {
        val env = TestEnvironment()
        val criteria = SearchCriteria("indiranagar", ServiceType.CLEANING, env.tomorrow)
        env.faults.set(Fault.SEARCH_FAILURE, FaultMode.ALWAYS)

        repeat(2) { assertFailsWith<AppException.Network> { env.professionals.search(criteria) } }
        assertEquals(FaultMode.ALWAYS, env.faults.modes.value[Fault.SEARCH_FAILURE])

        env.faults.reset()
        assertTrue(env.faults.modes.value.values.all { it == FaultMode.OFF })
        assertTrue(env.professionals.search(criteria).isNotEmpty())
    }

    @Test
    fun aNextCallFaultDisarmsItself() = runTest {
        val env = TestEnvironment()
        env.faults.set(Fault.NO_RESULTS, FaultMode.ONCE)
        val criteria = SearchCriteria("indiranagar", ServiceType.CLEANING, env.tomorrow)

        assertTrue(env.professionals.search(criteria).isEmpty())
        assertEquals(FaultMode.OFF, env.faults.modes.value[Fault.NO_RESULTS])
        assertTrue(env.professionals.search(criteria).isNotEmpty())
    }

    @Test
    fun corruptSavedStateFallsBackToTheSeed() = runTest {
        val unreadable = InMemoryKeyValueStore().apply {
            putString(stateKey, "{ not json")
            putString(draftKey, "][")
        }
        val env = TestEnvironment(store = unreadable)
        env.bookings.refresh()
        assertEquals(3, env.bookings.bookings.value!!.size)
        assertEquals("", env.draft.draft.value.customer.name)

        // Well-formed JSON holding a value this version doesn't know.
        val good = TestEnvironment()
        good.bookings.refresh()
        good.draft.setPaymentMethod(PaymentMethod.CARD)
        val unknownValues = InMemoryKeyValueStore().apply {
            putString(stateKey, good.store.getString(stateKey)!!.replace("CLEANING", "LAUNDRY"))
            putString(draftKey, good.store.getString(draftKey)!!.replace("CARD", "CHEQUE"))
        }
        val recovered = TestEnvironment(store = unknownValues)
        recovered.bookings.refresh()
        assertEquals(3, recovered.bookings.bookings.value!!.size)
        assertEquals(PaymentMethod.UPI, recovered.draft.draft.value.paymentMethod)
    }

    @Test
    fun paymentMethodSurvivesARestartAndStoreEntriesCanBeRemoved() {
        val store = InMemoryKeyValueStore()
        TestEnvironment(store = store).draft.setPaymentMethod(PaymentMethod.CARD)
        assertEquals(PaymentMethod.CARD, TestEnvironment(store = store).draft.draft.value.paymentMethod)

        store.remove(draftKey)
        assertNull(store.getString(draftKey))
        assertEquals(PaymentMethod.UPI, TestEnvironment(store = store).draft.draft.value.paymentMethod)
    }

    @Test
    fun slotsEndSixtyMinutesAfterTheyStartAndPastOnesAreHidden() = runTest {
        val env = TestEnvironment()
        val slot = env.professionals.slots("p1", env.tomorrow).first()
        assertEquals(60.minutes, slot.end - slot.start)

        val todaySlots = env.professionals.slots("p1", LocalDate(2026, 10, 8))
        assertTrue(todaySlots.all { it.start > TEST_NOW }, "10:00 and earlier are gone at 10:00")
        assertEquals(9, todaySlots.size)
    }

    @Test
    fun theProductionContainerWiresAWorkingGraphWithRealLatency() = runTest {
        val clock = MutableClock(TEST_NOW)
        val container = AppContainer(InMemoryKeyValueStore(), clock)
        val tomorrow = TestEnvironment(clock = clock).tomorrow

        val result = container.professionalRepository
            .search(SearchCriteria("koramangala", ServiceType.COOKING, tomorrow)).first()
        assertEquals(result.professional, container.professionalRepository.professional(result.professional.id))
        val slot = container.professionalRepository.slots(result.professional.id, tomorrow).first { it.isAvailable }
        container.bookingRepository.refresh()
        val booking = container.bookingRepository.create(
            BookingRequest(result.professional.id, ServiceType.COOKING, slot.start, VALID_CUSTOMER, PaymentMethod.CARD),
        )
        assertEquals(result.price, booking.amount)
        assertTrue(booking.paymentRef.startsWith("CARD-"))
        assertEquals(BookingStatus.CANCELLED, container.bookingRepository.cancel(booking.id).status)
        assertSame(container.faults.modes, FaultLabViewModel(container.faults).modes)
    }
}

/** A bookings backend that is down: every call fails with a network error. */
private class OfflineBookingRepository : BookingRepository {
    var refreshCount = 0
    override val bookings: StateFlow<List<Booking>?> = MutableStateFlow(null)
    override suspend fun refresh() {
        refreshCount++
        throw AppException.Network()
    }

    override suspend fun create(request: BookingRequest): Booking = throw AppException.Network()
    override suspend fun cancel(bookingId: String): Booking = throw AppException.Network()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelEdgeCaseTest : MainDispatcherTest() {

    @Test
    fun splashFinishesAfterItsDuration() = runTest {
        val viewModel = SplashViewModel(durationMs = 1_000)
        advanceTimeBy(999)
        assertFalse(viewModel.finished.value)
        advanceTimeBy(2)
        assertTrue(viewModel.finished.value)
    }

    @Test
    fun faultLabChangesAndResetsBackendFaults() {
        val env = TestEnvironment()
        val viewModel = FaultLabViewModel(env.faults)

        viewModel.onModeSelected(Fault.PAYMENT_FAILURE, FaultMode.ALWAYS)
        assertEquals(FaultMode.ALWAYS, viewModel.modes.value[Fault.PAYMENT_FAILURE])
        assertEquals(FaultMode.OFF, viewModel.modes.value[Fault.SEARCH_FAILURE])

        viewModel.onReset()
        assertTrue(viewModel.modes.value.values.all { it == FaultMode.OFF })
    }

    @Test
    fun searchFiltersNarrowResultsAndCanBeCleared() = runTest {
        val env = TestEnvironment()
        val viewModel = SearchViewModel(env.professionals, env.clock)
        viewModel.onDateSelected(env.tomorrow)
        advanceUntilIdle()
        val all = viewModel.state.value.results.valueOrNull!!
        assertFalse(viewModel.state.value.hasFilters)

        viewModel.onMinRatingSelected(4.5)
        viewModel.onMaxPriceSelected(300)
        assertEquals(Load.Loading, viewModel.state.value.results)
        advanceUntilIdle()
        val narrowed = viewModel.state.value.results.valueOrNull!!
        assertTrue(viewModel.state.value.hasFilters)
        assertTrue(narrowed.size < all.size)
        assertTrue(narrowed.all { it.professional.rating >= 4.5 && it.price <= 300 })

        viewModel.onClearFilters()
        advanceUntilIdle()
        assertEquals(all, viewModel.state.value.results.valueOrNull)

        viewModel.onLocalitySelected("koramangala")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.results.valueOrNull!!.all { it.professional.localityId == "koramangala" })
    }

    @Test
    fun reselectingTheSameCriteriaDoesNotSearchAgain() = runTest {
        val env = TestEnvironment()
        val viewModel = SearchViewModel(env.professionals, env.clock)
        advanceUntilIdle()
        val searches = env.count(ApiCall.SEARCH)

        viewModel.onServiceSelected(viewModel.state.value.criteria.service)
        viewModel.onHourSelected(null)
        advanceUntilIdle()

        assertEquals(searches, env.count(ApiCall.SEARCH))
    }

    @Test
    fun aBookingElsewhereRefreshesResultsWithoutBlankingThem() = runTest {
        val env = TestEnvironment()
        val viewModel = SearchViewModel(env.professionals, env.clock)
        viewModel.onDateSelected(env.tomorrow)
        advanceUntilIdle()
        val before = viewModel.state.value.results.valueOrNull!!
        val lakshmi = before.first { it.professional.id == "p1" }

        env.bookings.create(env.request(env.firstOpenSlot()))
        runCurrent()
        // Old results stay visible while the refresh is in flight.
        assertTrue(viewModel.state.value.isRefreshing)
        assertEquals(before, viewModel.state.value.results.valueOrNull)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        val after = viewModel.state.value.results.valueOrNull!!.first { it.professional.id == "p1" }
        assertEquals(lakshmi.openSlotCount - 1, after.openSlotCount)
    }

    @Test
    fun aFailedBackgroundRefreshKeepsTheResultsOnScreen() = runTest {
        val env = TestEnvironment()
        val viewModel = SearchViewModel(env.professionals, env.clock)
        viewModel.onDateSelected(env.tomorrow)
        advanceUntilIdle()
        val before = viewModel.state.value.results.valueOrNull!!
        val slot = env.firstOpenSlot()

        env.faults.set(Fault.SEARCH_FAILURE, FaultMode.ONCE)
        env.bookings.create(env.request(slot))
        advanceUntilIdle()

        assertEquals(before, viewModel.state.value.results.valueOrNull)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun profileShowsAnErrorForAnUnknownProfessionalAndCannotContinue() = runTest {
        val env = TestEnvironment()
        val viewModel = ProfileViewModel(
            "nobody", ServiceType.CLEANING, env.tomorrow, null, env.professionals, env.draft, env.clock,
        )
        advanceUntilIdle()
        assertIs<Load.Failed>(viewModel.state.value.professional)
        assertIs<Load.Failed>(viewModel.state.value.slots)
        assertFalse(viewModel.confirmSelection())

        viewModel.onRetryProfessional()
        viewModel.onRetrySlots()
        assertEquals(Load.Loading, viewModel.state.value.professional)
        assertEquals(Load.Loading, viewModel.state.value.slots)
        advanceUntilIdle()
        assertIs<Load.Failed>(viewModel.state.value.professional)
        assertNull(env.draft.draft.value.selection)
    }

    @Test
    fun profileServiceAndDateChangesUpdatePriceAndSlots() = runTest {
        val env = TestEnvironment()
        // Dishwashing is asked for but Meena Kumari (p3) offers only cleaning and cooking.
        val viewModel = ProfileViewModel(
            "p3", ServiceType.DISHWASHING, env.tomorrow, null, env.professionals, env.draft, env.clock,
        )
        assertEquals(14, viewModel.state.value.dates.size)
        advanceUntilIdle()
        assertEquals(ServiceType.CLEANING, viewModel.state.value.service, "falls back to a service she offers")
        assertEquals(199, viewModel.state.value.price)
        assertEquals("Indiranagar", viewModel.state.value.localityName)
        assertNull(viewModel.state.value.selectedSlot, "nothing is preselected for an any-time search")
        assertFalse(viewModel.confirmSelection())

        viewModel.onServiceSelected(ServiceType.COOKING)
        assertEquals(299, viewModel.state.value.price)

        val slots = viewModel.state.value.slots.valueOrNull!!
        viewModel.onSlotSelected(slots.first().copy(isAvailable = false))
        assertNull(viewModel.state.value.selectedSlot, "a taken slot can't be chosen")
        viewModel.onSlotSelected(slots.first { it.isAvailable })
        assertTrue(viewModel.state.value.canContinue)

        val nextDay = viewModel.state.value.dates[2]
        viewModel.onDateSelected(nextDay)
        assertNull(viewModel.state.value.selectedSlot, "the slot belongs to the old date")
        assertEquals(Load.Loading, viewModel.state.value.slots)
        advanceUntilIdle()
        val calls = env.count(ApiCall.SLOTS)
        viewModel.onDateSelected(nextDay)
        advanceUntilIdle()
        assertEquals(calls, env.count(ApiCall.SLOTS), "reselecting the same date does nothing")

        viewModel.onSlotSelected(viewModel.state.value.slots.valueOrNull!!.first { it.isAvailable })
        assertTrue(viewModel.confirmSelection())
        val selection = assertNotNull(env.draft.draft.value.selection)
        assertEquals(ServiceType.COOKING, selection.service)
        assertEquals(299, selection.price)
        assertEquals("Meena Kumari", selection.professionalName)
    }

    @Test
    fun aRouteDateOutsideTheBookingWindowFallsBackToToday() = runTest {
        val env = TestEnvironment()
        val viewModel = ProfileViewModel(
            "p1", ServiceType.CLEANING, LocalDate(2020, 1, 1), null,
            env.professionals, env.draft, env.clock,
        )
        assertEquals(viewModel.state.value.dates.first(), viewModel.state.value.selectedDate)
    }

    @Test
    fun checkoutEditsAreWrittenThroughToTheDraft() = runTest {
        val env = TestEnvironment()
        env.bookings.refresh()
        env.selectForCheckout(env.firstOpenSlot())
        val viewModel = CheckoutViewModel(env.draft, env.bookings)

        viewModel.onNameChanged("Asha Rao")
        viewModel.onAddressChanged("22, Lake View Road, HSR Layout")
        viewModel.onPaymentMethodSelected(PaymentMethod.CARD)

        val draft = env.draft.draft.value
        assertEquals("Asha Rao", draft.customer.name)
        assertEquals("22, Lake View Road, HSR Layout", draft.customer.address)
        assertEquals(PaymentMethod.CARD, draft.paymentMethod)
        assertEquals(PaymentMethod.CARD, viewModel.state.value.paymentMethod)
        assertTrue(viewModel.state.value.errors.isValid, "no errors are shown before the first attempt to pay")

        viewModel.onPay()
        advanceUntilIdle()
        val booking = env.bookings.bookings.value!!.first { it.id == viewModel.state.value.confirmedBookingId }
        assertEquals("Asha Rao", booking.customer.name)
        assertEquals(PaymentMethod.CARD, booking.paymentMethod)
    }

    @Test
    fun checkoutIsLockedWhileThePaymentIsInFlight() = runTest {
        val env = TestEnvironment()
        env.selectForCheckout(env.firstOpenSlot())
        val viewModel = CheckoutViewModel(env.draft, env.bookings)

        viewModel.onPay()
        runCurrent()
        viewModel.onNameChanged("Someone Else")
        viewModel.onPaymentMethodSelected(PaymentMethod.CARD)

        assertEquals(VALID_CUSTOMER.name, viewModel.state.value.customer.name)
        assertEquals(PaymentMethod.UPI, viewModel.state.value.paymentMethod)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.confirmedBookingId)
    }

    @Test
    fun checkoutWithoutASelectionDoesNothing() = runTest {
        val env = TestEnvironment()
        val viewModel = CheckoutViewModel(env.draft, env.bookings)
        assertNull(viewModel.state.value.selection)

        viewModel.onPay()
        advanceUntilIdle()

        assertEquals(0, env.count(ApiCall.CREATE_BOOKING))
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun unexpectedBookingErrorsAreShownAndCanBeDismissed() = runTest {
        val env = TestEnvironment()
        val slot = env.firstOpenSlot()
        env.draft.updateCustomer(VALID_CUSTOMER)
        env.draft.select(DraftSelection("nobody", "Ghost", "Nowhere", ServiceType.CLEANING, 1, slot.start))
        val viewModel = CheckoutViewModel(env.draft, env.bookings)

        viewModel.onPay()
        advanceUntilIdle()

        val failure = assertIs<CheckoutFailure.Other>(viewModel.state.value.failure)
        assertTrue(failure.message.isNotBlank())
        assertNotNull(env.draft.draft.value.selection, "the draft is kept")
        viewModel.onFailureDismissed()
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun bookingsAreSplitIntoUpcomingAndPastAndFollowTheClock() = runTest {
        val env = TestEnvironment()
        val viewModel = BookingsViewModel(env.bookings, env.clock)
        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.upcoming)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(listOf(SEEDED_UPCOMING_ID), state.upcoming!!.map { it.booking.id })
        assertEquals(
            setOf(BookingPhase.CANCELLED, BookingPhase.COMPLETED),
            state.past.map { it.phase }.toSet(),
        )

        // Half an hour into the visit it is in progress, and still listed as upcoming.
        val start = state.upcoming.single().booking.start
        env.clock.instant = start + 30.minutes
        viewModel.onShown()
        assertEquals(BookingPhase.IN_PROGRESS, viewModel.state.value.upcoming!!.single().phase)

        env.clock.instant = start + 2.hours
        viewModel.onShown()
        assertTrue(viewModel.state.value.upcoming!!.isEmpty())
        assertEquals(3, viewModel.state.value.past.size)
    }

    @Test
    fun bookingsShowAnErrorWhenTheBackendIsDownAndRetry() = runTest {
        val offline = OfflineBookingRepository()
        val viewModel = BookingsViewModel(offline, MutableClock(TEST_NOW))
        viewModel.onShown()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.upcoming)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isEmpty)

        viewModel.onRetry()
        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
        advanceUntilIdle()
        assertEquals(2, offline.refreshCount)
    }

    @Test
    fun bookingDetailHandlesLoadFailuresAndUnknownIds() = runTest {
        val offline = OfflineBookingRepository()
        val failing = BookingDetailViewModel(SEEDED_UPCOMING_ID, offline, MutableClock(TEST_NOW))
        assertEquals(Load.Loading, failing.state.value.booking)
        advanceUntilIdle()
        assertIs<Load.Failed>(failing.state.value.booking)
        failing.onCancelRequested()
        assertNull(failing.state.value.cancelDialog, "nothing to cancel without a booking")
        failing.onCancelConfirmed()
        failing.onRetry()
        advanceUntilIdle()
        assertEquals(2, offline.refreshCount)

        val env = TestEnvironment()
        val unknown = BookingDetailViewModel("MB-NOPE00", env.bookings, env.clock)
        advanceUntilIdle()
        assertIs<Load.Failed>(unknown.state.value.booking)
    }

    @Test
    fun theCancelDialogCanBeDismissedAndNoticesClear() = runTest {
        val env = TestEnvironment()
        val detail = BookingDetailViewModel(SEEDED_UPCOMING_ID, env.bookings, env.clock)
        advanceUntilIdle()
        assertEquals(BookingPhase.UPCOMING, detail.state.value.phase)

        detail.onCancelRequested()
        assertNotNull(detail.state.value.cancelDialog)
        detail.onCancelDismissed()
        assertNull(detail.state.value.cancelDialog)
        assertEquals(0, env.count(ApiCall.CANCEL_BOOKING))

        detail.onCancelRequested()
        detail.onCancelConfirmed()
        advanceUntilIdle()
        assertTrue(detail.state.value.notice!!.contains("₹249"))
        assertEquals(BookingPhase.CANCELLED, detail.state.value.phase)
        detail.onNoticeShown()
        assertNull(detail.state.value.notice)
    }

    @Test
    fun aBookingThatStartsWhileTheDialogIsOpenIsNotCancelled() = runTest {
        val env = TestEnvironment()
        val detail = BookingDetailViewModel(SEEDED_UPCOMING_ID, env.bookings, env.clock)
        advanceUntilIdle()
        detail.onCancelRequested()

        env.clock.instant = detail.state.value.booking.valueOrNull!!.start
        detail.onCancelConfirmed()
        advanceUntilIdle()

        assertNull(detail.state.value.cancelDialog)
        assertNotNull(detail.state.value.notice)
        assertFalse(detail.state.value.canCancel)
        assertEquals(BookingPhase.IN_PROGRESS, detail.state.value.phase)
        assertEquals(BookingStatus.CONFIRMED, detail.state.value.booking.valueOrNull!!.status)
    }
}

class NavigatorTest {
    private val profile = Screen.Profile("p1", ServiceType.CLEANING, LocalDate(2026, 10, 9), null)

    private fun Navigator.screens() = stack.value.map { it.screen }

    private fun loggedIn() = Navigator().apply {
        replaceAll(Screen.Login)
        replaceAll(Screen.Home)
    }

    @Test
    fun startsOnSplashThenLoginThenHomeWithNothingToGoBackTo() {
        val navigator = Navigator()
        assertEquals(listOf<Screen>(Screen.Splash), navigator.screens())

        navigator.replaceAll(Screen.Login)
        assertEquals(listOf<Screen>(Screen.Login), navigator.screens())
        assertFalse(navigator.back(), "back on login leaves the app")

        navigator.replaceAll(Screen.Home)
        navigator.replaceAll(Screen.Home)
        assertEquals(listOf<Screen>(Screen.Home), navigator.screens())
        assertEquals(HomeTab.EXPLORE, navigator.tab.value)
        assertFalse(navigator.back(), "back on Home never returns to login")
    }

    @Test
    fun pushIgnoresADoubleTapAndBackPops() {
        val navigator = loggedIn()
        navigator.push(profile)
        navigator.push(profile)
        navigator.push(Screen.Checkout)
        assertEquals(listOf(Screen.Home, profile, Screen.Checkout), navigator.screens())
        assertTrue(navigator.stack.value.map { it.id }.zipWithNext().all { (a, b) -> b > a }, "ids only grow")

        assertTrue(navigator.back())
        assertEquals(listOf(Screen.Home, profile), navigator.screens())
    }

    @Test
    fun backOnTheBookingsTabReturnsToExploreFirst() {
        val navigator = loggedIn()
        navigator.selectTab(HomeTab.BOOKINGS)
        assertEquals(HomeTab.BOOKINGS, navigator.tab.value)

        assertTrue(navigator.back())
        assertEquals(HomeTab.EXPLORE, navigator.tab.value)
        assertFalse(navigator.back())
    }

    @Test
    fun confirmationReplacesTheBookingFlowAndCanReturnToEitherTab() {
        val navigator = loggedIn()
        navigator.push(profile)
        navigator.push(Screen.Checkout)

        navigator.replaceAboveHome(Screen.Confirmation("MB-1"))
        assertEquals(listOf(Screen.Home, Screen.Confirmation("MB-1")), navigator.screens())

        navigator.backToHome(HomeTab.BOOKINGS)
        assertEquals(listOf<Screen>(Screen.Home), navigator.screens())
        assertEquals(HomeTab.BOOKINGS, navigator.tab.value)

        navigator.push(Screen.BookingDetail("MB-1"))
        assertEquals(Screen.BookingDetail("MB-1"), navigator.screens().last())
    }

    @Test
    fun poppedEntriesHaveTheirViewModelsClearedButCoveredOnesDoNot() {
        class Tracked : ViewModel() {
            var cleared = false
            override fun onCleared() {
                cleared = true
            }
        }

        val navigator = loggedIn()
        navigator.push(profile)
        val home = navigator.stack.value.first()
        val pushed = navigator.stack.value.last()
        val homeViewModel = Tracked().also { home.viewModelStore.put("vm", it) }
        val pushedViewModel = Tracked().also { pushed.viewModelStore.put("vm", it) }

        // Home's UI leaves the screen when it is covered, but it is still on the stack.
        navigator.onEntryDisposed(home)
        assertFalse(homeViewModel.cleared)

        navigator.back()
        navigator.onEntryDisposed(pushed)
        assertTrue(pushedViewModel.cleared)
        assertFalse(homeViewModel.cleared)
    }
}
