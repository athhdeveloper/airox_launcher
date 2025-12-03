package com.lib.airox.launcher.model

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val applicationInfo: ApplicationInfo,
    val isSystemApp: Boolean = false
) {
    companion object {
        fun fromApplicationInfo(
            pm: PackageManager,
            applicationInfo: ApplicationInfo
        ): AppInfo {
            return AppInfo(
                packageName = applicationInfo.packageName,
                name = pm.getApplicationLabel(applicationInfo).toString(),
                icon = pm.getApplicationIcon(applicationInfo),
                applicationInfo = applicationInfo,
                isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
    }
}

