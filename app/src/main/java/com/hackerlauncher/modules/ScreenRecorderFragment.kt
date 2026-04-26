package com.hackerlauncher.modules

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecorderFragment : Fragment() {

    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }

    private lateinit var buttonRecord: Button
    private lateinit var buttonStop: Button
    private lateinit var textViewTimer: TextView
    private lateinit var textViewStatus: TextView
    private lateinit var spinnerResolution: Spinner
    private lateinit var spinnerBitrate: Spinner
    private lateinit var spinnerFps: Spinner
    private lateinit var recyclerViewRecordings: RecyclerView
    private lateinit var emptyStateText: TextView

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    private var isRecording = false
    private var recordingStartTime = 0L
    private var timerJob: Job? = null
    private var currentOutputFile: String = ""

    private val recordingsList = mutableListOf<RecordingItem>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val resolutions = arrayOf("720x1280", "1080x1920", "1440x2560", "2160x3840")
    private val bitrates = arrayOf("2 Mbps", "4 Mbps", "8 Mbps", "16 Mbps", "32 Mbps")
    private val fpsOptions = arrayOf("24", "30", "60")

    data class RecordingItem(
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val timestamp: Long,
        val duration: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_screen_recorder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonRecord = view.findViewById(R.id.buttonRecord)
        buttonStop = view.findViewById(R.id.buttonStop)
        textViewTimer = view.findViewById(R.id.textViewTimer)
        textViewStatus = view.findViewById(R.id.textViewStatus)
        spinnerResolution = view.findViewById(R.id.spinnerResolution)
        spinnerBitrate = view.findViewById(R.id.spinnerBitrate)
        spinnerFps = view.findViewById(R.id.spinnerFps)
        recyclerViewRecordings = view.findViewById(R.id.recyclerViewRecordings)
        emptyStateText = view.findViewById(R.id.textViewEmptyState)

        mediaProjectionManager =
            requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupSpinners()
        setupRecyclerView()
        setupButtons()
        loadRecordings()
        updateUI()
    }

    private fun setupSpinners() {
        val resolutionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resolutions
        )
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerResolution.adapter = resolutionAdapter
        spinnerResolution.setSelection(1)

        val bitrateAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            bitrates
        )
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBitrate.adapter = bitrateAdapter
        spinnerBitrate.setSelection(2)

        val fpsAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fpsOptions
        )
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFps.adapter = fpsAdapter
        spinnerFps.setSelection(1)
    }

    private fun setupRecyclerView() {
        recyclerViewRecordings.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewRecordings.adapter = RecordingsAdapter(recordingsList) { item ->
            playRecording(item)
        }
    }

    private fun setupButtons() {
        buttonRecord.setOnClickListener {
            if (!isRecording) {
                requestScreenCapture()
            }
        }

        buttonStop.setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
        }
    }

    private fun requestScreenCapture() {
        if (mediaProjectionManager != null) {
            val intent = mediaProjectionManager!!.createScreenCaptureIntent()
            startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
        } else {
            Toast.makeText(requireContext(), "Screen capture not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                startRecording()
            } else {
                Toast.makeText(requireContext(), "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        val resolution = resolutions[spinnerResolution.selectedItemPosition].split("x")
        val width = resolution[0].toInt()
        val height = resolution[1].toInt()
        val bitrateStr = bitrates[spinnerBitrate.selectedItemPosition]
        val bitrate = bitrateStr.replace(" Mbps", "").toInt() * 1_000_000
        val fps = fpsOptions[spinnerFps.selectedItemPosition].toInt()

        val outputDir = File(
            requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
            "ScreenRecordings"
        )
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = "screen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
        currentOutputFile = File(outputDir, fileName).absolutePath

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(currentOutputFile)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(bitrate)
                setVideoFrameRate(fps)
                prepare()
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder",
                width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null, null
            )

            mediaRecorder?.start()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            startTimer()
            updateUI()
            Toast.makeText(requireContext(), "Recording started", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            cleanupRecording()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }

        val duration = formatDuration(System.currentTimeMillis() - recordingStartTime)
        val file = File(currentOutputFile)

        if (file.exists()) {
            val item = RecordingItem(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                timestamp = System.currentTimeMillis(),
                duration = duration
            )
            recordingsList.add(0, item)
            saveRecordings()
            recyclerViewRecordings.adapter?.notifyItemInserted(0)
            updateEmptyState()
        }

        cleanupRecording()
        Toast.makeText(requireContext(), "Recording saved", Toast.LENGTH_SHORT).show()
    }

    private fun cleanupRecording() {
        isRecording = false
        timerJob?.cancel()
        timerJob = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        mediaRecorder = null
        updateUI()
    }

    private fun startTimer() {
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                withContext(Dispatchers.Main) {
                    textViewTimer.text = formatDuration(elapsed)
                }
                delay(1000)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun updateUI() {
        if (isRecording) {
            buttonRecord.visibility = View.GONE
            buttonStop.visibility = View.VISIBLE
            textViewStatus.text = "● REC"
            textViewStatus.setTextColor(0xFFFF0000.toInt())
            spinnerResolution.isEnabled = false
            spinnerBitrate.isEnabled = false
            spinnerFps.isEnabled = false
        } else {
            buttonRecord.visibility = View.VISIBLE
            buttonStop.visibility = View.GONE
            textViewStatus.text = "READY"
            textViewStatus.setTextColor(0xFF00FF00.toInt())
            textViewTimer.text = "00:00:00"
            spinnerResolution.isEnabled = true
            spinnerBitrate.isEnabled = true
            spinnerFps.isEnabled = true
        }
    }

    private fun playRecording(item: RecordingItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.fromFile(File(item.filePath)), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No video player found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecordings() {
        val prefs = requireContext().getSharedPreferences("screen_recorder_prefs", Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        for (item in recordingsList) {
            val jsonObj = org.json.JSONObject().apply {
                put("fileName", item.fileName)
                put("filePath", item.filePath)
                put("fileSize", item.fileSize)
                put("timestamp", item.timestamp)
                put("duration", item.duration)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString("recordings", jsonArray.toString()).apply()
    }

    private fun loadRecordings() {
        val prefs = requireContext().getSharedPreferences("screen_recorder_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("recordings", null) ?: return
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            recordingsList.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val file = File(jsonObj.getString("filePath"))
                if (file.exists()) {
                    recordingsList.add(
                        RecordingItem(
                            fileName = jsonObj.getString("fileName"),
                            filePath = jsonObj.getString("filePath"),
                            fileSize = jsonObj.getLong("fileSize"),
                            timestamp = jsonObj.getLong("timestamp"),
                            duration = jsonObj.getString("duration")
                        )
                    )
                }
            }
            recyclerViewRecordings.adapter?.notifyDataSetChanged()
        } catch (_: Exception) {
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyStateText.visibility = if (recordingsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRecording) {
            stopRecording()
        }
    }

    inner class RecordingsAdapter(
        private val items: List<RecordingItem>,
        private val onPlayClick: (RecordingItem) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textFileName: TextView = view.findViewById(R.id.textViewFileName)
            val textDetails: TextView = view.findViewById(R.id.textViewDetails)
            val textDuration: TextView = view.findViewById(R.id.textViewDuration)
            val buttonPlay: ImageButton = view.findViewById(R.id.buttonPlay)
            val buttonShare: ImageButton = view.findViewById(R.id.buttonShare)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textFileName.text = item.fileName
            holder.textDetails.text = "${formatFileSize(item.fileSize)} | ${dateFormat.format(Date(item.timestamp))}"
            holder.textDuration.text = item.duration

            holder.buttonPlay.setOnClickListener { onPlayClick(item) }

            holder.buttonShare.setOnClickListener {
                val file = File(item.filePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share recording"))
            }
        }

        override fun getItemCount(): Int = items.size

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
                size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
                else -> "${"%.1f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
            }
        }
    }
}
