package com.example.telemetry.udp

import com.example.telemetry.model.CompletedLapSummary
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.TyreCompound
import com.example.telemetry.model.WheelData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Decodes Forza Motorsport (FM7 311B, FM2023 331B) & Forza Horizon 5 (323/324B) + Sled (232B) Data Out UDP packets.
 */
object ForzaPacketParser {

    const val DEFAULT_PORT = 5300

    fun looksLikeForzaPacket(length: Int): Boolean =
        length == 232 || length == 311 || length == 323 || length == 324 || length == 331

    fun parsePacket(
        bytes: ByteArray,
        length: Int,
        world: F125PacketParser.UdpMutableWorld,
        preferredGame: GameTitle = GameTitle.AUTO,
    ): Boolean {
        if (!looksLikeForzaPacket(length)) return false
        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        val isRaceOn = buf.getInt(0) == 1
        val maxRpm = buf.getFloat(8).coerceAtLeast(1000f)
        val idleRpm = buf.getFloat(12).coerceAtLeast(500f)
        val currentRpm = buf.getFloat(16).coerceAtLeast(0f)

        val accelX = buf.getFloat(20) // lateral m/s^2
        val accelZ = buf.getFloat(28) // longitudinal m/s^2
        val velX = buf.getFloat(32)
        val velY = buf.getFloat(36)
        val velZ = buf.getFloat(40)

        // Forza wheel array order: [FL, FR, RL, RR]
        val normSusp = FloatArray(4) { i -> buf.getFloat(68 + i * 4).coerceIn(0f, 1.5f) }
        val slipRatio = FloatArray(4) { i -> buf.getFloat(84 + i * 4).coerceIn(-5f, 5f) }
        val suspMeters = FloatArray(4) { i -> buf.getFloat(196 + i * 4) * 1000f }
        val carOrdinal = buf.getInt(212)

        val sledSpeedMs = sqrt(velX * velX + velY * velY + velZ * velZ)

        val isHorizon = length == 323 || length == 324
        val detected = when {
            isHorizon -> GameTitle.FORZA_HORIZON_5
            preferredGame == GameTitle.FORZA_HORIZON_5 -> GameTitle.FORZA_HORIZON_5
            else -> GameTitle.FORZA_MOTORSPORT
        }
        world.detectedGame = detected
        world.activeCarsCount = 1

        var speedKmh = (sledSpeedMs * 3.6f).roundToInt().coerceIn(0, 520)
        var throttle = 0f
        var brake = 0f
        var clutch = 0
        var handbrake = 0f
        var steer = 0f
        var gear = 1
        var boostBar = 0f
        var fuelFraction = 0.8f
        var lapNumber = 1
        var racePos = 1
        var bestLapMs = 0L
        var lastLapMs = 0L
        var currentLapMs = 0L
        val tireTempsC = IntArray(4) { 82 }
        val tireWearPct = FloatArray(4) { 0f }

        if (length >= 311) {
            val dashBase = if (isHorizon) 244 else 232
            val posX = buf.getFloat(dashBase)
            val posZ = buf.getFloat(dashBase + 8)
            val dashSpeedMs = buf.getFloat(dashBase + 12).coerceAtLeast(0f)
            speedKmh = (dashSpeedMs * 3.6f).roundToInt().coerceIn(0, 520)

            // Dash tire temps are in Fahrenheit [FL, FR, RL, RR]
            for (i in 0 until 4) {
                val f = buf.getFloat(dashBase + 24 + i * 4)
                tireTempsC[i] = (((f - 32f) * 5f / 9f).roundToInt()).coerceIn(0, 220)
            }
            val boostPsi = buf.getFloat(dashBase + 40)
            boostBar = (boostPsi * 0.0689476f).coerceIn(-1f, 3.5f)
            fuelFraction = buf.getFloat(dashBase + 44).coerceIn(0f, 1f)
            bestLapMs = (buf.getFloat(dashBase + 52).coerceAtLeast(0f) * 1000f).toLong()
            lastLapMs = (buf.getFloat(dashBase + 56).coerceAtLeast(0f) * 1000f).toLong()
            currentLapMs = (buf.getFloat(dashBase + 60).coerceAtLeast(0f) * 1000f).toLong()

            lapNumber = ((buf.getShort(dashBase + 68).toInt() and 0xFFFF) + 1).coerceAtLeast(1)
            racePos = (buf.get(dashBase + 70).toInt() and 0xFF).coerceIn(1, 24)
            throttle = (buf.get(dashBase + 71).toInt() and 0xFF) / 255f
            brake = (buf.get(dashBase + 72).toInt() and 0xFF) / 255f
            clutch = (((buf.get(dashBase + 73).toInt() and 0xFF) / 255f) * 100f).roundToInt()
            handbrake = (buf.get(dashBase + 74).toInt() and 0xFF) / 255f
            val rawGear = buf.get(dashBase + 75).toInt() and 0xFF
            gear = when (rawGear) {
                0 -> -1 // Reverse
                1 -> 0  // Neutral (in Forza Dash 0=R, 1=1st or 0=R depending on game; map 1..10 safely)
                else -> rawGear - 1
            }.let { if (rawGear == 1 && speedKmh > 3) 1 else it }
            steer = (buf.get(dashBase + 76).toInt() / 127f).coerceIn(-1f, 1f)

            if (length == 331) {
                // FM 2023 Tire wear [FL, FR, RL, RR] at 311..326
                for (i in 0 until 4) {
                    tireWearPct[i] = (buf.getFloat(311 + i * 4) * 100f).coerceIn(0f, 100f)
                }
            }

            if (posX in -20000f..20000f && posZ in -20000f..20000f) {
                if (posX < world.worldBoundsMinX) world.worldBoundsMinX = posX
                if (posX > world.worldBoundsMaxX) world.worldBoundsMaxX = posX
                if (posZ < world.worldBoundsMinZ) world.worldBoundsMinZ = posZ
                if (posZ > world.worldBoundsMaxZ) world.worldBoundsMaxZ = posZ
                val spanX = maxOf(100f, world.worldBoundsMaxX - world.worldBoundsMinX)
                val spanZ = maxOf(100f, world.worldBoundsMaxZ - world.worldBoundsMinZ)
                world.drivers[0] = world.drivers[0].copy(
                    worldX = ((posX - world.worldBoundsMinX) / spanX).coerceIn(0.05f, 0.95f),
                    worldZ = ((posZ - world.worldBoundsMinZ) / spanZ).coerceIn(0.05f, 0.95f),
                )
            }
        }

        val revPct = (((currentRpm - idleRpm) / (maxRpm - idleRpm).coerceAtLeast(500f)) * 100f)
            .roundToInt().coerceIn(0, 100)

        if (isRaceOn) {
            if (speedKmh > world.currentLapMaxSpeed) world.currentLapMaxSpeed = speedKmh
            if (world.currentLapSpeedSamples.size < 200) world.currentLapSpeedSamples.add(speedKmh)

            if (lapNumber > world.lastRecordedLapNum && lastLapMs > 10_000L) {
                val s1 = (lastLapMs * 0.31).toLong()
                val s2 = (lastLapMs * 0.39).toLong()
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
                        avgThrottlePct = 72,
                        maxTyreWearPct = tireWearPct.max().roundToInt(),
                        speedTrace = F125PacketParser.downsampleSpeedTrace(world.currentLapSpeedSamples),
                    )
                )
                world.lastRecordedLapNum = lapNumber
                world.currentLapMaxSpeed = 0
                world.currentLapSpeedSamples.clear()
            }
        }

        world.playerSample = world.playerSample.copy(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh,
            throttle = throttle,
            brake = brake,
            steer = steer,
            clutch = clutch,
            gear = gear,
            engineRpm = currentRpm.roundToInt(),
            revLightsPct = revPct,
            gForceLat = (accelX / 9.81f).coerceIn(-5f, 5f),
            gForceLon = (accelZ / 9.81f).coerceIn(-5f, 5f),
            deltaMs = if (bestLapMs > 0L && lastLapMs > 0L) (lastLapMs - bestLapMs).toInt() else 0,
            turboBoostBar = boostBar,
            handbrake = handbrake,
        )

        // Forza order [FL=0, FR=1, RL=2, RR=3] -> app [RL, RR, FL, FR]
        world.carCondition = world.carCondition.copy(
            wheels = listOf(
                WheelData("TE", tireTempsC[2], tireTempsC[2] + 3, 28.5f, tireWearPct[2], slipRatio = slipRatio[2], suspensionTravelMm = if (suspMeters[2] != 0f) suspMeters[2] else normSusp[2] * 90f),
                WheelData("TD", tireTempsC[3], tireTempsC[3] + 3, 28.5f, tireWearPct[3], slipRatio = slipRatio[3], suspensionTravelMm = if (suspMeters[3] != 0f) suspMeters[3] else normSusp[3] * 90f),
                WheelData("DE", tireTempsC[0], tireTempsC[0] + 3, 29.2f, tireWearPct[0], slipRatio = slipRatio[0], suspensionTravelMm = if (suspMeters[0] != 0f) suspMeters[0] else normSusp[0] * 90f),
                WheelData("DD", tireTempsC[1], tireTempsC[1] + 3, 29.2f, tireWearPct[1], slipRatio = slipRatio[1], suspensionTravelMm = if (suspMeters[1] != 0f) suspMeters[1] else normSusp[1] * 90f),
            ),
            turboBoostBar = boostBar,
            fuelInTankKg = fuelFraction * 65f,
            fuelCapacityLiters = 65f,
            fuelRemainingLaps = (fuelFraction * 18f).coerceAtLeast(0.5f),
            fuelMix = if (carOrdinal > 0) "Carro #$carOrdinal" else detected.shortTag,
        )

        world.drivers[0] = world.drivers[0].copy(
            position = racePos,
            driverCode = detected.shortTag,
            driverName = "Piloto ${detected.shortTag}",
            teamName = if (carOrdinal > 0) "ID #$carOrdinal" else detected.displayName,
            teamColorHex = detected.badgeColorHex,
            isPlayer = true,
            currentLapNum = lapNumber,
            lastLapMs = lastLapMs,
            bestLapMs = bestLapMs,
        )

        world.sessionMeta = world.sessionMeta.copy(
            trackName = if (isHorizon) "Forza Horizon 5 (Data Out)" else "Forza Motorsport (Data Out)",
            sessionType = if (isHorizon) "Evento / Livre" else "Corrida",
            currentLap = lapNumber,
            totalLaps = lapNumber.coerceAtLeast(1),
            playerCurrentLapMs = currentLapMs,
            playerBestLapMs = bestLapMs,
            playerLastLapMs = lastLapMs,
            forecastSummary = if (isRaceOn) "Fluxo Data Out ativo (${length}B)" else "Jogo em pausa / menus",
        )
        return true
    }
}
