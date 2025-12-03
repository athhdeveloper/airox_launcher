package com.lib.airox.launcher.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lib.airox.launcher.databinding.ActivitySettingsBinding
import com.lib.airox.launcher.model.LauncherPreferences

/**
 * Settings activity for launcher preferences
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: LauncherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = LauncherPreferences(this)

        loadPreferences()
        setupClickListeners()
    }

    private fun loadPreferences() {
        binding.showSystemAppsSwitch.isChecked = preferences.showSystemApps

        when (preferences.sortBy) {
            LauncherPreferences.SORT_BY_NAME -> binding.sortByNameRadio.isChecked = true
            LauncherPreferences.SORT_BY_INSTALL_DATE -> binding.sortByDateRadio.isChecked = true
        }

        val columns = preferences.gridColumns
        binding.gridColumnsSeekbar.progress = columns - 3
        updateGridColumnsText(columns)
    }


    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.showSystemAppsSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.showSystemApps = isChecked
        }

        binding.sortByNameRadio.setOnClickListener {
            preferences.sortBy = LauncherPreferences.SORT_BY_NAME
        }

        binding.sortByDateRadio.setOnClickListener {
            preferences.sortBy = LauncherPreferences.SORT_BY_INSTALL_DATE
        }

        binding.gridColumnsSeekbar.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: android.widget.SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) {
                if (fromUser) {
                    val columns = progress + 3
                    preferences.gridColumns = columns
                    updateGridColumnsText(columns)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun updateGridColumnsText(columns: Int) {
        binding.gridColumnsValue.text = "$columns columns"
    }
}
