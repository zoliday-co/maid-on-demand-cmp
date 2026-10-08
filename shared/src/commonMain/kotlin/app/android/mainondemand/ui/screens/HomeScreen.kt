package app.android.mainondemand.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.android.mainondemand.data.fake.FaultMode
import app.android.mainondemand.di.AppContainer
import app.android.mainondemand.presentation.BookingsViewModel
import app.android.mainondemand.presentation.FaultLabViewModel
import app.android.mainondemand.presentation.SearchViewModel
import app.android.mainondemand.ui.navigation.HomeTab
import app.android.mainondemand.ui.navigation.Navigator
import app.android.mainondemand.ui.navigation.Screen

@Composable
fun HomeScreen(container: AppContainer, navigator: Navigator) {
    // Both ViewModels live as long as Home does, so search criteria survive every detour.
    val searchViewModel = viewModel { SearchViewModel(container.professionalRepository, container.clock) }
    val bookingsViewModel = viewModel { BookingsViewModel(container.bookingRepository, container.clock) }
    val labViewModel = viewModel { FaultLabViewModel(container.faults) }

    val tab by navigator.tab.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val bookingsState by bookingsViewModel.state.collectAsStateWithLifecycle()
    val faultModes by labViewModel.modes.collectAsStateWithLifecycle()
    var showLab by rememberSaveable { mutableStateOf(false) }

    val tabStates = rememberSaveableStateHolder()
    val listPadding = PaddingValues(top = 8.dp, bottom = 112.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Crossfade(targetState = tab) { current ->
            tabStates.SaveableStateProvider(current.name) {
                when (current) {
                    HomeTab.EXPLORE -> ExploreTab(
                        state = searchState,
                        viewModel = searchViewModel,
                        faultsArmed = faultModes.values.any { it != FaultMode.OFF },
                        onOpenLab = { showLab = true },
                        onOpenResult = { result ->
                            navigator.push(
                                Screen.Profile(
                                    professionalId = result.professional.id,
                                    service = searchState.criteria.service,
                                    date = searchState.criteria.date,
                                    // Only preselect a time the customer actually asked for.
                                    preferredStart = result.matchedSlotStart.takeIf { searchState.criteria.startHour != null },
                                ),
                            )
                        },
                        contentPadding = listPadding,
                    )

                    HomeTab.BOOKINGS -> {
                        LaunchedEffect(Unit) { bookingsViewModel.onShown() }
                        BookingsTab(
                            state = bookingsState,
                            onRetry = bookingsViewModel::onRetry,
                            onOpenBooking = { navigator.push(Screen.BookingDetail(it)) },
                            onExplore = { navigator.selectTab(HomeTab.EXPLORE) },
                            contentPadding = listPadding,
                        )
                    }
                }
            }
        }

        TabBar(
            selected = tab,
            onSelect = navigator::selectTab,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }

    if (showLab) {
        FaultLabSheet(
            modes = faultModes,
            onModeSelected = labViewModel::onModeSelected,
            onReset = labViewModel::onReset,
            onDismiss = { showLab = false },
        )
    }
}

/** Floating segmented switch between the two top-level destinations. */
@Composable
private fun TabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
    ) {
        Row(Modifier.padding(6.dp).selectableGroup()) {
            HomeTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val scheme = MaterialTheme.colorScheme
                val container by animateColorAsState(if (isSelected) scheme.primary else scheme.surfaceContainerHighest)
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 120.dp)
                        .clip(CircleShape)
                        .background(container)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) scheme.onPrimary else scheme.onSurface,
                    )
                }
            }
        }
    }
}
