package com.example.telemetry.model

import androidx.compose.ui.graphics.Color
import com.example.telemetry.data.GameCatalog
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorPurple
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TyreHardWhite
import com.example.ui.theme.TyreInterGreen
import com.example.ui.theme.TyreMediumYellow
import com.example.ui.theme.TyreSoftRed
import com.example.ui.theme.TyreWetBlue

enum class TelemetrySourceMode(val label: String) {
    SIMULATOR("Simulador"),
    UDP("UDP Ao Vivo"),
}

enum class ConnectionState(val label: String) {
    DISCONNECTED("Desconectado"),
    WAITING_UDP("Aguardando UDP"),
    CONNECTED_UDP("UDP Ao Vivo"),
    SIMULATOR_ACTIVE("Simulador Ativo"),
}

enum class CarCategory(
    val label: String,
    val setupTitle: String,
) {
    FORMULA("Fórmula / Monoposto", "Setup Fórmula / Monoposto"),
    GT_ENDURANCE("GT / Endurance", "Setup GT3 / Endurance"),
    STREET_TOURING("Turismo / Rua", "Setup Turismo & Rua"),
}

enum class GameTitle(
    val displayName: String,
    val shortTag: String,
    val defaultCategory: CarCategory,
    val defaultPort: Int,
    val badgeColorHex: Long,
    val protocolSummary: String,
) {
    AUTO(
        displayName = "Auto (Detectar)",
        shortTag = "AUTO",
        defaultCategory = CarCategory.FORMULA,
        defaultPort = 20777,
        badgeColorHex = 0xFF00D2BE,
        protocolSummary = "Detecção automática multi-porta",
    ),
    F1_25(
        displayName = "EA Sports F1 25",
        shortTag = "F1 25",
        defaultCategory = CarCategory.FORMULA,
        defaultPort = 20777,
        badgeColorHex = 0xFFE10600,
        protocolSummary = "UDP Formato 2025 (20777)",
    ),
    F1_24(
        displayName = "EA Sports F1 24",
        shortTag = "F1 24",
        defaultCategory = CarCategory.FORMULA,
        defaultPort = 20777,
        badgeColorHex = 0xFFE10600,
        protocolSummary = "UDP Formato 2024 (20777)",
    ),
    F1_23(
        displayName = "EA Sports F1 23",
        shortTag = "F1 23",
        defaultCategory = CarCategory.FORMULA,
        defaultPort = 20777,
        badgeColorHex = 0xFFE10600,
        protocolSummary = "UDP Formato 2023 (20777)",
    ),
    GRAN_TURISMO_7(
        displayName = "Gran Turismo 7",
        shortTag = "GT7",
        defaultCategory = CarCategory.GT_ENDURANCE,
        defaultPort = 33740,
        badgeColorHex = 0xFF2979FF,
        protocolSummary = "Salsa20 UDP (33740 / Heartbeat 33739)",
    ),
    FORZA_MOTORSPORT(
        displayName = "Forza Motorsport",
        shortTag = "FM",
        defaultCategory = CarCategory.GT_ENDURANCE,
        defaultPort = 5300,
        badgeColorHex = 0xFFFF9100,
        protocolSummary = "Data Out UDP Dash 311/331B (5300)",
    ),
    FORZA_HORIZON_5(
        displayName = "Forza Horizon 5",
        shortTag = "FH5",
        defaultCategory = CarCategory.STREET_TOURING,
        defaultPort = 5300,
        badgeColorHex = 0xFFFF6D00,
        protocolSummary = "Horizon Data Out UDP 323/324B (5300)",
    ),
    ASSETTO_CORSA(
        displayName = "Assetto Corsa",
        shortTag = "AC",
        defaultCategory = CarCategory.STREET_TOURING,
        defaultPort = 9996,
        badgeColorHex = 0xFF00E676,
        protocolSummary = "Remote Telemetry Handshake (9996)",
    ),
    ASSETTO_CORSA_COMPETIZIONE(
        displayName = "Assetto Corsa Comp.",
        shortTag = "ACC",
        defaultCategory = CarCategory.GT_ENDURANCE,
        defaultPort = 9000,
        badgeColorHex = 0xFF00C853,
        protocolSummary = "Broadcasting UDP Protocol (9000)",
    ),
    PROJECT_CARS_2(
        displayName = "Project CARS 2",
        shortTag = "PC2",
        defaultCategory = CarCategory.GT_ENDURANCE,
        defaultPort = 5606,
        badgeColorHex = 0xFFB026FF,
        protocolSummary = "SMS UDP v2 Protocol (5606)",
    ),
    AUTOMOBILISTA_2(
        displayName = "Automobilista 2",
        shortTag = "AMS2",
        defaultCategory = CarCategory.GT_ENDURANCE,
        defaultPort = 5606,
        badgeColorHex = 0xFF9C27B0,
        protocolSummary = "SMS UDP v2 Protocol (5606)",
    );

    companion object {
        fun fromName(raw: String?): GameTitle =
            entries.firstOrNull { it.name == raw } ?: F1_25
    }
}

data class VehicleCapabilities(
    val hasErs: Boolean = true,
    val hasDrs: Boolean = true,
    val hasTractionControlAbs: Boolean = false,
    val hasTurboBoost: Boolean = false,
    val hasSuspensionTravel: Boolean = false,
    val hasTireSlipRatio: Boolean = false,
    val hasOpponentGrid: Boolean = true,
    val hasPitStrategy: Boolean = true,
    val hasEngineOilWater: Boolean = false,
    val hasBrakeTemps: Boolean = true,
    val hasAeroDamage: Boolean = true,
    val hasPowerUnitWear: Boolean = true,
)

enum class TyreCompound(
    val code: String,
    val displayName: String,
    val badgeColor: Color,
) {
    SOFT("S", "Macio", TyreSoftRed),
    MEDIUM("M", "Médio", TyreMediumYellow),
    HARD("H", "Duro", TyreHardWhite),
    INTER("I", "Intermediário", TyreInterGreen),
    WET("W", "Chuva", TyreWetBlue);

    companion object {
        fun fromCode(code: String): TyreCompound =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MEDIUM

        fun fromF1VisualCompound(visualId: Int): TyreCompound = when (visualId) {
            16 -> SOFT
            17 -> MEDIUM
            18 -> HARD
            7 -> INTER
            8 -> WET
            else -> MEDIUM
        }
    }
}

enum class FlagStatus(val label: String) {
    GREEN("Bandeira Verde"),
    YELLOW("Bandeira Amarela"),
    DOUBLE_YELLOW("Amarela Dupla"),
    VSC("Virtual Safety Car"),
    SAFETY_CAR("Safety Car"),
    RED("Bandeira Vermelha"),
}

enum class SectorStatus(val color: Color) {
    NONE(TextMuted),
    YELLOW(SectorYellow),
    GREEN(SectorGreen),
    PURPLE(SectorPurple),
}

enum class AlertSeverity(val label: String) {
    INFO("INFO"),
    WARNING("AVISO"),
    CRITICAL("CRÍTICO"),
}

enum class AlertCategory(val label: String) {
    CRITICAL_TELEMETRY("Telemetria Crítica"),
    STRATEGY("Oportunidade Estratégica"),
}

data class TelemetryAlert(
    val id: String,
    val severity: AlertSeverity,
    val category: AlertCategory,
    val title: String,
    val message: String,
    val metricBadge: String,
    val timestampMs: Long,
    val acknowledged: Boolean = false,
)

data class AlertThresholds(
    val engineTempWarnC: Int = 110,
    val engineTempCritC: Int = 117,
    val tyreWearWarnPct: Int = 55,
    val tyreWearCritPct: Int = 75,
    val tyreTempWarnC: Int = 104,
    val brakeTempWarnC: Int = 920,
    val fuelWarnLaps: Float = 1.6f,
    val pitLossSeconds: Float = 22.0f,
    val enableStrategyAlerts: Boolean = true,
)

data class CarTelemetrySample(
    val timestampMs: Long = 0L,
    val speedKmh: Int = 0,
    val throttle: Float = 0f,
    val brake: Float = 0f,
    val steer: Float = 0f,
    val clutch: Int = 0,
    val gear: Int = 0,
    val engineRpm: Int = 0,
    val revLightsPct: Int = 0,
    val drsActive: Boolean = false,
    val drsAvailable: Boolean = false,
    val gForceLat: Float = 0f,
    val gForceLon: Float = 0f,
    val deltaMs: Int = 0,
    val lapProgress: Float = 0f,
    val turboBoostBar: Float = 0f,
    val tcActive: Boolean = false,
    val absActive: Boolean = false,
    val handbrake: Float = 0f,
    val suggestedGear: Int = 0,
)

/**
 * Wheel indexing convention:
 * 0 = RL (Traseiro Esq), 1 = RR (Traseiro Dir), 2 = FL (Dianteiro Esq), 3 = FR (Dianteiro Dir)
 */
data class WheelData(
    val label: String,
    val tempSurfaceC: Int = 90,
    val tempInnerC: Int = 92,
    val pressurePsi: Float = 23.2f,
    val wearPct: Float = 0f,
    val damagePct: Int = 0,
    val blistersPct: Int = 0,
    val brakeTempC: Int = 420,
    val slipRatio: Float = 0f,
    val suspensionTravelMm: Float = 0f,
)

data class CarConditionState(
    val wheels: List<WheelData> = listOf(
        WheelData("TE"),
        WheelData("TD"),
        WheelData("DE"),
        WheelData("DD"),
    ),
    val engineTempC: Int = 98,
    val iceWearPct: Int = 4,
    val mguKWearPct: Int = 3,
    val turboWearPct: Int = 5,
    val gearboxWearPct: Int = 4,
    val frontWingLeftDamagePct: Int = 0,
    val frontWingRightDamagePct: Int = 0,
    val floorDamagePct: Int = 0,
    val ersStorePct: Int = 78,
    val ersDeployMode: String = "Médio",
    val fuelInTankKg: Float = 24.8f,
    val fuelRemainingLaps: Float = 14.2f,
    val fuelMix: String = "Padrão",
    val brakeBiasPct: Int = 56,
    val oilTempC: Int = 98,
    val waterTempC: Int = 86,
    val oilPressureBar: Float = 4.2f,
    val tcLevel: Int = 0,
    val absLevel: Int = 0,
    val turboBoostBar: Float = 0f,
    val fuelCapacityLiters: Float = 110f,
)

data class GridCarEntry(
    val carIndex: Int,
    val position: Int,
    val gridPosition: Int,
    val driverCode: String,
    val driverName: String,
    val teamName: String,
    val teamColorHex: Long,
    val isPlayer: Boolean,
    val lapProgress: Float,
    val worldX: Float,
    val worldZ: Float,
    val currentLapNum: Int,
    val lastLapMs: Long,
    val bestLapMs: Long,
    val gapToLeaderMs: Long,
    val intervalMs: Long,
    val tyreCompound: TyreCompound,
    val tyreAgeLaps: Int,
    val pitStatus: Int,
    val pitStops: Int,
    val penaltiesSec: Int,
    val warnings: Int,
    val sector1Status: SectorStatus = SectorStatus.NONE,
    val sector2Status: SectorStatus = SectorStatus.NONE,
    val sector3Status: SectorStatus = SectorStatus.NONE,
)

data class SessionMeta(
    val trackId: Int = 18,
    val trackName: String = "Interlagos (São Paulo)",
    val sessionType: String = "Corrida",
    val totalLaps: Int = 24,
    val currentLap: Int = 1,
    val trackLengthM: Int = 4309,
    val weather: String = "Seco",
    val trackTempC: Int = 36,
    val airTempC: Int = 25,
    val flagStatus: FlagStatus = FlagStatus.GREEN,
    val sessionBestLapMs: Long = 71420L,
    val playerBestLapMs: Long = 71680L,
    val playerLastLapMs: Long = 72050L,
    val playerCurrentLapMs: Long = 0L,
    val sector1Ms: Long = 18420L,
    val sector2Ms: Long = 36110L,
    val sector3Ms: Long = 17150L,
    val sector1Status: SectorStatus = SectorStatus.GREEN,
    val sector2Status: SectorStatus = SectorStatus.YELLOW,
    val sector3Status: SectorStatus = SectorStatus.NONE,
    val forecastSummary: String = "Céu parcialmente nublado • 10% chance chuva em 15 min",
)

data class CompletedLapSummary(
    val lapNumber: Int,
    val lapTimeMs: Long,
    val sector1Ms: Long,
    val sector2Ms: Long,
    val sector3Ms: Long,
    val tyreCompound: TyreCompound,
    val topSpeedKmh: Int,
    val avgThrottlePct: Int,
    val maxTyreWearPct: Int,
    val speedTrace: List<Int>,
)

data class TrackPoint(val x: Float, val z: Float)

data class TrackProfile(
    val trackId: Int,
    val name: String,
    val country: String,
    val lengthMeters: Int,
    val cornersCount: Int,
    val elevationChangeM: Int,
    val downforceDemand: String,
    val straightRatioPct: Int,
    val cornerProfileSummary: String,
    val kerbAggression: String,
    val tyreStressLevel: String,
    val baseFrontWing: Int,
    val baseRearWing: Int,
    val pitLossSec: Float,
    val centerline: List<TrackPoint>,
)

data class SetupDiagnosticInsight(
    val area: String,
    val observation: String,
    val adjustment: String,
    val priority: AlertSeverity,
)

data class SetupRecommendation(
    val trackProfile: TrackProfile,
    val carCategory: CarCategory = CarCategory.FORMULA,
    val aeroLabelPrimary: String = "Asa Dianteira",
    val aeroLabelSecondary: String = "Asa Traseira",
    val aeroFrontWing: Int,
    val aeroRearWing: Int,
    val diffOnThrottle: Int,
    val diffOffThrottle: Int,
    val frontCamber: Float,
    val rearCamber: Float,
    val frontToe: Float,
    val rearToe: Float,
    val frontSuspension: Int,
    val rearSuspension: Int,
    val frontAntiRollBar: Int,
    val rearAntiRollBar: Int,
    val frontRideHeight: Int,
    val rearRideHeight: Int,
    val brakePressurePct: Int,
    val brakeBiasPct: Int,
    val frontPsi: Float,
    val rearPsi: Float,
    val recommendedTcLevel: Int? = null,
    val recommendedAbsLevel: Int? = null,
    val targetHotPsiRange: String = "21.5 - 24.5 PSI",
    val insights: List<SetupDiagnosticInsight>,
)

data class PitWindowPrediction(
    val recommendedLapRange: String,
    val estimatedRejoinPosition: Int,
    val trafficAheadDriver: String,
    val trafficGapSec: Float,
    val undercutGainSec: Float,
    val isClearAirRejoin: Boolean,
    val summary: String,
)

data class LiveTelemetryState(
    val sourceMode: TelemetrySourceMode = TelemetrySourceMode.SIMULATOR,
    val selectedGameMode: GameTitle = GameTitle.AUTO,
    val detectedGame: GameTitle = GameTitle.F1_25,
    val carCategory: CarCategory = CarCategory.FORMULA,
    val capabilities: VehicleCapabilities = GameCatalog.capabilitiesFor(GameTitle.F1_25),
    val connectionState: ConnectionState = ConnectionState.SIMULATOR_ACTIVE,
    val udpPort: Int = 20777,
    val targetHostIp: String = "",
    val localIpAddress: String = "0.0.0.0",
    val packetCount: Long = 0L,
    val sessionMeta: SessionMeta = SessionMeta(),
    val currentSample: CarTelemetrySample = CarTelemetrySample(),
    val traceBuffer: List<CarTelemetrySample> = emptyList(),
    val carCondition: CarConditionState = CarConditionState(),
    val gridEntries: List<GridCarEntry> = emptyList(),
    val completedLaps: List<CompletedLapSummary> = emptyList(),
    val activeAlerts: List<TelemetryAlert> = emptyList(),
    val pitWindow: PitWindowPrediction? = null,
    val setupRecommendation: SetupRecommendation? = null,
    val selectedGridCarIndex: Int? = null,
    val activeSimulatorTrackId: Int = 18,
    val keepScreenOn: Boolean = true,
)
