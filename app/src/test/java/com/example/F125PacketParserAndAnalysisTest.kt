package com.example

import com.example.telemetry.analysis.AlertEngine
import com.example.telemetry.analysis.SetupAdvisorEngine
import com.example.telemetry.model.AlertCategory
import com.example.telemetry.model.AlertSeverity
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.CarConditionState
import com.example.telemetry.model.FlagStatus
import com.example.telemetry.model.GridCarEntry
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.SessionMeta
import com.example.telemetry.model.TyreCompound
import com.example.telemetry.model.WheelData
import com.example.telemetry.udp.F125PacketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class F125PacketParserAndAnalysisTest {

    @Test
    fun `parses F1 25 CarTelemetry packet 6 little-endian header and player speed gear throttle`() {
        val totalSize = 29 + 60 * 22 + 3
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header (29 bytes)
        buf.putShort(0, 2025.toShort()) // m_packetFormat
        buf.put(2, 25.toByte()) // m_gameYear
        buf.put(6, 6.toByte()) // m_packetId = 6 (CarTelemetry)
        buf.putFloat(15, 124.5f) // m_sessionTime
        buf.put(27, 2.toByte()) // m_playerCarIndex = 2

        // Car index 2 telemetry at 29 + 2 * 60 = 149
        val base = 29 + 2 * 60
        buf.putShort(base, 318.toShort()) // speed = 318 km/h
        buf.putFloat(base + 2, 0.95f) // throttle
        buf.putFloat(base + 6, -0.15f) // steer
        buf.putFloat(base + 10, 0.0f) // brake
        buf.put(base + 15, 8.toByte()) // gear 8
        buf.putShort(base + 16, 11850.toShort()) // RPM
        buf.put(base + 18, 1.toByte()) // DRS active
        buf.put(base + 19, 85.toByte()) // rev lights %
        buf.putShort(base + 38, 108.toShort()) // engine temp

        val world = F125PacketParser.UdpMutableWorld()
        val parsed = F125PacketParser.parsePacket(buf.array(), totalSize, world)

        assertTrue(parsed)
        assertEquals(318, world.playerSample.speedKmh)
        assertEquals(8, world.playerSample.gear)
        assertEquals(11850, world.playerSample.engineRpm)
        assertTrue(world.playerSample.drsActive)
        assertEquals(108, world.carCondition.engineTempC)
    }

    @Test
    fun `alert engine fires critical engine and strategic VSC pit alerts`() {
        val state = LiveTelemetryState(
            sessionMeta = SessionMeta(
                trackId = 18,
                currentLap = 12,
                totalLaps = 24,
                flagStatus = FlagStatus.VSC,
            ),
            carCondition = CarConditionState(
                engineTempC = 119,
                wheels = listOf(
                    WheelData("TE", 102, 105, 23.1f, 64f, 0, 10, 520),
                    WheelData("TD", 103, 106, 23.2f, 68f, 0, 12, 530),
                    WheelData("DE", 99, 102, 24.3f, 58f, 0, 5, 560),
                    WheelData("DD", 108, 110, 24.5f, 77f, 0, 32, 935),
                ),
                fuelRemainingLaps = 1.4f,
            ),
            gridEntries = listOf(
                GridCarEntry(0, 1, 1, "VER", "Verstappen", "Red Bull", 0xFF3671C6, false, 0.5f, 0.5f, 0.5f, 12, 71500, 71100, 0L, 0L, TyreCompound.MEDIUM, 10, 0, 0, 0, 0),
                GridCarEntry(1, 2, 2, "PIL", "Player", "McLaren", 0xFFFF8000, true, 0.45f, 0.4f, 0.4f, 12, 71800, 71400, 3200L, 3200L, TyreCompound.MEDIUM, 12, 0, 0, 0, 0),
                GridCarEntry(2, 3, 3, "LEC", "Leclerc", "Ferrari", 0xFFDC0000, false, 0.25f, 0.2f, 0.2f, 12, 72000, 71600, 18000L, 14800L, TyreCompound.HARD, 12, 0, 0, 0, 0),
            ),
        )

        val engine = AlertEngine()
        val res = engine.evaluate(state, AlertThresholds(), emptySet())

        assertTrue(res.alerts.any { it.id == "engine_temp_crit" && it.severity == AlertSeverity.CRITICAL })
        assertTrue(res.alerts.any { it.id == "tyre_wear_crit" && it.severity == AlertSeverity.CRITICAL })
        assertTrue(res.alerts.any { it.category == AlertCategory.STRATEGY })
        assertNotNull(res.pitWindow)
    }

    @Test
    fun `setup advisor adapts rear wing and differential when rear tyres overheat`() {
        val state = LiveTelemetryState(
            sessionMeta = SessionMeta(trackId = 18, trackTempC = 42),
            carCondition = CarConditionState(
                wheels = listOf(
                    WheelData("TE", 104, 108, 23.1f, 52f, 0, 18, 520),
                    WheelData("TD", 105, 110, 23.2f, 54f, 0, 22, 530),
                    WheelData("DE", 93, 96, 24.1f, 40f, 0, 4, 550),
                    WheelData("DD", 95, 97, 24.2f, 42f, 0, 5, 560),
                ),
            ),
        )
        val advisor = SetupAdvisorEngine()
        val rec = advisor.analyzeAndRecommend(state)

        // Base rear wing for Interlagos is 26 -> +2 when rear overheating = 28
        assertEquals(28, rec.aeroRearWing)
        assertEquals(52, rec.diffOnThrottle)
        assertTrue(rec.insights.size >= 4)
    }
}
