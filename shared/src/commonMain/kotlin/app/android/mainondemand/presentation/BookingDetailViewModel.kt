package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.BookingPhase
import app.android.mainondemand.domain.BookingRules
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.repository.BookingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class CancelDialogState(
    val refundAmount: Int,
    val isCancelling: Boolean = false,
    val error: String? = null,
)

data class BookingDetailUiState(
    val booking: Load<Booking> = Load.Loading,
    val phase: BookingPhase? = null,
    val canCancel: Boolean = false,
    /** Non-null while the cancellation confirmation is on screen. */
    val cancelDialog: CancelDialogState? = null,
    /** One-off message after a cancellation attempt that the dialog can't show. */
    val notice: String? = null,
)

class BookingDetailViewModel(
    private val bookingId: String,
    private val repository: BookingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingDetailUiState())
    val state: StateFlow<BookingDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The repository is the single source of truth, so this screen and the list
            // change together — and only once the backend has confirmed a change.
            repository.bookings.collect { bookings -> if (bookings != null) show(bookings) }
        }
        if (repository.bookings.value == null) load()
    }

    fun onRetry() = load()

    fun onCancelRequested() {
        val booking = _state.value.booking.valueOrNull ?: return
        val now = clock.now()
        if (!BookingRules.canCancel(booking, now)) {
            // The booking started while this screen was open.
            showBooking(booking)
            _state.update { it.copy(notice = "Bookings can't be cancelled once they've started.") }
            return
        }
        _state.update { it.copy(cancelDialog = CancelDialogState(BookingRules.refundAmount(booking, now))) }
    }

    fun onCancelDismissed() {
        if (_state.value.cancelDialog?.isCancelling == true) return
        _state.update { it.copy(cancelDialog = null) }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    /** Also the retry action after a failed cancellation. Ignored while one is in flight. */
    fun onCancelConfirmed() {
        val dialog = _state.value.cancelDialog ?: return
        if (dialog.isCancelling) return
        _state.update { it.copy(cancelDialog = dialog.copy(isCancelling = true, error = null)) }
        viewModelScope.launch {
            try {
                val cancelled = repository.cancel(bookingId)
                _state.update {
                    it.copy(
                        cancelDialog = null,
                        notice = "Booking cancelled. ${formatInr(cancelled.refundAmount ?: 0)} will be refunded.",
                    )
                }
            } catch (e: AppException.CancellationNotAllowed) {
                _state.update { it.copy(cancelDialog = null, notice = e.message) }
                _state.value.booking.valueOrNull?.let(::showBooking)
            } catch (e: AppException) {
                _state.update {
                    it.copy(cancelDialog = it.cancelDialog?.copy(isCancelling = false, error = e.message))
                }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(booking = Load.Loading) }
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (e: AppException) {
                _state.update { it.copy(booking = Load.Failed(e.message ?: "Couldn't load this booking.")) }
            }
        }
    }

    private fun show(bookings: List<Booking>) {
        val booking = bookings.firstOrNull { it.id == bookingId }
        if (booking == null) {
            _state.update { it.copy(booking = Load.Failed("We couldn't find booking $bookingId.")) }
        } else {
            showBooking(booking)
        }
    }

    private fun showBooking(booking: Booking) {
        val now = clock.now()
        _state.update {
            it.copy(
                booking = Load.Ready(booking),
                phase = BookingRules.phase(booking, now),
                canCancel = BookingRules.canCancel(booking, now),
            )
        }
    }
}
