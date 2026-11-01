package com.mafrunner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WorkoutDetailDialog(
    workout: WorkoutEntity,
    onSave: (speed: Float?, distance: Float?, isBenchmark: Boolean) -> Unit,
    onDelete: (workoutId: Long) -> Unit,
    onExport: (workout: WorkoutEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var speedText by remember { mutableStateOf(workout.treadmillSpeed?.toString() ?: "") }
    var distanceText by remember { mutableStateOf(workout.treadmillDistance?.toString() ?: "") }
    var isBenchmark by remember { mutableStateOf(workout.isBenchmark) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMMM dd, yyyy • h:mm a", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, NordicCardBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "WORKOUT DETAILS",
                            color = NordicLightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = dateFormat.format(Date(workout.startTime)),
                            color = NordicWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isBenchmark) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF332400)
                        ) {
                            Text(
                                text = "MAF TEST",
                                color = NordicAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Banner Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181A20), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DURATION", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val mins = workout.durationSec / 60
                        val secs = workout.durationSec % 60
                        Text(
                            String.format("%02d:%02d", mins, secs),
                            color = NordicWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AVG HR", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${workout.avgHr}",
                            color = NordicWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MAX HR", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${workout.maxHr}",
                            color = NordicCrimson,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CADENCE", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${workout.avgCadence}",
                            color = NordicCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Zone Time Distribution
                Text("Zone Distribution", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                val total = workout.durationSec.coerceAtLeast(1)
                val underPct = ((workout.underSec.toDouble() / total) * 100).toInt()
                val inZonePct = workout.inZonePercentage
                val overPct = ((workout.overSec.toDouble() / total) * 100).toInt()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    if (underPct > 0) Box(modifier = Modifier.weight(underPct.toFloat()).fillMaxHeight().background(NordicCyan))
                    if (inZonePct > 0) Box(modifier = Modifier.weight(inZonePct.toFloat()).fillMaxHeight().background(NordicEmerald))
                    if (overPct > 0) Box(modifier = Modifier.weight(overPct.toFloat()).fillMaxHeight().background(NordicCrimson))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Under: ${workout.underSec / 60}m ($underPct%)", color = NordicCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("In Zone: ${workout.inZoneSec / 60}m ($inZonePct%)", color = NordicEmerald, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("Over: ${workout.overSec / 60}m ($overPct%)", color = NordicCrimson, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = NordicCardBorder)
                Spacer(modifier = Modifier.height(14.dp))

                Text("EDIT TREADMILL DATA", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(10.dp))

                // Speed & Distance Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = speedText,
                        onValueChange = { speedText = it },
                        label = { Text("Speed (km/h)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NordicCyan,
                            unfocusedBorderColor = NordicCardBorder,
                            focusedTextColor = NordicWhite,
                            unfocusedTextColor = NordicWhite
                        )
                    )
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it },
                        label = { Text("Distance (km)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NordicCyan,
                            unfocusedBorderColor = NordicCardBorder,
                            focusedTextColor = NordicWhite,
                            unfocusedTextColor = NordicWhite
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Benchmark toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141518),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBenchmark = !isBenchmark }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("MAF Benchmark Test", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Used to calculate progression over time", color = NordicLightGray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = isBenchmark,
                            onCheckedChange = { isBenchmark = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NordicWhite, checkedTrackColor = NordicAmber)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Save button
                Button(
                    onClick = {
                        val speed = speedText.toFloatOrNull()
                        val distance = distanceText.toFloatOrNull()
                        onSave(speed, distance, isBenchmark)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NordicWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("SAVE CHANGES", color = NordicBlack, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Export and Delete row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onExport(workout) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1D22)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NordicCardBorder),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SHARE TCX/CSV", color = NordicCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1212)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NordicCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("DELETE RUN", color = NordicCrimson, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Workout?", color = NordicWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this workout and all its heart rate samples? This cannot be undone.", color = NordicLightGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(workout.id)
                    }
                ) {
                    Text("DELETE", color = NordicCrimson, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL", color = NordicWhite)
                }
            },
            containerColor = NordicDarkGray
        )
    }
}
