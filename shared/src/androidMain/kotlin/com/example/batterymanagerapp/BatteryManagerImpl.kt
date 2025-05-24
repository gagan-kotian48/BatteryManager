package com.example.batterymanagerapp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

class BatteryManagerImpl(context: Context) : BatteryManager {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as AndroidBatteryManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var lastBatteryLevel: Int
    private var appStartTime: Long
    private val startBatteryLevel: Int
    private var isInForeground = true
    private var startedActivities = 0
    private var foregroundUsage = 0f
    private var backgroundUsage = 0f
    private var lastStateChangeLevel: Int
    private var foregroundDuration: Long = 0
    private var backgroundDuration: Long = 0
    private var lastStateChangeTime: Long
    private val handler = Handler(Looper.getMainLooper())
    private val intervalMeasurements = mutableListOf<IntervalConsumption>()
    private val measurementIntervalMillis = 60000
    private var lastIntervalTime: Long
    private var lastIntervalLevel: Int

    data class IntervalConsumption(
        val startTime: Long,
        val endTime: Long,
        val startLevel: Int,
        val endLevel: Int,
        val consumptionRate: Float
    )

    init {

        lastBatteryLevel = getBatteryLevel()
        startBatteryLevel = lastBatteryLevel
        lastStateChangeLevel = startBatteryLevel
        appStartTime = System.currentTimeMillis()
        lastStateChangeTime = appStartTime
        lastIntervalTime = appStartTime
        lastIntervalLevel = startBatteryLevel


        startIntervalTracking()
        setupActivityLifecycleTracking()
    }

    private fun setupActivityLifecycleTracking() {
        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1 && !isInForeground) {
                    val currentTime = System.currentTimeMillis()
                    val currentLevel = getBatteryLevel()

                    backgroundDuration += (currentTime - lastStateChangeTime)
                    val usage = (lastStateChangeLevel - currentLevel).coerceAtLeast(0)
                    backgroundUsage += usage

                    isInForeground = true
                    lastStateChangeLevel = currentLevel
                    lastStateChangeTime = currentTime
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0 && isInForeground) {
                    val currentTime = System.currentTimeMillis()
                    val currentLevel = getBatteryLevel()

                    foregroundDuration += (currentTime - lastStateChangeTime)
                    val usage = (lastStateChangeLevel - currentLevel).coerceAtLeast(0)
                    foregroundUsage += usage

                    isInForeground = false
                    lastStateChangeLevel = currentLevel
                    lastStateChangeTime = currentTime
                }
            }


            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }

        (appContext as Application).registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    private fun startIntervalTracking() {
        handler.post(object : Runnable {
            override fun run() {
                recordIntervalMeasurement()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun recordIntervalMeasurement() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastIntervalTime >= measurementIntervalMillis) {
            val currentLevel = getBatteryLevel()
            val levelDrop = (lastIntervalLevel - currentLevel).toFloat().coerceAtLeast(0.0f)
            val hoursFraction = (currentTime - lastIntervalTime) / (1000.0f * 60.0f * 60.0f)
            val hourlyRate = if (hoursFraction > 0) levelDrop / hoursFraction else 0f

            intervalMeasurements.add(
                IntervalConsumption(
                    startTime = lastIntervalTime,
                    endTime = currentTime,
                    startLevel = lastIntervalLevel,
                    endLevel = currentLevel,
                    consumptionRate = hourlyRate
                )
            )
            if (intervalMeasurements.size > 60) {
                intervalMeasurements.removeAt(0)
            }

            lastIntervalTime = currentTime
            lastIntervalLevel = currentLevel
        }
    }


    override fun getBatteryLevel(): Int {
        return batteryManager.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun isCharging(): Boolean {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                status == AndroidBatteryManager.BATTERY_STATUS_FULL
    }

    override fun isPowerSaveModeEnabled(): Boolean {
        return powerManager.isPowerSaveMode
    }

    override fun getBatteryStatus(): BatteryStatus {
        return BatteryStatus(
            level = getBatteryLevel(),
            isCharging = isCharging(),
            isPowerSavingEnabled = isPowerSaveModeEnabled(),
            appConsumptionRate = getAppBatteryConsumption()
        )
    }

    override fun getAppBatteryConsumption(): Float {
        if (isCharging()) return 0.0f

        val currentLevel = getBatteryLevel()
        val consumedPercentage = (lastBatteryLevel - currentLevel).toFloat().coerceAtLeast(0.0f)
        val appRunningTime = (System.currentTimeMillis() - appStartTime) / (1000.0f * 60.0f * 60.0f)
        lastBatteryLevel = currentLevel

        return if (appRunningTime > (1.0f/60.0f)) consumedPercentage / appRunningTime else 0.0f
    }

    override fun resetConsumptionTracking() {
        val currentLevel = getBatteryLevel()
        appStartTime = System.currentTimeMillis()
        lastBatteryLevel = currentLevel
        lastStateChangeLevel = currentLevel
        foregroundUsage = 0f
        backgroundUsage = 0f
        foregroundDuration = 0
        backgroundDuration = 0
        lastStateChangeTime = appStartTime
        lastIntervalTime = appStartTime
        lastIntervalLevel = currentLevel
        intervalMeasurements.clear()
    }

    override fun getAppBatteryUsageSinceStart(): Float {
        val currentLevel = getBatteryLevel()
        return (startBatteryLevel - currentLevel).coerceAtLeast(0).toFloat()
    }

    override fun getBackgroundBatteryUsage(): Float {
        if (!isInForeground) {
            val currentLevel = getBatteryLevel()
            val currentUsage = (lastStateChangeLevel - currentLevel).coerceAtLeast(0)
            return backgroundUsage + currentUsage
        }
        return backgroundUsage
    }

    override fun getForegroundBatteryUsage(): Float {
        if (isInForeground) {
            val currentLevel = getBatteryLevel()
            val currentUsage = (lastStateChangeLevel - currentLevel).coerceAtLeast(0)
            return foregroundUsage + currentUsage
        }
        return foregroundUsage
    }


    override fun getForegroundDurationMinutes(): Int {
        var totalForegroundMs = foregroundDuration
        if (isInForeground) {
            totalForegroundMs += (System.currentTimeMillis() - lastStateChangeTime)
        }
        return (totalForegroundMs / (1000 * 60)).toInt()
    }

    override fun getBackgroundDurationMinutes(): Int {
        var totalBackgroundMs = backgroundDuration
        if (!isInForeground) {
            totalBackgroundMs += (System.currentTimeMillis() - lastStateChangeTime)
        }
        return (totalBackgroundMs / (1000 * 60)).toInt()
    }

    override fun getTotalDurationMinutes(): Int {
        return ((System.currentTimeMillis() - appStartTime) / (1000 * 60)).toInt()
    }


    override fun getSessionBatteryReport(): BatterySessionReport {
        val currentLevel = getBatteryLevel()
        val totalUsage = (startBatteryLevel - currentLevel).coerceAtLeast(0).toFloat()

        return BatterySessionReport(
            startBatteryLevel = startBatteryLevel,
            endBatteryLevel = currentLevel,
            totalDurationMinutes = getTotalDurationMinutes(),
            appConsumptionPercentage = totalUsage,
            foregroundDurationMinutes = getForegroundDurationMinutes(),
            backgroundDurationMinutes = getBackgroundDurationMinutes()
        )
    }

    override fun getAverageConsumption(intervals: Int?): Float? {
        if (intervalMeasurements.isEmpty()) return null

        val measurementsToUse = intervals?.let {
            intervalMeasurements.takeLast(minOf(it, intervalMeasurements.size))
        } ?: intervalMeasurements

        return measurementsToUse.map { it.consumptionRate }.average().toFloat()
    }

    override fun getIntervalConsumptionData(maxIntervals: Int?): List<Map<String, Any>> {
        val dataToReturn = maxIntervals?.let {
            intervalMeasurements.takeLast(it)
        } ?: intervalMeasurements

        return dataToReturn.map { interval ->
            mapOf(
                "startTimeMillis" to interval.startTime,
                "endTimeMillis" to interval.endTime,
                "startLevel" to interval.startLevel,
                "endLevel" to interval.endLevel,
                "consumptionRate" to interval.consumptionRate
            )
        }
    }
}

actual object BatteryManagerFactory {
    actual fun create(platformContext: Any?): BatteryManager {
        val context = platformContext as? Context
            ?: throw IllegalArgumentException("Android requires Context")
        return BatteryManagerImpl(context)
    }
}