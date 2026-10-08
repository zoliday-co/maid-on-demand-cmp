package app.android.mainondemand.data.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The failure scenarios the fake backend can be told to produce. */
enum class Fault(val title: String, val description: String) {
    NO_RESULTS("No results", "Search succeeds but finds nobody."),
    SEARCH_FAILURE("Search failure", "Search fails with a network error."),
    SLOT_CONFLICT("Slot conflict", "Someone else takes your slot just as you pay."),
    PAYMENT_FAILURE("Payment failure", "The payment is declined. No booking is created."),
    CANCELLATION_FAILURE("Cancellation failure", "Cancelling fails with a network error."),
}

enum class FaultMode(val label: String) {
    OFF("Off"),

    /** Fails the next matching call, then switches itself off — handy for failure-then-retry demos. */
    ONCE("Next call"),
    ALWAYS("Always"),
}

class FaultController {
    private val _modes = MutableStateFlow(Fault.entries.associateWith { FaultMode.OFF })
    val modes: StateFlow<Map<Fault, FaultMode>> = _modes.asStateFlow()

    fun set(fault: Fault, mode: FaultMode) {
        _modes.update { it + (fault to mode) }
    }

    fun reset() {
        _modes.value = Fault.entries.associateWith { FaultMode.OFF }
    }

    /** True if [fault] should fire for the current call. Disarms a [FaultMode.ONCE] fault. */
    internal fun consume(fault: Fault): Boolean {
        var fired = false
        _modes.update { modes ->
            when (modes[fault]) {
                FaultMode.ALWAYS -> {
                    fired = true
                    modes
                }

                FaultMode.ONCE -> {
                    fired = true
                    modes + (fault to FaultMode.OFF)
                }

                else -> {
                    fired = false
                    modes
                }
            }
        }
        return fired
    }
}
