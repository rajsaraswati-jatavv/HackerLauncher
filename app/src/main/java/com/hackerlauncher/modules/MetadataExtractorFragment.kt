package com.hackerlauncher.modules

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface as AndroidXExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MetadataField(
    val key: String,
    val value: String
)

class MetadataExtractorFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private val metadataFields = mutableListOf<MetadataField>()
    private var currentFileName = ""

    private lateinit var mainLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var metadataContainer: LinearLayout
    private lateinit var fileNameText: TextView

    private val FILE_PICK_REQUEST = 4242

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
        mainLayout.addView(makeTitle("[>] METADATA EXTRACTOR"))

        // Status
        statusText = makeLabel("[>] Select a file to extract metadata")
        mainLayout.addView(statusText)

        // File selection buttons
        mainLayout.addView(makeSectionHeader("SELECT FILE TYPE"))

        val btnRow1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        btnRow1.addView(makeHalfButton("IMAGE") { pickFile("image/*") })
        btnRow1.addView(makeHalfButton("DOCUMENT") { pickFile("application/*") })
        mainLayout.addView(btnRow1)

        val btnRow2 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        btnRow2.addView(makeHalfButton("APK") { pickFile("application/vnd.android.package-archive") })
        btnRow2.addView(makeHalfButton("ANY FILE") { pickFile("*/*") })
        mainLayout.addView(btnRow2)

        // File name
        fileNameText = makeLabel("No file selected")
        mainLayout.addView(fileNameText)

        // Action buttons
        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        actionRow.addView(makeHalfButton("COPY ALL") { copyMetadata() })
        actionRow.addView(makeHalfButton("SHARE") { shareMetadata() })
        mainLayout.addView(actionRow)

        // Metadata container
        mainLayout.addView(makeSectionHeader("METADATA"))

        metadataContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(metadataContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    private fun pickFile(mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
            }
            startActivityForResult(intent, FILE_PICK_REQUEST)
        } catch (e: Exception) {
            statusText.text = "[!] Error opening file picker: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICK_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                processFile(uri)
            }
        }
    }

    private fun processFile(uri: Uri) {
        statusText.text = "[~] Extracting metadata..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                extractMetadata(uri)
            }
            metadataFields.clear()
            metadataFields.addAll(result)
            withContext(Dispatchers.Main) {
                renderMetadata()
                statusText.text = "[+] Found ${metadataFields.size} metadata fields"
            }
        }
    }

    private fun extractMetadata(uri: Uri): List<MetadataField> {
        val fields = mutableListOf<MetadataField>()

        try {
            // Basic file info from ContentResolver
            extractBasicFileInfo(uri, fields)

            // Determine file type and extract accordingly
            val mimeType = requireContext().contentResolver.getType(uri) ?: ""
            val fileName = fields.find { it.key == "File Name" }?.value ?: ""

            when {
                mimeType.startsWith("image/") || fileName.lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp|heic|heif|tiff|tif|bmp)$")) -> {
                    extractImageExif(uri, fields)
                }
                mimeType == "application/vnd.android.package-archive" || fileName.lowercase().endsWith(".apk") -> {
                    extractApkMetadata(uri, fields)
                }
                else -> {
                    // Generic file metadata
                    extractGenericMetadata(uri, fields)
                }
            }
        } catch (e: Exception) {
            fields.add(MetadataField("ERROR", "Extraction failed: ${e.message}"))
        }

        return fields
    }

    private fun extractBasicFileInfo(uri: Uri, fields: MutableList<MetadataField>) {
        try {
            fields.add(MetadataField("URI", uri.toString()))
            fields.add(MetadataField("Scheme", uri.scheme ?: ""))

            // Content resolver info
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // File name
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = it.getString(nameIdx) ?: ""
                        currentFileName = name
                        fields.add(MetadataField("File Name", name))
                    }

                    // File size
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0) {
                        val size = it.getLong(sizeIdx)
                        fields.add(MetadataField("File Size", formatSize(size)))
                        fields.add(MetadataField("File Size (bytes)", size.toString()))
                    }

                    // Other columns
                    val columnNames = it.columnNames
                    for (colName in columnNames) {
                        try {
                            val idx = it.getColumnIndex(colName)
                            if (idx >= 0 && colName != OpenableColumns.DISPLAY_NAME && colName != OpenableColumns.SIZE) {
                                val value = it.getString(idx) ?: ""
                                if (value.isNotBlank()) {
                                    fields.add(MetadataField("CR: $colName", value))
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (e: Exception) {
            fields.add(MetadataField("Basic Info Error", e.message ?: "Unknown"))
        }
    }

    private fun extractImageExif(uri: Uri, fields: MutableList<MetadataField>) {
        try {
            fields.add(MetadataField("--- EXIF DATA ---", ""))

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                try {
                    val exif = AndroidXExifInterface(inputStream)

                    // Standard EXIF tags
                    val exifTags = mapOf(
                        "Image Width" to AndroidXExifInterface.TAG_IMAGE_WIDTH,
                        "Image Height" to "ImageLength",
                        "Make" to AndroidXExifInterface.TAG_MAKE,
                        "Model" to AndroidXExifInterface.TAG_MODEL,
                        "Orientation" to AndroidXExifInterface.TAG_ORIENTATION,
                        "Date/Time" to AndroidXExifInterface.TAG_DATETIME,
                        "Date/Time Original" to AndroidXExifInterface.TAG_DATETIME_ORIGINAL,
                        "Date/Time Digitized" to AndroidXExifInterface.TAG_DATETIME_DIGITIZED,
                        "Software" to AndroidXExifInterface.TAG_SOFTWARE,
                        "Artist" to AndroidXExifInterface.TAG_ARTIST,
                        "Copyright" to AndroidXExifInterface.TAG_COPYRIGHT,
                        "Exposure Time" to AndroidXExifInterface.TAG_EXPOSURE_TIME,
                        "F-Number" to AndroidXExifInterface.TAG_F_NUMBER,
                        "Exposure Program" to AndroidXExifInterface.TAG_EXPOSURE_PROGRAM,
                        "ISO Speed" to AndroidXExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                        "Shutter Speed" to AndroidXExifInterface.TAG_SHUTTER_SPEED_VALUE,
                        "Aperture" to AndroidXExifInterface.TAG_APERTURE_VALUE,
                        "Brightness" to AndroidXExifInterface.TAG_BRIGHTNESS_VALUE,
                        "Max Aperture" to AndroidXExifInterface.TAG_MAX_APERTURE_VALUE,
                        "Metering Mode" to AndroidXExifInterface.TAG_METERING_MODE,
                        "Flash" to AndroidXExifInterface.TAG_FLASH,
                        "Focal Length" to AndroidXExifInterface.TAG_FOCAL_LENGTH,
                        "Subject Distance" to AndroidXExifInterface.TAG_SUBJECT_DISTANCE,
                        "White Balance" to AndroidXExifInterface.TAG_WHITE_BALANCE,
                        "Color Space" to AndroidXExifInterface.TAG_COLOR_SPACE,
                        "GPS Latitude" to AndroidXExifInterface.TAG_GPS_LATITUDE,
                        "GPS Longitude" to AndroidXExifInterface.TAG_GPS_LONGITUDE,
                        "GPS Altitude" to AndroidXExifInterface.TAG_GPS_ALTITUDE,
                        "GPS Timestamp" to AndroidXExifInterface.TAG_GPS_DATESTAMP,
                        "Lens Make" to AndroidXExifInterface.TAG_LENS_MAKE,
                        "Lens Model" to AndroidXExifInterface.TAG_LENS_MODEL,
                        "Image Description" to AndroidXExifInterface.TAG_IMAGE_DESCRIPTION,
                        "X Resolution" to AndroidXExifInterface.TAG_X_RESOLUTION,
                        "Y Resolution" to AndroidXExifInterface.TAG_Y_RESOLUTION,
                        "Resolution Unit" to AndroidXExifInterface.TAG_RESOLUTION_UNIT,
                        "Scene Type" to AndroidXExifInterface.TAG_SCENE_TYPE,
                        "Sensing Method" to AndroidXExifInterface.TAG_SENSING_METHOD,
                        "Compression" to AndroidXExifInterface.TAG_COMPRESSION,
                        "Bits Per Sample" to AndroidXExifInterface.TAG_BITS_PER_SAMPLE,
                        "Photometric Interpretation" to AndroidXExifInterface.TAG_PHOTOMETRIC_INTERPRETATION
                    )

                    for ((label, tag) in exifTags) {
                        try {
                            val value = exif.getAttribute(tag)
                            if (!value.isNullOrEmpty()) {
                                fields.add(MetadataField(label, value))
                            }
                        } catch (_: Exception) { }
                    }

                    // GPS coordinates
                    try {
                        val latLong = exif.latLong
                        if (latLong != null) {
                            fields.add(MetadataField("GPS Coordinates", "${latLong[0]}, ${latLong[1]}"))
                            fields.add(MetadataField("Google Maps", "https://maps.google.com/?q=${latLong[0]},${latLong[1]}"))
                        }
                    } catch (_: Exception) { }

                    // Altitude
                    try {
                        val altitudeStr = exif.getAttribute(AndroidXExifInterface.TAG_GPS_ALTITUDE)
                        if (!altitudeStr.isNullOrEmpty()) {
                            val altitude = altitudeStr.toDoubleOrNull()
                            if (altitude != null && !altitude.isNaN()) {
                                fields.add(MetadataField("GPS Altitude (computed)", "${"%.2f".format(altitude)}m"))
                            }
                        }
                    } catch (_: Exception) { }

                    // Thumbnail
                    try {
                        val hasThumbnail = exif.hasThumbnail()
                        fields.add(MetadataField("Has Thumbnail", hasThumbnail.toString()))
                    } catch (_: Exception) { }

                    // Additional EXIF tags (manual iteration)
                    try {
                        val additionalTags = listOf(
                            AndroidXExifInterface.TAG_DATETIME,
                            AndroidXExifInterface.TAG_MAKE,
                            AndroidXExifInterface.TAG_MODEL,
                            AndroidXExifInterface.TAG_ORIENTATION,
                            AndroidXExifInterface.TAG_FLASH,
                            AndroidXExifInterface.TAG_FOCAL_LENGTH,
                            AndroidXExifInterface.TAG_WHITE_BALANCE,
                            AndroidXExifInterface.TAG_GPS_LATITUDE,
                            AndroidXExifInterface.TAG_GPS_LONGITUDE,
                            AndroidXExifInterface.TAG_GPS_ALTITUDE,
                            AndroidXExifInterface.TAG_GPS_DATESTAMP,
                            AndroidXExifInterface.TAG_SOFTWARE,
                            AndroidXExifInterface.TAG_COPYRIGHT
                        )
                        fields.add(MetadataField("--- ADDITIONAL EXIF TAGS ---", ""))
                        for (tag in additionalTags) {
                            try {
                                val value = exif.getAttribute(tag)
                                if (!value.isNullOrEmpty() && fields.none { it.key == tag || it.value == value && it.key != tag }) {
                                    fields.add(MetadataField("EXIF: $tag", value))
                                }
                            } catch (_: Exception) { }
                        }
                    } catch (_: Exception) { }

                } finally {
                    inputStream.close()
                }
            }
        } catch (e: Exception) {
            fields.add(MetadataField("EXIF Error", e.message ?: "Unknown"))
        }
    }

    private fun extractApkMetadata(uri: Uri, fields: MutableList<MetadataField>) {
        try {
            fields.add(MetadataField("--- APK METADATA ---", ""))

            // Copy APK to temp file for PackageInfo parsing
            val tempFile = File(requireContext().cacheDir, "temp_metadata.apk")
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    FileOutputStream(tempFile).use { fos ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (inputStream.read(buf).also { len = it } > 0) {
                            fos.write(buf, 0, len)
                        }
                    }
                    inputStream.close()

                    val pm = requireContext().packageManager
                    val pkgInfo = pm.getPackageArchiveInfo(tempFile.absolutePath,
                        PackageManager.GET_PERMISSIONS or
                        PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS or
                        PackageManager.GET_SIGNATURES or
                        PackageManager.GET_META_DATA or
                        PackageManager.GET_SHARED_LIBRARY_FILES
                    )

                    if (pkgInfo != null) {
                        val appInfo = pkgInfo.applicationInfo
                        appInfo.sourceDir = tempFile.absolutePath
                        appInfo.publicSourceDir = tempFile.absolutePath

                        // Basic info
                        val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { "N/A" }
                        fields.add(MetadataField("App Name", appName))
                        fields.add(MetadataField("Package Name", pkgInfo.packageName))
                        fields.add(MetadataField("Version Name", pkgInfo.versionName ?: "N/A"))

                        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pkgInfo.longVersionCode.toString()
                        } else {
                            @Suppress("DEPRECATION")
                            pkgInfo.versionCode.toString()
                        }
                        fields.add(MetadataField("Version Code", versionCode))

                        // SDK versions
                        fields.add(MetadataField("Min SDK", appInfo.minSdkVersion.toString()))
                        fields.add(MetadataField("Target SDK", appInfo.targetSdkVersion.toString()))

                        // App flags
                        val flags = appInfo.flags
                        fields.add(MetadataField("System App", ((flags and ApplicationInfo.FLAG_SYSTEM) != 0).toString()))
                        fields.add(MetadataField("Debuggable", ((flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0).toString()))
                        fields.add(MetadataField("Large Heap", ((flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0).toString()))
                        fields.add(MetadataField("Has Code", ((flags and ApplicationInfo.FLAG_HAS_CODE) != 0).toString()))
                        fields.add(MetadataField("Allow Backup", ((flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0).toString()))
                        fields.add(MetadataField("Allow Clear User Data", ((flags and ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) != 0).toString()))

                        // Permissions
                        try {
                            val permissions = pkgInfo.requestedPermissions
                            if (permissions != null && permissions.isNotEmpty()) {
                                fields.add(MetadataField("Permissions Count", permissions.size.toString()))
                                fields.add(MetadataField("--- PERMISSIONS ---", ""))
                                for (perm in permissions.sorted()) {
                                    fields.add(MetadataField("Permission", perm))
                                }
                            }
                        } catch (_: Exception) { }

                        // Activities
                        try {
                            val activities = pkgInfo.activities
                            if (activities != null && activities.isNotEmpty()) {
                                fields.add(MetadataField("Activities Count", activities.size.toString()))
                                fields.add(MetadataField("--- ACTIVITIES ---", ""))
                                for (act in activities) {
                                    fields.add(MetadataField("Activity", act.name))
                                }
                            }
                        } catch (_: Exception) { }

                        // Services
                        try {
                            val services = pkgInfo.services
                            if (services != null && services.isNotEmpty()) {
                                fields.add(MetadataField("Services Count", services.size.toString()))
                                fields.add(MetadataField("--- SERVICES ---", ""))
                                for (svc in services) {
                                    fields.add(MetadataField("Service", svc.name))
                                }
                            }
                        } catch (_: Exception) { }

                        // Receivers
                        try {
                            val receivers = pkgInfo.receivers
                            if (receivers != null && receivers.isNotEmpty()) {
                                fields.add(MetadataField("Receivers Count", receivers.size.toString()))
                                fields.add(MetadataField("--- RECEIVERS ---", ""))
                                for (rcv in receivers) {
                                    fields.add(MetadataField("Receiver", rcv.name))
                                }
                            }
                        } catch (_: Exception) { }

                        // Providers
                        try {
                            val providers = pkgInfo.providers
                            if (providers != null && providers.isNotEmpty()) {
                                fields.add(MetadataField("Providers Count", providers.size.toString()))
                                fields.add(MetadataField("--- PROVIDERS ---", ""))
                                for (prov in providers) {
                                    fields.add(MetadataField("Provider", prov.name))
                                }
                            }
                        } catch (_: Exception) { }

                        // Signatures
                        try {
                            val signatures = pkgInfo.signatures
                            if (signatures != null && signatures.isNotEmpty()) {
                                fields.add(MetadataField("--- SIGNING INFO ---", ""))
                                for ((idx, sig) in signatures.withIndex()) {
                                    val md5 = java.security.MessageDigest.getInstance("MD5").digest(sig.toByteArray())
                                        .joinToString(":") { "%02X".format(it) }
                                    val sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())
                                        .joinToString(":") { "%02X".format(it) }
                                    val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                                        .joinToString(":") { "%02X".format(it) }

                                    fields.add(MetadataField("Signature $idx MD5", md5))
                                    fields.add(MetadataField("Signature $idx SHA-1", sha1))
                                    fields.add(MetadataField("Signature $idx SHA-256", sha256))
                                }
                            }
                        } catch (_: Exception) { }

                        // Meta-data
                        try {
                            val metaData = appInfo.metaData
                            if (metaData != null && metaData.size() > 0) {
                                fields.add(MetadataField("--- META-DATA ---", ""))
                                for (key in metaData.keySet()) {
                                    val value = metaData.get(key)?.toString() ?: "null"
                                    fields.add(MetadataField("Meta: $key", value))
                                }
                            }
                        } catch (_: Exception) { }

                        // Shared libraries
                        try {
                            val sharedLibs = appInfo.sharedLibraryFiles
                            if (sharedLibs != null && sharedLibs.isNotEmpty()) {
                                fields.add(MetadataField("--- SHARED LIBRARIES ---", ""))
                                for (lib in sharedLibs) {
                                    fields.add(MetadataField("Library", lib))
                                }
                            }
                        } catch (_: Exception) { }
                    } else {
                        fields.add(MetadataField("APK Parse", "Could not parse APK - may be corrupted"))
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            fields.add(MetadataField("APK Error", e.message ?: "Unknown"))
        }
    }

    private fun extractGenericMetadata(uri: Uri, fields: MutableList<MetadataField>) {
        try {
            fields.add(MetadataField("--- FILE METADATA ---", ""))

            // Try to read first bytes for magic number detection
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                try {
                    val header = ByteArray(32)
                    val read = inputStream.read(header)
                    if (read > 0) {
                        val hexHeader = header.take(read).joinToString(" ") { "%02X".format(it) }
                        fields.add(MetadataField("File Header (hex)", hexHeader))

                        // Detect common file types by magic number
                        val magic = if (read >= 4) {
                            String(header.take(4).toByteArray(), Charsets.US_ASCII)
                        } else ""
                        val detectedType = when {
                            header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte() -> "PDF"
                            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() -> "ZIP/APK/DOCX"
                            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> "PNG"
                            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "JPEG"
                            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() -> "GIF"
                            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() -> "RIFF (AVI/WAV/WebP)"
                            header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() && header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> "MKV/WebM"
                            header[0] == 0x00.toByte() && header[1] == 0x00.toByte() && header[2] == 0x00.toByte() && (header[3] == 0x18.toByte() || header[3] == 0x1C.toByte()) -> "MP4"
                            else -> "Unknown ($magic)"
                        }
                        fields.add(MetadataField("Detected Type", detectedType))
                    }
                } finally {
                    inputStream.close()
                }
            }

            // MIME type
            val mimeType = requireContext().contentResolver.getType(uri) ?: "unknown"
            fields.add(MetadataField("MIME Type", mimeType))

        } catch (e: Exception) {
            fields.add(MetadataField("Generic Error", e.message ?: "Unknown"))
        }
    }

    private fun renderMetadata() {
        metadataContainer.removeAllViews()

        if (metadataFields.isEmpty()) {
            metadataContainer.addView(TextView(requireContext()).apply {
                text = "[~] No metadata found"
                setTextColor(DARK_GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        // Update file name
        fileNameText.text = "File: $currentFileName"

        for ((index, field) in metadataFields.withIndex()) {
            // Section headers
            if (field.key.startsWith("---") && field.key.endsWith("---")) {
                metadataContainer.addView(TextView(requireContext()).apply {
                    text = field.key
                    setTextColor(Color.parseColor("#FFFF00"))
                    textSize = 13f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                    setPadding(0, 8, 0, 4)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
                continue
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                setPadding(8, 4, 8, 4)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            // Key
            row.addView(TextView(requireContext()).apply {
                text = field.key
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
                layoutParams = LinearLayout.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.4).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Value
            row.addView(TextView(requireContext()).apply {
                text = field.value.take(200) + if (field.value.length > 200) "..." else ""
                setTextColor(GREEN)
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setOnClickListener {
                    // Copy single value
                    try {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(field.key, field.value))
                        Toast.makeText(requireContext(), "Copied: ${field.key}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { }
                }
            })

            metadataContainer.addView(row)
        }

        // Summary
        metadataContainer.addView(TextView(requireContext()).apply {
            text = "\n[+] Total fields: ${metadataFields.count { !it.key.startsWith("---") }}"
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
    }

    private fun copyMetadata() {
        if (metadataFields.isEmpty()) {
            Toast.makeText(requireContext(), "No metadata to copy", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sb = StringBuilder()
            sb.appendLine("=== METADATA: $currentFileName ===")
            for (field in metadataFields) {
                if (field.key.startsWith("---") && field.key.endsWith("---")) {
                    sb.appendLine("\n${field.key}")
                } else {
                    sb.appendLine("${field.key}: ${field.value}")
                }
            }

            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Metadata", sb.toString()))
            Toast.makeText(requireContext(), "Metadata copied to clipboard", Toast.LENGTH_SHORT).show()
            statusText.text = "[+] Metadata copied"
        } catch (e: Exception) {
            statusText.text = "[!] Copy error: ${e.message}"
        }
    }

    private fun shareMetadata() {
        if (metadataFields.isEmpty()) {
            Toast.makeText(requireContext(), "No metadata to share", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sb = StringBuilder()
            sb.appendLine("=== METADATA: $currentFileName ===")
            for (field in metadataFields) {
                if (field.key.startsWith("---") && field.key.endsWith("---")) {
                    sb.appendLine("\n${field.key}")
                } else {
                    sb.appendLine("${field.key}: ${field.value}")
                }
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                putExtra(Intent.EXTRA_SUBJECT, "Metadata: $currentFileName")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Metadata"))
        } catch (e: Exception) {
            statusText.text = "[!] Share error: ${e.message}"
        }
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

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
