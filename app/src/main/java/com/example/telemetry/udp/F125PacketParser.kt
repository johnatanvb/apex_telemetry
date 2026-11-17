package com.example.telemetry.udp

import com.example.telemetry.data.TrackCatalog
import com.example.telemetry.model.CarConditionState
import com.example.telemetry.model.CarTelemetrySample
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GridCarEntry
import com.example.telemetry.model.SectorStatus
import com.example.telemetry.model.SessionMeta
import com.example.telemetry.model.TyreCompound
import com.example.telemetry.model.WheelData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Decodes EA Sports F1 25 Little-Endian UDP telemetry packets (29-byte header).
 */
object F125PacketParser {

    private const val HEADER_SIZE = 29
    private const val MAX_CARS = 22

    private val teamNames = mapOf(
        0 to ("Mercedes" to 0xFF00D2BE),
        1 to ("Ferrari" to 0xFFDC0000),
        2 to ("Red Bull Racing" to 0xFF3671C6),
        3 to ("Williams" to 0xFF64C4FF),
        4 to ("Aston Martin" to 0xFF229971),
        5 to ("Alpine" to 0xFFFF87BC),
        6 to ("Racing Bulls" to 0xFF6692FF),
        7 to ("Haas" to 0xFFB6BABD),
        8 to ("McLaren" to 0xFFFF8000),
        9 to ("Sauber" to 0xFF52E252),
    )

    data class PacketHeader(
        val packetFormat: Int,
        val gameYear: Int,
        val packetId: Int,
        val sessionTime: Float,
        val playerCarIndex: Int,
    )

    class UdpMutableWorld {
        var sessionMeta = SessionMeta()
        var playerSample = CarTelemetrySample()
        var carCondition = CarConditionState()
        val drivers = Array(MAX_CARS) { idx ->
            GridCarEntry(
                carIndex = idx,
                position = idx + 1,
                gridPosition = idx + 1,
                driverCode = "P${idx + 1}",
                driverName = "Piloto ${idx + 1}",
                teamName = "F1 Team",
                teamColorHex = 0xFF888899,
                isPlayer = idx == 0,
                lapProgress = 0f,
                worldX = 0.5f,
                worldZ = 0.5f,
                currentLapNum = 1,
                lastLapMs = 0L,
                bestLapMs = 0L,
                gapToLeaderMs = 0L,
                intervalMs = 0L,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = 0,
                pitStatus = 0,
                pitStops = 0,
                penaltiesSec = 0,
                warnings = 0,
            )
        }
        var activeCarsCount: Int = 20
        var playerIndex: Int = 0
        var worldBoundsMinX = -900f
        var worldBoundsMaxX = 900f
        var worldBoundsMinZ = -900f
        var worldBoundsMaxZ = 900f
    }

    fun parsePacket(bytes: ByteArray, length: Int, world: UdpMutableWorld): Boolean {
        if (length < HEADER_SIZE) return false
        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val packetFormat = buf.getShort(0).toInt() and 0xFFFF
        val gameYear = buf.get(2).toInt() and 0xFF
        val packetId = buf.get(6).toInt() and 0xFF
        val sessionTime = buf.getFloat(15)
        val playerCarIndex = (buf.get(27).toInt() and 0xFF).coerceIn(0, MAX_CARS - 1)
        world.playerIndex = playerCarIndex

        if (packetFormat < 2023) return false

        val header = PacketHeader(packetFormat, gameYear, packetId, sessionTime, playerCarIndex)
        return when (packetId) {
            0 -> parseMotion(buf, length, world, header)
            1 -> parseSession(buf, length, world)
            2 -> parseLapData(buf, length, world, header)
            4 -> parseParticipants(buf, length, world)
            6 -> parseCarTelemetry(buf, length, world, header)
            7 -> parseCarStatus(buf, length, world, header)
            10 -> parseCarDamage(buf, length, world, header)
            else -> false
        }
    }

    private fun parseMotion(
        buf: ByteBuffer,
        length: Int,
        world: UdpMutableWorld,
        header: PacketHeader,
    ): Boolean {
        val stride = 60
        if (length < HEADER_SIZE + stride * MAX_CARS) return false
        for (i in 0 until MAX_CARS) {
            val base = HEADER_SIZE + i * stride
            val wx = buf.getFloat(base)
            val wz = buf.getFloat(base + 8)
            if (wx in -5000f..5000f && wz in -5000f..5000f) {
                if (wx < world.worldBoundsMinX) world.worldBoundsMinX = wx
                if (wx > world.worldBoundsMaxX) world.worldBoundsMaxX = wx
                if (wz < world.worldBoundsMinZ) world.worldBoundsMinZ = wz
                if (wz > world.worldBoundsMaxZ) world.worldBoundsMaxZ = wz
                val spanX = max(100f, world.worldBoundsMaxX - world.worldBoundsMinX)
                val spanZ = max(100f, world.worldBoundsMaxZ - world.worldBoundsMinZ)
                val nx = ((wx - world.worldBoundsMinX) / spanX).coerceIn(0.05f, 0.95f)
                val nz = ((wz - world.worldBoundsMinZ) / spanZ).coerceIn(0.05f, 0.95f)
                world.drivers[i] = world.drivers[i].copy(
                    worldX = nx,
                    worldZ = nz,
                    isPlayer = i == header.playerCarIndex,
                )
            }
            if (i == header.playerCarIndex) {
                val gLat = buf.getFloat(base + 36)
                val gLon = buf.getFloat(base + 40)
                world.playerSample = world.playerSample.copy(
                    gForceLat = gLat.coerceIn(-7f, 7f),
                    gForceLon = gLon.coerceIn(-7f, 7f),
                )
            }
        }
        return true
    }

    private fun parseSession(buf: ByteBuffer, length: Int, world: UdpMutableWorld): Boolean {
        if (length < HEADER_SIZE + 12) return false
        val weatherId = buf.get(HEADER_SIZE).toInt() and 0xFF
        val trackTemp = buf.get(HEADER_SIZE + 1).toInt()
        val airTemp = buf.get(HEADER_SIZE + 2).toInt()
        val totalLaps = (buf.get(HEADER_SIZE + 3).toInt() and 0xFF).coerceAtLeast(1)
        val trackLength = (buf.getShort(HEADER_SIZE + 4).toInt() and 0xFFFF).coerceAtLeast(1000)
        val sessionTypeId = buf.get(HEADER_SIZE + 6).toInt() and 0xFF
        val trackId = buf.get(HEADER_SIZE + 7).toInt()
        val safetyCar = if (length > HEADER_SIZE + 133) buf.get(HEADER_SIZE + 133).toInt() and 0xFF else 0

        val weatherText = when (weatherId) {
            0 -> "Limpo"
            1 -> "Nublado Parcial"
            2 -> "Nublado"
            3 -> "Chuva Leve"
            4 -> "Chuva Forte"
            5 -> "Tempestade"
            else -> "Seco"
        }
        val sessionName = when (sessionTypeId) {
            in 1..4 -> "Treino Livre"
            in 5..9 -> "Classificação"
            10, 11 -> "Sprint"
            15 -> "Tomada de Tempo"
            else -> "Corrida"
        }
        val flag = when (safetyCar) {
            1 -> FlagStatus.SAFETY_CAR
            2 -> FlagStatus.VSC
            3 -> FlagStatus.YELLOW
            else -> FlagStatus.GREEN
        }
        val track = TrackCatalog.byId(trackId)
        world.sessionMeta = world.sessionMeta.copy(
            trackId = trackId,
            trackName = track.name,
            sessionType = sessionName,
            totalLaps = totalLaps,
            trackLengthM = trackLength,
            weather = weatherText,
            trackTempC = trackTemp,
            airTempC = airTemp,
            flagStatus = flag,
        )
        return true
    }

    private fun parseLapData(
        buf: ByteBuffer,
        length: Int,
        world: UdpMutableWorld,
        header: PacketHeader,
    ): Boolean {
        val stride = 57
        if (length < HEADER_SIZE + stride * MAX_CARS) return false
        val trackLen = world.sessionMeta.trackLengthM.coerceAtLeast(1000).toFloat()
        var bestSessionLap = Long.MAX_VALUE

        for (i in 0 until MAX_CARS) {
            val base = HEADER_SIZE + i * stride
            val lastLapMs = buf.getInt(base).toLong() and 0xFFFFFFFFL
            val currentLapMs = buf.getInt(base + 4).toLong() and 0xFFFFFFFFL
            val s1MsPart = buf.getShort(base + 8).toInt() and 0xFFFF
            val s1MinPart = buf.get(base + 10).toInt() and 0xFF
            val s2MsPart = buf.getShort(base + 11).toInt() and 0xFFFF
            val s2MinPart = buf.get(base + 13).toInt() and 0xFF
            val deltaFrontMs = buf.getShort(base + 14).toInt() and 0xFFFF
            val deltaLeaderMs = buf.getShort(base + 17).toInt() and 0xFFFF
            val lapDistance = buf.getFloat(base + 20)
            val carPos = (buf.get(base + 28).toInt() and 0xFF).coerceIn(1, MAX_CARS)
            val currentLapNum = (buf.get(base + 29).toInt() and 0xFF).coerceAtLeast(1)
            val pitStatus = buf.get(base + 30).toInt() and 0xFF
            val pitStops = buf.get(base + 31).toInt() and 0xFF
            val penalties = buf.get(base + 34).toInt() and 0xFF
            val warnings = buf.get(base + 35).toInt() and 0xFF
            val gridPos = (buf.get(base + 39).toInt() and 0xFF).coerceIn(1, MAX_CARS)

            val prevBest = world.drivers[i].bestLapMs
            val updatedBest = when {
                lastLapMs > 10_000L && (prevBest == 0L || lastLapMs < prevBest) -> lastLapMs
                else -> prevBest
            }
            if (updatedBest in 10_000L until bestSessionLap) {
                bestSessionLap = updatedBest
            }

            val progress = ((lapDistance / trackLen) % 1f + 1f) % 1f
            world.drivers[i] = world.drivers[i].copy(
                position = carPos,
                gridPosition = gridPos,
                isPlayer = i == header.playerCarIndex,
                lapProgress = progress,
                currentLapNum = currentLapNum,
                lastLapMs = lastLapMs,
                bestLapMs = updatedBest,
                gapToLeaderMs = deltaLeaderMs.toLong(),
                intervalMs = deltaFrontMs.toLong(),
                pitStatus = pitStatus,
                pitStops = pitStops,
                penaltiesSec = penalties,
                warnings = warnings,
            )

            if (i == header.playerCarIndex) {
                val s1Total = s1MinPart * 60_000L + s1MsPart
                val s2Total = s2MinPart * 60_000L + s2MsPart
                world.sessionMeta = world.sessionMeta.copy(
                    currentLap = currentLapNum,
                    playerCurrentLapMs = currentLapMs,
                    playerLastLapMs = if (lastLapMs > 0L) lastLapMs else world.sessionMeta.playerLastLapMs,
                    playerBestLapMs = updatedBest,
                    sector1Ms = if (s1Total > 0L) s1Total else world.sessionMeta.sector1Ms,
                    sector2Ms = if (s2Total > 0L) s2Total else world.sessionMeta.sector2Ms,
                    sector1Status = if (s1Total > 0L) SectorStatus.GREEN else SectorStatus.NONE,
                    sector2Status = if (s2Total > 0L) SectorStatus.YELLOW else SectorStatus.NONE,
                )
                world.playerSample = world.playerSample.copy(lapProgress = progress)
            }
        }
        if (bestSessionLap < Long.MAX_VALUE) {
            world.sessionMeta = world.sessionMeta.copy(sessionBestLapMs = bestSessionLap)
        }
        return true
    }

    private fun parseParticipants(buf: ByteBuffer, length: Int, world: UdpMutableWorld): Boolean {
        if (length < HEADER_SIZE + 1) return false
        val numActive = (buf.get(HEADER_SIZE).toInt() and 0xFF).coerceIn(1, MAX_CARS)
        world.activeCarsCount = numActive
        val stride = 57
        val start = HEADER_SIZE + 1
        if (length < start + stride * numActive) return false

        for (i in 0 until numActive) {
            val base = start + i * stride
            val teamId = buf.get(base + 2).toInt() and 0xFF
            val nameBytes = ByteArray(32)
            for (b in 0 until 32) {
                nameBytes[b] = buf.get(base + 7 + b)
            }
            val rawName = String(nameBytes, Charsets.UTF_8).substringBefore('\u0000').trim()
            val driverName = rawName.ifBlank { "Piloto ${i + 1}" }
            val code = driverName
                .split(" ")
                .lastOrNull()
                ?.take(3)
                ?.uppercase()
                ?.ifBlank { "DRV" } ?: "DRV"
            val (teamName, teamColor) = teamNames[teamId] ?: ("F1 Team" to 0xFF9095B0)
            world.drivers[i] = world.drivers[i].copy(
                driverName = driverName,
                driverCode = code,
                teamName = teamName,
                teamColorHex = teamColor,
            )
        }
        return true
    }

    private fun parseCarTelemetry(
        buf: ByteBuffer,
        length: Int,
        world: UdpMutableWorld,
        header: PacketHeader,
    ): Boolean {
        val stride = 60
        if (length < HEADER_SIZE + stride * MAX_CARS) return false
        val base = HEADER_SIZE + header.playerCarIndex * stride

        val speed = buf.getShort(base).toInt() and 0xFFFF
        val throttle = buf.getFloat(base + 2).coerceIn(0f, 1f)
        val steer = buf.getFloat(base + 6).coerceIn(-1f, 1f)
        val brake = buf.getFloat(base + 10).coerceIn(0f, 1f)
        val clutch = (buf.get(base + 14).toInt() and 0xFF).coerceIn(0, 100)
        val gear = buf.get(base + 15).toInt()
        val rpm = buf.getShort(base + 16).toInt() and 0xFFFF
        val drs = (buf.get(base + 18).toInt() and 0xFF) == 1
        val revLights = (buf.get(base + 19).toInt() and 0xFF).coerceIn(0, 100)

        val brakeTemps = IntArray(4) { idx -> buf.getShort(base + 22 + idx * 2).toInt() and 0xFFFF }
        val tyreSurfTemps = IntArray(4) { idx -> buf.get(base + 30 + idx).toInt() and 0xFF }
        val tyreInnerTemps = IntArray(4) { idx -> buf.get(base + 34 + idx).toInt() and 0xFF }
        val engineTemp = buf.getShort(base + 38).toInt() and 0xFFFF
        val tyrePressures = FloatArray(4) { idx -> buf.getFloat(base + 40 + idx * 4) }

        world.playerSample = world.playerSample.copy(
            timestampMs = (header.sessionTime * 1000f).toLong(),
            speedKmh = speed,
            throttle = throttle,
            brake = brake,
            steer = steer,
            clutch = clutch,
            gear = gear,
            engineRpm = rpm,
            revLightsPct = revLights,
            drsActive = drs,
        )

        val updatedWheels = world.carCondition.wheels.mapIndexed { idx, w ->
            w.copy(
                tempSurfaceC = tyreSurfTemps[idx],
                tempInnerC = tyreInnerTemps[idx],
                pressurePsi = tyrePressures[idx],
                brakeTempC = brakeTemps[idx],
            )
        }
        world.carCondition = world.carCondition.copy(
            wheels = updatedWheels,
            engineTempC = engineTemp,
        )
        return true
    }

    private fun parseCarStatus(
        buf: ByteBuffer,
        length: Int,
        world: UdpMutableWorld,
        header: PacketHeader,
    ): Boolean {
        val stride = 55
        if (length < HEADER_SIZE + stride * MAX_CARS) return false
        for (i in 0 until MAX_CARS) {
            val base = HEADER_SIZE + i * stride
            val visualCompound = buf.get(base + 25).toInt() and 0xFF
            val tyreAge = buf.get(base + 26).toInt() and 0xFF
            world.drivers[i] = world.drivers[i].copy(
                tyreCompound = TyreCompound.fromF1VisualCompound(visualCompound),
                tyreAgeLaps = tyreAge,
            )
            if (i == header.playerCarIndex) {
                val fuelInTank = buf.getFloat(base + 5).coerceAtLeast(0f)
                val fuelRemLaps = buf.getFloat(base + 13)
                val fuelMixId = buf.get(base + 3).toInt() and 0xFF
                val brakeBias = (buf.get(base + 4).toInt() and 0xFF).coerceIn(45, 70)
                val drsAllowed = (buf.get(base + 21).toInt() and 0xFF) == 1
                val ersEnergyJ = buf.getFloat(base + 36).coerceIn(0f, 4_000_000f)
                val ersModeId = buf.get(base + 40).toInt() and 0xFF
                val ersMode = when (ersModeId) {
                    0 -> "Nenhum"
                    1 -> "Médio"
                    2 -> "Quente"
                    3 -> "Ultrapassagem"
                    else -> "Médio"
                }
                val fuelMix = when (fuelMixId) {
                    0 -> "Pobre"
                    1 -> "Padrão"
                    2 -> "Rico"
                    3 -> "Máximo"
                    else -> "Padrão"
                }
                world.playerSample = world.playerSample.copy(drsAvailable = drsAllowed)
                world.carCondition = world.carCondition.copy(
                    fuelInTankKg = fuelInTank,
                    fuelRemainingLaps = fuelRemLaps,
                    fuelMix = fuelMix,
                    brakeBiasPct = brakeBias,
                    ersStorePct = ((ersEnergyJ / 4_000_000f) * 100f).toInt().coerceIn(0, 100),
                    ersDeployMode = ersMode,
                )
            }
        }
        return true
    }

    private fun parseCarDamage(
        buf: ByteBuffer,
        length: Int,
        world: UdpMutableWorld,
        header: PacketHeader,
    ): Boolean {
        val stride = 46
        if (length < HEADER_SIZE + stride * MAX_CARS) return false
        val base = HEADER_SIZE + header.playerCarIndex * stride
        val wear = FloatArray(4) { idx -> buf.getFloat(base + idx * 4).coerceIn(0f, 100f) }
        val tyreDamage = IntArray(4) { idx -> buf.get(base + 16 + idx).toInt() and 0xFF }
        val blisters = IntArray(4) { idx -> buf.get(base + 24 + idx).toInt() and 0xFF }
        val wingLeft = buf.get(base + 28).toInt() and 0xFF
        val wingRight = buf.get(base + 29).toInt() and 0xFF
        val floor = buf.get(base + 32).toInt() and 0xFF
        val gearbox = buf.get(base + 36).toInt() and 0xFF
        val iceWear = buf.get(base + 41).toInt() and 0xFF
        val mgukWear = buf.get(base + 42).toInt() and 0xFF
        val turboWear = buf.get(base + 40).toInt() and 0xFF

        val updatedWheels = world.carCondition.wheels.mapIndexed { idx, w ->
            w.copy(
                wearPct = wear[idx],
                damagePct = tyreDamage[idx],
                blistersPct = blisters[idx],
            )
        }
        world.carCondition = world.carCondition.copy(
            wheels = updatedWheels,
            frontWingLeftDamagePct = wingLeft,
            frontWingRightDamagePct = wingRight,
            floorDamagePct = floor,
            gearboxWearPct = gearbox,
            iceWearPct = iceWear,
            mguKWearPct = mgukWear,
            turboWearPct = turboWear,
        )
        return true
    }
}
