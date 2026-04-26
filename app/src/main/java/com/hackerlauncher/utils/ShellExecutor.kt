package com.hackerlauncher.utils

data class ShellResult(val output: String, val error: String, val exitCode: Int)

class ShellExecutor {

    companion object {
        fun execute(command: String): ShellResult {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                ShellResult(output.trim(), error.trim(), exitCode)
            } catch (e: Exception) {
                ShellResult("", e.message ?: "Execution failed", -1)
            }
        }

        fun executeWithTimeout(command: String, timeoutMs: Long = 30000): ShellResult {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val completed = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    ShellResult("", "Command timed out after ${timeoutMs}ms", -1)
                } else {
                    val output = process.inputStream.bufferedReader().readText()
                    val error = process.errorStream.bufferedReader().readText()
                    val exitCode = process.exitValue()
                    ShellResult(output.trim(), error.trim(), exitCode)
                }
            } catch (e: Exception) {
                ShellResult("", e.message ?: "Execution failed", -1)
            }
        }

        fun executeRoot(command: String): ShellResult {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                ShellResult(output.trim(), error.trim(), exitCode)
            } catch (e: Exception) {
                ShellResult("", "Root not available: ${e.message}", -1)
            }
        }

        fun isRootAvailable(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                exitCode == 0 && output.contains("uid=0")
            } catch (e: Exception) {
                false
            }
        }
    }
}
