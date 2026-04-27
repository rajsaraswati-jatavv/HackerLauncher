package com.hackerlauncher.modules

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class ScreenDimmerFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var prefs: SharedPreferences
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    private lateinit var statusText: TextView
    private lateinit var opacitySeekBar: SeekBar
    private lateinit var opacityLabel: TextView
    private lateinit var colorTempSeekBar: SeekBar
    private lateinit var colorTempLabel: TextView
    private lateinit var nightModeLabel: TextView

    private var currentOpacity = 0
    private var currentColorTemp = 50 // 0=cool(blue), 100=warm(orange)
    private var isNightMode = false
    private var isOverlayActive = false
    private var autoScheduleEnabled = false
    private var autoOnHour = 22
    private var autoOffHour = 6

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireContext().getSharedPreferences("screen_dimmer", Context.MODE_PRIVATE)
        loadPrefs()

        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        mainLayout.addView(makeTitle("[>] SCREEN DIMMER"))

        // Status
        statusText = makeLabel("[~] Checking overlay permission...")
        mainLayout.addView(statusText)

        // Overlay permission check
        mainLayout.addView(makeButton("CHECK PERMISSION") { checkOverlayPermission() })

        // Divider
        mainLayout.addView(makeDivider())

        // Opacity control
        mainLayout.addView(makeSectionHeader("FILTER OPACITY"))

        opacityLabel = makeLabel("Opacity: $currentOpacity%")
        mainLayout.addView(opacityLabel)

        opacitySeekBar = SeekBar(requireContext()).apply {
            max = 90
            progress = currentOpacity
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentOpacity = progress
                    opacityLabel.text = "Opacity: $progress%"
                    if (isOverlayActive) updateOverlay()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { savePrefs() }
            })
        }
        mainLayout.addView(opacitySeekBar)

        mainLayout.addView(makeDivider())

        // Color temperature
        mainLayout.addView(makeSectionHeader("COLOR TEMPERATURE"))

        colorTempLabel = makeLabel("Temperature: ${tempToLabel(currentColorTemp)}")
        mainLayout.addView(colorTempLabel)

        colorTempSeekBar = SeekBar(requireContext()).apply {
            max = 100
            progress = currentColorTemp
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentColorTemp = progress
                    colorTempLabel.text = "Temperature: ${tempToLabel(progress)}"
                    if (isOverlayActive) updateOverlay()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { savePrefs() }
            })
        }
        mainLayout.addView(colorTempSeekBar)

        mainLayout.addView(makeDivider())

        // Night mode
        mainLayout.addView(makeSectionHeader("NIGHT MODE"))

        nightModeLabel = makeLabel("Night Mode: ${if (isNightMode) "ON" else "OFF"}")
        mainLayout.addView(nightModeLabel)

        mainLayout.addView(makeButton("TOGGLE NIGHT MODE") {
            isNightMode = !isNightMode
            nightModeLabel.text = "Night Mode: ${if (isNightMode) "ON" else "OFF"}"
            if (isOverlayActive) updateOverlay()
            savePrefs()
        })

        mainLayout.addView(makeDivider())

        // Quick toggle buttons
        mainLayout.addView(makeSectionHeader("QUICK TOGGLES"))

        val quickRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        quickRow.addView(makeSmallButton("25%") { setQuickOpacity(25) })
        quickRow.addView(makeSmallButton("50%") { setQuickOpacity(50) })
        quickRow.addView(makeSmallButton("75%") { setQuickOpacity(75) })
        quickRow.addView(makeSmallButton("OFF") { setQuickOpacity(0) })

        mainLayout.addView(quickRow)

        mainLayout.addView(makeDivider())

        // Main toggle
        mainLayout.addView(makeSectionHeader("FILTER CONTROL"))

        mainLayout.addView(makeButton("ENABLE FILTER") { enableOverlay() })
        mainLayout.addView(makeButton("DISABLE FILTER") { disableOverlay() })

        mainLayout.addView(makeDivider())

        // Auto schedule
        mainLayout.addView(makeSectionHeader("AUTO SCHEDULE"))

        val autoLabel = makeLabel("Schedule: ${if (autoScheduleEnabled) "ON" else "OFF"} | ${autoOnHour}:00 - ${autoOffHour}:00")
        mainLayout.addView(autoLabel)

        mainLayout.addView(makeButton("TOGGLE SCHEDULE") {
            autoScheduleEnabled = !autoScheduleEnabled
            autoLabel.text = "Schedule: ${if (autoScheduleEnabled) "ON" else "OFF"} | ${autoOnHour}:00 - ${autoOffHour}:00"
            savePrefs()
        })

        // On time
        val onRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val onEdit = EditText(requireContext()).apply {
            hint = "On hour (0-23)"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 4, 8, 4)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
        }
        onRow.addView(onEdit)
        onRow.addView(makeSmallButton("SET ON") {
            val h = onEdit.text.toString().toIntOrNull()
            if (h != null && h in 0..23) {
                autoOnHour = h
                autoLabel.text = "Schedule: ${if (autoScheduleEnabled) "ON" else "OFF"} | ${autoOnHour}:00 - ${autoOffHour}:00"
                savePrefs()
            } else {
                Toast.makeText(requireContext(), "Invalid hour", Toast.LENGTH_SHORT).show()
            }
        })
        mainLayout.addView(onRow)

        // Off time
        val offRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val offEdit = EditText(requireContext()).apply {
            hint = "Off hour (0-23)"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 4, 8, 4)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
        }
        offRow.addView(offEdit)
        offRow.addView(makeSmallButton("SET OFF") {
            val h = offEdit.text.toString().toIntOrNull()
            if (h != null && h in 0..23) {
                autoOffHour = h
                autoLabel.text = "Schedule: ${if (autoScheduleEnabled) "ON" else "OFF"} | ${autoOnHour}:00 - ${autoOffHour}:00"
                savePrefs()
            } else {
                Toast.makeText(requireContext(), "Invalid hour", Toast.LENGTH_SHORT).show()
            }
        })
        mainLayout.addView(offRow)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        windowManager = requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(requireContext())) {
                statusText.text = "[>] Overlay permission: GRANTED"
            } else {
                statusText.text = "[!] Overlay permission: DENIED - Tap to request"
            }
        } else {
            statusText.text = "[>] Overlay permission: Available"
        }
    }

    private fun requestOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("InlinedApi")
    private fun enableOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
            requestOverlayPermission()
            Toast.makeText(requireContext(), "Grant overlay permission first", Toast.LENGTH_LONG).show()
            return
        }

        try {
            if (overlayView != null) {
                disableOverlay()
            }

            overlayView = View(requireContext()).apply {
                setBackgroundColor(calculateFilterColor())
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(overlayView, params)
            isOverlayActive = true
            statusText.text = "[ON] Filter active - Opacity: $currentOpacity%"
            savePrefs()
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
            Toast.makeText(requireContext(), "Failed to enable filter: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
            }
            overlayView = null
            isOverlayActive = false
            statusText.text = "[OFF] Filter disabled"
            savePrefs()
        } catch (e: Exception) {
            statusText.text = "[!] Error disabling: ${e.message}"
        }
    }

    private fun updateOverlay() {
        try {
            overlayView?.setBackgroundColor(calculateFilterColor())
        } catch (_: Exception) { }
    }

    private fun calculateFilterColor(): Int {
        val alpha = (currentOpacity / 100f * 255).toInt().coerceIn(0, 255)

        if (isNightMode) {
            // Red tint for night mode
            return Color.argb(alpha, 255, 0, 0)
        }

        // Color temperature interpolation
        // 0 = cool (blue tint), 50 = neutral (black), 100 = warm (orange tint)
        val r: Int
        val g: Int
        val b: Int

        when {
            currentColorTemp <= 50 -> {
                // Cool to neutral: blue decreases
                val t = currentColorTemp / 50f
                r = 0
                g = (30 * t).toInt()
                b = ((1 - t) * 80).toInt()
            }
            else -> {
                // Neutral to warm: orange increases
                val t = (currentColorTemp - 50) / 50f
                r = (180 * t).toInt()
                g = (80 * t).toInt()
                b = 0
            }
        }

        return Color.argb(alpha, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun setQuickOpacity(percent: Int) {
        currentOpacity = percent.coerceIn(0, 90)
        opacitySeekBar.progress = currentOpacity
        opacityLabel.text = "Opacity: $currentOpacity%"

        if (currentOpacity == 0) {
            disableOverlay()
        } else if (isOverlayActive) {
            updateOverlay()
        } else {
            enableOverlay()
        }
        savePrefs()
    }

    private fun tempToLabel(temp: Int): String {
        return when {
            temp < 20 -> "Cool (Blue)"
            temp < 40 -> "Slightly Cool"
            temp < 60 -> "Neutral"
            temp < 80 -> "Slightly Warm"
            else -> "Warm (Orange)"
        }
    }

    private fun loadPrefs() {
        currentOpacity = prefs.getInt("opacity", 0)
        currentColorTemp = prefs.getInt("color_temp", 50)
        isNightMode = prefs.getBoolean("night_mode", false)
        autoScheduleEnabled = prefs.getBoolean("auto_schedule", false)
        autoOnHour = prefs.getInt("auto_on_hour", 22)
        autoOffHour = prefs.getInt("auto_off_hour", 6)
    }

    private fun savePrefs() {
        try {
            prefs.edit()
                .putInt("opacity", currentOpacity)
                .putInt("color_temp", currentColorTemp)
                .putBoolean("night_mode", isNightMode)
                .putBoolean("auto_schedule", autoScheduleEnabled)
                .putInt("auto_on_hour", autoOnHour)
                .putInt("auto_off_hour", autoOffHour)
                .apply()
        } catch (_: Exception) { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            overlayView?.let { windowManager?.removeView(it) }
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

    private fun makeSectionHeader(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = "▸ $text"
            setTextColor(Color.parseColor("#FFFF00"))
            textSize = 13f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 12, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeDivider(): View {
        return View(requireContext()).apply {
            setBackgroundColor(MED_GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
        }
    }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            setOnClickListener { onClick() }
        }
    }

    private fun makeSmallButton(text: String, onClick: () -> Unit): Button {
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
