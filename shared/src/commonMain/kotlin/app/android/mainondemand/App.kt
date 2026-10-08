package app.android.mainondemand

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.android.mainondemand.di.AppContainer
import app.android.mainondemand.ui.navigation.HomeTab
import app.android.mainondemand.ui.navigation.Navigator
import app.android.mainondemand.ui.navigation.PlatformBackHandler
import app.android.mainondemand.ui.navigation.Screen
import app.android.mainondemand.ui.screens.BookingDetailScreen
import app.android.mainondemand.ui.screens.CheckoutScreen
import app.android.mainondemand.ui.screens.ConfirmationScreen
import app.android.mainondemand.ui.screens.HomeScreen
import app.android.mainondemand.ui.screens.LoginScreen
import app.android.mainondemand.ui.screens.ProfileScreen
import app.android.mainondemand.ui.screens.SplashScreen
import app.android.mainondemand.ui.theme.AppTheme

/** Shared entry point. Each platform builds the [AppContainer] once and hands it in. */
@Composable
fun App(container: AppContainer) {
    AppTheme {
        val navigator = viewModel { Navigator() }
        val stack by navigator.stack.collectAsStateWithLifecycle()
        val tab by navigator.tab.collectAsStateWithLifecycle()
        val screenStates = rememberSaveableStateHolder()

        PlatformBackHandler(enabled = stack.size > 1 || tab != HomeTab.EXPLORE) { navigator.back() }

        AnimatedContent(
            targetState = stack.last(),
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentKey = { it.id },
            transitionSpec = {
                // Entry ids only grow, so a larger id means we are going deeper.
                val direction = if (targetState.id > initialState.id) 1 else -1
                (slideInHorizontally { direction * it / 5 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -direction * it / 5 } + fadeOut())
            },
        ) { entry ->
            // Each entry owns its ViewModels and saved UI state (scroll positions, text).
            CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                screenStates.SaveableStateProvider(entry.id) {
                    when (val screen = entry.screen) {
                        Screen.Splash -> SplashScreen(onFinished = { navigator.replaceAll(Screen.Login) })
                        Screen.Login -> LoginScreen(onLogin = { navigator.replaceAll(Screen.Home) })
                        Screen.Home -> HomeScreen(container, navigator)
                        is Screen.Profile -> ProfileScreen(container, navigator, screen)
                        Screen.Checkout -> CheckoutScreen(container, navigator)
                        is Screen.Confirmation -> ConfirmationScreen(container, navigator, screen.bookingId)
                        is Screen.BookingDetail -> BookingDetailScreen(container, navigator, screen.bookingId)
                    }
                }
            }
            DisposableEffect(entry) {
                onDispose {
                    if (entry !in navigator.stack.value) screenStates.removeState(entry.id)
                    navigator.onEntryDisposed(entry)
                }
            }
        }
    }
}
