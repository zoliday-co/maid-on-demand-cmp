package app.android.mainondemand.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The brand glyph: a home with a "sparkling clean" star. Same geometry as the Android
 * launcher icon (`ic_launcher_foreground.xml`), drawn in code so both platforms share it.
 */
@Composable
fun AppLogo(houseColor: Color, sparkleColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // The glyph is designed on a 60-unit square.
        val unit = size.minDimension / 60f
        fun x(value: Float) = (size.width - 60f * unit) / 2 + value * unit
        fun y(value: Float) = (size.height - 60f * unit) / 2 + value * unit

        val house = Path().apply {
            moveTo(x(30f), y(7f))
            lineTo(x(53f), y(27f))
            lineTo(x(53f), y(53f))
            lineTo(x(7f), y(53f))
            lineTo(x(7f), y(27f))
            close()
        }
        drawPath(house, houseColor)
        // A same-colour stroke with round joins is what softens the corners.
        drawPath(house, houseColor, style = Stroke(width = 7f * unit, join = StrokeJoin.Round))

        val sparkle = Path().apply {
            moveTo(x(30f), y(21f))
            cubicTo(x(31.6f), y(30f), x(34f), y(32.4f), x(43f), y(34f))
            cubicTo(x(34f), y(35.6f), x(31.6f), y(38f), x(30f), y(47f))
            cubicTo(x(28.4f), y(38f), x(26f), y(35.6f), x(17f), y(34f))
            cubicTo(x(26f), y(32.4f), x(28.4f), y(30f), x(30f), y(21f))
            close()
        }
        drawPath(sparkle, sparkleColor)
    }
}
