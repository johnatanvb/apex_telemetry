package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.model.CarCategory
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.WheelData
import com.example.ui.components.MetricPill
import com.example.ui.components.PitWallCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.thermalColorForTyre
import com.example.ui.components.thermalColorForWear
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThermalAmber
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VehicleConditionScreen(
    state: LiveTelemetryState,
    modifier: Modifier = Modifier,
) {
    val cond = state.carCondition
    val caps = state.capabilities
    val rl = cond.wheels.getOrElse(0) { WheelData("TE") }
    val rr = cond.wheels.getOrElse(1) { WheelData("TD") }
    val fl = cond.wheels.getOrElse(2) { WheelData("DE") }
    val fr = cond.wheels.getOrElse(3) { WheelData("DD") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Tyres & Corners Card (Adapted per category capabilities)
        item {
            PitWallCard {
                SectionHeader(
                    title = "Pneus & Dinâmica (${state.detectedGame.shortTag})",
                    subtitle = when (state.carCategory) {
                        CarCategory.FORMULA -> "Temperatura superficial/interna, desgaste, pressão e bolhas"
                        CarCategory.GT_ENDURANCE -> "Pressão quente GT, temperatura, freios, slip e curso"
                        CarCategory.STREET_TOURING -> "Temperatura, deslizamento (slip) e curso da suspensão"
                    },
                    accentColor = F1Red,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WheelCornerCard(
                        title = "DIANT. ESQ",
                        wheel = fl,
                        showWearAndBlisters = caps.hasPowerUnitWear,
                        showBrakeTemp = caps.hasBrakeTemps,
                        showSlipAndSusp = caps.hasTireSlipRatio || caps.hasSuspensionTravel,
                        modifier = Modifier.weight(1f),
                    )
                    WheelCornerCard(
                        title = "DIANT. DIR",
                        wheel = fr,
                        showWearAndBlisters = caps.hasPowerUnitWear,
                        showBrakeTemp = caps.hasBrakeTemps,
                        showSlipAndSusp = caps.hasTireSlipRatio || caps.hasSuspensionTravel,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WheelCornerCard(
                        title = "TRAS. ESQ",
                        wheel = rl,
                        showWearAndBlisters = caps.hasPowerUnitWear,
                        showBrakeTemp = caps.hasBrakeTemps,
                        showSlipAndSusp = caps.hasTireSlipRatio || caps.hasSuspensionTravel,
                        modifier = Modifier.weight(1f),
                    )
                    WheelCornerCard(
                        title = "TRAS. DIR",
                        wheel = rr,
                        showWearAndBlisters = caps.hasPowerUnitWear,
                        showBrakeTemp = caps.hasBrakeTemps,
                        showSlipAndSusp = caps.hasTireSlipRatio || caps.hasSuspensionTravel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 2. Hybrid ERS (Formula) OR GT/Touring Electronics, Turbo & Fuel
        item {
            PitWallCard {
                if (caps.hasErs) {
                    SectionHeader(
                        title = "ERS & Combustível",
                        subtitle = "Entrega híbrida, balanço de freio e autonomia em voltas",
                        accentColor = TelemetryCyan,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            label = "BATERIA ERS",
                            value = "${cond.ersStorePct}%",
                            accentColor = if (cond.ersStorePct > 25) TelemetryCyan else F1Red,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "MODO ERS",
                            value = cond.ersDeployMode,
                            accentColor = SectorYellow,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "BAL. FREIO",
                            value = "${cond.brakeBiasPct}%",
                            accentColor = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonBlack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((cond.ersStorePct / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(TelemetryCyan)
                        )
                    }
                } else {
                    SectionHeader(
                        title = "Turbo, Eletrônica & Combustível",
                        subtitle = "Pressão de sobrealimentação, assistências e autonomia",
                        accentColor = TelemetryCyan,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (caps.hasTurboBoost) {
                            MetricPill(
                                label = "BOOST TURBO",
                                value = "%+.2f bar".format(cond.turboBoostBar),
                                accentColor = if (cond.turboBoostBar > 0.8f) ThermalAmber else TelemetryCyan,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (caps.hasTractionControlAbs) {
                            MetricPill(
                                label = "TC / ABS",
                                value = "TC${cond.tcLevel} / ABS${cond.absLevel}",
                                accentColor = SectorYellow,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        MetricPill(
                            label = "BAL. FREIO",
                            value = "${cond.brakeBiasPct}%",
                            accentColor = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val fuelUnit = if (state.carCategory == CarCategory.FORMULA) "kg" else "L"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricPill(
                        label = "TANQUE",
                        value = "%.1f %s".format(cond.fuelInTankKg, fuelUnit),
                        accentColor = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = "AUTONOMIA",
                        value = "%.1f voltas".format(cond.fuelRemainingLaps),
                        accentColor = if (cond.fuelRemainingLaps >= 1.6f) SectorGreen else F1Red,
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = "MAPA / ID",
                        value = cond.fuelMix,
                        accentColor = TelemetryCyan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 3. Power Unit & Aero (Formula) OR Oil/Water & Suspension Health (GT / Touring)
        item {
            PitWallCard {
                if (caps.hasPowerUnitWear) {
                    SectionHeader(
                        title = "Unidade de Potência & Aerodinâmica",
                        subtitle = "Temperatura do motor, desgaste de componentes e danos",
                        accentColor = ThermalAmber,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val engineColor = when {
                            cond.engineTempC >= 117 -> F1Red
                            cond.engineTempC >= 110 -> SectorYellow
                            else -> SectorGreen
                        }
                        MetricPill(
                            label = "TEMP. MOTOR",
                            value = "${cond.engineTempC}°C",
                            accentColor = engineColor,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "ASA DIANT. E/D",
                            value = "${cond.frontWingLeftDamagePct}% / ${cond.frontWingRightDamagePct}%",
                            accentColor = if (cond.frontWingLeftDamagePct + cond.frontWingRightDamagePct > 0) F1Red else SectorGreen,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "ASSOALHO",
                            value = "${cond.floorDamagePct}%",
                            accentColor = if (cond.floorDamagePct > 5) SectorYellow else SectorGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ComponentWearRow("Motor Combustão (ICE)", cond.iceWearPct)
                        ComponentWearRow("MGU-K", cond.mguKWearPct)
                        ComponentWearRow("Turbocompressor", cond.turboWearPct)
                        ComponentWearRow("Caixa de Câmbio", cond.gearboxWearPct)
                    }
                } else {
                    SectionHeader(
                        title = "Motor, Fluidos & Suspensão",
                        subtitle = "Monitoramento térmico de óleo/água e curso de amortecedores",
                        accentColor = ThermalAmber,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            label = "TEMP. ÓLEO",
                            value = "${cond.oilTempC}°C",
                            accentColor = if (cond.oilTempC >= 112) F1Red else SectorGreen,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "TEMP. ÁGUA",
                            value = "${cond.waterTempC}°C",
                            accentColor = if (cond.waterTempC >= 102) SectorYellow else TelemetryCyan,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "PRESSÃO ÓLEO",
                            value = "%.1f bar".format(cond.oilPressureBar),
                            accentColor = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            label = "SUSP. DIANT. E/D",
                            value = "${fl.suspensionTravelMm.roundToInt()} / ${fr.suspensionTravelMm.roundToInt()} mm",
                            accentColor = TelemetryCyan,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            label = "SUSP. TRAS. E/D",
                            value = "${rl.suspensionTravelMm.roundToInt()} / ${rr.suspensionTravelMm.roundToInt()} mm",
                            accentColor = SectorYellow,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelCornerCard(
    title: String,
    wheel: WheelData,
    showWearAndBlisters: Boolean,
    showBrakeTemp: Boolean,
    showSlipAndSusp: Boolean,
    modifier: Modifier = Modifier,
) {
    val surfColor = thermalColorForTyre(wheel.tempSurfaceC)
    val wearColor = thermalColorForWear(wheel.wearPct)
    val brakeColor = when {
        wheel.brakeTempC >= 900 -> F1Red
        wheel.brakeTempC >= 680 -> SectorYellow
        else -> TelemetryCyan
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonBlack)
            .border(1.dp, surfColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = "%.1f PSI".format(wheel.pressurePsi),
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "${wheel.tempSurfaceC}°C",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = TelemetryMonoFamily,
                    color = surfColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Núcleo ${wheel.tempInnerC}°C",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (showWearAndBlisters) {
                    Text(
                        text = "${wheel.wearPct.roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = TelemetryMonoFamily,
                        color = wearColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Desgaste", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                } else {
                    val slipPct = (abs(wheel.slipRatio) * 100f).roundToInt()
                    Text(
                        text = "$slipPct%",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = TelemetryMonoFamily,
                        color = if (slipPct >= 18) ThermalAmber else SectorGreen,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Slip Ratio", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val progress = if (showWearAndBlisters) {
            (wheel.wearPct / 100f).coerceIn(0f, 1f)
        } else {
            (abs(wheel.slipRatio) / 0.35f).coerceIn(0f, 1f)
        }
        val barColor = if (showWearAndBlisters) wearColor else if (abs(wheel.slipRatio) >= 0.18f) ThermalAmber else TelemetryCyan
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AsphaltBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (showBrakeTemp) {
                Text(
                    text = "Freio ${wheel.brakeTempC}°C",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFamily,
                    color = brakeColor,
                )
            } else if (showSlipAndSusp) {
                Text(
                    text = "Susp ${wheel.suspensionTravelMm.roundToInt()}mm",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFamily,
                    color = TelemetryCyan,
                )
            }
            if (showWearAndBlisters) {
                Text(
                    text = "Bolhas ${wheel.blistersPct}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wheel.blistersPct >= 25) ThermalAmber else TextSecondary,
                )
            } else if (showSlipAndSusp && showBrakeTemp) {
                Text(
                    text = "Susp ${wheel.suspensionTravelMm.roundToInt()}mm",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ComponentWearRow(name: String, wearPct: Int) {
    val barColor = thermalColorForWear(wearPct.toFloat())
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("$wearPct%", style = MaterialTheme.typography.labelMedium, color = barColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CarbonBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((wearPct / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}
