package com.hackerlauncher.modules

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.CallLog
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val date: Long,
    val duration: Long = 0,
    var phoneNumber: String = ""
)

class CallRecorderFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var prefs: SharedPreferences
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var currentRecordingFile: String? = null
    private var recordingStartTime: Long = 0

    private val recordings = mutableListOf<RecordingInfo>()

    private lateinit var mainLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var autoRecordCheck: CheckBox
    private lateinit var audioSourceSpinner: Spinner
    private lateinit var storagePathEdit: EditText
    private lateinit var recordingsContainer: LinearLayout
    private var disclaimerShown: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireContext().getSharedPreferences("call_recorder", Context.MODE_PRIVATE)
        disclaimerShown = prefs.getBoolean("disclaimer_shown", false)

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
        mainLayout.addView(makeTitle("[>] CALL RECORDER"))

        // Show disclaimer on first use
        if (!disclaimerShown) {
            showDisclaimer()
        }

        // Status
        statusText = makeLabel("[~] Initializing...")
        mainLayout.addView(statusText)

        // Disclaimer button
        mainLayout.addView(makeButton("VIEW DISCLAIMER") { showDisclaimer() })

        // Manual record controls
        mainLayout.addView(makeSectionHeader("MANUAL RECORDING"))

        val recRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        recRow.addView(makeHalfButton("START REC") { startManualRecording() })
        recRow.addView(makeHalfButton("STOP REC") { stopRecording() })
        mainLayout.addView(recRow)

        // Auto-record toggle
        mainLayout.addView(makeSectionHeader("AUTO-RECORD"))

        autoRecordCheck = CheckBox(requireContext()).apply {
            text = "Auto-record calls"
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            isChecked = prefs.getBoolean("auto_record", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("auto_record", checked).apply()
                statusText.text = "[>] Auto-record: ${if (checked) "ON" else "OFF"}"
            }
        }
        mainLayout.addView(autoRecordCheck)

        // Audio source selection
        mainLayout.addView(makeSectionHeader("AUDIO SOURCE"))

        audioSourceSpinner = Spinner(requireContext()).apply {
            val sources = arrayOf(
                "VOICE_COMMUNICATION (6)",
                "VOICE_CALL (4)",
                "MIC (1)",
                "DEFAULT (0)"
            )
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sources)
            setSelection(prefs.getInt("audio_source", 0))
            setPadding(8, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        (audioSourceSpinner.adapter as? ArrayAdapter<String>)?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mainLayout.addView(audioSourceSpinner)

        // Storage location
        mainLayout.addView(makeSectionHeader("STORAGE"))

        storagePathEdit = EditText(requireContext()).apply {
            val defaultPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "CallRecordings"
            ).absolutePath
            setText(prefs.getString("storage_path", defaultPath))
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(storagePathEdit)

        mainLayout.addView(makeSmallButton("SAVE PATH") {
            prefs.edit().putString("storage_path", storagePathEdit.text.toString()).apply()
            Toast.makeText(requireContext(), "Path saved", Toast.LENGTH_SHORT).show()
        })

        // Call log integration
        mainLayout.addView(makeSectionHeader("CALL LOG"))

        mainLayout.addView(makeButton("VIEW CALL LOG") { showCallLog() })

        // Permissions
        mainLayout.addView(makeSectionHeader("PERMISSIONS"))

        mainLayout.addView(makeButton("REQUEST PERMISSIONS") { requestPermissions() })

        // Recordings list
        mainLayout.addView(makeSectionHeader("RECORDINGS"))

        mainLayout.addView(makeButton("REFRESH LIST") { loadRecordings() })

        recordingsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(recordingsContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRecordings()
        statusText.text = "[>] Ready"
    }

    private fun showDisclaimer() {
        val disclaimer = """
            ⚠️ LEGAL DISCLAIMER ⚠️
            
            Call recording laws vary by jurisdiction.
            
            • In some regions, ALL parties must consent to recording (two-party consent).
            • In other regions, only one party needs to consent.
            • Recording calls without proper consent may be ILLEGAL.
            
            By using this feature, you acknowledge:
            1. You are responsible for complying with local laws.
            2. You will inform all parties when required by law.
            3. The developer is NOT liable for any legal issues.
            4. Recordings should only be made for legitimate purposes.
            
            Use responsibly and legally.
        """.trimIndent()

        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(BLACK) }
        scroll.addView(TextView(requireContext()).apply {
            text = disclaimer
            setTextColor(Color.parseColor("#FF4444"))
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(24, 24, 24, 24)
        })

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ LEGAL DISCLAIMER")
            .setView(scroll)
            .setPositiveButton("I UNDERSTAND") { _, _ ->
                prefs.edit().putBoolean("disclaimer_shown", true).apply()
                disclaimerShown = true
            }
            .setNegativeButton("DECLINE") { _, _ ->
                statusText.text = "[!] Disclaimer not accepted - recording disabled"
            }
            .setCancelable(false)
            .show()
    }

    private fun getAudioSource(): Int {
        return when (audioSourceSpinner.selectedItemPosition) {
            0 -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            1 -> {
                try {
                    MediaRecorder.AudioSource::class.java.getField("VOICE_CALL").getInt(null)
                } catch (_: Exception) { MediaRecorder.AudioSource.VOICE_COMMUNICATION }
            }
            2 -> MediaRecorder.AudioSource.MIC
            else -> MediaRecorder.AudioSource.DEFAULT
        }
    }

    private fun getStorageDir(): File {
        val path = prefs.getString("storage_path",
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CallRecordings").absolutePath
        )!!
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun startManualRecording() {
        if (!disclaimerShown) {
            showDisclaimer()
            return
        }

        if (isRecording) {
            Toast.makeText(requireContext(), "Already recording", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val storageDir = getStorageDir()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentRecordingFile = File(storageDir, "call_$timestamp.3gp").absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(getAudioSource())
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(currentRecordingFile)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            statusText.text = "[REC] Recording in progress..."
            statusText.setTextColor(Color.parseColor("#FF0000"))
            Toast.makeText(requireContext(), "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "[!] Recording error: ${e.message}"
            statusText.setTextColor(GREEN)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            cleanupRecorder()
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            Toast.makeText(requireContext(), "Not recording", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            val duration = (System.currentTimeMillis() - recordingStartTime) / 1000

            statusText.text = "[+] Recording saved (${duration}s)"
            statusText.setTextColor(GREEN)

            // Save audio source preference
            prefs.edit().putInt("audio_source", audioSourceSpinner.selectedItemPosition).apply()

            loadRecordings()
            Toast.makeText(requireContext(), "Recording stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "[!] Stop error: ${e.message}"
        } finally {
            cleanupRecorder()
        }
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) { }
        mediaRecorder = null
        isRecording = false
        statusText.setTextColor(GREEN)
    }

    private fun loadRecordings() {
        recordings.clear()
        try {
            val dir = getStorageDir()
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter {
                    it.extension in listOf("3gp", "mp4", "amr", "wav", "ogg")
                }?.forEach { file ->
                    recordings.add(RecordingInfo(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        date = file.lastModified(),
                        duration = 0,
                        phoneNumber = ""
                    ))
                }
            }

            // Also check call log for phone numbers
            enrichWithCallLog()

            recordings.sortByDescending { it.date }
            renderRecordings()
        } catch (e: Exception) {
            statusText.text = "[!] Error loading recordings: ${e.message}"
        }
    }

    private fun enrichWithCallLog() {
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED
            ) return

            val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val calls = mutableMapOf<String, String>()

            val cursor = requireContext().contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                while (it.moveToNext()) {
                    val number = it.getString(numIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val typeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "IN"
                        CallLog.Calls.OUTGOING_TYPE -> "OUT"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "?"
                    }
                    calls[df.format(Date(date))] = "$typeStr $number"
                }
            }

            // Match recordings to calls by date
            for (rec in recordings) {
                val recDateKey = df.format(Date(rec.date))
                calls[recDateKey]?.let { rec.phoneNumber = it }
            }
        } catch (_: Exception) { }
    }

    private fun renderRecordings() {
        recordingsContainer.removeAllViews()
        val df = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())

        if (recordings.isEmpty()) {
            recordingsContainer.addView(TextView(requireContext()).apply {
                text = "[~] No recordings found"
                setTextColor(DARK_GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                setPadding(0, 16, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        for ((index, rec) in recordings.withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                setPadding(12, 8, 12, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(TextView(requireContext()).apply {
                text = rec.fileName
                setTextColor(GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            })

            row.addView(TextView(requireContext()).apply {
                text = "${df.format(Date(rec.date))} | ${formatSize(rec.fileSize)}" +
                    if (rec.phoneNumber.isNotBlank()) " | ${rec.phoneNumber}" else ""
                setTextColor(Color.parseColor("#888888"))
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
            })

            val btnRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            btnRow.addView(makeTinyButton("PLAY") { playRecording(rec) })
            btnRow.addView(makeTinyButton("STOP") { stopPlayback() })
            btnRow.addView(makeTinyButton("SHARE") { shareRecording(rec) })
            btnRow.addView(makeTinyButton("DELETE") { deleteRecording(rec) })

            row.addView(btnRow)
            recordingsContainer.addView(row)
        }
    }

    private fun playRecording(rec: RecordingInfo) {
        try {
            stopPlayback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(rec.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    statusText.text = "[>] Playback finished"
                }
            }
            statusText.text = "[>] Playing: ${rec.fileName}"
        } catch (e: Exception) {
            statusText.text = "[!] Playback error: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) { }
        mediaPlayer = null
    }

    private fun shareRecording(rec: RecordingInfo) {
        try {
            val file = File(rec.filePath)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Recording"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRecording(rec: RecordingInfo) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Recording?")
            .setMessage("Delete ${rec.fileName}?")
            .setPositiveButton("DELETE") { _, _ ->
                try {
                    File(rec.filePath).delete()
                    statusText.text = "[+] Deleted: ${rec.fileName}"
                    loadRecordings()
                } catch (e: Exception) {
                    statusText.text = "[!] Delete error: ${e.message}"
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showCallLog() {
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions()
                return
            }

            val sb = StringBuilder()
            sb.appendLine("=== CALL LOG ===")

            val cursor = requireContext().contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_NAME
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                var count = 0
                while (it.moveToNext() && count < 50) {
                    val number = it.getString(numIdx) ?: "Unknown"
                    val date = it.getLong(dateIdx)
                    val duration = it.getString(durIdx) ?: "0"
                    val type = when (it.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "IN"
                        CallLog.Calls.OUTGOING_TYPE -> "OUT"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        else -> "?"
                    }
                    val name = it.getString(nameIdx) ?: ""
                    val df = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

                    sb.appendLine("[$type] ${if (name.isNotBlank()) "$name " else ""}$number | ${df.format(Date(date))} | ${duration}s")
                    count++
                }
            }

            showInfoDialog("Call Log", sb.toString())
        } catch (e: Exception) {
            statusText.text = "[!] Call log error: ${e.message}"
        }
    }

    private fun requestPermissions() {
        try {
            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissions.toTypedArray(),
                100
            )
        } catch (e: Exception) {
            statusText.text = "[!] Permission request error: ${e.message}"
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(BLACK) }
        scroll.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(24, 24, 24, 24)
        })

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            if (isRecording) stopRecording()
            stopPlayback()
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

    private fun makeHalfButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 6, 8, 6)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = 4 }
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
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    private fun makeTinyButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 10f
            setTypeface(Typeface.MONOSPACE)
            setPadding(4, 2, 4, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 4 }
            setOnClickListener { onClick() }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
