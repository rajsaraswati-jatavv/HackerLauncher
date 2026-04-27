package com.hackerlauncher.modules

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

data class SensorInfo(
    val sensor: Sensor,
    val name: String,
    val vendor: String,
    val version: Int,
    val range: Float,
    val resolution: Float,
    val power: Float,
    val minDelay: Int,
    val type: Int
)

class SensorBoxFragment : Fragment(), SensorEventListener {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private var sensorManager: SensorManager? = null
    private val sensorList = mutableListOf<SensorInfo>()
    private val activeSensors = mutableMapOf<Int, TextView>()
    private val sensorValueViews = mutableMapOf<Int, TextView>()
    private lateinit var appListContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var mainLayout: LinearLayout

    private val sensorTypeNames = mapOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_GYROSCOPE to "Gyroscope",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_LIGHT to "Light",
        Sensor.TYPE_PRESSURE to "Pressure",
        Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
        Sensor.TYPE_AMBIENT_TEMPERATURE to "Temperature",
        Sensor.TYPE_RELATIVE_HUMIDITY to "Humidity",
        Sensor.TYPE_GRAVITY to "Gravity",
        Sensor.TYPE_LINEAR_ACCELERATION to "Linear Accel",
        Sensor.TYPE_ROTATION_VECTOR to "Rotation Vector",
        Sensor.TYPE_STEP_COUNTER to "Step Counter",
        Sensor.TYPE_GAME_ROTATION_VECTOR to "Game Rotation",
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED to "Gyroscope Uncal",
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED to "Magnetometer Uncal",
        Sensor.TYPE_POSE_6DOF to "6DOF Pose",
        Sensor.TYPE_STATIONARY_DETECT to "Stationary Detect",
        Sensor.TYPE_MOTION_DETECT to "Motion Detect",
        Sensor.TYPE_HEART_RATE to "Heart Rate",
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT to "Offbody Detect",
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to "Accel Uncal"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        mainLayout.addView(makeTitle("[>] SENSOR BOX"))

        // Status
        statusText = makeLabel("[~] Scanning sensors...")
        mainLayout.addView(statusText)

        // Buttons
        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnRow.addView(makeButton("TEST ALL") { testAllSensors() })
        btnRow.addView(makeButton("STOP ALL") { stopAllSensors() })
        btnRow.addView(makeButton("REFRESH") { loadSensors() })

        mainLayout.addView(btnRow)

        // Sensor list
        appListContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(appListContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSensors()
    }

    private fun loadSensors() {
        try {
            sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorList.clear()

            val sensors = sensorManager?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
            for (sensor in sensors) {
                sensorList.add(SensorInfo(
                    sensor = sensor,
                    name = sensor.name,
                    vendor = sensor.vendor,
                    version = sensor.version,
                    range = sensor.maximumRange,
                    resolution = sensor.resolution,
                    power = sensor.power,
                    minDelay = sensor.minDelay,
                    type = sensor.type
                ))
            }

            statusText.text = "[>] Found ${sensorList.size} sensors"
            renderSensorList()
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
        }
    }

    private fun renderSensorList() {
        appListContainer.removeAllViews()
        sensorValueViews.clear()

        // Group by sensor type name for better readability
        val grouped = sensorList.groupBy { sensorTypeNames[it.type] ?: "Other (${it.type})" }

        for ((typeName, sensorInfos) in grouped.toList().sortedBy { it.first }) {
            // Group header
            appListContainer.addView(TextView(requireContext()).apply {
                text = "━━━ $typeName (${sensorInfos.size}) ━━━"
                setTextColor(Color.parseColor("#FFFF00"))
                textSize = 13f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setPadding(0, 12, 0, 4)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            for ((index, info) in sensorInfos.withIndex()) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                    setPadding(12, 8, 12, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // Sensor name
                row.addView(TextView(requireContext()).apply {
                    text = info.name
                    setTextColor(GREEN)
                    textSize = 12f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                })

                // Details
                row.addView(TextView(requireContext()).apply {
                    text = "Vendor: ${info.vendor} | v${info.version}"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 10f
                    setTypeface(Typeface.MONOSPACE)
                })

                row.addView(TextView(requireContext()).apply {
                    text = "Range: ${info.range} | Res: ${info.resolution} | Power: ${info.power}mA | Delay: ${info.minDelay}us"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 10f
                    setTypeface(Typeface.MONOSPACE)
                })

                // Real-time value display
                val valueView = TextView(requireContext()).apply {
                    text = "Values: --"
                    setTextColor(Color.parseColor("#00FFFF"))
                    textSize = 12f
                    setTypeface(Typeface.MONOSPACE)
                    tag = "value_${info.type}_${info.name}"
                }
                sensorValueViews[info.type] = valueView
                row.addView(valueView)

                // Test button
                val testBtn = Button(requireContext()).apply {
                    text = "TEST"
                    setTextColor(BLACK)
                    setBackgroundColor(GREEN)
                    textSize = 10f
                    setTypeface(Typeface.MONOSPACE)
                    setPadding(4, 2, 4, 2)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { testSensor(info) }
                }

                val stopBtn = Button(requireContext()).apply {
                    text = "STOP"
                    setTextColor(GREEN)
                    setBackgroundColor(MED_GRAY)
                    textSize = 10f
                    setTypeface(Typeface.MONOSPACE)
                    setPadding(4, 2, 4, 2)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { stopSensor(info) }
                }

                val btnRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                btnRow.addView(testBtn)
                btnRow.addView(stopBtn)
                row.addView(btnRow)

                appListContainer.addView(row)
            }
        }
    }

    private fun testSensor(info: SensorInfo) {
        try {
            sensorManager?.registerListener(
                this,
                info.sensor,
                SensorManager.SENSOR_DELAY_UI
            )
            activeSensors[info.type] = sensorValueViews[info.type]
            statusText.text = "[>] Testing: ${info.name}"
            Toast.makeText(requireContext(), "Testing ${info.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "[!] Error testing sensor: ${e.message}"
        }
    }

    private fun stopSensor(info: SensorInfo) {
        try {
            sensorManager?.unregisterListener(this, info.sensor)
            activeSensors.remove(info.type)
            sensorValueViews[info.type]?.text = "Values: --"
            statusText.text = "[>] Stopped: ${info.name}"
        } catch (e: Exception) {
            statusText.text = "[!] Error stopping sensor: ${e.message}"
        }
    }

    private fun testAllSensors() {
        try {
            var count = 0
            for (info in sensorList) {
                try {
                    sensorManager?.registerListener(this, info.sensor, SensorManager.SENSOR_DELAY_UI)
                    activeSensors[info.type] = sensorValueViews[info.type]!!
                    count++
                } catch (_: Exception) { }
            }
            statusText.text = "[>] Testing $count sensors"
            Toast.makeText(requireContext(), "Testing $count sensors", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
        }
    }

    private fun stopAllSensors() {
        try {
            sensorManager?.unregisterListener(this)
            activeSensors.clear()
            for ((_, view) in sensorValueViews) {
                view.text = "Values: --"
            }
            statusText.text = "[>] All sensors stopped"
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        try {
            val view = activeSensors[event.sensor.type] ?: return
            val values = event.values.joinToString(", ") { "%.4f".format(it) }
            val typeName = sensorTypeNames[event.sensor.type] ?: "Sensor(${event.sensor.type})"
            activity?.runOnUiThread {
                view.text = "Values: [$values]"
            }
        } catch (_: Exception) { }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) { }
    }

    private fun makeTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            textSize = 14f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, 8, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = 4 }
            setOnClickListener { onClick() }
        }
    }
}
