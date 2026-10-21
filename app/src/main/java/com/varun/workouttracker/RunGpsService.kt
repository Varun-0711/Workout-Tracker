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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.Locale

/** Foreground GPS collection for an explicitly started running workout. */
class RunGpsService : Service(), LocationListener {
    private var locationManager: LocationManager? = null
    private var lastAcceptedLocation: Location? = null
    private var distanceMeters = 0.0
    private var lastPaceSecondsPerKm = 0.0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val prefs = preferences(this)
        distanceMeters = prefs.getFloat(KEY_DISTANCE_METERS, 0f).toDouble()
        lastPaceSecondsPerKm = prefs.getFloat(KEY_PACE_SECONDS_PER_KM, 0f).toDouble()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopLocationUpdates()
            preferences(this).edit().putBoolean(KEY_TRACKING, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = preferences(this)
        if (prefs.getBoolean(KEY_RESET_PENDING, false)) {
            distanceMeters = 0.0
            lastPaceSecondsPerKm = 0.0
            lastAcceptedLocation = null
            prefs.edit().putBoolean(KEY_RESET_PENDING, false).apply()
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            preferences(this).edit().putBoolean(KEY_TRACKING, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val providers = if (fineGranted) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)
        try {
            val provider = providers.firstOrNull { candidate ->
                runCatching { locationManager?.isProviderEnabled(candidate) == true }.getOrDefault(false)
            }
            if (provider == null) {
                preferences(this).edit().putBoolean(KEY_TRACKING, false).putString(KEY_STATUS, "location_disabled").apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            locationManager?.requestLocationUpdates(provider, 2_000L, 2f, this)
            preferences(this).edit().putBoolean(KEY_TRACKING, true).putString(KEY_STATUS, "acquiring").apply()
        } catch (_: SecurityException) {
            preferences(this).edit().putBoolean(KEY_TRACKING, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalArgumentException) {
            preferences(this).edit().putBoolean(KEY_TRACKING, false).putString(KEY_STATUS, "location_disabled").apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > MAX_ACCEPTED_ACCURACY_METERS) return
        val previous = lastAcceptedLocation
        if (previous != null) {
            val segmentMeters = previous.distanceTo(location).toDouble()
            val elapsedSeconds = (location.time - previous.time).coerceAtLeast(1L) / 1_000.0
            val maxPlausibleMeters = elapsedSeconds * MAX_RUNNING_SPEED_METERS_PER_SECOND + 10.0
            if (segmentMeters > MIN_SEGMENT_METERS && segmentMeters <= maxPlausibleMeters) {
                distanceMeters += segmentMeters
                val speed = if (location.hasSpeed()) location.speed.toDouble() else segmentMeters / elapsedSeconds
                if (speed in MIN_PACE_SPEED_METERS_PER_SECOND..MAX_RUNNING_SPEED_METERS_PER_SECOND) {
                    lastPaceSecondsPerKm = 1_000.0 / speed
                } else if (speed < MIN_PACE_SPEED_METERS_PER_SECOND) {
                    lastPaceSecondsPerKm = 0.0
                }
                lastAcceptedLocation = location
            } else {
                lastAcceptedLocation = location
            }
        } else {
            lastAcceptedLocation = location
        }

        preferences(this).edit()
            .putFloat(KEY_DISTANCE_METERS, distanceMeters.toFloat())
            .putFloat(KEY_PACE_SECONDS_PER_KM, lastPaceSecondsPerKm.toFloat())
            .putBoolean(KEY_HAS_FIX, true)
            .putString(KEY_STATUS, "tracking")
            .apply()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) {
        preferences(this).edit().putString(KEY_STATUS, "location_disabled").apply()
    }
    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopLocationUpdates() {
        runCatching { locationManager?.removeUpdates(this) }
    }

    private fun buildNotification(): Notification {
        val launch = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 17, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val km = preferences(this).getFloat(KEY_DISTANCE_METERS, distanceMeters.toFloat()) / 1_000f
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Run GPS tracking")
            .setContentText(String.format(Locale.getDefault(), "%.2f km · recording your run", km))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Run GPS tracking", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_START = "com.varun.workouttracker.action.START_RUN_GPS"
        const val ACTION_STOP = "com.varun.workouttracker.action.STOP_RUN_GPS"
        const val PREFS = "workout_run_gps"
        const val KEY_DISTANCE_METERS = "distance_meters"
        const val KEY_PACE_SECONDS_PER_KM = "pace_seconds_per_km"
        const val KEY_TRACKING = "tracking"
        const val KEY_HAS_FIX = "has_fix"
        const val KEY_STATUS = "status"
        private const val KEY_RESET_PENDING = "reset_pending"
        private const val CHANNEL_ID = "run_gps_tracking"
        private const val NOTIFICATION_ID = 4103
        private const val MAX_ACCEPTED_ACCURACY_METERS = 100f
        private const val MIN_SEGMENT_METERS = 1.5
        private const val MIN_PACE_SPEED_METERS_PER_SECOND = 0.5
        private const val MAX_RUNNING_SPEED_METERS_PER_SECOND = 8.0

        fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun resetSession(context: Context) {
            preferences(context).edit()
                .putFloat(KEY_DISTANCE_METERS, 0f)
                .putFloat(KEY_PACE_SECONDS_PER_KM, 0f)
                .putBoolean(KEY_HAS_FIX, false)
                .putBoolean(KEY_TRACKING, false)
                .putBoolean(KEY_RESET_PENDING, true)
                .putString(KEY_STATUS, "idle")
                .apply()
        }
    }
}
