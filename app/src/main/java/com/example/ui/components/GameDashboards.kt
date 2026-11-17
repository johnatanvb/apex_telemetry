package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SectorStatus
import com.example.telemetry.model.TelemetrySourceMode
import com.example.telemetry.model.WheelData
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.AsphaltElevated
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
import com.example.ui.theme.ThermalAmber
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hands-free, zero-scroll standard race dashboard tailored specifically per game title.
 * Adapts automatically between Portrait and Landscape/Wide cockpit layouts so every essential
 * race metric stays legible at a single glance without touching the screen.
 */
@Composable
fun StandardGameRaceDashboard(
    state: LiveTelemetryState,
    modifier: Modifier = Modifier,
) {
    val game = state.detectedGame
    val gameAccent = Color(game.badgeColorHex)

    PitWallCard(
        borderColor = gameAccent.copy(alpha = 0.65f),
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isLandscapeCockpit = maxWidth >= 560.dp

            if (isLandscapeCockpit) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardTopStrip(state = state, gameAccent = gameAccent)
                    RevLightsBar(revLightsPct = state.currentSample.revLightsPct)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(0.95f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CoreGlanceHeroCluster(state = state, gameAccent = gameAccent, compact = true)
                            PedalBarsCluster(state = state)
                        }
                        Column(
                            modifier = Modifier.weight(1.05f),
                        ) {
                            GameSpecificHudGrid(state = state)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    DashboardTopStrip(state = state, gameAccent = gameAccent)
                    RevLightsBar(revLightsPct = state.currentSample.revLightsPct)
                    CoreGlanceHeroCluster(state = state, gameAccent = gameAccent, compact = false)
                    PedalBarsCluster(state = state)
                    GameSpecificHudGrid(state = state)
                }
            }
        }
    }
}

@Composable
private fun DashboardTopStrip(
    state: LiveTelemetryState,
    gameAccent: Color,
) {
    val game = state.detectedGame
    val meta = state.sessionMeta
    val playerGrid = state.gridEntries.firstOrNull { it.isPlayer }
    val isWaitingUdp = state.sourceMode == TelemetrySourceMode.UDP &&
        state.connectionState != ConnectionState.CONNECTED_UDP

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(gameAccent.copy(alpha = 0.22f))
                    .border(1.dp, gameAccent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = game.shortTag,
                    style = MaterialTheme.typography.labelMedium,
                    color = gameAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isWaitingUdp) {
                    "Aguardando UDP :${state.udpPort}"
                } else {
                    state.carCategory.label
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isWaitingUdp) SectorYellow else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val flagColor = when (meta.flagStatus) {
                FlagStatus.GREEN -> SectorGreen
                FlagStatus.RED -> F1Red
                else -> SectorYellow
            }
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(flagColor)
            )
            if (playerGrid != null) {
                Text(
                    text = "P${playerGrid.position}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = TelemetryMonoFamily,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (meta.totalLaps > 1) "V ${meta.currentLap}/${meta.totalLaps}" else "V ${meta.currentLap}",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = TelemetryMonoFamily,
                color = TelemetryCyan,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CoreGlanceHeroCluster(
    state: LiveTelemetryState,
    gameAccent: Color,
    compact: Boolean,
) {
    val sample = state.currentSample
    val speedFont = if (compact) 40.sp else 46.sp
    val gearFont = if (compact) 38.sp else 44.sp
    val gearBoxW = if (compact) 78.dp else 88.dp
    val gearBoxH = if (compact) 84.dp else 94.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Speed + RPM
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VELOCIDADE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${sample.speedKmh}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = speedFont),
                    fontFamily = TelemetryMonoFamily,
                    color = TelemetryCyan,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            Text(
                text = "${sample.engineRpm} RPM",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = TelemetryMonoFamily,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Center: High-Contrast Gear Box (+ Suggested Gear when provided)
        val gearLabel = when (sample.gear) {
            -1 -> "R"
            0 -> "N"
            else -> "${sample.gear}"
        }
        Box(
            modifier = Modifier
                .size(width = gearBoxW, height = gearBoxH)
                .clip(RoundedCornerShape(14.dp))
                .background(CarbonBlack)
                .border(2.dp, gameAccent, RoundedCornerShape(14.dp)),
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
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = gearFont),
                    fontFamily = TelemetryMonoFamily,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (sample.suggestedGear > 0 && sample.suggestedGear != sample.gear) {
                    Text(
                        text = "SUG ${sample.suggestedGear}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = TelemetryMonoFamily,
                        color = SectorYellow,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Right: Game-specific critical badge + Delta
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            PrimaryRightBadgeForGame(state)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "DELTA",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                text = formatDeltaMs(sample.deltaMs),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = TelemetryMonoFamily,
                color = if (sample.deltaMs <= 0) SectorGreen else F1Red,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PedalBarsCluster(state: LiveTelemetryState) {
    val sample = state.currentSample
    val showClutchOrHandbrake = state.detectedGame == GameTitle.FORZA_HORIZON_5

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompactPedalBar(
            label = "ACEL",
            fraction = sample.throttle,
            color = SectorGreen,
            modifier = Modifier.weight(1f),
        )
        CompactPedalBar(
            label = "FREIO",
            fraction = sample.brake,
            color = F1Red,
            modifier = Modifier.weight(1f),
        )
        if (showClutchOrHandbrake) {
            CompactPedalBar(
                label = if (sample.handbrake > 0.1f) "MÃO" else "EMBR",
                fraction = if (sample.handbrake > 0.1f) sample.handbrake else sample.clutch / 100f,
                color = if (sample.handbrake > 0.1f) ThermalAmber else TelemetryCyan,
                modifier = Modifier.weight(0.7f),
            )
        }
    }
}

@Composable
private fun GameSpecificHudGrid(state: LiveTelemetryState) {
    when (state.detectedGame) {
        GameTitle.F1_25, GameTitle.F1_24, GameTitle.F1_23, GameTitle.AUTO ->
            FormulaRaceHudGrid(state)

        GameTitle.GRAN_TURISMO_7 ->
            Gt7RaceHudGrid(state)

        GameTitle.ASSETTO_CORSA_COMPETIZIONE ->
            AccRaceHudGrid(state)

        GameTitle.ASSETTO_CORSA ->
            AcRaceHudGrid(state)

        GameTitle.FORZA_MOTORSPORT ->
            ForzaMotorsportRaceHudGrid(state)

        GameTitle.FORZA_HORIZON_5 ->
            ForzaHorizonRaceHudGrid(state)

        GameTitle.PROJECT_CARS_2, GameTitle.AUTOMOBILISTA_2 ->
            SmsPcarsRaceHudGrid(state)
    }
}

@Composable
private fun PrimaryRightBadgeForGame(state: LiveTelemetryState) {
    val sample = state.currentSample
    val cond = state.carCondition
    when (state.detectedGame) {
        GameTitle.F1_25, GameTitle.F1_24, GameTitle.F1_23, GameTitle.AUTO -> {
            val drsColor = when {
                sample.drsActive -> SectorGreen
                sample.drsAvailable -> SectorYellow
                else -> TextMuted
            }
            val drsLabel = when {
                sample.drsActive -> "DRS ATIVO"
                sample.drsAvailable -> "DRS DISP."
                else -> "DRS OFF"
            }
            StatusPillBox(text = drsLabel, accent = drsColor)
        }

        GameTitle.ASSETTO_CORSA_COMPETIZIONE, GameTitle.ASSETTO_CORSA -> {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusPillBox(
                    text = "TC${cond.tcLevel}",
                    accent = if (sample.tcActive) SectorYellow else SectorGreen,
                )
                StatusPillBox(
                    text = "ABS${cond.absLevel}",
                    accent = if (sample.absActive) F1Red else TelemetryCyan,
                )
            }
        }

        GameTitle.GRAN_TURISMO_7, GameTitle.FORZA_MOTORSPORT -> {
            StatusPillBox(
                text = "TURBO %+.2f b".format(cond.turboBoostBar),
                accent = if (cond.turboBoostBar > 0.8f) ThermalAmber else TelemetryCyan,
            )
        }

        GameTitle.FORZA_HORIZON_5 -> {
            if (sample.handbrake > 0.1f) {
                StatusPillBox(text = "DRIFT / MÃO", accent = SectorYellow)
            } else {
                StatusPillBox(
                    text = "BOOST %+.2f b".format(cond.turboBoostBar),
                    accent = if (cond.turboBoostBar > 0.8f) ThermalAmber else TelemetryCyan,
                )
            }
        }

        GameTitle.PROJECT_CARS_2, GameTitle.AUTOMOBILISTA_2 -> {
            StatusPillBox(
                text = "BB ${cond.brakeBiasPct}%",
                accent = TelemetryCyan,
            )
        }
    }
}

@Composable
private fun FormulaRaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val meta = state.sessionMeta
    val maxWearWheel = cond.wheels.maxByOrNull { it.wearPct }
    val hottestTyre = cond.wheels.maxByOrNull { it.tempSurfaceC }
    val playerGrid = state.gridEntries.firstOrNull { it.isPlayer }

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_formula"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "ERS",
                value = "${cond.ersStorePct}%",
                sub = cond.ersDeployMode,
                accent = if (cond.ersStorePct > 25) TelemetryCyan else F1Red,
                progressFraction = cond.ersStorePct / 100f,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "COMBUSTÍVEL",
                value = "%.1f V".format(cond.fuelRemainingLaps),
                sub = "%.1f kg • %s".format(cond.fuelInTankKg, cond.fuelMix),
                accent = if (cond.fuelRemainingLaps > 1.8f) SectorGreen else F1Red,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "GAP / BOX",
                value = if (playerGrid != null && playerGrid.position > 1) formatGapMs(playerGrid.intervalMs) else "LÍDER",
                sub = "Box ${playerGrid?.pitStops ?: 0} • ${playerGrid?.tyreCompound?.displayName ?: "M"} (${playerGrid?.tyreAgeLaps ?: 0}v)",
                accent = SectorYellow,
                modifier = Modifier.weight(1f),
            )
        }

        // Glanceable live S1/S2/S3 + Best Lap row for F1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CarbonBlack)
                .border(1.dp, AsphaltBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniSectorBadge("S1", meta.sector1Ms, meta.sector1Status)
            MiniSectorBadge("S2", meta.sector2Ms, meta.sector2Status)
            MiniSectorBadge("S3", meta.sector3Ms, meta.sector3Status)
            Text(
                text = "MELHOR ${formatLapTimeMs(meta.playerBestLapMs)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFamily,
                color = SectorGreen,
                fontWeight = FontWeight.Bold,
            )
        }

        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = false,
            showWear = true,
            highlightTargetLabel = "Pneus °C / Desgaste • Pico: ${hottestTyre?.label ?: "-"} ${hottestTyre?.tempSurfaceC ?: 0}°C (${maxWearWheel?.wearPct?.roundToInt() ?: 0}%)",
        )
    }
}

@Composable
private fun Gt7RaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val meta = state.sessionMeta
    val fuelPct = (cond.fuelInTankKg / cond.fuelCapacityLiters.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_gt7"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "TANQUE GT7",
                value = "%.1f L".format(cond.fuelInTankKg),
                sub = "%.1f voltas rest.".format(cond.fuelRemainingLaps),
                accent = if (cond.fuelRemainingLaps > 1.8f) SectorGreen else F1Red,
                progressFraction = fuelPct,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "MOTOR / ÓLEO",
                value = "${cond.oilTempC}°/${cond.waterTempC}°",
                sub = "Óleo %.1f bar • BB ${cond.brakeBiasPct}%".format(cond.oilPressureBar),
                accent = if (cond.oilTempC >= 112) F1Red else TelemetryCyan,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "MELHOR VOLTA",
                value = formatLapTimeMs(meta.playerBestLapMs),
                sub = "Últ. ${formatLapTimeMs(meta.playerLastLapMs)}",
                accent = SectorPurple,
                modifier = Modifier.weight(1f),
            )
        }
        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = false,
            showWear = false,
            highlightTargetLabel = "Mapa Térmico GT7 (°C) • Turbo %+.2f bar".format(cond.turboBoostBar),
        )
    }
}

@Composable
private fun AccRaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val meta = state.sessionMeta
    val hottestBrake = cond.wheels.maxByOrNull { it.brakeTempC }

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_acc"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "COMBUSTÍVEL",
                value = "%.1f V".format(cond.fuelRemainingLaps),
                sub = "%.1f L • BB ${cond.brakeBiasPct}%".format(cond.fuelInTankKg),
                accent = if (cond.fuelRemainingLaps > 2f) SectorGreen else F1Red,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "FREIOS GT3",
                value = "${hottestBrake?.brakeTempC ?: 420}°C",
                sub = "${hottestBrake?.label ?: "DD"} • Alvo <720°C",
                accent = if ((hottestBrake?.brakeTempC ?: 0) >= 720) F1Red else TelemetryCyan,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "MELHOR VOLTA",
                value = formatLapTimeMs(meta.playerBestLapMs),
                sub = "Atual ${formatLapTimeMs(meta.playerCurrentLapMs)}",
                accent = SectorGreen,
                modifier = Modifier.weight(1f),
            )
        }
        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = true,
            showWear = false,
            psiFirstPriority = true,
            highlightTargetLabel = "Pressão GT3 (Alvo 26.5 – 27.5 PSI quentes)",
        )
    }
}

@Composable
private fun AcRaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val sample = state.currentSample
    val meta = state.sessionMeta
    val maxSlip = cond.wheels.maxOfOrNull { abs(it.slipRatio) } ?: 0f

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_ac"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "FORÇA G",
                value = "%.1fG".format(abs(sample.gForceLat)),
                sub = "Lon %+.1fG • Vol ${(sample.steer * 100).roundToInt()}%".format(sample.gForceLon),
                accent = TelemetryCyan,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "SLIP PNEUS",
                value = "${(maxSlip * 100).roundToInt()}%",
                sub = if (sample.tcActive) "TC Atuando" else "Tração Limpa",
                accent = if (maxSlip >= 0.22f) ThermalAmber else SectorGreen,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "MELHOR VOLTA",
                value = formatLapTimeMs(meta.playerBestLapMs),
                sub = "Últ. ${formatLapTimeMs(meta.playerLastLapMs)}",
                accent = SectorPurple,
                modifier = Modifier.weight(1f),
            )
        }
        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = true,
            showWear = false,
            highlightTargetLabel = "Suspensão D/T: ${cond.wheels[2].suspensionTravelMm.roundToInt()} / ${cond.wheels[0].suspensionTravelMm.roundToInt()} mm • Volta ${(sample.lapProgress * 100).roundToInt()}%",
        )
    }
}

@Composable
private fun ForzaMotorsportRaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val sample = state.currentSample
    val meta = state.sessionMeta
    val maxSlip = cond.wheels.maxOfOrNull { abs(it.slipRatio) } ?: 0f
    val fuelPct = ((cond.fuelInTankKg / cond.fuelCapacityLiters.coerceAtLeast(1f)) * 100).roundToInt()

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_fm"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "SLIP TRAÇÃO",
                value = "${(maxSlip * 100).roundToInt()}%",
                sub = "Força G %.1f lat".format(abs(sample.gForceLat)),
                accent = if (maxSlip > 0.2f) ThermalAmber else SectorGreen,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "COMBUSTÍVEL",
                value = "$fuelPct%",
                sub = "%.1f voltas rest.".format(cond.fuelRemainingLaps),
                accent = if (fuelPct > 15) TelemetryCyan else F1Red,
                progressFraction = fuelPct / 100f,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "MELHOR VOLTA",
                value = formatLapTimeMs(meta.playerBestLapMs),
                sub = "Atual ${formatLapTimeMs(meta.playerCurrentLapMs)}",
                accent = SectorYellow,
                modifier = Modifier.weight(1f),
            )
        }
        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = false,
            showWear = true,
            highlightTargetLabel = "Forza Motorsport • Temp (°C) & Desgaste FM23",
        )
    }
}

@Composable
private fun ForzaHorizonRaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val sample = state.currentSample
    val maxSlip = cond.wheels.maxOfOrNull { abs(it.slipRatio) } ?: 0f
    val avgSusp = cond.wheels.map { it.suspensionTravelMm }.average().roundToInt()

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_fh5"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "DESTRACIONAMENTO",
                value = "${(maxSlip * 100).roundToInt()}%",
                sub = if (sample.handbrake > 0.1f) "FREIO MÃO ON" else "Embreagem ${sample.clutch}%",
                accent = if (sample.handbrake > 0.1f || maxSlip > 0.22f) ThermalAmber else SectorGreen,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "SUSPENSÃO",
                value = "$avgSusp mm",
                sub = "G Lat %.1f / Lon %+.1f".format(abs(sample.gForceLat), sample.gForceLon),
                accent = TelemetryCyan,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "TURBO BOOST",
                value = "%+.2f b".format(cond.turboBoostBar),
                sub = "Evento / Rua FH5",
                accent = SectorYellow,
                modifier = Modifier.weight(1f),
            )
        }
        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = false,
            showWear = false,
            highlightTargetLabel = "Forza Horizon 5 • Temperaturas Data Out (°C)",
        )
    }
}

@Composable
private fun SmsPcarsRaceHudGrid(state: LiveTelemetryState) {
    val cond = state.carCondition
    val meta = state.sessionMeta
    val fuelPct = (cond.fuelInTankKg / cond.fuelCapacityLiters.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("hud_grid_sms"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudTile(
                label = "ÓLEO / ÁGUA",
                value = "${cond.oilTempC}°/${cond.waterTempC}°",
                sub = "Óleo %.1f bar".format(cond.oilPressureBar),
                accent = if (cond.oilTempC >= 112) F1Red else SectorGreen,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "COMBUSTÍVEL",
                value = "%.1f L".format(cond.fuelInTankKg),
                sub = "%.1f voltas est.".format(cond.fuelRemainingLaps),
                accent = TelemetryCyan,
                progressFraction = fuelPct,
                modifier = Modifier.weight(1f),
            )
            HudTile(
                label = "MELHOR VOLTA",
                value = formatLapTimeMs(meta.playerBestLapMs),
                sub = "Últ. ${formatLapTimeMs(meta.playerLastLapMs)}",
                accent = SectorPurple,
                modifier = Modifier.weight(1f),
            )
        }
        MiniFourWheelStrip(
            wheels = cond.wheels,
            showPsi = true,
            showWear = true,
            highlightTargetLabel = "SMS UDP v2 • Temperaturas, PSI & Desgaste",
        )
    }
}

@Composable
private fun MiniSectorBadge(
    label: String,
    timeMs: Long,
    status: SectorStatus,
) {
    val color = when (status) {
        SectorStatus.PURPLE -> SectorPurple
        SectorStatus.GREEN -> SectorGreen
        SectorStatus.YELLOW -> SectorYellow
        SectorStatus.NONE -> TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (timeMs > 0L) "%.2fs".format(timeMs / 1000f) else "--",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = TelemetryMonoFamily,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MiniFourWheelStrip(
    wheels: List<WheelData>,
    showPsi: Boolean,
    showWear: Boolean,
    highlightTargetLabel: String,
    psiFirstPriority: Boolean = false,
) {
    val rl = wheels.getOrElse(0) { WheelData("TE") }
    val rr = wheels.getOrElse(1) { WheelData("TD") }
    val fl = wheels.getOrElse(2) { WheelData("DE") }
    val fr = wheels.getOrElse(3) { WheelData("DD") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonBlack)
            .border(1.dp, AsphaltBorder, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = highlightTargetLabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(fl, fr, rl, rr).forEach { w ->
                val tColor = thermalColorForTyre(w.tempSurfaceC)
                val psiColor = when {
                    w.pressurePsi in 26.5f..27.5f -> SectorGreen
                    w.pressurePsi < 26.0f -> TelemetryCyan
                    else -> ThermalAmber
                }
                val borderAccent = if (psiFirstPriority) psiColor else tColor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AsphaltElevated)
                        .border(1.dp, borderAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (psiFirstPriority) {
                        Text(
                            text = "${w.label} %.1f".format(w.pressurePsi),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = TelemetryMonoFamily,
                            color = psiColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = "${w.tempSurfaceC}°C",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TelemetryMonoFamily,
                            color = tColor,
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = "${w.label} ${w.tempSurfaceC}°",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = TelemetryMonoFamily,
                            color = tColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        if (showPsi) {
                            Text(
                                text = "%.1f PSI".format(w.pressurePsi),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = TelemetryMonoFamily,
                                color = psiColor,
                                maxLines = 1,
                            )
                        } else if (showWear) {
                            Text(
                                text = "Desg ${w.wearPct.roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = TelemetryMonoFamily,
                                color = thermalColorForWear(w.wearPct),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
    progressFraction: Float? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonBlack)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontFamily = TelemetryMonoFamily,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (progressFraction != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AsphaltElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusPillBox(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = TelemetryMonoFamily,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactPedalBar(
    label: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFamily,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CarbonBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}
