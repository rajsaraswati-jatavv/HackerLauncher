package com.hackerlauncher.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntruderSelfieFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var switchMonitor: Switch
    private lateinit var switchEmailNotify: Switch
    private lateinit var etEmail: EditText
    private lateinit var tvBadge: TextView
    private var monitoring = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> INTRUDER SELFIE v2.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Photo count badge
        val badgeLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        tvBadge = TextView(context).apply {
            text = "  Intruder Photos: 0  "
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
        }
        badgeLayout.addView(tvBadge)
        root.addView(badgeLayout)

        // Email notification
        root.addView(TextView(context).apply {
            text = "Email Notification:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 0)
        })

        switchEmailNotify = Switch(context).apply {
            text = "Email on capture"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            isChecked = false
        }
        root.addView(switchEmailNotify)

        etEmail = EditText(context).apply {
            hint = "your@email.com"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(8, 8, 8, 8)
        }
        root.addView(etEmail)

        // Monitor switch
        switchMonitor = Switch(context).apply {
            text = "Monitor (capture on wrong PIN)"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            isChecked = false
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) startMonitoring() else stopMonitoring()
            }
        }
        root.addView(switchMonitor)

        // Camera info
        root.addView(TextView(context).apply {
            text = "[i] Works with AppLockService for auto-capture"
            setTextColor(0xFF005500.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        })

        // Buttons row 1
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Test Capture") { captureSelfie() })
        btnRow1.addView(makeBtn("Gallery") { showIntruderGallery() })
        btnRow1.addView(makeBtn("Count") { updateBadge() })
        root.addView(btnRow1)

        // Buttons row 2
        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Delete All") { clearIntruderPhotos() })
        btnRow2.addView(makeBtn("Delete Oldest") { deleteOldestPhoto() })
        btnRow2.addView(makeBtn("Delete Photo") { deleteSelectedPhoto() })
        root.addView(btnRow2)

        // Buttons row 3
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Camera Info") { showCameraInfo() })
        btnRow3.addView(makeBtn("Timestamps") { showTimestamps() })
        root.addView(btnRow3)

        // Output
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        tvOutput = TextView(context).apply {
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        root.addView(scrollView)

        updateBadge()
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   INTRUDER SELFIE v2.1          ║\n")
        appendOutput("║  Front camera capture on wrong  ║\n")
        appendOutput("║  PIN with CameraX support       ║\n")
        appendOutput("║  Saves to /intruder_photos/     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeBtn(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(6, 2, 6, 2)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
        }
    }

    private fun getIntruderDir(): File {
        val dir = File(requireContext().filesDir, "intruder_photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun updateBadge() {
        try {
            val dir = getIntruderDir()
            val count = dir.listFiles()?.count { it.name.endsWith(".jpg") } ?: 0
            tvBadge.text = "  Intruder Photos: $count  "
        } catch (e: Exception) {
            tvBadge.text = "  Intruder Photos: ?  "
        }
    }

    private fun captureSelfie() {
        scope.launch {
            appendOutput("[*] Taking front camera photo...\n")
            try {
                val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
                var frontCameraId: String? = null

                for (id in cameraManager.cameraIdList) {
                    try {
                        val chars = cameraManager.getCameraCharacteristics(id)
                        val facing = chars.get(CameraCharacteristics.LENS_FACING)
                        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                            frontCameraId = id
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (frontCameraId == null) {
                    appendOutput("[!] No front camera found\n")
                    captureWithOldApi()
                    return@launch
                }

                appendOutput("[*] Using camera ID: $frontCameraId\n")
                captureWithOldApi()
            } catch (e: Exception) {
                appendOutput("[E] Camera2 check: ${e.message}\n")
                captureWithOldApi()
            }
        }
    }

    private fun captureWithOldApi() {
        try {
            val camera = android.hardware.Camera.open(android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
            if (camera != null) {
                try {
                    val params = camera.parameters
                    params.jpegQuality = 85
                    camera.parameters = params
                } catch (_: Exception) {}

                camera.startPreview()

                scope.launch {
                    delay(1500)
                    try {
                        camera.takePicture(null, null, { _, data ->
                            try {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val file = File(getIntruderDir(), "intruder_$timestamp.jpg")
                                FileOutputStream(file).use { fos ->
                                    fos.write(data)
                                    fos.flush()
                                }
                                camera.stopPreview()
                                camera.release()
                                appendOutput("[+] Photo saved: ${file.name}\n")
                                appendOutput("[+] Size: ${file.length() / 1024}KB\n")
                                appendOutput("[+] Path: ${file.absolutePath}\n")
                                updateBadge()

                                if (switchEmailNotify.isChecked) {
                                    appendOutput("[*] Email notification would be sent to: ${etEmail.text}\n")
                                }
                            } catch (e: Exception) {
                                try { camera.release() } catch (_: Exception) {}
                                appendOutput("[E] Save failed: ${e.message}\n")
                            }
                        })
                    } catch (e: Exception) {
                        camera.release()
                        appendOutput("[E] Capture failed: ${e.message}\n")
                    }
                }
            } else {
                appendOutput("[!] Front camera not available\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] Camera failed: ${e.message}\n")
            appendOutput("[*] Camera permission required\n")
        }
    }

    private fun showIntruderGallery() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Intruder Photo Gallery      ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val dir = getIntruderDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() }

            if (files.isNullOrEmpty()) {
                appendOutput("║ No intruder photos yet\n")
                appendOutput("║ Photos captured on wrong PIN\n")
                appendOutput("║ Works with AppLockService\n")
            } else {
                appendOutput("║ Found ${files.size} photos:\n\n")
                for ((idx, file) in files.withIndex()) {
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified()))
                    val size = file.length() / 1024
                    appendOutput("║ ${idx + 1}. ${file.name}\n")
                    appendOutput("║    Date: $date | Size: ${size}KB\n")
                }

                if (files.isNotEmpty()) {
                    try {
                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeFile(files[0].absolutePath, options)
                        appendOutput("\n║ Latest: ${options.outWidth}x${options.outHeight}px\n")
                    } catch (_: Exception) {}
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Gallery: ${e.message}\n")
        }
    }

    private fun showTimestamps() {
        try {
            val dir = getIntruderDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() }

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Photo Timestamps            ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            if (files.isNullOrEmpty()) {
                appendOutput("  No photos found\n")
            } else {
                for ((idx, file) in files.withIndex()) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    val date = sdf.format(Date(file.lastModified()))
                    val ageMs = System.currentTimeMillis() - file.lastModified()
                    val ageMins = ageMs / 60000
                    appendOutput("  ${idx + 1}. $date (${ageMins}m ago)\n")
                }
            }
            appendOutput("\n╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Timestamps: ${e.message}\n")
        }
    }

    private fun clearIntruderPhotos() {
        try {
            val dir = getIntruderDir()
            val files = dir.listFiles()
            var deleted = 0
            files?.forEach { file ->
                if (file.name.endsWith(".jpg") && file.delete()) deleted++
            }
            appendOutput("[+] Deleted $deleted intruder photos\n")
            updateBadge()
        } catch (e: Exception) {
            appendOutput("[E] Clear: ${e.message}\n")
        }
    }

    private fun deleteOldestPhoto() {
        try {
            val dir = getIntruderDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedBy { it.lastModified() }
            if (files.isNullOrEmpty()) {
                appendOutput("[!] No photos to delete\n")
            } else {
                val oldest = files.first()
                if (oldest.delete()) {
                    appendOutput("[+] Deleted: ${oldest.name}\n")
                    updateBadge()
                } else {
                    appendOutput("[E] Could not delete: ${oldest.name}\n")
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] Delete: ${e.message}\n")
        }
    }

    private fun deleteSelectedPhoto() {
        try {
            val dir = getIntruderDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() }
            if (files.isNullOrEmpty()) {
                appendOutput("[!] No photos to delete\n")
                return
            }
            // Delete the newest photo
            val newest = files.first()
            if (newest.delete()) {
                appendOutput("[+] Deleted: ${newest.name}\n")
                updateBadge()
            }
        } catch (e: Exception) {
            appendOutput("[E] Delete: ${e.message}\n")
        }
    }

    private fun showCameraInfo() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Camera Information          ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in cameraManager.cameraIdList) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                        else -> "UNKNOWN"
                    }
                    appendOutput("║ Camera $id: $facing\n")
                    val streamConfig = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (streamConfig != null) {
                        appendOutput("║   Formats: ${streamConfig.outputFormats.size}\n")
                    }
                } catch (e: Exception) {
                    appendOutput("║ Camera $id: [Error]\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Camera info: ${e.message}\n")
        }
    }

    private fun startMonitoring() {
        monitoring = true
        val prefs = requireContext().getSharedPreferences("intruder_selfie", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("monitoring", true)
            .putBoolean("email_notify", switchEmailNotify.isChecked)
            .putString("notify_email", etEmail.text.toString().trim())
            .apply()

        appendOutput("[*] Intruder monitoring STARTED\n")
        appendOutput("[*] Captures on wrong PIN via AppLockService\n")
        appendOutput("[*] Requires: Camera + Accessibility service\n\n")

        scope.launch {
            while (monitoring && isActive) {
                try {
                    delay(5000)
                } catch (e: Exception) {
                    if (isActive) appendOutput("[E] Monitor: ${e.message}\n")
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitoring = false
        val prefs = requireContext().getSharedPreferences("intruder_selfie", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("monitoring", false).apply()
        appendOutput("[*] Intruder monitoring STOPPED\n")
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitoring = false
        scope.cancel()
    }
}
