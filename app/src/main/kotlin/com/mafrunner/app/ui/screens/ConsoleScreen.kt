package com.mafrunner.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.mafrunner.app.domain.TestPhase
import com.mafrunner.app.service.WorkoutSessionState
import com.mafrunner.app.ui.components.RealtimeHeartRateGraph
import com.mafrunner.app.ui.theme.*

@Composable
fun ConsoleScreen(
    state: WorkoutSessionState,
    zones: MafZones,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onSkipWarmup: () -> Unit = {},
    onAdjustDuration: (Long) -> Unit = {},
    onStartRecoveryEarly: () -> Unit = {},
    ecgSamples: List<Int> = emptyList()
) {
    val currentHr = state.currentHr
    val isConnected = currentHr != null && currentHr > 0
    val activeZone = when {
        !isConnected -> null
        currentHr >= zones.ceiling -> MafZone.OVER_CEILING
        currentHr < zones.floor -> MafZone.BELOW_FLOOR
        else -> MafZone.MAF_TARGET
    }

    // Border color based on zone
    val targetBorderColor = when (activeZone) {
        null -> NordicCardBorder
        MafZone.BELOW_FLOOR -> NordicCyan
        MafZone.MAF_TARGET -> NordicEmerald
        MafZone.OVER_CEILING -> NordicCrimson
    }

    // Pulse animation when over ceiling
    val infiniteTransition = rememberInfiniteTransition(label = "AlertPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    val borderColor = if (activeZone == MafZone.OVER_CEILING) {
        targetBorderColor.copy(alpha = pulseAlpha)
    } else {
        targetBorderColor
    }

    // ECG telemetry rolling buffer & view mode state
    val ecgBuffer = remember { mutableStateListOf<Int>() }
    var hasEcgTelemetry by remember { mutableStateOf(false) }
    var showEcgMode by remember { mutableStateOf(false) }

    LaunchedEffect(ecgSamples) {
        if (ecgSamples.isNotEmpty()) {
            hasEcgTelemetry = true
            ecgBuffer.addAll(ecgSamples)
            // Maintain a rolling buffer of 300 samples (~2.4s at 125Hz)
            val overflow = ecgBuffer.size - 300
            if (overflow > 0) {
                repeat(overflow) {
                    if (ecgBuffer.isNotEmpty()) {
                        ecgBuffer.removeAt(0)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NordicBlack)
            .border(4.dp, borderColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isGuidedTest) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141518),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NordicCardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (phaseText, phaseColor) = when (state.testPhase) {
                                TestPhase.WARMUP -> "WARMUP" to NordicCyan
                                TestPhase.TEST_STEADY -> "MAF TEST" to NordicEmerald
                                TestPhase.RECOVERY -> "RECOVERY" to NordicAmber
                                else -> "TEST COMPLETE" to NordicEmerald
                            }
                            Box(
                                modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(phaseColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = phaseText,
                                color = phaseColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = state.formattedPhaseCountdown,
                            color = NordicWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (state.testPhase == TestPhase.WARMUP) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .border(1.dp, NordicCyan, RoundedCornerShape(6.dp))
                                    .clickable { onSkipWarmup() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("SKIP TO TEST ▶", color = NordicCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (state.testPhase == TestPhase.TEST_STEADY) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1F2937),
                                    modifier = Modifier
                                        .border(1.dp, NordicCardBorder, RoundedCornerShape(6.dp))
                                        .clickable { onAdjustDuration(-300L) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("-5m", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1F2937),
                                    modifier = Modifier
                                        .border(1.dp, NordicCardBorder, RoundedCornerShape(6.dp))
                                        .clickable { onAdjustDuration(300L) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("+5m", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2E1010),
                                modifier = Modifier
                                    .border(1.dp, NordicCrimson.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { onStartRecoveryEarly() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("START RECOVERY ▶", color = NordicCrimson, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    } else if (state.testPhase == TestPhase.RECOVERY) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Stand still on rails. Peak: ${state.recoveryPeakHr ?: "--"} | 60s: -${state.recoveryDrop60 ?: "--"} | 120s: -${state.recoveryDrop120 ?: "--"}",
                            color = NordicAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Status Top Bar with Straps, Battery & HRV telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF141518), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) NordicEmerald else NordicLightGray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("COOSPO H9Z", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (state.strapBatteryLevel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("|", color = Color(0xFF333333), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    val batteryColor = when {
                        state.strapBatteryLevel >= 50 -> NordicEmerald
                        state.strapBatteryLevel >= 20 -> NordicAmber
                        else -> NordicCrimson
                    }
                    Text(
                        text = "${state.strapBatteryLevel}% 🔋",
                        color = batteryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (state.currentHrvRmssd != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("|", color = Color(0xFF333333), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    val hrvFormatted = if (state.currentHrvRmssd % 1.0 == 0.0) "${state.currentHrvRmssd.toInt()}" else "${state.currentHrvRmssd}"
                    Text(
                        text = "HRV: $hrvFormatted ms",
                        color = NordicEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = when (activeZone) {
                    null -> "CONNECTING STRAP"
                    MafZone.BELOW_FLOOR -> "BELOW FLOOR"
                    MafZone.MAF_TARGET -> "MAF OPTIMAL"
                    MafZone.OVER_CEILING -> "ALERT: OVER CEILING"
                },
                color = if (isConnected) targetBorderColor else NordicLightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State Action Banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = targetBorderColor.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, targetBorderColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
        ) {
            Text(
                text = when (activeZone) {
                    null -> "SEARCHING COOSPO H9Z SIGNAL • ATTACH STRAP"
                    MafZone.BELOW_FLOOR -> "BELOW AEROBIC FLOOR • INCREASE PACE"
                    MafZone.MAF_TARGET -> "AEROBIC SWEET SPOT • KEEP STEADY"
                    MafZone.OVER_CEILING -> "OVER MAF CEILING • SLOW TO A WALK NOW"
                },
                color = if (isConnected) targetBorderColor else NordicLightGray,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Giant Heart Rate Display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CURRENT HEART RATE",
                color = NordicLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = if (isConnected) "$currentHr" else "--",
                color = NordicWhite,
                fontSize = 88.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                lineHeight = 84.sp,
                letterSpacing = (-3).sp
            )
            Text(
                text = "MAF Ceiling: ${zones.ceiling} BPM  (Floor: ${zones.floor})",
                color = NordicLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-Time Waveform / Graph Section with Toggle
        if (hasEcgTelemetry) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TELEMETRY MONITOR",
                    color = NordicLightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141518),
                    border = BorderStroke(1.dp, NordicCardBorder)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!showEcgMode) Color(0xFF1E293B) else Color.Transparent)
                                .clickable { showEcgMode = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "HR TREND",
                                color = if (!showEcgMode) NordicWhite else NordicMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showEcgMode) NordicEmerald.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { showEcgMode = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NordicEmerald)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE ECG",
                                    color = if (showEcgMode) NordicEmerald else NordicMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEcgMode && hasEcgTelemetry) {
            LiveEcgWaveformGraph(
                modifier = Modifier.fillMaxWidth(),
                ecgSamples = ecgBuffer.toList(),
                activeHr = currentHr
            )
        } else {
            RealtimeHeartRateGraph(
                modifier = Modifier.fillMaxWidth(),
                hrHistory = state.recentHrHistory,
                zones = zones,
                activeHr = currentHr,
                activeZone = activeZone
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Dual Metrics: Cadence and Timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NordicDarkGray,
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, NordicCardBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isRecentStrike = (System.currentTimeMillis() - state.lastStrikeTimestampMs) < 250L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRecentStrike) NordicEmerald else NordicMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ACOUSTIC CADENCE", color = NordicLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (state.currentCadence > 0) "${state.currentCadence}" else "--",
                            color = NordicWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(" SPM", color = NordicLightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    }

                    if (state.currentHrvRmssd != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val hrvFormatted = if (state.currentHrvRmssd % 1.0 == 0.0) "${state.currentHrvRmssd.toInt()}" else "${state.currentHrvRmssd}"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF141518),
                            border = BorderStroke(1.dp, NordicEmerald.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "HRV: $hrvFormatted ms",
                                color = NordicEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NordicDarkGray,
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, NordicCardBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ELAPSED TIME", color = NordicLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.formattedTime,
                        color = NordicWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Zone Distribution Track
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Zone Distribution", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${state.inZonePercentage}% in MAF", color = NordicEmerald, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1C1D22))
                ) {
                    val totalSec = state.elapsedSeconds.coerceAtLeast(1L).toFloat()
                    val underWeight = (state.underSeconds / totalSec).coerceIn(0f, 1f)
                    val inZoneWeight = (state.inZoneSeconds / totalSec).coerceIn(0f, 1f)
                    val overWeight = (state.overSeconds / totalSec).coerceIn(0f, 1f)

                    if (underWeight > 0f) Box(modifier = Modifier.weight(underWeight).fillMaxHeight().background(NordicCyan))
                    if (inZoneWeight > 0f) Box(modifier = Modifier.weight(inZoneWeight).fillMaxHeight().background(NordicEmerald))
                    if (overWeight > 0f) Box(modifier = Modifier.weight(overWeight).fillMaxHeight().background(NordicCrimson))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sweat-Friendly Workout Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = if (state.isPaused) onResume else onPause,
                enabled = state.testPhase != TestPhase.RECOVERY,
                colors = ButtonDefaults.buttonColors(containerColor = if (state.isPaused) Color(0xFF2563EB) else Color(0xFF1C1D22)),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NordicCardBorder)
            ) {
                Text(
                    text = if (state.isPaused) "RESUME" else "PAUSE",
                    color = NordicWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1214)),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF5C1D20))
            ) {
                Text(
                    text = "FINISH RUN",
                    color = NordicCrimson,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Real-time Lead II ECG waveform oscilloscope monitor.
 * Displays streaming 125Hz 16-bit bio-potential samples with medical telemetry grid,
 * dynamic microvolt scale normalization, and a live sweep head cursor.
 */
@Composable
fun LiveEcgWaveformGraph(
    modifier: Modifier = Modifier,
    ecgSamples: List<Int>,
    activeHr: Int?
) {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val strokeStyle = remember(density) {
        Stroke(
            width = with(density) { 2.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ecgPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecgPulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecgPulseAlpha"
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
            // Header: Status and Lead info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NordicEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE ECG WAVEFORM (125Hz)",
                        style = TextStyle(
                            color = NordicLightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                Text(
                    text = "LEAD II • ${if (activeHr != null && activeHr > 0) "$activeHr BPM" else "-- BPM"}",
                    style = TextStyle(
                        color = NordicEmerald,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Waveform Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                // Draw Medical Telemetry Oscilloscope Grid
                val gridSpacing = 24.dp.toPx()
                val gridColor = Color(0xFF10B981).copy(alpha = 0.07f)

                var xPos = 0f
                while (xPos <= size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, size.height),
                        strokeWidth = 1f
                    )
                    xPos += gridSpacing
                }

                var yPos = 0f
                while (yPos <= size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, yPos),
                        end = Offset(size.width, yPos),
                        strokeWidth = 1f
                    )
                    yPos += gridSpacing
                }

                if (ecgSamples.size < 2) {
                    val placeholderText = "STREAMING ECG TELEMETRY..."
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
                    val minSample = (ecgSamples.minOrNull() ?: -200).toFloat()
                    val maxSample = (ecgSamples.maxOrNull() ?: 200).toFloat()
                    val span = (maxSample - minSample).coerceAtLeast(100f)
                    val paddingY = size.height * 0.12f
                    val usableHeight = size.height - (paddingY * 2)

                    fun yFor(sample: Int): Float {
                        val norm = ((sample.toFloat() - minSample) / span).coerceIn(0f, 1f)
                        return paddingY + usableHeight * (1f - norm)
                    }

                    val stepX = size.width / (ecgSamples.size - 1).coerceAtLeast(1)
                    val path = Path()

                    ecgSamples.forEachIndexed { index, sample ->
                        val px = index * stepX
                        val py = yFor(sample)
                        if (index == 0) {
                            path.moveTo(px, py)
                        } else {
                            path.lineTo(px, py)
                        }
                    }

                    // Draw the green ECG waveform trace
                    drawPath(
                        path = path,
                        color = NordicEmerald,
                        style = strokeStyle
                    )

                    // Live sweep head indicator
                    val lastX = (ecgSamples.size - 1) * stepX
                    val lastY = yFor(ecgSamples.last())

                    drawCircle(
                        color = NordicEmerald.copy(alpha = pulseAlpha),
                        radius = pulseRadius.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = NordicEmerald,
                        radius = 4.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = NordicWhite,
                        radius = 1.5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }
    }
}
