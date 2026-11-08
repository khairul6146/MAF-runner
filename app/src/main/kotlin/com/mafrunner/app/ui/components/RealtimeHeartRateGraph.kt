package com.mafrunner.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mafrunner.app.domain.MafZone
import com.mafrunner.app.domain.MafZones
import com.mafrunner.app.ui.theme.NordicCardBorder
import com.mafrunner.app.ui.theme.NordicCrimson
import com.mafrunner.app.ui.theme.NordicCyan
import com.mafrunner.app.ui.theme.NordicEmerald
import com.mafrunner.app.ui.theme.NordicLightGray
import com.mafrunner.app.ui.theme.NordicMuted
import com.mafrunner.app.ui.theme.NordicWhite

/**
 * Dynamic Y-axis scale calculator for real-time heart rate waveform visualization.
 */
object RealtimeGraphScaling {
    fun calculateYRange(samples: List<Int>, zones: MafZones): Pair<Float, Float> {
        var minY = (zones.floor - 15).toFloat()
        var maxY = (zones.ceiling + 15).toFloat()

        if (samples.isNotEmpty()) {
            val minSample = samples.minOrNull() ?: zones.floor
            val maxSample = samples.maxOrNull() ?: zones.ceiling
            if (minSample - 5 < minY) {
                minY = (minSample - 5).toFloat()
            }
            if (maxSample + 5 > maxY) {
                maxY = (maxSample + 5).toFloat()
            }
        }

        minY = minY.coerceAtLeast(40f)
        maxY = maxY.coerceAtMost(220f)

        if (maxY - minY < 30f) {
            val diff = 30f - (maxY - minY)
            if (maxY + diff <= 220f) {
                maxY += diff
            } else {
                minY = (minY - diff).coerceAtLeast(40f)
                maxY = (minY + 30f).coerceAtMost(220f)
            }
        }

        return Pair(minY, maxY)
    }
}

/**
 * Real-time Heart Rate Graph featuring cubic Bézier smoothed waveform, MAF ceiling/target/floor
 * guidelines, zone gradient area fill, and a live pulsing head indicator.
 */
@Composable
fun RealtimeHeartRateGraph(
    modifier: Modifier = Modifier,
    hrHistory: List<Int>,
    zones: MafZones,
    activeHr: Int?,
    activeZone: MafZone? = null
) {
    val effectiveZone = activeZone ?: when {
        activeHr != null -> when {
            activeHr > zones.ceiling -> MafZone.OVER_CEILING
            activeHr < zones.floor -> MafZone.BELOW_FLOOR
            else -> MafZone.MAF_TARGET
        }
        hrHistory.isNotEmpty() -> {
            val last = hrHistory.last()
            when {
                last > zones.ceiling -> MafZone.OVER_CEILING
                last < zones.floor -> MafZone.BELOW_FLOOR
                else -> MafZone.MAF_TARGET
            }
        }
        else -> MafZone.MAF_TARGET
    }

    val activeColor = when (effectiveZone) {
        MafZone.OVER_CEILING -> NordicCrimson
        MafZone.MAF_TARGET -> NordicEmerald
        MafZone.BELOW_FLOOR -> NordicCyan
    }

    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141518),
        border = BorderStroke(1.dp, NordicCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header: Legend & Live Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE HR TREND (90s)",
                    style = TextStyle(
                        color = NordicLightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )

                val minBpm = hrHistory.minOrNull()
                val maxBpm = hrHistory.maxOrNull()
                val displayHr = activeHr ?: hrHistory.lastOrNull()

                val statsText = if (minBpm != null && maxBpm != null && displayHr != null && displayHr > 0) {
                    "MIN $minBpm  MAX $maxBpm • $displayHr BPM"
                } else if (displayHr != null && displayHr > 0) {
                    "$displayHr BPM"
                } else {
                    "-- BPM"
                }

                Text(
                    text = statsText,
                    style = TextStyle(
                        color = if (displayHr != null && displayHr > 0) activeColor else NordicMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Waveform Area
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val (minY, maxY) = RealtimeGraphScaling.calculateYRange(hrHistory, zones)
                val yRangeSpan = (maxY - minY).coerceAtLeast(1f)

                fun yFor(hrVal: Float): Float {
                    val normalized = ((hrVal - minY) / yRangeSpan).coerceIn(0f, 1f)
                    return size.height * (1f - normalized)
                }

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                // 1. Guidelines & Threshold Bands
                val yCeiling = yFor(zones.ceiling.toFloat())
                val yTarget = yFor(zones.targetMin.toFloat())
                val yFloor = yFor(zones.floor.toFloat())

                // Ceiling Line (Crimson, dashed, alpha 0.45)
                drawLine(
                    color = NordicCrimson.copy(alpha = 0.45f),
                    start = Offset(0f, yCeiling),
                    end = Offset(size.width, yCeiling),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "CEILING ${zones.ceiling}",
                    topLeft = Offset(6.dp.toPx(), (yCeiling - 13.sp.toPx()).coerceAtLeast(2.dp.toPx())),
                    style = TextStyle(
                        color = NordicCrimson.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Target Min Line (Emerald, dashed, alpha 0.35)
                drawLine(
                    color = NordicEmerald.copy(alpha = 0.35f),
                    start = Offset(0f, yTarget),
                    end = Offset(size.width, yTarget),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Floor Line (Cyan, dashed, alpha 0.35)
                drawLine(
                    color = NordicCyan.copy(alpha = 0.35f),
                    start = Offset(0f, yFloor),
                    end = Offset(size.width, yFloor),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "FLOOR ${zones.floor}",
                    topLeft = Offset(6.dp.toPx(), (yFloor + 2.dp.toPx()).coerceAtMost(size.height - 13.sp.toPx())),
                    style = TextStyle(
                        color = NordicCyan.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )

                // 2. Waveform or Placeholder Rendering
                if (hrHistory.size < 2) {
                    val yCenter = size.height / 2f
                    drawLine(
                        color = NordicMuted.copy(alpha = 0.35f),
                        start = Offset(0f, yCenter),
                        end = Offset(size.width, yCenter),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    val placeholderText = "COLLECTING HEART RATE TELEMETRY..."
                    val layoutResult = textMeasurer.measure(
                        text = placeholderText,
                        style = TextStyle(
                            color = NordicLightGray.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                    )
                    drawText(
                        textLayoutResult = layoutResult,
                        topLeft = Offset(
                            x = (size.width - layoutResult.size.width) / 2f,
                            y = (size.height - layoutResult.size.height) / 2f
                        )
                    )
                } else {
                    val stepX = size.width / (hrHistory.size - 1).coerceAtLeast(1)
                    val points = hrHistory.mapIndexed { index, hr ->
                        Offset(x = index * stepX, y = yFor(hr.toFloat()))
                    }

                    val strokePath = Path()
                    val fillPath = Path()

                    strokePath.moveTo(points.first().x, points.first().y)
                    fillPath.moveTo(points.first().x, size.height)
                    fillPath.lineTo(points.first().x, points.first().y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points.getOrElse(i - 1) { points[i] }
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points.getOrElse(i + 2) { points[i + 1] }

                        val cp1 = Offset(
                            x = p1.x + (p2.x - p0.x) * 0.2f,
                            y = p1.y + (p2.y - p0.y) * 0.2f
                        )
                        val cp2 = Offset(
                            x = p2.x - (p3.x - p1.x) * 0.2f,
                            y = p2.y - (p3.y - p1.y) * 0.2f
                        )

                        strokePath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                        fillPath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                    }

                    fillPath.lineTo(points.last().x, size.height)
                    fillPath.close()

                    // Vertical gradient area fill under the curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.25f),
                                activeColor.copy(alpha = 0.0f)
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )

                    // Waveform line stroke
                    drawPath(
                        path = strokePath,
                        color = activeColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 3. Live Head Indicator Dot
                    val lastPoint = points.last()

                    // Pulsing animated glow ring
                    drawCircle(
                        color = activeColor.copy(alpha = pulseAlpha),
                        radius = pulseRadius.dp.toPx(),
                        center = lastPoint
                    )

                    // Solid dot in active zone color
                    drawCircle(
                        color = activeColor,
                        radius = 4.5.dp.toPx(),
                        center = lastPoint
                    )

                    // Inner white dot
                    drawCircle(
                        color = NordicWhite,
                        radius = 2.dp.toPx(),
                        center = lastPoint
                    )
                }
            }
        }
    }
}
