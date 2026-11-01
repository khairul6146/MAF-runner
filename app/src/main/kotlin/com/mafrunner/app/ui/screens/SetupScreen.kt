package com.mafrunner.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mafrunner.app.ble.BleConnectionState
import com.mafrunner.app.domain.MafCategory
import com.mafrunner.app.domain.MafZones
import com.mafrunner.app.ui.theme.*

@Composable
fun SetupScreen(
    age: Int,
    onAgeChange: (Int) -> Unit,
    category: MafCategory,
    onCategoryChange: (MafCategory) -> Unit,
    zones: MafZones,
    bleState: BleConnectionState,
    savedMac: String?,
    onScanBle: () -> Unit,
    cadenceEnabled: Boolean,
    onToggleCadence: (Boolean) -> Unit,
    cadenceSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    liveAudioEnergy: Float = 0f,
    audioThreshold: Float = 0.6f,
    lastStrikeTimeMs: Long = 0L,
    strapBatteryLevel: Int? = null,
    onStartWorkout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NordicBlack)
            .padding(18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // MAF Ceiling Display Banner
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CALCULATED MAF CEILING",
                    color = NordicLightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${zones.ceiling}",
                            color = NordicWhite,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 46.sp
                        )
                        Text(
                            text = " BPM",
                            color = NordicLightGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Target: ${zones.targetMin} - ${zones.ceiling}",
                            color = NordicWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Floor: ${zones.floor} bpm",
                            color = NordicLightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 180 Formula Config Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Runner Age", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { if (age > 10) onAgeChange(age - 1) },
                            colors = ButtonDefaults.buttonColors(containerColor = NordicCardBorder),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("-", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "$age",
                            color = NordicWhite,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        Button(
                            onClick = { if (age < 99) onAgeChange(age + 1) },
                            colors = ButtonDefaults.buttonColors(containerColor = NordicCardBorder),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("+", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Health & Training Category", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // 2x2 Category Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val categories = MafCategory.entries
                    for (i in 0 until categories.size step 2) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (j in 0..1) {
                                if (i + j < categories.size) {
                                    val cat = categories[i + j]
                                    val isSelected = cat == category
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF22252C) else Color(0xFF16181D))
                                            .border(
                                                1.dp,
                                                if (isSelected) NordicCyan else NordicCardBorder,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onCategoryChange(cat) }
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = cat.label,
                                                color = if (isSelected) NordicCyan else NordicLightGray,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = cat.name.replace("_", " "),
                                                color = if (isSelected) NordicWhite else NordicMuted,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bluetooth Strap Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when (bleState) {
                            BleConnectionState.CONNECTED -> NordicEmerald
                            BleConnectionState.CONNECTING -> NordicAmber
                            BleConnectionState.SCANNING -> NordicCyan
                            BleConnectionState.DISCONNECTED -> NordicCrimson
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Heart Rate Sensor", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = when (bleState) {
                                    BleConnectionState.CONNECTED -> savedMac ?: "Connected"
                                    BleConnectionState.CONNECTING -> "Connecting..."
                                    BleConnectionState.SCANNING -> "Scanning for Coospo..."
                                    BleConnectionState.DISCONNECTED -> savedMac?.let { "Auto-reconnects to $it" } ?: "Not paired"
                                },
                                color = NordicLightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (bleState == BleConnectionState.CONNECTED && strapBatteryLevel != null) {
                            val batteryColor = when {
                                strapBatteryLevel >= 50 -> NordicEmerald
                                strapBatteryLevel >= 20 -> NordicAmber
                                else -> NordicCrimson
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF141518),
                                modifier = Modifier
                                    .border(1.dp, batteryColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "$strapBatteryLevel% 🔋",
                                    color = batteryColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Button(
                            onClick = onScanBle,
                            colors = ButtonDefaults.buttonColors(containerColor = NordicCardBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (bleState == BleConnectionState.CONNECTED) "Change" else "Scan",
                                color = NordicWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (bleState == BleConnectionState.CONNECTED && strapBatteryLevel != null && strapBatteryLevel < 20) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E1010),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NordicCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Strap battery low (${strapBatteryLevel}%)",
                                color = NordicCrimson,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Acoustic Microphone Cadence Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Acoustic Cadence (Mic)", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Detects treadmill deck impact thuds", color = NordicLightGray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = cadenceEnabled,
                        onCheckedChange = onToggleCadence,
                        colors = SwitchDefaults.colors(checkedThumbColor = NordicWhite, checkedTrackColor = NordicCyan)
                    )
                }

                if (cadenceEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sensitivity", color = NordicLightGray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Slider(
                            value = cadenceSensitivity,
                            onValueChange = onSensitivityChange,
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = NordicCyan, activeTrackColor = NordicCyan),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${cadenceSensitivity.toInt()}",
                            color = NordicWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    CadenceVuMeter(
                        liveEnergy = liveAudioEnergy,
                        threshold = audioThreshold,
                        lastStrikeTimeMs = lastStrikeTimeMs
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Workout Button
        Button(
            onClick = onStartWorkout,
            colors = ButtonDefaults.buttonColors(containerColor = NordicWhite),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "START TREADMILL RUN",
                color = NordicBlack,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun CadenceVuMeter(
    liveEnergy: Float,
    threshold: Float,
    lastStrikeTimeMs: Long,
    modifier: Modifier = Modifier
) {
    val animatedEnergy by animateFloatAsState(
        targetValue = liveEnergy.coerceIn(0f, 1f),
        label = "LiveVuEnergy"
    )
    val isTriggered = liveEnergy >= threshold && liveEnergy > 0.05f
    val isRecentStrike = (System.currentTimeMillis() - lastStrikeTimeMs) < 350L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME ACOUSTIC LEVEL",
                color = NordicLightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isRecentStrike) NordicEmerald.copy(alpha = 0.2f) else Color(0xFF1C1D22),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRecentStrike) NordicEmerald else NordicCardBorder
                )
            ) {
                Text(
                    text = if (isRecentStrike) "● STEP DETECTED" else "CALIBRATING",
                    color = if (isRecentStrike) NordicEmerald else NordicLightGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Level bar with threshold notch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF141518))
                .border(
                    1.dp,
                    if (isRecentStrike) NordicEmerald.copy(alpha = 0.6f) else NordicCardBorder,
                    RoundedCornerShape(4.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val fillWidth = width * animatedEnergy.coerceIn(0f, 1f)
                val thresholdX = width * threshold.coerceIn(0f, 1f)

                // Fill bar
                if (fillWidth > 0f) {
                    val barColor = if (isTriggered) NordicEmerald else NordicCyan
                    drawRect(
                        color = barColor,
                        topLeft = Offset.Zero,
                        size = Size(fillWidth, height)
                    )
                }

                // Threshold vertical marker notch
                drawLine(
                    color = NordicWhite,
                    start = Offset(thresholdX, 0f),
                    end = Offset(thresholdX, height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LEVEL: ${(liveEnergy * 100).toInt()}%",
                color = if (isTriggered) NordicEmerald else NordicCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TRIGGER LINE: ${(threshold * 100).toInt()}%",
                color = NordicLightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap deck or jog to test. Set line safely above treadmill motor hum.",
            color = NordicMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )
    }
}
