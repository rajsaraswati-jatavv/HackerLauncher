package com.hackerlauncher.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.content.Intent
import com.hackerlauncher.livewallpaper.HackerWallpaperService
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

// ─── Theme Data Class ─────────────────────────────────────────────────────────

data class Theme(
    val name: String,
    val primaryColor: String,    // Main text/accent color
    val accentColor: String,     // Secondary accent
    val bgColor: String,         // Background color
    val fontName: String = "monospace"  // Font name from assets
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("primaryColor", primaryColor)
        put("accentColor", accentColor)
        put("bgColor", bgColor)
        put("fontName", fontName)
    }

    companion object {
        fun fromJson(json: JSONObject): Theme = Theme(
            name = json.getString("name"),
            primaryColor = json.getString("primaryColor"),
            accentColor = json.getString("accentColor"),
            bgColor = json.getString("bgColor"),
            fontName = json.optString("fontName", "monospace")
        )
    }
}

// ─── Pre-defined Themes ───────────────────────────────────────────────────────

object HackerThemes {

    val MATRIX_GREEN = Theme(
        name = "Matrix Green",
        primaryColor = "#00FF00",
        accentColor = "#00CC00",
        bgColor = "#000000",
        fontName = "matrix"
    )

    val CYBER_RED = Theme(
        name = "Cyber Red",
        primaryColor = "#FF0040",
        accentColor = "#FF4444",
        bgColor = "#0A0000",
        fontName = "cyber"
    )

    val NEON_BLUE = Theme(
        name = "Neon Blue",
        primaryColor = "#00BFFF",
        accentColor = "#0080FF",
        bgColor = "#000510",
        fontName = "neon"
    )

    val PURPLE_HAZE = Theme(
        name = "Purple Haze",
        primaryColor = "#BF00FF",
        accentColor = "#8000FF",
        bgColor = "#0A0010",
        fontName = "haze"
    )

    val AMBER = Theme(
        name = "Amber",
        primaryColor = "#FFBF00",
        accentColor = "#FF8800",
        bgColor = "#0A0800",
        fontName = "amber"
    )

    val WHITE_ICE = Theme(
        name = "White Ice",
        primaryColor = "#FFFFFF",
        accentColor = "#AAEEFF",
        bgColor = "#0A0A14",
        fontName = "ice"
    )

    val ALL = listOf(MATRIX_GREEN, CYBER_RED, NEON_BLUE, PURPLE_HAZE, AMBER, WHITE_ICE)

    fun getByName(name: String): Theme? = ALL.find { it.name == name }
}

// ─── Theme Manager ────────────────────────────────────────────────────────────

class ThemeManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_CURRENT_THEME = "current_theme"
        private const val KEY_CUSTOM_THEMES = "custom_themes"
        private const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"
        private const val FONTS_DIR = "fonts"
        private const val EXPORT_DIR = "theme_exports"

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentTheme: Theme = HackerThemes.MATRIX_GREEN
        private set

    private var customThemes = mutableListOf<Theme>()
    private var wallpaperEnabled = false

    // Listener for theme changes
    var onThemeChanged: ((Theme) -> Unit)? = null

    // Font cache
    private val fontCache = mutableMapOf<String, Typeface>()

    init {
        loadCurrentTheme()
        loadCustomThemes()
        wallpaperEnabled = prefs.getBoolean(KEY_WALLPAPER_ENABLED, false)
    }

    // ─── Apply Theme ───────────────────────────────────────────────────────

    fun applyTheme(theme: Theme, animate: Boolean = false) {
        val previousTheme = currentTheme
        currentTheme = theme
        saveCurrentTheme()
        onThemeChanged?.invoke(theme)
    }

    fun applyThemeToView(view: View, theme: Theme = currentTheme) {
        view.setBackgroundColor(parseColor(theme.bgColor))
        if (view is android.widget.TextView) {
            view.setTextColor(parseColor(theme.primaryColor))
            view.typeface = getFont(theme.fontName)
        }
    }

    fun applyThemeRecursive(view: View, theme: Theme = currentTheme) {
        view.setBackgroundColor(parseColor(theme.bgColor))
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeRecursive(view.getChildAt(i), theme)
            }
        } else if (view is android.widget.TextView) {
            view.setTextColor(parseColor(theme.primaryColor))
            view.typeface = getFont(theme.fontName)
        } else if (view is android.widget.Button) {
            view.setTextColor(parseColor(theme.primaryColor))
            view.typeface = getFont(theme.fontName)
        } else if (view is android.widget.EditText) {
            view.setTextColor(parseColor(theme.primaryColor))
            view.setHintTextColor(parseColorWithAlpha(theme.primaryColor, 0.5f))
            view.typeface = getFont(theme.fontName)
        }
    }

    // ─── Transition Animations ─────────────────────────────────────────────

    fun applyThemeWithTransition(
        rootView: View,
        theme: Theme,
        duration: Long = 500
    ) {
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            this.duration = duration / 2
        }

        val fadeIn = AlphaAnimation(0f, 1f).apply {
            this.duration = duration / 2
            startOffset = duration / 2
        }

        val animSet = AnimationSet(true).apply {
            addAnimation(fadeOut)
            addAnimation(fadeIn)
        }

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                applyTheme(theme)
                applyThemeRecursive(rootView, theme)
            }
        })

        rootView.startAnimation(animSet)
    }

    // ─── Font Loading ──────────────────────────────────────────────────────

    fun getFont(fontName: String): Typeface {
        fontCache[fontName]?.let { return it }

        // Try loading from assets/fonts/
        val fontFileName = "$FONTS_DIR/$fontName.ttf"
        try {
            val inputStream = context.assets.open(fontFileName)
            inputStream.close()
            val typeface = Typeface.createFromAsset(context.assets, fontFileName)
            fontCache[fontName] = typeface
            return typeface
        } catch (_: Exception) { }

        // Try loading from internal storage
        val fontFile = File(context.filesDir, "$FONTS_DIR/$fontName.ttf")
        if (fontFile.exists()) {
            val typeface = Typeface.createFromFile(fontFile)
            fontCache[fontName] = typeface
            return typeface
        }

        // Fallback to monospace
        return Typeface.MONOSPACE
    }

    // ─── Wallpaper Integration ─────────────────────────────────────────────

    fun setWallpaperEnabled(enabled: Boolean) {
        wallpaperEnabled = enabled
        prefs.edit().putBoolean(KEY_WALLPAPER_ENABLED, enabled).apply()

        if (enabled) {
            startHackerWallpaper()
        } else {
            stopHackerWallpaper()
        }
    }

    fun isWallpaperEnabled(): Boolean = wallpaperEnabled

    private fun startHackerWallpaper() {
        try {
            val intent = Intent(context, HackerWallpaperService::class.java)
            intent.action = HackerWallpaperService.ACTION_START
            context.startService(intent)
        } catch (_: Exception) { }
    }

    private fun stopHackerWallpaper() {
        try {
            val intent = Intent(context, HackerWallpaperService::class.java)
            intent.action = HackerWallpaperService.ACTION_STOP
            context.startService(intent)
        } catch (_: Exception) { }
    }

    // ─── Custom Themes ─────────────────────────────────────────────────────

    fun addCustomTheme(theme: Theme) {
        if (customThemes.none { it.name == theme.name }) {
            customThemes.add(theme)
            saveCustomThemes()
        }
    }

    fun removeCustomTheme(name: String) {
        customThemes.removeAll { it.name == name }
        saveCustomThemes()
    }

    fun getCustomThemes(): List<Theme> = customThemes.toList()

    fun getAllThemes(): List<Theme> = HackerThemes.ALL + customThemes

    // ─── Export / Import ───────────────────────────────────────────────────

    fun exportTheme(theme: Theme): String {
        return theme.toJson().toString(2)
    }

    fun exportAllThemes(): String {
        val arr = JSONArray()
        HackerThemes.ALL.forEach { arr.put(it.toJson()) }
        customThemes.forEach { arr.put(it.toJson()) }
        return arr.toString(2)
    }

    fun exportThemeToFile(theme: Theme): File {
        val dir = File(context.getExternalFilesDir(null), EXPORT_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${theme.name.replace(" ", "_")}.json")
        file.writeText(exportTheme(theme))
        return file
    }

    fun importTheme(json: String): Theme? {
        return try {
            val obj = JSONObject(json)
            val theme = Theme.fromJson(obj)
            if (theme.name.isNotBlank()) {
                addCustomTheme(theme)
                theme
            } else null
        } catch (_: Exception) { null }
    }

    fun importThemeFromFile(file: File): Theme? {
        return try {
            val json = file.readText()
            importTheme(json)
        } catch (_: Exception) { null }
    }

    // ─── Color Utilities ───────────────────────────────────────────────────

    fun getPrimaryColor(): Int = parseColor(currentTheme.primaryColor)
    fun getAccentColor(): Int = parseColor(currentTheme.accentColor)
    fun getBgColor(): Int = parseColor(currentTheme.bgColor)

    fun getPrimaryColorHex(): String = currentTheme.primaryColor
    fun getAccentColorHex(): String = currentTheme.accentColor
    fun getBgColorHex(): String = currentTheme.bgColor

    // ─── Persistence ───────────────────────────────────────────────────────

    private fun loadCurrentTheme() {
        val json = prefs.getString(KEY_CURRENT_THEME, null)
        if (json != null) {
            try {
                currentTheme = Theme.fromJson(JSONObject(json))
            } catch (_: Exception) {
                currentTheme = HackerThemes.MATRIX_GREEN
            }
        }
    }

    private fun saveCurrentTheme() {
        prefs.edit().putString(KEY_CURRENT_THEME, currentTheme.toJson().toString()).apply()
    }

    private fun loadCustomThemes() {
        val json = prefs.getString(KEY_CUSTOM_THEMES, null) ?: return
        try {
            val arr = JSONArray(json)
            customThemes.clear()
            for (i in 0 until arr.length()) {
                customThemes.add(Theme.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
            customThemes.clear()
        }
    }

    private fun saveCustomThemes() {
        val arr = JSONArray()
        customThemes.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM_THEMES, arr.toString()).apply()
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun parseColor(colorHex: String): Int {
        return try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.parseColor("#00FF00")
        }
    }

    private fun parseColorWithAlpha(colorHex: String, alpha: Float): Int {
        val color = parseColor(colorHex)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = (alpha * 255).toInt()
        return Color.argb(a, r, g, b)
    }
}


