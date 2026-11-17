package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SetupDiagnosticInsight
import com.example.ui.components.MetricPill
import com.example.ui.components.PitWallCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorPurple
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SetupAdvisorScreen(
    state: LiveTelemetryState,
    modifier: Modifier = Modifier,
) {
    val setup = state.setupRecommendation

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (setup == null) {
            item {
                PitWallCard {
                    Text(
                        text = "Coletando amostras de telemetria para calcular setup...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        } else {
            val track = setup.trackProfile
            // Track Characteristics Card
            item {
                PitWallCard {
                    SectionHeader(
                        title = "DNA da Pista: ${track.name}",
                        subtitle = "${track.country} • ${track.cornersCount} curvas • ${track.elevationChangeM}m de desnível",
                        accentColor = SectorPurple,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            label = "CARGA AERO",
                            value = track.downforceDemand,
                            accentColor = TelemetryCyan,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "RETAS",
                            value = "${track.straightRatioPct}%",
                            accentColor = SectorYellow,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "PNEUS",
                            value = track.tyreStressLevel.substringBefore(" "),
                            accentColor = F1Red,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = track.cornerProfileSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // Recommended Numeric Setup Sheet
            item {
                PitWallCard(borderColor = TelemetryCyan.copy(alpha = 0.55f)) {
                    SectionHeader(
                        title = "Setup Recomendado F1 25",
                        subtitle = "Recalculado com base na telemetria térmica e velocidade",
                        accentColor = TelemetryCyan,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SetupCategoryGrid(
                        title = "AERODINÂMICA",
                        items = listOf(
                            "Asa Dianteira" to "${setup.aeroFrontWing}",
                            "Asa Traseira" to "${setup.aeroRearWing}",
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SetupCategoryGrid(
                        title = "TRANSMISSÃO (DIFERENCIAL)",
                        items = listOf(
                            "Aceleração (On)" to "${setup.diffOnThrottle}%",
                            "Desaceleração (Off)" to "${setup.diffOffThrottle}%",
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SetupCategoryGrid(
                        title = "GEOMETRIA DA SUSPENSÃO",
                        items = listOf(
                            "Cambagem Diant." to "%.2f°".format(setup.frontCamber),
                            "Cambagem Tras." to "%.2f°".format(setup.rearCamber),
                            "Convergência Diant." to "%.2f°".format(setup.frontToe),
                            "Convergência Tras." to "%.2f°".format(setup.rearToe),
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SetupCategoryGrid(
                        title = "SUSPENSÃO & ALTURA",
                        items = listOf(
                            "Suspensão D / T" to "${setup.frontSuspension} / ${setup.rearSuspension}",
                            "Barra Estab. D / T" to "${setup.frontAntiRollBar} / ${setup.rearAntiRollBar}",
                            "Altura Solo D / T" to "${setup.frontRideHeight} / ${setup.rearRideHeight}",
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SetupCategoryGrid(
                        title = "FREIOS & PRESSÃO DOS PNEUS",
                        items = listOf(
                            "Pressão Freio" to "${setup.brakePressurePct}%",
                            "Balanço Freio" to "${setup.brakeBiasPct}%",
                            "PSI Dianteiro" to "%.1f".format(setup.frontPsi),
                            "PSI Traseiro" to "%.1f".format(setup.rearPsi),
                        ),
                    )
                }
            }

            // Telemetry-Backed Diagnostic Insights
            item {
                SectionHeader(
                    title = "Diagnóstico de Telemetria & Por Quê",
                    subtitle = "Justificativa técnica dos ajustes recomendados",
                    accentColor = F1Red,
                )
            }

            items(setup.insights, key = { it.area }) { insight ->
                SetupInsightCard(insight)
            }
        }
    }
}

@Composable
private fun SetupCategoryGrid(
    title: String,
    items: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonBlack)
            .border(1.dp, AsphaltBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryCyan,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        items.chunked(2).forEach { rowPairs ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPairs.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = TelemetryMonoFamily,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (rowPairs.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SetupInsightCard(insight: SetupDiagnosticInsight) {
    val badgeColor = when (insight.priority) {
        AlertSeverity.CRITICAL -> F1Red
        AlertSeverity.WARNING -> SectorYellow
        AlertSeverity.INFO -> SectorGreen
    }
    PitWallCard(borderColor = badgeColor.copy(alpha = 0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = insight.area,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = insight.priority.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = insight.observation,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Recomendação: ${insight.adjustment}",
            style = MaterialTheme.typography.bodyMedium,
            color = TelemetryCyan,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
