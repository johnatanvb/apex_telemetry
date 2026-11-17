package com.example

import com.example.telemetry.analysis.AlertEngine
import com.example.telemetry.analysis.SetupAdvisorEngine
import com.example.telemetry.data.GameCatalog
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.CarCategory
import com.example.telemetry.model.CarConditionState
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SessionMeta
import com.example.telemetry.model.WheelData
import com.example.telemetry.udp.AssettoCorsaParsers
import com.example.telemetry.udp.F125PacketParser
import com.example.telemetry.udp.Gt7Salsa20Decoder
import com.example.telemetry.udp.PacketAutoDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MultiGameParsersTest {

    @Test
    fun `gt7 salsa20 decrypts Packet A and auto-detects GRAN_TURISMO_7`() {
        val plain = ByteBuffer.allocate(Gt7Salsa20Decoder.PACKET_A_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        plain.putInt(0x00, Gt7Salsa20Decoder.MAGIC_G7S0)
        plain.putFloat(0x3C, 7850f) // RPM
        plain.putFloat(0x44, 42.5f) // fuel liters
        plain.putFloat(0x48, 80.0f) // fuel cap
        plain.putFloat(0x4C, 65.0f) // speed m/s -> 234 km/h
        plain.putFloat(0x50, 2.25f) // raw boost -> 1.25 bar
        plain.putFloat(0x58, 88f) // water temp
        plain.putFloat(0x5C, 102f) // oil temp
        // GT7 TyreTemp order: FL, FR, RL, RR
        plain.putFloat(0x60, 81f) // FL
        plain.putFloat(0x64, 83f) // FR
        plain.putFloat(0x68, 86f) // RL
        plain.putFloat(0x6C, 87f) // RR
        plain.putShort(0x74, 4.toShort()) // lapCount
        plain.putShort(0x76, 10.toShort()) // totalLaps
        plain.putInt(0x78, 92_450) // bestLapMs
        plain.putInt(0x7C, 93_120) // lastLapMs
        plain.putShort(0x88, 6500.toShort()) // minAlertRpm
        plain.putShort(0x8A, 8500.toShort()) // maxAlertRpm
        plain.put(0x90, ((5 shl 4) or 4).toByte()) // gear 4, suggested 5
        plain.put(0x91, 204.toByte()) // ~80% throttle
        plain.put(0x92, 0.toByte()) // 0% brake

        val encrypted = Gt7Salsa20Decoder.encryptForTest(plain.array(), oiv = 0x2468ACE0)
        val world = F125PacketParser.UdpMutableWorld(GameTitle.F1_25)

        val detected = PacketAutoDetector.detectAndParse(
            bytes = encrypted,
            length = encrypted.size,
            world = world,
            selectedMode = GameTitle.AUTO,
            sourcePortHint = Gt7Salsa20Decoder.GT7_RECEIVE_PORT,
        )

        assertEquals(GameTitle.GRAN_TURISMO_7, detected)
        assertEquals(234, world.playerSample.speedKmh)
        assertEquals(7850, world.playerSample.engineRpm)
        assertEquals(4, world.playerSample.gear)
        assertEquals(5, world.playerSample.suggestedGear)
        assertEquals(1.25f, world.playerSample.turboBoostBar, 0.02f)
        assertEquals(102, world.carCondition.oilTempC)
        assertEquals(88, world.carCondition.waterTempC)
        // App wheel indexing: [RL, RR, FL, FR]
        assertEquals(86, world.carCondition.wheels[0].tempSurfaceC)
        assertEquals(87, world.carCondition.wheels[1].tempSurfaceC)
        assertEquals(81, world.carCondition.wheels[2].tempSurfaceC)
        assertEquals(83, world.carCondition.wheels[3].tempSurfaceC)
    }

    @Test
    fun `forza parses 311B FM7 324B FH5 and 331B FM2023 with tire wear`() {
        // 1) Forza Horizon 5 (324B with +12B Horizon shift -> dashBase = 244)
        val fh5 = ByteBuffer.allocate(324).order(ByteOrder.LITTLE_ENDIAN)
        fh5.putInt(0, 1) // isRaceOn
        fh5.putFloat(8, 8000f)
        fh5.putFloat(12, 900f)
        fh5.putFloat(16, 6200f)
        val fh5Base = 244
        fh5.putFloat(fh5Base + 12, 50.0f) // 180 km/h
        fh5.putFloat(fh5Base + 24, 176f) // FL 80C
        fh5.putFloat(fh5Base + 28, 176f)
        fh5.putFloat(fh5Base + 32, 194f) // RL 90C
        fh5.putFloat(fh5Base + 36, 194f)
        fh5.put(fh5Base + 71, 255.toByte()) // 100% throttle
        fh5.put(fh5Base + 75, 4.toByte()) // rawGear 4 -> gear 3

        val worldFh5 = F125PacketParser.UdpMutableWorld()
        val detFh5 = PacketAutoDetector.detectAndParse(fh5.array(), 324, worldFh5, GameTitle.AUTO)
        assertEquals(GameTitle.FORZA_HORIZON_5, detFh5)
        assertEquals(180, worldFh5.playerSample.speedKmh)
        assertEquals(3, worldFh5.playerSample.gear)
        assertEquals(90, worldFh5.carCondition.wheels[0].tempSurfaceC)
        assertEquals(80, worldFh5.carCondition.wheels[2].tempSurfaceC)

        // 2) Forza Motorsport 2023 (331B -> dashBase = 232 + tire wear at 311..326)
        val fm23 = ByteBuffer.allocate(331).order(ByteOrder.LITTLE_ENDIAN)
        fm23.putInt(0, 1)
        fm23.putFloat(8, 9000f)
        fm23.putFloat(12, 1000f)
        fm23.putFloat(16, 7400f)
        fm23.putFloat(232 + 12, 70.0f) // 252 km/h
        fm23.put(232 + 75, 6.toByte()) // gear 5
        fm23.putFloat(311, 0.25f) // FL 25%
        fm23.putFloat(315, 0.28f) // FR 28%
        fm23.putFloat(319, 0.42f) // RL 42%
        fm23.putFloat(323, 0.45f) // RR 45%

        val worldFm = F125PacketParser.UdpMutableWorld()
        val detFm = PacketAutoDetector.detectAndParse(fm23.array(), 331, worldFm, GameTitle.AUTO)
        assertEquals(GameTitle.FORZA_MOTORSPORT, detFm)
        assertEquals(252, worldFm.playerSample.speedKmh)
        assertEquals(42f, worldFm.carCondition.wheels[0].wearPct, 0.1f)
        assertEquals(25f, worldFm.carCondition.wheels[2].wearPct, 0.1f)
    }

    @Test
    fun `assetto corsa parses 408B handshake and 328B RTCarInfo and ACC parses msgType 3`() {
        // 1) AC 408B handshake response
        val hs = ByteArray(408)
        "Porsche 911 GT3 R".toByteArray(Charsets.UTF_16LE).copyInto(hs, 0)
        "Rafael Silva".toByteArray(Charsets.UTF_16LE).copyInto(hs, 100)
        "Monza".toByteArray(Charsets.UTF_16LE).copyInto(hs, 208)

        val worldAc = F125PacketParser.UdpMutableWorld()
        assertEquals(
            GameTitle.ASSETTO_CORSA,
            PacketAutoDetector.detectAndParse(hs, hs.size, worldAc, GameTitle.AUTO)
        )
        assertEquals("Monza", worldAc.sessionMeta.trackName)
        assertEquals("Porsche 911 GT3 R", worldAc.drivers[0].teamName)

        // 2) AC 328B RTCarInfo
        val rt = ByteBuffer.allocate(328).order(ByteOrder.LITTLE_ENDIAN)
        rt.putInt(4, 328)
        rt.putFloat(8, 248.4f) // speed km/h
        rt.put(20, 1.toByte()) // ABS enabled
        rt.put(22, 1.toByte()) // TC active
        rt.put(23, 1.toByte()) // TC enabled
        rt.putFloat(56, 0.85f) // throttle
        rt.putFloat(68, 7600f) // rpm
        rt.putInt(76, 5) // gear 4 (5 - 1)
        rt.putFloat(132, 0.08f) // FL slip

        assertEquals(
            GameTitle.ASSETTO_CORSA,
            PacketAutoDetector.detectAndParse(rt.array(), 328, worldAc, GameTitle.AUTO)
        )
        assertEquals(248, worldAc.playerSample.speedKmh)
        assertEquals(4, worldAc.playerSample.gear)
        assertTrue(worldAc.playerSample.tcActive)
        assertEquals(3, worldAc.carCondition.tcLevel)

        // 3) ACC msgType 3 (RealtimeCarUpdate) on port 9000
        val acc = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        acc.put(0, 3.toByte()) // msgType 3
        acc.putShort(1, 0.toShort()) // carIndex 0
        acc.put(5, 5.toByte()) // gear 4 (5 - 1)
        acc.putShort(10, 3.toShort()) // P3
        acc.putInt(acc.capacity() - 14, -420) // delta -420ms
        acc.putShort(acc.capacity() - 10, 261.toShort()) // speed 261

        val worldAcc = F125PacketParser.UdpMutableWorld()
        val detAcc = PacketAutoDetector.detectAndParse(
            acc.array(),
            acc.capacity(),
            worldAcc,
            GameTitle.AUTO,
            sourcePortHint = AssettoCorsaParsers.ACC_PORT,
        )
        assertEquals(GameTitle.ASSETTO_CORSA_COMPETIZIONE, detAcc)
        assertEquals(261, worldAcc.playerSample.speedKmh)
        assertEquals(4, worldAcc.playerSample.gear)
        assertEquals(-420, worldAcc.playerSample.deltaMs)
        assertEquals(3, worldAcc.drivers[0].position)
    }

    @Test
    fun `sms v2 parses project cars 2 and automobilista 2 telemetry packet`() {
        val sms = ByteBuffer.allocate(559).order(ByteOrder.LITTLE_ENDIAN)
        sms.put(10, 0.toByte()) // mPacketType = 0 (CarPhysics)
        sms.put(11, 2.toByte()) // mPacketVersion = 2
        sms.putShort(18, 98.toShort()) // oil temp
        sms.putShort(20, 480.toShort()) // oil pressure * 100 -> 4.8 bar
        sms.putShort(22, 86.toShort()) // water temp
        sms.put(28, 75.toByte()) // fuel capacity
        sms.put(30, 230.toByte()) // throttle
        sms.put(33, 5.toByte()) // gear 5
        sms.putFloat(36, 72.0f) // speed m/s -> 259 km/h
        sms.putFloat(40, 8100f) // rpm
        sms.putFloat(44, 9000f) // maxRpm
        sms.putFloat(48, 38.5f) // fuel level
        sms.putFloat(52, 0.56f) // brake bias 56%
        // Tyre temps [FL, FR, RL, RR] at 140
        sms.putFloat(140, 84f)
        sms.putFloat(144, 85f)
        sms.putFloat(148, 91f)
        sms.putFloat(152, 92f)

        val world = F125PacketParser.UdpMutableWorld()
        val det = PacketAutoDetector.detectAndParse(sms.array(), 559, world, GameTitle.AUTO)
        assertEquals(GameTitle.AUTOMOBILISTA_2, det)
        assertEquals(259, world.playerSample.speedKmh)
        assertEquals(5, world.playerSample.gear)
        assertEquals(8100, world.playerSample.engineRpm)
        assertEquals(56, world.carCondition.brakeBiasPct)
        assertEquals(91, world.carCondition.wheels[0].tempSurfaceC)
    }

    @Test
    fun `f1 parser detects packet formats 2023 2024 and 2025`() {
        listOf(2023 to GameTitle.F1_23, 2024 to GameTitle.F1_24, 2025 to GameTitle.F1_25).forEach { (year, expected) ->
            val buf = ByteBuffer.allocate(29 + 60 * 22 + 3).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(0, year.toShort())
            buf.put(6, 6.toByte())
            buf.put(27, 0.toByte())
            buf.putShort(29, 305.toShort())

            val world = F125PacketParser.UdpMutableWorld()
            val detected = PacketAutoDetector.detectAndParse(buf.array(), buf.capacity(), world, GameTitle.AUTO)
            assertEquals(expected, detected)
            assertEquals(305, world.playerSample.speedKmh)
        }
    }

    @Test
    fun `alert and setup engines adapt to GT_ENDURANCE and STREET_TOURING categories`() {
        val gtState = LiveTelemetryState(
            detectedGame = GameTitle.ASSETTO_CORSA_COMPETIZIONE,
            carCategory = CarCategory.GT_ENDURANCE,
            capabilities = GameCatalog.capabilitiesFor(GameTitle.ASSETTO_CORSA_COMPETIZIONE),
            sessionMeta = SessionMeta(trackId = 18, trackTempC = 34),
            carCondition = CarConditionState(
                wheels = listOf(
                    WheelData("TE", 96, 99, 25.2f, slipRatio = 0.28f),
                    WheelData("TD", 95, 98, 26.8f, slipRatio = 0.12f),
                    WheelData("DE", 88, 91, 26.9f, slipRatio = 0.06f),
                    WheelData("DD", 89, 92, 27.0f, slipRatio = 0.05f),
                ),
            ),
        )

        val alertRes = AlertEngine().evaluate(gtState, AlertThresholds(), emptySet())
        assertTrue(alertRes.alerts.any { it.id == "gt3_psi_window" })
        assertTrue(alertRes.alerts.any { it.id == "high_wheel_slip" })

        val gtSetup = SetupAdvisorEngine().analyzeAndRecommend(gtState)
        assertEquals(CarCategory.GT_ENDURANCE, gtSetup.carCategory)
        assertEquals("Splitter Dianteiro", gtSetup.aeroLabelPrimary)
        assertEquals("26.5 – 27.5 PSI", gtSetup.targetHotPsiRange)
        assertEquals(4, gtSetup.recommendedTcLevel)

        val streetState = gtState.copy(
            detectedGame = GameTitle.FORZA_HORIZON_5,
            carCategory = CarCategory.STREET_TOURING,
            capabilities = GameCatalog.capabilitiesFor(GameTitle.FORZA_HORIZON_5),
        )
        val streetSetup = SetupAdvisorEngine().analyzeAndRecommend(streetState)
        assertEquals(CarCategory.STREET_TOURING, streetSetup.carCategory)
        assertEquals("Lip Dianteiro", streetSetup.aeroLabelPrimary)
        assertEquals(30.5f, streetSetup.frontPsi, 0.01f)
    }
}
