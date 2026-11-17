package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.cloud.SavedLapDoc
import com.example.telemetry.model.CarTelemetrySample
import com.example.telemetry.model.GridCarEntry
import com.example.telemetry.model.TrackProfile
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorPurple
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RevLightsBar(
    revLightsPct: Int,
    modifier: Modifier = Modifier,
) {
    val totalLeds = 15
    val activeLeds = ((revLightsPct / 100f) * totalLeds).toInt().coerceIn(0, totalLeds)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonBlack)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalLeds) {
            val ledColor = when {
                i < 5 -> SectorGreen
                i < 10 -> F1Red
                else -> SectorPurple
            }
            val lit = i < activeLeds
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (lit) ledColor else ledColor.copy(alpha = 0.16f))
            )
        }
    }
}

@Composable
fun SpeedAndPedalsCanvasChart(
    samples: List<CarTelemetrySample>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartLegendDot("Velocidade (km/h)", TelemetryCyan)
            ChartLegendDot("Acelerador (%)", SectorGreen)
            ChartLegendDot("Freio (%)", F1Red)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CarbonBlack)
                .padding(8.dp)
        ) {
            if (samples.size < 2) {
                Text(
                    text = "Aguardando amostras de telemetria...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val speedHeight = h * 0.56f
                    val pedalTop = h * 0.64f
                    val pedalHeight = h * 0.34f

                    // Grid lines
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                        val y = speedHeight * ratio
                        drawLine(
                            color = AsphaltBorder.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = gridDash,
                        )
                    }
                    drawLine(
                        color = AsphaltBorder,
                        start = Offset(0f, h * 0.60f),
                        end = Offset(w, h * 0.60f),
                        strokeWidth = 1.5f,
                    )

                    val stepX = w / (samples.size - 1).coerceAtLeast(1)

                    // Speed Path & Gradient Fill
                    val speedPath = Path()
                    val speedFill = Path()
                    samples.forEachIndexed { idx, s ->
                        val x = idx * stepX
                        val normSpeed = (s.speedKmh / 360f).coerceIn(0f, 1f)
                        val y = speedHeight - normSpeed * (speedHeight - 8f)
                        if (idx == 0) {
                            speedPath.moveTo(x, y)
                            speedFill.moveTo(x, speedHeight)
                            speedFill.lineTo(x, y)
                        } else {
                            speedPath.lineTo(x, y)
                            speedFill.lineTo(x, y)
                        }
                    }
                    speedFill.lineTo(w, speedHeight)
                    speedFill.close()

                    drawPath(
                        path = speedFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TelemetryCyan.copy(alpha = 0.32f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = speedHeight,
                        ),
                    )
                    drawPath(
                        path = speedPath,
                        color = TelemetryCyan,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )

                    // Throttle & Brake Paths
                    val throttlePath = Path()
                    val brakePath = Path()
                    samples.forEachIndexed { idx, s ->
                        val x = idx * stepX
                        val yt = pedalTop + pedalHeight - s.throttle.coerceIn(0f, 1f) * pedalHeight
                        val yb = pedalTop + pedalHeight - s.brake.coerceIn(0f, 1f) * pedalHeight
                        if (idx == 0) {
                            throttlePath.moveTo(x, yt)
                            brakePath.moveTo(x, yb)
                        } else {
                            throttlePath.lineTo(x, yt)
                            brakePath.lineTo(x, yb)
                        }
                    }
                    drawPath(
                        path = throttlePath,
                        color = SectorGreen,
                        style = Stroke(width = 2.6f, cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = brakePath,
                        color = F1Red,
                        style = Stroke(width = 2.6f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

@Composable
fun GForceAndDeltaPanel(
    sample: CarTelemetrySample,
    samples: List<CarTelemetrySample>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Friction Circle (G-Force)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(120.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(CarbonBlack)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f - 8f

                    listOf(0.35f, 0.7f, 1f).forEach { ratio ->
                        drawCircle(
                            color = AsphaltBorder.copy(alpha = 0.65f),
                            radius = maxR * ratio,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5f),
                        )
                    }
                    drawLine(AsphaltBorder, Offset(cx, 6f), Offset(cx, size.height - 6f), 1f)
                    drawLine(AsphaltBorder, Offset(6f, cy), Offset(size.width - 6f, cy), 1f)

                    // Trail dots
                    val trail = samples.takeLast(10)
                    trail.forEachIndexed { idx, s ->
                        val alpha = (idx + 1).toFloat() / trail.size
                        val dx = (s.gForceLat / 5.5f).coerceIn(-1f, 1f) * maxR
                        val dy = (-s.gForceLon / 5.5f).coerceIn(-1f, 1f) * maxR
                        drawCircle(
                            color = TelemetryCyan.copy(alpha = alpha * 0.55f),
                            radius = 3.5f,
                            center = Offset(cx + dx, cy + dy),
                        )
                    }

                    val curX = (sample.gForceLat / 5.5f).coerceIn(-1f, 1f) * maxR
                    val curY = (-sample.gForceLon / 5.5f).coerceIn(-1f, 1f) * maxR
                    drawCircle(
                        color = F1Red,
                        radius = 7f,
                        center = Offset(cx + curX, cy + curY),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "G Lat %.1f | Lon %.1f".format(sample.gForceLat, sample.gForceLon),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFamily,
                color = TextSecondary,
            )
        }

        // Delta & Gear Trace
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "DELTA VS MELHOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Text(
                    text = formatDeltaMs(sample.deltaMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (sample.deltaMs <= 0) SectorGreen else F1Red,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarbonBlack)
                    .padding(6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2f
                    drawLine(
                        color = AsphaltBorder,
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.5f,
                    )
                    if (samples.size >= 2) {
                        val stepX = size.width / (samples.size - 1)
                        val deltaPath = Path()
                        samples.forEachIndexed { idx, s ->
                            val x = idx * stepX
                            val norm = (s.deltaMs / 800f).coerceIn(-1f, 1f)
                            val y = midY + norm * (midY - 6f)
                            if (idx == 0) deltaPath.moveTo(x, y) else deltaPath.lineTo(x, y)
                        }
                        drawPath(
                            path = deltaPath,
                            color = if (sample.deltaMs <= 0) SectorGreen else SectorYellow,
                            style = Stroke(width = 3f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Abaixo da linha = mais rápido que sua melhor volta",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
fun TrackMapCanvas(
    track: TrackProfile,
    gridCars: List<GridCarEntry>,
    selectedCarIndex: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(225.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonBlack)
            .padding(14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pts = track.centerline
            if (pts.size >= 2) {
                val trackPath = Path().apply {
                    pts.forEachIndexed { idx, p ->
                        val px = p.x * w
                        val py = p.z * h
                        if (idx == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                // Outer kerbs / asphalt shadow
                drawPath(
                    path = trackPath,
                    color = AsphaltBorder,
                    style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // Main track ribbon
                drawPath(
                    path = trackPath,
                    color = Color(0xFF33364D),
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // Sector accent line
                drawPath(
                    path = trackPath,
                    color = TelemetryCyan.copy(alpha = 0.5f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Start/Finish chequered marker at pts[0]
                val sf = Offset(pts[0].x * w, pts[0].z * h)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(sf.x - 4f, sf.y - 11f),
                    size = Size(8f, 22f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }

            // Draw rival cars first, then selected/player on top
            val orderedDraw = gridCars.sortedBy {
                when {
                    it.isPlayer -> 2
                    it.carIndex == selectedCarIndex -> 1
                    else -> 0
                }
            }
            orderedDraw.forEach { car ->
                val cx = car.worldX.coerceIn(0.05f, 0.95f) * w
                val cy = car.worldZ.coerceIn(0.05f, 0.95f) * h
                val carColor = Color(car.teamColorHex)
                val isSelected = car.carIndex == selectedCarIndex

                when {
                    car.isPlayer -> {
                        drawCircle(
                            color = F1Red.copy(alpha = 0.30f),
                            radius = 18f,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 9f,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = F1Red,
                            radius = 6.5f,
                            center = Offset(cx, cy),
                        )
                    }

                    isSelected -> {
                        drawCircle(
                            color = SectorYellow.copy(alpha = 0.35f),
                            radius = 16f,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = SectorYellow,
                            radius = 7.5f,
                            center = Offset(cx, cy),
                        )
                    }

                    else -> {
                        drawCircle(
                            color = carColor,
                            radius = 5.5f,
                            center = Offset(cx, cy),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonLapChart(
    lapsA: List<SavedLapDoc>,
    lapsB: List<SavedLapDoc>,
    labelA: String,
    labelB: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChartLegendDot(labelA, TelemetryCyan)
            ChartLegendDot(labelB, F1Red)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CarbonBlack)
                .padding(10.dp)
        ) {
            val allTimes = (lapsA + lapsB).map { it.lapTimeMs }.filter { it > 0L }
            if (allTimes.isEmpty()) {
                Text(
                    text = "Selecione duas corridas com voltas gravadas para plotar comparação.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val minT = (allTimes.minOrNull() ?: 60_000L) - 500L
                    val maxT = (allTimes.maxOrNull() ?: 90_000L) + 500L
                    val range = (maxT - minT).coerceAtLeast(1000L).toFloat()
                    val maxCount = maxOf(lapsA.size, lapsB.size, 2)

                    listOf(0.25f, 0.5f, 0.75f).forEach { r ->
                        drawLine(
                            color = AsphaltBorder.copy(alpha = 0.5f),
                            start = Offset(0f, size.height * r),
                            end = Offset(size.width, size.height * r),
                            strokeWidth = 1f,
                        )
                    }

                    fun drawSeries(laps: List<SavedLapDoc>, color: Color) {
                        if (laps.isEmpty()) return
                        val path = Path()
                        val stepX = size.width / (maxCount - 1).coerceAtLeast(1)
                        laps.forEachIndexed { idx, lap ->
                            val x = idx * stepX
                            val norm = ((lap.lapTimeMs - minT) / range).coerceIn(0f, 1f)
                            val y = size.height - norm * (size.height - 16f) - 8f
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color = color, radius = 4.5f, center = Offset(x, y))
                        }
                        if (laps.size >= 2) {
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }

                    drawSeries(lapsA, TelemetryCyan)
                    drawSeries(lapsB, F1Red)
                }
            }
        }
    }
}

@Composable
fun SpeedTraceOverlayChart(
    traceA: List<Long>,
    traceB: List<Long>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonBlack)
            .padding(10.dp)
    ) {
        if (traceA.size < 2 && traceB.size < 2) {
            Text(
                text = "Curva de velocidade indisponível para esta comparação.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun plotTrace(trace: List<Long>, color: Color) {
                    if (trace.size < 2) return
                    val path = Path()
                    val stepX = size.width / (trace.size - 1)
                    trace.forEachIndexed { idx, spd ->
                        val x = idx * stepX
                        val norm = (spd / 360f).coerceIn(0f, 1f)
                        val y = size.height - norm * (size.height - 10f) - 5f
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.8f, cap = StrokeCap.Round),
                    )
                }
                plotTrace(traceA, TelemetryCyan)
                plotTrace(traceB, F1Red)
            }
        }
    }
}

@Composable
private fun ChartLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
