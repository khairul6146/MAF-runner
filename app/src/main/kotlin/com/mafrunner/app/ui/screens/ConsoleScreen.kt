package com.mafrunner.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mafrunner.app.domain.MafZone
import com.mafrunner.app.domain.MafZones
import com.mafrunner.app.service.WorkoutSessionState
import com.mafrunner.app.ui.theme.*

@Composable
fun ConsoleScreen(
    state: WorkoutSessionState,
    zones: MafZones,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NordicBlack)
            .border(4.dp, borderColor)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Top Bar
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

        Spacer(modifier = Modifier.height(14.dp))

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

        Spacer(modifier = Modifier.weight(0.5f))

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
                fontSize = 118.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                lineHeight = 110.sp,
                letterSpacing = (-4).sp
            )
            Text(
                text = "MAF Ceiling: ${zones.ceiling} BPM  (Floor: ${zones.floor})",
                color = NordicLightGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.weight(0.5f))

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
                    Text("ACOUSTIC CADENCE", color = NordicLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

        Spacer(modifier = Modifier.height(14.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        // Sweat-Friendly Workout Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = if (state.isPaused) onResume else onPause,
                colors = ButtonDefaults.buttonColors(containerColor = if (state.isPaused) Color(0xFF2563EB) else Color(0xFF1C1D22)),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NordicCardBorder)
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
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5C1D20))
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
