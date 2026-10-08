package app.android.mainondemand.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.android.mainondemand.di.AppContainer
import app.android.mainondemand.domain.BookingPhase
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.presentation.BookingDetailUiState
import app.android.mainondemand.presentation.BookingDetailViewModel
import app.android.mainondemand.presentation.CancelDialogState
import app.android.mainondemand.presentation.Load
import app.android.mainondemand.ui.components.AppCard
import app.android.mainondemand.ui.components.Avatar
import app.android.mainondemand.ui.components.CheckMark
import app.android.mainondemand.ui.components.DetailRow
import app.android.mainondemand.ui.components.ErrorState
import app.android.mainondemand.ui.components.PrimaryButton
import app.android.mainondemand.ui.components.ScreenPadding
import app.android.mainondemand.ui.components.ScreenScaffold
import app.android.mainondemand.ui.components.SecondaryButton
import app.android.mainondemand.ui.components.SectionLabel
import app.android.mainondemand.ui.components.SkeletonList
import app.android.mainondemand.ui.formatIstDate
import app.android.mainondemand.ui.formatIstDateTime
import app.android.mainondemand.ui.formatIstSlot
import app.android.mainondemand.ui.navigation.HomeTab
import app.android.mainondemand.ui.navigation.Navigator
import app.android.mainondemand.ui.theme.extraColors

@Composable
private fun bookingDetailViewModel(container: AppContainer, bookingId: String): BookingDetailViewModel =
    viewModel { BookingDetailViewModel(bookingId, container.bookingRepository, container.clock) }

@Composable
fun ConfirmationScreen(container: AppContainer, navigator: Navigator, bookingId: String) {
    val viewModel = bookingDetailViewModel(container, bookingId)
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Booking confirmed",
        onBack = navigator::back,
        bottomBar = {
            PrimaryButton(
                "View my bookings",
                onClick = { navigator.backToHome(HomeTab.BOOKINGS) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { navigator.backToHome(HomeTab.EXPLORE) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Book another visit", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        when (val booking = state.booking) {
            Load.Loading -> SkeletonList(Modifier.padding(ScreenPadding), count = 2)
            is Load.Failed -> ErrorState(booking.message, viewModel::onRetry)
            is Load.Ready -> ConfirmationContent(booking.value)
        }
    }
}

@Composable
private fun ConfirmationContent(booking: Booking) {
    val extras = MaterialTheme.extraColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(extras.accent).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(extras.onAccent, Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "You're booked!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${booking.professionalName} will arrive on ${booking.start.formatIstDateTime()}.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Booking ID")
                Spacer(Modifier.height(6.dp))
                BookingIdText(booking.id)
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailRow("Service", "${booking.service.emoji} ${booking.service.label}")
                    DetailRow("Date", booking.start.formatIstDate())
                    DetailRow("Time", booking.start.formatIstSlot())
                    DetailRow("Paid", "${formatInr(booking.amount)} · ${booking.paymentMethod.label}")
                }
            }
        }
    }
}

@Composable
private fun BookingIdText(id: String, modifier: Modifier = Modifier) {
    Text(
        text = id,
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.06.em,
        color = MaterialTheme.colorScheme.primary,
        // Read the ID character by character instead of as a word.
        modifier = modifier.semantics { contentDescription = id.toList().joinToString(" ") },
    )
}

@Composable
fun BookingDetailScreen(container: AppContainer, navigator: Navigator, bookingId: String) {
    val viewModel = bookingDetailViewModel(container, bookingId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    val booking = state.booking
    ScreenScaffold(
        title = "Booking details",
        onBack = navigator::back,
        snackbarHostState = snackbar,
        bottomBar = if (booking is Load.Ready && booking.value.status == BookingStatus.CONFIRMED) {
            {
                if (state.canCancel) {
                    SecondaryButton(
                        "Cancel booking",
                        onClick = viewModel::onCancelRequested,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "Bookings can't be cancelled once they've started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            null
        },
    ) {
        when (booking) {
            Load.Loading -> SkeletonList(Modifier.padding(ScreenPadding), count = 3)
            is Load.Failed -> ErrorState(booking.message, viewModel::onRetry)
            is Load.Ready -> BookingDetailContent(booking.value, state)
        }
    }

    state.cancelDialog?.let { dialog ->
        CancelDialog(
            dialog = dialog,
            booking = booking as? Load.Ready,
            onConfirm = viewModel::onCancelConfirmed,
            onDismiss = viewModel::onCancelDismissed,
        )
    }
}

@Composable
private fun BookingDetailContent(booking: Booking, state: BookingDetailUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Booking ID")
                Spacer(Modifier.height(4.dp))
                BookingIdText(booking.id)
            }
            PhasePill(state.phase ?: BookingPhase.UPCOMING)
        }

        Spacer(Modifier.height(20.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(booking.professionalName, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        booking.professionalName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${booking.service.emoji} ${booking.service.label} · ${booking.localityName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionLabel("Visit", Modifier.padding(top = 24.dp, bottom = 10.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Date", booking.start.formatIstDate())
                DetailRow("Time", booking.start.formatIstSlot())
                DetailRow("Duration", "60 minutes")
                DetailRow("Name", booking.customer.name)
                DetailRow("Mobile", "+91 ${booking.customer.mobile}")
                DetailRow("Address", booking.customer.address)
            }
        }

        SectionLabel("Payment", Modifier.padding(top = 24.dp, bottom = 10.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Total paid", formatInr(booking.amount))
                DetailRow("Method", booking.paymentMethod.label)
                DetailRow("Reference", booking.paymentRef)
                DetailRow("Booked on", booking.createdAt.formatIstDateTime())
                if (booking.status == BookingStatus.CANCELLED) {
                    booking.cancelledAt?.let { DetailRow("Cancelled on", it.formatIstDateTime()) }
                    DetailRow("Refund", formatInr(booking.refundAmount ?: 0))
                }
            }
        }
    }
}

@Composable
private fun CancelDialog(
    dialog: CancelDialogState,
    booking: Load.Ready<Booking>?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this booking?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You'll get a full refund of ${formatInr(dialog.refundAmount)}" +
                        (booking?.value?.paymentMethod?.let { " to your ${it.label}" } ?: "") +
                        ". The slot will be released for others to book.",
                )
                dialog.error?.let {
                    Text(
                        "$it Your booking is unchanged.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isCancelling) {
                if (dialog.isCancelling) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelling…")
                } else {
                    Text(
                        if (dialog.error != null) "Try again" else "Cancel booking",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isCancelling) { Text("Keep booking") }
        },
    )
}
