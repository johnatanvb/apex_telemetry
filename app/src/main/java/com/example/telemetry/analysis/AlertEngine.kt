package com.example.telemetry.analysis

import com.example.telemetry.model.AlertCategory
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.CarCategory
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
        val category = state.carCategory
        val caps = state.capabilities

        // 1. Engine / Oil-Water Temperature Threshold Alerts
        val engineLabel = if (caps.hasEngineOilWater) "Óleo/Motor" else "Motor"
        if (cond.engineTempC >= thresholds.engineTempCritC) {
            alerts.add(
                TelemetryAlert(
                    id = "engine_temp_crit",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Superaquecimento Crítico ($engineLabel)",
                    message = if (category == CarCategory.FORMULA) {
                        "ICE a ${cond.engineTempC}°C (limite ${thresholds.engineTempCritC}°C). Reduza mistura ou saia do vácuo."
                    } else {
                        "$engineLabel a ${cond.engineTempC}°C / Água ${cond.waterTempC}°C. Antecipe trocas e abra arrefecimento."
                    },
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
                    title = "Temperatura Elevada ($engineLabel)",
                    message = "$engineLabel atingiu ${cond.engineTempC}°C (aviso ${thresholds.engineTempWarnC}°C).",
                    metricBadge = "${cond.engineTempC}°C",
                    timestampMs = now,
                    acknowledged = "engine_temp_warn" in acknowledgedIds,
                )
            )
        }

        // 2. Tyre Wear Alerts (when reported by protocol/sim)
        val maxWearWheel = cond.wheels.maxByOrNull { it.wearPct }
        val maxWear = maxWearWheel?.wearPct?.roundToInt() ?: 0
        if (maxWear >= thresholds.tyreWearCritPct) {
            alerts.add(
                TelemetryAlert(
                    id = "tyre_wear_crit",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Desgaste Crítico de Pneus (${maxWearWheel?.label ?: "DD"})",
                    message = "Pneu ${maxWearWheel?.label} em $maxWear% (limite ${thresholds.tyreWearCritPct}%). Risco de perda severa de aderência.",
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
                    message = "Desgaste atingiu $maxWear% (aviso ${thresholds.tyreWearWarnPct}%). Ajuste ritmo nas freadas e saídas.",
                    metricBadge = "$maxWear%",
                    timestampMs = now,
                    acknowledged = "tyre_wear_warn" in acknowledgedIds,
                )
            )
        }

        // 3. Category-Adapted Tyre Temperature / GT3 Pressure Window
        val effectiveTyreWarn = when (category) {
            CarCategory.FORMULA -> thresholds.tyreTempWarnC
            CarCategory.GT_ENDURANCE -> (thresholds.tyreTempWarnC - 6).coerceAtLeast(92)
            CarCategory.STREET_TOURING -> (thresholds.tyreTempWarnC - 12).coerceAtLeast(86)
        }
        val hottestWheel = cond.wheels.maxByOrNull { it.tempSurfaceC }
        if (hottestWheel != null && hottestWheel.tempSurfaceC >= effectiveTyreWarn) {
            alerts.add(
                TelemetryAlert(
                    id = "tyre_temp_warn",
                    severity = AlertSeverity.WARNING,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Superaquecimento de Pneu (${hottestWheel.label})",
                    message = "Superfície a ${hottestWheel.tempSurfaceC}°C na categoria ${category.label}. Evite deslizamento excessivo.",
                    metricBadge = "${hottestWheel.tempSurfaceC}°C",
                    timestampMs = now,
                    acknowledged = "tyre_temp_warn" in acknowledgedIds,
                )
            )
        }

        if (category == CarCategory.GT_ENDURANCE) {
            val outOfWindow = cond.wheels.firstOrNull { it.pressurePsi < 25.8f || it.pressurePsi > 28.1f }
            if (outOfWindow != null) {
                alerts.add(
                    TelemetryAlert(
                        id = "gt3_psi_window",
                        severity = AlertSeverity.WARNING,
                        category = AlertCategory.CRITICAL_TELEMETRY,
                        title = "Pressão GT Fora da Janela (${outOfWindow.label})",
                        message = "Pressão em ${"%.1f".format(outOfWindow.pressurePsi)} PSI (ideal GT3: 26.5–27.5 PSI).",
                        metricBadge = "%.1f PSI".format(outOfWindow.pressurePsi),
                        timestampMs = now,
                        acknowledged = "gt3_psi_window" in acknowledgedIds,
                    )
                )
            }
        }

        // 4. Category-Adapted Brake Temperature Alert
        if (caps.hasBrakeTemps) {
            val brakeLimit = when (category) {
                CarCategory.FORMULA -> thresholds.brakeTempWarnC
                CarCategory.GT_ENDURANCE -> (thresholds.brakeTempWarnC - 180).coerceAtLeast(650)
                CarCategory.STREET_TOURING -> (thresholds.brakeTempWarnC - 260).coerceAtLeast(560)
            }
            val hottestBrake = cond.wheels.maxByOrNull { it.brakeTempC }
            if (hottestBrake != null && hottestBrake.brakeTempC >= brakeLimit) {
                alerts.add(
                    TelemetryAlert(
                        id = "brake_temp_warn",
                        severity = AlertSeverity.WARNING,
                        category = AlertCategory.CRITICAL_TELEMETRY,
                        title = "Freios Acima da Janela (${hottestBrake.label})",
                        message = "Disco em ${hottestBrake.brakeTempC}°C (limite ${brakeLimit}°C). Recue balanço de freio ou alivie freada.",
                        metricBadge = "${hottestBrake.brakeTempC}°C",
                        timestampMs = now,
                        acknowledged = "brake_temp_warn" in acknowledgedIds,
                    )
                )
            }
        }

        // 5. Slip / Traction Intervention Alert (GT & Street/Touring)
        if (caps.hasTireSlipRatio) {
            val maxSlip = cond.wheels.maxOfOrNull { abs(it.slipRatio) } ?: 0f
            if (maxSlip >= 0.24f) {
                alerts.add(
                    TelemetryAlert(
                        id = "high_wheel_slip",
                        severity = AlertSeverity.INFO,
                        category = AlertCategory.CRITICAL_TELEMETRY,
                        title = "Perda de Tração / Destracionamento",
                        message = "Deslizamento de ${(maxSlip * 100).roundToInt()}% detectado. Module acelerador ou eleve o TC.",
                        metricBadge = "${(maxSlip * 100).roundToInt()}% SLIP",
                        timestampMs = now,
                        acknowledged = "high_wheel_slip" in acknowledgedIds,
                    )
                )
            }
        }

        // 6. Fuel Alert
        if (cond.fuelRemainingLaps <= thresholds.fuelWarnLaps) {
            val unit = if (category == CarCategory.FORMULA) "kg" else "L"
            alerts.add(
                TelemetryAlert(
                    id = "fuel_low",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CRITICAL_TELEMETRY,
                    title = "Margem Crítica de Combustível",
                    message = "Restam %.1f voltas (${String.format("%.1f", cond.fuelInTankKg)} $unit). Faça economia nas retas.".format(cond.fuelRemainingLaps),
                    metricBadge = "%.1f V".format(cond.fuelRemainingLaps),
                    timestampMs = now,
                    acknowledged = "fuel_low" in acknowledgedIds,
                )
            )
        }

        // 7. Strategic Pit Stop & Traffic Window Analysis (only for games/categories with grid & pit support)
        val pitWindow = if (caps.hasPitStrategy && caps.hasOpponentGrid) {
            computePitWindow(state, thresholds)
        } else {
            null
        }
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
                        message = "Janela ideal (${pitWindow.recommendedLapRange}): retorno em P${pitWindow.estimatedRejoinPosition} com +${"%.1f".format(pitWindow.trafficGapSec)}s livre e ganho de ${"%.1f".format(pitWindow.undercutGainSec)}s/v.",
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
                        message = "Parar agora recoloca o carro em P${pitWindow.estimatedRejoinPosition} a ${"%.1f".format(pitWindow.trafficGapSec)}s de ${pitWindow.trafficAheadDriver}. Estenda 1–2 voltas.",
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
