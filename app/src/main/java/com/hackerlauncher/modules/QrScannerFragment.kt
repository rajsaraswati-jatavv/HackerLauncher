package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class QrScannerFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var resultOverlay: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var scanHistoryRecycler: RecyclerView
    private lateinit var fabGenerate: FloatingActionButton
    private lateinit var fabToggleCamera: FloatingActionButton
    private lateinit var emptyStateText: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var isFlashOn = false
    private var useFrontCamera = false

    private val scanHistory = mutableListOf<ScanResult>()
    private lateinit var historyAdapter: ScanHistoryAdapter

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showToast("Camera permission required")
    }

    data class ScanResult(
        val content: String,
        val format: String,
        val timestamp: Long
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_qr_scanner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.previewView)
        resultOverlay = view.findViewById(R.id.resultOverlay)
        resultText = view.findViewById(R.id.resultText)
        progressBar = view.findViewById(R.id.progressBar)
        scanHistoryRecycler = view.findViewById(R.id.scanHistoryRecycler)
        fabGenerate = view.findViewById(R.id.fabGenerate)
        fabToggleCamera = view.findViewById(R.id.fabToggleCamera)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        historyAdapter = ScanHistoryAdapter(scanHistory) { result ->
            showResultDialog(result)
        }
        scanHistoryRecycler.layoutManager = LinearLayoutManager(requireContext())
        scanHistoryRecycler.adapter = historyAdapter

        fabGenerate.setOnClickListener { showGenerateDialog() }
        fabToggleCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            startCamera()
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                showToast("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_REAR_CAMERA
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

        try {
            val camera = provider.bindToLifecycle(
                viewLifecycleOwner, cameraSelector, preview, imageAnalysis
            )
        } catch (e: Exception) {
            showToast("Camera bind failed: ${e.message}")
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue ?: continue
                        activity?.runOnUiThread {
                            handleScannedBarcode(barcode)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private var lastScannedContent: String? = null
    private var lastScanTime: Long = 0

    private fun handleScannedBarcode(barcode: Barcode) {
        val content = barcode.rawValue ?: return
        val now = System.currentTimeMillis()

        // Debounce: ignore same scan within 2 seconds
        if (content == lastScannedContent && now - lastScanTime < 2000) return
        lastScannedContent = content
        lastScanTime = now

        val formatName = when (barcode.format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "Aztec"
            else -> "Unknown"
        }

        val result = ScanResult(content, formatName, now)
        scanHistory.add(0, result)
        historyAdapter.notifyItemInserted(0)
        emptyStateText.visibility = View.GONE

        showResultDialog(result)
    }

    private fun showResultDialog(result: ScanResult) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_qr_result, null)

        val tvContent: TextView = sheetView.findViewById(R.id.tvResultContent)
        val tvFormat: TextView = sheetView.findViewById(R.id.tvResultFormat)
        val tvTime: TextView = sheetView.findViewById(R.id.tvResultTime)
        val chipCopy: Chip = sheetView.findViewById(R.id.chipCopy)
        val chipOpen: Chip = sheetView.findViewById(R.id.chipOpenUrl)
        val chipShare: Chip = sheetView.findViewById(R.id.chipShare)
        val chipGenerate: Chip = sheetView.findViewById(R.id.chipGenerate)

        tvContent.text = result.content
        tvFormat.text = "Format: ${result.format}"
        tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(result.timestamp))

        val isUrl = result.content.startsWith("http://") || result.content.startsWith("https://")
        chipOpen.visibility = if (isUrl) View.VISIBLE else View.GONE

        chipCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", result.content))
            showToast("Copied to clipboard")
        }

        chipOpen.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(result.content))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Cannot open URL")
            }
        }

        chipShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, result.content)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        chipGenerate.setOnClickListener {
            generateQrBitmap(result.content)?.let { bitmap ->
                showQrBitmapDialog(bitmap)
            }
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun generateQrBitmap(text: String, size: Int = 512): Bitmap? {
        return try {
            val multiWriter = MultiWriter()
            val bitMatrix: BitMatrix = multiWriter.encode(
                text, BarcodeFormat.QR_CODE, size, size
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: WriterException) {
            showToast("QR generation failed: ${e.message}")
            null
        }
    }

    private fun showQrBitmapDialog(bitmap: Bitmap) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
        }

        val shareButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "Share QR Code"
            setOnClickListener {
                shareQrBitmap(bitmap)
            }
        }

        sheetView.addView(imageView)
        sheetView.addView(shareButton)
        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun shareQrBitmap(bitmap: Bitmap) {
        // Save to cache and share via intent
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val cachePath = java.io.File(requireContext().cacheDir, "qr_shared.png")
                    cachePath.parentFile?.mkdirs()
                    java.io.FileOutputStream(cachePath).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        cachePath
                    )
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            type = "image/png"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Share failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showGenerateDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter text or URL"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.parseColor("#005500"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Generate QR Code")
            .setView(editText)
            .setPositiveButton("Generate") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    lifecycleScope.launch {
                        progressBar.visibility = View.VISIBLE
                        val bitmap = withContext(Dispatchers.Default) {
                            generateQrBitmap(text)
                        }
                        progressBar.visibility = View.GONE
                        if (bitmap != null) {
                            showQrBitmapDialog(bitmap)
                        }
                    }
                } else {
                    showToast("Enter text to generate QR code")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
    }

    // --- Inner Adapter ---

    inner class ScanHistoryAdapter(
        private val items: List<ScanResult>,
        private val onClick: (ScanResult) -> Unit
    ) : RecyclerView.Adapter<ScanHistoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvContent: TextView = view.findViewById(R.id.tvHistoryContent)
            val tvFormat: TextView = view.findViewById(R.id.tvHistoryFormat)
            val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_history, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvContent.text = item.content.take(80) + if (item.content.length > 80) "..." else ""
            holder.tvFormat.text = item.format
            holder.tvTime.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(item.timestamp))
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
