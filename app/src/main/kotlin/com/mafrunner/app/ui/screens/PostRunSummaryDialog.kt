package com.mafrunner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.ui.theme.*

@Composable
fun PostRunSummaryDialog(
    workout: WorkoutEntity,
    onSave: (speed: Float?, distance: Float?, isBenchmark: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var speedText by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }
    var isBenchmark by remember { mutableStateOf(workout.isBenchmark || workout.isGuidedTest) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "WORKOUT SUMMARY",
                    color = NordicLightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181A20), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DURATION", color = NordicLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val mins = workout.durationSec / 60
                        val secs = workout.durationSec % 60
                        Text(String.format("%02d:%02d", mins, secs), color = NordicWhite, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AVG HR", color = NordicLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${workout.avgHr}", color = NordicWhite, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("IN ZONE", color = NordicLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${workout.inZonePercentage}%", color = NordicEmerald, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }

                if (workout.recoveryDrop60 != null || workout.recoveryDrop120 != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF101B14),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NordicEmerald.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("CARDIAC RECOVERY", color = NordicEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Peak: ${workout.recoveryPeakHr ?: "--"} bpm • 1m: -${workout.recoveryDrop60 ?: "--"} bpm • 2m: -${workout.recoveryDrop120 ?: "--"} bpm",
                                color = NordicWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Treadmill Optional Inputs
                Text("Treadmill Log (Optional)", color = NordicLightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = speedText,
                        onValueChange = { speedText = it },
                        label = { Text("Speed (km/h)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NordicEmerald,
                            unfocusedBorderColor = NordicCardBorder,
                            focusedTextColor = NordicWhite,
                            unfocusedTextColor = NordicWhite
                        )
                    )
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it },
                        label = { Text("Distance (km)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NordicEmerald,
                            unfocusedBorderColor = NordicCardBorder,
                            focusedTextColor = NordicWhite,
                            unfocusedTextColor = NordicWhite
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isBenchmark,
                        onCheckedChange = { isBenchmark = it },
                        colors = CheckboxDefaults.colors(checkedColor = NordicAmber, checkmarkColor = NordicBlack)
                    )
                    Text("Tag as official MAF Benchmark Test", color = NordicWhite, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val s = speedText.toFloatOrNull()
                        val d = distanceText.toFloatOrNull()
                        onSave(s, d, isBenchmark)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NordicEmerald),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Workout", color = NordicBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
