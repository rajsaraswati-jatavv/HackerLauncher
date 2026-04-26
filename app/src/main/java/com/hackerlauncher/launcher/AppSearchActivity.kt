package com.hackerlauncher.launcher

import com.hackerlauncher.utils.PreferencesManager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Universal Search Activity.
 * Tabs: Apps, Contacts, Files, Web
 * Supports live filtering, recent searches, and voice search.
 */
class AppSearchActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_VOICE = 1001
        const val REQUEST_CONTACTS_PERMISSION = 1002
        const val REQUEST_STORAGE_PERMISSION = 1003
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefsManager: PreferencesManager
    private lateinit var searchEditText: EditText
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var recentRecyclerView: RecyclerView

    private var currentTab = 0 // 0=Apps, 1=Contacts, 2=Files, 3=Web
    private var allApps = mutableListOf<AppDrawerActivity.AppItem>()
    private var searchResults = mutableListOf<SearchResult>()
    private var recentSearches = mutableListOf<String>()

    private lateinit var resultsAdapter: SearchResultsAdapter

    data class SearchResult(
        val title: String,
        val subtitle: String,
        val type: Type,
        val data: Any? = null
    ) {
        enum class Type { APP, CONTACT, FILE, WEB, RECENT }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        prefsManager = PreferencesManager(this)
        recentSearches = prefsManager.getRecentSearches().toMutableList()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Search bar row
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 8)
        }

        searchEditText = EditText(this).apply {
            hint = "> search..."
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
            setPadding(24, 16, 24, 16)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        searchRow.addView(searchEditText)

        // Voice search button
        val voiceBtn = TextView(this).apply {
            text = "🎤"
            textSize = 24f
            setPadding(16, 8, 16, 8)
            setOnClickListener { startVoiceSearch() }
        }
        searchRow.addView(voiceBtn)

        rootLayout.addView(searchRow)

        // Tab layout
        tabLayout = TabLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setSelectedTabIndicatorColor(Color.parseColor("#00FF00"))
            setTabTextColors(Color.parseColor("#005500"), Color.parseColor("#00FF00"))
            addTab(newTab().setText("APPS"))
            addTab(newTab().setText("CONTACTS"))
            addTab(newTab().setText("FILES"))
            addTab(newTab().setText("WEB"))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                performSearch(searchEditText.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        rootLayout.addView(tabLayout)

        // Recent searches
        recentRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AppSearchActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(recentRecyclerView)

        // Results
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AppSearchActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(recyclerView)

        resultsAdapter = SearchResultsAdapter(this, searchResults) { result ->
            onResultClick(result)
        }
        recyclerView.adapter = resultsAdapter

        setContentView(rootLayout)

        // Load recent searches
        updateRecentSearches()

        // Text watcher
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Load apps for search
        loadApps()
    }

    private fun loadApps() {
        scope.launch {
            allApps = withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                resolveInfos.map { resolveInfo ->
                    AppDrawerActivity.AppItem(
                        label = resolveInfo.loadLabel(packageManager).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                }.sortedBy { it.label.lowercase() }.toMutableList()
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults.clear()
            resultsAdapter.notifyDataSetChanged()
            updateRecentSearches()
            recentRecyclerView.visibility = View.VISIBLE
            return
        }

        recentRecyclerView.visibility = View.GONE
        searchResults.clear()

        scope.launch {
            when (currentTab) {
                0 -> searchApps(query)
                1 -> searchContacts(query)
                2 -> searchFiles(query)
                3 -> searchWeb(query)
            }
            resultsAdapter.notifyDataSetChanged()
        }
    }

    private suspend fun searchApps(query: String) {
        val q = query.lowercase()
        for (app in allApps) {
            if (app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)) {
                searchResults.add(
                    SearchResult(
                        title = app.label,
                        subtitle = app.packageName,
                        type = SearchResult.Type.APP,
                        data = app
                    )
                )
            }
        }
    }

    private suspend fun searchContacts(query: String) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                this@AppSearchActivity,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@AppSearchActivity,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS_PERMISSION
            )
            searchResults.add(
                SearchResult(
                    title = "> permission_required",
                    subtitle = "Grant contacts permission to search",
                    type = SearchResult.Type.CONTACT
                )
            )
            return@withContext
        }

        val q = query.lowercase()
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: ""
                    val number = it.getString(numberIndex) ?: ""
                    searchResults.add(
                        SearchResult(
                            title = name,
                            subtitle = number,
                            type = SearchResult.Type.CONTACT
                        )
                    )
                }
            }
        } catch (e: Exception) {
            searchResults.add(
                SearchResult(
                    title = "> error_searching_contacts",
                    subtitle = e.message ?: "",
                    type = SearchResult.Type.CONTACT
                )
            )
        }
    }

    private suspend fun searchFiles(query: String) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                this@AppSearchActivity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            searchResults.add(
                SearchResult(
                    title = "> storage_permission_required",
                    subtitle = "Grant storage permission to search files",
                    type = SearchResult.Type.FILE
                )
            )
            return@withContext
        }

        try {
            val q = query.lowercase()
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE
            )
            val cursor = contentResolver.query(
                uri,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"),
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC LIMIT 50"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: ""
                    val path = it.getString(pathIndex) ?: ""
                    searchResults.add(
                        SearchResult(
                            title = name,
                            subtitle = path,
                            type = SearchResult.Type.FILE
                        )
                    )
                }
            }
        } catch (e: Exception) {
            searchResults.add(
                SearchResult(
                    title = "> error_searching_files",
                    subtitle = e.message ?: "",
                    type = SearchResult.Type.FILE
                )
            )
        }
    }

    private fun searchWeb(query: String) {
        searchResults.add(
            SearchResult(
                title = "> search_web: \"$query\"",
                subtitle = "Open in browser",
                type = SearchResult.Type.WEB,
                data = query
            )
        )
    }

    private fun onResultClick(result: SearchResult) {
        // Save to recent
        val query = searchEditText.text.toString()
        if (query.isNotBlank()) {
            addRecentSearch(query)
        }

        when (result.type) {
            SearchResult.Type.APP -> {
                val app = result.data as? AppDrawerActivity.AppItem
                if (app != null) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    }
                }
            }
            SearchResult.Type.CONTACT -> {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.fromParts("tel", result.subtitle, null)
                }
                startActivity(intent)
            }
            SearchResult.Type.FILE -> {
                val file = java.io.File(result.subtitle)
                val uri = file.let { androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", it) }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/octet-stream")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "> error: cannot_open_file", Toast.LENGTH_SHORT).show()
                }
            }
            SearchResult.Type.WEB -> {
                val url = "https://www.google.com/search?q=${android.net.Uri.encode(query)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
            SearchResult.Type.RECENT -> {
                searchEditText.setText(result.title)
                searchEditText.setSelection(result.title.length)
                performSearch(result.title)
            }
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "> speak_search_query")
        }
        try {
            startActivityForResult(intent, REQUEST_VOICE)
        } catch (e: Exception) {
            Toast.makeText(this, "> error: voice_search_unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                searchEditText.setText(results[0])
                searchEditText.setSelection(results[0].length)
            }
        }
    }

    private fun addRecentSearch(query: String) {
        recentSearches.remove(query)
        recentSearches.add(0, query)
        if (recentSearches.size > 20) {
            recentSearches = recentSearches.take(20).toMutableList()
        }
        prefsManager.saveRecentSearches(recentSearches)
    }

    private fun updateRecentSearches() {
        if (recentSearches.isEmpty()) {
            recentRecyclerView.visibility = View.GONE
            return
        }
        recentRecyclerView.visibility = View.VISIBLE
        val recentResults = recentSearches.take(10).map {
            SearchResult(title = it, subtitle = "recent_search", type = SearchResult.Type.RECENT)
        }
        recentRecyclerView.adapter = SearchResultsAdapter(this, recentResults) { result ->
            searchEditText.setText(result.title)
            searchEditText.setSelection(result.title.length)
            performSearch(result.title)
        }
    }

    //region SearchResultsAdapter

    class SearchResultsAdapter(
        private val context: AppSearchActivity,
        private val items: List<SearchResult>,
        private val onClick: (SearchResult) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.ResultViewHolder>() {

        class ResultViewHolder(itemView: View, val titleView: TextView, val subtitleView: TextView, val typeBadge: TextView) :
            RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 12, 24, 12)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val typeBadge = TextView(context).apply {
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#00FF00"))
                typeface = Typeface.MONOSPACE
                textSize = 9f
                setPadding(6, 2, 6, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8 }
            }
            val titleView = TextView(context).apply {
                setTextColor(Color.parseColor("#00FF00"))
                typeface = Typeface.MONOSPACE
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            topRow.addView(typeBadge)
            topRow.addView(titleView)
            container.addView(topRow)

            val subtitleView = TextView(context).apply {
                setTextColor(Color.parseColor("#008800"))
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(0, 2, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(subtitleView)

            return ResultViewHolder(container, titleView, subtitleView, typeBadge)
        }

        override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
            val item = items[position]
            holder.titleView.text = item.title
            holder.subtitleView.text = item.subtitle
            holder.typeBadge.text = when (item.type) {
                SearchResult.Type.APP -> "APP"
                SearchResult.Type.CONTACT -> "CONTACT"
                SearchResult.Type.FILE -> "FILE"
                SearchResult.Type.WEB -> "WEB"
                SearchResult.Type.RECENT -> "RECENT"
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    //endregion
}
