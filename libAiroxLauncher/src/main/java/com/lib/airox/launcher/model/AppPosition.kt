package com.lib.airox.launcher.model

/**
 * Represents the position of an app on a home screen
 */
data class AppPosition(
    val packageName: String,
    val page: Int,
    val position: Int // Position in the grid (0-based index)
) {
    companion object {
        fun fromString(str: String): AppPosition? {
            return try {
                val parts = str.split(":")
                if (parts.size == 3) {
                    AppPosition(
                        packageName = parts[0],
                        page = parts[1].toInt(),
                        position = parts[2].toInt()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun toString(): String = "$packageName:$page:$position"
}

