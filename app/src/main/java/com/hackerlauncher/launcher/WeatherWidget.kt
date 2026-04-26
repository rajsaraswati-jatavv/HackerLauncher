package com.hackerlauncher.launcher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Weather data provider for HackerLauncher.
 * Fetches weather data from OpenWeatherMap API using OkHttp.
 * Caches results in SharedPreferences as JSON.
 * Provides mock data when no API key is configured.
 * Supports auto-refresh via WorkManager.
 */
object WeatherWidget {

    private const val TAG = "WeatherWidget"
    private const val PREFS_NAME = "hackerlauncher_weather"
    private const val KEY_CACHED_WEATHER = "cached_weather"
    private const val KEY_LAST_FETCH = "last_fetch_time"
    private const val KEY_TEMP_UNIT = "temp_unit" // "C" or "F"
    private const val KEY_API_KEY = "owm_api_key"
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Weather data model.
     */
    data class WeatherData(
        val temp: Double,
        val tempMin: Double,
        val tempMax: Double,
        val feelsLike: Double,
        val condition: String,
        val icon: String,
        val location: String,
        val humidity: Int,
        val windSpeed: Double,
        val windDeg: Int,
        val pressure: Int,
        val visibility: Int,
        val clouds: Int,
        val forecast: List<ForecastItem> = emptyList()
    ) {
        /**
         * Get temperature in preferred unit.
         */
        fun getDisplayTemp(unit: String): String {
            return if (unit == "F") {
                "${(temp * 9 / 5 + 32).toInt()}°F"
            } else {
                "${temp.toInt()}°C"
            }
        }

        /**
         * Get formatted weather string.
         */
        fun getFormattedWeather(unit: String = "C"): String {
            return "$condition ${getDisplayTemp(unit)} @ $location"
        }

        /**
         * Get hacker-style weather string.
         */
        fun getHackerFormat(unit: String = "C"): String {
            val tempStr = getDisplayTemp(unit)
            val condStr = condition.uppercase().replace(" ", "_")
            return "> weather: $condStr | temp: $tempStr | loc: $location | humidity: ${humidity}% | wind: ${windSpeed}m/s"
        }
    }

    /**
     * Forecast item for multi-day forecast.
     */
    data class ForecastItem(
        val date: String,
        val tempMin: Double,
        val tempMax: Double,
        val condition: String,
        val icon: String
    )

    /**
     * Fetch weather data for given coordinates.
     * Uses cached data if available and fresh (< 30 min).
     * Falls back to mock data if no API key is set.
     */
    suspend fun fetchWeather(
        context: Context,
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false
    ): WeatherData = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""

        // Check cache first
        if (!forceRefresh) {
            val cached = getCachedWeather(prefs)
            val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
            if (cached != null && (System.currentTimeMillis() - lastFetch) < CACHE_DURATION_MS) {
                Log.d(TAG, "> returning_cached_weather")
                return@withContext cached
            }
        }

        if (apiKey.isNullOrEmpty() || apiKey.length < 10) {
            Log.d(TAG, "> no_api_key_set. returning_mock_data")
            return@withContext getMockWeather(lat, lon)
        }

        try {
            val weatherData = fetchFromApi(apiKey, lat, lon)
            // Cache the result
            cacheWeather(prefs, weatherData)
            Log.d(TAG, "> weather_fetched_and_cached: ${weatherData.getHackerFormat()}")
            weatherData
        } catch (e: Exception) {
            Log.e(TAG, "> error_fetching_weather: ${e.message}")
            // Return cached data if available, otherwise mock
            getCachedWeather(prefs) ?: getMockWeather(lat, lon)
        }
    }

    /**
     * Fetch weather from OpenWeatherMap API.
     */
    private fun fetchFromApi(apiKey: String, lat: Double, lon: Double): WeatherData {
        // Current weather
        val currentUrl = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&units=metric"
        val currentRequest = Request.Builder().url(currentUrl).build()
        val currentResponse = client.newCall(currentRequest).execute()
        val currentBody = currentResponse.body?.string() ?: throw Exception("Empty response")

        val currentJson = JSONObject(currentBody)

        val main = currentJson.getJSONObject("main")
        val weather = currentJson.getJSONArray("weather").getJSONObject(0)
        val wind = currentJson.getJSONObject("wind")
        val clouds = currentJson.optJSONObject("clouds")

        val temp = main.getDouble("temp")
        val tempMin = main.getDouble("temp_min")
        val tempMax = main.getDouble("temp_max")
        val feelsLike = main.getDouble("feels_like")
        val humidity = main.getInt("humidity")
        val pressure = main.getInt("pressure")
        val condition = weather.getString("main")
        val icon = weather.getString("icon")
        val location = currentJson.getString("name")
        val windSpeed = wind.getDouble("speed")
        val windDeg = wind.optInt("deg", 0)
        val visibility = currentJson.optInt("visibility", 0)
        val cloudPercent = clouds?.optInt("all", 0) ?: 0

        // Fetch 5-day forecast
        val forecastItems = try {
            val forecastUrl = "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&appid=$apiKey&units=metric"
            val forecastRequest = Request.Builder().url(forecastUrl).build()
            val forecastResponse = client.newCall(forecastRequest).execute()
            val forecastBody = forecastResponse.body?.string()

            if (forecastBody != null) {
                val forecastJson = JSONObject(forecastBody)
                val list = forecastJson.getJSONArray("list")
                val items = mutableListOf<ForecastItem>()
                val processedDates = mutableSetOf<String>()

                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val dtTxt = item.getString("dt_txt")
                    val date = dtTxt.split(" ")[0]

                    if (date !in processedDates && items.size < 5) {
                        processedDates.add(date)
                        val fMain = item.getJSONObject("main")
                        val fWeather = item.getJSONArray("weather").getJSONObject(0)
                        items.add(
                            ForecastItem(
                                date = date,
                                tempMin = fMain.getDouble("temp_min"),
                                tempMax = fMain.getDouble("temp_max"),
                                condition = fWeather.getString("main"),
                                icon = fWeather.getString("icon")
                            )
                        )
                    }
                }
                items
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "> forecast_fetch_failed: ${e.message}")
            emptyList()
        }

        return WeatherData(
            temp = temp,
            tempMin = tempMin,
            tempMax = tempMax,
            feelsLike = feelsLike,
            condition = condition,
            icon = icon,
            location = location,
            humidity = humidity,
            windSpeed = windSpeed,
            windDeg = windDeg,
            pressure = pressure,
            visibility = visibility,
            clouds = cloudPercent,
            forecast = forecastItems
        )
    }

    /**
     * Generate mock weather data for when no API key is set.
     */
    private fun getMockWeather(lat: Double, lon: Double): WeatherData {
        return WeatherData(
            temp = 22.5,
            tempMin = 18.0,
            tempMax = 26.0,
            feelsLike = 23.1,
            condition = "Clear",
            icon = "01d",
            location = "UNKNOWN_SECTOR",
            humidity = 45,
            windSpeed = 3.2,
            windDeg = 180,
            pressure = 1013,
            visibility = 10000,
            clouds = 10,
            forecast = listOf(
                ForecastItem("2025-01-01", 17.0, 25.0, "Clear", "01d"),
                ForecastItem("2025-01-02", 16.0, 23.0, "Clouds", "02d"),
                ForecastItem("2025-01-03", 15.0, 21.0, "Rain", "10d"),
                ForecastItem("2025-01-04", 14.0, 20.0, "Clouds", "03d"),
                ForecastItem("2025-01-05", 18.0, 26.0, "Clear", "01d")
            )
        )
    }

    /**
     * Cache weather data to SharedPreferences.
     */
    private fun cacheWeather(prefs: SharedPreferences, data: WeatherData) {
        try {
            val json = JSONObject().apply {
                put("temp", data.temp)
                put("tempMin", data.tempMin)
                put("tempMax", data.tempMax)
                put("feelsLike", data.feelsLike)
                put("condition", data.condition)
                put("icon", data.icon)
                put("location", data.location)
                put("humidity", data.humidity)
                put("windSpeed", data.windSpeed)
                put("windDeg", data.windDeg)
                put("pressure", data.pressure)
                put("visibility", data.visibility)
                put("clouds", data.clouds)

                val forecastArray = JSONArray()
                for (item in data.forecast) {
                    forecastArray.put(JSONObject().apply {
                        put("date", item.date)
                        put("tempMin", item.tempMin)
                        put("tempMax", item.tempMax)
                        put("condition", item.condition)
                        put("icon", item.icon)
                    })
                }
                put("forecast", forecastArray)
            }

            prefs.edit()
                .putString(KEY_CACHED_WEATHER, json.toString())
                .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "> error_caching_weather: ${e.message}")
        }
    }

    /**
     * Get cached weather data.
     */
    private fun getCachedWeather(prefs: SharedPreferences): WeatherData? {
        try {
            val jsonStr = prefs.getString(KEY_CACHED_WEATHER, null) ?: return null
            val json = JSONObject(jsonStr)

            val forecastItems = mutableListOf<ForecastItem>()
            val forecastArray = json.optJSONArray("forecast")
            if (forecastArray != null) {
                for (i in 0 until forecastArray.length()) {
                    val item = forecastArray.getJSONObject(i)
                    forecastItems.add(
                        ForecastItem(
                            date = item.getString("date"),
                            tempMin = item.getDouble("tempMin"),
                            tempMax = item.getDouble("tempMax"),
                            condition = item.getString("condition"),
                            icon = item.getString("icon")
                        )
                    )
                }
            }

            return WeatherData(
                temp = json.getDouble("temp"),
                tempMin = json.getDouble("tempMin"),
                tempMax = json.getDouble("tempMax"),
                feelsLike = json.getDouble("feelsLike"),
                condition = json.getString("condition"),
                icon = json.getString("icon"),
                location = json.getString("location"),
                humidity = json.getInt("humidity"),
                windSpeed = json.getDouble("windSpeed"),
                windDeg = json.getInt("windDeg"),
                pressure = json.getInt("pressure"),
                visibility = json.getInt("visibility"),
                clouds = json.getInt("clouds"),
                forecast = forecastItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "> error_reading_cached_weather: ${e.message}")
            return null
        }
    }

    /**
     * Set the OpenWeatherMap API key.
     */
    fun setApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    /**
     * Get the current temperature unit preference.
     */
    fun getTempUnit(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TEMP_UNIT, "C") ?: "C"
    }

    /**
     * Set the temperature unit preference.
     */
    fun setTempUnit(context: Context, unit: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TEMP_UNIT, unit).apply()
    }

    /**
     * Convert temperature between units.
     */
    fun convertTemp(tempC: Double, targetUnit: String): Double {
        return if (targetUnit == "F") {
            tempC * 9 / 5 + 32
        } else {
            tempC
        }
    }

    /**
     * Get a hacker-styled weather summary for display.
     */
    fun getHackerSummary(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = getCachedWeather(prefs) ?: return "> weather: NO_DATA | run_fetch_first"
        val unit = getTempUnit(context)
        return data.getHackerFormat(unit)
    }

    /**
     * Check if cached data is stale and needs refresh.
     */
    fun isCacheStale(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
        return (System.currentTimeMillis() - lastFetch) > CACHE_DURATION_MS
    }

    /**
     * Clear all cached weather data.
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
