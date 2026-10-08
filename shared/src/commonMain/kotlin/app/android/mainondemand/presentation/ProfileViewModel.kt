package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.bookableDates
import app.android.mainondemand.domain.model.DraftSelection
import app.android.mainondemand.domain.model.Professional
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.domain.model.Slot
import app.android.mainondemand.domain.repository.DraftRepository
import app.android.mainondemand.domain.repository.ProfessionalRepository
import app.android.mainondemand.domain.todayIst
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

data class ProfileUiState(
    val professional: Load<Professional> = Load.Loading,
    val localityName: String = "",
    val service: ServiceType,
    val dates: List<LocalDate>,
    val selectedDate: LocalDate,
    val slots: Load<List<Slot>> = Load.Loading,
    val selectedSlot: Instant? = null,
    /** Set when the chosen slot disappeared and the customer has to pick again. */
    val slotNotice: String? = null,
) {
    val price: Int? get() = professional.valueOrNull?.prices?.get(service)
    val canContinue: Boolean get() = selectedSlot != null && price != null
}

class ProfileViewModel(
    private val professionalId: String,
    initialService: ServiceType,
    initialDate: LocalDate,
    /** Slot the customer searched for; preselected if it is still open. */
    private val preferredStart: Instant?,
    private val repository: ProfessionalRepository,
    private val draftRepository: DraftRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        bookableDates(clock.todayIst()).let { dates ->
            ProfileUiState(
                service = initialService,
                dates = dates,
                selectedDate = initialDate.takeIf { it in dates } ?: dates.first(),
            )
        },
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var slotsJob: Job? = null

    init {
        loadProfessional()
        loadSlots()
        viewModelScope.launch {
            // Availability changed somewhere (conflict at checkout, a booking, a cancellation).
            repository.availabilityVersion.drop(1).collect { loadSlots(quiet = true) }
        }
    }

    fun onServiceSelected(service: ServiceType) = _state.update { it.copy(service = service) }

    fun onDateSelected(date: LocalDate) {
        if (date == _state.value.selectedDate) return
        _state.update { it.copy(selectedDate = date, selectedSlot = null, slotNotice = null) }
        loadSlots()
    }

    fun onSlotSelected(slot: Slot) {
        if (!slot.isAvailable) return
        _state.update { it.copy(selectedSlot = slot.start, slotNotice = null) }
    }

    fun onRetryProfessional() = loadProfessional()

    fun onRetrySlots() = loadSlots()

    /** Stores the selection in the draft. Returns false if there is nothing valid to continue with. */
    fun confirmSelection(): Boolean {
        val current = _state.value
        val pro = current.professional.valueOrNull ?: return false
        val price = current.price ?: return false
        val slot = current.selectedSlot ?: return false
        draftRepository.select(
            DraftSelection(
                professionalId = pro.id,
                professionalName = pro.name,
                localityName = current.localityName,
                service = current.service,
                price = price,
                slotStart = slot,
            ),
        )
        return true
    }

    private fun loadProfessional() {
        _state.update { it.copy(professional = Load.Loading) }
        viewModelScope.launch {
            val outcome = try {
                Load.Ready(repository.professional(professionalId))
            } catch (e: AppException) {
                Load.Failed(e.message ?: "Couldn't load this profile.")
            }
            _state.update { current ->
                val pro = outcome.valueOrNull
                current.copy(
                    professional = outcome,
                    localityName = repository.localities.firstOrNull { it.id == pro?.localityId }?.name.orEmpty(),
                    // The searched service always exists on the profile, but never trust a route argument.
                    service = if (pro == null || current.service in pro.prices) current.service else pro.services.first(),
                )
            }
        }
    }

    private fun loadSlots(quiet: Boolean = false) {
        slotsJob?.cancel()
        val date = _state.value.selectedDate
        if (!quiet || _state.value.slots !is Load.Ready) _state.update { it.copy(slots = Load.Loading) }
        slotsJob = viewModelScope.launch {
            val outcome = try {
                Load.Ready(repository.slots(professionalId, date))
            } catch (e: AppException) {
                Load.Failed(e.message ?: "Couldn't load slots.")
            }
            _state.update { current ->
                if (current.selectedDate != date) return@update current
                val slots = outcome.valueOrNull ?: return@update current.copy(slots = outcome)
                val open = slots.filter { it.isAvailable }.map { it.start }
                val wanted = current.selectedSlot
                when {
                    wanted != null && wanted !in open -> current.copy(
                        slots = outcome,
                        selectedSlot = null,
                        slotNotice = "That slot was just taken. We've refreshed the times — pick another.",
                    )

                    wanted == null && current.slotNotice == null && preferredStart != null && preferredStart in open ->
                        current.copy(slots = outcome, selectedSlot = preferredStart)

                    else -> current.copy(slots = outcome)
                }
            }
        }
    }
}
