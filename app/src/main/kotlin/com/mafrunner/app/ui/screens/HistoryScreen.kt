package com.mafrunner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mafrunner.app.data.local.WorkoutEntity
import com.mafrunner.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    workouts: List<WorkoutEntity>,
    onExportAll: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NordicBlack)
            .padding(18.dp)
    ) {
        // MAF Benchmark Trend Card
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
                    Text("MAF PROGRESSION", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF332400)
                    ) {
                        Text(
                            text = "BENCHMARKS",
                            color = NordicAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val benchmarkRuns = workouts.filter { it.isBenchmark && it.treadmillSpeed != null }
                if (benchmarkRuns.size >= 2) {
                    val latest = benchmarkRuns[0]
                    val previous = benchmarkRuns[1]
                    val speedDiff = (latest.treadmillSpeed ?: 0f) - (previous.treadmillSpeed ?: 0f)
                    val sign = if (speedDiff >= 0) "+" else ""
                    Text(
                        text = "$sign${String.format("%.1f", speedDiff)} km/h pace change",
                        color = if (speedDiff >= 0) NordicCyan else NordicCrimson,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    Text(
                        text = "Complete 2+ tagged MAF benchmark runs to measure your aerobic speed progression.",
                        color = NordicLightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WORKOUT LOG", color = NordicLightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("${workouts.size} runs", color = NordicMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No recorded workouts yet.\nStart your first treadmill run!", color = NordicMuted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            Text("EXPORT CSV & TCX", color = NordicWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
