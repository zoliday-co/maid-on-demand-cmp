package app.android.mainondemand.presentation

import androidx.lifecycle.ViewModel
import app.android.mainondemand.data.fake.Fault
import app.android.mainondemand.data.fake.FaultController
import app.android.mainondemand.data.fake.FaultMode
import kotlinx.coroutines.flow.StateFlow

/** Backs the in-app "Failure lab" sheet that arms the fake backend's failure scenarios. */
class FaultLabViewModel(private val faults: FaultController) : ViewModel() {
    val modes: StateFlow<Map<Fault, FaultMode>> = faults.modes

    fun onModeSelected(fault: Fault, mode: FaultMode) = faults.set(fault, mode)

    fun onReset() = faults.reset()
}
