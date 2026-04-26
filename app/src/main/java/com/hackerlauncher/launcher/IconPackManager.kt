package com.hackerlauncher.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

// ─── Data Models ──────────────────────────────────────────────────────────────

data class IconPack(
    val name: String,
    val packageName: String,
    val iconCount: Int = 0
)

// ─── Icon Pack Manager ────────────────────────────────────────────────────────

class IconPackManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "icon_pack_prefs"
        private const val KEY_ACTIVE_PACK = "active_icon_pack"
        private const val KEY_PER_APP_OVERRIDES = "per_app_icon_overrides"
        private const val ADW_ICON_PACK_ACTION = "org.adw.launcher.THEMES"
        private const val NOVA_ICON_PACK_ACTION = "com.novalauncher.THEME"

        // ADW/Nova XML resource names
        private const val ADW_APPFILTER = "appfilter"
        private const val ADW_DRAWABLE = "drawable"

        private const val CACHE_SIZE = 256 // number of icons

        @Volatile
        private var instance: IconPackManager? = null

        fun getInstance(context: Context): IconPackManager {
            return instance ?: synchronized(this) {
                instance ?: IconPackManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Icon cache: key = "packPkg:appPkg" or "default:appPkg"
    private val iconCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    // Parsed icon mappings: packPackageName -> (componentName -> drawableResourceName)
    private val iconMappings = mutableMapOf<String, Map<String, String>>()

    // Per-app icon overrides: appPackageName -> icon pack packageName
    private var perAppOverrides = mutableMapOf<String, String>()

    // Currently active icon pack
    var activePack: String = ""
        private set

    init {
        loadActivePack()
        loadPerAppOverrides()
    }

    // ─── Scan Installed Icon Packs ─────────────────────────────────────────

    fun scanInstalledPacks(): List<IconPack> {
        val packs = mutableListOf<IconPack>()

        // Scan for ADW icon packs
        val adwIntent = Intent(ADW_ICON_PACK_ACTION)
        val adwResolveInfos: List<ResolveInfo> = context.packageManager
            .queryIntentActivities(adwIntent, PackageManager.GET_META_DATA)
        for (info in adwResolveInfos) {
            val pkg = info.activityInfo.packageName
            val name = info.loadLabel(context.packageManager).toString()
            val count = countIconsInPack(pkg)
            packs.add(IconPack(name, pkg, count))
        }

        // Scan for Nova icon packs (may overlap with ADW)
        val novaIntent = Intent(NOVA_ICON_PACK_ACTION)
        val novaResolveInfos: List<ResolveInfo> = context.packageManager
            .queryIntentActivities(novaIntent, PackageManager.GET_META_DATA)
        for (info in novaResolveInfos) {
            val pkg = info.activityInfo.packageName
            if (packs.none { it.packageName == pkg }) {
                val name = info.loadLabel(context.packageManager).toString()
                val count = countIconsInPack(pkg)
                packs.add(IconPack(name, pkg, count))
            }
        }

        return packs.sortedBy { it.name.lowercase() }
    }

    private fun countIconsInPack(packPackageName: String): Int {
        val mappings = parseAppFilter(packPackageName)
        return mappings.size
    }

    // ─── Parse ADW/Nova Icon Pack Format ───────────────────────────────────

    fun parseAppFilter(packPackageName: String): Map<String, String> {
        // Check cache first
        iconMappings[packPackageName]?.let { return it }

        val mappings = mutableMapOf<String, String>()

        try {
            val resources = context.packageManager.getResourcesForApplication(packPackageName)
            val appFilterId = resources.getIdentifier(
                ADW_APPFILTER, "xml", packPackageName
            )

            if (appFilterId != 0) {
                val parser = resources.getXml(appFilterId)
                mappings.putAll(parseAppFilterXml(parser))
            } else {
                // Try raw XML resource
                val rawId = resources.getIdentifier(
                    ADW_APPFILTER, "raw", packPackageName
                )
                if (rawId != 0) {
                    val inputStream = resources.openRawResource(rawId)
                    mappings.putAll(parseAppFilterFromStream(inputStream))
                }
            }
        } catch (e: Exception) {
            // Pack parsing failed
        }

        iconMappings[packPackageName] = mappings
        return mappings
    }

    private fun parseAppFilterXml(parser: XmlPullParser): Map<String, String> {
        val mappings = mutableMapOf<String, String>()
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) {
                            // Component format: {pkg/cls} or ComponentInfo{pkg/cls}
                            val cleaned = component
                                .removeSurrounding("{", "}")
                                .removePrefix("ComponentInfo{")
                                .removeSuffix("}")
                            mappings[cleaned] = drawable
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) { }
        return mappings
    }

    private fun parseAppFilterFromStream(inputStream: InputStream): Map<String, String> {
        val mappings = mutableMapOf<String, String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            mappings.putAll(parseAppFilterXml(parser))
        } catch (_: Exception) { }
        return mappings
    }

    // ─── Load Icon for App ─────────────────────────────────────────────────

    fun getIconForApp(appPackageName: String, componentName: String = ""): Drawable? {
        val cacheKey = buildCacheKey(appPackageName)

        // Check cache
        iconCache.get(cacheKey)?.let {
            return BitmapDrawable(context.resources, it)
        }

        // Check per-app override first
        val overridePack = perAppOverrides[appPackageName]
        if (overridePack != null) {
            val icon = loadIconFromPack(overridePack, appPackageName, componentName)
            if (icon != null) return icon
        }

        // Check active pack
        if (activePack.isNotEmpty()) {
            val icon = loadIconFromPack(activePack, appPackageName, componentName)
            if (icon != null) return icon
        }

        // Default hacker icon
        return generateDefaultIcon(appPackageName)
    }

    private fun loadIconFromPack(
        packPackageName: String,
        appPackageName: String,
        componentName: String
    ): Drawable? {
        val mappings = parseAppFilter(packPackageName)
        if (mappings.isEmpty()) return null

        // Try exact component match first
        var drawableName: String? = null
        if (componentName.isNotEmpty()) {
            drawableName = mappings[componentName]
        }

        // Try package match
        if (drawableName == null) {
            drawableName = mappings.entries.find {
                it.key.startsWith(appPackageName) || it.key.contains(appPackageName)
            }?.value
        }

        if (drawableName == null) return null

        try {
            val resources = context.packageManager.getResourcesForApplication(packPackageName)
            val resId = resources.getIdentifier(drawableName, ADW_DRAWABLE, packPackageName)
            if (resId != 0) {
                val drawable = resources.getDrawable(resId, null)
                if (drawable is BitmapDrawable) {
                    val cacheKey = buildCacheKey(appPackageName)
                    iconCache.put(cacheKey, drawable.bitmap)
                }
                return drawable
            }
        } catch (_: Exception) { }

        return null
    }

    // ─── Default Hacker Icon ───────────────────────────────────────────────

    fun generateDefaultIcon(packageName: String): Drawable {
        val cacheKey = "default:$packageName"
        iconCache.get(cacheKey)?.let {
            return BitmapDrawable(context.resources, it)
        }

        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw green circle background
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A3A1A")
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, circlePaint)

        // Draw circle border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF00")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(radius, radius, radius - 2f, borderPaint)

        // Draw first letter
        val letter = getFirstLetter(packageName)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF00")
            textSize = size * 0.5f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(letter, 0, letter.length, textBounds)
        val textY = radius + textBounds.height() / 2f
        canvas.drawText(letter, radius, textY, textPaint)

        // Small terminal-like decoration
        val decoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#005500")
            strokeWidth = 1f
        }
        canvas.drawLine(8f, size - 16f, size - 8f, size - 16f, decoPaint)
        canvas.drawLine(8f, size - 12f, (size * 0.6f), size - 12f, decoPaint)

        iconCache.put(cacheKey, bitmap)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun getFirstLetter(packageName: String): String {
        // Try to get app name first letter
        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val label = context.packageManager.getApplicationLabel(appInfo).toString()
            if (label.isNotEmpty()) return label.first().uppercaseChar().toString()
        } catch (_: Exception) { }
        // Fallback to package name
        val parts = packageName.split(".")
        val last = parts.lastOrNull() ?: packageName
        return if (last.isNotEmpty()) last.first().uppercaseChar().toString() else "?"
    }

    // ─── Apply Pack ────────────────────────────────────────────────────────

    fun applyPack(packPackageName: String) {
        activePack = packPackageName
        prefs.edit().putString(KEY_ACTIVE_PACK, packPackageName).apply()
        iconCache.evictAll()
        iconMappings.remove(packPackageName) // Force re-parse
    }

    fun clearPack() {
        activePack = ""
        prefs.edit().remove(KEY_ACTIVE_PACK).apply()
        iconCache.evictAll()
    }

    // ─── Per-App Override ──────────────────────────────────────────────────

    fun setPerAppOverride(appPackageName: String, packPackageName: String) {
        perAppOverrides[appPackageName] = packPackageName
        savePerAppOverrides()
        iconCache.remove(buildCacheKey(appPackageName))
    }

    fun removePerAppOverride(appPackageName: String) {
        perAppOverrides.remove(appPackageName)
        savePerAppOverrides()
        iconCache.remove(buildCacheKey(appPackageName))
    }

    fun getPerAppOverride(appPackageName: String): String? {
        return perAppOverrides[appPackageName]
    }

    // ─── Reset to Default ──────────────────────────────────────────────────

    fun resetToDefault() {
        activePack = ""
        perAppOverrides.clear()
        iconCache.evictAll()
        iconMappings.clear()
        prefs.edit().clear().apply()
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun buildCacheKey(appPackageName: String): String {
        val pack = perAppOverrides[appPackageName] ?: activePack
        return "$pack:$appPackageName"
    }

    private fun loadActivePack() {
        activePack = prefs.getString(KEY_ACTIVE_PACK, "") ?: ""
    }

    private fun loadPerAppOverrides() {
        val json = prefs.getString(KEY_PER_APP_OVERRIDES, null) ?: return
        try {
            val obj = JSONObject(json)
            perAppOverrides.clear()
            obj.keys().forEach { key ->
                perAppOverrides[key] = obj.getString(key)
            }
        } catch (_: Exception) {
            perAppOverrides.clear()
        }
    }

    private fun savePerAppOverrides() {
        val obj = JSONObject()
        perAppOverrides.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_PER_APP_OVERRIDES, obj.toString()).apply()
    }
}
