package app.android.mainondemand.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.android.mainondemand.domain.CustomerValidator
import app.android.mainondemand.presentation.SplashViewModel
import app.android.mainondemand.ui.components.AppLogo
import app.android.mainondemand.ui.components.PrimaryButton
import app.android.mainondemand.ui.components.ScreenPadding
import app.android.mainondemand.ui.theme.extraColors

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val viewModel = viewModel { SplashViewModel() }
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onFinished() }

    val extras = MaterialTheme.extraColors
    val logoScale = remember { Animatable(0.6f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { textAlpha.animateTo(1f, tween(durationMillis = 500, delayMillis = 350)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(extras.heroGradient))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics(mergeDescendants = true) { contentDescription = "Maid on Demand" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(
                houseColor = extras.accent,
                sparkleColor = extras.heroGradient.first(),
                modifier = Modifier.size(132.dp).graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
            )
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value }.clearAndSetSemantics { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Maid on Demand", style = MaterialTheme.typography.displaySmall, color = extras.onHero)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Home help, on your schedule",
                    style = MaterialTheme.typography.bodyLarge,
                    color = extras.onHero.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Demo sign-in. Authentication is out of scope for the assignment, so the number is
 * optional and "Log in" always continues to Home.
 */
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val extras = MaterialTheme.extraColors
    val focus = LocalFocusManager.current
    var mobile by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(extras.heroGradient))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(
                    houseColor = extras.accent,
                    sparkleColor = extras.heroGradient.first(),
                    modifier = Modifier.size(56.dp).clearAndSetSemantics { },
                )
                Spacer(Modifier.width(12.dp))
                Text("Maid on Demand", style = MaterialTheme.typography.titleLarge, color = extras.onHero)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "A cleaner home\nis one tap away.",
                style = MaterialTheme.typography.displaySmall,
                color = extras.onHero,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Book trusted pros for cleaning, cooking and dishes in under a minute.",
                style = MaterialTheme.typography.bodyLarge,
                color = extras.onHero.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🧹 Cleaning", "🍳 Cooking", "🍽️ Dishes").forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onHero,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(extras.onHero.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text(
                    "Welcome",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Log in to book and manage your visits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = CustomerValidator.sanitizeMobile(it) },
                    label = { Text("Mobile number (optional)") },
                    prefix = { Text("+91 ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Log in", onClick = onLogin, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(
                    "Demo app — no account or OTP needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
