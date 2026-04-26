package com.hackerlauncher.modules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CryptoFragment : Fragment() {

    private lateinit var tvCryptoOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_crypto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCryptoOutput = view.findViewById(R.id.tvCryptoOutput)
        scrollView = view.findViewById(R.id.scrollViewCrypto)
        val etInput = view.findViewById<EditText>(R.id.etCryptoInput)
        val spinnerOp = view.findViewById<Spinner>(R.id.spinnerCryptoOp)
        val btnGo = view.findViewById<Button>(R.id.btnCryptoGo)
        val btnCopy = view.findViewById<Button>(R.id.btnCryptoCopy)

        val ops = listOf(
            "MD5 Hash", "SHA-1 Hash", "SHA-256 Hash", "SHA-512 Hash",
            "Base64 Encode", "Base64 Decode", "XOR Encrypt", "XOR Decrypt",
            "Caesar Encrypt", "Caesar Decrypt", "Password Strength", "Random Password"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ops)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOp.adapter = adapter

        btnGo.setOnClickListener {
            val input = etInput.text.toString()
            val op = spinnerOp.selectedItemPosition
            processCrypto(input, op)
        }

        btnCopy.setOnClickListener {
            val text = tvCryptoOutput.text.toString()
            val clip = android.content.ClipData.newPlainText("crypto_result", text)
            (requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processCrypto(input: String, op: Int) {
        val result = when (op) {
            0 -> hash(input, "MD5")
            1 -> hash(input, "SHA-1")
            2 -> hash(input, "SHA-256")
            3 -> hash(input, "SHA-512")
            4 -> base64Encode(input)
            5 -> base64Decode(input)
            6 -> xorEncrypt(input)
            7 -> xorDecrypt(input)
            8 -> caesarEncrypt(input)
            9 -> caesarDecrypt(input)
            10 -> checkPasswordStrength(input)
            11 -> generateRandomPassword()
            else -> "Unknown operation"
        }
        tvCryptoOutput.text = result
    }

    private fun hash(input: String, algorithm: String): String {
        if (input.isEmpty()) return "[!] Enter input text"
        return try {
            val md = MessageDigest.getInstance(algorithm)
            val bytes = md.digest(input.toByteArray())
            val hex = bytes.joinToString("") { "%02x".format(it) }
            "═══ $algorithm Hash ═══\nInput: $input\nHash:  $hex\n══════════════════════"
        } catch (e: Exception) {
            "[E] Hash error: ${e.message}"
        }
    }

    private fun base64Encode(input: String): String {
        if (input.isEmpty()) return "[!] Enter input text"
        return try {
            val encoded = Base64.getEncoder().encodeToString(input.toByteArray())
            "═══ Base64 Encode ═══\nInput:   $input\nEncoded: $encoded\n══════════════════════"
        } catch (e: Exception) {
            "[E] Encode error: ${e.message}"
        }
    }

    private fun base64Decode(input: String): String {
        if (input.isEmpty()) return "[!] Enter base64 string"
        return try {
            val decoded = String(Base64.getDecoder().decode(input))
            "═══ Base64 Decode ═══\nInput:   $input\nDecoded: $decoded\n══════════════════════"
        } catch (e: Exception) {
            "[E] Decode error: Invalid base64 string"
        }
    }

    private fun xorEncrypt(input: String): String {
        if (input.isEmpty()) return "[!] Enter input text"
        val key = "HACKER" // Default XOR key
        val encrypted = input.mapIndexed { i, c ->
            (c.code xor key[i % key.length].code).toString(16).padStart(2, '0')
        }.joinToString(" ")
        return "═══ XOR Encrypt ═══\nInput:     $input\nKey:       $key\nEncrypted: $encrypted\n══════════════════════"
    }

    private fun xorDecrypt(input: String): String {
        if (input.isEmpty()) return "[!] Enter hex-encoded XOR data (space separated)"
        val key = "HACKER"
        return try {
            val bytes = input.split(" ").map { it.toInt(16) }
            val decrypted = bytes.mapIndexed { i, b ->
                (b xor key[i % key.length].code).toChar()
            }.joinToString("")
            "═══ XOR Decrypt ═══\nInput:     $input\nKey:       $key\nDecrypted: $decrypted\n══════════════════════"
        } catch (e: Exception) {
            "[E] Decrypt error: Invalid hex format"
        }
    }

    private fun caesarEncrypt(input: String): String {
        if (input.isEmpty()) return "[!] Enter input text"
        val shift = 3 // Default shift
        val encrypted = input.map { c ->
            when {
                c.isUpperCase() -> ((c.code - 'A'.code + shift) % 26 + 'A'.code).toChar()
                c.isLowerCase() -> ((c.code - 'a'.code + shift) % 26 + 'a'.code).toChar()
                else -> c
            }
        }.joinToString("")
        return "═══ Caesar Encrypt ═══\nInput:     $input\nShift:     $shift\nEncrypted: $encrypted\n══════════════════════"
    }

    private fun caesarDecrypt(input: String): String {
        if (input.isEmpty()) return "[!] Enter ciphertext"
        val shift = 3
        val decrypted = input.map { c ->
            when {
                c.isUpperCase() -> ((c.code - 'A'.code - shift + 26) % 26 + 'A'.code).toChar()
                c.isLowerCase() -> ((c.code - 'a'.code - shift + 26) % 26 + 'a'.code).toChar()
                else -> c
            }
        }.joinToString("")
        return "═══ Caesar Decrypt ═══\nInput:     $input\nShift:     $shift\nDecrypted: $decrypted\n══════════════════════"
    }

    private fun checkPasswordStrength(password: String): String {
        if (password.isEmpty()) return "[!] Enter a password to check"
        var score = 0
        val checks = mutableListOf<String>()

        if (password.length >= 8) { score++; checks.add("[+] Length >= 8") } 
        else checks.add("[-] Too short (<8)")
        if (password.length >= 12) { score++; checks.add("[+] Length >= 12") }
        if (password.any { it.isUpperCase() }) { score++; checks.add("[+] Has uppercase") }
        else checks.add("[-] No uppercase")
        if (password.any { it.isLowerCase() }) { score++; checks.add("[+] Has lowercase") }
        else checks.add("[-] No lowercase")
        if (password.any { it.isDigit() }) { score++; checks.add("[+] Has digits") }
        else checks.add("[-] No digits")
        if (password.any { !it.isLetterOrDigit() }) { score++; checks.add("[+] Has special chars") }
        else checks.add("[-] No special chars")
        if (password.length >= 16) { score++; checks.add("[+] Length >= 16") }

        val strength = when {
            score <= 2 -> "WEAK"
            score <= 4 -> "MEDIUM"
            score <= 5 -> "STRONG"
            else -> "VERY STRONG"
        }
        val emoji = when {
            score <= 2 -> "[-]"; score <= 4 -> "[~]"; else -> "[+]"
        }

        return "═══ Password Strength ═══\nPassword: ${"*".repeat(password.length)}\nScore:    $score/7\nStrength: $emoji $strength\n\nChecks:\n${checks.joinToString("\n")}\n══════════════════════"
    }

    private fun generateRandomPassword(): String {
        val length = 16
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?"
        val random = SecureRandom()
        val password = (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        return "═══ Random Password ═══\nLength:   $length\nPassword: $password\n══════════════════════"
    }
}
