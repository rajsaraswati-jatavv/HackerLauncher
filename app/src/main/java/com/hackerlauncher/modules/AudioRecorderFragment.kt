package com.hackerlauncher.modules

import com.hackerlauncher.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderFragment : Fragment() {

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 2001
    }

    private lateinit var buttonRecord: Button
    private lateinit var buttonStop: Button
    private lateinit var textViewTimer: TextView
    private lateinit var textViewStatus: TextView
    private lateinit var spinnerFormat: Spinner
    private lateinit var recyclerViewRecordings: RecyclerView
    private lateinit var emptyStateText: TextView

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private var recordingStartTime = 0L
    private var timerJob: Job? = null
    private var currentOutputFile: String = ""

    private val recordingsList = mutableListOf<AudioRecordingItem>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val audioFormats = arrayOf("3GP", "AAC", "AMR_WB")

    data class AudioRecordingItem(
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val timestamp: Long,
        val duration: String,
        val format: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio_recorder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonRecord = view.findViewById(R.id.buttonRecord)
        buttonStop = view.findViewById(R.id.buttonStop)
        textViewTimer = view.findViewById(R.id.textViewTimer)
        textViewStatus = view.findViewById(R.id.textViewStatus)
        spinnerFormat = view.findViewById(R.id.spinnerFormat)
        recyclerViewRecordings = view.findViewById(R.id.recyclerViewRecordings)
        emptyStateText = view.findViewById(R.id.textViewEmptyState)

        setupSpinner()
        setupRecyclerView()
        setupButtons()
        loadRecordings()
        updateUI()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            audioFormats
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFormat.adapter = adapter
    }

    private fun setupRecyclerView() {
        recyclerViewRecordings.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewRecordings.adapter = AudioRecordingsAdapter(
            recordingsList,
            onPlayClick = { item -> playRecording(item) },
            onShareClick = { item -> shareRecording(item) },
            onDeleteClick = { item -> deleteRecording(item) }
        )
    }

    private fun setupButtons() {
        buttonRecord.setOnClickListener {
            if (checkAudioPermission()) {
                startRecording()
            } else {
                requestAudioPermission()
            }
        }

        buttonStop.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else if (isPlaying) {
                stopPlayback()
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        val format = audioFormats[spinnerFormat.selectedItemPosition]
        val outputDir = File(
            requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
            "AudioRecordings"
        )
        if (!outputDir.exists()) outputDir.mkdirs()

        val ext = when (format) {
            "AAC" -> "aac"
            "AMR_WB" -> "amr"
            else -> "3gp"
        }
        val fileName = "audio_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$ext"
        currentOutputFile = File(outputDir, fileName).absolutePath

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)

                when (format) {
                    "AAC" -> {
                        setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    "AMR_WB" -> {
                        setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                    }
                    else -> {
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    }
                }

                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(currentOutputFile)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            startTimer()
            updateUI()
            Toast.makeText(requireContext(), "Recording started [$format]", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            Toast.makeText(requireContext(), "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            cleanupRecorder()
        } catch (e: IllegalStateException) {
            Toast.makeText(requireContext(), "Recorder error: ${e.message}", Toast.LENGTH_LONG).show()
            cleanupRecorder()
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
        val format = audioFormats[spinnerFormat.selectedItemPosition]

        if (file.exists()) {
            val item = AudioRecordingItem(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                timestamp = System.currentTimeMillis(),
                duration = duration,
                format = format
            )
            recordingsList.add(0, item)
            saveRecordings()
            recyclerViewRecordings.adapter?.notifyItemInserted(0)
            updateEmptyState()
        }

        mediaRecorder = null
        isRecording = false
        timerJob?.cancel()
        timerJob = null
        updateUI()
        Toast.makeText(requireContext(), "Recording saved", Toast.LENGTH_SHORT).show()
    }

    private fun playRecording(item: AudioRecordingItem) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(item.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    this@AudioRecorderFragment.isPlaying = false
                    updateUI()
                    Toast.makeText(requireContext(), "Playback finished", Toast.LENGTH_SHORT).show()
                }
            }
            isPlaying = true
            recordingStartTime = System.currentTimeMillis()
            startPlaybackTimer()
            updateUI()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPlaying = false
        timerJob?.cancel()
        timerJob = null
    }

    private fun shareRecording(item: AudioRecordingItem) {
        val file = File(item.filePath)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share recording"))
    }

    private fun deleteRecording(item: AudioRecordingItem) {
        val file = File(item.filePath)
        if (file.exists()) file.delete()
        val index = recordingsList.indexOf(item)
        if (index >= 0) {
            recordingsList.removeAt(index)
            recyclerViewRecordings.adapter?.notifyItemRemoved(index)
            saveRecordings()
            updateEmptyState()
        }
        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
    }

    private fun startTimer() {
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                withContext(Dispatchers.Main) {
                    textViewTimer.text = formatDuration(elapsed)
                }
                delay(100)
            }
        }
    }

    private fun startPlaybackTimer() {
        timerJob = lifecycleScope.launch {
            while (isActive && isPlaying) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                withContext(Dispatchers.Main) {
                    textViewTimer.text = "▶ ${formatDuration(elapsed)}"
                }
                delay(100)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        isRecording = false
        timerJob?.cancel()
        timerJob = null
        updateUI()
    }

    private fun updateUI() {
        if (isRecording) {
            buttonRecord.visibility = View.GONE
            buttonStop.visibility = View.VISIBLE
            buttonStop.text = "STOP REC"
            textViewStatus.text = "● REC"
            textViewStatus.setTextColor(0xFFFF0000.toInt())
            spinnerFormat.isEnabled = false
        } else if (isPlaying) {
            buttonRecord.visibility = View.GONE
            buttonStop.visibility = View.VISIBLE
            buttonStop.text = "STOP PLAY"
            textViewStatus.text = "▶ PLAYING"
            textViewStatus.setTextColor(0xFF00FF00.toInt())
            spinnerFormat.isEnabled = false
        } else {
            buttonRecord.visibility = View.VISIBLE
            buttonStop.visibility = View.GONE
            textViewStatus.text = "READY"
            textViewStatus.setTextColor(0xFF00FF00.toInt())
            textViewTimer.text = "00:00:00"
            spinnerFormat.isEnabled = true
        }
    }

    private fun saveRecordings() {
        val prefs = requireContext().getSharedPreferences("audio_recorder_prefs", Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        for (item in recordingsList) {
            val jsonObj = org.json.JSONObject().apply {
                put("fileName", item.fileName)
                put("filePath", item.filePath)
                put("fileSize", item.fileSize)
                put("timestamp", item.timestamp)
                put("duration", item.duration)
                put("format", item.format)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString("recordings", jsonArray.toString()).apply()
    }

    private fun loadRecordings() {
        val prefs = requireContext().getSharedPreferences("audio_recorder_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("recordings", null) ?: return
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            recordingsList.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val file = File(jsonObj.getString("filePath"))
                if (file.exists()) {
                    recordingsList.add(
                        AudioRecordingItem(
                            fileName = jsonObj.getString("fileName"),
                            filePath = jsonObj.getString("filePath"),
                            fileSize = jsonObj.getLong("fileSize"),
                            timestamp = jsonObj.getLong("timestamp"),
                            duration = jsonObj.getString("duration"),
                            format = jsonObj.optString("format", "3GP")
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
        if (isRecording) stopRecording()
        stopPlayback()
    }

    inner class AudioRecordingsAdapter(
        private val items: List<AudioRecordingItem>,
        private val onPlayClick: (AudioRecordingItem) -> Unit,
        private val onShareClick: (AudioRecordingItem) -> Unit,
        private val onDeleteClick: (AudioRecordingItem) -> Unit
    ) : RecyclerView.Adapter<AudioRecordingsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textFileName: TextView = view.findViewById(R.id.textViewFileName)
            val textDetails: TextView = view.findViewById(R.id.textViewDetails)
            val textDuration: TextView = view.findViewById(R.id.textViewDuration)
            val buttonPlay: ImageButton = view.findViewById(R.id.buttonPlay)
            val buttonShare: ImageButton = view.findViewById(R.id.buttonShare)
            val buttonDelete: ImageButton = view.findViewById(R.id.buttonDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_audio_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textFileName.text = item.fileName
            holder.textDetails.text = "${item.format} | ${formatFileSize(item.fileSize)} | ${dateFormat.format(Date(item.timestamp))}"
            holder.textDuration.text = item.duration

            holder.buttonPlay.setOnClickListener { onPlayClick(item) }
            holder.buttonShare.setOnClickListener { onShareClick(item) }
            holder.buttonDelete.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount(): Int = items.size

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
                else -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
            }
        }
    }
}
