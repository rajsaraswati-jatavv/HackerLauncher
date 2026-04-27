package com.hackerlauncher.modules

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * FEATURE 4: StorageManagerFragment
 * Storage analysis, duplicate file finder
 * Hacker-themed dark UI with green text (#00FF00) on black background (#0D0D0D)
 */
class StorageManagerFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val YELLOW = Color.parseColor("#FFFF00")
    private val CYAN = Color.parseColor("#00FFFF")
    private val BLACK = Color.parseColor("#0D0D0D")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")

    private lateinit var tvStorageOverview: TextView
    private lateinit var storageProgressBar: ProgressBar
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private var scanJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        rootLayout.addView(TextView(ctx).apply {
            text = "[ STORAGE MANAGER ]"
            setTextColor(GREEN)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })

        // Storage overview
        tvStorageOverview = TextView(ctx).apply {
            text = "Loading..."
            setTextColor(GREEN)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
            setBackgroundColor(DARK_GRAY)
        }
        rootLayout.addView(tvStorageOverview)

        // Progress bar
        storageProgressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(storageProgressBar)

        // Buttons
        rootLayout.addView(makeSectionHeader("ANALYSIS"))

        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeBtn("FULL ANALYSIS") { fullAnalysis() })
        row1.addView(makeBtn("DUPLICATE SCAN") { duplicateScan() })
        row1.addView(makeBtn("LARGE FILES") { largeFileScan() })
        rootLayout.addView(row1)

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeBtn("CATEGORY BREAK") { categoryBreakdown() })
        row2.addView(makeBtn("APP STORAGE") { appStorageScan() })
        row2.addView(makeBtn("CLEANUP") { quickCleanup() })
        rootLayout.addView(row2)

        // Output
        scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        tvOutput = TextView(ctx).apply {
            setTextColor(GREEN)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        rootLayout.addView(scrollView)

        updateStorageOverview()
        return rootLayout
    }

    private fun updateStorageOverview() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availBytes = stat.availableBytes
            val usedBytes = totalBytes - availBytes
            val pct = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()

            tvStorageOverview.text = buildString {
                appendLine("═══ STORAGE OVERVIEW ═══")
                appendLine("Total:  ${formatSize(totalBytes)}")
                appendLine("Used:   ${formatSize(usedBytes)} ($pct%)")
                appendLine("Free:   ${formatSize(availBytes)}")
                appendLine("───────────────────────")
            }
            storageProgressBar.progress = pct
        } catch (e: Exception) {
            tvStorageOverview.text = "Error: ${e.message}"
        }
    }

    private fun fullAnalysis() {
        if (scanJob?.isActive == true) return
        appendOutput("[>] Running full storage analysis...\n\n")
        scanJob = lifecycleScope.launch {
            val root = Environment.getExternalStorageDirectory()
            val categories = mutableMapOf<String, Long>()

            withContext(Dispatchers.IO) {
                val dirs = root.listFiles() ?: emptyArray()
                for ((index, dir) in dirs.withIndex()) {
                    if (dir.isDirectory) {
                        val size = calculateDirSize(dir)
                        if (size > 0) categories[dir.name] = size
                    }
                    withContext(Dispatchers.Main) {
                        storageProgressBar.progress = ((index + 1) * 100 / dirs.size)
                    }
                }
            }

            val sorted = categories.entries.sortedByDescending { it.value }
            val total = sorted.sumOf { it.value }

            appendOutput("═══ FULL STORAGE ANALYSIS ═══\n\n")
            for ((name, size) in sorted) {
                val pct = if (total > 0) ((size * 100 / total).toInt()) else 0
                val bar = "█".repeat((pct / 3).coerceAtMost(25))
                appendOutput("${name.take(15).padEnd(15)} ${formatSize(size).padEnd(10)} $bar $pct%\n")
            }
            appendOutput("\nTotal Used: ${formatSize(total)}\n\n")
            updateStorageOverview()
        }
    }

    private fun duplicateScan() {
        if (scanJob?.isActive == true) return
        appendOutput("[>] Scanning for duplicate files (MD5)...\n\n")
        scanJob = lifecycleScope.launch {
            val hashMap = mutableMapOf<String, MutableList<File>>()
            var filesScanned = 0

            withContext(Dispatchers.IO) {
                val root = Environment.getExternalStorageDirectory()
                scanForDuplicates(root, hashMap, 0, 5)
            }

            // Filter duplicates only
            val duplicates = hashMap.filter { it.value.size > 1 }
            var wastedSpace = 0L

            appendOutput("═══ DUPLICATE FILES ═══\n\n")
            if (duplicates.isEmpty()) {
                appendOutput("[+] No duplicate files found!\n\n")
            } else {
                for ((hash, files) in duplicates.entries.take(20)) {
                    val size = files.first().length()
                    wastedSpace += size * (files.size - 1)
                    appendOutput("Hash: ${hash.take(16)}...\n")
                    for (file in files) {
                        appendOutput("  → ${file.absolutePath} (${formatSize(size)})\n")
                    }
                    appendOutput("\n")
                }
                appendOutput("───────────────────────\n")
                appendOutput("Duplicate groups: ${duplicates.size}\n")
                appendOutput("Wasted space: ${formatSize(wastedSpace)}\n\n")
            }
        }
    }

    private suspend fun scanForDuplicates(dir: File, hashMap: MutableMap<String, MutableList<File>>, depth: Int, maxDepth: Int) {
        if (!dir.exists() || !dir.isDirectory || depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanForDuplicates(file, hashMap, depth + 1, maxDepth)
            } else if (file.length() > 1024 * 1024) { // Only files > 1MB
                try {
                    val hash = md5(file)
                    hashMap.getOrPut(hash) { mutableListOf() }.add(file)
                } catch (_: Exception) {}
            }
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val input = file.inputStream()
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        input.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun largeFileScan() {
        if (scanJob?.isActive == true) return
        appendOutput("[>] Scanning for large files (>50MB)...\n\n")
        scanJob = lifecycleScope.launch {
            val largeFiles = mutableListOf<Pair<File, Long>>()

            withContext(Dispatchers.IO) {
                val root = Environment.getExternalStorageDirectory()
                findLargeFiles(root, largeFiles, 0, 6)
            }

            largeFiles.sortByDescending { it.second }

            appendOutput("═══ LARGE FILES (>50MB) ═══\n\n")
            for ((file, size) in largeFiles.take(30)) {
                val bar = "█".repeat((size / (100 * 1024 * 1024)).coerceAtMost(25).toInt())
                appendOutput("${formatSize(size).padEnd(10)} ${file.name.take(40)}\n")
            }
            appendOutput("\nTotal large files: ${largeFiles.size}\n")
            appendOutput("Total size: ${formatSize(largeFiles.sumOf { it.second })}\n\n")
        }
    }

    private fun findLargeFiles(dir: File, results: MutableList<Pair<File, Long>>, depth: Int, maxDepth: Int) {
        if (!dir.exists() || !dir.isDirectory || depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                findLargeFiles(file, results, depth + 1, maxDepth)
            } else if (file.length() > 50 * 1024 * 1024) {
                results.add(Pair(file, file.length()))
            }
        }
    }

    private fun categoryBreakdown() {
        appendOutput("═══ STORAGE BY CATEGORY ═══\n\n")
        val root = Environment.getExternalStorageDirectory()
        val categories = mapOf(
            "Images" to listOf("DCIM", "Pictures"),
            "Music" to listOf("Music"),
            "Videos" to listOf("Movies", "Videos"),
            "Documents" to listOf("Documents", "Download"),
            "Android Data" to listOf("Android"),
            "Audio" to listOf("Audio", "Podcasts", "Ringtones"),
            "Other" to listOf("Alarms", "Notifications")
        )

        lifecycleScope.launch {
            for ((category, dirs) in categories) {
                var totalSize = 0L
                withContext(Dispatchers.IO) {
                    for (dirName in dirs) {
                        val dir = File(root, dirName)
                        if (dir.exists()) totalSize += calculateDirSize(dir)
                    }
                }
                val bar = if (totalSize > 0) "█".repeat((totalSize / (500 * 1024 * 1024)).coerceAtMost(25).toInt()) else ""
                appendOutput("${category.padEnd(15)} ${formatSize(totalSize).padEnd(10)} $bar\n")
            }
            appendOutput("\n")
        }
    }

    private fun appStorageScan() {
        appendOutput("[*] Scanning app storage usage...\n\n")
        lifecycleScope.launch {
            val pm = requireContext().packageManager
            val apps = withContext(Dispatchers.IO) { pm.getInstalledApplications(0) }
            val appSizes = mutableListOf<Pair<String, Long>>()

            withContext(Dispatchers.IO) {
                for (app in apps) {
                    var size = 0L
                    val dataDir = File("${Environment.getExternalStorageDirectory()}/Android/data/${app.packageName}")
                    if (dataDir.exists()) size += calculateDirSize(dataDir)
                    if (size > 10 * 1024 * 1024) { // Only apps > 10MB
                        val name = try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                        appSizes.add(Pair(name, size))
                    }
                }
            }

            appSizes.sortByDescending { it.second }

            appendOutput("═══ APP STORAGE USAGE ═══\n\n")
            for ((name, size) in appSizes.take(20)) {
                appendOutput("${name.take(25).padEnd(25)} ${formatSize(size)}\n")
            }
            appendOutput("\nTotal apps scanned: ${apps.size}\n")
            appendOutput("Apps > 10MB: ${appSizes.size}\n\n")
        }
    }

    private fun quickCleanup() {
        appendOutput("[*] Running quick cleanup...\n\n")
        lifecycleScope.launch {
            var totalFreed = 0L
            val dirs = mutableListOf<File>()
            requireContext().cacheDir?.let { dirs.add(it) }
            requireContext().externalCacheDir?.let { dirs.add(it) }
            dirs.add(File(Environment.getExternalStorageDirectory(), ".thumbnails"))

            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    val size = calculateDirSize(dir)
                    dir.listFiles()?.forEach { it.deleteRecursively() }
                    totalFreed += size
                    appendOutput("[+] Cleaned: ${dir.name} (${formatSize(size)})\n")
                }
            }
            appendOutput("\n[+] Total freed: ${formatSize(totalFreed)}\n\n")
            updateStorageOverview()
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1073741824 -> "${"%.1f".format(bytes / 1073741824.0)} GB"
            bytes >= 1048576 -> "${"%.1f".format(bytes / 1048576.0)} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun makeSectionHeader(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = "▸ $text"
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 12, 0, 4)
        }
    }

    private fun makeBtn(label: String, listener: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            setTextColor(GREEN)
            setBackgroundColor(DARK_GRAY)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(2, 2, 2, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 1, 1, 1)
            }
            setOnClickListener { listener() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
    }
}
