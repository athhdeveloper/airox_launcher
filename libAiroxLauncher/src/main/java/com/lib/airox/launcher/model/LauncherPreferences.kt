package com.lib.airox.launcher.model

import android.content.Context
import android.content.SharedPreferences

class LauncherPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "airox_launcher_prefs"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_APP_POSITIONS = "app_positions"
        private const val KEY_NUM_PAGES = "num_pages"

        const val SORT_BY_NAME = "name"
        const val SORT_BY_INSTALL_DATE = "install_date"
        const val DEFAULT_NUM_PAGES = 3
    }

    var showSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, value).apply()

    var sortBy: String
        get() = prefs.getString(KEY_SORT_BY, SORT_BY_NAME) ?: SORT_BY_NAME
        set(value) = prefs.edit().putString(KEY_SORT_BY, value).apply()

    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 4)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value).apply()

    var iconSize: Int
        get() = prefs.getInt(KEY_ICON_SIZE, 80)
        set(value) = prefs.edit().putInt(KEY_ICON_SIZE, value).apply()

    var numPages: Int
        get() = prefs.getInt(KEY_NUM_PAGES, DEFAULT_NUM_PAGES)
        set(value) = prefs.edit().putInt(KEY_NUM_PAGES, value).apply()

    fun saveAppPositions(positions: List<AppPosition>) {
        val positionsString = positions.joinToString("|") { it.toString() }
        prefs.edit().putString(KEY_APP_POSITIONS, positionsString).apply()
    }

    fun loadAppPositions(): List<AppPosition> {
        val positionsString = prefs.getString(KEY_APP_POSITIONS, null) ?: return emptyList()
        return positionsString.split("|")
            .mapNotNull { AppPosition.fromString(it) }
    }

    fun getAppPosition(packageName: String): AppPosition? {
        return loadAppPositions().firstOrNull { it.packageName == packageName }
    }

    fun saveAppPosition(position: AppPosition) {
        val positions = loadAppPositions().toMutableList()
        positions.removeAll { it.packageName == position.packageName }
        positions.add(position)
        saveAppPositions(positions)
    }

    fun removeAppPosition(packageName: String) {
        val positions = loadAppPositions().toMutableList()
        positions.removeAll { it.packageName == packageName }
        saveAppPositions(positions)
    }
}

