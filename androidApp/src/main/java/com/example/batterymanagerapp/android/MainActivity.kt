package com.example.batterymanagerapp.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batterymanagerapp.BatteryManagerSDK
import com.example.batterymanagerapp.BatterySessionReport
import com.example.batterymanagerapp.BatteryStatus
import java.text.DecimalFormat
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    // Use a coroutine for updates
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val updateInterval = 5000L // Update every 5 seconds

    private var batteryStatusState by mutableStateOf<BatteryStatus?>(null)
    private var sessionReportState by mutableStateOf<BatterySessionReport?>(null)
    private var foregroundUsage by mutableStateOf(0f)
    private var backgroundUsage by mutableStateOf(0f)
    private var totalUsageSinceStart by mutableStateOf(0f)
    private var averageConsumption by mutableStateOf<Float?>(null)

    // Duration tracking states
    private var foregroundDurationMinutes by mutableStateOf(0)
    private var backgroundDurationMinutes by mutableStateOf(0)
    private var totalDurationMinutes by mutableStateOf(0)

    private var errorState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Initialize BatteryManagerSDK with the application context
            BatteryManagerSDK.initialize(applicationContext)

            // Get initial battery data
            updateAllBatteryData()
        } catch (e: Exception) {
            Log.e("BatteryMonitor", "Failed to initialize battery monitoring", e)
            errorState = "Failed to initialize: ${e.message}"
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompactBatteryInfoScreen(
                        batteryStatus = batteryStatusState,
                        sessionReport = sessionReportState,
                        foregroundUsage = foregroundUsage,
                        backgroundUsage = backgroundUsage,
                        totalUsageSinceStart = totalUsageSinceStart,
                        averageConsumption = averageConsumption,
                        foregroundDurationMinutes = foregroundDurationMinutes,
                        backgroundDurationMinutes = backgroundDurationMinutes,
                        totalDurationMinutes = totalDurationMinutes,
                        errorMessage = errorState,
                        onRefreshClick = { updateAllBatteryData() },
                        onResetTrackingClick = {
                            try {
                                BatteryManagerSDK.getInstance().resetConsumptionTracking()
                                updateAllBatteryData()
                            } catch (e: Exception) {
                                Log.e("BatteryMonitor", "Failed to reset tracking", e)
                                errorState = "Reset failed: ${e.message}"
                            }
                        },
                        onOpenPowerMonitorClick = {
                            val intent = Intent(this@MainActivity, PowerMonitorActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    private fun updateAllBatteryData() {
        try {
            val batteryManager = BatteryManagerSDK.getInstance()
            batteryStatusState = batteryManager.getBatteryStatus()
            sessionReportState = batteryManager.getSessionBatteryReport()
            foregroundUsage = batteryManager.getForegroundBatteryUsage()
            backgroundUsage = batteryManager.getBackgroundBatteryUsage()
            totalUsageSinceStart = batteryManager.getAppBatteryUsageSinceStart()

            // Get the average consumption data
            averageConsumption = batteryManager.getAverageConsumption()

            // Update duration tracking data
            foregroundDurationMinutes = batteryManager.getForegroundDurationMinutes()
            backgroundDurationMinutes = batteryManager.getBackgroundDurationMinutes()
            totalDurationMinutes = batteryManager.getTotalDurationMinutes()

            errorState = null // Clear any previous errors
        } catch (e: Exception) {
            Log.e("BatteryMonitor", "Error updating battery data", e)
            errorState = "Update failed: ${e.message}"
        }
    }

    override fun onResume() {
        super.onResume()
        // Start periodic updates with coroutines
        startBatteryUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop updates when activity is not visible
        coroutineScope.coroutineContext.cancelChildren()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up coroutines
        coroutineScope.cancel()
    }

    private fun startBatteryUpdates() {
        coroutineScope.launch {
            while (isActive) {
                updateAllBatteryData()
                delay(updateInterval)
            }
        }
    }
}

@Composable
fun CompactBatteryInfoScreen(
    batteryStatus: BatteryStatus?,
    sessionReport: BatterySessionReport?,
    foregroundUsage: Float,
    backgroundUsage: Float,
    totalUsageSinceStart: Float,
    averageConsumption: Float?,
    foregroundDurationMinutes: Int,
    backgroundDurationMinutes: Int,
    totalDurationMinutes: Int,
    errorMessage: String?,
    onRefreshClick: () -> Unit,
    onResetTrackingClick: () -> Unit,
    onOpenPowerMonitorClick: () -> Unit
) {
    val decimalFormat = remember { DecimalFormat("#.##") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp), // Minimal outer padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Battery Monitor",
            fontSize = 16.sp, // Smaller title
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Error message display with minimal padding
        errorMessage?.let {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "Error",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (batteryStatus == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Loading battery data...", fontSize = 12.sp)
                }
            }
        } else {
            // Use a scrollable column for the cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Take available space
                    .verticalScroll(rememberScrollState()), // Make scrollable if needed
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp) // Compact spacing between cards
            ) {
                // Battery Status Card - Compact
                CompactCard(title = "Device Battery") {
                    CompactInfoRow("Level", "${batteryStatus.level}%")
                    CompactInfoRow("Charging", if (batteryStatus.isCharging) "Yes" else "No")
                    CompactInfoRow("Power Saving", if (batteryStatus.isPowerSavingEnabled) "On" else "Off")
                }

                // App Battery Usage Card - Compact
                CompactCard(title = "App Battery Usage") {
                    val consumptionText = if (batteryStatus.appConsumptionRate > 0) {
                        "${decimalFormat.format(batteryStatus.appConsumptionRate)}% per hour"
                    } else {
                        "Calculating..."
                    }
                    CompactInfoRow("Current Rate", consumptionText)

                    // Add the Average Consumption Rate
                    val avgConsumptionText = if (averageConsumption != null && averageConsumption > 0) {
                        "${decimalFormat.format(averageConsumption)}% per hour"
                    } else {
                        "Calculating..."
                    }
                    CompactInfoRow("Avg. Consumption", avgConsumptionText)

                    CompactInfoRow("Total Usage", "${decimalFormat.format(totalUsageSinceStart)}%")
                    CompactInfoRow("Foreground", "${decimalFormat.format(foregroundUsage)}%")
                    CompactInfoRow("Background", "${decimalFormat.format(backgroundUsage)}%")
                }

                // App Duration Card - Compact
                CompactCard(title = "App Usage Duration") {
                    CompactInfoRow("Total", formatDuration(totalDurationMinutes))
                    CompactInfoRow("Foreground", formatDuration(foregroundDurationMinutes))
                    CompactInfoRow("Background", formatDuration(backgroundDurationMinutes))
                }

                // Session Report Card - Compact
                sessionReport?.let { report ->
                    CompactCard(title = "Session Report") {
                        CompactInfoRow("Duration", formatDuration(report.totalDurationMinutes))
                        CompactInfoRow("Start Level", "${report.startBatteryLevel}%")
                        CompactInfoRow("Current Level", "${report.endBatteryLevel}%")

                        val batteryChange = report.startBatteryLevel - report.endBatteryLevel
                        val changeText = if (batteryChange > 0) {
                            "↓ $batteryChange%"
                        } else if (batteryChange < 0) {
                            "↑ ${-batteryChange}%"
                        } else {
                            "No change"
                        }
                        CompactInfoRow("Battery Change", changeText)
                        CompactInfoRow("Used", "${decimalFormat.format(report.appConsumptionPercentage)}%")
                    }
                }

                // Power Monitor Card - Compact
                CompactCard(
                    title = "Advanced Power Monitoring",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "Track detailed power measurements",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Button(
                        onClick = onOpenPowerMonitorClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Open Power Monitor", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Buttons at the bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SmallButton(
                    text = "Refresh",
                    onClick = onRefreshClick
                )

                SmallButton(
                    text = "Reset Tracking",
                    onClick = onResetTrackingClick
                )
            }
        }
    }
}

@Composable
fun CompactCard(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable
fun CompactInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text = text, fontSize = 12.sp)
    }
}

// Helper function to format duration - Compact version
fun formatDuration(minutes: Int): String {
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        "${hours}h ${mins}m"
    } else {
        "${minutes}m"
    }
}

@Preview
@Composable
fun CompactBatteryInfoPreview() {
    val previewBatteryStatus = BatteryStatus(
        level = 85,
        isCharging = true,
        isPowerSavingEnabled = false,
        appConsumptionRate = 2.5f
    )

    val previewSessionReport = BatterySessionReport(
        startBatteryLevel = 90,
        endBatteryLevel = 85,
        totalDurationMinutes = 30,
        appConsumptionPercentage = 5.0f,
        foregroundDurationMinutes = 20,
        backgroundDurationMinutes = 10
    )

    MyApplicationTheme {
        CompactBatteryInfoScreen(
            batteryStatus = previewBatteryStatus,
            sessionReport = previewSessionReport,
            foregroundUsage = 3.5f,
            backgroundUsage = 1.5f,
            totalUsageSinceStart = 5.0f,
            averageConsumption = 2.7f,
            foregroundDurationMinutes = 20,
            backgroundDurationMinutes = 10,
            totalDurationMinutes = 30,
            errorMessage = null,
            onRefreshClick = {},
            onResetTrackingClick = {},
            onOpenPowerMonitorClick = {}
        )
    }
}