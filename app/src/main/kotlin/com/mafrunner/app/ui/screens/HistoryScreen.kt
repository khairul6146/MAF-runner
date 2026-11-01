package com.mafrunner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.domain.AnalyticsPeriod
import com.mafrunner.app.domain.WorkoutAnalyticsEngine
import com.mafrunner.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    workouts: List<WorkoutEntity>,
    onExportAll: () -> Unit,
    onSaveWorkout: (id: Long, speed: Float?, distance: Float?, isBenchmark: Boolean) -> Unit,
    onDeleteWorkout: (id: Long) -> Unit,
    onExportSingle: (workout: WorkoutEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()) }
    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.WEEK) }
    var selectedWorkout by remember { mutableStateOf<WorkoutEntity?>(null) }

    val analyticsEngine = remember { WorkoutAnalyticsEngine() }
    val analytics = remember(workouts, selectedPeriod) {
        analyticsEngine.computeAnalytics(workouts, selectedPeriod)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NordicBlack)
            .padding(18.dp)
    ) {
        // Analytics Period Selector Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsPeriod.entries.forEach { period ->
                val isSelected = period == selectedPeriod
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) NordicWhite else Color(0xFF141518),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) NordicWhite else NordicCardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedPeriod = period }
                ) {
                    Text(
                        text = period.label,
                        color = if (isSelected) NordicBlack else NordicLightGray,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Period Analytics Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NordicDarkGray,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NordicCardBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedPeriod.label.uppercase()} AEROBIC BASE",
                        color = NordicLightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${analytics.totalWorkouts} runs",
                        color = NordicWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Key 4-stat Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("MAF COMPLIANCE", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (analytics.totalWorkouts > 0) "${analytics.overallInZonePercentage}%" else "--%",
                            color = NordicEmerald,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column {
                        Text("VOLUME", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val hours = analytics.totalDurationSec / 3600
                        val mins = (analytics.totalDurationSec % 3600) / 60
                        val volumeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                        Text(
                            text = if (analytics.totalWorkouts > 0) volumeText else "--",
                            color = NordicWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column {
                        Text("DISTANCE", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (analytics.totalDistanceKm > 0) "${String.format("%.1f", analytics.totalDistanceKm)}k" else "--",
                            color = NordicCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column {
                        Text("AVG HR", color = NordicLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (analytics.avgHeartRate > 0) "${analytics.avgHeartRate}" else "--",
                            color = NordicWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Progression Pace Banner if available
                if (analytics.speedProgressionDelta != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val diff = analytics.speedProgressionDelta
                    val sign = if (diff >= 0) "+" else ""
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (diff >= 0) Color(0xFF0D2818) else Color(0xFF281010),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$sign${String.format("%.1f", diff)} km/h speed gain at MAF ceiling (${analytics.benchmarkCount} tests)",
                            color = if (diff >= 0) NordicEmerald else NordicCrimson,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Tri-Zone Distribution Bar
                if (analytics.totalDurationSec > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val total = analytics.totalDurationSec.toFloat()
                    val underFrac = analytics.underSec / total
                    val inZoneFrac = analytics.inZoneSec / total
                    val overFrac = analytics.overSec / total

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    ) {
                        if (underFrac > 0) Box(modifier = Modifier.weight(underFrac.coerceAtLeast(0.01f)).fillMaxHeight().background(NordicCyan))
                        if (inZoneFrac > 0) Box(modifier = Modifier.weight(inZoneFrac.coerceAtLeast(0.01f)).fillMaxHeight().background(NordicEmerald))
                        if (overFrac > 0) Box(modifier = Modifier.weight(overFrac.coerceAtLeast(0.01f)).fillMaxHeight().background(NordicCrimson))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WORKOUT LOG", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("Tap to view & edit", color = NordicMuted, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No recorded workouts yet.\nStart your first treadmill run!",
                    color = NordicMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(workouts) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NordicDarkGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NordicCardBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedWorkout = item }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormat.format(Date(item.startTime)),
                                        color = NordicWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.isBenchmark) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF332400)
                                        ) {
                                            Text(
                                                text = "MAF TEST",
                                                color = NordicAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${item.inZonePercentage}% In Zone",
                                    color = NordicEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val mins = item.durationSec / 60
                                val secs = item.durationSec % 60
                                Text("Time: ${String.format("%02d:%02d", mins, secs)}", color = NordicLightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Avg HR: ${item.avgHr} bpm", color = NordicLightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Cadence: ${item.avgCadence} spm", color = NordicLightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }

                            if (item.treadmillSpeed != null || item.treadmillDistance != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val speedStr = item.treadmillSpeed?.let { "$it km/h" } ?: ""
                                val distStr = item.treadmillDistance?.let { "$it km" } ?: ""
                                Text(
                                    text = "Treadmill: $speedStr  $distStr".trim(),
                                    color = NordicCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onExportAll,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1D22)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NordicCardBorder)
        ) {
            Text("EXPORT ALL CSV & TCX", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    // Workout Detail & Edit Dialog
    selectedWorkout?.let { workout ->
        WorkoutDetailDialog(
            workout = workout,
            onSave = { speed, distance, isBenchmark ->
                onSaveWorkout(workout.id, speed, distance, isBenchmark)
                selectedWorkout = null
            },
            onDelete = { id ->
                onDeleteWorkout(id)
                selectedWorkout = null
            },
            onExport = { w ->
                onExportSingle(w)
            },
            onDismiss = { selectedWorkout = null }
        )
    }
}
