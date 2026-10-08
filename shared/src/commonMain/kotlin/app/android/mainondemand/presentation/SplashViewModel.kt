package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds the splash timer so a rotation mid-splash doesn't restart it. */
class SplashViewModel(durationMs: Long = 1_600) : ViewModel() {
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch {
            delay(durationMs)
            _finished.value = true
        }
    }
}
