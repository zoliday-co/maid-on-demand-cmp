package app.android.mainondemand.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.android.mainondemand.di.AppContainer
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.domain.model.Professional
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.domain.model.Slot
import app.android.mainondemand.presentation.Load
import app.android.mainondemand.presentation.ProfileUiState
import app.android.mainondemand.presentation.ProfileViewModel
import app.android.mainondemand.ui.components.AppCard
import app.android.mainondemand.ui.components.Avatar
import app.android.mainondemand.ui.components.DateStrip
import app.android.mainondemand.ui.components.ErrorState
import app.android.mainondemand.ui.components.PillChip
import app.android.mainondemand.ui.components.PrimaryButton
import app.android.mainondemand.ui.components.RatingText
import app.android.mainondemand.ui.components.ScreenPadding
import app.android.mainondemand.ui.components.ScreenScaffold
import app.android.mainondemand.ui.components.SectionLabel
import app.android.mainondemand.ui.components.SkeletonBlock
import app.android.mainondemand.ui.components.SkeletonList
import app.android.mainondemand.ui.formatIstDate
import app.android.mainondemand.ui.formatIstSlot
import app.android.mainondemand.ui.formatIstTime
import app.android.mainondemand.ui.formatShort
import app.android.mainondemand.ui.navigation.Navigator
import app.android.mainondemand.ui.navigation.Screen

@Composable
fun ProfileScreen(container: AppContainer, navigator: Navigator, screen: Screen.Profile) {
    val viewModel = viewModel {
        ProfileViewModel(
            professionalId = screen.professionalId,
            initialService = screen.service,
            initialDate = screen.date,
            preferredStart = screen.preferredStart,
            repository = container.professionalRepository,
            draftRepository = container.draftRepository,
            clock = container.clock,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val professional = state.professional

    ScreenScaffold(
        title = "Professional",
        onBack = navigator::back,
        bottomBar = if (professional is Load.Ready) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.price?.let(::formatInr).orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            state.selectedSlot?.let { "${it.formatIstDate()} · ${it.formatIstTime()}" } ?: "Select a slot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PrimaryButton(
                        text = "Continue",
                        enabled = state.canContinue,
                        onClick = { if (viewModel.confirmSelection()) navigator.push(Screen.Checkout) },
                    )
                }
            }
        } else {
            null
        },
    ) {
        when (professional) {
            Load.Loading -> SkeletonList(Modifier.padding(ScreenPadding), count = 4)
            is Load.Failed -> ErrorState(professional.message, viewModel::onRetryProfessional)
            is Load.Ready -> ProfileContent(professional.value, state, viewModel)
        }
    }
}

@Composable
private fun ProfileContent(pro: Professional, state: ProfileUiState, viewModel: ProfileViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(pro.name, size = 76.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(pro.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${state.localityName}, Bengaluru",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                RatingText(pro.rating, pro.reviewCount)
            }
        }

        Row(
            Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile("${pro.experienceYears} yrs", "Experience", Modifier.weight(1f))
            StatTile(pro.jobsCompleted.toString(), "Visits done", Modifier.weight(1f))
            StatTile(pro.languages.size.toString(), "Languages", Modifier.weight(1f))
        }

        Text(
            pro.about,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 20.dp),
        )
        Text(
            "Speaks ${pro.languages.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 6.dp),
        )

        SectionLabel("Skills & pricing", Modifier.padding(start = ScreenPadding, top = 28.dp, bottom = 10.dp))
        Column(
            Modifier.padding(horizontal = ScreenPadding).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pro.services.forEach { service ->
                ServiceRow(
                    service = service,
                    price = pro.prices.getValue(service),
                    selected = service == state.service,
                    onClick = { viewModel.onServiceSelected(service) },
                )
            }
        }

        SectionLabel("Date", Modifier.padding(start = ScreenPadding, top = 28.dp, bottom = 10.dp))
        DateStrip(state.dates, state.selectedDate, viewModel::onDateSelected)

        SectionLabel("Start time · India time (IST)", Modifier.padding(start = ScreenPadding, top = 28.dp, bottom = 10.dp))
        Column(Modifier.padding(horizontal = ScreenPadding)) {
            state.slotNotice?.let { notice ->
                Text(
                    notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            when (val slots = state.slots) {
                Load.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(44.dp), MaterialTheme.shapes.extraLarge) }
                }

                is Load.Failed -> Column {
                    Text(slots.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::onRetrySlots, contentPadding = PaddingValues(0.dp)) {
                        Text("Try again")
                    }
                }

                is Load.Ready -> if (slots.value.none { it.isAvailable }) {
                    Text(
                        "No slots left on ${state.selectedDate.formatShort()}. Try another date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SlotGrid(slots.value, state, viewModel::onSlotSelected)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.selectedSlot?.let { "Your visit: ${it.formatIstSlot()} (60 minutes)." }
                    ?: "Every visit lasts 60 minutes. Greyed-out times are already booked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SlotGrid(slots: List<Slot>, state: ProfileUiState, onSelect: (Slot) -> Unit) {
    val columns = 3
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { slot ->
                    PillChip(
                        text = slot.start.formatIstTime(),
                        selected = slot.start == state.selectedSlot,
                        enabled = slot.isAvailable,
                        onClick = { onSelect(slot) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServiceRow(service: ServiceType, price: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) scheme.primary else Color.Transparent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainer)
            .border(2.dp, border, MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceContainerHigh)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(service.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(service.label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(service.tagline, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatInr(price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) scheme.primary else scheme.onSurface,
            )
            Text("60 min", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
