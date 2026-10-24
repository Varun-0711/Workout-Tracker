package com.varun.workouttracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the phone's hardware step counter registered while the app is in the background. */
class StepCounterService : Service(), SensorEventListener {
    private var stepSensor: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var lastSensorTotal = -1L
    private var stepsToday = 0L
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val prefs = stepPreferences(this)
        val today = todayKey()
        if (prefs.getString(KEY_DATE, null) != today) {
            stepsToday = 0L
            // Keep the sensor's cumulative total across midnight. Reset only today's count.
            prefs.edit().putString(KEY_DATE, today).putLong(KEY_STEPS, 0L).apply()
        } else {
            stepsToday = prefs.getLong(KEY_STEPS, 0L)
        }
        lastSensorTotal = prefs.getLong(KEY_RAW_TOTAL, -1L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stepPreferences(this).edit().putBoolean(KEY_TRACKING, false).apply()
            sensorManager?.unregisterListener(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stepPreferences(this).edit().putBoolean(KEY_TRACKING, false).putString(KEY_STATUS, "permission_denied").apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val sensor = stepSensor
        if (sensor == null) {
            stepPreferences(this).edit().putBoolean(KEY_TRACKING, false).putBoolean(KEY_SENSOR_AVAILABLE, false).putString(KEY_STATUS, "sensor_unavailable").apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        stepPreferences(this).edit().putBoolean(KEY_TRACKING, true).putBoolean(KEY_SENSOR_AVAILABLE, true).putString(KEY_STATUS, "starting").apply()
        sensorManager?.unregisterListener(this)
        val registered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        if (!registered) {
            stepPreferences(this).edit().putBoolean(KEY_TRACKING, false).putString(KEY_STATUS, "sensor_registration_failed").apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val rawTotal = event?.values?.firstOrNull()?.toLong() ?: return
        val prefs = stepPreferences(this)
        val today = todayKey()
        if (prefs.getString(KEY_DATE, null) != today) {
            stepsToday = 0L
            // Sensor total is cumulative since reboot; retaining its baseline avoids losing
            // steps taken after midnight when the service was not active at that moment.
            if (lastSensorTotal < 0L || rawTotal < lastSensorTotal) lastSensorTotal = rawTotal
        } else if (lastSensorTotal < 0L) {
            lastSensorTotal = rawTotal
        } else if (rawTotal >= lastSensorTotal) {
            stepsToday += rawTotal - lastSensorTotal
            lastSensorTotal = rawTotal
        } else {
            // The hardware total resets after a device reboot. Keep today's saved steps and
            // establish a new baseline so the reset is not interpreted as negative steps.
            lastSensorTotal = rawTotal
        }
        prefs.edit()
            .putString(KEY_DATE, today)
            .putLong(KEY_STEPS, stepsToday)
            .putLong(KEY_RAW_TOTAL, lastSensorTotal)
            .putString(KEY_STATUS, "active")
            .apply()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = stepPreferences(this).getLong(KEY_STEPS, stepsToday)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_step_tracking_notification)
            .setContentTitle("Step tracking is on")
            .setContentText("$count steps today · tap to open Workout Tracker")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Step tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.varun.workouttracker.action.START_STEP_TRACKING"
        const val ACTION_STOP = "com.varun.workouttracker.action.STOP_STEP_TRACKING"
        const val PREFS = "workout_step_counter"
        const val KEY_STEPS = "steps_today"
        const val KEY_DATE = "steps_date"
        const val KEY_RAW_TOTAL = "sensor_total"
        const val KEY_TRACKING = "tracking_enabled"
        const val KEY_SENSOR_AVAILABLE = "sensor_available"
        const val KEY_STATUS = "tracking_status"
        const val KEY_GOAL_STEPS = "goal_steps"
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 4102

        fun stepPreferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun isSupported(context: Context): Boolean =
            (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
                .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
}
