package com.hackerlauncher.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ShellResult(val output: String, val error: String, val exitCode: Int)

class ShellExecutor {

    companion object {
        fun execute(command: String): ShellResult {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                // FIX: Read both streams concurrently to prevent deadlock
                val outputCaptureThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                outputBuilder.append(line).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }

                val errorCaptureThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                errorBuilder.append(line).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }

                outputCaptureThread.start()
                errorCaptureThread.start()

                val exitCode = process.waitFor()

                outputCaptureThread.join(5000)
                errorCaptureThread.join(5000)

                process.destroy()

                ShellResult(outputBuilder.toString().trim(), errorBuilder.toString().trim(), exitCode)
            } catch (e: Exception) {
                ShellResult("", e.message ?: "Execution failed", -1)
            }
        }

        fun executeWithTimeout(command: String, timeoutMs: Long = 30000): ShellResult {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                val outputCaptureThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                outputBuilder.append(line).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }

                val errorCaptureThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                errorBuilder.append(line).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }

                outputCaptureThread.start()
                errorCaptureThread.start()

                val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    outputCaptureThread.interrupt()
                    errorCaptureThread.interrupt()
                    ShellResult("", "Command timed out after ${timeoutMs}ms", -1)
                } else {
                    outputCaptureThread.join(5000)
                    errorCaptureThread.join(5000)
                    val exitCode = process.exitValue()
                    ShellResult(outputBuilder.toString().trim(), errorBuilder.toString().trim(), exitCode)
                }
            } catch (e: Exception) {
                ShellResult("", e.message ?: "Execution failed", -1)
            }
        }

        fun executeRoot(command: String): ShellResult {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                val outputCaptureThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                outputBuilder.append(line).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }

                val errorCaptureThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                errorBuilder.append(line).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }

                outputCaptureThread.start()
                errorCaptureThread.start()

                val exitCode = process.waitFor()

                outputCaptureThread.join(5000)
                errorCaptureThread.join(5000)

                process.destroy()

                ShellResult(outputBuilder.toString().trim(), errorBuilder.toString().trim(), exitCode)
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
