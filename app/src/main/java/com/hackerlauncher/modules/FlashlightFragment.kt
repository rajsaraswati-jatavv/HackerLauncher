package com.hackerlauncher.modules

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FlashlightFragment : Fragment() {

    private lateinit var toggleButton: ToggleButton
    private lateinit var buttonStrobe: ToggleButton
    private lateinit var buttonSos: ToggleButton
    private lateinit var buttonDisco: ToggleButton
    private lateinit var seekBarStrobeSpeed: SeekBar
    private lateinit var textViewStrobeSpeed: TextView
    private lateinit var editTextAutoOff: EditText
    private lateinit var buttonAutoOff: Button
    private lateinit var buttonBrightScreen: ToggleButton
    private lateinit var textViewStatus: TextView
    private lateinit var layoutBrightScreen: LinearLayout

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isFlashOn = false
    private var isFlashAvailable = false

    private var strobeJob: Job? = null
    private var sosJob: Job? = null
    private var discoJob: Job? = null
    private var autoOffJob: Job? = null

    private var strobeSpeed = 100L // milliseconds
    private val handler = Handler(Looper.getMainLooper())

    // SOS pattern: ... --- ... (3 short, 3 long, 3 short)
    private val sosPattern = listOf(
        // S: 3 short
        200L, 200L, 200L, 200L, 200L, 400L,
        // O: 3 long
        600L, 200L, 600L, 200L, 600L, 400L,
        // S: 3 short
        200L, 200L, 200L, 200L, 200L, 800L
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_flashlight, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleButton = view.findViewById(R.id.toggleFlashlight)
        buttonStrobe = view.findViewById(R.id.toggleStrobe)
        buttonSos = view.findViewById(R.id.toggleSos)
        buttonDisco = view.findViewById(R.id.toggleDisco)
        seekBarStrobeSpeed = view.findViewById(R.id.seekBarStrobeSpeed)
        textViewStrobeSpeed = view.findViewById(R.id.textViewStrobeSpeed)
        editTextAutoOff = view.findViewById(R.id.editTextAutoOffMinutes)
        buttonAutoOff = view.findViewById(R.id.buttonSetAutoOff)
        buttonBrightScreen = view.findViewById(R.id.toggleBrightScreen)
        textViewStatus = view.findViewById(R.id.textViewStatus)
        layoutBrightScreen = view.findViewById(R.id.layoutBrightScreen)

        initCamera()
        setupToggle()
        setupStrobe()
        setupSos()
        setupDisco()
        setupAutoOff()
        setupBrightScreen()
    }

    private fun initCamera() {
        cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (id in cameraManager!!.cameraIdList) {
                val characteristics = cameraManager!!.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) {
                    cameraId = id
                    isFlashAvailable = true
                    break
                }
            }
            if (!isFlashAvailable) {
                textViewStatus.text = "[!] No flashlight detected"
                toggleButton.isEnabled = false
            } else {
                textViewStatus.text = "[>] Flashlight ready"
            }
        } catch (e: Exception) {
            textViewStatus.text = "[!] Camera access error"
            toggleButton.isEnabled = false
        }
    }

    private fun setupToggle() {
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                stopAllModes()
                turnOnFlash()
                textViewStatus.text = "[ON] Flashlight active"
            } else {
                turnOffFlash()
                textViewStatus.text = "[OFF] Flashlight inactive"
            }
        }
    }

    private fun setupStrobe() {
        seekBarStrobeSpeed.max = 490
        seekBarStrobeSpeed.progress = 100
        seekBarStrobeSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                strobeSpeed = (progress + 10).toLong()
                textViewStrobeSpeed.text = "${strobeSpeed}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        buttonStrobe.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                stopAllModes()
                startStrobe()
                textViewStatus.text = "[STROBE] ${strobeSpeed}ms"
            } else {
                strobeJob?.cancel()
                strobeJob = null
                turnOffFlash()
                textViewStatus.text = "[OFF] Strobe stopped"
            }
        }
    }

    private fun startStrobe() {
        strobeJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                turnOnFlash()
                delay(strobeSpeed)
                turnOffFlash()
                delay(strobeSpeed)
            }
        }
    }

    private fun setupSos() {
        buttonSos.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                stopAllModes()
                startSos()
                textViewStatus.text = "[SOS] Morse pattern active"
            } else {
                sosJob?.cancel()
                sosJob = null
                turnOffFlash()
                textViewStatus.text = "[OFF] SOS stopped"
            }
        }
    }

    private fun startSos() {
        sosJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                var index = 0
                for (duration in sosPattern) {
                    if (!isActive) break
                    if (index % 2 == 0) {
                        turnOnFlash()
                    } else {
                        turnOffFlash()
                    }
                    delay(duration)
                    index++
                }
                turnOffFlash()
                delay(1500L)
            }
        }
    }

    private fun setupDisco() {
        buttonDisco.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                stopAllModes()
                startDisco()
                textViewStatus.text = "[DISCO] Random pattern active"
            } else {
                discoJob?.cancel()
                discoJob = null
                turnOffFlash()
                textViewStatus.text = "[OFF] Disco stopped"
            }
        }
    }

    private fun startDisco() {
        discoJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val onTime = (50..300).random().toLong()
                val offTime = (50..300).random().toLong()
                turnOnFlash()
                delay(onTime)
                turnOffFlash()
                delay(offTime)
            }
        }
    }

    private fun setupAutoOff() {
        buttonAutoOff.setOnClickListener {
            val minutesStr = editTextAutoOff.text.toString()
            val minutes = minutesStr.toIntOrNull()
            if (minutes == null || minutes <= 0) {
                Toast.makeText(requireContext(), "Enter valid minutes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            autoOffJob?.cancel()
            autoOffJob = lifecycleScope.launch {
                delay(minutes * 60 * 1000L)
                stopAllModes()
                turnOffFlash()
                toggleButton.isChecked = false
                buttonStrobe.isChecked = false
                buttonSos.isChecked = false
                buttonDisco.isChecked = false
                textViewStatus.text = "[AUTO-OFF] Timer expired"
                Toast.makeText(requireContext(), "Auto-off: flashlight turned off", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(requireContext(), "Auto-off set: $minutes min", Toast.LENGTH_SHORT).show()
            textViewStatus.text = "[TIMER] Auto-off in $minutes min"
        }
    }

    private fun setupBrightScreen() {
        buttonBrightScreen.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activity?.window?.apply {
                    attributes = attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    }
                    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                layoutBrightScreen.visibility = View.VISIBLE
                layoutBrightScreen.setBackgroundColor(0xFFFFFFFF.toInt())
                textViewStatus.text = "[SCREEN] Bright mode active"
            } else {
                activity?.window?.apply {
                    attributes = attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                    clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                layoutBrightScreen.visibility = View.GONE
                textViewStatus.text = "[OFF] Bright screen off"
            }
        }
    }

    private fun turnOnFlash() {
        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, true)
                isFlashOn = true
            }
        } catch (_: Exception) {
        }
    }

    private fun turnOffFlash() {
        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, false)
                isFlashOn = false
            }
        } catch (_: Exception) {
        }
    }

    private fun stopAllModes() {
        strobeJob?.cancel()
        strobeJob = null
        sosJob?.cancel()
        sosJob = null
        discoJob?.cancel()
        discoJob = null

        toggleButton.isChecked = false
        buttonStrobe.isChecked = false
        buttonSos.isChecked = false
        buttonDisco.isChecked = false
        turnOffFlash()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAllModes()
        autoOffJob?.cancel()
        buttonBrightScreen.isChecked = false
        // Restore brightness
        activity?.window?.apply {
            attributes = attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
