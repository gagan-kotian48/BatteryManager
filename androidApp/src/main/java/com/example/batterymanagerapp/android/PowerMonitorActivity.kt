package com.example.batterymanagerapp.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batterymanagerapp.*
import kotlinx.coroutines.*
import java.text.DecimalFormat

class PowerMonitorActivity : ComponentActivity() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val updateInterval = 1000L // Update every second

    private var currentMeasurement by mutableStateOf<PowerMeasurement?>(null)
    private var energyReport by mutableStateOf<EnergyConsumptionReport?>(null)
    private var powerData by mutableStateOf<PowerConsumptionData?>(null)
    private var errorState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            PowerMonitorSDK.initialize(applicationContext)
            updatePowerData()
        } catch (e: Exception) {
            Log.e("PowerMonitor", "Failed to initialize", e)
            errorState = "Failed to initialize: ${e.message}"
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SimplePowerMonitorScreen(
                        currentMeasurement = currentMeasurement,
                        energyReport = energyReport,
                        powerData = powerData,
                        errorMessage = errorState,
                        onRefreshClick = { updatePowerData() }
                    )
                }
            }
        }
    }

    private fun updatePowerData() {
        try {
            val powerManager = PowerMonitorSDK.getInstance()
            currentMeasurement = powerManager.getCurrentPowerMeasurement()
            energyReport = powerManager.getEnergyConsumptionReport()
            powerData = powerManager.getPowerConsumptionData()
            errorState = null
        } catch (e: Exception) {
            Log.e("PowerMonitor", "Error updating power data", e)
            errorState = "Update failed: ${e.message}"
        }
    }

    override fun onResume() {
        super.onResume()
        startPowerUpdates()
    }

    override fun onPause() {
        super.onPause()
        coroutineScope.coroutineContext.cancelChildren()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun startPowerUpdates() {
        coroutineScope.launch {
            while (isActive) {
                updatePowerData()
                delay(updateInterval)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePowerMonitorScreen(
    currentMeasurement: PowerMeasurement?,
    energyReport: EnergyConsumptionReport?,
    powerData: PowerConsumptionData?,
    errorMessage: String?,
    onRefreshClick: () -> Unit
) {
    val decimalFormat = remember { DecimalFormat("#.##") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Power Monitor",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error message if any
            errorMessage?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Error",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (currentMeasurement == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Loading power data...")
            } else {
                // Current Measurement Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Current Measurements",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Divider()

                        DataRow("Current", "${currentMeasurement.currentInMicroamps ?: "N/A"} μA")
                        DataRow("Voltage", "${currentMeasurement.voltageInMillivolts ?: "N/A"} mV")
                        DataRow("Power", "${currentMeasurement.instantPowerInMicrowatts ?: "N/A"} μW")
                    }
                }

                // Average Measurements Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Average Measurements",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Divider()

                        energyReport?.let { report ->
                            DataRow("Avg. Current", "${report.averageCurrentMicroamps ?: "N/A"} μA")
                            DataRow("Avg. Voltage", "${report.averageVoltageMv ?: "N/A"} mV")
                            DataRow("Avg. Power", "${report.averagePowerMicroWatts ?: "N/A"} μW")
                        } ?: Text("No average data available")
                    }
                }

                // Power Consumption Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Power Consumption",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Divider()

                        powerData?.let { data ->
                            DataRow("Energy Used", "${decimalFormat.format(data.energyUsedMicroWattHours)} μWh")
                            DataRow("Consumption Rate",
                                "${data.getConsumptionRateWattsPerHour()?.let { decimalFormat.format(it) } ?: "N/A"} W/h")
                            DataRow("Session Duration", formatDuration(data.durationMillis))
                        } ?: Text("No consumption data available")
                    }
                }

                // Refresh Button
                Button(
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Refresh Data")
                }
            }
        }
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// Helper function to format duration
private fun formatDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return if (hours > 0) {
        String.format("%d h %d m %d s", hours, minutes % 60, seconds % 60)
    } else if (minutes > 0) {
        String.format("%d m %d s", minutes, seconds % 60)
    } else {
        String.format("%d s", seconds)
    }
}