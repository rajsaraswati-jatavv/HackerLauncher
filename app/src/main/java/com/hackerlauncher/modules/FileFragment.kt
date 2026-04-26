package com.hackerlauncher.modules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

class FileFragment : Fragment() {

    private lateinit var tvFileOutput: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var scrollView: ScrollView
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentPath = "/sdcard"
    private val fileAdapter = FileAdapter { file -> navigateTo(file) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_file, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvFileOutput = view.findViewById(R.id.tvFileOutput)
        rvFiles = view.findViewById(R.id.rvFiles)
        scrollView = view.findViewById(R.id.scrollViewFile)
        val btnBrowse = view.findViewById<Button>(R.id.btnBrowse)
        val btnEncrypt = view.findViewById<Button>(R.id.btnEncrypt)
        val btnDecrypt = view.findViewById<Button>(R.id.btnDecrypt)
        val btnSecureDelete = view.findViewById<Button>(R.id.btnSecureDelete)
        val btnViewFile = view.findViewById<Button>(R.id.btnViewFile)
        val etFilePath = view.findViewById<EditText>(R.id.etFilePath)

        rvFiles.layoutManager = LinearLayoutManager(requireContext())
        rvFiles.adapter = fileAdapter

        btnBrowse.setOnClickListener {
            val path = etFilePath.text.toString().ifBlank { "/sdcard" }
            browseDirectory(path)
        }

        btnViewFile.setOnClickListener {
            val path = etFilePath.text.toString().trim()
            if (path.isNotEmpty()) viewFile(path) else appendOutput("[!] Enter file path\n")
        }

        btnEncrypt.setOnClickListener {
            val path = etFilePath.text.toString().trim()
            if (path.isNotEmpty()) encryptFile(path) else appendOutput("[!] Enter file path\n")
        }

        btnDecrypt.setOnClickListener {
            val path = etFilePath.text.toString().trim()
            if (path.isNotEmpty()) decryptFile(path) else appendOutput("[!] Enter file path\n")
        }

        btnSecureDelete.setOnClickListener {
            val path = etFilePath.text.toString().trim()
            if (path.isNotEmpty()) secureDelete(path) else appendOutput("[!] Enter file path\n")
        }

        browseDirectory(currentPath)
    }

    private fun browseDirectory(path: String) {
        scope.launch {
            currentPath = path
            val files = withContext(Dispatchers.IO) {
                val dir = File(path)
                if (dir.isDirectory && dir.exists()) {
                    dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })?.toList() ?: emptyList()
                } else emptyList()
            }
            fileAdapter.updateFiles(files)
            appendOutput("[*] Directory: $path (${files.size} items)\n")
        }
    }

    private fun navigateTo(file: File) {
        if (file.isDirectory) {
            browseDirectory(file.absolutePath)
        } else {
            viewFile(file.absolutePath)
        }
    }

    private fun viewFile(path: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val file = File(path)
                if (!file.exists()) return@withContext "[E] File not found: $path"
                if (file.isDirectory) return@withContext "[E] Is a directory: $path"
                if (file.length() > 1_000_000) return@withContext "[!] File too large (${file.length()} bytes). Showing first 10KB.\n" +
                    file.readText().take(10000)

                "═══ File: $path ═══\n" +
                "Size: ${formatFileSize(file.length())}\n" +
                "Modified: ${java.text.SimpleDateFormat.getDateTimeInstance().format(file.lastModified())}\n" +
                "Readable: ${file.canRead()}\n" +
                "Writable: ${file.canWrite()}\n\n" +
                file.readText().take(5000) + "\n════════════════════════"
            }
            appendOutput(result + "\n")
        }
    }

    private fun encryptFile(path: String) {
        scope.launch {
            appendOutput("[*] Encrypting: $path\n")
            val result = withContext(Dispatchers.IO) {
                try {
                    val inputFile = File(path)
                    if (!inputFile.exists()) return@withContext "[E] File not found"
                    val outputFile = File("$path.enc")

                    // Generate AES key
                    val keyGen = KeyGenerator.getInstance("AES")
                    keyGen.init(256)
                    val secretKey = keyGen.generateKey()
                    val keyBytes = secretKey.encoded

                    // Save key to separate file
                    val keyFile = File("$path.key")
                    keyFile.writeBytes(keyBytes)

                    // Encrypt
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    val iv = cipher.iv

                    val fis = FileInputStream(inputFile)
                    val fos = FileOutputStream(outputFile)
                    fos.write(iv) // Write IV at beginning

                    val buffer = ByteArray(4096)
                    var read: Int
                    while (fis.read(buffer).also { read = it } > 0) {
                        val encrypted = cipher.update(buffer, 0, read)
                        if (encrypted != null) fos.write(encrypted)
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) fos.write(finalBytes)

                    fis.close()
                    fos.close()

                    "═══ File Encrypted ═══\nInput:  $path\nOutput: $path.enc\nKey:    $path.key\n[!] Keep the .key file safe! You need it to decrypt.\n════════════════════════"
                } catch (e: Exception) {
                    "[E] Encryption failed: ${e.message}"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun decryptFile(path: String) {
        scope.launch {
            appendOutput("[*] Decrypting: $path\n")
            val result = withContext(Dispatchers.IO) {
                try {
                    val encFile = File(path)
                    val keyFile = File(path.removeSuffix(".enc") + ".key")
                    if (!encFile.exists()) return@withContext "[E] Encrypted file not found"
                    if (!keyFile.exists()) return@withContext "[E] Key file not found: ${keyFile.absolutePath}"

                    val keyBytes = keyFile.readBytes()
                    val secretKey = SecretKeySpec(keyBytes, "AES")

                    val fis = FileInputStream(encFile)
                    val iv = ByteArray(16)
                    fis.read(iv)

                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

                    val outputFile = File(path.removeSuffix(".enc") + ".dec")
                    val fos = FileOutputStream(outputFile)

                    val buffer = ByteArray(4096)
                    var read: Int
                    while (fis.read(buffer).also { read = it } > 0) {
                        val decrypted = cipher.update(buffer, 0, read)
                        if (decrypted != null) fos.write(decrypted)
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) fos.write(finalBytes)

                    fis.close()
                    fos.close()

                    "═══ File Decrypted ═══\nInput:  $path\nOutput: ${outputFile.absolutePath}\n════════════════════════"
                } catch (e: Exception) {
                    "[E] Decryption failed: ${e.message}"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun secureDelete(path: String) {
        scope.launch {
            appendOutput("[*] Securely deleting: $path\n")
            val result = withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (!file.exists()) return@withContext "[E] File not found"

                    // Overwrite with random data 3 times
                    val random = SecureRandom()
                    repeat(3) { pass ->
                        val fos = FileOutputStream(file)
                        val length = file.length()
                        val buffer = ByteArray(minOf(length, 4096L).toInt())
                        var remaining = length
                        while (remaining > 0) {
                            random.nextBytes(buffer)
                            val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                            fos.write(buffer, 0, toWrite)
                            remaining -= toWrite
                        }
                        fos.flush()
                        fos.fd.sync()
                        fos.close()
                    }

                    // Delete the file
                    val deleted = file.delete()
                    if (deleted) "═══ Secure Delete ═══\nFile: $path\nOverwrites: 3\nDeleted: YES\n════════════════════════"
                    else "[E] File overwritten but could not be deleted"
                } catch (e: Exception) {
                    "[E] Secure delete failed: ${e.message}"
                }
            }
            appendOutput(result + "\n")
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvFileOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }

    inner class FileAdapter(private val onClick: (File) -> Unit) :
        RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        private var files = listOf<File>()

        fun updateFiles(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val file = files[position]
            holder.tvName.text = file.name
            holder.tvInfo.text = if (file.isDirectory) {
                "${file.listFiles()?.size ?: 0} items"
            } else {
                formatFileSize(file.length())
            }
            holder.tvIcon.text = if (file.isDirectory) "[DIR]" else "[FILE]"
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = files.size

        inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvFileName)
            val tvInfo: TextView = view.findViewById(R.id.tvFileInfo)
            val tvIcon: TextView = view.findViewById(R.id.tvFileIcon)
        }
    }
}
