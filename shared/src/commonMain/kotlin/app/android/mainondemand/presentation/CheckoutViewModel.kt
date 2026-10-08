package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.CustomerErrors
import app.android.mainondemand.domain.CustomerValidator
import app.android.mainondemand.domain.model.BookingRequest
import app.android.mainondemand.domain.model.CustomerDetails
import app.android.mainondemand.domain.model.DraftSelection
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.domain.repository.BookingRepository
import app.android.mainondemand.domain.repository.DraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CheckoutFailure {
    val message: String

    /** Recoverable: the draft is intact and the customer can pay again. */
    data class Payment(override val message: String) : CheckoutFailure

    /** The slot is gone; the customer has to go back and choose another one. */
    data class SlotTaken(override val message: String) : CheckoutFailure
    data class Other(override val message: String) : CheckoutFailure
}

data class CheckoutUiState(
    val selection: DraftSelection?,
    val customer: CustomerDetails,
    val paymentMethod: PaymentMethod,
    /** Field errors, populated once the customer has tried to pay. */
    val errors: CustomerErrors = CustomerErrors(),
    val isSubmitting: Boolean = false,
    val failure: CheckoutFailure? = null,
    /** Non-null once the booking is confirmed; the screen navigates away on it. */
    val confirmedBookingId: String? = null,
)

class CheckoutViewModel(
    private val draftRepository: DraftRepository,
    private val bookingRepository: BookingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        draftRepository.draft.value.let { CheckoutUiState(it.selection, it.customer, it.paymentMethod) },
    )
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    private var validateOnEdit = false

    fun onNameChanged(name: String) = editCustomer { it.copy(name = name) }

    fun onMobileChanged(mobile: String) = editCustomer { it.copy(mobile = CustomerValidator.sanitizeMobile(mobile)) }

    fun onAddressChanged(address: String) = editCustomer { it.copy(address = address) }

    fun onPaymentMethodSelected(method: PaymentMethod) {
        if (_state.value.isSubmitting) return
        draftRepository.setPaymentMethod(method)
        _state.update { it.copy(paymentMethod = method) }
    }

    fun onFailureDismissed() = _state.update { it.copy(failure = null) }

    /** Also the retry action after a failed payment. Ignored while a payment is in flight. */
    fun onPay() {
        val current = _state.value
        if (current.isSubmitting || current.confirmedBookingId != null) return
        // The selection stays on screen for context, but it can't be paid for any more.
        if (current.failure is CheckoutFailure.SlotTaken) return
        val selection = current.selection ?: return

        validateOnEdit = true
        val errors = CustomerValidator.validate(current.customer)
        if (!errors.isValid) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, failure = null, errors = errors) }
        viewModelScope.launch {
            try {
                val booking = bookingRepository.create(
                    BookingRequest(
                        professionalId = selection.professionalId,
                        service = selection.service,
                        slotStart = selection.slotStart,
                        customer = current.customer,
                        paymentMethod = current.paymentMethod,
                    ),
                )
                // Customer details stay in the draft for the next booking; the slot is used up.
                draftRepository.clearSelection()
                _state.update { it.copy(confirmedBookingId = booking.id) }
            } catch (e: AppException.SlotUnavailable) {
                draftRepository.clearSelection()
                _state.update { it.copy(failure = CheckoutFailure.SlotTaken(e.message.orEmpty())) }
            } catch (e: AppException.PaymentDeclined) {
                _state.update { it.copy(failure = CheckoutFailure.Payment(e.message.orEmpty())) }
            } catch (e: AppException) {
                _state.update { it.copy(failure = CheckoutFailure.Other(e.message.orEmpty())) }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun editCustomer(transform: (CustomerDetails) -> CustomerDetails) {
        if (_state.value.isSubmitting) return
        val customer = transform(_state.value.customer)
        draftRepository.updateCustomer(customer)
        _state.update {
            it.copy(
                customer = customer,
                errors = if (validateOnEdit) CustomerValidator.validate(customer) else it.errors,
            )
        }
    }
}
