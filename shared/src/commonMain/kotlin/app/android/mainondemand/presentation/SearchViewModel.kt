package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.android.mainondemand.domain.AppException
import app.android.mainondemand.domain.bookableDates
import app.android.mainondemand.domain.futureHours
import app.android.mainondemand.domain.model.Locality
import app.android.mainondemand.domain.model.SearchCriteria
import app.android.mainondemand.domain.model.SearchResult
import app.android.mainondemand.domain.model.ServiceType
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

data class SearchUiState(
    val localities: List<Locality>,
    val dates: List<LocalDate>,
    /** Start hours still bookable on the selected date. */
    val hours: List<Int>,
    val criteria: SearchCriteria,
    val results: Load<List<SearchResult>> = Load.Loading,
    /** True while results for the current criteria are being re-fetched in the background. */
    val isRefreshing: Boolean = false,
) {
    val hasFilters: Boolean get() = criteria.minRating != null || criteria.maxPrice != null
}

class SearchViewModel(
    private val repository: ProfessionalRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        search()
        viewModelScope.launch {
            // Someone booked or cancelled: refresh quietly so results never show a taken slot.
            repository.availabilityVersion.drop(1).collect { search(quiet = true) }
        }
    }

    fun onLocalitySelected(localityId: String) = changeCriteria { it.copy(localityId = localityId) }

    fun onServiceSelected(service: ServiceType) = changeCriteria { it.copy(service = service) }

    fun onDateSelected(date: LocalDate) = changeCriteria { it.copy(date = date) }

    fun onHourSelected(hour: Int?) = changeCriteria { it.copy(startHour = hour) }

    fun onMinRatingSelected(minRating: Double?) = changeCriteria { it.copy(minRating = minRating) }

    fun onMaxPriceSelected(maxPrice: Int?) = changeCriteria { it.copy(maxPrice = maxPrice) }

    fun onClearFilters() = changeCriteria { it.copy(minRating = null, maxPrice = null, startHour = null) }

    fun onRetry() = search()

    private fun changeCriteria(transform: (SearchCriteria) -> SearchCriteria) {
        val current = _state.value
        var criteria = transform(current.criteria)
        if (criteria == current.criteria) return
        val hours = futureHours(criteria.date, clock.now())
        if (criteria.startHour != null && criteria.startHour !in hours) criteria = criteria.copy(startHour = null)
        _state.update { it.copy(criteria = criteria, hours = hours) }
        search()
    }

    private fun search(quiet: Boolean = false) {
        // Cancelling the in-flight request is what keeps stale results off the screen; the
        // criteria check below also covers a response that slips through cancellation.
        searchJob?.cancel()
        val criteria = _state.value.criteria
        _state.update {
            if (quiet && it.results is Load.Ready) it.copy(isRefreshing = true)
            else it.copy(results = Load.Loading, isRefreshing = false)
        }
        searchJob = viewModelScope.launch {
            val outcome = try {
                Load.Ready(repository.search(criteria))
            } catch (e: AppException) {
                Load.Failed(e.message ?: "Search failed.")
            }
            _state.update {
                when {
                    it.criteria != criteria -> it
                    // A failed background refresh keeps the results already on screen.
                    quiet && outcome is Load.Failed && it.results is Load.Ready -> it.copy(isRefreshing = false)
                    else -> it.copy(results = outcome, isRefreshing = false)
                }
            }
        }
    }

    private fun initialState(): SearchUiState {
        val now = clock.now()
        val today = clock.todayIst()
        val dates = bookableDates(today)
        // Late in the evening there is nothing left to book today, so start on tomorrow.
        val date = dates.first { futureHours(it, now).isNotEmpty() }
        return SearchUiState(
            localities = repository.localities,
            dates = dates,
            hours = futureHours(date, now),
            criteria = SearchCriteria(
                localityId = repository.localities.first().id,
                service = ServiceType.CLEANING,
                date = date,
            ),
        )
    }
}
