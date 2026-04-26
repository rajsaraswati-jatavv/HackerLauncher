package com.hackerlauncher.livewallpaper

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hackerlauncher.R

class WallpaperSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_settings)

        prefs = getSharedPreferences(HackerWallpaperService.PREFS_NAME, MODE_PRIVATE)

        setupModeSpinner()
        setupColorSpinner()
        setupSpeedSeekBar()
        setupDensitySeekBar()
    }

    private fun setupModeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerMode)
        val modes = listOf("Matrix Rain", "Glitch Effect", "CRT Scanlines", "Particle Network", "Hex Fall")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(prefs.getInt(HackerWallpaperService.KEY_MODE, 0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt(HackerWallpaperService.KEY_MODE, position).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupColorSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerColor)
        val colors = listOf("Green (#00FF00)", "Red (#FF0000)", "Blue (#0080FF)", "Amber (#FFBF00)", "Purple (#8800FF)")
        val colorValues = listOf("#00FF00", "#FF0000", "#0080FF", "#FFBF00", "#8800FF")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colors)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val currentColor = prefs.getString(HackerWallpaperService.KEY_COLOR, "#00FF00")
        spinner.setSelection(colorValues.indexOf(currentColor).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString(HackerWallpaperService.KEY_COLOR, colorValues[position]).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupSpeedSeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.seekBarSpeed)
        val label = findViewById<TextView>(R.id.tvSpeedLabel)
        val speeds = listOf("Slow (24fps)", "Normal (30fps)", "Fast (45fps)", "Ultra (60fps)")
        seekBar.max = 3
        seekBar.progress = prefs.getInt(HackerWallpaperService.KEY_SPEED, 1)
        label.text = speeds[seekBar.progress]
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = speeds[progress]
                prefs.edit().putInt(HackerWallpaperService.KEY_SPEED, progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupDensitySeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.seekBarDensity)
        val label = findViewById<TextView>(R.id.tvDensityLabel)
        val densities = listOf("Low", "Medium", "High")
        seekBar.max = 2
        seekBar.progress = prefs.getInt(HackerWallpaperService.KEY_DENSITY, 1)
        label.text = densities[seekBar.progress]
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = densities[progress]
                prefs.edit().putInt(HackerWallpaperService.KEY_DENSITY, progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
