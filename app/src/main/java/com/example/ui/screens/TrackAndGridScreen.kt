package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.data.TrackCatalog
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GridCarEntry
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.TelemetrySourceMode
import com.example.ui.components.MetricPill
import com.example.ui.components.PitWallCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackMapCanvas
import com.example.ui.components.TyreCompoundBadge
import com.example.ui.components.formatGapMs
import com.example.ui.components.formatLapTimeMs
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.AsphaltElevated
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TrackAndGridScreen(
    state: LiveTelemetryState,
    onSelectSimTrack: (Int) -> Unit,
    onSelectGridCar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = TrackCatalog.byId(state.sessionMeta.trackId)
    val meta = state.sessionMeta

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Track Map & Conditions
        item {
            PitWallCard {
                SectionHeader(
                    title = meta.trackName,
                    subtitle = "${meta.sessionType} • Volta ${meta.currentLap}/${meta.totalLaps} • ${track.lengthMeters}m",
                    accentColor = TelemetryCyan,
                )

                if (state.sourceMode == TelemetrySourceMode.SIMULATOR) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TrackCatalog.tracks.forEach { t ->
                            val selected = t.trackId == state.activeSimulatorTrackId
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectSimTrack(t.trackId) },
                                label = { Text(t.name.substringBefore(" (")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = F1Red,
                                    selectedLabelColor = TextPrimary,
                                ),
                                modifier = Modifier.testTag("track_chip_${t.trackId}"),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TrackMapCanvas(
                    track = track,
                    gridCars = state.gridEntries,
                    selectedCarIndex = state.selectedGridCarIndex,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricPill(
                        label = "CLIMA",
                        value = meta.weather,
                        accentColor = TelemetryCyan,
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = "PISTA / AR",
                        value = "${meta.trackTempC}° / ${meta.airTempC}°C",
                        accentColor = SectorYellow,
                        modifier = Modifier.weight(1f),
                    )
                    val flagColor = when (meta.flagStatus) {
                        FlagStatus.GREEN -> SectorGreen
                        FlagStatus.YELLOW, FlagStatus.DOUBLE_YELLOW, FlagStatus.VSC, FlagStatus.SAFETY_CAR -> SectorYellow
                        FlagStatus.RED -> F1Red
                    }
                    MetricPill(
                        label = "STATUS",
                        value = meta.flagStatus.label,
                        accentColor = flagColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = meta.forecastSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        // Strategic Pit Window Card
        state.pitWindow?.let { pit ->
            item {
                val border = if (pit.isClearAirRejoin) SectorGreen.copy(alpha = 0.65f) else SectorYellow.copy(alpha = 0.7f)
                PitWallCard(borderColor = border) {
                    SectionHeader(
                        title = "Estratégia & Janela de Pit Stop",
                        subtitle = "Projeção de saída considerando perda nos boxes e tráfego do grid",
                        accentColor = if (pit.isClearAirRejoin) SectorGreen else SectorYellow,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            label = "JANELA IDEAL",
                            value = pit.recommendedLapRange,
                            accentColor = TelemetryCyan,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "RETORNO EST.",
                            value = "P${pit.estimatedRejoinPosition}",
                            accentColor = if (pit.isClearAirRejoin) SectorGreen else SectorYellow,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "GANHO UNDERCUT",
                            value = "-%.1fs/v".format(pit.undercutGainSec),
                            accentColor = SectorGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pit.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }
        }

        // Grid Classification Header
        item {
            SectionHeader(
                title = "Classificação do Grid ao Vivo",
                subtitle = "Toque em um piloto para destacá-lo no mapa da pista",
                accentColor = F1Red,
            )
        }

        if (state.gridEntries.isEmpty()) {
            item {
                PitWallCard {
                    Text(
                        text = "Nenhum dado de classificação recebido ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
            }
        } else {
            items(state.gridEntries, key = { it.carIndex }) { car ->
                GridRowItem(
                    car = car,
                    isSelected = car.carIndex == state.selectedGridCarIndex,
                    onClick = { onSelectGridCar(car.carIndex) },
                )
            }
        }
    }
}

@Composable
private fun GridRowItem(
    car: GridCarEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when {
        car.isPlayer -> F1Red.copy(alpha = 0.14f)
        isSelected -> SectorYellow.copy(alpha = 0.12f)
        else -> AsphaltElevated.copy(alpha = 0.65f)
    }
    val borderColor = when {
        car.isPlayer -> F1Red
        isSelected -> SectorYellow
        else -> AsphaltBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("grid_row_${car.driverCode}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Position
        Text(
            text = "${car.position}".padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = TelemetryMonoFamily,
            color = if (car.isPlayer) F1Red else TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )

        // Team Color Stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(car.teamColorHex))
        )
        Spacer(modifier = Modifier.width(10.dp))

        // Driver Code + Team + Penalties
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = car.driverCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = car.teamName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                )
                if (car.penaltiesSec > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SectorYellow.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "+${car.penaltiesSec}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = SectorYellow,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = "Melhor: ${formatLapTimeMs(car.bestLapMs)}  •  Boxes: ${car.pitStops}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFamily,
                color = TextMuted,
            )
        }

        // Tyre Badge
        TyreCompoundBadge(
            compound = car.tyreCompound,
            ageLaps = car.tyreAgeLaps,
        )
        Spacer(modifier = Modifier.width(10.dp))

        // Gap
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(74.dp)) {
            Text(
                text = formatGapMs(car.gapToLeaderMs, isLeader = car.position == 1),
                style = MaterialTheme.typography.labelMedium,
                color = if (car.position == 1) TelemetryCyan else TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            if (car.position > 1) {
                Text(
                    text = "Int ${formatGapMs(car.intervalMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFamily,
                    color = TextSecondary,
                )
            }
        }
    }
}
