package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telemetry.model.SectorStatus
import com.example.telemetry.model.TyreCompound
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.AsphaltCard
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

fun formatLapTimeMs(ms: Long): String {
    if (ms <= 0L) return "--:--.---"
    val minutes = ms / 60_000L
    val seconds = (ms % 60_000L) / 1000L
    val millis = ms % 1000L
    return String.format("%d:%02d.%03d", minutes, seconds, millis)
}

fun formatSectorMs(ms: Long): String {
    if (ms <= 0L) return "--.---"
    val seconds = ms / 1000L
    val millis = ms % 1000L
    return String.format("%d.%03d", seconds, millis)
}

fun formatGapMs(ms: Long, isLeader: Boolean = false): String {
    if (isLeader || ms <= 0L) return "LÍDER"
    val sec = ms / 1000f
    return if (sec >= 60f) {
        val mins = (sec / 60f).toInt()
        val rem = sec - mins * 60f
        String.format("+%d:%05.2f", mins, rem)
    } else {
        String.format("+%.3fs", sec)
    }
}

fun formatDeltaMs(deltaMs: Int): String {
    val sign = if (deltaMs <= 0) "-" else "+"
    val absSec = abs(deltaMs) / 1000f
    return String.format("%s%.3fs", sign, absSec)
}

@Composable
fun PitWallCard(
    modifier: Modifier = Modifier,
    borderColor: Color = AsphaltBorder,
    containerColor: Color = AsphaltCard,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    accentColor: Color = F1Red,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun TyreCompoundBadge(
    compound: TyreCompound,
    ageLaps: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(CarbonBlack)
                .border(2.5.dp, compound.badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = compound.code,
                style = MaterialTheme.typography.labelMedium,
                color = compound.badgeColor,
                fontWeight = FontWeight.Bold,
            )
        }
        if (ageLaps != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${ageLaps}V",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFamily,
                color = TextSecondary,
            )
        }
    }
}

@Composable
fun SectorTimingBar(
    s1Ms: Long,
    s2Ms: Long,
    s3Ms: Long,
    s1Status: SectorStatus,
    s2Status: SectorStatus,
    s3Status: SectorStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectorChip("S1", formatSectorMs(s1Ms), s1Status, Modifier.weight(1f))
        SectorChip("S2", formatSectorMs(s2Ms), s2Status, Modifier.weight(1f))
        SectorChip("S3", formatSectorMs(s3Ms), s3Status, Modifier.weight(1f))
    }
}

@Composable
private fun SectorChip(
    label: String,
    timeText: String,
    status: SectorStatus,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonBlack.copy(alpha = 0.65f))
            .border(1.dp, status.color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(status.color)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = if (status == SectorStatus.NONE) TextSecondary else status.color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    accentColor: Color = SectorGreen,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonBlack.copy(alpha = 0.7f))
            .border(1.dp, AsphaltBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = TelemetryMonoFamily,
            color = accentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

fun thermalColorForTyre(tempC: Int): Color = when {
    tempC >= 106 -> F1Red
    tempC >= 99 -> SectorYellow
    tempC >= 82 -> SectorGreen
    else -> Color(0xFF40C4FF)
}

fun thermalColorForWear(wearPct: Float): Color = when {
    wearPct >= 70f -> F1Red
    wearPct >= 45f -> SectorYellow
    else -> SectorGreen
}
