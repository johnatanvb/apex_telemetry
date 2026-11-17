package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.TelemetryAlert
import com.example.telemetry.model.TelemetrySourceMode
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun LiveAlertTickerBanner(
    alerts: List<TelemetryAlert>,
    onOpenAlertCenter: () -> Unit,
    onAcknowledge: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAlert = alerts.firstOrNull { !it.acknowledged } ?: return
    val accent = when (topAlert.severity) {
        AlertSeverity.CRITICAL -> F1Red
        AlertSeverity.WARNING -> SectorYellow
        AlertSeverity.INFO -> TelemetryCyan
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenAlertCenter)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("live_alert_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = topAlert.severity.label,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${topAlert.title} (${topAlert.metricBadge})",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = topAlert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
            )
        }
        IconButton(
            onClick = { onAcknowledge(topAlert.id) },
            modifier = Modifier.testTag("dismiss_banner_alert"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dispensar alerta",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun AlertsCenterDialog(
    alerts: List<TelemetryAlert>,
    thresholds: AlertThresholds,
    stressTestEnabled: Boolean,
    isSimulatorMode: Boolean,
    onToggleStressTest: (Boolean) -> Unit,
    onAcknowledge: (String) -> Unit,
    onSaveThresholds: (AlertThresholds) -> Unit,
    onDismiss: () -> Unit,
) {
    var engineWarn by remember(thresholds) { mutableFloatStateOf(thresholds.engineTempWarnC.toFloat()) }
    var engineCrit by remember(thresholds) { mutableFloatStateOf(thresholds.engineTempCritC.toFloat()) }
    var tyreWearWarn by remember(thresholds) { mutableFloatStateOf(thresholds.tyreWearWarnPct.toFloat()) }
    var tyreWearCrit by remember(thresholds) { mutableFloatStateOf(thresholds.tyreWearCritPct.toFloat()) }
    var brakeWarn by remember(thresholds) { mutableFloatStateOf(thresholds.brakeTempWarnC.toFloat()) }
    var pitLoss by remember(thresholds) { mutableFloatStateOf(thresholds.pitLossSeconds) }
    var strategyEnabled by remember(thresholds) { mutableStateOf(thresholds.enableStrategyAlerts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AsphaltSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = F1Red)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Alertas & Limites Críticos", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isSimulatorMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarbonBlack)
                            .border(1.dp, AsphaltBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simular Sobrecarga & VSC",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = "Eleva motor/pneus e ativa janela estratégica",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                        Switch(
                            checked = stressTestEnabled,
                            onCheckedChange = onToggleStressTest,
                            colors = SwitchDefaults.colors(checkedTrackColor = F1Red),
                            modifier = Modifier.testTag("stress_test_switch"),
                        )
                    }
                }

                Text(
                    text = "ALERTAS ATIVOS (${alerts.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan,
                    fontWeight = FontWeight.Bold,
                )

                if (alerts.isEmpty()) {
                    Text(
                        text = "Nenhum limite ultrapassado no momento. Carro operando dentro da janela ideal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SectorGreen,
                    )
                } else {
                    alerts.forEach { alert ->
                        val color = when (alert.severity) {
                            AlertSeverity.CRITICAL -> F1Red
                            AlertSeverity.WARNING -> SectorYellow
                            AlertSeverity.INFO -> TelemetryCyan
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CarbonBlack)
                                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${alert.category.label} • ${alert.metricBadge}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (!alert.acknowledged) {
                                    TextButton(onClick = { onAcknowledge(alert.id) }) {
                                        Icon(
                                            Icons.Rounded.CheckCircleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("OK", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text(alert.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CONFIGURAR LIMITES DE DISPARO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan,
                    fontWeight = FontWeight.Bold,
                )

                ThresholdSliderRow(
                    label = "Motor Aviso",
                    valueText = "${engineWarn.roundToInt()}°C",
                    value = engineWarn,
                    range = 100f..116f,
                    onValueChange = {
                        engineWarn = it
                        if (engineCrit <= it + 2f) engineCrit = (it + 4f).coerceAtMost(125f)
                    },
                )
                ThresholdSliderRow(
                    label = "Motor Crítico",
                    valueText = "${engineCrit.roundToInt()}°C",
                    value = engineCrit,
                    range = 110f..125f,
                    onValueChange = { engineCrit = it },
                )
                ThresholdSliderRow(
                    label = "Desgaste Pneu Aviso",
                    valueText = "${tyreWearWarn.roundToInt()}%",
                    value = tyreWearWarn,
                    range = 35f..70f,
                    onValueChange = {
                        tyreWearWarn = it
                        if (tyreWearCrit <= it + 5f) tyreWearCrit = (it + 10f).coerceAtMost(90f)
                    },
                )
                ThresholdSliderRow(
                    label = "Desgaste Pneu Crítico",
                    valueText = "${tyreWearCrit.roundToInt()}%",
                    value = tyreWearCrit,
                    range = 60f..90f,
                    onValueChange = { tyreWearCrit = it },
                )
                ThresholdSliderRow(
                    label = "Freios Superaquecidos",
                    valueText = "${brakeWarn.roundToInt()}°C",
                    value = brakeWarn,
                    range = 800f..1050f,
                    onValueChange = { brakeWarn = it },
                )
                ThresholdSliderRow(
                    label = "Perda nos Boxes (Pit Loss)",
                    valueText = "%.1fs".format(pitLoss),
                    value = pitLoss,
                    range = 17f..28f,
                    onValueChange = { pitLoss = it },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Alertas de Estratégia & Tráfego",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Switch(
                        checked = strategyEnabled,
                        onCheckedChange = { strategyEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveThresholds(
                        thresholds.copy(
                            engineTempWarnC = engineWarn.roundToInt(),
                            engineTempCritC = engineCrit.roundToInt(),
                            tyreWearWarnPct = tyreWearWarn.roundToInt(),
                            tyreWearCritPct = tyreWearCrit.roundToInt(),
                            brakeTempWarnC = brakeWarn.roundToInt(),
                            pitLossSeconds = pitLoss,
                            enableStrategyAlerts = strategyEnabled,
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = F1Red),
                modifier = Modifier.testTag("save_thresholds_button"),
            ) {
                Text("Aplicar Limites")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
    )
}

@Composable
private fun ThresholdSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = TelemetryMonoFamily,
                color = TelemetryCyan,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = F1Red,
                activeTrackColor = F1Red,
            ),
        )
    }
}

@Composable
fun UdpSettingsDialog(
    currentMode: TelemetrySourceMode,
    currentPort: Int,
    localIp: String,
    packetCount: Long,
    onSwitchMode: (TelemetrySourceMode) -> Unit,
    onSavePort: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var portText by remember(currentPort) { mutableStateOf(currentPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AsphaltSurface,
        title = {
            Text("Fonte de Telemetria F1 25", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelemetrySourceMode.entries.forEach { mode ->
                        val selected = mode == currentMode
                        Button(
                            onClick = { onSwitchMode(mode) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) F1Red else CarbonBlack,
                                contentColor = TextPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("source_mode_${mode.name}"),
                        ) {
                            Text(mode.label, maxLines = 1)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CarbonBlack)
                        .border(1.dp, AsphaltBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Text("IP Local (Wi-Fi): $localIp", style = MaterialTheme.typography.labelMedium, color = TelemetryCyan)
                    Text("Pacotes recebidos: $packetCount", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure no F1 25: Formato UDP 2025, IP $localIp e a porta abaixo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }

                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        portText = input.filter { it.isDigit() }.take(5)
                    },
                    label = { Text("Porta UDP (padrão 20777)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("udp_port_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = portText.toIntOrNull() ?: 20777
                    onSavePort(parsed.coerceIn(1024, 65535))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = F1Red),
                modifier = Modifier.testTag("save_udp_port_button"),
            ) {
                Text("Salvar Porta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
    )
}
