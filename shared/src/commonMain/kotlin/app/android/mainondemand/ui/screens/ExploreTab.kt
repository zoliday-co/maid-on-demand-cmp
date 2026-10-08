package app.android.mainondemand.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.domain.model.SearchResult
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.presentation.Load
import app.android.mainondemand.presentation.SearchUiState
import app.android.mainondemand.presentation.SearchViewModel
import app.android.mainondemand.ui.components.AppCard
import app.android.mainondemand.ui.components.Avatar
import app.android.mainondemand.ui.components.DateStrip
import app.android.mainondemand.ui.components.ErrorState
import app.android.mainondemand.ui.components.MessageState
import app.android.mainondemand.ui.components.PillChip
import app.android.mainondemand.ui.components.RatingText
import app.android.mainondemand.ui.components.ScreenPadding
import app.android.mainondemand.ui.components.SectionLabel
import app.android.mainondemand.ui.components.SkeletonList
import app.android.mainondemand.ui.formatHour
import app.android.mainondemand.ui.formatIstTime
import app.android.mainondemand.ui.formatShort
import app.android.mainondemand.ui.theme.extraColors

private val RatingFilters = listOf(4.0, 4.5)
private val PriceFilters = listOf(200, 300)

@Composable
fun ExploreTab(
    state: SearchUiState,
    viewModel: SearchViewModel,
    faultsArmed: Boolean,
    onOpenLab: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    contentPadding: PaddingValues,
) {
    val criteria = state.criteria
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") { Header(faultsArmed, onOpenLab) }
        item(key = "hero") { Hero() }

        item(key = "where") {
            SectionLabel("Where", Modifier.padding(start = ScreenPadding, top = 24.dp, bottom = 10.dp))
            ChipRow {
                state.localities.forEach { locality ->
                    PillChip(
                        text = locality.name,
                        selected = locality.id == criteria.localityId,
                        onClick = { viewModel.onLocalitySelected(locality.id) },
                    )
                }
            }
        }

        item(key = "what") {
            SectionLabel("Service", Modifier.padding(start = ScreenPadding, top = 24.dp, bottom = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ServiceType.entries.forEach { service ->
                    ServiceTile(
                        service = service,
                        selected = service == criteria.service,
                        onClick = { viewModel.onServiceSelected(service) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "when") {
            SectionLabel("When · India time (IST)", Modifier.padding(start = ScreenPadding, top = 24.dp, bottom = 10.dp))
            DateStrip(state.dates, criteria.date, viewModel::onDateSelected)
            Spacer(Modifier.height(10.dp))
            ChipRow {
                PillChip("Any time", criteria.startHour == null, { viewModel.onHourSelected(null) })
                state.hours.forEach { hour ->
                    PillChip(formatHour(hour), criteria.startHour == hour, { viewModel.onHourSelected(hour) })
                }
            }
        }

        item(key = "filters") {
            SectionLabel("Filters", Modifier.padding(start = ScreenPadding, top = 24.dp, bottom = 10.dp))
            ChipRow {
                RatingFilters.forEach { rating ->
                    val selected = criteria.minRating == rating
                    PillChip(
                        text = "★ $rating+",
                        selected = selected,
                        onClick = { viewModel.onMinRatingSelected(if (selected) null else rating) },
                        role = Role.Checkbox,
                    )
                }
                PriceFilters.forEach { price ->
                    val selected = criteria.maxPrice == price
                    PillChip(
                        text = "Up to ${formatInr(price)}",
                        selected = selected,
                        onClick = { viewModel.onMaxPriceSelected(if (selected) null else price) },
                        role = Role.Checkbox,
                    )
                }
            }
        }

        item(key = "results-title") { ResultsTitle(state) }

        when (val results = state.results) {
            Load.Loading -> item(key = "loading") {
                SkeletonList(Modifier.padding(horizontal = ScreenPadding))
            }

            is Load.Failed -> item(key = "error") { ErrorState(results.message, viewModel::onRetry) }

            is Load.Ready -> if (results.value.isEmpty()) {
                item(key = "empty") {
                    val narrowed = state.hasFilters || criteria.startHour != null
                    MessageState(
                        emoji = "🔍",
                        title = "No one's free then",
                        message = if (narrowed) {
                            "Nobody matches every filter on ${criteria.date.formatShort()}. Loosen the filters or try another day."
                        } else {
                            "No ${criteria.service.label.lowercase()} pros are free on ${criteria.date.formatShort()}. Try another day or locality."
                        },
                        actionLabel = if (narrowed) "Clear filters" else "Search again",
                        onAction = if (narrowed) viewModel::onClearFilters else viewModel::onRetry,
                    )
                }
            } else {
                items(results.value, key = { it.professional.id }) { result ->
                    ResultCard(
                        result = result,
                        exactTime = criteria.startHour != null,
                        onClick = { onOpenResult(result) },
                        modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun Header(faultsArmed: Boolean, onOpenLab: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Namaste 👋",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Maid on Demand",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        // Developer control: arms the fake backend's failure scenarios.
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onOpenLab)
                .padding(horizontal = 16.dp)
                .semantics {
                    contentDescription = if (faultsArmed) "Failure lab, failures armed" else "Failure lab"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (faultsArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Lab",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun Hero() {
    val extras = MaterialTheme.extraColors
    Column(
        modifier = Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(extras.heroGradient))
            .padding(24.dp),
    ) {
        Text(
            text = "60-MIN VISITS",
            style = MaterialTheme.typography.labelSmall,
            color = extras.onAccent,
            modifier = Modifier.clip(CircleShape).background(extras.accent).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Home help,\nbooked in a minute.", style = MaterialTheme.typography.displaySmall, color = extras.onHero)
        Spacer(Modifier.height(8.dp))
        Text(
            "Trusted pros for cleaning, cooking and dishes — on your schedule.",
            style = MaterialTheme.typography.bodyMedium,
            color = extras.onHero.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun ServiceTile(service: ServiceType, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainer)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(service.emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.height(8.dp))
        Text(
            text = service.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResultsTitle(state: SearchUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val results = state.results
        Text(
            text = when (results) {
                is Load.Ready -> when (val count = results.value.size) {
                    0 -> "No professionals"
                    1 -> "1 professional available"
                    else -> "$count professionals available"
                }

                else -> "Available professionals"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (state.isRefreshing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ResultCard(result: SearchResult, exactTime: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pro = result.professional
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(pro.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pro.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pro.services.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    RatingText(pro.rating, pro.reviewCount)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatInr(result.price),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "per visit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (exactTime) "Free at " else "Next ") + result.matchedSlotStart.formatIstTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (!exactTime) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (result.openSlotCount == 1) "1 slot open" else "${result.openSlotCount} slots open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "View  →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}
