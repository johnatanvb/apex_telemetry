package com.example.telemetry.sim

import com.example.telemetry.data.GameCatalog
import com.example.telemetry.data.TrackCatalog
import com.example.telemetry.model.CarCategory
import com.example.telemetry.model.CarConditionState
import com.example.telemetry.model.CarTelemetrySample
import com.example.telemetry.model.CompletedLapSummary
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GameTitle
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

    private val f1Templates = listOf(
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

    private val gtTemplates = listOf(
        SimDriverTemplate("VAN", "Dries Vanthoor", "BMW M Team WRT", 0xFF2979FF, 1.005f, TyreCompound.MEDIUM),
        SimDriverTemplate("EST", "Kévin Estre", "Manthey Porsche 911 GT3", 0xFFFFD600, 1.003f, TyreCompound.MEDIUM),
        SimDriverTemplate("CAL", "Alessandro Pier Guidi", "AF Corse Ferrari 296 GT3", 0xFFDC0000, 1.001f, TyreCompound.MEDIUM),
        SimDriverTemplate("PIL", "Você (GT3 #25)", "Porsche 911 GT3 R", 0xFF00D2BE, 1.002f, TyreCompound.MEDIUM, isPlayer = true),
        SimDriverTemplate("MAR", "Raffaele Marciello", "Mercedes-AMG GT3", 0xFF00C853, 0.998f, TyreCompound.MEDIUM),
        SimDriverTemplate("BOR", "Mirko Bortolotti", "Lamborghini Huracán GT3", 0xFF76FF03, 0.996f, TyreCompound.MEDIUM),
        SimDriverTemplate("DRU", "Mattia Drudi", "Aston Martin Vantage GT3", 0xFF229971, 0.994f, TyreCompound.MEDIUM),
        SimDriverTemplate("PEP", "Jordan Pepper", "McLaren 720S GT3", 0xFFFF8000, 0.991f, TyreCompound.MEDIUM),
        SimDriverTemplate("FRI", "Kelvin van der Linde", "Audi R8 LMS GT3", 0xFFE10600, 0.989f, TyreCompound.MEDIUM),
        SimDriverTemplate("FAR", "Augusto Farfus", "BMW M4 GT3", 0xFF64C4FF, 0.986f, TyreCompound.MEDIUM),
    )

    fun observeSimulation(
        trackId: Int,
        udpPort: Int,
        localIp: String,
        stressTestMode: Boolean = false,
        simulatedGame: GameTitle = GameTitle.F1_25,
    ): Flow<LiveTelemetryState> = flow {
        val activeGame = if (simulatedGame == GameTitle.AUTO) GameTitle.F1_25 else simulatedGame
        val category = activeGame.defaultCategory
        val capabilities = GameCatalog.capabilitiesFor(activeGame)
        val templates = when (category) {
            CarCategory.FORMULA -> f1Templates
            CarCategory.GT_ENDURANCE -> gtTemplates
            CarCategory.STREET_TOURING -> gtTemplates.take(6)
        }
        val playerTemplateIdx = templates.indexOfFirst { it.isPlayer }.coerceAtLeast(0)
        val track = TrackCatalog.byId(trackId)
        val traceHistory = ArrayDeque<CarTelemetrySample>(120)
        val completedLaps = mutableListOf<CompletedLapSummary>()
        val lapSpeedPoints = mutableListOf<Int>()

        val baseLapMs = when (category) {
            CarCategory.FORMULA -> when (trackId) {
                11 -> 81_200L
                10 -> 106_400L
                6 -> 88_900L
                14 -> 91_300L
                else -> 71_850L
            }
            CarCategory.GT_ENDURANCE -> when (trackId) {
                10 -> 136_400L
                11 -> 108_200L
                else -> 94_500L
            }
            CarCategory.STREET_TOURING -> 102_800L
        }

        val maxSimSpeed = when (category) {
            CarCategory.FORMULA -> if (trackId == 11) 346 else 334
            CarCategory.GT_ENDURANCE -> if (trackId == 11) 288 else 276
            CarCategory.STREET_TOURING -> 296
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
                    topSpeedKmh = maxSimSpeed,
                    avgThrottlePct = 74,
                    maxTyreWearPct = lapIdx * 4,
                    speedTrace = List(40) { step ->
                        val phase = (step / 40f) * 2f * PI
                        val mid = if (category == CarCategory.FORMULA) 235 else 195
                        val amp = if (category == CarCategory.FORMULA) 95 else 78
                        (mid + amp * sin(phase) - 24 * cos(phase * 3f)).roundToInt().coerceIn(72, maxSimSpeed)
                    },
                )
            )
        }

        val driverProgress = FloatArray(templates.size) { idx ->
            (0.42f - idx * 0.024f + 1f) % 1f
        }
        val driverLaps = IntArray(templates.size) { 4 }
        val tyreWear = FloatArray(4) { if (stressTestMode) 68f else 22f }
        var engineTempC = when {
            stressTestMode -> 115f
            category == CarCategory.FORMULA -> 101f
            else -> 94f
        }
        var ersStorePct = 78f
        var fuelKg = when (category) {
            CarCategory.FORMULA -> 24.5f
            CarCategory.GT_ENDURANCE -> 46.0f
            CarCategory.STREET_TOURING -> 38.0f
        }
        var tick = 0L
        var packetCount = 0L
        var currentLapNum = 4
        var lastLapProgress = driverProgress[playerTemplateIdx]

        while (true) {
            val dtSec = 0.05f
            tick++
            packetCount += 5

            val paceScale = when (category) {
                CarCategory.FORMULA -> 0.0155f
                CarCategory.GT_ENDURANCE -> 0.0125f
                CarCategory.STREET_TOURING -> 0.0115f
            }
            for (i in templates.indices) {
                val baseSpeed = paceScale * templates[i].paceFactor
                val nextProg = driverProgress[i] + baseSpeed * dtSec
                if (nextProg >= 1f) {
                    driverLaps[i]++
                }
                driverProgress[i] = nextProg % 1f
            }

            val pProg = driverProgress[playerTemplateIdx]
            if (pProg < lastLapProgress) {
                val normalizedLap = baseLapMs + ((sin(currentLapNum.toDouble()) * 320).toLong()) + (tyreWear.max() * 14).toLong()
                val s1 = (normalizedLap * 0.258).toLong()
                val s2 = (normalizedLap * 0.504).toLong()
                val s3 = normalizedLap - s1 - s2
                val downsampled = if (lapSpeedPoints.size >= 40) {
                    val step = lapSpeedPoints.size / 40f
                    List(40) { idx -> lapSpeedPoints[(idx * step).toInt().coerceIn(0, lapSpeedPoints.lastIndex)] }
                } else {
                    List(40) { idx -> 185 + (sin(idx * 0.4) * 75).roundToInt() }
                }
                completedLaps.add(
                    CompletedLapSummary(
                        lapNumber = currentLapNum,
                        lapTimeMs = normalizedLap,
                        sector1Ms = s1,
                        sector2Ms = s2,
                        sector3Ms = s3,
                        tyreCompound = templates[playerTemplateIdx].compound,
                        topSpeedKmh = (lapSpeedPoints.maxOrNull() ?: maxSimSpeed),
                        avgThrottlePct = 73,
                        maxTyreWearPct = tyreWear.max().roundToInt(),
                        speedTrace = downsampled,
                    )
                )
                currentLapNum++
                lapSpeedPoints.clear()
            }
            lastLapProgress = pProg

            val cornerWave = (
                0.45 * sin(pProg * 2 * PI * 3.0) +
                    0.30 * cos(pProg * 2 * PI * 5.0 + 0.8) +
                    0.25 * sin(pProg * 2 * PI * 7.0 + 1.4)
                ).toFloat()

            val isStraight = cornerWave > 0.28f
            val isHeavyBraking = cornerWave in -0.55f..-0.22f && sin(pProg * 2 * PI * 8.0) > 0f

            val baseMidSpeed = when (category) {
                CarCategory.FORMULA -> 212f
                CarCategory.GT_ENDURANCE -> 178f
                CarCategory.STREET_TOURING -> 168f
            }
            val speedAmp = when (category) {
                CarCategory.FORMULA -> 118f
                CarCategory.GT_ENDURANCE -> 98f
                CarCategory.STREET_TOURING -> 105f
            }
            val speedKmh = (baseMidSpeed + cornerWave * speedAmp).roundToInt().coerceIn(64, maxSimSpeed)
            val throttle = when {
                isHeavyBraking -> 0f
                cornerWave > 0.1f -> (0.74f + (cornerWave * 0.26f)).coerceIn(0f, 1f)
                else -> (0.35f + cornerWave * 0.4f).coerceIn(0.08f, 0.92f)
            }
            val brake = when {
                isHeavyBraking -> (0.72f + abs(cornerWave) * 0.28f).coerceIn(0f, 1f)
                cornerWave < -0.35f -> 0.45f
                else -> 0f
            }
            val steer = (sin(pProg * 2 * PI * 6.0) * (1f - throttle * 0.65f)).toFloat().coerceIn(-1f, 1f)

            val gear = when (category) {
                CarCategory.FORMULA -> when {
                    speedKmh < 95 -> 2
                    speedKmh < 130 -> 3
                    speedKmh < 175 -> 4
                    speedKmh < 220 -> 5
                    speedKmh < 265 -> 6
                    speedKmh < 300 -> 7
                    else -> 8
                }
                else -> when {
                    speedKmh < 82 -> 2
                    speedKmh < 125 -> 3
                    speedKmh < 170 -> 4
                    speedKmh < 220 -> 5
                    else -> 6
                }
            }

            val rpm = when (category) {
                CarCategory.FORMULA -> (9600 + ((speedKmh % 42) / 42f) * 2650).roundToInt().coerceIn(8500, 12550)
                CarCategory.GT_ENDURANCE -> (5400 + ((speedKmh % 45) / 45f) * 2850).roundToInt().coerceIn(4800, 8450)
                CarCategory.STREET_TOURING -> (4600 + ((speedKmh % 48) / 48f) * 3200).roundToInt().coerceIn(3800, 7950)
            }
            val revLights = when (category) {
                CarCategory.FORMULA -> (((rpm - 9200) / 3300f) * 100f).roundToInt().coerceIn(10, 100)
                CarCategory.GT_ENDURANCE -> (((rpm - 5000) / 3450f) * 100f).roundToInt().coerceIn(10, 100)
                CarCategory.STREET_TOURING -> (((rpm - 4200) / 3750f) * 100f).roundToInt().coerceIn(10, 100)
            }

            val drsAvailable = capabilities.hasDrs && isStraight
            val drsActive = capabilities.hasDrs && isStraight && speedKmh > 255
            val gLat = steer * (speedKmh / (if (category == CarCategory.FORMULA) 105f else 135f))
            val gLon = if (brake > 0.2f) {
                -brake * (if (category == CarCategory.FORMULA) 4.8f else 2.2f)
            } else {
                throttle * (if (category == CarCategory.FORMULA) 1.45f else 0.95f)
            }
            val deltaMs = (sin(pProg * 2 * PI * 2.0) * 290f - 120f).roundToInt()
            val boostBar = if (capabilities.hasTurboBoost) {
                (throttle * 1.38f - 0.15f).coerceIn(-0.4f, 1.45f)
            } else {
                0f
            }
            val tcActive = capabilities.hasTractionControlAbs && throttle > 0.72f && abs(steer) > 0.45f
            val absActive = capabilities.hasTractionControlAbs && brake > 0.78f

            if (tick % 3L == 0L) lapSpeedPoints.add(speedKmh)

            val wearDelta = if (stressTestMode) 0.045f else 0.012f
            tyreWear[0] = (tyreWear[0] + wearDelta * 1.15f).coerceAtMost(96f)
            tyreWear[1] = (tyreWear[1] + wearDelta * 1.05f).coerceAtMost(96f)
            tyreWear[2] = (tyreWear[2] + wearDelta * 0.95f).coerceAtMost(94f)
            tyreWear[3] = (tyreWear[3] + wearDelta * 1.10f).coerceAtMost(96f)

            val stressBoost = if (stressTestMode) 12f else 0f
            val baseTyreTemp = when (category) {
                CarCategory.FORMULA -> 92
                CarCategory.GT_ENDURANCE -> 84
                CarCategory.STREET_TOURING -> 78
            }
            val surfBase = baseTyreTemp + (abs(gLat) * 4.5f).roundToInt() + stressBoost.roundToInt()
            val brakeBase = when (category) {
                CarCategory.FORMULA -> (410 + brake * 520f + (if (stressTestMode) 95f else 0f)).roundToInt()
                CarCategory.GT_ENDURANCE -> (360 + brake * 390f + (if (stressTestMode) 110f else 0f)).roundToInt()
                CarCategory.STREET_TOURING -> (290 + brake * 340f + (if (stressTestMode) 105f else 0f)).roundToInt()
            }
            engineTempC = (engineTempC + (if (throttle > 0.85f) 0.05f else -0.03f)).coerceIn(88f, 121f)
            ersStorePct = (ersStorePct + (if (brake > 0.2f) 0.35f else -0.18f)).coerceIn(12f, 100f)
            fuelKg = max(1.2f, fuelKg - 0.0014f)

            val psiBase = when (category) {
                CarCategory.FORMULA -> 23.2f
                CarCategory.GT_ENDURANCE -> 27.0f
                CarCategory.STREET_TOURING -> 30.8f
            }
            val slipRear = if (tcActive || (throttle > 0.8f && abs(steer) > 0.4f)) 0.14f else 0.03f
            val slipFront = if (absActive || brake > 0.65f) 0.12f else 0.02f
            val suspCompress = (abs(gLat) * 8f + brake * 14f)

            val wheels = listOf(
                WheelData("TE", surfBase + 2, surfBase + 5, psiBase - 0.2f, tyreWear[0], 0, if (surfBase > 103) 26 else 4, brakeBase - 35, slipRear, 42f + suspCompress),
                WheelData("TD", surfBase + 4, surfBase + 7, psiBase, tyreWear[1], 0, if (surfBase > 103) 31 else 6, brakeBase - 30, slipRear * 1.1f, 44f + suspCompress),
                WheelData("DE", surfBase - 1, surfBase + 2, psiBase + 0.3f, tyreWear[2], 0, if (surfBase > 103) 18 else 2, brakeBase + 25, slipFront, 38f + suspCompress * 1.3f),
                WheelData("DD", surfBase + 5, surfBase + 8, psiBase + 0.4f, tyreWear[3], 0, if (surfBase > 103) 34 else 8, brakeBase + 35, slipFront * 1.15f, 40f + suspCompress * 1.3f),
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
                turboBoostBar = boostBar,
                tcActive = tcActive,
                absActive = absActive,
                suggestedGear = if (isHeavyBraking) (gear - 1).coerceAtLeast(1) else 0,
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
                    pitStops = if (idx % 5 == 0) 1 else 0,
                    penaltiesSec = if (idx == 6) 5 else 0,
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
                    selectedGameMode = simulatedGame,
                    detectedGame = activeGame,
                    carCategory = category,
                    capabilities = capabilities,
                    connectionState = ConnectionState.SIMULATOR_ACTIVE,
                    udpPort = udpPort,
                    localIpAddress = localIp,
                    packetCount = packetCount,
                    sessionMeta = SessionMeta(
                        trackId = track.trackId,
                        trackName = track.name,
                        sessionType = when (category) {
                            CarCategory.FORMULA -> "Corrida ${activeGame.shortTag}"
                            CarCategory.GT_ENDURANCE -> "Stint GT3 • ${activeGame.shortTag}"
                            CarCategory.STREET_TOURING -> "Track Day • ${activeGame.shortTag}"
                        },
                        totalLaps = if (category == CarCategory.STREET_TOURING) 12 else 24,
                        currentLap = currentLapNum.coerceAtMost(24),
                        trackLengthM = track.lengthMeters,
                        weather = if (trackId == 10) "Nublado Parcial" else "Seco",
                        trackTempC = if (stressTestMode) 44 else 34,
                        airTempC = 24,
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
                            "Nuvens no Setor 2 • Simulador ${activeGame.displayName}"
                        } else {
                            "Simulador ${activeGame.displayName} (${category.label}) ativo"
                        },
                    ),
                    currentSample = sample,
                    traceBuffer = traceHistory.toList(),
                    carCondition = CarConditionState(
                        wheels = wheels,
                        engineTempC = engineTempC.roundToInt(),
                        oilTempC = engineTempC.roundToInt() + 2,
                        waterTempC = (engineTempC - 11f).roundToInt(),
                        oilPressureBar = 4.6f,
                        iceWearPct = 12,
                        mguKWearPct = 9,
                        turboWearPct = 10,
                        gearboxWearPct = 11,
                        frontWingLeftDamagePct = 0,
                        frontWingRightDamagePct = 0,
                        floorDamagePct = 2,
                        ersStorePct = ersStorePct.roundToInt(),
                        ersDeployMode = if (drsActive) "Ultrapassagem" else "Médio",
                        fuelInTankKg = fuelKg,
                        fuelCapacityLiters = if (category == CarCategory.FORMULA) 110f else 100f,
                        fuelRemainingLaps = (fuelKg / (if (category == CarCategory.FORMULA) 1.55f else 2.8f)),
                        fuelMix = if (category == CarCategory.FORMULA) "Padrão" else "Mapa 1 (Corrida)",
                        brakeBiasPct = if (category == CarCategory.FORMULA) 56 else 54,
                        tcLevel = if (capabilities.hasTractionControlAbs) 3 else 0,
                        absLevel = if (capabilities.hasTractionControlAbs) 2 else 0,
                        turboBoostBar = boostBar,
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
