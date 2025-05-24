package com.example.batterymanagerapp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import android.os.Handler
import android.os.Looper
import kotlin.math.absoluteValue

class PowerMeasurementManagerImpl(context: Context) : PowerMeasurementManager {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as AndroidBatteryManager
    private val handler = Handler(Looper.getMainLooper())
    private val measurements = mutableListOf<PowerMeasurement>()
    private val intervalMeasurements = mutableListOf<IntervalMeasurement>()
    private var startTime = System.currentTimeMillis()
    private var lastMeasurementTime = System.currentTimeMillis()
    private var lastCheckTime = System.currentTimeMillis()
    private var lastIntervalTime = System.currentTimeMillis()
    private var totalEnergyMicroWattHours = 0.0
    private var lastPowerReading = 0
    private var lastEnergyReading = 0.0
    private var lastIntervalEnergy = 0.0
    private val measurementIntervalMillis = 60000

    data class IntervalMeasurement(
        val intervalStartTime: Long,
        val intervalEndTime: Long,
        val energyUsedInInterval: Double,
        val powerConsumptionWattHour: Double
    )

    init {
        startMeasuring()
    }

    private fun startMeasuring() {
        handler.post(object : Runnable {
            override fun run() {
                takeMeasurement()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun takeMeasurement() {
        val current = getCurrentInMicroamps()
        val voltage = getVoltageInMillivolts()
        val power = if (current != null && voltage != null) (current * voltage) / 1000 else 0
        val now = System.currentTimeMillis()
        val timeDeltaHours = (now - lastMeasurementTime) / 3600000.0
        val avgPower = (lastPowerReading + power) / 2.0
        val energyAddedMicroWattHours = avgPower * timeDeltaHours
        totalEnergyMicroWattHours += energyAddedMicroWattHours

        val measurement = PowerMeasurement(
            currentInMicroamps = current,
            voltageInMillivolts = voltage,
            instantPowerInMicrowatts = power,
            timestamp = now
        )

        measurements.add(measurement)
        if (measurements.size > 60) {
            measurements.removeAt(0)
        }

        if (now - lastIntervalTime >= measurementIntervalMillis) {
            val intervalEnergy = totalEnergyMicroWattHours - lastIntervalEnergy
            val powerConsumptionWattHour = intervalEnergy / 1_000_000

            intervalMeasurements.add(
                IntervalMeasurement(
                    intervalStartTime = lastIntervalTime,
                    intervalEndTime = now,
                    energyUsedInInterval = intervalEnergy,
                    powerConsumptionWattHour = powerConsumptionWattHour
                )
            )

            lastIntervalTime = now
            lastIntervalEnergy = totalEnergyMicroWattHours
        }


        lastPowerReading = power ?: 0
        lastMeasurementTime = now
    }

    private fun getCurrentInMicroamps(): Int? {
        return try {
            val current = batteryManager.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (current.absoluteValue < 30000) current * 1000 else current
        } catch (e: Exception) {
            null
        }
    }

    private fun getVoltageInMillivolts(): Int? {
        return try {
            val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    override fun getCurrentPowerMeasurement(): PowerMeasurement {
        if (measurements.isEmpty()) {
            takeMeasurement()
        }
        return measurements.lastOrNull() ?: PowerMeasurement(null, null, null, System.currentTimeMillis())
    }

    override fun getEnergyConsumptionReport(): EnergyConsumptionReport {
        val avgCurrent = measurements.mapNotNull { it.currentInMicroamps }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgVoltage = measurements.mapNotNull { it.voltageInMillivolts }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgPower = measurements.mapNotNull { it.instantPowerInMicrowatts }.takeIf { it.isNotEmpty() }?.average()?.toInt()

        return EnergyConsumptionReport(
            durationMillis = System.currentTimeMillis() - startTime,
            averageCurrentMicroamps = avgCurrent,
            averageVoltageMv = avgVoltage,
            averagePowerMicroWatts = avgPower,
            totalEnergyMicroWattHours = totalEnergyMicroWattHours
        )
    }

    override fun getAverageCurrentDraw(): Int? {
        return measurements.mapNotNull { it.currentInMicroamps }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    }

    override fun getAverageVoltageDraw(): Int? {
        return measurements.mapNotNull { it.voltageInMillivolts }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    }

    override fun getAveragePower(): Int? {
        return measurements.mapNotNull { it.instantPowerInMicrowatts }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    }

    override fun getPowerConsumptionData(): PowerConsumptionData {
        return PowerConsumptionData(
            energyUsedMicroWattHours = totalEnergyMicroWattHours,
            averagePowerDrawMicroWatts = getAveragePower(),
            durationMillis = System.currentTimeMillis() - startTime
        )
    }

    override fun getAppPowerConsumption(): Float {
        val isCharging = try {
            val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1) ?: -1
            status == AndroidBatteryManager.BATTERY_STATUS_CHARGING || status == AndroidBatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }

        if (isCharging) return 0.0f

        val now = System.currentTimeMillis()
        val timeDeltaHours = (now - lastCheckTime) / 3600000.0f
        val energyUsed = (totalEnergyMicroWattHours - lastEnergyReading).toFloat()

        lastEnergyReading = totalEnergyMicroWattHours
        lastCheckTime = now


        return if (timeDeltaHours > 0.0167f) energyUsed / timeDeltaHours else 0.0f
    }

    override fun getAverageBatteryConsumption(intervals: Int?): Double? {
        if (intervalMeasurements.isEmpty()) return null

        val measurementsToUse = intervals?.let {
            intervalMeasurements.takeLast(minOf(it, intervalMeasurements.size))
        } ?: intervalMeasurements

        return measurementsToUse.map { it.powerConsumptionWattHour }.average()
    }

    override fun getIntervalConsumptionData(maxIntervals: Int?): List<Map<String, Any>> {
        val dataToReturn = maxIntervals?.let {
            intervalMeasurements.takeLast(it)
        } ?: intervalMeasurements

        return dataToReturn.map { interval ->
            mapOf(
                "startTimeMillis" to interval.intervalStartTime,
                "endTimeMillis" to interval.intervalEndTime,
                "durationMinutes" to ((interval.intervalEndTime - interval.intervalStartTime) / 60000.0),
                "consumptionWattHour" to interval.powerConsumptionWattHour
            )
        }
    }
}

actual object PowerMeasurementFactory {
    actual fun create(platformContext: Any?): PowerMeasurementManager {
        val context = platformContext as? Context
            ?: throw IllegalArgumentException("Android requires Context")
        return PowerMeasurementManagerImpl(context)
    }
}