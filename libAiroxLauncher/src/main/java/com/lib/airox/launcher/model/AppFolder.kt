package com.lib.airox.launcher.model

/**
 * Represents a folder containing multiple apps
 */
data class AppFolder(
    val id: String,
    val name: String,
    val apps: MutableList<AppInfo>,
    val page: Int,
    val position: Int
) {
    companion object {
        fun createNew(name: String, page: Int, position: Int): AppFolder {
            return AppFolder(
                id = "folder_${System.currentTimeMillis()}",
                name = name,
                apps = mutableListOf(),
                page = page,
                position = position
            )
        }
    }
}

