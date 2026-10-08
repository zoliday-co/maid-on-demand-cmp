package app.android.mainondemand.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.android.mainondemand.ui.formatRating
import app.android.mainondemand.ui.formatSpoken
import app.android.mainondemand.ui.theme.extraColors
import app.android.mainondemand.ui.weekdayShort
import kotlinx.datetime.LocalDate

/** Horizontal gutter shared by every screen. */
val ScreenPadding = 20.dp

@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHigh)
    val content by animateColorAsState(
        when {
            selected -> scheme.onPrimary
            enabled -> scheme.onSurface
            else -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
    )
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            textDecoration = if (enabled) null else TextDecoration.LineThrough,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, content = content)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        // Stays visually enabled while loading, but swallows taps so nothing is submitted twice.
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.2.dp.toPx()
            val mid = size.height / 2
            val tip = Offset(size.width * 0.12f, mid)
            drawLine(color, tip, Offset(size.width * 0.9f, mid), stroke, StrokeCap.Round)
            drawLine(color, tip, Offset(size.width * 0.48f, mid - size.width * 0.36f), stroke, StrokeCap.Round)
            drawLine(color, tip, Offset(size.width * 0.48f, mid + size.width * 0.36f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}

/** Initials on a gradient picked from the name, so each professional keeps a colour. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val gradients = MaterialTheme.extraColors.avatarGradients
    val gradient = gradients[name.hashCode().mod(gradients.size)]
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
fun RatingText(rating: Double, reviewCount: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Rated ${rating.formatRating()} out of 5" +
                (reviewCount?.let { " from $it reviews" } ?: "")
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = MaterialTheme.extraColors.star, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(4.dp))
        Text(rating.formatRating(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (reviewCount != null) {
            Text(
                " ($reviewCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MessageState(
    emoji: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 30.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        emoji = "📡",
        title = "Something went wrong",
        message = message,
        modifier = modifier,
        actionLabel = "Try again",
        onAction = onRetry,
    )
}

/** Pulsing placeholder shown while content loads. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    )
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
fun SkeletonList(modifier: Modifier = Modifier, count: Int = 3, itemHeight: Dp = 132.dp) {
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) { SkeletonBlock(Modifier.fillMaxWidth().height(itemHeight)) }
    }
}

@Composable
fun DateStrip(
    dates: List<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = ScreenPadding),
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (dates.indexOf(selected) - 1).coerceAtLeast(0),
    )
    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates, key = { it.toString() }) { date ->
            val isSelected = date == selected
            val scheme = MaterialTheme.colorScheme
            val container by animateColorAsState(if (isSelected) scheme.primary else scheme.surfaceContainerHigh)
            val content = if (isSelected) scheme.onPrimary else scheme.onSurface
            Column(
                modifier = Modifier
                    .size(width = 60.dp, height = 76.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(date) })
                    .semantics { contentDescription = date.formatSpoken() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (date == dates.first()) "TODAY" else date.weekdayShort().uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = content,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** A tick drawn in code; avoids shipping an icon pack for one glyph. */
@Composable
fun CheckMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.width * 0.13f
        val knee = Offset(size.width * 0.42f, size.height * 0.72f)
        drawLine(color, Offset(size.width * 0.18f, size.height * 0.5f), knee, stroke, StrokeCap.Round)
        drawLine(color, knee, Offset(size.width * 0.84f, size.height * 0.28f), stroke, StrokeCap.Round)
    }
}
