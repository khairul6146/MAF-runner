package com.mafrunner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mafrunner.app.ui.theme.*

@Composable
fun TestSetupDialog(
    onDismiss: () -> Unit,
    onStart: (isGuidedTest: Boolean, warmupSec: Long, testDurationSec: Long) -> Unit
) {
    var isGuided by remember { mutableStateOf(true) }
    var warmupMinutes by remember { mutableStateOf(5) }
    var testMinutes by remember { mutableStateOf(30) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WORKOUT MODE",
                    color = NordicLightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF141518), RoundedCornerShape(10.dp))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (!isGuided) Color(0xFF2563EB) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isGuided = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Free Run",
                            color = if (!isGuided) NordicWhite else NordicLightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isGuided) NordicEmerald else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isGuided = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Guided MAF Test",
                            color = if (isGuided) NordicBlack else NordicLightGray,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                if (isGuided) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Warmup Selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "WARMUP RAMP",
                            color = NordicLightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0 to "Skip", 5 to "5 min", 10 to "10 min").forEach { (min, label) ->
                                val selected = warmupMinutes == min
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) NordicCyan.copy(alpha = 0.2f) else Color(0xFF16171B),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (selected) NordicCyan else NordicCardBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { warmupMinutes = min }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) NordicCyan else NordicLightGray,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Target Test Duration Presets & Steppers
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "STEADY TEST TARGET DURATION",
                            color = NordicLightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(20, 30, 45, 60).forEach { presetMin ->
                                val selected = testMinutes == presetMin
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) NordicEmerald.copy(alpha = 0.2f) else Color(0xFF16171B),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (selected) NordicEmerald else NordicCardBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { testMinutes = presetMin }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${presetMin}m",
                                        color = if (selected) NordicEmerald else NordicLightGray,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fine Stepper Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141518),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NordicCardBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("CUSTOM DURATION", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "$testMinutes min",
                                        color = NordicWhite,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(-5, -1, 1, 5).forEach { delta ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF202228),
                                            modifier = Modifier
                                                .border(1.dp, NordicCardBorder, RoundedCornerShape(6.dp))
                                                .clickable {
                                                    testMinutes = (testMinutes + delta).coerceIn(5, 120)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (delta > 0) "+$delta" else "$delta",
                                                color = NordicWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Standing Recovery Feature Callout
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF101C14),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NordicEmerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓ Automatically records 60s & 120s Heart Rate Recovery drops upon test completion.",
                            color = NordicEmerald,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1D22)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NordicCardBorder)
                    ) {
                        Text("CANCEL", color = NordicLightGray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val warmupSec = if (isGuided) warmupMinutes * 60L else 0L
                            val testSec = if (isGuided) testMinutes * 60L else 0L
                            onStart(isGuided, warmupSec, testSec)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isGuided) NordicEmerald else Color(0xFF2563EB)),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isGuided) "START TEST" else "START RUN",
                            color = if (isGuided) NordicBlack else NordicWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
