package app.android.mainondemand.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.android.mainondemand.domain.BookingPhase
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.presentation.BookingItem
import app.android.mainondemand.presentation.BookingsUiState
import app.android.mainondemand.ui.components.AppCard
import app.android.mainondemand.ui.components.ErrorState
import app.android.mainondemand.ui.components.MessageState
import app.android.mainondemand.ui.components.ScreenPadding
import app.android.mainondemand.ui.components.SectionLabel
import app.android.mainondemand.ui.components.SkeletonList
import app.android.mainondemand.ui.components.StatusPill
import app.android.mainondemand.ui.formatIstDateTime
import app.android.mainondemand.ui.theme.extraColors

@Composable
fun BookingsTab(
    state: BookingsUiState,
    onRetry: () -> Unit,
    onOpenBooking: (String) -> Unit,
    onExplore: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Text(
                "Your bookings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = ScreenPadding)
                    .padding(top = 16.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
        }
        val upcoming = state.upcoming
        when {
            upcoming == null && state.error != null -> item(key = "error") { ErrorState(state.error, onRetry) }

            upcoming == null -> item(key = "loading") {
                SkeletonList(Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp), itemHeight = 116.dp)
            }

            state.isEmpty -> item(key = "empty") {
                MessageState(
                    emoji = "🗓️",
                    title = "No bookings yet",
                    message = "Book a professional and it will show up here.",
                    actionLabel = "Find a professional",
                    onAction = onExplore,
                )
            }

            else -> {
                item(key = "upcoming-label") {
                    SectionLabel("Upcoming", Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp, bottom = 10.dp))
                }
                if (upcoming.isEmpty()) {
                    item(key = "upcoming-empty") {
                        Text(
                            "Nothing scheduled. Your next booking will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 8.dp),
                        )
                    }
                }
                items(upcoming, key = { it.booking.id }) { BookingCard(it, onOpenBooking) }
                if (state.past.isNotEmpty()) {
                    item(key = "past-label") {
                        SectionLabel(
                            "Past & cancelled",
                            Modifier.padding(horizontal = ScreenPadding).padding(top = 20.dp, bottom = 10.dp),
                        )
                    }
                    items(state.past, key = { it.booking.id }) { BookingCard(it, onOpenBooking) }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(item: BookingItem, onOpen: (String) -> Unit) {
    val booking = item.booking
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 12.dp),
        onClick = { onOpen(booking.id) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(booking.service.emoji, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${booking.service.label} · ${booking.professionalName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        booking.start.formatIstDateTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhasePill(item.phase)
                Spacer(Modifier.width(10.dp))
                Text(
                    booking.id,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatInr(booking.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun PhasePill(phase: BookingPhase, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extras = MaterialTheme.extraColors
    val (container, content) = when (phase) {
        BookingPhase.UPCOMING -> extras.successContainer to extras.onSuccessContainer
        BookingPhase.IN_PROGRESS -> scheme.secondaryContainer to scheme.onSecondaryContainer
        BookingPhase.COMPLETED -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        BookingPhase.CANCELLED -> scheme.errorContainer to scheme.onErrorContainer
    }
    StatusPill(if (phase == BookingPhase.UPCOMING) "Confirmed" else phase.label, container, content, modifier)
}
