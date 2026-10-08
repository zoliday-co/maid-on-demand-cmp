package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.BookingPhase
import app.android.mainondemand.domain.BookingRules
import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.repository.BookingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class BookingItem(val booking: Booking, val phase: BookingPhase)

data class BookingsUiState(
    /** Null until the first load finishes. */
    val upcoming: List<BookingItem>? = null,
    val past: List<BookingItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = upcoming?.isEmpty() == true && past.isEmpty()
}

class BookingsViewModel(
    private val repository: BookingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingsUiState())
    val state: StateFlow<BookingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.bookings.collect { bookings -> if (bookings != null) show(bookings) }
        }
        refresh()
    }

    fun onRetry() = refresh()

    /** Re-reads the clock so a booking that has started moves out of "Upcoming". */
    fun onShown() {
        repository.bookings.value?.let(::show)
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                repository.refresh()
                _state.update { it.copy(isLoading = false) }
            } catch (e: AppException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun show(bookings: List<Booking>) {
        val now = clock.now()
        val (upcoming, past) = bookings
            .map { BookingItem(it, BookingRules.phase(it, now)) }
            .partition { it.phase == BookingPhase.UPCOMING || it.phase == BookingPhase.IN_PROGRESS }
        _state.update {
            it.copy(
                upcoming = upcoming.sortedBy { item -> item.booking.start },
                past = past.sortedByDescending { item -> item.booking.start },
            )
        }
    }
}
