package com.example.batterymanagerapp

import platform.UIKit.*
import platform.Foundation.*
import kotlin.math.max

actual object BatteryManagerFactory {
    actual fun create(platformContext: Any?): BatteryManager = IOSBatteryManager()
}

class IOSBatteryManager : BatteryManager {
    private var batteryLevelAtStart: Int
    private var appStartTime: Double
    private var lastBatteryLevel: Int

    // Foreground/background tracking
    private var foregroundUsage: Float = 0.0f
    private var backgroundUsage: Float = 0.0f
    private var foregroundDuration: Double = 0.0
    private var backgroundDuration: Double = 0.0

    private var isInForeground: Boolean = true
    private var lastStateChangeTime: Double
    private var lastStateChangeLevel: Int

    // For interval-based consumption tracking
    private val intervalMeasurements = mutableListOf<IntervalConsumption>()
    private val measurementIntervalSeconds = 60.0 // 1 minute
    private var lastIntervalTime: Double
    private var lastIntervalLevel: Int
    private var timer: NSTimer? = null

    // Data class for interval measurements
    data class IntervalConsumption(
        val startTime: Double,
        val endTime: Double,
        val startLevel: Int,
        val endLevel: Int,
        val consumptionRate: Float // Consumption per hour
    )

    init {
        // Enable battery monitoring
        UIDevice.currentDevice.batteryMonitoringEnabled = true

        // Initialize tracking variables
        lastBatteryLevel = getBatteryLevel()
        batteryLevelAtStart = lastBatteryLevel
        lastStateChangeLevel = batteryLevelAtStart
        appStartTime = NSDate().timeIntervalSince1970
        lastStateChangeTime = appStartTime

        // Initialize interval tracking
        lastIntervalTime = appStartTime
        lastIntervalLevel = batteryLevelAtStart

        // Start interval tracking timer
        startIntervalTracking()

        // Register for app state notifications
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification,
            null,
            null
        ) { _ ->
            if (isInForeground) {
                val currentTime = NSDate().timeIntervalSince1970
                val currentLevel = getBatteryLevel()

                // Calculate foreground duration and usage
                val sessionDuration = currentTime - lastStateChangeTime
                foregroundDuration += sessionDuration

                // Add usage to foreground total
                val usage = max(0, lastStateChangeLevel - currentLevel).toFloat()
                foregroundUsage += usage

                isInForeground = false
                lastStateChangeTime = currentTime
                lastStateChangeLevel = currentLevel
            }
        }

        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification,
            null,
            null
        ) { _ ->
            if (!isInForeground) {
                val currentTime = NSDate().timeIntervalSince1970
                val currentLevel = getBatteryLevel()

                // Calculate background duration and usage
                val sessionDuration = currentTime - lastStateChangeTime
                backgroundDuration += sessionDuration

                // Add usage to background total
                val usage = max(0, lastStateChangeLevel - currentLevel).toFloat()
                backgroundUsage += usage

                isInForeground = true
                lastStateChangeTime = currentTime
                lastStateChangeLevel = currentLevel
            }
        }

        // Register for battery level changes
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIDeviceBatteryLevelDidChangeNotification,
            null,
            null
        ) { _ ->
            lastBatteryLevel = getBatteryLevel()
        }
    }

    // Start interval tracking timer
    private fun startIntervalTracking() {
        // Create a timer that fires every second to check if we need to record an interval
        timer = NSTimer.scheduledTimerWithTimeInterval(
            1.0, // Check every second
            true, // Repeats
            {
                checkAndRecordInterval()
            }
        )
    }

    private fun checkAndRecordInterval() {
        val currentTime = NSDate().timeIntervalSince1970

        // Check if it's time to record a new interval (every minute)
        if (currentTime - lastIntervalTime >= measurementIntervalSeconds) {
            val currentLevel = getBatteryLevel()

            // Calculate consumption rate in percentage per hour
            val levelDrop = max(0, lastIntervalLevel - currentLevel).toFloat()
            val hoursFraction = (currentTime - lastIntervalTime) / 3600.0 // Convert to hours
            val hourlyRate = if (hoursFraction > 0) levelDrop / hoursFraction.toFloat() else 0f

            // Store the interval measurement
            intervalMeasurements.add(
                IntervalConsumption(
                    startTime = lastIntervalTime,
                    endTime = currentTime,
                    startLevel = lastIntervalLevel,
                    endLevel = currentLevel,
                    consumptionRate = hourlyRate
                )
            )

            // Keep only the last 60 intervals (1 hour of data)
            if (intervalMeasurements.size > 60) {
                intervalMeasurements.removeAt(0)
            }

            // Update for next interval
            lastIntervalTime = currentTime
            lastIntervalLevel = currentLevel
        }
    }

    override fun getBatteryLevel(): Int {
        val level = UIDevice.currentDevice.batteryLevel
        return if (level >= 0) (level * 100).toInt() else -1
    }

    override fun isCharging(): Boolean {
        val state = UIDevice.currentDevice.batteryState
        return state == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                state == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    override fun isPowerSaveModeEnabled(): Boolean {
        return NSProcessInfo.processInfo.lowPowerModeEnabled
    }

    override fun getAppBatteryConsumption(): Float {
        val currentLevel = getBatteryLevel()
        return max(0, batteryLevelAtStart - currentLevel).toFloat()
    }

    override fun getBatteryStatus(): BatteryStatus {
        val currentTime = NSDate().timeIntervalSince1970
        val runningHours = (currentTime - appStartTime) / 3600.0 // Convert to hours

        // Calculate consumption rate per hour
        val totalConsumption = getAppBatteryConsumption()
        val consumptionRate = if (runningHours > 0) totalConsumption / runningHours.toFloat() else 0.0f

        return BatteryStatus(
            level = getBatteryLevel(),
            isCharging = isCharging(),
            isPowerSavingEnabled = isPowerSaveModeEnabled(),
            appConsumptionRate = consumptionRate
        )
    }

    override fun resetConsumptionTracking() {
        val currentTime = NSDate().timeIntervalSince1970
        val currentLevel = getBatteryLevel()

        // Reset all tracking variables
        appStartTime = currentTime
        lastStateChangeTime = currentTime
        batteryLevelAtStart = currentLevel
        lastBatteryLevel = currentLevel
        lastStateChangeLevel = currentLevel

        // Reset usage and duration counters
        foregroundUsage = 0.0f
        backgroundUsage = 0.0f
        foregroundDuration = 0.0
        backgroundDuration = 0.0

        // Reset interval tracking
        lastIntervalTime = currentTime
        lastIntervalLevel = currentLevel
        intervalMeasurements.clear()
    }

    override fun getAppBatteryUsageSinceStart(): Float {
        return getAppBatteryConsumption()
    }

    override fun getBackgroundBatteryUsage(): Float {
        if (!isInForeground) {
            val currentLevel = getBatteryLevel()
            val currentUsage = max(0, lastStateChangeLevel - currentLevel).toFloat()
            return backgroundUsage + currentUsage
        }
        return backgroundUsage
    }

    override fun getForegroundBatteryUsage(): Float {
        if (isInForeground) {
            val currentLevel = getBatteryLevel()
            val currentUsage = max(0, lastStateChangeLevel - currentLevel).toFloat()
            return foregroundUsage + currentUsage
        }
        return foregroundUsage
    }

    override fun getSessionBatteryReport(): BatterySessionReport {
        val currentTime = NSDate().timeIntervalSince1970
        val currentLevel = getBatteryLevel()

        // Calculate current durations
        var totalForegroundDuration = foregroundDuration
        var totalBackgroundDuration = backgroundDuration

        // Add current session duration to the appropriate counter
        val currentSessionDuration = currentTime - lastStateChangeTime
        if (isInForeground) {
            totalForegroundDuration += currentSessionDuration
        } else {
            totalBackgroundDuration += currentSessionDuration
        }

        // Convert to minutes
        val foregroundMinutes = (totalForegroundDuration / 60.0).toInt()
        val backgroundMinutes = (totalBackgroundDuration / 60.0).toInt()
        val totalMinutes = ((currentTime - appStartTime) / 60.0).toInt()

        return BatterySessionReport(
            startBatteryLevel = batteryLevelAtStart,
            endBatteryLevel = currentLevel,
            totalDurationMinutes = totalMinutes,
            appConsumptionPercentage = getAppBatteryConsumption(),
            foregroundDurationMinutes = foregroundMinutes,
            backgroundDurationMinutes = backgroundMinutes
        )
    }

    override fun getForegroundDurationMinutes(): Int {
        val currentTime = NSDate().timeIntervalSince1970
        var totalDuration = foregroundDuration

        // Add current session if in foreground
        if (isInForeground) {
            totalDuration += (currentTime - lastStateChangeTime)
        }

        return (totalDuration / 60.0).toInt()
    }

    override fun getBackgroundDurationMinutes(): Int {
        val currentTime = NSDate().timeIntervalSince1970
        var totalDuration = backgroundDuration

        // Add current session if in background
        if (!isInForeground) {
            totalDuration += (currentTime - lastStateChangeTime)
        }

        return (totalDuration / 60.0).toInt()
    }

    override fun getTotalDurationMinutes(): Int {
        val currentTime = NSDate().timeIntervalSince1970
        val totalDuration = currentTime - appStartTime
        return (totalDuration / 60.0).toInt()
    }

    /**
     * Gets the average battery consumption rate over the specified number of intervals.
     * Each interval is typically 1 minute.
     *
     * @param intervals Number of most recent intervals to include in the average. Default is all recorded intervals.
     * @return Average consumption rate in percentage per hour, or null if no intervals are available.
     */
    override fun getAverageConsumption(intervals: Int?): Float? {
        if (intervalMeasurements.isEmpty()) {
            return null
        }

        val measurementsToUse = intervals?.let {
            val count = minOf(it, intervalMeasurements.size)
            intervalMeasurements.takeLast(count)
        } ?: intervalMeasurements

        return measurementsToUse
            .map { it.consumptionRate }
            .average()
            .toFloat()
    }

    /**
     * Returns the battery consumption data for each interval (typically 1 minute).
     *
     * @param maxIntervals Maximum number of intervals to return. Default returns all recorded intervals.
     * @return List of interval consumption data.
     */
    override fun getIntervalConsumptionData(maxIntervals: Int?): List<Map<String, Any>> {
        val dataToReturn = maxIntervals?.let {
            intervalMeasurements.takeLast(it)
        } ?: intervalMeasurements

        return dataToReturn.map { interval ->
            mapOf(
                "startTimeSeconds" to interval.startTime,
                "endTimeSeconds" to interval.endTime,
                "startLevel" to interval.startLevel,
                "endLevel" to interval.endLevel,
                "consumptionRate" to interval.consumptionRate
            )
        }
    }
}