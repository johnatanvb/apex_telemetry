package com.example.telemetry.udp

import com.example.telemetry.model.CompletedLapSummary
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.WheelData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes Assetto Corsa (port 9996 Handshaker + 328-byte RTCarInfo) and
 * Assetto Corsa Competizione Broadcasting UDP (port 9000).
 */
object AssettoCorsaParsers {

    const val AC_PORT = 9996
    const val ACC_PORT = 9000

    private const val AC_OP_HANDSHAKE = 0
    private const val AC_OP_SUBSCRIBE_UPDATE = 1
    private const val AC_OP_DISMISS = 3

    fun buildAcHandshakePacket(operationId: Int): ByteArray =
        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2) // identifier: Android Phone
            .putInt(1) // version
            .putInt(operationId)
            .array()

    fun acHandshakeStart(): ByteArray = buildAcHandshakePacket(AC_OP_HANDSHAKE)
    fun acSubscribeUpdate(): ByteArray = buildAcHandshakePacket(AC_OP_SUBSCRIBE_UPDATE)
    fun acDismiss(): ByteArray = buildAcHandshakePacket(AC_OP_DISMISS)

    /**
     * Builds ACC Broadcasting Register command (msgType = 1, protocolVersion = 4).
     */
    fun buildAccRegisterCommand(
        displayName: String = "Apex Telemetry",
        connectionPassword: String = "asd",
        updateIntervalMs: Int = 50,
    ): ByteArray {
        val nameBytes = displayName.toByteArray(Charsets.UTF_8)
        val passBytes = connectionPassword.toByteArray(Charsets.UTF_8)
        val cmdPassBytes = ByteArray(0)
        val buf = ByteBuffer.allocate(1 + 1 + 2 + nameBytes.size + 2 + passBytes.size + 4 + 2 + cmdPassBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(1.toByte()) // REGISTER_COMMAND_APPLICATION
        buf.put(4.toByte()) // BROADCASTING_PROTOCOL_VERSION
        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)
        buf.putShort(passBytes.size.toShort())
        buf.put(passBytes)
        buf.putInt(updateIntervalMs)
        buf.putShort(cmdPassBytes.size.toShort())
        return buf.array()
    }

    fun parseAcPacket(
        bytes: ByteArray,
        length: Int,
        world: F125PacketParser.UdpMutableWorld,
    ): Boolean {
        // 1) HandshakerResponse (408 bytes) carries UTF-16LE carName / driverName / trackName
        if (length == 408) {
            val carName = readUtf16LeString(bytes, 0, 100).ifBlank { "Assetto Corsa" }
            val driverName = readUtf16LeString(bytes, 100, 100).ifBlank { "Piloto AC" }
            val trackName = readUtf16LeString(bytes, 208, 100).ifBlank { "Circuito AC" }
            world.detectedGame = GameTitle.ASSETTO_CORSA
            world.sessionMeta = world.sessionMeta.copy(trackName = trackName)
            world.drivers[0] = world.drivers[0].copy(
                driverName = driverName,
                driverCode = driverName.take(3).uppercase().ifBlank { "AC" },
                teamName = carName,
                teamColorHex = GameTitle.ASSETTO_CORSA.badgeColorHex,
            )
            return true
        }

        // 2) RTCarInfo (328 bytes)
        if (length < 328) return false
        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val sizeField = buf.getInt(4)
        if (sizeField != 328) return false

        world.detectedGame = GameTitle.ASSETTO_CORSA
        world.activeCarsCount = 1

        val speedKmh = buf.getFloat(8).roundToInt().coerceIn(0, 500)
        val isAbsEnabled = buf.get(20).toInt() != 0
        val isAbsInAction = buf.get(21).toInt() != 0
        val isTcInAction = buf.get(22).toInt() != 0
        val isTcEnabled = buf.get(23).toInt() != 0
        val isInPit = buf.get(24).toInt() != 0
        val isRevLimiter = buf.get(25).toInt() != 0

        val gLat = buf.getFloat(32).coerceIn(-5f, 5f)
        val gLon = buf.getFloat(36).coerceIn(-5f, 5f)
        val currentLapMs = buf.getInt(40).coerceAtLeast(0).toLong()
        val lastLapMs = buf.getInt(44).coerceAtLeast(0).toLong()
        val bestLapMs = buf.getInt(48).coerceAtLeast(0).toLong()
        val lapCount = (buf.getInt(52) + 1).coerceAtLeast(1)

        val throttle = buf.getFloat(56).coerceIn(0f, 1f)
        val brake = buf.getFloat(60).coerceIn(0f, 1f)
        val clutch = (buf.getFloat(64).coerceIn(0f, 1f) * 100f).roundToInt()
        val rpm = buf.getFloat(68).roundToInt().coerceIn(0, 22000)
        val steer = buf.getFloat(72).coerceIn(-1f, 1f)
        val rawGear = buf.getInt(76)
        val gear = when (rawGear) {
            0 -> -1
            1 -> 0
            else -> rawGear - 1
        }

        val slips = FloatArray(4) { i -> buf.getFloat(132 + i * 4).coerceIn(-2f, 2f) }
        val suspHeightMm = FloatArray(4) { i -> buf.getFloat(292 + i * 4) * 1000f }
        val lapProg = buf.getFloat(308).coerceIn(0f, 1f)
        val worldX = buf.getFloat(316)
        val worldZ = buf.getFloat(324)

        if (speedKmh > world.currentLapMaxSpeed) world.currentLapMaxSpeed = speedKmh
        if (world.currentLapSpeedSamples.size < 200) world.currentLapSpeedSamples.add(speedKmh)

        if (lapCount > world.lastRecordedLapNum && lastLapMs > 10_000L) {
            val s1 = (lastLapMs * 0.30).toLong()
            val s2 = (lastLapMs * 0.40).toLong()
            world.completedLaps.add(
                CompletedLapSummary(
                    lapNumber = world.lastRecordedLapNum,
                    lapTimeMs = lastLapMs,
                    sector1Ms = s1,
                    sector2Ms = s2,
                    sector3Ms = (lastLapMs - s1 - s2).coerceAtLeast(0L),
                    tyreCompound = world.drivers[0].tyreCompound,
                    topSpeedKmh = world.currentLapMaxSpeed.coerceAtLeast(speedKmh),
                    avgThrottlePct = 72,
                    maxTyreWearPct = 0,
                    speedTrace = F125PacketParser.downsampleSpeedTrace(world.currentLapSpeedSamples),
                )
            )
            world.lastRecordedLapNum = lapCount
            world.currentLapMaxSpeed = 0
            world.currentLapSpeedSamples.clear()
        }

        if (worldX in -10000f..10000f && worldZ in -10000f..10000f) {
            if (worldX < world.worldBoundsMinX) world.worldBoundsMinX = worldX
            if (worldX > world.worldBoundsMaxX) world.worldBoundsMaxX = worldX
            if (worldZ < world.worldBoundsMinZ) world.worldBoundsMinZ = worldZ
            if (worldZ > world.worldBoundsMaxZ) world.worldBoundsMaxZ = worldZ
            val spanX = maxOf(100f, world.worldBoundsMaxX - world.worldBoundsMinX)
            val spanZ = maxOf(100f, world.worldBoundsMaxZ - world.worldBoundsMinZ)
            world.drivers[0] = world.drivers[0].copy(
                worldX = ((worldX - world.worldBoundsMinX) / spanX).coerceIn(0.05f, 0.95f),
                worldZ = ((worldZ - world.worldBoundsMinZ) / spanZ).coerceIn(0.05f, 0.95f),
            )
        }

        world.playerSample = world.playerSample.copy(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh,
            throttle = throttle,
            brake = brake,
            steer = steer,
            clutch = clutch,
            gear = gear,
            engineRpm = rpm,
            revLightsPct = if (isRevLimiter) 100 else ((rpm / 8500f) * 100f).roundToInt().coerceIn(0, 100),
            gForceLat = gLat,
            gForceLon = gLon,
            lapProgress = lapProg,
            tcActive = isTcInAction,
            absActive = isAbsInAction,
            deltaMs = if (bestLapMs > 0L && lastLapMs > 0L) (lastLapMs - bestLapMs).toInt() else 0,
        )

        // AC wheels: [FL, FR, RL, RR]
        world.carCondition = world.carCondition.copy(
            wheels = listOf(
                WheelData("TE", 86, 89, 26.8f, slipRatio = slips[2], suspensionTravelMm = suspHeightMm[2]),
                WheelData("TD", 86, 89, 26.8f, slipRatio = slips[3], suspensionTravelMm = suspHeightMm[3]),
                WheelData("DE", 88, 91, 27.0f, slipRatio = slips[0], suspensionTravelMm = suspHeightMm[0]),
                WheelData("DD", 88, 91, 27.0f, slipRatio = slips[1], suspensionTravelMm = suspHeightMm[1]),
            ),
            tcLevel = if (isTcEnabled) 3 else 0,
            absLevel = if (isAbsEnabled) 2 else 0,
        )

        world.drivers[0] = world.drivers[0].copy(
            isPlayer = true,
            lapProgress = lapProg,
            currentLapNum = lapCount,
            lastLapMs = lastLapMs,
            bestLapMs = bestLapMs,
            pitStatus = if (isInPit) 1 else 0,
        )

        world.sessionMeta = world.sessionMeta.copy(
            currentLap = lapCount,
            playerCurrentLapMs = currentLapMs,
            playerLastLapMs = lastLapMs,
            playerBestLapMs = bestLapMs,
            forecastSummary = "Assetto Corsa Remote Telemetry (RTCarInfo)",
        )
        return true
    }

    /**
     * Parses ACC Broadcasting UDP Server responses:
     * msgType 1 = REGISTRATION_RESULT
     * msgType 2 = REALTIME_UPDATE
     * msgType 3 = REALTIME_CAR_UPDATE
     */
    fun parseAccPacket(
        bytes: ByteArray,
        length: Int,
        world: F125PacketParser.UdpMutableWorld,
    ): Boolean {
        if (length < 5) return false
        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val msgType = buf.get(0).toInt() and 0xFF
        when (msgType) {
            1 -> { // RegistrationResult
                world.detectedGame = GameTitle.ASSETTO_CORSA_COMPETIZIONE
                return true
            }
            2 -> { // RealtimeUpdate (session meta)
                if (length < 30) return false
                world.detectedGame = GameTitle.ASSETTO_CORSA_COMPETIZIONE
                val ambientTemp = buf.get(length - 5).toInt() and 0xFF
                val trackTemp = buf.get(length - 4).toInt() and 0xFF
                world.sessionMeta = world.sessionMeta.copy(
                    trackName = "ACC GT World Challenge",
                    airTempC = ambientTemp.coerceIn(0, 60),
                    trackTempC = trackTemp.coerceIn(0, 75),
                    flagStatus = FlagStatus.GREEN,
                    forecastSummary = "ACC Broadcasting UDP (Porta 9000)",
                )
                return true
            }
            3 -> { // RealtimeCarUpdate
                if (length < 24) return false
                world.detectedGame = GameTitle.ASSETTO_CORSA_COMPETIZIONE
                val carIndex = buf.getShort(1).toInt() and 0xFFFF
                val gear = (buf.get(5).toInt() and 0xFF) - 1 // ACC: 0=R, 1=N, 2=1st
                val pos = (buf.getShort(10).toInt() and 0xFFFF).coerceIn(1, 30)
                val speedKmh = if (length >= 22) (buf.getShort(length - 10).toInt() and 0xFFFF).coerceIn(0, 360) else world.playerSample.speedKmh
                val delta = if (length >= 18) buf.getInt(length - 14) else 0
                if (carIndex == 0 || carIndex == world.playerIndex) {
                    world.playerSample = world.playerSample.copy(
                        timestampMs = System.currentTimeMillis(),
                        speedKmh = speedKmh,
                        gear = gear,
                        deltaMs = delta,
                    )
                    world.drivers[0] = world.drivers[0].copy(
                        position = pos,
                        teamColorHex = GameTitle.ASSETTO_CORSA_COMPETIZIONE.badgeColorHex,
                    )
                }
                return true
            }
            else -> return false
        }
    }

    private fun readUtf16LeString(bytes: ByteArray, offset: Int, maxBytes: Int): String {
        val safeLen = minOf(maxBytes, bytes.size - offset).coerceAtLeast(0)
        if (safeLen <= 0) return ""
        return String(bytes, offset, safeLen, Charsets.UTF_16LE)
            .substringBefore('\u0000')
            .trim()
    }
}
