package com.example.batterymanagerapp

data class PowerMeasurement(
    val currentInMicroamps: Int?,
    val voltageInMillivolts: Int?,
    val instantPowerInMicrowatts: Int?,
    val timestamp: Long
)

data class EnergyConsumptionReport(
    val durationMillis: Long,
    val averageCurrentMicroamps: Int?,
    val averageVoltageMv: Int?,
    val averagePowerMicroWatts: Int?,
    val totalEnergyMicroWattHours: Double?
)

data class PowerConsumptionData(
    val energyUsedMicroWattHours: Double,
    val averagePowerDrawMicroWatts: Int?,
    val durationMillis: Long
) {
    fun getConsumptionRateWattsPerHour(): Double? {
        if (durationMillis <= 0) return null
        val hours = durationMillis / 3600000.0
        return if (hours > 0) energyUsedMicroWattHours / (hours * 1000000.0) else null
    }
}

interface PowerMeasurementManager {
    fun getCurrentPowerMeasurement(): PowerMeasurement
    fun getEnergyConsumptionReport(): EnergyConsumptionReport
    fun getPowerConsumptionData(): PowerConsumptionData
    fun getAverageCurrentDraw(): Int?
    fun getAverageVoltageDraw(): Int?
    fun getAveragePower(): Int?
    fun getAppPowerConsumption(): Float
    fun getAverageBatteryConsumption(intervals: Int? = null): Double?
    fun getIntervalConsumptionData(maxIntervals: Int? = null): List<Map<String, Any>>
}

expect object PowerMeasurementFactory {
    fun create(platformContext: Any?): PowerMeasurementManager
}

object PowerMonitorSDK {
    private var instance: PowerMeasurementManager? = null

    fun initialize(context: Any? = null) {
        instance = instance ?: PowerMeasurementFactory.create(context)
    }

    fun getInstance(): PowerMeasurementManager =
        instance ?: throw IllegalStateException("PowerMonitorSDK not initialized")
}