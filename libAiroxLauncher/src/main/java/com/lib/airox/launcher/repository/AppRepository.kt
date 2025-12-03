package com.lib.airox.launcher.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.core.content.ContextCompat
import com.lib.airox.launcher.R
import com.lib.airox.launcher.model.AppInfo
import com.lib.airox.launcher.model.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing installed applications
 */
class AppRepository(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    private val preferences = LauncherPreferences(context)

    /**
     * Get all installed applications
     */
    suspend fun getAllApps(showSystemApps: Boolean = preferences.showSystemApps): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val apps = mutableListOf<AppInfo>()
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            for (resolveInfo in resolveInfoList) {
                try {
                    val applicationInfo = resolveInfo.activityInfo.applicationInfo
                    val appInfo = AppInfo.fromApplicationInfo(packageManager, applicationInfo)

                    if (showSystemApps || !appInfo.isSystemApp) {
                        apps.add(appInfo)
                    }
                } catch (e: Exception) {
                    // Skip apps that can't be loaded
                    e.printStackTrace()
                }
            }
            apps.add(AppInfo("com.airox.launcher", "Airox Settings", ContextCompat.getDrawable(context,R.drawable.settings), ApplicationInfo(), true))

            // Sort apps
            when (preferences.sortBy) {
                LauncherPreferences.SORT_BY_NAME -> {
                    apps.sortBy { it.name.lowercase() }
                }
                LauncherPreferences.SORT_BY_INSTALL_DATE -> {
                    // Sort by package name as a proxy for install date
                    apps.sortByDescending { it.packageName }
                }
            }



            apps
        }

    /**
     * Get app icon
     */
    fun getAppIcon(packageName: String): android.graphics.drawable.Drawable? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Launch an application
     */
    fun launchApp(appInfo: AppInfo): Boolean {
        return try {

            if(appInfo.name == "Airox Settings"){
                val intent = Intent(context, com.lib.airox.launcher.activity.SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }else {
                val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
            false
        }


    }

    /**
     * Get app count
     */
//    suspend fun getAppCount(showSystemApps: Boolean = preferences.showSystemApps): Int {
//        return getAllApps(showSystemApps).size
//    }
}

