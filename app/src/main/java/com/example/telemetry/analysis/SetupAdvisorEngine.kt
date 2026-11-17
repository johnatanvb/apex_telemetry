package com.example.telemetry.analysis

import com.example.telemetry.data.TrackCatalog
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.CarCategory
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SetupDiagnosticInsight
import com.example.telemetry.model.SetupRecommendation
import kotlin.math.abs
import kotlin.math.roundToInt

class SetupAdvisorEngine {

    fun analyzeAndRecommend(state: LiveTelemetryState): SetupRecommendation {
        return when (state.carCategory) {
            CarCategory.FORMULA -> buildFormulaSetup(state)
            CarCategory.GT_ENDURANCE -> buildGtEnduranceSetup(state)
            CarCategory.STREET_TOURING -> buildStreetTouringSetup(state)
        }
    }

    private fun buildFormulaSetup(state: LiveTelemetryState): SetupRecommendation {
        val track = TrackCatalog.byId(state.sessionMeta.trackId)
        val cond = state.carCondition
        val traces = state.traceBuffer
        val laps = state.completedLaps

        val avgFrontSurface = (cond.wheels[2].tempSurfaceC + cond.wheels[3].tempSurfaceC) / 2f
        val avgRearSurface = (cond.wheels[0].tempSurfaceC + cond.wheels[1].tempSurfaceC) / 2f
        val avgFrontInner = (cond.wheels[2].tempInnerC + cond.wheels[3].tempInnerC) / 2f
        val avgRearInner = (cond.wheels[0].tempInnerC + cond.wheels[1].tempInnerC) / 2f

        val frontWear = (cond.wheels[2].wearPct + cond.wheels[3].wearPct) / 2f
        val rearWear = (cond.wheels[0].wearPct + cond.wheels[1].wearPct) / 2f
        val rearOverheating = avgRearSurface > 101f || (rearWear - frontWear) > 5f
        val frontOverheating = avgFrontSurface > 101f || (frontWear - rearWear) > 5f

        val peakSpeed = maxOf(
            traces.maxOfOrNull { it.speedKmh } ?: 320,
            laps.maxOfOrNull { it.topSpeedKmh } ?: 320,
        )
        val maxBrakeTemp = cond.wheels.maxOfOrNull { it.brakeTempC } ?: 450
        val highSpeedCornersRatio = traces.count { it.speedKmh in 185..270 && abs(it.gForceLat) > 2.2f }

        var frontWing = track.baseFrontWing
        var rearWing = track.baseRearWing
        if (frontOverheating) frontWing = (frontWing + 2).coerceAtMost(50)
        if (rearOverheating) rearWing = (rearWing + 2).coerceAtMost(50)
        if (peakSpeed < 318 && track.straightRatioPct >= 58) {
            frontWing = (frontWing - 2).coerceAtLeast(4)
            rearWing = (rearWing - 2).coerceAtLeast(4)
        }

        val diffOnThrottle = when {
            rearOverheating -> 52
            track.straightRatioPct >= 65 -> 58
            else -> 55
        }
        val diffOffThrottle = if (frontOverheating) 51 else 54

        val frontThermalDelta = avgFrontInner - avgFrontSurface
        val rearThermalDelta = avgRearInner - avgRearSurface
        val frontCamber = if (frontThermalDelta > 6f) -2.70f else -3.30f
        val rearCamber = if (rearThermalDelta > 6f) -1.20f else -1.80f
        val frontToe = if (highSpeedCornersRatio > 6) 0.02f else 0.05f
        val rearToe = if (rearOverheating) 0.14f else 0.10f

        val needsSoftKerbs = track.kerbAggression.contains("Alta", ignoreCase = true)
        val frontSusp = if (needsSoftKerbs) 29 else 35
        val rearSusp = if (rearOverheating) 12 else 17
        val frontArb = if (frontOverheating) 10 else 14
        val rearArb = if (rearOverheating) 5 else 8
        val frontRideHeight = if (track.elevationChangeM > 50) 24 else 21
        val rearRideHeight = if (track.elevationChangeM > 50) 50 else 47

        val brakePressure = if (maxBrakeTemp > 880) 97 else 100
        val brakeBias = if (maxBrakeTemp > 880) 55 else 56
        val trackHeatOffset = ((state.sessionMeta.trackTempC - 32) * 0.06f).coerceIn(-0.4f, 0.8f)
        val frontPsi = ((23.8f + trackHeatOffset) * 10f).roundToInt() / 10f
        val rearPsi = ((21.8f + trackHeatOffset) * 10f).roundToInt() / 10f

        val insights = listOf(
            SetupDiagnosticInsight(
                area = "Aerodinâmica (${track.downforceDemand})",
                observation = "${track.name} possui ${track.straightRatioPct}% de retas e vel. máxima de $peakSpeed km/h.",
                adjustment = "Asa Dianteira $frontWing / Traseira $rearWing" +
                    if (rearOverheating) " (+2 traseira para conter desgaste traseiro)" else "",
                priority = if (rearOverheating || frontOverheating) AlertSeverity.WARNING else AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Suspensão & Elevação (${track.elevationChangeM}m)",
                observation = "${track.kerbAggression}. Delta térmico D/T: ${avgFrontSurface.roundToInt()}°C vs ${avgRearSurface.roundToInt()}°C.",
                adjustment = "Susp. $frontSusp/$rearSusp • ARB $frontArb/$rearArb • Altura $frontRideHeight/$rearRideHeight",
                priority = if (abs(avgFrontSurface - avgRearSurface) > 5f) AlertSeverity.WARNING else AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Diferencial & Tração",
                observation = "Desgaste traseiro em ${rearWear.roundToInt()}% contra ${frontWear.roundToInt()}% na dianteira.",
                adjustment = "Aceleração $diffOnThrottle% / Desaceleração $diffOffThrottle% para estabilizar saída.",
                priority = if (rearWear - frontWear > 4f) AlertSeverity.CRITICAL else AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Geometria, Freios & Pressão",
                observation = "Delta interno/superfície de ${frontThermalDelta.roundToInt()}°C e pico de freio em $maxBrakeTemp°C.",
                adjustment = "Cambagem $frontCamber° / $rearCamber° • Freio $brakePressure% (Bal. $brakeBias%) • PSI $frontPsi / $rearPsi",
                priority = if (maxBrakeTemp > 880) AlertSeverity.WARNING else AlertSeverity.INFO,
            ),
        )

        return SetupRecommendation(
            trackProfile = track,
            carCategory = CarCategory.FORMULA,
            aeroLabelPrimary = "Asa Dianteira",
            aeroLabelSecondary = "Asa Traseira",
            aeroFrontWing = frontWing,
            aeroRearWing = rearWing,
            diffOnThrottle = diffOnThrottle,
            diffOffThrottle = diffOffThrottle,
            frontCamber = frontCamber,
            rearCamber = rearCamber,
            frontToe = frontToe,
            rearToe = rearToe,
            frontSuspension = frontSusp,
            rearSuspension = rearSusp,
            frontAntiRollBar = frontArb,
            rearAntiRollBar = rearArb,
            frontRideHeight = frontRideHeight,
            rearRideHeight = rearRideHeight,
            brakePressurePct = brakePressure,
            brakeBiasPct = brakeBias,
            frontPsi = frontPsi,
            rearPsi = rearPsi,
            targetHotPsiRange = "21.5 – 24.5 PSI",
            insights = insights,
        )
    }

    private fun buildGtEnduranceSetup(state: LiveTelemetryState): SetupRecommendation {
        val track = TrackCatalog.byId(state.sessionMeta.trackId)
        val cond = state.carCondition
        val rearAvgTemp = (cond.wheels[0].tempSurfaceC + cond.wheels[1].tempSurfaceC) / 2f
        val frontAvgTemp = (cond.wheels[2].tempSurfaceC + cond.wheels[3].tempSurfaceC) / 2f
        val maxSlip = cond.wheels.maxOfOrNull { abs(it.slipRatio) } ?: 0f
        val rearOverheating = rearAvgTemp > 94f || maxSlip > 0.16f

        val splitter = if (track.downforceDemand == "Alta") 4 else 2
        val rearWing = when {
            track.downforceDemand == "Alta" -> 10
            track.straightRatioPct >= 62 -> 5
            else -> 8
        } + if (rearOverheating) 1 else 0

        val tcLevel = if (rearOverheating || maxSlip > 0.16f) 4 else 3
        val absLevel = if (state.sessionMeta.weather.contains("Chuva", ignoreCase = true)) 4 else 2
        val brakeBias = if (frontAvgTemp > rearAvgTemp + 5f) 53 else 55
        val frontPsi = (26.9f + ((32 - state.sessionMeta.trackTempC) * 0.04f)).coerceIn(26.2f, 27.6f)
        val rearPsi = (26.8f + ((32 - state.sessionMeta.trackTempC) * 0.04f)).coerceIn(26.1f, 27.5f)

        val insights = listOf(
            SetupDiagnosticInsight(
                area = "Aero GT3 (Splitter & Asa Traseira)",
                observation = "${state.detectedGame.displayName} • ${track.name} exige equilíbrio de pressão aerodinâmica para curvas médias.",
                adjustment = "Splitter Diant. $splitter • Asa Traseira $rearWing (estabilidade em frenagem de alta)",
                priority = AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Eletrônica GT (TC & ABS)",
                observation = "Deslizamento máximo medido em ${(maxSlip * 100).roundToInt()}% e temp. traseira ${rearAvgTemp.roundToInt()}°C.",
                adjustment = "Controle de Tração TC $tcLevel • ABS Nível $absLevel • Balanço de Freio $brakeBias%",
                priority = if (maxSlip > 0.16f) AlertSeverity.WARNING else AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Pressão Alvo a Quente GT3",
                observation = "Pneus GT3 alcançam pico de aderência entre 26.5 e 27.5 PSI quentes (${state.sessionMeta.trackTempC}°C pista).",
                adjustment = "Calibragem fria sugerida para atingir ${"%.1f".format(frontPsi)} / ${"%.1f".format(rearPsi)} PSI a quente",
                priority = AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Altura & Barras GT / Endurance",
                observation = "${track.kerbAggression} com ${track.elevationChangeM}m de desnível.",
                adjustment = "Altura Solo 54 mm / 72 mm • ARB 6 / 4 • Cambagem -3.4° / -2.1°",
                priority = AlertSeverity.INFO,
            ),
        )

        return SetupRecommendation(
            trackProfile = track,
            carCategory = CarCategory.GT_ENDURANCE,
            aeroLabelPrimary = "Splitter Dianteiro",
            aeroLabelSecondary = "Asa Traseira GT",
            aeroFrontWing = splitter,
            aeroRearWing = rearWing,
            diffOnThrottle = 70,
            diffOffThrottle = 45,
            frontCamber = -3.40f,
            rearCamber = -2.10f,
            frontToe = -0.08f,
            rearToe = 0.16f,
            frontSuspension = 148,
            rearSuspension = 132,
            frontAntiRollBar = 6,
            rearAntiRollBar = 4,
            frontRideHeight = 54,
            rearRideHeight = 72,
            brakePressurePct = 100,
            brakeBiasPct = brakeBias,
            frontPsi = (frontPsi * 10f).roundToInt() / 10f,
            rearPsi = (rearPsi * 10f).roundToInt() / 10f,
            recommendedTcLevel = tcLevel,
            recommendedAbsLevel = absLevel,
            targetHotPsiRange = "26.5 – 27.5 PSI",
            insights = insights,
        )
    }

    private fun buildStreetTouringSetup(state: LiveTelemetryState): SetupRecommendation {
        val track = TrackCatalog.byId(state.sessionMeta.trackId)
        val cond = state.carCondition
        val maxSlip = cond.wheels.maxOfOrNull { abs(it.slipRatio) } ?: 0f
        val avgFrontTemp = (cond.wheels[2].tempSurfaceC + cond.wheels[3].tempSurfaceC) / 2f
        val avgRearTemp = (cond.wheels[0].tempSurfaceC + cond.wheels[1].tempSurfaceC) / 2f

        val insights = listOf(
            SetupDiagnosticInsight(
                area = "Tração & Diferencial Turismo/Rua",
                observation = "Deslizamento de roda em ${(maxSlip * 100).roundToInt()}% com temperatura ${avgRearTemp.roundToInt()}°C atrás.",
                adjustment = "Diferencial Aceleração 45% / Desaceleração 25% para tracionar sem destracionar.",
                priority = if (maxSlip > 0.18f) AlertSeverity.WARNING else AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Suspensão & Altura de Rua/Pista",
                observation = "Equilíbrio para zebras, ondulações de asfalto e transferências de peso rápidas.",
                adjustment = "Molas 110 / 98 • Altura 105 mm / 112 mm • ARB 24 / 19",
                priority = AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Pressão de Pneus Esportivos",
                observation = "Dianteiros a ${avgFrontTemp.roundToInt()}°C vs traseiros a ${avgRearTemp.roundToInt()}°C.",
                adjustment = "Pressão fria recomendada 30.5 PSI Diant. / 29.5 PSI Tras. • Cambagem -2.2° / -1.5°",
                priority = AlertSeverity.INFO,
            ),
            SetupDiagnosticInsight(
                area = "Freios & Balanço em Carga Lateral",
                observation = "Evite travamento na entrada de curva mantendo modulação progressiva.",
                adjustment = "Pressão 95% • Balanço 53% Dianteiro",
                priority = AlertSeverity.INFO,
            ),
        )

        return SetupRecommendation(
            trackProfile = track,
            carCategory = CarCategory.STREET_TOURING,
            aeroLabelPrimary = "Lip Dianteiro",
            aeroLabelSecondary = "Aerofólio Traseiro",
            aeroFrontWing = 1,
            aeroRearWing = 3,
            diffOnThrottle = 45,
            diffOffThrottle = 25,
            frontCamber = -2.20f,
            rearCamber = -1.50f,
            frontToe = 0.00f,
            rearToe = 0.10f,
            frontSuspension = 110,
            rearSuspension = 98,
            frontAntiRollBar = 24,
            rearAntiRollBar = 19,
            frontRideHeight = 105,
            rearRideHeight = 112,
            brakePressurePct = 95,
            brakeBiasPct = 53,
            frontPsi = 30.5f,
            rearPsi = 29.5f,
            recommendedTcLevel = 2,
            recommendedAbsLevel = 2,
            targetHotPsiRange = "30.0 – 32.5 PSI",
            insights = insights,
        )
    }
}
