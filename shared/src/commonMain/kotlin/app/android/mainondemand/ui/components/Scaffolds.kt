package app.android.mainondemand.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Frame for every pushed screen: top bar, content, optional pinned bottom bar. Padding
 * follows the safe-drawing insets, so content moves above the keyboard when it opens.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (snackbarHostState != null) {
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
        }
        if (bottomBar != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 16.dp),
                    content = bottomBar,
                )
            }
        }
    }
}
