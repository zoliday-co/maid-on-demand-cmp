package app.android.mainondemand.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import app.android.mainondemand.domain.model.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object Home : Screen
    data class Profile(
        val professionalId: String,
        val service: ServiceType,
        val date: LocalDate,
        val preferredStart: Instant?,
    ) : Screen

    data object Checkout : Screen
    data class Confirmation(val bookingId: String) : Screen
    data class BookingDetail(val bookingId: String) : Screen
}

enum class HomeTab(val label: String) { EXPLORE("Explore"), BOOKINGS("Bookings") }

/**
 * One back-stack entry. It owns the ViewModels of its screen, so their coroutines are
 * cancelled when the entry is popped rather than when the whole app goes away.
 */
class NavEntry(val id: Int, val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Back stack held in a ViewModel so it survives configuration changes. */
class Navigator : ViewModel() {
    private var nextId = 0
    private val _stack = MutableStateFlow(listOf(NavEntry(nextId++, Screen.Splash)))
    val stack: StateFlow<List<NavEntry>> = _stack.asStateFlow()

    private val _tab = MutableStateFlow(HomeTab.EXPLORE)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    fun push(screen: Screen) = _stack.update { stack ->
        // A double tap must not open the same screen twice.
        if (stack.last().screen == screen) stack else stack + NavEntry(nextId++, screen)
    }

    /** Returns false when there is nothing left to go back to and the platform should take over. */
    fun back(): Boolean {
        if (_stack.value.size > 1) {
            _stack.update { it.dropLast(1) }
            return true
        }
        if (_tab.value != HomeTab.EXPLORE) {
            _tab.value = HomeTab.EXPLORE
            return true
        }
        return false
    }

    fun selectTab(tab: HomeTab) {
        _tab.value = tab
    }

    /** Replaces the whole stack — splash to login, login to Home — so back never returns to them. */
    fun replaceAll(screen: Screen) = _stack.update { stack ->
        if (stack.last().screen == screen) stack else listOf(NavEntry(nextId++, screen))
    }

    /** Drops everything above Home, then shows [screen] — used once a booking is confirmed. */
    fun replaceAboveHome(screen: Screen) = _stack.update { listOf(it.first(), NavEntry(nextId++, screen)) }

    fun backToHome(tab: HomeTab) {
        _tab.value = tab
        _stack.update { it.take(1) }
    }

    /** Called when an entry's UI has left the screen; clears it if it was popped. */
    fun onEntryDisposed(entry: NavEntry) {
        if (entry !in _stack.value) entry.viewModelStore.clear()
    }

    override fun onCleared() {
        _stack.value.forEach { it.viewModelStore.clear() }
    }
}
