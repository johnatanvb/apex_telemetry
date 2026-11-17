package com.example.telemetry.analysis

import com.example.telemetry.model.AlertCategory
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.PitWindowPrediction
import com.example.telemetry.model.TelemetryAlert
import kotlin.math.abs
import kotlin.math.roundToInt

class AlertEngine {

    data class AlertAnalysisResult(
        val alerts: List<TelemetryAlert>,
        val pitWindow: PitWindowPrediction?,
    )

    fun evaluate(
        state: LiveTelemetryState,
        thresholds: AlertThresholds,
        acknowledgedIds: Set<String>,
    ): AlertAnalysisResult {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<TelemetryAlert>()
        val cond = state.carCondition

        // 1. Engine temperature threshold alerts
        if (cond.engineTempC >= thresholds.engineTempCritC) {
            alerts.add(
                TelemetryAlert(
                    id = "engine_temp_crit",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Superaquecimento Crítico do Motor",
                    message = "Temperatura do ICE em ${cond.engineTempC}°C (limite ${thresholds.engineTempCritC}°C). Reduza mistura ou busque ar limpo fora do vácuo.",
                    metricBadge = "${cond.engineTempC}°C",
                    timestampMs = now,
                    acknowledged = "engine_temp_crit" in acknowledgedIds,
                )
            )
        } else if (cond.engineTempC >= thresholds.engineTempWarnC) {
            alerts.add(
                TelemetryAlert(
                    id = "engine_temp_warn",
                    severity = AlertSeverity.WARNING,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Temperatura do Motor Elevada",
                    message = "Motor atingiu ${cond.engineTempC}°C (aviso ${thresholds.engineTempWarnC}°C). Evite trocas curtas prolongadas no modo Quente.",
                    metricBadge = "${cond.engineTempC}°C",
                    timestampMs = now,
                    acknowledged = "engine_temp_warn" in acknowledgedIds,
                )
            )
        }

        // 2. Tyre wear alerts
        val maxWearWheel = cond.wheels.maxByOrNull { it.wearPct }
        val maxWear = maxWearWheel?.wearPct?.roundToInt() ?: 0
        if (maxWear >= thresholds.tyreWearCritPct) {
            alerts.add(
                TelemetryAlert(
                    id = "tyre_wear_crit",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Desgaste Crítico de Pneus (${maxWearWheel?.label ?: "DD"})",
                    message = "Pneu ${maxWearWheel?.label} em $maxWear% (limite ${thresholds.tyreWearCritPct}%). Risco de perda severa de aderência e furo.",
                    metricBadge = "$maxWear%",
                    timestampMs = now,
                    acknowledged = "tyre_wear_crit" in acknowledgedIds,
                )
            )
        } else if (maxWear >= thresholds.tyreWearWarnPct) {
            alerts.add(
                TelemetryAlert(
                    id = "tyre_wear_warn",
                    severity = AlertSeverity.WARNING,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Desgaste Elevado (${maxWearWheel?.label ?: "DD"})",
                    message = "Desgaste atingiu $maxWear% (aviso ${thresholds.tyreWearWarnPct}%). Prepare estratégia de troca.",
                    metricBadge = "$maxWear%",
                    timestampMs = now,
                    acknowledged = "tyre_wear_warn" in acknowledgedIds,
                )
            )
        }

        // 3. Tyre thermal / blisters alert
        val hottestWheel = cond.wheels.maxByOrNull { it.tempSurfaceC }
        if (hottestWheel != null && hottestWheel.tempSurfaceC >= thresholds.tyreTempWarnC) {
            alerts.add(
                TelemetryAlert(
                    id = "tyre_temp_warn",
                    severity = AlertSeverity.WARNING,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Superaquecimento Térmico (${hottestWheel.label})",
                    message = "Superfície a ${hottestWheel.tempSurfaceC}°C com ${hottestWheel.blistersPct}% de bolhas. Reduza esterçamento em curvas longas.",
                    metricBadge = "${hottestWheel.tempSurfaceC}°C",
                    timestampMs = now,
                    acknowledged = "tyre_temp_warn" in acknowledgedIds,
                )
            )
        }

        // 4. Brake temperature alert
        val hottestBrake = cond.wheels.maxByOrNull { it.brakeTempC }
        if (hottestBrake != null && hottestBrake.brakeTempC >= thresholds.brakeTempWarnC) {
            alerts.add(
                TelemetryAlert(
                    id = "brake_temp_warn",
                    severity = AlertSeverity.WARNING,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Freios Acima da Janela (${hottestBrake.label})",
                    message = "Disco em ${hottestBrake.brakeTempC}°C (limite ${thresholds.brakeTempWarnC}°C). Ajuste o balanço de freio ou antecipe freada.",
                    metricBadge = "${hottestBrake.brakeTempC}°C",
                    timestampMs = now,
                    acknowledged = "brake_temp_warn" in acknowledgedIds,
                )
            )
        }

        // 5. Fuel alert
        if (cond.fuelRemainingLaps <= thresholds.fuelWarnLaps) {
            alerts.add(
                TelemetryAlert(
                    id = "fuel_low",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Margem Crítica de Combustível",
                    message = "Restam %.1f voltas estimadas (${String.format("%.1f", cond.fuelInTankKg)} kg). Faça lift-and-coast nas retas.".format(cond.fuelRemainingLaps),
                    metricBadge = "%.1f V".format(cond.fuelRemainingLaps),
                    timestampMs = now,
                    acknowledged = "fuel_low" in acknowledgedIds,
                )
            )
        }

        // 6. Strategic Pit Stop & Traffic Window Analysis
        val pitWindow = computePitWindow(state, thresholds)
        if (thresholds.enableStrategyAlerts && pitWindow != null) {
            val isSafetyCar = state.sessionMeta.flagStatus == FlagStatus.VSC ||
                state.sessionMeta.flagStatus == FlagStatus.SAFETY_CAR

            if (isSafetyCar && maxWear >= 22) {
                alerts.add(
                    TelemetryAlert(
                        id = "strategy_sc_pit",
                        severity = AlertSeverity.CRITICAL,
                        category = AlertCategory.STRATEGY,
                        title = "BOX AGORA — Pit Barato (${state.sessionMeta.flagStatus.label})",
                        message = "Economia de ~8.5s na parada! Retorno projetado em P${pitWindow.estimatedRejoinPosition} (${if (pitWindow.isClearAirRejoin) "ar limpo" else "atrás de ${pitWindow.trafficAheadDriver}"}).",
                        metricBadge = "BOX P${pitWindow.estimatedRejoinPosition}",
                        timestampMs = now,
                        acknowledged = "strategy_sc_pit" in acknowledgedIds,
                    )
                )
            } else if (maxWear >= 48 && pitWindow.isClearAirRejoin) {
                alerts.add(
                    TelemetryAlert(
                        id = "strategy_undercut_clear",
                        severity = AlertSeverity.INFO,
                        category = AlertCategory.STRATEGY,
                        title = "Oportunidade de Undercut em Ar Limpo",
                        message = "Janela ideal (${pitWindow.recommendedLapRange}): retorno em P${pitWindow.estimatedRejoinPosition} com +${"%.1f".format(pitWindow.trafficGapSec)}s livre e ganho estimado de ${"%.1f".format(pitWindow.undercutGainSec)}s/volta.",
                        metricBadge = "UNDERCUT",
                        timestampMs = now,
                        acknowledged = "strategy_undercut_clear" in acknowledgedIds,
                    )
                )
            } else if (maxWear >= 50 && !pitWindow.isClearAirRejoin) {
                alerts.add(
                    TelemetryAlert(
                        id = "strategy_traffic_warn",
                        severity = AlertSeverity.WARNING,
                        category = AlertCategory.STRATEGY,
                        title = "Risco de Tráfego na Saída dos Boxes",
                        message = "Parar agora recoloca o carro em P${pitWindow.estimatedRejoinPosition} a ${"%.1f".format(pitWindow.trafficGapSec)}s de ${pitWindow.trafficAheadDriver}. Estenda mais 1–2 voltas.",
                        metricBadge = "TRÁFEGO",
                        timestampMs = now,
                        acknowledged = "strategy_traffic_warn" in acknowledgedIds,
                    )
                )
            }
        }

        return AlertAnalysisResult(
            alerts = alerts.sortedByDescending { it.severity.ordinal },
            pitWindow = pitWindow,
        )
    }

    private fun computePitWindow(
        state: LiveTelemetryState,
        thresholds: AlertThresholds,
    ): PitWindowPrediction? {
        val grid = state.gridEntries
        val player = grid.firstOrNull { it.isPlayer } ?: return null
        val pitLossMs = (thresholds.pitLossSeconds * 1000f).toLong()
        val projectedGapMs = player.gapToLeaderMs + pitLossMs

        val carsAheadAfterPit = grid.count { !it.isPlayer && it.gapToLeaderMs < projectedGapMs }
        val rejoinPos = (carsAheadAfterPit + 1).coerceIn(1, grid.size)

        val nearestTraffic = grid
            .filter { !it.isPlayer }
            .minByOrNull { abs(it.gapToLeaderMs - projectedGapMs) }

        val trafficGapSec = if (nearestTraffic != null) {
            abs(nearestTraffic.gapToLeaderMs - projectedGapMs) / 1000f
        } else {
            5.0f
        }

        val maxWear = state.carCondition.wheels.maxOfOrNull { it.wearPct } ?: 0f
        val degradationLossSec = (maxWear / 100f) * 1.9f
        val lapDeltaTrendSec = if (state.completedLaps.size >= 2) {
            val last = state.completedLaps.last().lapTimeMs
            val best = state.completedLaps.minOf { it.lapTimeMs }
            ((last - best).coerceAtLeast(0L) / 1000f)
        } else {
            0.3f
        }
        val undercutGain = (degradationLossSec + lapDeltaTrendSec * 0.5f).coerceIn(0.2f, 2.8f)
        val clearAir = trafficGapSec >= 2.1f

        val curLap = state.sessionMeta.currentLap
        val startWin = (curLap + (if (maxWear > 55f) 0 else 2)).coerceAtMost(state.sessionMeta.totalLaps)
        val endWin = (startWin + 3).coerceAtMost(state.sessionMeta.totalLaps)

        val summary = if (clearAir) {
            "Saída projetada em P$rejoinPos com pista livre (${"%.1f".format(trafficGapSec)}s de margem)."
        } else {
            "Saída próxima a ${nearestTraffic?.driverCode ?: "tráfego"} (${"%.1f".format(trafficGapSec)}s)."
        }

        return PitWindowPrediction(
            recommendedLapRange = "V$startWin–V$endWin",
            estimatedRejoinPosition = rejoinPos,
            trafficAheadDriver = nearestTraffic?.driverCode ?: "---",
            trafficGapSec = trafficGapSec,
            undercutGainSec = undercutGain,
            isClearAirRejoin = clearAir,
            summary = summary,
        )
    }
}
