package com.example.telemetry.sim

import com.example.telemetry.data.TrackCatalog
import com.example.telemetry.model.CarConditionState
import com.example.telemetry.model.CarTelemetrySample
import com.example.telemetry.model.CompletedLapSummary
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GridCarEntry
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SectorStatus
import com.example.telemetry.model.SessionMeta
import com.example.telemetry.model.TelemetrySourceMode
import com.example.telemetry.model.TyreCompound
import com.example.telemetry.model.WheelData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class RaceSimulatorEngine {

    private data class SimDriverTemplate(
        val code: String,
        val name: String,
        val team: String,
        val color: Long,
        val paceFactor: Float,
        val compound: TyreCompound,
        val isPlayer: Boolean = false,
    )

    private val templates = listOf(
        SimDriverTemplate("VER", "Max Verstappen", "Red Bull Racing", 0xFF3671C6, 1.006f, TyreCompound.MEDIUM),
        SimDriverTemplate("NOR", "Lando Norris", "McLaren", 0xFFFF8000, 1.004f, TyreCompound.MEDIUM),
        SimDriverTemplate("LEC", "Charles Leclerc", "Ferrari", 0xFFDC0000, 1.002f, TyreCompound.SOFT),
        SimDriverTemplate("PIL", "Você (Apex #25)", "McLaren", 0xFFFF8000, 1.003f, TyreCompound.MEDIUM, isPlayer = true),
        SimDriverTemplate("PIA", "Oscar Piastri", "McLaren", 0xFFFF8000, 0.999f, TyreCompound.MEDIUM),
        SimDriverTemplate("HAM", "Lewis Hamilton", "Ferrari", 0xFFDC0000, 0.998f, TyreCompound.HARD),
        SimDriverTemplate("RUS", "George Russell", "Mercedes", 0xFF00D2BE, 0.997f, TyreCompound.MEDIUM),
        SimDriverTemplate("ANT", "Kimi Antonelli", "Mercedes", 0xFF00D2BE, 0.993f, TyreCompound.SOFT),
        SimDriverTemplate("ALO", "Fernando Alonso", "Aston Martin", 0xFF229971, 0.990f, TyreCompound.HARD),
        SimDriverTemplate("SAI", "Carlos Sainz", "Williams", 0xFF64C4FF, 0.989f, TyreCompound.MEDIUM),
        SimDriverTemplate("ALB", "Alexander Albon", "Williams", 0xFF64C4FF, 0.986f, TyreCompound.HARD),
        SimDriverTemplate("GAS", "Pierre Gasly", "Alpine", 0xFFFF87BC, 0.984f, TyreCompound.SOFT),
        SimDriverTemplate("TSU", "Yuki Tsunoda", "Red Bull Racing", 0xFF3671C6, 0.983f, TyreCompound.MEDIUM),
        SimDriverTemplate("HAD", "Isack Hadjar", "Racing Bulls", 0xFF6692FF, 0.980f, TyreCompound.MEDIUM),
        SimDriverTemplate("STR", "Lance Stroll", "Aston Martin", 0xFF229971, 0.978f, TyreCompound.HARD),
        SimDriverTemplate("OCO", "Esteban Ocon", "Haas", 0xFFB6BABD, 0.977f, TyreCompound.MEDIUM),
        SimDriverTemplate("BEA", "Oliver Bearman", "Haas", 0xFFB6BABD, 0.976f, TyreCompound.SOFT),
        SimDriverTemplate("HUL", "Nico Hulkenberg", "Sauber", 0xFF52E252, 0.974f, TyreCompound.HARD),
        SimDriverTemplate("BOR", "Gabriel Bortoleto", "Sauber", 0xFF52E252, 0.973f, TyreCompound.MEDIUM),
        SimDriverTemplate("DOO", "Jack Doohan", "Alpine", 0xFFFF87BC, 0.970f, TyreCompound.SOFT),
    )

    fun observeSimulation(
        trackId: Int,
        udpPort: Int,
        localIp: String,
        stressTestMode: Boolean = false,
    ): Flow<LiveTelemetryState> = flow {
        val track = TrackCatalog.byId(trackId)
        val traceHistory = ArrayDeque<CarTelemetrySample>(120)
        val completedLaps = mutableListOf<CompletedLapSummary>()
        val lapSpeedPoints = mutableListOf<Int>()

        // Pre-seed 3 realistic completed laps so charts, history, and setup advisor work immediately
        val baseLapMs = when (trackId) {
            11 -> 81_200L
            10 -> 106_400L
            6 -> 88_900L
            14 -> 91_300L
            else -> 71_850L
        }
        for (lapIdx in 1..3) {
            val variance = when (lapIdx) {
                1 -> 620L
                2 -> 180L
                else -> -240L
            }
            val lapTime = baseLapMs + variance
            val s1 = (lapTime * 0.258).toLong()
            val s2 = (lapTime * 0.504).toLong()
            val s3 = lapTime - s1 - s2
            completedLaps.add(
                CompletedLapSummary(
                    lapNumber = lapIdx,
                    lapTimeMs = lapTime,
                    sector1Ms = s1,
                    sector2Ms = s2,
                    sector3Ms = s3,
                    tyreCompound = TyreCompound.MEDIUM,
                    topSpeedKmh = if (trackId == 11) 346 else 331,
                    avgThrottlePct = 74,
                    maxTyreWearPct = lapIdx * 5,
                    speedTrace = List(40) { step ->
                        val phase = (step / 40f) * 2f * PI
                        (235 + 95 * sin(phase) - 28 * cos(phase * 3f)).roundToInt().coerceIn(78, 348)
                    },
                )
            )
        }

        val driverProgress = FloatArray(templates.size) { idx ->
            (0.42f - idx * 0.024f + 1f) % 1f
        }
        val driverLaps = IntArray(templates.size) { 4 }
        val tyreWear = FloatArray(4) { if (stressTestMode) 68f else 28f }
        var engineTempC = if (stressTestMode) 114f else 101f
        var ersStorePct = 78f
        var fuelKg = 24.5f
        var tick = 0L
        var packetCount = 0L
        var lapStartTimeMs = System.currentTimeMillis()
        var currentLapNum = 4
        var playerPitStops = 0
        var lastLapProgress = driverProgress[3]

        while (true) {
            val dtSec = 0.05f
            tick++
            packetCount += 5

            // Advance all 20 cars around the track
            for (i in templates.indices) {
                val baseSpeed = 0.0155f * templates[i].paceFactor
                val nextProg = driverProgress[i] + baseSpeed * dtSec
                if (nextProg >= 1f) {
                    driverLaps[i]++
                }
                driverProgress[i] = nextProg % 1f
            }

            val pProg = driverProgress[3]
            if (pProg < lastLapProgress) {
                // Player completed a lap!
                val lapDuration = (System.currentTimeMillis() - lapStartTimeMs).coerceIn(60_000L, 125_000L)
                val normalizedLap = baseLapMs + ((sin(currentLapNum.toDouble()) * 320).toLong()) + (tyreWear.max() * 14).toLong()
                val s1 = (normalizedLap * 0.258).toLong()
                val s2 = (normalizedLap * 0.504).toLong()
                val s3 = normalizedLap - s1 - s2
                val downsampled = if (lapSpeedPoints.size >= 40) {
                    val step = lapSpeedPoints.size / 40f
                    List(40) { idx -> lapSpeedPoints[(idx * step).toInt().coerceIn(0, lapSpeedPoints.lastIndex)] }
                } else {
                    List(40) { idx -> 210 + (sin(idx * 0.4) * 90).roundToInt() }
                }
                completedLaps.add(
                    CompletedLapSummary(
                        lapNumber = currentLapNum,
                        lapTimeMs = normalizedLap,
                        sector1Ms = s1,
                        sector2Ms = s2,
                        sector3Ms = s3,
                        tyreCompound = templates[3].compound,
                        topSpeedKmh = (lapSpeedPoints.maxOrNull() ?: 332),
                        avgThrottlePct = 73,
                        maxTyreWearPct = tyreWear.max().roundToInt(),
                        speedTrace = downsampled,
                    )
                )
                currentLapNum++
                lapStartTimeMs = System.currentTimeMillis()
                lapSpeedPoints.clear()
            }
            lastLapProgress = pProg

            // Synthetic realistic corner/straight profile from lapProgress (0..1)
            val cornerWave = (
                0.45 * sin(pProg * 2 * PI * 3.0) +
                    0.30 * cos(pProg * 2 * PI * 5.0 + 0.8) +
                    0.25 * sin(pProg * 2 * PI * 7.0 + 1.4)
                ).toFloat()

            val isStraight = cornerWave > 0.28f
            val isHeavyBraking = cornerWave in -0.55f..-0.22f && sin(pProg * 2 * PI * 8.0) > 0f
            val targetMax = if (trackId == 11) 346f else 334f
            val speedKmh = (212f + cornerWave * 118f).roundToInt().coerceIn(72, targetMax.roundToInt())
            val throttle = when {
                isHeavyBraking -> 0f
                cornerWave > 0.1f -> (0.72f + (cornerWave * 0.28f)).coerceIn(0f, 1f)
                else -> (0.35f + cornerWave * 0.4f).coerceIn(0.08f, 0.92f)
            }
            val brake = when {
                isHeavyBraking -> (0.72f + abs(cornerWave) * 0.28f).coerceIn(0f, 1f)
                cornerWave < -0.35f -> 0.45f
                else -> 0f
            }
            val steer = (sin(pProg * 2 * PI * 6.0) * (1f - throttle * 0.65f)).toFloat().coerceIn(-1f, 1f)
            val gear = when {
                speedKmh < 95 -> 2
                speedKmh < 130 -> 3
                speedKmh < 175 -> 4
                speedKmh < 220 -> 5
                speedKmh < 265 -> 6
                speedKmh < 300 -> 7
                else -> 8
            }
            val rpm = (9600 + ((speedKmh % 42) / 42f) * 2650).roundToInt().coerceIn(8500, 12550)
            val revLights = (((rpm - 9200) / 3300f) * 100f).roundToInt().coerceIn(10, 100)
            val drsAvailable = isStraight
            val drsActive = isStraight && speedKmh > 255
            val gLat = steer * (speedKmh / 105f)
            val gLon = if (brake > 0.2f) -brake * 4.8f else throttle * 1.45f
            val deltaMs = (sin(pProg * 2 * PI * 2.0) * 290f - 120f).roundToInt()

            if (tick % 3L == 0L) lapSpeedPoints.add(speedKmh)

            // Thermal & wear evolution
            val wearDelta = if (stressTestMode) 0.045f else 0.015f
            tyreWear[0] = (tyreWear[0] + wearDelta * 1.15f).coerceAtMost(96f)
            tyreWear[1] = (tyreWear[1] + wearDelta * 1.05f).coerceAtMost(96f)
            tyreWear[2] = (tyreWear[2] + wearDelta * 0.95f).coerceAtMost(94f)
            tyreWear[3] = (tyreWear[3] + wearDelta * 1.10f).coerceAtMost(96f)

            val stressBoost = if (stressTestMode) 11f else 0f
            val surfBase = 92 + (abs(gLat) * 4.2f).roundToInt() + stressBoost.roundToInt()
            val brakeBase = (410 + brake * 520f + (if (stressTestMode) 90f else 0f)).roundToInt()
            engineTempC = (engineTempC + (if (throttle > 0.85f) 0.05f else -0.03f)).coerceIn(94f, 121f)
            ersStorePct = (ersStorePct + (if (brake > 0.2f) 0.35f else -0.18f)).coerceIn(12f, 100f)
            fuelKg = max(1.2f, fuelKg - 0.0014f)

            val wheels = listOf(
                WheelData("TE", surfBase + 2, surfBase + 5, 23.1f, tyreWear[0], 0, if (surfBase > 103) 26 else 4, brakeBase - 35),
                WheelData("TD", surfBase + 4, surfBase + 7, 23.3f, tyreWear[1], 0, if (surfBase > 103) 31 else 6, brakeBase - 30),
                WheelData("DE", surfBase - 1, surfBase + 2, 24.4f, tyreWear[2], 0, if (surfBase > 103) 18 else 2, brakeBase + 25),
                WheelData("DD", surfBase + 5, surfBase + 8, 24.6f, tyreWear[3], 0, if (surfBase > 103) 34 else 8, brakeBase + 35),
            )

            val sample = CarTelemetrySample(
                timestampMs = System.currentTimeMillis(),
                speedKmh = speedKmh,
                throttle = throttle,
                brake = brake,
                steer = steer,
                clutch = 0,
                gear = gear,
                engineRpm = rpm,
                revLightsPct = revLights,
                drsActive = drsActive,
                drsAvailable = drsAvailable,
                gForceLat = gLat,
                gForceLon = gLon,
                deltaMs = deltaMs,
                lapProgress = pProg,
            )

            if (traceHistory.size >= 110) traceHistory.removeFirst()
            traceHistory.addLast(sample)

            val sortedIndices = templates.indices.sortedByDescending { idx ->
                driverLaps[idx] + driverProgress[idx]
            }
            val leaderScore = driverLaps[sortedIndices.first()] + driverProgress[sortedIndices.first()]

            val grid = sortedIndices.mapIndexed { rank, idx ->
                val t = templates[idx]
                val totalProg = driverLaps[idx] + driverProgress[idx]
                val gapToLeader = ((leaderScore - totalProg) * (baseLapMs / 1000f) * 1000f).toLong().coerceAtLeast(0L)
                val prevTotal = if (rank == 0) totalProg else driverLaps[sortedIndices[rank - 1]] + driverProgress[sortedIndices[rank - 1]]
                val interval = ((prevTotal - totalProg) * (baseLapMs / 1000f) * 1000f).toLong().coerceAtLeast(0L)
                val pt = TrackCatalog.samplePosition(track, driverProgress[idx])

                GridCarEntry(
                    carIndex = idx,
                    position = rank + 1,
                    gridPosition = idx + 1,
                    driverCode = t.code,
                    driverName = t.name,
                    teamName = t.team,
                    teamColorHex = t.color,
                    isPlayer = t.isPlayer,
                    lapProgress = driverProgress[idx],
                    worldX = pt.x,
                    worldZ = pt.z,
                    currentLapNum = driverLaps[idx],
                    lastLapMs = baseLapMs + (idx * 140L),
                    bestLapMs = baseLapMs - 240L + (idx * 110L),
                    gapToLeaderMs = gapToLeader,
                    intervalMs = interval,
                    tyreCompound = t.compound,
                    tyreAgeLaps = if (t.isPlayer) (tyreWear.max() / 4.2f).roundToInt().coerceAtLeast(1) else 8 + (idx % 6),
                    pitStatus = 0,
                    pitStops = if (t.isPlayer) playerPitStops else if (idx % 5 == 0) 1 else 0,
                    penaltiesSec = if (idx == 6 || idx == 11) 5 else 0,
                    warnings = if (t.isPlayer) 1 else idx % 3,
                    sector1Status = if (rank < 2) SectorStatus.PURPLE else SectorStatus.GREEN,
                    sector2Status = SectorStatus.GREEN,
                    sector3Status = if (rank == 0) SectorStatus.GREEN else SectorStatus.YELLOW,
                )
            }

            val bestPlayerLap = completedLaps.minOfOrNull { it.lapTimeMs } ?: baseLapMs
            val lastPlayerLap = completedLaps.lastOrNull()?.lapTimeMs ?: baseLapMs
            val currentLapElapsed = (pProg * baseLapMs).toLong()
            val flag = if (stressTestMode && (tick / 160L) % 2L == 1L) FlagStatus.VSC else FlagStatus.GREEN

            emit(
                LiveTelemetryState(
                    sourceMode = TelemetrySourceMode.SIMULATOR,
                    connectionState = ConnectionState.SIMULATOR_ACTIVE,
                    udpPort = udpPort,
                    localIpAddress = localIp,
                    packetCount = packetCount,
                    sessionMeta = SessionMeta(
                        trackId = track.trackId,
                        trackName = track.name,
                        sessionType = "Corrida",
                        totalLaps = 24,
                        currentLap = currentLapNum.coerceAtMost(24),
                        trackLengthM = track.lengthMeters,
                        weather = if (trackId == 10) "Nublado Parcial" else "Seco",
                        trackTempC = if (stressTestMode) 44 else 36,
                        airTempC = 25,
                        flagStatus = flag,
                        sessionBestLapMs = bestPlayerLap - 220L,
                        playerBestLapMs = bestPlayerLap,
                        playerLastLapMs = lastPlayerLap,
                        playerCurrentLapMs = currentLapElapsed,
                        sector1Ms = (lastPlayerLap * 0.258).toLong(),
                        sector2Ms = (lastPlayerLap * 0.504).toLong(),
                        sector3Ms = (lastPlayerLap * 0.238).toLong(),
                        sector1Status = SectorStatus.GREEN,
                        sector2Status = if (deltaMs < 0) SectorStatus.PURPLE else SectorStatus.YELLOW,
                        sector3Status = SectorStatus.GREEN,
                        forecastSummary = if (trackId == 10) {
                            "Nuvens carregadas no Setor 2 • 35% chuva em 12 voltas"
                        } else {
                            "Pista seca estável • Vento 11 km/h NO"
                        },
                    ),
                    currentSample = sample,
                    traceBuffer = traceHistory.toList(),
                    carCondition = CarConditionState(
                        wheels = wheels,
                        engineTempC = engineTempC.roundToInt(),
                        iceWearPct = 14,
                        mguKWearPct = 9,
                        turboWearPct = 12,
                        gearboxWearPct = 11,
                        frontWingLeftDamagePct = 0,
                        frontWingRightDamagePct = 0,
                        floorDamagePct = 2,
                        ersStorePct = ersStorePct.roundToInt(),
                        ersDeployMode = if (drsActive) "Ultrapassagem" else "Médio",
                        fuelInTankKg = fuelKg,
                        fuelRemainingLaps = (fuelKg / 1.55f),
                        fuelMix = "Padrão",
                        brakeBiasPct = 56,
                    ),
                    gridEntries = grid,
                    completedLaps = completedLaps.toList(),
                    activeSimulatorTrackId = trackId,
                )
            )

            delay(50L)
        }
    }
}
