package com.hackerlauncher.services

import com.hackerlauncher.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * LocationTrackerService - Background location tracker for HackerLauncher.
 *
 * Uses FusedLocationProviderClient for accurate location tracking,
 * records location history, supports geofencing, location-based app
 * launching, route/path recording, KML/GPX export, configurable
 * update intervals, and battery-optimized location requests.
 */
class LocationTrackerService : Service() {

    companion object {
        const val TAG = "LocationTrackerService"
        const val CHANNEL_ID = "location_tracker"
        const val NOTIFICATION_ID = 1005
        const val ACTION_START = "com.hackerlauncher.ACTION_START_LOCATION_TRACKER"
        const val ACTION_STOP = "com.hackerlauncher.ACTION_STOP_LOCATION_TRACKER"
        const val ACTION_SET_INTERVAL = "com.hackerlauncher.ACTION_SET_INTERVAL"
        const val ACTION_EXPORT_KML = "com.hackerlauncher.ACTION_EXPORT_KML"
        const val ACTION_EXPORT_GPX = "com.hackerlauncher.ACTION_EXPORT_GPX"
        const val ACTION_ADD_GEOFENCE = "com.hackerlauncher.ACTION_ADD_GEOFENCE"
        const val ACTION_REMOVE_GEOFENCE = "com.hackerlauncher.ACTION_REMOVE_GEOFENCE"
        const val ACTION_START_ROUTE = "com.hackerlauncher.ACTION_START_ROUTE"
        const val ACTION_STOP_ROUTE = "com.hackerlauncher.ACTION_STOP_ROUTE"

        const val ACTION_LOCATION_UPDATE = "com.hackerlauncher.ACTION_LOCATION_UPDATE"
        const val ACTION_GEOFENCE_EVENT = "com.hackerlauncher.ACTION_GEOFENCE_EVENT"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_BEARING = "bearing"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_GEOFENCE_ID = "geofence_id"
        const val EXTRA_GEOFENCE_LAT = "geofence_lat"
        const val EXTRA_GEOFENCE_LNG = "geofence_lng"
        const val EXTRA_GEOFENCE_RADIUS = "geofence_radius"
        const val EXTRA_GEOFENCE_TRANSITION = "geofence_transition"
        const val EXTRA_GEOFENCE_NAME = "geofence_name"

        const val PREFS_NAME = "location_tracker_prefs"
        const val KEY_HISTORY = "location_history"
        const val KEY_INTERVAL = "update_interval_ms"
        const val KEY_ROUTE_ACTIVE = "route_active"
        const val KEY_ROUTE_POINTS = "route_points"
        const val KEY_GEOFENCES = "geofences"
        const val KEY_LAUNCH_RULES = "launch_rules"
        const val MAX_HISTORY_ENTRIES = 10000

        const val INTERVAL_FAST = 1_000L    // 1 second
        const val INTERVAL_NORMAL = 5_000L   // 5 seconds
        const val INTERVAL_SLOW = 30_000L    // 30 seconds
        const val INTERVAL_BATTERY = 60_000L // 60 seconds

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var notificationUpdateJob: Job? = null
    private var historySaveJob: Job? = null
    private var routeTrackingJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var prefs: SharedPreferences

    private var updateInterval = INTERVAL_NORMAL
    private var currentLocation: Location? = null
    private var isRouteActive = false
    private var routeDistance = 0f
    private var routeStartTime = 0L
    private var lastRouteLocation: Location? = null
    private val activeGeofenceIds = mutableSetOf<String>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                handleLocationUpdate(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "LocationTrackerService onCreate")
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        updateInterval = prefs.getLong(KEY_INTERVAL, INTERVAL_NORMAL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "LocationTrackerService onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopTracker()
                return START_NOT_STICKY
            }
            ACTION_SET_INTERVAL -> {
                val interval = intent.getLongExtra(EXTRA_INTERVAL_MS, INTERVAL_NORMAL)
                setUpdateInterval(interval)
            }
            ACTION_EXPORT_KML -> {
                exportAsKML()
            }
            ACTION_EXPORT_GPX -> {
                exportAsGPX()
            }
            ACTION_ADD_GEOFENCE -> {
                val id = intent.getStringExtra(EXTRA_GEOFENCE_ID) ?: UUID.randomUUID().toString()
                val lat = intent.getDoubleExtra(EXTRA_GEOFENCE_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_GEOFENCE_LNG, 0.0)
                val radius = intent.getFloatExtra(EXTRA_GEOFENCE_RADIUS, 100f)
                val name = intent.getStringExtra(EXTRA_GEOFENCE_NAME) ?: "Geofence"
                addGeofence(id, lat, lng, radius, name)
            }
            ACTION_REMOVE_GEOFENCE -> {
                val id = intent.getStringExtra(EXTRA_GEOFENCE_ID) ?: return START_STICKY
                removeGeofence(id)
            }
            ACTION_START_ROUTE -> {
                startRouteRecording()
            }
            ACTION_STOP_ROUTE -> {
                stopRouteRecording()
            }
            ACTION_START, null -> {
                startTracker()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "LocationTrackerService onDestroy")
        stopTracker()
        serviceScope.cancel()
    }

    // ─── Service Lifecycle ────────────────────────────────────────────

    private fun startTracker() {
        if (isRunning) {
            Logger.d(TAG, "LocationTrackerService already running")
            return
        }

        isRunning = true
        Logger.i(TAG, "LocationTrackerService starting...")

        startForeground(NOTIFICATION_ID, buildNotification("Initializing location tracker..."))
        requestLocationUpdates()

        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(5_000L)
            }
        }

        historySaveJob = serviceScope.launch {
            while (isActive) {
                checkLocationBasedLaunchRules()
                delay(10_000L)
            }
        }

        // Restore route if was active
        if (prefs.getBoolean(KEY_ROUTE_ACTIVE, false)) {
            isRouteActive = true
            routeStartTime = System.currentTimeMillis()
            Logger.i(TAG, "Restored active route recording")
        }

        Logger.i(TAG, "LocationTrackerService started successfully")
    }

    private fun stopTracker() {
        Logger.i(TAG, "LocationTrackerService stopping...")
        isRunning = false
        stopLocationUpdates()
        notificationUpdateJob?.cancel()
        historySaveJob?.cancel()
        routeTrackingJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i(TAG, "LocationTrackerService stopped")
    }

    // ─── Location Updates ─────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun requestLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                updateInterval
            ).apply {
                setMinUpdateDistanceMeters(1f)
                setGranularity(com.google.android.gms.location.Granularity.GRANULARITY_FINE)
                setWaitForAccurateLocation(true)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )

            Logger.i(TAG, "Location updates requested with interval ${updateInterval}ms")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to request location updates", e)
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Logger.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to stop location updates", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        currentLocation = location
        Logger.d(TAG, "Location update: ${location.latitude}, ${location.longitude} " +
                "(acc: ${"%.1f".format(location.accuracy)}m)")

        // Record to history
        recordLocation(location)

        // Update route tracking
        if (isRouteActive) {
            updateRouteTracking(location)
        }

        // Send broadcast
        sendLocationBroadcast(location)
    }

    private fun setUpdateInterval(intervalMs: Long) {
        val validInterval = when (intervalMs) {
            INTERVAL_FAST, INTERVAL_NORMAL, INTERVAL_SLOW, INTERVAL_BATTERY -> intervalMs
            else -> INTERVAL_NORMAL
        }

        updateInterval = validInterval
        prefs.edit().putLong(KEY_INTERVAL, validInterval).apply()
        Logger.i(TAG, "Update interval set to ${validInterval}ms")

        if (isRunning) {
            stopLocationUpdates()
            requestLocationUpdates()
        }
    }

    // ─── Location History ─────────────────────────────────────────────

    private fun recordLocation(location: Location) {
        try {
            val history = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")

            val entry = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy.toDouble())
                put("altitude", location.altitude)
                put("speed", location.speed.toDouble())
                put("bearing", location.bearing.toDouble())
                put("timestamp", location.time)
                put("provider", location.provider ?: "unknown")
            }

            history.put(entry)

            while (history.length() > MAX_HISTORY_ENTRIES) {
                history.remove(0)
            }

            prefs.edit().putString(KEY_HISTORY, history.toString()).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to record location", e)
        }
    }

    // ─── Route Tracking ───────────────────────────────────────────────

    private fun startRouteRecording() {
        isRouteActive = true
        routeDistance = 0f
        routeStartTime = System.currentTimeMillis()
        lastRouteLocation = currentLocation

        prefs.edit()
            .putBoolean(KEY_ROUTE_ACTIVE, true)
            .putString(KEY_ROUTE_POINTS, JSONArray().toString())
            .apply()

        Logger.i(TAG, "Route recording started")

        routeTrackingJob = serviceScope.launch {
            while (isActive && isRouteActive) {
                currentLocation?.let { location ->
                    saveRoutePoint(location)
                }
                delay(updateInterval)
            }
        }
    }

    private fun stopRouteRecording() {
        isRouteActive = false
        routeTrackingJob?.cancel()

        prefs.edit().putBoolean(KEY_ROUTE_ACTIVE, false).apply()

        val duration = System.currentTimeMillis() - routeStartTime
        Logger.i(TAG, "Route recording stopped. Distance: ${"%.1f".format(routeDistance)}m, " +
                "Duration: ${duration / 1000}s")
    }

    private fun updateRouteTracking(location: Location) {
        lastRouteLocation?.let { last ->
            val distance = last.distanceTo(location)
            routeDistance += distance
        }
        lastRouteLocation = location
    }

    private fun saveRoutePoint(location: Location) {
        try {
            val points = JSONArray(prefs.getString(KEY_ROUTE_POINTS, "[]") ?: "[]")

            val point = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("altitude", location.altitude)
                put("timestamp", location.time)
                put("speed", location.speed.toDouble())
            }

            points.put(point)
            prefs.edit().putString(KEY_ROUTE_POINTS, points.toString()).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save route point", e)
        }
    }

    // ─── Geofencing ───────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun addGeofence(id: String, lat: Double, lng: Double, radius: Float, name: String) {
        try {
            val geofence = Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(lat, lng, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setNotificationResponsiveness(0)
                .build()

            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            val pendingIntent = createGeofencePendingIntent(id)

            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    activeGeofenceIds.add(id)
                    saveGeofenceConfig(id, lat, lng, radius, name)
                    Logger.i(TAG, "Geofence added: $name at ($lat, $lng) radius ${radius}m")
                }
                .addOnFailureListener { e ->
                    Logger.e(TAG, "Failed to add geofence: $name", e)
                }
        } catch (e: Exception) {
            Logger.e(TAG, "Error adding geofence", e)
        }
    }

    private fun removeGeofence(id: String) {
        try {
            geofencingClient.removeGeofences(listOf(id))
                .addOnSuccessListener {
                    activeGeofenceIds.remove(id)
                    removeGeofenceConfig(id)
                    Logger.i(TAG, "Geofence removed: $id")
                }
                .addOnFailureListener { e ->
                    Logger.e(TAG, "Failed to remove geofence: $id", e)
                }
        } catch (e: Exception) {
            Logger.e(TAG, "Error removing geofence", e)
        }
    }

    private fun createGeofencePendingIntent(geofenceId: String): PendingIntent {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_GEOFENCE_ID, geofenceId)
        }
        return PendingIntent.getBroadcast(
            this, geofenceId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun saveGeofenceConfig(id: String, lat: Double, lng: Double, radius: Float, name: String) {
        try {
            val geofences = JSONArray(prefs.getString(KEY_GEOFENCES, "[]") ?: "[]")
            val geofence = JSONObject().apply {
                put("id", id)
                put("lat", lat)
                put("lng", lng)
                put("radius", radius)
                put("name", name)
            }
            geofences.put(geofence)
            prefs.edit().putString(KEY_GEOFENCES, geofences.toString()).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save geofence config", e)
        }
    }

    private fun removeGeofenceConfig(id: String) {
        try {
            val geofences = JSONArray(prefs.getString(KEY_GEOFENCES, "[]") ?: "[]")
            val updated = JSONArray()
            for (i in 0 until geofences.length()) {
                val obj = geofences.getJSONObject(i)
                if (obj.optString("id") != id) {
                    updated.put(obj)
                }
            }
            prefs.edit().putString(KEY_GEOFENCES, updated.toString()).apply()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove geofence config", e)
        }
    }

    // ─── Location-Based App Launching ─────────────────────────────────

    private fun checkLocationBasedLaunchRules() {
        currentLocation?.let { location ->
            try {
                val rules = JSONArray(prefs.getString(KEY_LAUNCH_RULES, "[]") ?: "[]")
                for (i in 0 until rules.length()) {
                    val rule = rules.getJSONObject(i)
                    val lat = rule.optDouble("lat", 0.0)
                    val lng = rule.optDouble("lng", 0.0)
                    val radius = rule.optDouble("radius", 100.0)
                    val packageName = rule.optString("packageName", "")

                    if (lat != 0.0 && lng != 0.0 && packageName.isNotEmpty()) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            lat, lng, results
                        )
                        if (results[0] <= radius) {
                            Logger.i(TAG, "Location-based launch: $packageName (within ${results[0]}m)")
                            launchApp(packageName)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check launch rules", e)
            }
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Logger.i(TAG, "Launched app: $packageName")
            } else {
                Logger.w(TAG, "Cannot launch app: $packageName (no launch intent)")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to launch app: $packageName", e)
        }
    }

    // ─── Export ───────────────────────────────────────────────────────

    private fun exportAsKML() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val history = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
                if (history.length() == 0) {
                    Logger.w(TAG, "No location history to export")
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val sb = StringBuilder()
                sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                sb.appendLine("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
                sb.appendLine("  <Document>")
                sb.appendLine("    <name>HackerLauncher Location History</name>")
                sb.appendLine("    <description>Exported on ${dateFormat.format(Date())}</description>")

                for (i in 0 until history.length()) {
                    val entry = history.getJSONObject(i)
                    val lat = entry.optDouble("latitude", 0.0)
                    val lng = entry.optDouble("longitude", 0.0)
                    val alt = entry.optDouble("altitude", 0.0)
                    val timestamp = entry.optLong("timestamp", 0)

                    sb.appendLine("    <Placemark>")
                    sb.appendLine("      <name>Point $i</name>")
                    sb.appendLine("      <description>Timestamp: ${dateFormat.format(Date(timestamp))}</description>")
                    sb.appendLine("      <Point>")
                    sb.appendLine("        <coordinates>$lng,$lat,$alt</coordinates>")
                    sb.appendLine("      </Point>")
                    sb.appendLine("    </Placemark>")
                }

                sb.appendLine("  </Document>")
                sb.appendLine("</kml>")

                writeExportFile("location_history.kml", sb.toString())
                Logger.i(TAG, "KML export complete")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to export KML", e)
            }
        }
    }

    private fun exportAsGPX() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val history = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
                if (history.length() == 0) {
                    Logger.w(TAG, "No location history to export")
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val sb = StringBuilder()
                sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                sb.appendLine("<gpx version=\"1.1\" creator=\"HackerLauncher\"")
                sb.appendLine("  xmlns=\"http://www.topografix.com/GPX/1/1\">")
                sb.appendLine("  <metadata>")
                sb.appendLine("    <name>HackerLauncher Location History</name>")
                sb.appendLine("    <time>${dateFormat.format(Date())}</time>")
                sb.appendLine("  </metadata>")
                sb.appendLine("  <trk>")
                sb.appendLine("    <name>Location Track</name>")
                sb.appendLine("    <trkseg>")

                for (i in 0 until history.length()) {
                    val entry = history.getJSONObject(i)
                    val lat = entry.optDouble("latitude", 0.0)
                    val lng = entry.optDouble("longitude", 0.0)
                    val alt = entry.optDouble("altitude", 0.0)
                    val timestamp = entry.optLong("timestamp", 0)

                    sb.appendLine("      <trkpt lat=\"$lat\" lon=\"$lng\">")
                    sb.appendLine("        <ele>$alt</ele>")
                    sb.appendLine("        <time>${dateFormat.format(Date(timestamp))}</time>")
                    sb.appendLine("      </trkpt>")
                }

                sb.appendLine("    </trkseg>")
                sb.appendLine("  </trk>")
                sb.appendLine("</gpx>")

                writeExportFile("location_history.gpx", sb.toString())
                Logger.i(TAG, "GPX export complete")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to export GPX", e)
            }
        }
    }

    private fun writeExportFile(filename: String, content: String) {
        val exportDir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, filename)
        FileWriter(file).use { writer ->
            writer.write(content)
        }
        Logger.i(TAG, "Export file written: ${file.absolutePath}")
    }

    // ─── Broadcast ────────────────────────────────────────────────────

    private fun sendLocationBroadcast(location: Location) {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_ACCURACY, location.accuracy)
            putExtra(EXTRA_ALTITUDE, location.altitude)
            putExtra(EXTRA_SPEED, location.speed)
            putExtra(EXTRA_BEARING, location.bearing)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HackerLauncher location tracker status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, LocationTrackerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exportKmlIntent = Intent(this, LocationTrackerService::class.java).apply {
            action = ACTION_EXPORT_KML
        }
        val exportKmlPendingIntent = PendingIntent.getService(
            this, 2, exportKmlIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracker")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_save,
                "Export KML",
                exportKmlPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val text = currentLocation?.let { loc ->
                val coords = "${String.format("%.5f", loc.latitude)}, ${String.format("%.5f", loc.longitude)}"
                val acc = "${String.format("%.0f", loc.accuracy)}m"
                val routeInfo = if (isRouteActive) {
                    " | Route: ${String.format("%.0f", routeDistance)}m"
                } else ""
                "$coords (±$acc)$routeInfo"
            } ?: "Waiting for location..."

            val notification = buildNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update notification", e)
        }
    }
}

/**
 * BroadcastReceiver for geofence transition events.
 */
class GeofenceBroadcastReceiver : android.content.BroadcastReceiver() {

    companion object {
        const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofenceId = intent.getStringExtra(LocationTrackerService.EXTRA_GEOFENCE_ID)
        val transition = intent.getIntExtra(
            LocationTrackerService.EXTRA_GEOFENCE_TRANSITION,
            -1
        )

        val transitionType = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            else -> "UNKNOWN"
        }

        Logger.i(LocationTrackerService.TAG, "Geofence $transitionType: $geofenceId")

        // Send local broadcast
        val broadcastIntent = Intent(LocationTrackerService.ACTION_GEOFENCE_EVENT).apply {
            putExtra(LocationTrackerService.EXTRA_GEOFENCE_ID, geofenceId)
            putExtra(LocationTrackerService.EXTRA_GEOFENCE_TRANSITION, transition)
            setPackage(context.packageName)
        }
        context.sendBroadcast(broadcastIntent)
    }
}
