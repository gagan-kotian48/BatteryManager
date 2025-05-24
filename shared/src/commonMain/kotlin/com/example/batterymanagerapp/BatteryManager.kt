package com.example.batterymanagerapp

interface BatteryManager {

    fun getBatteryLevel(): Int
    fun isCharging(): Boolean
    fun isPowerSaveModeEnabled(): Boolean
    fun getBatteryStatus(): BatteryStatus
    fun getAppBatteryConsumption(): Float
    fun resetConsumptionTracking()
    fun getAppBatteryUsageSinceStart(): Float
    fun getBackgroundBatteryUsage(): Float
    fun getForegroundBatteryUsage(): Float
    fun getForegroundDurationMinutes(): Int
    fun getBackgroundDurationMinutes(): Int
    fun getTotalDurationMinutes(): Int
    fun getAverageConsumption(intervals: Int? = null): Float?
    fun getIntervalConsumptionData(maxIntervals: Int? = null): List<Map<String, Any>>
    fun getSessionBatteryReport(): BatterySessionReport
}

data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean,
    val isPowerSavingEnabled: Boolean,
    val appConsumptionRate: Float
)

data class BatterySessionReport(
    val startBatteryLevel: Int,
    val endBatteryLevel: Int,
    val totalDurationMinutes: Int,
    val appConsumptionPercentage: Float,
    val foregroundDurationMinutes: Int = 0,
    val backgroundDurationMinutes: Int = 0
)

expect object BatteryManagerFactory {
    fun create(platformContext: Any?): BatteryManager
}

object BatteryManagerSDK {
    private var instance: BatteryManager? = null
    fun initialize(context: Any? = null) {
        instance = BatteryManagerFactory.create(context)
    }
    fun getInstance(): BatteryManager {
        return instance ?: throw IllegalStateException("BatteryManagerSDK not initialized")
    }
}