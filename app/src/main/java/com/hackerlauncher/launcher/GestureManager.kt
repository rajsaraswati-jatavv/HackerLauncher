package com.hackerlauncher.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PointF
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject

// ─── Gesture Types ────────────────────────────────────────────────────────────

enum class GestureType(val label: String) {
    SWIPE_UP("Swipe Up"),
    SWIPE_DOWN("Swipe Down"),
    SWIPE_LEFT("Swipe Left"),
    SWIPE_RIGHT("Swipe Right"),
    DOUBLE_TAP("Double Tap"),
    LONG_PRESS("Long Press"),
    PINCH_IN("Pinch In"),
    PINCH_OUT("Pinch Out")
}

// ─── Action Types ─────────────────────────────────────────────────────────────

enum class ActionType(val label: String) {
    OPEN_DRAWER("Open Drawer"),
    OPEN_SEARCH("Open Search"),
    OPEN_SETTINGS("Open Settings"),
    LAUNCH_APP("Launch App"),
    TOGGLE_SETTING("Toggle Setting"),
    NONE("None")
}

// ─── Gesture Action Data Class ────────────────────────────────────────────────

data class GestureAction(
    val type: GestureType,
    val action: ActionType,
    val targetPackage: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("gesture", type.name)
        put("action", action.name)
        put("targetPackage", targetPackage)
    }

    companion object {
        fun fromJson(json: JSONObject): GestureAction = GestureAction(
            type = GestureType.valueOf(json.getString("gesture")),
            action = ActionType.valueOf(json.getString("action")),
            targetPackage = json.optString("targetPackage", "")
        )
    }
}

// ─── Gesture Manager ──────────────────────────────────────────────────────────

class GestureManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "gesture_prefs"
        private const val KEY_GESTURES = "gesture_mappings"
        private const val KEY_SENSITIVITY = "gesture_sensitivity"
        private const val KEY_PER_SCREEN = "per_screen_gestures"

        @Volatile
        private var instance: GestureManager? = null

        fun getInstance(context: Context): GestureManager {
            return instance ?: synchronized(this) {
                instance ?: GestureManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var gestureMap = mutableMapOf<GestureType, GestureAction>()
    private var perScreenGestures = mutableMapOf<String, MutableMap<GestureType, GestureAction>>()
    var sensitivity: Float = 1.0f
        private set

    // Callback for action execution
    var onActionExecuted: ((ActionType, String) -> Unit)? = null

    init {
        loadGestures()
        loadSensitivity()
        loadPerScreenGestures()
        if (gestureMap.isEmpty()) {
            setDefaults()
        }
    }

    // ─── Default Gestures ──────────────────────────────────────────────────

    private fun setDefaults() {
        gestureMap.clear()
        gestureMap[GestureType.SWIPE_UP] = GestureAction(GestureType.SWIPE_UP, ActionType.OPEN_DRAWER)
        gestureMap[GestureType.SWIPE_DOWN] = GestureAction(GestureType.SWIPE_DOWN, ActionType.OPEN_SETTINGS, "notifications")
        gestureMap[GestureType.SWIPE_LEFT] = GestureAction(GestureType.SWIPE_LEFT, ActionType.NONE)
        gestureMap[GestureType.SWIPE_RIGHT] = GestureAction(GestureType.SWIPE_RIGHT, ActionType.NONE)
        gestureMap[GestureType.DOUBLE_TAP] = GestureAction(GestureType.DOUBLE_TAP, ActionType.TOGGLE_SETTING, "lock")
        gestureMap[GestureType.LONG_PRESS] = GestureAction(GestureType.LONG_PRESS, ActionType.OPEN_SEARCH)
        gestureMap[GestureType.PINCH_IN] = GestureAction(GestureType.PINCH_IN, ActionType.NONE)
        gestureMap[GestureType.PINCH_OUT] = GestureAction(GestureType.PINCH_OUT, ActionType.NONE)
        saveGestures()
    }

    // ─── Persistence ───────────────────────────────────────────────────────

    private fun loadGestures() {
        val json = prefs.getString(KEY_GESTURES, null) ?: return
        try {
            val obj = JSONObject(json)
            gestureMap.clear()
            GestureType.values().forEach { type ->
                if (obj.has(type.name)) {
                    gestureMap[type] = GestureAction.fromJson(obj.getJSONObject(type.name))
                }
            }
        } catch (_: Exception) {
            gestureMap.clear()
        }
    }

    private fun saveGestures() {
        val obj = JSONObject()
        gestureMap.forEach { (type, action) ->
            obj.put(type.name, action.toJson())
        }
        prefs.edit().putString(KEY_GESTURES, obj.toString()).apply()
    }

    private fun loadSensitivity() {
        sensitivity = prefs.getFloat(KEY_SENSITIVITY, 1.0f)
    }

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_SENSITIVITY, sensitivity).apply()
    }

    private fun loadPerScreenGestures() {
        val json = prefs.getString(KEY_PER_SCREEN, null) ?: return
        try {
            val obj = JSONObject(json)
            perScreenGestures.clear()
            obj.keys().forEach { screenId ->
                val screenObj = obj.getJSONObject(screenId)
                val screenGestures = mutableMapOf<GestureType, GestureAction>()
                GestureType.values().forEach { type ->
                    if (screenObj.has(type.name)) {
                        screenGestures[type] = GestureAction.fromJson(screenObj.getJSONObject(type.name))
                    }
                }
                perScreenGestures[screenId] = screenGestures
            }
        } catch (_: Exception) {
            perScreenGestures.clear()
        }
    }

    private fun savePerScreenGestures() {
        val obj = JSONObject()
        perScreenGestures.forEach { (screenId, gestures) ->
            val screenObj = JSONObject()
            gestures.forEach { (type, action) ->
                screenObj.put(type.name, action.toJson())
            }
            obj.put(screenId, screenObj)
        }
        prefs.edit().putString(KEY_PER_SCREEN, obj.toString()).apply()
    }

    // ─── Mapping ───────────────────────────────────────────────────────────

    fun setGestureAction(gestureType: GestureType, action: GestureAction) {
        gestureMap[gestureType] = action
        saveGestures()
    }

    fun getGestureAction(gestureType: GestureType): GestureAction? {
        return gestureMap[gestureType]
    }

    fun setPerScreenGesture(screenId: String, gestureType: GestureType, action: GestureAction) {
        val screenGestures = perScreenGestures.getOrPut(screenId) { mutableMapOf() }
        screenGestures[gestureType] = action
        savePerScreenGestures()
    }

    fun getPerScreenGesture(screenId: String, gestureType: GestureType): GestureAction? {
        return perScreenGestures[screenId]?.get(gestureType)
    }

    fun getAllMappings(): Map<GestureType, GestureAction> = gestureMap.toMap()

    fun resetToDefaults() {
        setDefaults()
        perScreenGestures.clear()
        savePerScreenGestures()
    }

    // ─── Execute Action ────────────────────────────────────────────────────

    fun executeAction(gestureType: GestureType, screenId: String? = null) {
        val action = if (screenId != null) {
            perScreenGestures[screenId]?.get(gestureType) ?: gestureMap[gestureType]
        } else {
            gestureMap[gestureType]
        } ?: return

        if (action.action == ActionType.NONE) return

        when (action.action) {
            ActionType.OPEN_DRAWER -> openDrawer()
            ActionType.OPEN_SEARCH -> openSearch()
            ActionType.OPEN_SETTINGS -> openSettings(action.targetPackage)
            ActionType.LAUNCH_APP -> launchApp(action.targetPackage)
            ActionType.TOGGLE_SETTING -> toggleSetting(action.targetPackage)
            ActionType.NONE -> { /* no-op */ }
        }

        onActionExecuted?.invoke(action.action, action.targetPackage)
    }

    private fun openDrawer() {
        val intent = Intent("com.hackerlauncher.action.OPEN_DRAWER")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    private fun openSearch() {
        val intent = Intent("com.hackerlauncher.action.OPEN_SEARCH")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    private fun openSettings(target: String) {
        when (target) {
            "notifications" -> {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            "wifi" -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            "bluetooth" -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            else -> {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    private fun launchApp(packageName: String) {
        if (packageName.isBlank()) return
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun toggleSetting(setting: String) {
        when (setting) {
            "wifi" -> toggleWifi()
            "bluetooth" -> toggleBluetooth()
            "lock" -> lockScreen()
            "airplane" -> toggleAirplane()
            "flashlight" -> toggleFlashlight()
        }
    }

    private fun toggleWifi() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun toggleBluetooth() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun lockScreen() {
        try {
            val intent = Intent("com.hackerlauncher.action.LOCK_SCREEN")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    private fun toggleAirplane() {
        try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun toggleFlashlight() {
        // Requires system permissions - delegate to service
        val intent = Intent("com.hackerlauncher.action.TOGGLE_FLASHLIGHT")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    // ─── Gesture Detector ─────────────────────────────────────────────────

    fun createGestureDetector(): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {
                executeAction(GestureType.DOUBLE_TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                executeAction(GestureType.LONG_PRESS)
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val threshold = 100f / sensitivity
                val velocityThreshold = 200f / sensitivity

                if (Math.abs(dx) > Math.abs(dy)) {
                    // Horizontal
                    if (Math.abs(dx) > threshold && Math.abs(velocityX) > velocityThreshold) {
                        if (dx > 0) {
                            executeAction(GestureType.SWIPE_RIGHT)
                        } else {
                            executeAction(GestureType.SWIPE_LEFT)
                        }
                        return true
                    }
                } else {
                    // Vertical
                    if (Math.abs(dy) > threshold && Math.abs(velocityY) > velocityThreshold) {
                        if (dy > 0) {
                            executeAction(GestureType.SWIPE_DOWN)
                        } else {
                            executeAction(GestureType.SWIPE_UP)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // ─── Pinch Detection ──────────────────────────────────────────────────

    fun detectPinch(event: MotionEvent): GestureType? {
        if (event.pointerCount < 2) return null

        val p0 = PointF(event.getX(0), event.getY(0))
        val p1 = PointF(event.getX(1), event.getY(1))
        val currentDist = distance(p0, p1)

        // Track previous distance through tag on the view
        val view = event.source.let { null } // We'll use a different approach
        return null // Pinch detection requires state tracking, handled in PinchHelper
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}

// ─── Pinch Helper ─────────────────────────────────────────────────────────────

class PinchHelper(private val gestureManager: GestureManager) {

    private var previousDistance: Float = 0f
    private var isPinching = false
    private val pinchThreshold = 50f

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    previousDistance = getDistance(event)
                    isPinching = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPinching && event.pointerCount >= 2) {
                    val currentDistance = getDistance(event)
                    val delta = currentDistance - previousDistance

                    if (Math.abs(delta) > pinchThreshold / gestureManager.sensitivity) {
                        if (delta > 0) {
                            gestureManager.executeAction(GestureType.PINCH_OUT)
                        } else {
                            gestureManager.executeAction(GestureType.PINCH_IN)
                        }
                        previousDistance = currentDistance
                        isPinching = false // Prevent repeated triggers
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                isPinching = false
                previousDistance = 0f
            }
        }
        return false
    }

    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}

// ─── Gesture Overlay View ─────────────────────────────────────────────────────

class GestureOverlayView(
    context: Context,
    private val gestureManager: GestureManager
) : View(context) {

    private val gestureDetector = gestureManager.createGestureDetector()
    private val pinchHelper = PinchHelper(gestureManager)
    private var currentScreenId: String = "default"

    fun setScreenId(screenId: String) {
        currentScreenId = screenId
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle pinch first
        if (pinchHelper.onTouchEvent(event)) return true

        // Handle other gestures
        gestureDetector.onTouchEvent(event)

        // Handle long press manually if needed
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
