package com.example.telemetry.analysis

import com.example.telemetry.data.TrackCatalog
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SetupDiagnosticInsight
import com.example.telemetry.model.SetupRecommendation
import kotlin.math.abs
import kotlin.math.roundToInt

class SetupAdvisorEngine {

    fun analyzeAndRecommend(state: LiveTelemetryState): SetupRecommendation {
        val track = TrackCatalog.byId(state.sessionMeta.trackId)
        val cond = state.carCondition
        val traces = state.traceBuffer
        val laps = state.completedLaps

        // Derive live telemetry diagnostics
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

        // 1. Aerodynamics adjusted by track demand + live speed / balance telemetry
        var frontWing = track.baseFrontWing
        var rearWing = track.baseRearWing
        if (frontOverheating) {
            frontWing = (frontWing + 2).coerceAtMost(50)
        }
        if (rearOverheating) {
            rearWing = (rearWing + 2).coerceAtMost(50)
        }
        if (peakSpeed < 318 && track.straightRatioPct >= 58) {
            frontWing = (frontWing - 2).coerceAtLeast(4)
            rearWing = (rearWing - 2).coerceAtLeast(4)
        }

        // 2. Differential adjusted by rear traction wear & surface temp
        val diffOnThrottle = when {
            rearOverheating -> 52
            track.straightRatioPct >= 65 -> 58
            else -> 55
        }
        val diffOffThrottle = when {
            frontOverheating -> 51
            else -> 54
        }

        // 3. Suspension Geometry (Camber/Toe) driven by inner vs surface thermal delta
        val frontThermalDelta = avgFrontInner - avgFrontSurface
        val rearThermalDelta = avgRearInner - avgRearSurface
        val frontCamber = if (frontThermalDelta > 6f) -2.70f else -3.30f
        val rearCamber = if (rearThermalDelta > 6f) -1.20f else -1.80f
        val frontToe = if (highSpeedCornersRatio > 6) 0.02f else 0.05f
        val rearToe = if (rearOverheating) 0.14f else 0.10f

        // 4. Suspension, Anti-Roll Bars & Ride Height driven by elevation, kerbs, and balance
        val needsSoftKerbs = track.kerbAggression.contains("Alta", ignoreCase = true)
        val frontSusp = if (needsSoftKerbs) 29 else 35
        val rearSusp = if (rearOverheating) 12 else 17
        val frontArb = if (frontOverheating) 10 else 14
        val rearArb = if (rearOverheating) 5 else 8
        val frontRideHeight = if (track.elevationChangeM > 50) 24 else 21
        val rearRideHeight = if (track.elevationChangeM > 50) 50 else 47

        // 5. Brakes & Tyre Pressures
        val brakePressure = if (maxBrakeTemp > 880) 97 else 100
        val brakeBias = if (maxBrakeTemp > 880) 55 else 56
        val trackHeatOffset = ((state.sessionMeta.trackTempC - 32) * 0.06f).coerceIn(-0.4f, 0.8f)
        val frontPsi = ((23.8f + trackHeatOffset) * 10f).roundToInt() / 10f
        val rearPsi = ((21.8f + trackHeatOffset) * 10f).roundToInt() / 10f

        // Generate concrete rationale cards
        val insights = buildList {
            add(
                SetupDiagnosticInsight(
                    area = "Aerodinâmica (${track.downforceDemand})",
                    observation = "${track.name} possui ${track.straightRatioPct}% de retas e velocidade máxima medida de $peakSpeed km/h.",
                    adjustment = "Asa Dianteira $frontWing / Traseira $rearWing" +
                        if (rearOverheating) " (+2 traseira para conter desgaste traseiro)" else "",
                    priority = if (rearOverheating || frontOverheating) AlertSeverity.WARNING else AlertSeverity.INFO,
                )
            )
            add(
                SetupDiagnosticInsight(
                    area = "Suspensão & Elevação (${track.elevationChangeM}m)",
                    observation = "${track.kerbAggression}. Diferença térmica D/T atual: ${avgFrontSurface.roundToInt()}°C vs ${avgRearSurface.roundToInt()}°C.",
                    adjustment = "Susp. $frontSusp/$rearSusp • ARB $frontArb/$rearArb • Altura $frontRideHeight/$rearRideHeight",
                    priority = if (abs(avgFrontSurface - avgRearSurface) > 5f) AlertSeverity.WARNING else AlertSeverity.INFO,
                )
            )
            add(
                SetupDiagnosticInsight(
                    area = "Diferencial & Tração",
                    observation = "Desgaste traseiro em ${rearWear.roundToInt()}% contra ${frontWear.roundToInt()}% na dianteira.",
                    adjustment = "Aceleração $diffOnThrottle% / Desaceleração $diffOffThrottle% para estabilizar saída de curva.",
                    priority = if (rearWear - frontWear > 4f) AlertSeverity.CRITICAL else AlertSeverity.INFO,
                )
            )
            add(
                SetupDiagnosticInsight(
                    area = "Geometria, Freios & Pressão",
                    observation = "Delta interno/superfície de ${frontThermalDelta.roundToInt()}°C na dianteira e pico de freio em $maxBrakeTemp°C.",
                    adjustment = "Cambagem $frontCamber° / $rearCamber° • Freio $brakePressure% (Bal. $brakeBias%) • PSI $frontPsi / $rearPsi",
                    priority = if (maxBrakeTemp > 880) AlertSeverity.WARNING else AlertSeverity.INFO,
                )
            )
        }

        return SetupRecommendation(
            trackProfile = track,
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
            insights = insights,
        )
    }
}
