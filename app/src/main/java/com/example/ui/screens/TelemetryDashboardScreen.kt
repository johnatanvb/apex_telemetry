package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SettingsInputAntenna
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.data.GameCatalog
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.TelemetrySourceMode
import com.example.ui.components.GForceAndDeltaPanel
import com.example.ui.components.MetricPill
import com.example.ui.components.PitWallCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SectorTimingBar
import com.example.ui.components.SpeedAndPedalsCanvasChart
import com.example.ui.components.StandardGameRaceDashboard
import com.example.ui.components.formatLapTimeMs
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorPurple
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SaveRaceUiState

@Composable
fun TelemetryDashboardScreen(
    state: LiveTelemetryState,
    saveState: SaveRaceUiState,
    onSaveRaceClick: () -> Unit,
    onSwitchToSimulator: () -> Unit,
    isCockpitFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sample = state.currentSample
    val meta = state.sessionMeta

    if (isCockpitFullscreen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            StandardGameRaceDashboard(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("standard_game_hud_${state.detectedGame.name}"),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Primary Zero-Interaction Per-Game Standard Race Dashboard ALWAYS first above the fold
        item {
            StandardGameRaceDashboard(
                state = state,
                modifier = Modifier.testTag("standard_game_hud_${state.detectedGame.name}"),
            )
        }

        if (state.sourceMode == TelemetrySourceMode.UDP &&
            state.connectionState != ConnectionState.CONNECTED_UDP
        ) {
            item {
                PitWallCard(borderColor = SectorYellow.copy(alpha = 0.7f)) {
                    SectionHeader(
                        title = "Aguardando Pacotes (${state.selectedGameMode.displayName})",
                        subtitle = "Pronto para recepção em tempo real na rede Wi-Fi",
                        accentColor = SectorYellow,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "IP deste aparelho: ${state.localIpAddress}  •  Porta UDP: ${state.udpPort}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TelemetryCyan,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = GameCatalog.setupInstructionsFor(
                            game = state.selectedGameMode,
                            localIp = state.localIpAddress,
                            customPort = state.udpPort,
                        ),
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
                        Text("Ativar Simulador (${state.detectedGame.shortTag}) para Testar")
                    }
                }
            }
        }

        // Post-Stint / Secondary Detailed Telemetry (Available below the fold)
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
                            text = "${state.detectedGame.displayName} • ${state.completedLaps.size} voltas registradas",
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
