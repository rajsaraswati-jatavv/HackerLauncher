package com.hackerlauncher.modules

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SearchHistoryEntry(
    val imageUrl: String,
    val timestamp: Long,
    val engines: List<String>
)

class ReverseImageSearchFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var prefs: SharedPreferences
    private val searchHistory = mutableListOf<SearchHistoryEntry>()

    private lateinit var mainLayout: LinearLayout
    private lateinit var urlEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var historyContainer: LinearLayout

    private val searchEngines = mapOf(
        "Google Images" to { url: String ->
            "https://images.google.com/searchbyimage?image_url=${URLEncoder.encode(url, "UTF-8")}"
        },
        "TinEye" to { url: String ->
            "https://tineye.com/search/?url=${URLEncoder.encode(url, "UTF-8")}"
        },
        "Yandex" to { url: String ->
            "https://yandex.com/images/search?rpt=imageview&url=${URLEncoder.encode(url, "UTF-8")}"
        },
        "Bing Visual" to { url: String ->
            "https://www.bing.com/images/search?q=imgurl:${URLEncoder.encode(url, "UTF-8")}&view=detailv2&iss=sbi"
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireContext().getSharedPreferences("reverse_image_search", Context.MODE_PRIVATE)

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
        mainLayout.addView(makeTitle("[>] REVERSE IMAGE SEARCH"))

        // Info
        mainLayout.addView(TextView(requireContext()).apply {
            text = "[i] Generates search URLs for various reverse\n" +
                "    image search engines. Does NOT upload images.\n" +
                "    Provide a publicly accessible image URL."
            setTextColor(Color.parseColor("#FFFF00"))
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#1A1A00"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        // URL input
        mainLayout.addView(makeSectionHeader("IMAGE URL"))

        urlEdit = EditText(requireContext()).apply {
            hint = "https://example.com/image.jpg"
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 13f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(urlEdit)

        // Paste from clipboard button
        mainLayout.addView(makeButton("PASTE FROM CLIPBOARD") { pasteFromClipboard() })

        // Status
        statusText = makeLabel("[>] Enter an image URL to search")
        mainLayout.addView(statusText)

        // Search engine buttons
        mainLayout.addView(makeSectionHeader("SEARCH ENGINES"))

        for ((name, _) in searchEngines) {
            mainLayout.addView(makeEngineButton(name) { searchWithEngine(name) })
        }

        // Search with all
        mainLayout.addView(makeButton("SEARCH ALL ENGINES") { searchAll() })

        mainLayout.addView(makeDivider())

        // Quick links preview
        mainLayout.addView(makeSectionHeader("GENERATED LINKS"))

        val linksContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            tag = "links_container"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(linksContainer)

        mainLayout.addView(makeDivider())

        // History
        mainLayout.addView(makeSectionHeader("SEARCH HISTORY"))

        val histBtnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        histBtnRow.addView(makeHalfButton("CLEAR HISTORY") { clearHistory() })
        histBtnRow.addView(makeHalfButton("REFRESH") { loadHistory() })
        mainLayout.addView(histBtnRow)

        historyContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(historyContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHistory()
        updateLinksPreview()
        urlEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateLinksPreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun getImageUrl(): String {
        return urlEdit.text.toString().trim()
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun searchWithEngine(engineName: String) {
        val url = getImageUrl()
        if (!isValidUrl(url)) {
            Toast.makeText(requireContext(), "Enter a valid image URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val generator = searchEngines[engineName] ?: return
            val searchUrl = generator(url)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            startActivity(intent)

            saveToHistory(url, listOf(engineName))
            statusText.text = "[>] Opened $engineName search"
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchAll() {
        val url = getImageUrl()
        if (!isValidUrl(url)) {
            Toast.makeText(requireContext(), "Enter a valid image URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            for ((name, generator) in searchEngines) {
                try {
                    val searchUrl = generator(url)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                    startActivity(intent)
                } catch (_: Exception) { }
            }

            saveToHistory(url, searchEngines.keys.toList())
            statusText.text = "[>] Opened all search engines"
        } catch (e: Exception) {
            statusText.text = "[!] Error: ${e.message}"
        }
    }

    private fun updateLinksPreview() {
        val container = mainLayout.findViewWithTag<LinearLayout>("links_container") ?: return
        container.removeAllViews()

        val url = getImageUrl()
        if (!isValidUrl(url)) {
            container.addView(TextView(requireContext()).apply {
                text = "[~] Enter URL to see generated links"
                setTextColor(DARK_GREEN)
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        for ((name, generator) in searchEngines) {
            try {
                val searchUrl = generator(url)

                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(DARK_GRAY)
                    setPadding(10, 6, 10, 6)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 4 }
                }

                row.addView(TextView(requireContext()).apply {
                    text = "▸ $name"
                    setTextColor(Color.parseColor("#FFFF00"))
                    textSize = 12f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                })

                row.addView(TextView(requireContext()).apply {
                    text = searchUrl.take(120) + if (searchUrl.length > 120) "..." else ""
                    setTextColor(GREEN)
                    textSize = 10f
                    setTypeface(Typeface.MONOSPACE)
                    setOnClickListener {
                        try {
                            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Search URL", searchUrl))
                            Toast.makeText(requireContext(), "Link copied", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) { }
                    }
                })

                container.addView(row)
            } catch (_: Exception) { }
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString() ?: ""
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    urlEdit.setText(text)
                    statusText.text = "[>] Pasted URL from clipboard"
                } else {
                    Toast.makeText(requireContext(), "Clipboard doesn't contain a valid URL", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            statusText.text = "[!] Paste error: ${e.message}"
        }
    }

    private fun saveToHistory(imageUrl: String, engines: List<String>) {
        try {
            searchHistory.add(0, SearchHistoryEntry(
                imageUrl = imageUrl,
                timestamp = System.currentTimeMillis(),
                engines = engines
            ))

            // Keep last 100
            while (searchHistory.size > 100) {
                searchHistory.removeAt(searchHistory.size - 1)
            }

            val arr = JSONArray()
            for (entry in searchHistory) {
                val obj = JSONObject().apply {
                    put("imageUrl", entry.imageUrl)
                    put("timestamp", entry.timestamp)
                    put("engines", JSONArray(entry.engines))
                }
                arr.put(obj)
            }
            prefs.edit().putString("history", arr.toString()).apply()
            renderHistory()
        } catch (_: Exception) { }
    }

    private fun loadHistory() {
        searchHistory.clear()
        try {
            val json = prefs.getString("history", "[]") ?: "[]"
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val enginesList = mutableListOf<String>()
                    val enginesArr = obj.getJSONArray("engines")
                    for (j in 0 until enginesArr.length()) {
                        enginesList.add(enginesArr.getString(j))
                    }
                    searchHistory.add(SearchHistoryEntry(
                        imageUrl = obj.getString("imageUrl"),
                        timestamp = obj.getLong("timestamp"),
                        engines = enginesList
                    ))
                } catch (_: Exception) { }
            }
            renderHistory()
        } catch (_: Exception) { }
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        val df = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        if (searchHistory.isEmpty()) {
            historyContainer.addView(TextView(requireContext()).apply {
                text = "[~] No search history"
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

        for ((index, entry) in searchHistory.take(30).withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(TextView(requireContext()).apply {
                text = entry.imageUrl.take(80) + if (entry.imageUrl.length > 80) "..." else ""
                setTextColor(GREEN)
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
            })

            row.addView(TextView(requireContext()).apply {
                text = "${df.format(Date(entry.timestamp))} | Engines: ${entry.engines.joinToString(", ")}"
                setTextColor(Color.parseColor("#888888"))
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
            })

            val btnRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            btnRow.addView(makeTinyButton("RE-SEARCH") {
                urlEdit.setText(entry.imageUrl)
                updateLinksPreview()
            })

            btnRow.addView(makeTinyButton("COPY URL") {
                try {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Image URL", entry.imageUrl))
                    Toast.makeText(requireContext(), "URL copied", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { }
            })

            row.addView(btnRow)
            historyContainer.addView(row)
        }

        if (searchHistory.size > 30) {
            historyContainer.addView(TextView(requireContext()).apply {
                text = "... and ${searchHistory.size - 30} more"
                setTextColor(DARK_GREEN)
                textSize = 11f
                setTypeface(Typeface.MONOSPACE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun clearHistory() {
        searchHistory.clear()
        prefs.edit().remove("history").apply()
        renderHistory()
        statusText.text = "[+] History cleared"
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

    private fun makeDivider(): View {
        return View(requireContext()).apply {
            setBackgroundColor(MED_GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
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

    private fun makeEngineButton(name: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = "SEARCH: $name"
            setTextColor(GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2; bottomMargin = 2 }
            setOnClickListener { onClick() }
        }
    }

    private fun makeTinyButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 10f
            setTypeface(Typeface.MONOSPACE)
            setPadding(4, 2, 4, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 4 }
            setOnClickListener { onClick() }
        }
    }
}
