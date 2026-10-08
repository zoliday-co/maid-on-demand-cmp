package app.android.mainondemand.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.android.mainondemand.di.AppContainer
import app.android.mainondemand.domain.CustomerValidator
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.domain.model.DraftSelection
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.presentation.CheckoutFailure
import app.android.mainondemand.presentation.CheckoutUiState
import app.android.mainondemand.presentation.CheckoutViewModel
import app.android.mainondemand.ui.components.AppCard
import app.android.mainondemand.ui.components.DetailRow
import app.android.mainondemand.ui.components.MessageState
import app.android.mainondemand.ui.components.PrimaryButton
import app.android.mainondemand.ui.components.ScreenPadding
import app.android.mainondemand.ui.components.ScreenScaffold
import app.android.mainondemand.ui.components.SectionLabel
import app.android.mainondemand.ui.formatIstDate
import app.android.mainondemand.ui.formatIstSlot
import app.android.mainondemand.ui.navigation.Navigator
import app.android.mainondemand.ui.navigation.PlatformBackHandler
import app.android.mainondemand.ui.navigation.Screen

@Composable
fun CheckoutScreen(container: AppContainer, navigator: Navigator) {
    val viewModel = viewModel { CheckoutViewModel(container.draftRepository, container.bookingRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection = state.selection

    LaunchedEffect(state.confirmedBookingId) {
        state.confirmedBookingId?.let { navigator.replaceAboveHome(Screen.Confirmation(it)) }
    }
    // Leaving mid-payment would cancel the request with the outcome unknown, so hold the screen.
    PlatformBackHandler(enabled = state.isSubmitting) {}

    ScreenScaffold(
        title = "Review & pay",
        onBack = { if (!state.isSubmitting) navigator.back() },
        bottomBar = if (selection != null) {
            { PayBar(state, selection, viewModel::onPay) }
        } else {
            null
        },
    ) {
        if (selection == null) {
            MessageState(
                emoji = "🕐",
                title = "Pick a slot first",
                message = "Choose a professional and a time to continue.",
                actionLabel = "Go back",
                onAction = { navigator.back() },
            )
        } else {
            CheckoutContent(state, selection, viewModel)
        }
    }

    if (state.failure is CheckoutFailure.SlotTaken) {
        AlertDialog(
            onDismissRequest = { navigator.back() },
            title = { Text("That slot was just taken") },
            text = {
                Text("Someone booked ${selection?.professionalName ?: "this professional"} for that time before your payment went through. You have not been charged, and your details are saved.")
            },
            confirmButton = { TextButton(onClick = { navigator.back() }) { Text("Choose another slot") } },
        )
    }
}

@Composable
private fun CheckoutContent(state: CheckoutUiState, selection: DraftSelection, viewModel: CheckoutViewModel) {
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    // Text is held locally so typing never waits on a state round-trip; the ViewModel
    // receives every change and owns validation and the persisted draft.
    var name by remember { mutableStateOf(state.customer.name) }
    var mobile by remember { mutableStateOf(state.customer.mobile) }
    var address by remember { mutableStateOf(state.customer.address) }
    val editable = !state.isSubmitting

    LaunchedEffect(state.errors) {
        if (!state.errors.isValid) scroll.animateScrollTo(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = ScreenPadding)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        SectionLabel("Your details", Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.onNameChanged(it)
                },
                label = { Text("Full name") },
                isError = state.errors.name != null,
                supportingText = state.errors.name?.let { { Text(it) } },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = mobile,
                onValueChange = {
                    val digits = CustomerValidator.sanitizeMobile(it)
                    mobile = digits
                    viewModel.onMobileChanged(digits)
                },
                label = { Text("Mobile number") },
                prefix = { Text("+91 ") },
                isError = state.errors.mobile != null,
                supportingText = { Text(state.errors.mobile ?: "10-digit Indian mobile number") },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    viewModel.onAddressChanged(it)
                },
                label = { Text("Service address") },
                isError = state.errors.address != null,
                supportingText = { Text(state.errors.address ?: "House / flat, street, area and pincode") },
                minLines = 2,
                maxLines = 4,
                enabled = editable,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionLabel("Your booking", Modifier.padding(top = 20.dp, bottom = 10.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Professional", selection.professionalName)
                DetailRow("Service", "${selection.service.emoji} ${selection.service.label}")
                DetailRow("Date", selection.slotStart.formatIstDate())
                DetailRow("Time", selection.slotStart.formatIstSlot())
                DetailRow("Duration", "60 minutes")
                DetailRow("Area", "${selection.localityName}, Bengaluru")
            }
        }

        SectionLabel("Pay with", Modifier.padding(top = 24.dp, bottom = 10.dp))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaymentMethod.entries.forEach { method ->
                PaymentTile(
                    method = method,
                    selected = method == state.paymentMethod,
                    enabled = editable,
                    onClick = { viewModel.onPaymentMethodSelected(method) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "This is a simulated payment. No UPI ID or card details are collected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun PaymentTile(
    method: PaymentMethod,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) scheme.primary else Color.Transparent)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainer)
            .border(2.dp, border, MaterialTheme.shapes.large)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            method.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) scheme.primary else scheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(method.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun PayBar(state: CheckoutUiState, selection: DraftSelection, onPay: () -> Unit) {
    val failure = state.failure
    if (failure is CheckoutFailure.Payment || failure is CheckoutFailure.Other) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(14.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        ) {
            Text(
                if (failure is CheckoutFailure.Payment) "Payment failed" else "Booking didn't go through",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "${failure.message} Your details are saved — try again when you're ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    } else if (!state.errors.isValid) {
        Text(
            "Check the highlighted details above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatInr(selection.price),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(12.dp))
        PrimaryButton(
            text = when {
                state.isSubmitting -> "Paying…"
                failure != null -> "Retry payment"
                else -> "Pay ${formatInr(selection.price)}"
            },
            loading = state.isSubmitting,
            enabled = failure !is CheckoutFailure.SlotTaken,
            onClick = onPay,
        )
    }
}
