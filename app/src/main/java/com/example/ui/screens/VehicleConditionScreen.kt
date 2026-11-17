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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlin.math.roundToInt

@Composable
fun VehicleConditionScreen(
    state: LiveTelemetryState,
    modifier: Modifier = Modifier,
) {
    val cond = state.carCondition
    // Wheel order: 0=RL(TE), 1=RR(TD), 2=FL(DE), 3=FR(DD)
    val rl = cond.wheels.getOrElse(0) { WheelData("TE") }
    val rr = cond.wheels.getOrElse(1) { WheelData("TD") }
    val fl = cond.wheels.getOrElse(2) { WheelData("DE") }
    val fr = cond.wheels.getOrElse(3) { WheelData("DD") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Tyres & Brakes 4-Corner Thermal Schematic
        item {
            PitWallCard {
                SectionHeader(
                    title = "Pneus & Freios (4 Cantos)",
                    subtitle = "Temperatura superficial/interna, desgaste, pressão e bolhas",
                    accentColor = F1Red,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WheelCornerCard(title = "DIANT. ESQ", wheel = fl, modifier = Modifier.weight(1f))
                    WheelCornerCard(title = "DIANT. DIR", wheel = fr, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WheelCornerCard(title = "TRAS. ESQ", wheel = rl, modifier = Modifier.weight(1f))
                    WheelCornerCard(title = "TRAS. DIR", wheel = rr, modifier = Modifier.weight(1f))
                }
            }
        }

        // ERS & Fuel Strategy Card
        item {
            PitWallCard {
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
                // ERS Bar
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
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricPill(
                        label = "TANQUE",
                        value = "%.1f kg".format(cond.fuelInTankKg),
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
                        label = "MISTURA",
                        value = cond.fuelMix,
                        accentColor = TelemetryCyan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Power Unit & Aero Damage
        item {
            PitWallCard {
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
            }
        }
    }
}

@Composable
private fun WheelCornerCard(
    title: String,
    wheel: WheelData,
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
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
                Text(
                    text = "${wheel.wearPct.roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = TelemetryMonoFamily,
                    color = wearColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Desgaste",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Wear bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AsphaltBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((wheel.wearPct / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(wearColor)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Freio ${wheel.brakeTempC}°C",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFamily,
                color = brakeColor,
            )
            Text(
                text = "Bolhas ${wheel.blistersPct}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (wheel.blistersPct >= 25) ThermalAmber else TextSecondary,
            )
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
