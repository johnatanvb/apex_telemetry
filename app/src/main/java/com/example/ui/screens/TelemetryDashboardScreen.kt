package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.TelemetrySourceMode
import com.example.ui.components.GForceAndDeltaPanel
import com.example.ui.components.MetricPill
import com.example.ui.components.PitWallCard
import com.example.ui.components.RevLightsBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SectorTimingBar
import com.example.ui.components.SpeedAndPedalsCanvasChart
import com.example.ui.components.formatDeltaMs
import com.example.ui.components.formatLapTimeMs
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorPurple
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SaveRaceUiState
import kotlin.math.roundToInt

@Composable
fun TelemetryDashboardScreen(
    state: LiveTelemetryState,
    saveState: SaveRaceUiState,
    onSaveRaceClick: () -> Unit,
    onSwitchToSimulator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sample = state.currentSample
    val meta = state.sessionMeta

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.sourceMode == TelemetrySourceMode.UDP &&
            state.connectionState != ConnectionState.CONNECTED_UDP
        ) {
            item {
                PitWallCard(borderColor = SectorYellow.copy(alpha = 0.7f)) {
                    SectionHeader(
                        title = "Aguardando Pacotes F1 25 (UDP)",
                        subtitle = "Configure a telemetria do jogo na rede local Wi-Fi",
                        accentColor = SectorYellow,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "IP deste aparelho: ${state.localIpAddress}  •  Porta UDP: ${state.udpPort}  •  Formato: 2025",
                        style = MaterialTheme.typography.labelMedium,
                        color = TelemetryCyan,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No jogo F1 25: Configurações > Telemetria > Telemetria UDP Ligada, Formato 2025, IP ${state.localIpAddress} e Porta ${state.udpPort}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSwitchToSimulator,
                        modifier = Modifier.testTag("fallback_simulator_button"),
                    ) {
                        Icon(Icons.Rounded.SettingsInputAntenna, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ativar Modo Simulador para Testar")
                    }
                }
            }
        }

        // Primary Cockpit & RPM Card
        item {
            PitWallCard {
                RevLightsBar(revLightsPct = sample.revLightsPct)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Speed + RPM
                    Column {
                        Text(
                            text = "VELOCIDADE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${sample.speedKmh}",
                                style = MaterialTheme.typography.displayLarge,
                                color = TelemetryCyan,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "km/h",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Text(
                            text = "${sample.engineRpm} RPM",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                    }

                    // Gear Display Box
                    val gearLabel = when (sample.gear) {
                        -1 -> "R"
                        0 -> "N"
                        else -> "${sample.gear}"
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 82.dp, height = 88.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CarbonBlack)
                            .border(2.dp, F1Red, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MARCHA",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Text(
                                text = gearLabel,
                                style = MaterialTheme.typography.displayLarge,
                                color = TextPrimary,
                            )
                        }
                    }

                    // DRS + Delta
                    Column(horizontalAlignment = Alignment.End) {
                        val drsBg = when {
                            sample.drsActive -> SectorGreen
                            sample.drsAvailable -> SectorYellow
                            else -> CarbonBlack
                        }
                        val drsText = when {
                            sample.drsActive -> "DRS ATIVO"
                            sample.drsAvailable -> "DRS DISP."
                            else -> "DRS OFF"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(drsBg.copy(alpha = if (sample.drsActive || sample.drsAvailable) 0.22f else 1f))
                                .border(1.dp, if (sample.drsActive) SectorGreen else AsphaltBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = drsText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sample.drsActive) SectorGreen else if (sample.drsAvailable) SectorYellow else TextMuted,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "DELTA VOLTA",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Text(
                            text = formatDeltaMs(sample.deltaMs),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = TelemetryMonoFamily,
                            color = if (sample.deltaMs <= 0) SectorGreen else F1Red,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pedals & Steering Bars
                PedalProgressBar(
                    label = "ACELERADOR",
                    fraction = sample.throttle,
                    barColor = SectorGreen,
                    valueText = "${(sample.throttle * 100).roundToInt()}%",
                )
                Spacer(modifier = Modifier.height(8.dp))
                PedalProgressBar(
                    label = "FREIO",
                    fraction = sample.brake,
                    barColor = F1Red,
                    valueText = "${(sample.brake * 100).roundToInt()}%",
                )
                Spacer(modifier = Modifier.height(8.dp))
                SteeringBar(steer = sample.steer)
            }
        }

        // Lap Times & Sectors
        item {
            PitWallCard {
                SectionHeader(
                    title = "Tempos & Setores",
                    subtitle = "Volta ${meta.currentLap}/${meta.totalLaps} • ${meta.trackName}",
                    accentColor = SectorPurple,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricPill(
                        label = "ATUAL",
                        value = formatLapTimeMs(meta.playerCurrentLapMs),
                        accentColor = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = "MELHOR PESSOAL",
                        value = formatLapTimeMs(meta.playerBestLapMs),
                        accentColor = SectorGreen,
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = "RECORDE PISTA",
                        value = formatLapTimeMs(meta.sessionBestLapMs),
                        accentColor = SectorPurple,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                SectorTimingBar(
                    s1Ms = meta.sector1Ms,
                    s2Ms = meta.sector2Ms,
                    s3Ms = meta.sector3Ms,
                    s1Status = meta.sector1Status,
                    s2Status = meta.sector2Status,
                    s3Status = meta.sector3Status,
                )
            }
        }

        // Detailed Speed & Pedals Chart
        item {
            PitWallCard {
                SectionHeader(
                    title = "Gráfico de Pilotagem em Tempo Real",
                    subtitle = "Curvas sincronizadas de velocidade, acelerador e frenagem",
                    accentColor = TelemetryCyan,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SpeedAndPedalsCanvasChart(samples = state.traceBuffer)
            }
        }

        // G-Force Circle & Delta Trace
        item {
            PitWallCard {
                SectionHeader(
                    title = "Força G & Evolução de Delta",
                    subtitle = "Círculo de aderência lateral/longitudinal e ganho de tempo",
                    accentColor = F1Red,
                )
                Spacer(modifier = Modifier.height(10.dp))
                GForceAndDeltaPanel(
                    sample = sample,
                    samples = state.traceBuffer,
                )
            }
        }

        // Cloud Save Action Card
        item {
            PitWallCard(borderColor = TelemetryCyan.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GRAVAR CORRIDA NA NUVEM",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${state.completedLaps.size} voltas registradas • Sincronização Firestore",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onSaveRaceClick,
                        enabled = saveState !is SaveRaceUiState.Saving,
                        colors = ButtonDefaults.buttonColors(containerColor = F1Red),
                        modifier = Modifier.testTag("save_race_cloud_button"),
                    ) {
                        if (saveState is SaveRaceUiState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PedalProgressBar(
    label: String,
    fraction: Float,
    barColor: Color,
    valueText: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = barColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(CarbonBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun SteeringBar(steer: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("ESTERÇAMENTO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = "${(steer * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = TelemetryCyan,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(CarbonBlack)
        ) {
            val center = 0.5f
            val offsetRatio = (steer.coerceIn(-1f, 1f) * 0.45f)
            val leftWeight = (center + offsetRatio).coerceIn(0.05f, 0.90f)
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(leftWeight))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(TelemetryCyan)
                )
                Spacer(modifier = Modifier.weight((1f - leftWeight).coerceAtLeast(0.05f)))
            }
        }
    }
}
