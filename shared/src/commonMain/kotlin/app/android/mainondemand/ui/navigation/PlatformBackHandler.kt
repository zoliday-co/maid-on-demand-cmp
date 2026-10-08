package app.android.mainondemand.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform's system back action while [enabled]. Android routes the back
 * button / gesture here; iOS has no system back, so its implementation does nothing and
 * screens rely on their on-screen back button.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
