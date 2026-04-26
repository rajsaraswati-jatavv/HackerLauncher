package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DownloadManagerFragment : Fragment() {

    private lateinit var etUrl: EditText
    private lateinit var btnDownload: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadsList = mutableListOf<DownloadItem>()
    private val completedList = mutableListOf<DownloadItem>()
    private lateinit var downloadAdapter: DownloadAdapter

    private var currentDownloadJob: Job? = null
    private var isPaused = false

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showToast("Storage permission required for downloads")
    }

    data class DownloadItem(
        val id: Long,
        val fileName: String,
        val url: String,
        val savePath: String,
        var progress: Int = 0,
        var totalBytes: Long = 0,
        var downloadedBytes: Long = 0,
        var status: String = "Downloading",
        var timestamp: Long = System.currentTimeMillis()
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_download_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etUrl = view.findViewById(R.id.etUrl)
        btnDownload = view.findViewById(R.id.btnDownload)
        progressBar = view.findViewById(R.id.downloadProgressBar)
        tvProgress = view.findViewById(R.id.tvProgress)
        tabLayout = view.findViewById(R.id.tabLayout)
        recyclerView = view.findViewById(R.id.downloadRecycler)
        emptyState = view.findViewById(R.id.emptyState)

        downloadAdapter = DownloadAdapter(
            mutableListOf(),
            onPause = { item -> pauseDownload(item) },
            onResume = { item -> resumeDownload(item) },
            onCancel = { item -> cancelDownload(item) },
            onOpen = { item -> openFile(item) },
            onShare = { item -> shareFile(item) },
            onDelete = { item -> deleteDownload(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = downloadAdapter

        tabLayout.addTab(tabLayout.newTab().setText("Active"))
        tabLayout.addTab(tabLayout.newTab().setText("Completed"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateListForTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        btnDownload.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                showToast("Enter a URL")
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                showToast("Enter a valid URL")
                return@setOnClickListener
            }
            checkStorageAndDownload(url)
        }

        checkStoragePermission()
        loadSystemDownloads()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun checkStorageAndDownload(url: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        startDownload(url)
    }

    private fun startDownload(url: String) {
        val fileName = getFileNameFromUrl(url)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val saveFile = File(downloadDir, fileName)
        var counter = 1
        var finalFile = saveFile
        while (finalFile.exists()) {
            val baseName = fileName.substringBeforeLast(".", fileName)
            val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
            finalFile = File(downloadDir, "${baseName}_$counter$ext")
            counter++
        }

        val item = DownloadItem(
            id = System.currentTimeMillis(),
            fileName = finalFile.name,
            url = url,
            savePath = finalFile.absolutePath,
            status = "Downloading"
        )
        downloadsList.add(0, item)
        updateListForTab(tabLayout.selectedTabPosition)

        // Use OkHttp for download with progress tracking
        downloadWithOkHttp(item, finalFile)
    }

    private fun downloadWithOkHttp(item: DownloadItem, saveFile: File) {
        isPaused = false
        currentDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(item.url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        item.status = "Failed: ${response.code}"
                        updateItemInList(item)
                        showToast("Download failed: HTTP ${response.code}")
                    }
                    return@launch
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                item.totalBytes = totalBytes

                saveFile.parentFile?.mkdirs()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(saveFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) break

                    // Check for pause
                    while (isPaused && isActive) {
                        delay(200)
                    }
                    if (!isActive) break

                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    item.downloadedBytes = totalRead

                    if (totalBytes > 0) {
                        item.progress = ((totalRead * 100) / totalBytes).toInt()
                    }

                    withContext(Dispatchers.Main) {
                        progressBar.progress = item.progress
                        tvProgress.text = "${item.progress}% - ${formatFileSize(totalRead)} / ${formatFileSize(totalBytes)}"
                        updateItemInList(item)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (isActive) {
                    item.progress = 100
                    item.status = "Completed"
                    withContext(Dispatchers.Main) {
                        progressBar.progress = 100
                        tvProgress.text = "Download complete"
                        downloadsList.remove(item)
                        completedList.add(0, item)
                        updateListForTab(tabLayout.selectedTabPosition)
                        showToast("Downloaded: ${item.fileName}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    item.status = "Failed: ${e.message}"
                    updateItemInList(item)
                    showToast("Download failed: ${e.message}")
                }
            }
        }
    }

    private fun pauseDownload(item: DownloadItem) {
        if (item.status == "Downloading") {
            isPaused = true
            item.status = "Paused"
            updateItemInList(item)
            showToast("Download paused")
        }
    }

    private fun resumeDownload(item: DownloadItem) {
        if (item.status == "Paused") {
            isPaused = false
            item.status = "Downloading"
            updateItemInList(item)
            showToast("Download resumed")
        }
    }

    private fun cancelDownload(item: DownloadItem) {
        currentDownloadJob?.cancel()
        item.status = "Cancelled"
        downloadsList.remove(item)
        updateListForTab(tabLayout.selectedTabPosition)
        showToast("Download cancelled")
    }

    private fun openFile(item: DownloadItem) {
        try {
            val file = File(item.savePath)
            if (!file.exists()) {
                showToast("File not found")
                return
            }
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(item.fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            showToast("Cannot open file: ${e.message}")
        }
    }

    private fun shareFile(item: DownloadItem) {
        try {
            val file = File(item.savePath)
            if (!file.exists()) {
                showToast("File not found")
                return
            }
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(item.fileName)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            showToast("Cannot share file: ${e.message}")
        }
    }

    private fun deleteDownload(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Download")
            .setMessage("Delete ${item.fileName}?")
            .setPositiveButton("Delete") { _, _ ->
                val file = File(item.savePath)
                if (file.exists()) file.delete()
                downloadsList.remove(item)
                completedList.remove(item)
                updateListForTab(tabLayout.selectedTabPosition)
                showToast("Deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateItemInList(item: DownloadItem) {
        val idx = downloadAdapter.items.indexOf(item)
        if (idx >= 0) {
            downloadAdapter.notifyItemChanged(idx)
        }
    }

    private fun updateListForTab(tabPosition: Int) {
        val items = if (tabPosition == 0) downloadsList else completedList
        downloadAdapter.updateItems(items.toMutableList())
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadSystemDownloads() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir.exists()) {
                    val files = downloadDir.listFiles()?.toList() ?: emptyList()
                    val loadedItems = files.map { file ->
                        DownloadItem(
                            id = file.lastModified(),
                            fileName = file.name,
                            url = "",
                            savePath = file.absolutePath,
                            progress = 100,
                            totalBytes = file.length(),
                            downloadedBytes = file.length(),
                            status = "Completed",
                            timestamp = file.lastModified()
                        )
                    }.sortedByDescending { it.timestamp }

                    withContext(Dispatchers.Main) {
                        completedList.clear()
                        completedList.addAll(loadedItems)
                        if (tabLayout.selectedTabPosition == 1) {
                            updateListForTab(1)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to load downloads: ${e.message}")
                }
            }
        }
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrEmpty() && lastSegment.contains(".")) {
                lastSegment
            } else {
                "download_${System.currentTimeMillis()}"
            }
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase(Locale.getDefault())
        return when (ext) {
            "pdf" -> "application/pdf"
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "image/$ext"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            else -> "*/*"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    // --- Inner Adapter ---

    inner class DownloadAdapter(
        var items: MutableList<DownloadItem>,
        private val onPause: (DownloadItem) -> Unit,
        private val onResume: (DownloadItem) -> Unit,
        private val onCancel: (DownloadItem) -> Unit,
        private val onOpen: (DownloadItem) -> Unit,
        private val onShare: (DownloadItem) -> Unit,
        private val onDelete: (DownloadItem) -> Unit
    ) : RecyclerView.Adapter<DownloadAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvSize: TextView = view.findViewById(R.id.tvSize)
            val itemProgress: ProgressBar = view.findViewById(R.id.itemProgress)
            val btnAction1: MaterialButton = view.findViewById(R.id.btnAction1)
            val btnAction2: MaterialButton = view.findViewById(R.id.btnAction2)
            val btnAction3: MaterialButton = view.findViewById(R.id.btnAction3)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvFileName.text = item.fileName
            holder.tvStatus.text = item.status
            holder.tvSize.text = formatFileSize(item.downloadedBytes) +
                    if (item.totalBytes > 0) " / ${formatFileSize(item.totalBytes)}" else ""
            holder.itemProgress.progress = item.progress

            when (item.status) {
                "Downloading" -> {
                    holder.btnAction1.text = "Pause"
                    holder.btnAction1.setOnClickListener { onPause(item) }
                    holder.btnAction2.text = "Cancel"
                    holder.btnAction2.setOnClickListener { onCancel(item) }
                    holder.btnAction3.visibility = View.GONE
                }
                "Paused" -> {
                    holder.btnAction1.text = "Resume"
                    holder.btnAction1.setOnClickListener { onResume(item) }
                    holder.btnAction2.text = "Cancel"
                    holder.btnAction2.setOnClickListener { onCancel(item) }
                    holder.btnAction3.visibility = View.GONE
                }
                "Completed" -> {
                    holder.btnAction1.text = "Open"
                    holder.btnAction1.setOnClickListener { onOpen(item) }
                    holder.btnAction2.text = "Share"
                    holder.btnAction2.setOnClickListener { onShare(item) }
                    holder.btnAction3.text = "Delete"
                    holder.btnAction3.visibility = View.VISIBLE
                    holder.btnAction3.setOnClickListener { onDelete(item) }
                }
                else -> {
                    holder.btnAction1.text = "Retry"
                    holder.btnAction1.setOnClickListener { /* retry logic */ }
                    holder.btnAction2.text = "Delete"
                    holder.btnAction2.setOnClickListener { onDelete(item) }
                    holder.btnAction3.visibility = View.GONE
                }
            }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: MutableList<DownloadItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
