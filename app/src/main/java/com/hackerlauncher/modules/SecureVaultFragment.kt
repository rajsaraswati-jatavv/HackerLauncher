package com.hackerlauncher.modules

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecureVaultFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var etPin: EditText
    private lateinit var etConfirmPin: EditText
    private lateinit var etImportPath: EditText
    private var isVaultUnlocked = false
    private var vaultPin = ""

    companion object {
        private const val VAULT_DIR = "secure_vault"
        private const val VAULT_EXT = ".vault"
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 16
        private const val REQUEST_IMPORT_FILE = 7771
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> SECURE VAULT v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // PIN setup
        root.addView(TextView(context).apply {
            text = "Vault PIN:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })

        etPin = EditText(context).apply {
            hint = "Enter PIN"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(8, 8, 8, 8)
        }
        root.addView(etPin)

        etConfirmPin = EditText(context).apply {
            hint = "Confirm PIN (for set)"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(8, 8, 8, 8)
        }
        root.addView(etConfirmPin)

        // Import path
        root.addView(TextView(context).apply {
            text = "File path to import:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 0)
        })

        etImportPath = EditText(context).apply {
            hint = "/sdcard/file.txt"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setPadding(8, 8, 8, 8)
        }
        root.addView(etImportPath)

        // Buttons row 1
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Set PIN") { setVaultPin() })
        btnRow1.addView(makeBtn("Unlock") { unlockVault() })
        btnRow1.addView(makeBtn("Lock") { lockVault() })
        root.addView(btnRow1)

        // Buttons row 2
        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Import File") { importFile() })
        btnRow2.addView(makeBtn("Pick File") { pickFile() })
        btnRow2.addView(makeBtn("List Files") { listVaultFiles() })
        root.addView(btnRow2)

        // Buttons row 3
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Decrypt") { decryptFile() })
        btnRow3.addView(makeBtn("Delete File") { deleteVaultFile() })
        btnRow3.addView(makeBtn("Delete All", Color.RED) { deleteAllFiles() })
        root.addView(btnRow3)

        // Buttons row 4
        val btnRow4 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow4.addView(makeBtn("Vault Stats") { showVaultInfo() })
        root.addView(btnRow4)

        // Status
        root.addView(TextView(context).apply {
            text = "[i] AES-256-CBC encryption with PBKDF2 key derivation"
            setTextColor(0xFF005500.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        })

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

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║    SECURE VAULT v1.1            ║\n")
        appendOutput("║  AES-256 encrypted file storage ║\n")
        appendOutput("║  PBKDF2 key derivation (10k)    ║\n")
        appendOutput("║  Import via path or file picker ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeBtn(label: String, color: Int = 0xFF00FF00.toInt(), onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(color)
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

    private fun getVaultDir(): File {
        val dir = File(requireContext().filesDir, VAULT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray, pin: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

        val encrypted = cipher.doFinal(data)

        // Format: salt(16) + iv(16) + encrypted_data
        val result = ByteArray(salt.size + iv.size + encrypted.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(encrypted, 0, result, salt.size + iv.size, encrypted.size)

        return result
    }

    private fun decrypt(data: ByteArray, pin: String): ByteArray {
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val encrypted = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)

        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        return cipher.doFinal(encrypted)
    }

    private fun setVaultPin() {
        try {
            val pin = etPin.text.toString().trim()
            val confirm = etConfirmPin.text.toString().trim()

            if (pin.length < 4) {
                appendOutput("[!] PIN must be at least 4 digits\n")
                return
            }

            if (pin != confirm) {
                appendOutput("[!] PINs do not match\n")
                return
            }

            val prefs = requireContext().getSharedPreferences("secure_vault", Context.MODE_PRIVATE)
            val hash = hashPin(pin)
            prefs.edit().putString("pin_hash", hash).apply()
            vaultPin = pin

            appendOutput("[+] Vault PIN set successfully\n")
            appendOutput("[+] Hash: ${hash.take(16)}...\n")
            appendOutput("[+] You can now import files to vault\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Set PIN: ${e.message}\n")
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salt = "hackerlauncher_vault_salt".toByteArray()
        var hash = pin.toByteArray()
        for (i in 1..1000) {
            digest.reset()
            digest.update(salt)
            hash = digest.digest(hash)
        }
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun unlockVault() {
        try {
            val pin = etPin.text.toString().trim()
            if (pin.isEmpty()) {
                appendOutput("[!] Enter PIN to unlock\n")
                return
            }

            val prefs = requireContext().getSharedPreferences("secure_vault", Context.MODE_PRIVATE)
            val storedHash = prefs.getString("pin_hash", null)

            if (storedHash == null) {
                appendOutput("[!] No PIN set. Use 'Set PIN' first.\n")
                return
            }

            val hash = hashPin(pin)
            if (hash == storedHash) {
                vaultPin = pin
                isVaultUnlocked = true
                appendOutput("[+] Vault UNLOCKED\n")
                appendOutput("[+] You can now access vault files\n\n")
            } else {
                appendOutput("[!] Incorrect PIN\n\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] Unlock: ${e.message}\n")
        }
    }

    private fun lockVault() {
        vaultPin = ""
        isVaultUnlocked = false
        appendOutput("[*] Vault LOCKED\n\n")
    }

    private fun importFile() {
        if (!ensureUnlocked()) return

        try {
            val path = etImportPath.text.toString().trim()
            if (path.isEmpty()) {
                appendOutput("[!] Enter file path or use 'Pick File'\n")
                return
            }

            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val sourceFile = File(path)
                        if (!sourceFile.exists()) {
                            return@withContext "[!] Source file not found: $path"
                        }

                        val data = FileInputStream(sourceFile).use { it.readBytes() }
                        val encrypted = encrypt(data, vaultPin)
                        val vaultFile = File(getVaultDir(), sourceFile.name + VAULT_EXT)
                        FileOutputStream(vaultFile).use { it.write(encrypted) }

                        "[+] Imported: ${sourceFile.name}\n" +
                        "  Original: ${data.size} bytes\n" +
                        "  Encrypted: ${encrypted.size} bytes\n" +
                        "  Vault path: ${vaultFile.absolutePath}"
                    }
                    appendOutput("$result\n\n")
                } catch (e: Exception) {
                    appendOutput("[E] Import: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] Import: ${e.message}\n")
        }
    }

    private fun pickFile() {
        if (!ensureUnlocked()) return

        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent, REQUEST_IMPORT_FILE)
        } catch (e: Exception) {
            appendOutput("[E] File picker: ${e.message}\n")
            appendOutput("[*] Use manual path import instead\n")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                importFileFromUri(uri)
            }
        }
    }

    private fun importFileFromUri(uri: android.net.Uri) {
        if (!ensureUnlocked()) return

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        return@withContext "[!] Could not open file from URI"
                    }

                    val data = inputStream.use { it.readBytes() }
                    val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "imported_${System.currentTimeMillis()}"
                    val encrypted = encrypt(data, vaultPin)
                    val vaultFile = File(getVaultDir(), fileName + VAULT_EXT)
                    FileOutputStream(vaultFile).use { it.write(encrypted) }

                    "[+] Imported from picker: $fileName\n" +
                    "  Original: ${data.size} bytes\n" +
                    "  Encrypted: ${encrypted.size} bytes\n" +
                    "  Vault path: ${vaultFile.absolutePath}"
                }
                appendOutput("$result\n\n")
            } catch (e: Exception) {
                appendOutput("[E] Import URI: ${e.message}\n")
            }
        }
    }

    private fun listVaultFiles() {
        try {
            val dir = getVaultDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(VAULT_EXT) }?.sortedByDescending { it.lastModified() }

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Vault Files                 ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            if (files.isNullOrEmpty()) {
                appendOutput("  Vault is empty\n")
                appendOutput("  Import files to get started\n")
            } else {
                appendOutput("  Found ${files.size} file(s):\n\n")
                for ((idx, file) in files.withIndex()) {
                    val name = file.name.removeSuffix(VAULT_EXT)
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified()))
                    val size = file.length()
                    val originalSize = size - SALT_LENGTH - IV_LENGTH
                    appendOutput("  ${idx + 1}. $name\n")
                    appendOutput("     Encrypted: ${size / 1024}KB | ~Original: ${originalSize / 1024}KB\n")
                    appendOutput("     Date: $date\n\n")
                }
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] List: ${e.message}\n")
        }
    }

    private fun decryptFile() {
        if (!ensureUnlocked()) return

        try {
            val dir = getVaultDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(VAULT_EXT) }?.sortedByDescending { it.lastModified() }

            if (files.isNullOrEmpty()) {
                appendOutput("[!] No vault files to decrypt\n")
                return
            }

            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val sb = StringBuilder()
                        for (file in files) {
                            try {
                                val encrypted = FileInputStream(file).use { it.readBytes() }
                                val decrypted = decrypt(encrypted, vaultPin)
                                val name = file.name.removeSuffix(VAULT_EXT)

                                val outDir = File(requireContext().getExternalFilesDir(null), "vault_output")
                                if (!outDir.exists()) outDir.mkdirs()
                                val outFile = File(outDir, name)
                                FileOutputStream(outFile).use { it.write(decrypted) }

                                sb.append("[+] Decrypted: $name\n")
                                sb.append("  Size: ${decrypted.size} bytes\n")
                                sb.append("  Output: ${outFile.absolutePath}\n\n")
                            } catch (e: javax.crypto.BadPaddingException) {
                                sb.append("[!] Wrong PIN for: ${file.name}\n\n")
                            } catch (e: Exception) {
                                sb.append("[E] ${file.name}: ${e.message}\n\n")
                            }
                        }
                        sb.toString()
                    }
                    appendOutput(result)
                } catch (e: Exception) {
                    appendOutput("[E] Decrypt: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] Decrypt: ${e.message}\n")
        }
    }

    private fun deleteVaultFile() {
        try {
            val dir = getVaultDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(VAULT_EXT) }?.sortedByDescending { it.lastModified() }

            if (files.isNullOrEmpty()) {
                appendOutput("[!] No vault files\n")
                return
            }

            appendOutput("[*] Vault files:\n")
            for ((idx, file) in files.withIndex()) {
                appendOutput("  ${idx + 1}. ${file.name.removeSuffix(VAULT_EXT)}\n")
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Delete Vault File")
                .setMessage("Delete all vault files? This cannot be undone!")
                .setPositiveButton("Delete All") { _, _ ->
                    var deleted = 0
                    files.forEach { if (it.delete()) deleted++ }
                    appendOutput("[+] Deleted $deleted vault files\n\n")
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            appendOutput("[E] Delete: ${e.message}\n")
        }
    }

    private fun deleteAllFiles() {
        try {
            AlertDialog.Builder(requireContext())
                .setTitle("!! DELETE ALL VAULT FILES !!")
                .setMessage("This will permanently delete all encrypted vault files. Cannot be undone!")
                .setPositiveButton("DELETE ALL") { _, _ ->
                    val dir = getVaultDir()
                    val files = dir.listFiles()
                    var deleted = 0
                    files?.forEach { if (it.delete()) deleted++ }
                    appendOutput("[+] Deleted $deleted vault files\n")
                    appendOutput("[+] Vault is now empty\n\n")
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun showVaultInfo() {
        try {
            val dir = getVaultDir()
            val files = dir.listFiles()?.filter { it.name.endsWith(VAULT_EXT) }
            val totalSize = files?.sumOf { it.length() } ?: 0L

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Vault Statistics            ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("  Status: ${if (isVaultUnlocked) "UNLOCKED" else "LOCKED"}\n")
            appendOutput("  Files: ${files?.size ?: 0}\n")
            appendOutput("  Total size: ${totalSize / 1024}KB\n")
            appendOutput("  Location: ${dir.absolutePath}\n")
            appendOutput("  Encryption: AES-256-CBC\n")
            appendOutput("  Key derivation: PBKDF2-HMAC-SHA256\n")
            appendOutput("  Iterations: $PBKDF2_ITERATIONS\n")
            appendOutput("  Salt length: ${SALT_LENGTH} bytes\n")
            appendOutput("  IV length: ${IV_LENGTH} bytes\n")
            appendOutput("  File extension: $VAULT_EXT\n")

            val prefs = requireContext().getSharedPreferences("secure_vault", Context.MODE_PRIVATE)
            appendOutput("  PIN set: ${prefs.contains("pin_hash")}\n")

            // Disk usage
            val freeSpace = dir.freeSpace / (1024 * 1024)
            val totalSpace = dir.totalSpace / (1024 * 1024)
            appendOutput("  Disk free: ${freeSpace}MB / ${totalSpace}MB\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun ensureUnlocked(): Boolean {
        if (!isVaultUnlocked || vaultPin.isEmpty()) {
            appendOutput("[!] Vault is LOCKED. Enter PIN and press Unlock.\n\n")
            return false
        }
        return true
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vaultPin = ""
        isVaultUnlocked = false
        scope.cancel()
    }
}
