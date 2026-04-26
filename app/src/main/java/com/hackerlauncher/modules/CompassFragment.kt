package com.hackerlauncher.modules

import com.hackerlauncher.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class CompassFragment : Fragment(), SensorEventListener, LocationListener {

    private lateinit var compassImage: ImageView
    private lateinit var tvDegrees: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvGpsCoords: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvAccuracy: TextView

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var locationManager: LocationManager? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var currentAzimuth = 0f
    private var currentPitch = 0f
    private var currentRoll = 0f

    private var currentLocation: Location? = null

    private val fineLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates()
    }

    // Compass drawing constants
    private val greenColor = Color.parseColor("#00FF00")
    private val darkGreenColor = Color.parseColor("#005500")
    private val dimGreenColor = Color.parseColor("#003300")
    private val blackColor = Color.parseColor("#000000")
    private val redColor = Color.parseColor("#FF0000")
    private val bgColor = Color.parseColor("#0A0A0A")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_compass, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        compassImage = view.findViewById(R.id.compassImage)
        tvDegrees = view.findViewById(R.id.tvDegrees)
        tvDirection = view.findViewById(R.id.tvDirection)
        tvGpsCoords = view.findViewById(R.id.tvGpsCoords)
        tvAltitude = view.findViewById(R.id.tvAltitude)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        tvAccuracy = view.findViewById(R.id.tvAccuracy)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Request location permission
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        drawCompass(0f)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 1f, this
            )
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 1000L, 1f, this
            )
            // Try to get last known location
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                onLocationChanged(it)
            }
            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                onLocationChanged(it)
            }
        } catch (e: SecurityException) {
            tvGpsCoords.text = "Location access denied"
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        val lat = location.latitude
        val lon = location.longitude
        tvGpsCoords.text = String.format("%.6f, %.6f", lat, lon)
        tvAltitude.text = if (location.hasAltitude())
            String.format("Alt: %.1f m", location.altitude) else "Alt: N/A"
        tvSpeed.text = if (location.hasSpeed())
            String.format("Spd: %.1f m/s", location.speed) else "Spd: N/A"
        tvAccuracy.text = if (location.hasAccuracy())
            String.format("Acc: %.1f m", location.accuracy) else "Acc: N/A"
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
            }
        }

        if (hasGravity && hasGeomagnetic) {
            val rotationMatrix = FloatArray(9)
            val inclinationMatrix = FloatArray(9)
            val success = SensorManager.getRotationMatrix(
                rotationMatrix, inclinationMatrix, gravity, geomagnetic
            )

            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // Convert radians to degrees
                currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                currentPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                currentRoll = Math.toDegrees(orientation[2].toDouble()).toFloat()

                // Normalize azimuth to 0-360
                if (currentAzimuth < 0) currentAzimuth += 360f

                updateCompassUI()
            }
        }
    }

    private fun updateCompassUI() {
        tvDegrees.text = String.format("%.1f°", currentAzimuth)
        tvDirection.text = getDirectionString(currentAzimuth)
        drawCompass(currentAzimuth)
    }

    private fun getDirectionString(degrees: Float): String {
        return when {
            degrees in 0f..11.25f || degrees > 348.75f -> "N"
            degrees in 11.25f..33.75f -> "NNE"
            degrees in 33.75f..56.25f -> "NE"
            degrees in 56.25f..78.75f -> "ENE"
            degrees in 78.75f..101.25f -> "E"
            degrees in 101.25f..123.75f -> "ESE"
            degrees in 123.75f..146.25f -> "SE"
            degrees in 146.25f..168.75f -> "SSE"
            degrees in 168.75f..191.25f -> "S"
            degrees in 191.25f..213.75f -> "SSW"
            degrees in 213.75f..236.25f -> "SW"
            degrees in 236.25f..258.75f -> "WSW"
            degrees in 258.75f..281.25f -> "W"
            degrees in 281.25f..303.75f -> "WNW"
            degrees in 303.75f..326.25f -> "NW"
            degrees in 326.25f..348.75f -> "NNW"
            else -> "N"
        }
    }

    private fun drawCompass(azimuth: Float) {
        val size = 800
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.42f

        // Background
        canvas.drawColor(bgColor)

        // Outer glow ring
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - radius, cy - radius, cx + radius, cy + radius,
                dimGreenColor, darkGreenColor, Shader.TileMode.CLAMP
            )
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(cx, cy, radius + 8, glowPaint)

        // Main circle
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blackColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // Circle border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Inner decorative circle
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dimGreenColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawCircle(cx, cy, radius * 0.75f, innerBorderPaint)

        // Rotate canvas for compass heading
        canvas.save()
        canvas.rotate(-azimuth, cx, cy)

        // Draw degree ticks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val tickPaintMajor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        for (i in 0 until 360 step 2) {
            val rad = Math.toRadians(i.toDouble())
            val isMajor = i % 30 == 0
            val isMinor = i % 10 == 0
            val innerR = if (isMajor) radius * 0.85f
            else if (isMinor) radius * 0.88f
            else radius * 0.92f
            val outerR = radius * 0.96f

            val paint = if (isMajor) tickPaintMajor else tickPaint
            val startX = cx + (innerR * Math.sin(rad)).toFloat()
            val startY = cy - (innerR * Math.cos(rad)).toFloat()
            val endX = cx + (outerR * Math.sin(rad)).toFloat()
            val endY = cy - (outerR * Math.cos(rad)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, paint)
        }

        // Draw cardinal direction labels
        val cardinals = arrayOf("N", "E", "S", "W")
        val cardinalAngles = floatArrayOf(0f, 90f, 180f, 270f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenColor
            textSize = 48f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = redColor
            textSize = 52f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        for (i in cardinals.indices) {
            val rad = Math.toRadians(cardinalAngles[i].toDouble())
            val labelR = radius * 0.72f
            val x = cx + (labelR * Math.sin(rad)).toFloat()
            val y = cy - (labelR * Math.cos(rad)).toFloat() + 16f

            val paint = if (i == 0) northPaint else textPaint
            canvas.drawText(cardinals[i], x, y, paint)
        }

        // Draw intercardinal labels
        val intercardinals = arrayOf("NE", "SE", "SW", "NW")
        val interAngles = floatArrayOf(45f, 135f, 225f, 315f)
        val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkGreenColor
            textSize = 28f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        for (i in intercardinals.indices) {
            val rad = Math.toRadians(interAngles[i].toDouble())
            val labelR = radius * 0.72f
            val x = cx + (labelR * Math.sin(rad)).toFloat()
            val y = cy - (labelR * Math.cos(rad)).toFloat() + 10f
            canvas.drawText(intercardinals[i], x, y, smallTextPaint)
        }

        // Draw degree numbers
        val degreeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dimGreenColor
            textSize = 22f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        for (deg in 0 until 360 step 30) {
            if (deg % 90 == 0) continue // Skip cardinal positions
            val rad = Math.toRadians(deg.toDouble())
            val labelR = radius * 0.6f
            val x = cx + (labelR * Math.sin(rad)).toFloat()
            val y = cy - (labelR * Math.cos(rad)).toFloat() + 8f
            canvas.drawText("${deg}°", x, y, degreeTextPaint)
        }

        // Draw crosshair lines
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dimGreenColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawLine(cx, cy - radius * 0.45f, cx, cy + radius * 0.45f, crossPaint)
        canvas.drawLine(cx - radius * 0.45f, cy, cx + radius * 0.45f, cy, crossPaint)

        canvas.restore()

        // Draw north indicator triangle (fixed at top)
        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = redColor
            style = Paint.Style.FILL
        }
        val trianglePath = Path().apply {
            moveTo(cx, cy - radius - 24)
            lineTo(cx - 14, cy - radius + 4)
            lineTo(cx + 14, cy - radius + 4)
            close()
        }
        canvas.drawPath(trianglePath, indicatorPaint)

        // Draw center dot
        val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 8f, centerDotPaint)

        // Draw center ring
        canvas.drawCircle(cx, cy, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })

        compassImage.setImageBitmap(bitmap)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No-op
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sensorManager?.unregisterListener(this)
        locationManager?.removeUpdates(this)
    }
}
