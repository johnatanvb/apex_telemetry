package com.example.telemetry.udp

import com.example.telemetry.model.CompletedLapSummary
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.TyreCompound
import com.example.telemetry.model.WheelData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-Kotlin Salsa20 stream cipher + Gran Turismo 7 UDP Packet A (296-byte) parser.
 * Key: first 32 ASCII bytes of "Simulator Interface Packet GT7 ver 0.0"
 * Nonce: iv1 = le32(packet[0x40..0x43]), iv2 = iv1 XOR 0xDEADBEAF -> [iv2_le32, iv1_le32]
 */
object Gt7Salsa20Decoder {

    const val GT7_RECEIVE_PORT = 33740
    const val GT7_HEARTBEAT_PORT = 33739
    const val PACKET_A_SIZE = 296
    const val MAGIC_G7S0 = 0x47375330 // "G7S0" in little-endian int32

    private val GT7_KEY: ByteArray = "Simulator Interface Packet GT7 ver 0.0"
        .toByteArray(Charsets.US_ASCII)
        .copyOf(32)

    private val SIGMA = intArrayOf(
        0x61707865, // "expa"
        0x3320646e, // "nd 3"
        0x79622d32, // "2-by"
        0x6b206574, // "te k"
    )

    fun heartbeatPayload(): ByteArray = byteArrayOf('A'.code.toByte())

    fun decryptPacketA(encrypted: ByteArray, length: Int = encrypted.size): ByteArray? {
        if (length < PACKET_A_SIZE) return null
        val oiv = ByteBuffer.wrap(encrypted, 0x40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val iv2 = oiv xor 0xDEADBEAF.toInt()
        val nonce = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(iv2)
            .putInt(oiv)
            .array()

        val decrypted = salsa20Xor(GT7_KEY, nonce, encrypted, PACKET_A_SIZE)
        val magic = ByteBuffer.wrap(decrypted, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return if (magic == MAGIC_G7S0) decrypted else null
    }

    /**
     * Encrypts plaintext with the same GT7 Salsa20 keystream (useful for testing & verification).
     */
    fun encryptForTest(plaintext: ByteArray, oiv: Int = 0x12345678): ByteArray {
        val copy = plaintext.copyOf(PACKET_A_SIZE)
        val iv2 = oiv xor 0xDEADBEAF.toInt()
        val nonce = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(iv2)
            .putInt(oiv)
            .array()
        val out = salsa20Xor(GT7_KEY, nonce, copy, PACKET_A_SIZE)
        // Store clear oiv bytes at 0x40..0x43 just like PS4/PS5 does
        ByteBuffer.wrap(out, 0x40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(oiv)
        return out
    }

    fun parseEncryptedPacket(
        rawBytes: ByteArray,
        length: Int,
        world: F125PacketParser.UdpMutableWorld,
    ): Boolean {
        val plain = decryptPacketA(rawBytes, length) ?: return false
        val buf = ByteBuffer.wrap(plain, 0, PACKET_A_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        world.detectedGame = GameTitle.GRAN_TURISMO_7
        world.activeCarsCount = 1

        val posX = buf.getFloat(0x04)
        val posZ = buf.getFloat(0x0C)
        val velX = buf.getFloat(0x10)
        val velZ = buf.getFloat(0x18)
        val rpm = buf.getFloat(0x3C).roundToInt().coerceIn(0, 25000)
        val fuelLiters = buf.getFloat(0x44).coerceAtLeast(0f)
        val fuelCap = buf.getFloat(0x48).coerceAtLeast(1f)
        val speedMs = buf.getFloat(0x4C).coerceAtLeast(0f)
        val boostRaw = buf.getFloat(0x50)
        val oilPressure = buf.getFloat(0x54).coerceIn(0f, 20f)
        val waterTemp = buf.getFloat(0x58).roundToInt().coerceIn(0, 180)
        val oilTemp = buf.getFloat(0x5C).roundToInt().coerceIn(0, 200)

        // GT7 TyreTemp order: FL, FR, RL, RR -> convert to app order [RL, RR, FL, FR]
        val tempFl = buf.getFloat(0x60).roundToInt().coerceIn(0, 220)
        val tempFr = buf.getFloat(0x64).roundToInt().coerceIn(0, 220)
        val tempRl = buf.getFloat(0x68).roundToInt().coerceIn(0, 220)
        val tempRr = buf.getFloat(0x6C).roundToInt().coerceIn(0, 220)

        val lapCount = (buf.getShort(0x74).toInt()).coerceAtLeast(1)
        val totalLaps = (buf.getShort(0x76).toInt()).coerceAtLeast(lapCount)
        val bestLapMs = buf.getInt(0x78).let { if (it > 0) it.toLong() else 0L }
        val lastLapMs = buf.getInt(0x7C).let { if (it > 0) it.toLong() else 0L }
        val startPos = (buf.getShort(0x84).toInt()).coerceIn(1, 22)
        val minAlertRpm = (buf.getShort(0x88).toInt() and 0xFFFF).coerceAtLeast(1000)
        val maxAlertRpm = (buf.getShort(0x8A).toInt() and 0xFFFF).coerceAtLeast(minAlertRpm + 500)
        val flags = buf.getShort(0x8E).toInt() and 0xFFFF
        val gearsByte = buf.get(0x90).toInt() and 0xFF
        val currentGearRaw = gearsByte and 0x0F
        val suggestedGear = (gearsByte ushr 4) and 0x0F
        val throttle = (buf.get(0x91).toInt() and 0xFF) / 255f
        val brake = (buf.get(0x92).toInt() and 0xFF) / 255f

        val wheelRps = FloatArray(4) { i -> buf.getFloat(0xA4 + i * 4) }
        val wheelRad = FloatArray(4) { i -> buf.getFloat(0xB4 + i * 4).coerceAtLeast(0.15f) }
        val suspHeightMm = FloatArray(4) { i -> buf.getFloat(0xC4 + i * 4) * 1000f }
        val clutch = (buf.getFloat(0xF4).coerceIn(0f, 1f) * 100f).roundToInt()
        val carCode = buf.getInt(0x124)

        val speedKmh = (speedMs * 3.6f).roundToInt().coerceIn(0, 520)
        val gear = when (currentGearRaw) {
            0 -> -1 // Reverse
            15 -> 0 // Neutral
            else -> currentGearRaw
        }
        val revPct = if (rpm <= minAlertRpm * 0.5f) {
            ((rpm.toFloat() / maxAlertRpm) * 60f).roundToInt().coerceIn(0, 100)
        } else {
            (((rpm - minAlertRpm * 0.5f) / (maxAlertRpm - minAlertRpm * 0.5f)) * 100f)
                .roundToInt().coerceIn(0, 100)
        }
        val boostBar = (boostRaw - 1.0f).coerceIn(-1f, 3.5f)
        val isRevLimit = (flags and (1 shl 5)) != 0
        val handbrakeActive = (flags and (1 shl 6)) != 0
        val inPit = (flags and (1 shl 3)) != 0

        // Approximate slip ratio from wheel ground speed vs vehicle speed
        val slips = FloatArray(4) { i ->
            val wheelSpeed = abs(wheelRps[i]) * wheelRad[i]
            if (speedMs > 2f) ((wheelSpeed - speedMs) / speedMs).coerceIn(-1f, 1f) else 0f
        }

        val gLat = (velX * 0.04f).coerceIn(-3.5f, 3.5f)
        val gLon = (if (brake > 0.1f) -brake * 2.1f else throttle * 1.1f)

        if (speedKmh > world.currentLapMaxSpeed) world.currentLapMaxSpeed = speedKmh
        if (world.currentLapSpeedSamples.size < 200) world.currentLapSpeedSamples.add(speedKmh)

        if (lapCount > world.lastRecordedLapNum && lastLapMs > 10_000L) {
            val s1 = (lastLapMs * 0.30).toLong()
            val s2 = (lastLapMs * 0.41).toLong()
            val s3 = (lastLapMs - s1 - s2).coerceAtLeast(0L)
            world.completedLaps.add(
                CompletedLapSummary(
                    lapNumber = world.lastRecordedLapNum,
                    lapTimeMs = lastLapMs,
                    sector1Ms = s1,
                    sector2Ms = s2,
                    sector3Ms = s3,
                    tyreCompound = TyreCompound.MEDIUM,
                    topSpeedKmh = world.currentLapMaxSpeed.coerceAtLeast(speedKmh),
                    avgThrottlePct = 70,
                    maxTyreWearPct = 0,
                    speedTrace = F125PacketParser.downsampleSpeedTrace(world.currentLapSpeedSamples),
                )
            )
            world.lastRecordedLapNum = lapCount
            world.currentLapMaxSpeed = 0
            world.currentLapSpeedSamples.clear()
        }

        // Track trail bounds for track map visualization
        if (posX in -8000f..8000f && posZ in -8000f..8000f) {
            if (posX < world.worldBoundsMinX) world.worldBoundsMinX = posX
            if (posX > world.worldBoundsMaxX) world.worldBoundsMaxX = posX
            if (posZ < world.worldBoundsMinZ) world.worldBoundsMinZ = posZ
            if (posZ > world.worldBoundsMaxZ) world.worldBoundsMaxZ = posZ
            val spanX = maxOf(100f, world.worldBoundsMaxX - world.worldBoundsMinX)
            val spanZ = maxOf(100f, world.worldBoundsMaxZ - world.worldBoundsMinZ)
            val nx = ((posX - world.worldBoundsMinX) / spanX).coerceIn(0.05f, 0.95f)
            val nz = ((posZ - world.worldBoundsMinZ) / spanZ).coerceIn(0.05f, 0.95f)
            world.drivers[0] = world.drivers[0].copy(worldX = nx, worldZ = nz)
        }

        val deltaMs = if (bestLapMs > 0L && lastLapMs > 0L) (lastLapMs - bestLapMs).toInt() else 0
        world.playerSample = world.playerSample.copy(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh,
            throttle = throttle,
            brake = brake,
            steer = (velX / (speedMs + 5f)).coerceIn(-1f, 1f),
            clutch = clutch,
            gear = gear,
            engineRpm = rpm,
            revLightsPct = if (isRevLimit) 100 else revPct,
            drsActive = false,
            drsAvailable = false,
            gForceLat = gLat,
            gForceLon = gLon,
            deltaMs = deltaMs,
            turboBoostBar = boostBar,
            handbrake = if (handbrakeActive) 1f else 0f,
            suggestedGear = suggestedGear,
        )

        // App wheel indexing: [RL, RR, FL, FR]
        world.carCondition = world.carCondition.copy(
            wheels = listOf(
                WheelData("TE", tempRl, tempRl + 3, 27.0f, slipRatio = slips[2], suspensionTravelMm = suspHeightMm[2]),
                WheelData("TD", tempRr, tempRr + 3, 27.0f, slipRatio = slips[3], suspensionTravelMm = suspHeightMm[3]),
                WheelData("DE", tempFl, tempFl + 3, 27.1f, slipRatio = slips[0], suspensionTravelMm = suspHeightMm[0]),
                WheelData("DD", tempFr, tempFr + 3, 27.1f, slipRatio = slips[1], suspensionTravelMm = suspHeightMm[1]),
            ),
            engineTempC = oilTemp,
            oilTempC = oilTemp,
            waterTempC = waterTemp,
            oilPressureBar = oilPressure,
            turboBoostBar = boostBar,
            fuelInTankKg = fuelLiters,
            fuelCapacityLiters = fuelCap,
            fuelRemainingLaps = ((fuelLiters / fuelCap) * 18f).coerceAtLeast(0.5f),
            fuelMix = if (carCode > 0) "Carro #$carCode" else "GT7",
            brakeBiasPct = 52,
        )

        world.drivers[0] = world.drivers[0].copy(
            position = startPos,
            driverCode = "GT7",
            driverName = "Piloto GT7",
            teamName = if (carCode > 0) "Carro #$carCode" else "Gran Turismo",
            teamColorHex = GameTitle.GRAN_TURISMO_7.badgeColorHex,
            isPlayer = true,
            currentLapNum = lapCount,
            lastLapMs = lastLapMs,
            bestLapMs = bestLapMs,
            pitStatus = if (inPit) 1 else 0,
        )

        world.sessionMeta = world.sessionMeta.copy(
            trackName = "Gran Turismo 7 (Ao Vivo)",
            sessionType = "Corrida GT7",
            currentLap = lapCount,
            totalLaps = totalLaps,
            playerBestLapMs = bestLapMs,
            playerLastLapMs = lastLapMs,
            flagStatus = FlagStatus.GREEN,
            forecastSummary = "Telemetria GT7 UDP criptografada via Salsa20",
        )

        return true
    }

    private fun rotl(v: Int, c: Int): Int = (v shl c) or (v ushr (32 - c))

    private fun salsa20Block(key: ByteArray, nonce: ByteArray, counter: Long): ByteArray {
        val kBuf = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
        val nBuf = ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN)

        val state = IntArray(16)
        state[0] = SIGMA[0]
        state[1] = kBuf.getInt(0)
        state[2] = kBuf.getInt(4)
        state[3] = kBuf.getInt(8)
        state[4] = kBuf.getInt(12)
        state[5] = SIGMA[1]
        state[6] = nBuf.getInt(0)
        state[7] = nBuf.getInt(4)
        state[8] = counter.toInt()
        state[9] = (counter ushr 32).toInt()
        state[10] = SIGMA[2]
        state[11] = kBuf.getInt(16)
        state[12] = kBuf.getInt(20)
        state[13] = kBuf.getInt(24)
        state[14] = kBuf.getInt(28)
        state[15] = SIGMA[3]

        val x = state.copyOf()
        repeat(10) {
            // Column rounds
            x[4] = x[4] xor rotl(x[0] + x[12], 7)
            x[8] = x[8] xor rotl(x[4] + x[0], 9)
            x[12] = x[12] xor rotl(x[8] + x[4], 13)
            x[0] = x[0] xor rotl(x[12] + x[8], 18)

            x[9] = x[9] xor rotl(x[5] + x[1], 7)
            x[13] = x[13] xor rotl(x[9] + x[5], 9)
            x[1] = x[1] xor rotl(x[13] + x[9], 13)
            x[5] = x[5] xor rotl(x[1] + x[13], 18)

            x[14] = x[14] xor rotl(x[10] + x[6], 7)
            x[2] = x[2] xor rotl(x[14] + x[10], 9)
            x[6] = x[6] xor rotl(x[2] + x[14], 13)
            x[10] = x[10] xor rotl(x[6] + x[2], 18)

            x[3] = x[3] xor rotl(x[15] + x[11], 7)
            x[7] = x[7] xor rotl(x[3] + x[15], 9)
            x[11] = x[11] xor rotl(x[7] + x[3], 13)
            x[15] = x[15] xor rotl(x[11] + x[7], 18)

            // Row rounds
            x[1] = x[1] xor rotl(x[0] + x[3], 7)
            x[2] = x[2] xor rotl(x[1] + x[0], 9)
            x[3] = x[3] xor rotl(x[2] + x[1], 13)
            x[0] = x[0] xor rotl(x[3] + x[2], 18)

            x[6] = x[6] xor rotl(x[5] + x[4], 7)
            x[7] = x[7] xor rotl(x[6] + x[5], 9)
            x[4] = x[4] xor rotl(x[7] + x[6], 13)
            x[5] = x[5] xor rotl(x[4] + x[7], 18)

            x[11] = x[11] xor rotl(x[10] + x[9], 7)
            x[8] = x[8] xor rotl(x[11] + x[10], 9)
            x[9] = x[9] xor rotl(x[8] + x[11], 13)
            x[10] = x[10] xor rotl(x[9] + x[8], 18)

            x[12] = x[12] xor rotl(x[15] + x[14], 7)
            x[13] = x[13] xor rotl(x[12] + x[15], 9)
            x[14] = x[14] xor rotl(x[13] + x[12], 13)
            x[15] = x[15] xor rotl(x[14] + x[13], 18)
        }

        val out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16) {
            out.putInt(x[i] + state[i])
        }
        return out.array()
    }

    private fun salsa20Xor(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        length: Int,
    ): ByteArray {
        val output = ByteArray(length)
        var offset = 0
        var blockCounter = 0L
        while (offset < length) {
            val block = salsa20Block(key, nonce, blockCounter++)
            val toCopy = minOf(64, length - offset)
            for (i in 0 until toCopy) {
                output[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += toCopy
        }
        return output
    }
}
