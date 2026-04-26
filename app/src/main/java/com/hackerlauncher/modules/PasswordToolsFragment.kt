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
import kotlin.math.pow

class PasswordToolsFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_crypto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOutput = view.findViewById(R.id.tvCryptoOutput)
        scrollView = view.findViewById(R.id.scrollViewCrypto)

        val etInput = view.findViewById<EditText>(R.id.etCryptoInput)
        val spinnerOp = view.findViewById<Spinner>(R.id.spinnerCryptoOp)
        val btnGo = view.findViewById<Button>(R.id.btnCryptoGo)
        val btnCopy = view.findViewById<Button>(R.id.btnCryptoCopy)

        val ops = listOf(
            "Password Strength", "Random Password", "Passphrase",
            "Wordlist Gen", "Hash Crack Check", "Password Mutations",
            "PIN Generator", "WPA Key Gen", "Hash All", "Entropy Calc"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ops)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOp.adapter = adapter

        btnGo.setOnClickListener {
            val input = etInput.text.toString()
            val op = spinnerOp.selectedItemPosition
            processPasswordTool(input, op)
        }

        btnCopy.setOnClickListener {
            val text = tvOutput.text.toString()
            val clip = android.content.ClipData.newPlainText("password_result", text)
            (requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processPasswordTool(input: String, op: Int) {
        val result = when (op) {
            0 -> checkPasswordStrength(input)
            1 -> generateRandomPassword(if (input.isNotEmpty()) input.toIntOrNull() ?: 16 else 16)
            2 -> generatePassphrase()
            3 -> generateWordlist(input)
            4 -> hashCrackCheck(input)
            5 -> generateMutations(input)
            6 -> generatePIN()
            7 -> generateWPAKey()
            8 -> hashAll(input)
            9 -> calculateEntropy(input)
            else -> "Unknown operation"
        }
        tvOutput.text = result
    }

    private fun checkPasswordStrength(password: String): String {
        if (password.isEmpty()) return "[!] Enter a password to check"
        var score = 0
        val checks = mutableListOf<String>()

        if (password.length >= 8) { score++; checks.add("[+] Length >= 8") }
        else checks.add("[-] Too short (<8)")
        if (password.length >= 12) { score++; checks.add("[+] Length >= 12") }
        if (password.length >= 16) { score++; checks.add("[+] Length >= 16") }
        if (password.any { it.isUpperCase() }) { score++; checks.add("[+] Has uppercase") }
        else checks.add("[-] No uppercase")
        if (password.any { it.isLowerCase() }) { score++; checks.add("[+] Has lowercase") }
        else checks.add("[-] No lowercase")
        if (password.any { it.isDigit() }) { score++; checks.add("[+] Has digits") }
        else checks.add("[-] No digits")
        if (password.any { !it.isLetterOrDigit() }) { score++; checks.add("[+] Has special chars") }
        else checks.add("[-] No special chars")
        if (!password.contains(Regex("(.)\\1{2,}"))) { score++; checks.add("[+] No repeating chars") }
        else checks.add("[-] Has repeating chars")
        if (!password.lowercase().let { p -> listOf("password", "123456", "qwerty", "admin", "letmein").any { p.contains(it) } }) {
            score++; checks.add("[+] No common patterns")
        } else checks.add("[-] Contains common pattern!")

        val strength = when {
            score <= 2 -> "VERY WEAK"
            score <= 4 -> "WEAK"
            score <= 6 -> "MEDIUM"
            score <= 7 -> "STRONG"
            else -> "VERY STRONG"
        }
        val crackTime = estimateCrackTime(password)

        return "═══ Password Strength Analysis ═══\n" +
                "Password: ${"*".repeat(password.length)}\n" +
                "Length:   ${password.length}\n" +
                "Score:    $score/9\n" +
                "Strength: $strength\n" +
                "Crack Time: $crackTime\n\n" +
                "Checks:\n${checks.joinToString("\n")}\n" +
                "════════════════════════════════"
    }

    private fun estimateCrackTime(password: String): String {
        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32
        if (poolSize == 0) return "Instant"

        val combinations = poolSize.toDouble().pow(password.length)
        val guessesPerSec = 10_000_000_000.0 // 10 billion/sec (modern GPU)
        val seconds = combinations / guessesPerSec

        return when {
            seconds < 1 -> "Instant"
            seconds < 60 -> "${seconds.toLong()} seconds"
            seconds < 3600 -> "${(seconds / 60).toLong()} minutes"
            seconds < 86400 -> "${(seconds / 3600).toLong()} hours"
            seconds < 31536000 -> "${(seconds / 86400).toLong()} days"
            seconds < 31536000 * 100 -> "${(seconds / 31536000).toLong()} years"
            seconds < 31536000 * 1_000_000 -> "${(seconds / 31536000).toLong()} years"
            else -> "Centuries+"
        }
    }

    private fun generateRandomPassword(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?"
        val random = SecureRandom()
        val password = (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        return "═══ Random Password ═══\nLength:   $length\nPassword: $password\n════════════════════════════════"
    }

    private fun generatePassphrase(): String {
        val words = listOf(
            "alpha", "bravo", "cipher", "delta", "echo", "falcon", "ghost", "hawk",
            "ice", "jade", "knight", "lambda", "matrix", "nexus", "omega", "prism",
            "quantum", "raven", "shadow", "titan", "ultra", "vector", "wolf", "xenon",
            "yield", "zero", "phoenix", "dragon", "storm", "crystal", "neon", "cyber",
            "pixel", "binary", "kernel", "daemon", "root", "shell", "token", "vault"
        )
        val random = SecureRandom()
        val count = 4 + random.nextInt(3) // 4-6 words
        val passphrase = (1..count).map { words[random.nextInt(words.size)] }.joinToString("-")
        val num = random.nextInt(100)
        return "═══ Passphrase ═══\nWords:    $count\nPassphrase: $passphrase$num\n════════════════════════════════"
    }

    private fun generateWordlist(input: String): String {
        if (input.isEmpty()) return "[!] Enter a base word for wordlist generation"
        val base = input.lowercase()
        val mutations = mutableSetOf<String>()

        // Basic mutations
        mutations.add(base)
        mutations.add(base.uppercase())
        mutations.add(base.capitalize())
        mutations.add("${base}123")
        mutations.add("${base}!")
        mutations.add("${base}2024")
        mutations.add("${base}2025")
        mutations.add("${base}2026")
        mutations.add("${base}@123")
        mutations.add("${base}#1")
        mutations.add("${base}\$")
        mutations.add("${base}!!")
        mutations.add("${base}1234")
        mutations.add("${base}12345")
        mutations.add("${base}123456")
        mutations.add("${base}_${base}")
        mutations.add(base.reversed())
        mutations.add("${base.capitalize()}${base.length}")
        mutations.add(base.replace('a', '@').replace('e', '3').replace('i', '1').replace('o', '0').replace('s', '$'))
        mutations.add(base.replace('a', '4').replace('e', '3').replace('i', '!').replace('o', '0').replace('s', '5'))

        // L33t speak
        val leet = base.map { c ->
            when (c.lowercaseChar()) {
                'a' -> "@"; 'e' -> "3"; 'i' -> "1"; 'o' -> "0"; 's' -> "$"; 't' -> "7"; 'l' -> "1"; 'g' -> "9"
                else -> c.toString()
            }
        }.joinToString("")
        mutations.add(leet)
        mutations.add("${leet}123")

        val sb = StringBuilder("═══ Wordlist Generation ═══\n")
        sb.append("Base: $base\n")
        sb.append("Mutations: ${mutations.size}\n\n")
        mutations.forEach { sb.append("  $it\n") }
        sb.append("\n════════════════════════════════")
        return sb.toString()
    }

    private fun hashCrackCheck(input: String): String {
        if (input.isEmpty()) return "[!] Enter a hash to check"
        val sb = StringBuilder("═══ Hash Analysis ═══\n")
        sb.append("Input: $input\n")
        sb.append("Length: ${input.length}\n\n")

        val hashType = when (input.length) {
            32 -> "MD5"
            40 -> "SHA-1"
            64 -> "SHA-256"
            128 -> "SHA-512"
            else -> "Unknown"
        }
        sb.append("Likely hash type: $hashType\n\n")
        sb.append("Online crack services:\n")
        sb.append("  - crackstation.net\n")
        sb.append("  - hashes.com\n")
        sb.append("  - md5decrypt.net\n")
        sb.append("  - hashkiller.co.uk\n\n")
        sb.append("[!] Rainbow table attack info:\n")
        when (hashType) {
            "MD5" -> sb.append("  MD5 is weak - easily cracked\n")
            "SHA-1" -> sb.append("  SHA-1 is deprecated - crackable\n")
            "SHA-256" -> sb.append("  SHA-256 is strong - very difficult\n")
            "SHA-512" -> sb.append("  SHA-512 is very strong\n")
            else -> sb.append("  Cannot determine hash strength\n")
        }
        sb.append("════════════════════════════════")
        return sb.toString()
    }

    private fun generateMutations(input: String): String {
        if (input.isEmpty()) return "[!] Enter a base word"
        val mutations = mutableListOf<String>()
        val base = input.trim()
        val suffixes = listOf("", "1", "12", "123", "1234", "!", "@", "#", "$", "2024", "2025", "2026", "!!", "@!", "01", "007")
        val prefixes = listOf("", "!", "@", "#", "my", "the")

        for (prefix in prefixes) {
            for (suffix in suffixes) {
                for (case in listOf(base, base.uppercase(), base.capitalize())) {
                    mutations.add("$prefix$case$suffix")
                }
            }
        }
        // Add reversed
        mutations.add(base.reversed())
        mutations.add(base.reversed().uppercase())
        mutations.add(base.reversed() + "123")

        val sb = StringBuilder("═══ Password Mutations ═══\n")
        sb.append("Base: $base\n")
        sb.append("Total mutations: ${mutations.size}\n\n")
        mutations.take(50).forEach { sb.append("  $it\n") }
        if (mutations.size > 50) sb.append("  ... and ${mutations.size - 50} more\n")
        sb.append("\n════════════════════════════════")
        return sb.toString()
    }

    private fun generatePIN(): String {
        val random = SecureRandom()
        val pins = listOf(4, 6, 8).map { len ->
            (1..len).map { random.nextInt(10) }.joinToString("")
        }
        return "═══ PIN Generator ═══\n" +
                "4-digit: ${pins[0]}\n" +
                "6-digit: ${pins[1]}\n" +
                "8-digit: ${pins[2]}\n" +
                "════════════════════════════════"
    }

    private fun generateWPAKey(): String {
        val random = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val key64 = (1..63).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val hexKey = (1..64).map { "0123456789abcdef"[random.nextInt(16)].toString() }.joinToString("")
        return "═══ WPA Key Generator ═══\n" +
                "ASCII Key (63 chars): $key64\n" +
                "HEX Key  (64 chars): $hexKey\n" +
                "════════════════════════════════"
    }

    private fun hashAll(input: String): String {
        if (input.isEmpty()) return "[!] Enter input text"
        val algorithms = listOf("MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512")
        val sb = StringBuilder("═══ Hash All Algorithms ═══\n")
        sb.append("Input: $input\n\n")
        for (algo in algorithms) {
            try {
                val md = MessageDigest.getInstance(algo)
                val hex = md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
                sb.append("$algo:\n  $hex\n\n")
            } catch (e: Exception) {
                sb.append("$algo: Error - ${e.message}\n")
            }
        }
        sb.append("════════════════════════════════")
        return sb.toString()
    }

    private fun calculateEntropy(input: String): String {
        if (input.isEmpty()) return "[!] Enter input to calculate entropy"
        var poolSize = 0
        if (input.any { it.isLowerCase() }) poolSize += 26
        if (input.any { it.isUpperCase() }) poolSize += 26
        if (input.any { it.isDigit() }) poolSize += 10
        if (input.any { !it.isLetterOrDigit() }) poolSize += 32
        if (poolSize == 0) poolSize = 1

        val entropy = (input.length * (Math.log(poolSize.toDouble()) / Math.log(2.0))).toInt()

        val rating = when {
            entropy < 28 -> "VERY WEAK"
            entropy < 36 -> "WEAK"
            entropy < 60 -> "REASONABLE"
            entropy < 80 -> "STRONG"
            entropy < 100 -> "VERY STRONG"
            else -> "EXTREMELY STRONG"
        }

        return "═══ Entropy Calculator ═══\n" +
                "Input:    ${"*".repeat(input.length)}\n" +
                "Length:   ${input.length}\n" +
                "Pool:     $poolSize characters\n" +
                "Entropy:  $entropy bits\n" +
                "Rating:   $rating\n" +
                "════════════════════════════════"
    }
}
