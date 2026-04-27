package com.hackerlauncher.modules

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

class FakeLocationFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var etProviderName: EditText
    private var isSpoofing = false

    // Favorite locations
    private val favorites = listOf(
        Triple("Times Square NYC", 40.7580, -73.9855),
        Triple("Eiffel Tower Paris", 48.8584, 2.2945),
        Triple("Big Ben London", 51.5007, -0.1246),
        Triple("Tokyo Tower", 35.6586, 139.7454),
        Triple("Sydney Opera House", -33.8568, 151.2153),
        Triple("Colosseum Rome", 41.8902, 12.4922),
        Triple("Statue of Liberty", 40.6892, -74.0445),
        Triple("Great Wall Beijing", 40.4319, 116.5704),
        Triple("Machu Picchu", -13.1631, -72.5450),
        Triple("Pyramids of Giza", 29.9792, 31.1342)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> FAKE LOCATION v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Developer options note
        root.addView(TextView(context).apply {
            text = "[!] Enable 'Select mock location app' in Developer Options"
            setTextColor(0xFFFFFF00.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 2, 0, 4)
        })

        // Latitude
        root.addView(TextView(context).apply {
            text = "Latitude:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etLatitude = EditText(context).apply {
            hint = "40.7128"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setPadding(8, 8, 8, 8)
        }
        root.addView(etLatitude)

        // Longitude
        root.addView(TextView(context).apply {
            text = "Longitude:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etLongitude = EditText(context).apply {
            hint = "-74.0060"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setPadding(8, 8, 8, 8)
        }
        root.addView(etLongitude)

        // Provider name
        root.addView(TextView(context).apply {
            text = "Mock Provider Name:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etProviderName = EditText(context).apply {
            hint = "FakeGPS"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
            setText("FakeGPS")
        }
        root.addView(etProviderName)

        // Buttons row 1
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Dev Options") { openDevOptions() })
        btnRow1.addView(makeBtn("Start Spoof") { startSpoofing() })
        btnRow1.addView(makeBtn("Stop") { stopSpoofing() })
        root.addView(btnRow1)

        // Buttons row 2
        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Random Loc") { setRandomLocation() })
        btnRow2.addView(makeBtn("Map Link") { openMapLink() })
        btnRow2.addView(makeBtn("Favorites") { showFavorites() })
        root.addView(btnRow2)

        // Buttons row 3
        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Add Provider") { addMockProvider() })
        btnRow3.addView(makeBtn("Remove Provider") { removeMockProvider() })
        btnRow3.addView(makeBtn("Status") { showStatus() })
        root.addView(btnRow3)

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
        appendOutput("║    FAKE LOCATION v1.1           ║\n")
        appendOutput("║   GPS location spoofing         ║\n")
        appendOutput("║   Requires mock location app    ║\n")
        appendOutput("║   set in Developer Options      ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeBtn(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
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

    private fun openDevOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            startActivity(intent)
            appendOutput("[*] Opened Developer Options\n")
            appendOutput("[*] Enable 'Select mock location app'\n")
            appendOutput("[*] Set this app as mock location provider\n\n")
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
                appendOutput("[*] Opened Settings (navigate to Developer Options)\n\n")
            } catch (e2: Exception) {
                appendOutput("[E] Could not open settings: ${e2.message}\n\n")
            }
        }
    }

    private fun addMockProvider() {
        try {
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providerName = etProviderName.text.toString().trim().ifEmpty { "FakeGPS" }

            try {
                lm.removeTestProvider(providerName)
            } catch (_: Exception) {}

            try {
                lm.addTestProvider(
                    providerName,
                    false, false, false, false,
                    true, true, true, 0, 1
                )
                lm.setTestProviderEnabled(providerName, true)
                appendOutput("[+] Mock provider '$providerName' added\n")
                appendOutput("[+] Provider enabled\n\n")
            } catch (e: SecurityException) {
                appendOutput("[!] SecurityException: ${e.message}\n")
                appendOutput("[!] This app must be set as mock location provider\n")
                appendOutput("[!] Go to Developer Options > Select mock location app\n\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] Add provider: ${e.message}\n\n")
        }
    }

    private fun removeMockProvider() {
        try {
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providerName = etProviderName.text.toString().trim().ifEmpty { "FakeGPS" }

            try {
                lm.removeTestProvider(providerName)
                appendOutput("[+] Mock provider '$providerName' removed\n\n")
            } catch (e: Exception) {
                appendOutput("[E] Remove: ${e.message}\n\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun startSpoofing() {
        val latStr = etLatitude.text.toString().trim()
        val lonStr = etLongitude.text.toString().trim()

        if (latStr.isEmpty() || lonStr.isEmpty()) {
            appendOutput("[!] Enter latitude and longitude\n\n")
            return
        }

        try {
            val lat = latStr.toDouble()
            val lon = lonStr.toDouble()
            val providerName = etProviderName.text.toString().trim().ifEmpty { "FakeGPS" }
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Add provider if not exists
            try {
                lm.addTestProvider(providerName, false, false, false, false, true, true, true, 0, 1)
                lm.setTestProviderEnabled(providerName, true)
            } catch (e: IllegalArgumentException) {
                // Provider already exists
            } catch (e: SecurityException) {
                appendOutput("[!] Not set as mock location app!\n")
                appendOutput("[!] Go to Developer Options > Select mock location app\n\n")
                return
            }

            isSpoofing = true
            appendOutput("[*] Spoofing started: $lat, $lon\n")
            appendOutput("[*] Provider: $providerName\n")

            scope.launch {
                while (isSpoofing && isActive) {
                    try {
                        val location = Location(providerName).apply {
                            latitude = lat
                            longitude = lon
                            altitude = 0.0
                            accuracy = 5.0f
                            time = System.currentTimeMillis()
                            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                bearingAccuracyDegrees = 0.1f
                                verticalAccuracyMeters = 1.0f
                                speedAccuracyMetersPerSecond = 0.1f
                            }
                            speed = 0.0f
                            bearing = 0.0f
                        }
                        lm.setTestProviderLocation(providerName, location)
                    } catch (e: Exception) {
                        appendOutput("[E] Spoof: ${e.message}\n")
                        break
                    }
                    delay(1000)
                }
            }
        } catch (e: NumberFormatException) {
            appendOutput("[!] Invalid coordinates\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Start: ${e.message}\n")
        }
    }

    private fun stopSpoofing() {
        isSpoofing = false
        try {
            val providerName = etProviderName.text.toString().trim().ifEmpty { "FakeGPS" }
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                lm.removeTestProvider(providerName)
            } catch (_: Exception) {}
            appendOutput("[*] Spoofing stopped\n")
            appendOutput("[*] Mock provider removed\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Stop: ${e.message}\n")
        }
    }

    private fun setRandomLocation() {
        try {
            val random = Random()
            val lat = (random.nextDouble() * 180) - 90  // -90 to 90
            val lon = (random.nextDouble() * 360) - 180  // -180 to 180

            etLatitude.setText(String.format("%.4f", lat))
            etLongitude.setText(String.format("%.4f", lon))

            appendOutput("[+] Random location set:\n")
            appendOutput("  Lat: ${String.format("%.4f", lat)}\n")
            appendOutput("  Lon: ${String.format("%.4f", lon)}\n")
            appendOutput("  Map: https://maps.google.com/?q=${String.format("%.4f", lat)},${String.format("%.4f", lon)}\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Random: ${e.message}\n")
        }
    }

    private fun openMapLink() {
        try {
            val lat = etLatitude.text.toString().trim()
            val lon = etLongitude.text.toString().trim()
            if (lat.isEmpty() || lon.isEmpty()) {
                appendOutput("[!] Enter coordinates first\n")
                return
            }

            val url = "https://maps.google.com/?q=$lat,$lon"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
            appendOutput("[*] Opened: $url\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Map: ${e.message}\n")
        }
    }

    private fun showFavorites() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   Favorite Locations            ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")

        for ((idx, triple) in favorites.withIndex()) {
            val (name, lat, lon) = triple
            appendOutput("  ${idx + 1}. $name\n")
            appendOutput("     Lat: $lat | Lon: $lon\n\n")
        }

        appendOutput("[*] First favorite auto-loaded into fields\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        // Set first favorite as example
        etLatitude.setText(favorites[0].second.toString())
        etLongitude.setText(favorites[0].third.toString())
        appendOutput("[*] Set to: ${favorites[0].first}\n\n")
    }

    private fun showStatus() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Location Spoof Status         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            appendOutput("  Spoofing: ${if (isSpoofing) "ACTIVE" else "INACTIVE"}\n")

            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

            appendOutput("  Providers:\n")
            for (provider in lm.allProviders) {
                appendOutput("    - $provider\n")
                try {
                    val lastKnown = lm.getLastKnownLocation(provider)
                    if (lastKnown != null) {
                        appendOutput("      Last: ${lastKnown.latitude}, ${lastKnown.longitude}\n")
                        appendOutput("      Acc: ${lastKnown.accuracy}m\n")
                    }
                } catch (e: SecurityException) {
                    appendOutput("      [Permission denied]\n")
                }
            }

            // Check if mock location is enabled
            try {
                val mockEnabled = Settings.Secure.getInt(
                    requireContext().contentResolver,
                    "mock_location", 0
                )
                appendOutput("  Mock Location: ${if (mockEnabled == 1) "Enabled" else "Disabled (API 22-)"}\n")
            } catch (_: Exception) {
                appendOutput("  Mock Location: Check Developer Options\n")
            }

            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Status: ${e.message}\n")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isSpoofing = false
        scope.cancel()
    }
}
