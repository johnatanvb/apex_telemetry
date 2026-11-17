package com.example.telemetry.udp

import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.WheelData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes Slightly Mad Studios (SMS) UDP v2 telemetry (port 5606) used by Project CARS 2 & Automobilista 2.
 */
object SmsPcars2Parser {

    const val DEFAULT_PORT = 5606
    private const val PACKET_BASE_SIZE = 12
    private const val TELEMETRY_MIN_SIZE = 488

    fun looksLikeSmsV2Packet(bytes: ByteArray, length: Int): Boolean {
        if (length < PACKET_BASE_SIZE) return false
        val packetType = bytes[10].toInt() and 0xFF
        val packetVersion = bytes[11].toInt() and 0xFF
        // SMS UDP v2: mPacketVersion == 2 and mPacketType in 0..8
        return packetVersion == 2 && packetType in 0..8
    }

    fun parsePacket(
        bytes: ByteArray,
        length: Int,
        world: F125PacketParser.UdpMutableWorld,
        preferredGame: GameTitle = GameTitle.AUTOMOBILISTA_2,
    ): Boolean {
        if (!looksLikeSmsV2Packet(bytes, length)) return false
        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val packetType = buf.get(10).toInt() and 0xFF

        val targetGame = if (preferredGame == GameTitle.PROJECT_CARS_2) {
            GameTitle.PROJECT_CARS_2
        } else {
            GameTitle.AUTOMOBILISTA_2
        }
        world.detectedGame = targetGame

        if (packetType != 0 || length < TELEMETRY_MIN_SIZE) {
            // Other SMS v2 packets (race data, participants, timings) confirm stream liveliness
            return true
        }

        val throttle = (buf.get(30).toInt() and 0xFF) / 255f
        val brake = (buf.get(29).toInt() and 0xFF) / 255f
        val clutch = (((buf.get(31).toInt() and 0xFF) / 255f) * 100f).roundToInt()
        val steer = (buf.get(32).toInt() / 127f).coerceIn(-1f, 1f)
        val rawGear = buf.get(33).toInt() // 0=N, 1..n, -1=R
        val oilTemp = buf.getShort(18).toInt().coerceIn(0, 200)
        val oilPressureBar = (buf.getShort(20).toInt() and 0xFFFF) / 100f
        val waterTemp = buf.getShort(22).toInt().coerceIn(0, 180)
        val fuelCapacity = (buf.get(28).toInt() and 0xFF).toFloat().coerceAtLeast(10f)
        val speedMs = buf.getFloat(36).coerceAtLeast(0f)
        val rpm = buf.getFloat(40).roundToInt().coerceIn(0, 22000)
        val maxRpm = buf.getFloat(44).coerceAtLeast(4000f)
        val fuelLevel = buf.getFloat(48).coerceAtLeast(0f)
        val brakeBiasPct = (buf.getFloat(52) * 100f).roundToInt().coerceIn(40, 75)

        val gLat = (buf.getFloat(104) / 9.81f).coerceIn(-6f, 6f)
        val gLon = (buf.getFloat(112) / 9.81f).coerceIn(-6f, 6f)

        // Wheel arrays [FL=0, FR=1, RL=2, RR=3]
        val tyreTempsC = IntArray(4) { i -> buf.getFloat(140 + i * 4).roundToInt().coerceIn(0, 220) }
        val tyreWearPct = FloatArray(4) { i -> (buf.getFloat(156 + i * 4) * 100f).coerceIn(0f, 100f) }
        val brakeTempsC = IntArray(4) { i -> (buf.getShort(204 + i * 2).toInt()).coerceIn(0, 1400) }
        val suspTravelMm = FloatArray(4) { i -> buf.getFloat(428 + i * 4) * 1000f }
        // Air pressure in kPa -> convert to PSI (* 0.145038)
        val airPsi = FloatArray(4) { i ->
            val raw = (buf.getShort(460 + i * 2).toInt() and 0xFFFF).toFloat()
            if (raw > 50f) (raw * 0.145038f).coerceIn(15f, 45f) else 26.8f
        }

        val aeroDamagePct = ((buf.get(487).toInt() and 0xFF) / 255f * 100f).roundToInt()
        val engineDamagePct = if (length > 488) {
            ((buf.get(488).toInt() and 0xFF) / 255f * 100f).roundToInt()
        } else {
            0
        }

        val speedKmh = (speedMs * 3.6f).roundToInt().coerceIn(0, 500)
        val revPct = ((rpm / maxRpm) * 100f).roundToInt().coerceIn(0, 100)

        if (speedKmh > world.currentLapMaxSpeed) world.currentLapMaxSpeed = speedKmh
        if (world.currentLapSpeedSamples.size < 200) world.currentLapSpeedSamples.add(speedKmh)

        world.playerSample = world.playerSample.copy(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speedKmh,
            throttle = throttle,
            brake = brake,
            steer = steer,
            clutch = clutch,
            gear = rawGear,
            engineRpm = rpm,
            revLightsPct = revPct,
            gForceLat = gLat,
            gForceLon = gLon,
        )

        // Map [FL=0, FR=1, RL=2, RR=3] -> app [RL, RR, FL, FR]
        world.carCondition = world.carCondition.copy(
            wheels = listOf(
                WheelData("TE", tyreTempsC[2], tyreTempsC[2] + 3, airPsi[2], tyreWearPct[2], brakeTempC = brakeTempsC[2], suspensionTravelMm = suspTravelMm[2]),
                WheelData("TD", tyreTempsC[3], tyreTempsC[3] + 3, airPsi[3], tyreWearPct[3], brakeTempC = brakeTempsC[3], suspensionTravelMm = suspTravelMm[3]),
                WheelData("DE", tyreTempsC[0], tyreTempsC[0] + 3, airPsi[0], tyreWearPct[0], brakeTempC = brakeTempsC[0], suspensionTravelMm = suspTravelMm[0]),
                WheelData("DD", tyreTempsC[1], tyreTempsC[1] + 3, airPsi[1], tyreWearPct[1], brakeTempC = brakeTempsC[1], suspensionTravelMm = suspTravelMm[1]),
            ),
            engineTempC = oilTemp,
            oilTempC = oilTemp,
            waterTempC = waterTemp,
            oilPressureBar = oilPressureBar,
            fuelInTankKg = fuelLevel,
            fuelCapacityLiters = fuelCapacity,
            fuelRemainingLaps = ((fuelLevel / fuelCapacity) * 20f).coerceAtLeast(0.5f),
            brakeBiasPct = brakeBiasPct,
            frontWingLeftDamagePct = aeroDamagePct,
            frontWingRightDamagePct = aeroDamagePct,
            iceWearPct = engineDamagePct,
        )

        world.drivers[0] = world.drivers[0].copy(
            driverCode = targetGame.shortTag,
            driverName = "Piloto ${targetGame.shortTag}",
            teamName = targetGame.displayName,
            teamColorHex = targetGame.badgeColorHex,
            isPlayer = true,
        )

        world.sessionMeta = world.sessionMeta.copy(
            trackName = "${targetGame.displayName} (SMS UDP v2)",
            flagStatus = FlagStatus.GREEN,
            forecastSummary = "Fluxo SMS UDP v2 ativo na porta 5606",
        )
        return true
    }
}
