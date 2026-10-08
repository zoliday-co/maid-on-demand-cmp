package app.android.mainondemand.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// "Midnight Lime": ink-violet surfaces, an electric-lime accent, lavender and coral support.
private val Lime = Color(0xFFC8F54A)
private val LimeInk = Color(0xFF161D00)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = LimeInk,
    primaryContainer = Color(0xFF2D3A08),
    onPrimaryContainer = Color(0xFFDFFB93),
    secondary = Color(0xFFB9A6FF),
    onSecondary = Color(0xFF1D1147),
    secondaryContainer = Color(0xFF33276A),
    onSecondaryContainer = Color(0xFFE4DBFF),
    tertiary = Color(0xFFFF9E80),
    onTertiary = Color(0xFF3E1404),
    tertiaryContainer = Color(0xFF5B2815),
    onTertiaryContainer = Color(0xFFFFDACC),
    error = Color(0xFFFF7A93),
    onError = Color(0xFF40061A),
    errorContainer = Color(0xFF5A1228),
    onErrorContainer = Color(0xFFFFD9E0),
    background = Color(0xFF0E0B1A),
    onBackground = Color(0xFFF2EEFF),
    surface = Color(0xFF0E0B1A),
    onSurface = Color(0xFFF2EEFF),
    surfaceVariant = Color(0xFF262040),
    onSurfaceVariant = Color(0xFFA9A1C6),
    surfaceContainerLowest = Color(0xFF0A0814),
    surfaceContainerLow = Color(0xFF161226),
    surfaceContainer = Color(0xFF1C1730),
    surfaceContainerHigh = Color(0xFF262040),
    surfaceContainerHighest = Color(0xFF312950),
    outline = Color(0xFF6A618C),
    outlineVariant = Color(0xFF2F2848),
    inverseSurface = Color(0xFFF2EEFF),
    inverseOnSurface = Color(0xFF17122B),
    inversePrimary = Color(0xFF5A3BF0),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A3BF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1D0F63),
    secondary = Color(0xFF4A3AA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7E0FF),
    onSecondaryContainer = Color(0xFF1D0F63),
    tertiary = Color(0xFFD0462A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDD3),
    onTertiaryContainer = Color(0xFF4A1205),
    error = Color(0xFFC3264E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAE1),
    onErrorContainer = Color(0xFF4F0619),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF17122B),
    surface = Color(0xFFF6F3EC),
    onSurface = Color(0xFF17122B),
    surfaceVariant = Color(0xFFECE7DC),
    onSurfaceVariant = Color(0xFF5F5978),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFBF7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECE7DC),
    surfaceContainerHighest = Color(0xFFE2DCCF),
    outline = Color(0xFF8A84A3),
    outlineVariant = Color(0xFFE0DACD),
    inverseSurface = Color(0xFF17122B),
    inverseOnSurface = Color(0xFFF6F3EC),
    inversePrimary = Lime,
    scrim = Color(0xFF000000),
)

/** Colours Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val star: Color,
    val heroGradient: List<Color>,
    val onHero: Color,
    val avatarGradients: List<List<Color>>,
)

private val AvatarGradients = listOf(
    listOf(Color(0xFF7B4DFF), Color(0xFFB9A6FF)),
    listOf(Color(0xFFFF7A59), Color(0xFFFFB199)),
    listOf(Color(0xFF12B886), Color(0xFF7CE7C0)),
    listOf(Color(0xFFE64980), Color(0xFFFFA8C5)),
    listOf(Color(0xFF228BE6), Color(0xFF8CC8FF)),
)

private val DarkExtras = ExtraColors(
    accent = Lime,
    onAccent = LimeInk,
    success = Color(0xFF5BE3A8),
    successContainer = Color(0xFF0F3D2B),
    onSuccessContainer = Color(0xFFB4F5D8),
    star = Color(0xFFFFC043),
    heroGradient = listOf(Color(0xFF2B1A6B), Color(0xFF5A3BF0), Color(0xFF8E6BFF)),
    onHero = Color(0xFFFFFFFF),
    avatarGradients = AvatarGradients,
)

private val LightExtras = ExtraColors(
    accent = Lime,
    onAccent = LimeInk,
    success = Color(0xFF0B7A52),
    successContainer = Color(0xFFD3F5E5),
    onSuccessContainer = Color(0xFF053D29),
    star = Color(0xFFD98A00),
    heroGradient = listOf(Color(0xFF2B1A6B), Color(0xFF5A3BF0), Color(0xFF8E6BFF)),
    onHero = Color(0xFFFFFFFF),
    avatarGradients = AvatarGradients,
)

private val LocalExtraColors = staticCompositionLocalOf { DarkExtras }

val MaterialTheme.extraColors: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
