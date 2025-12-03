package com.lib.airox.launcher.activity

import android.R
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.gesture.GestureOverlayView
import android.graphics.BitmapFactory
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.compose.SwipeDirection
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.lib.airox.launcher.Utils.toast
import com.lib.airox.launcher.databinding.ActivityLauncherBinding
import com.lib.airox.launcher.fragment.FragmentAppDrawer
import com.lib.airox.launcher.model.AppInfo
import com.lib.airox.launcher.model.AppPosition
import com.lib.airox.launcher.model.LauncherPreferences
import com.lib.airox.launcher.repository.AppRepository
import com.lib.airox.launcher.adapter.HomeScreenPagerAdapter
import com.lib.airox.launcher.fragment.HomeFragment
import kotlinx.coroutines.launch
import java.io.InputStream
import kotlin.math.abs


class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private lateinit var appRepository: AppRepository
    private lateinit var preferences: LauncherPreferences
    private lateinit var pagerAdapter: HomeScreenPagerAdapter
    private var allApps: List<AppInfo> = emptyList()

    private lateinit var launcherRoleRequestLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private val homePages = listOf(
        HomeFragment.newInstance(0),
        HomeFragment.newInstance(1),
        HomeFragment.newInstance(2)
    )

    private var currentPage = 0



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        showPage(0)

        setupWallpaper()



        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Log.d("Permission", "Permission Granted")
            } else {
                Log.d("Permission", "Permission Denied")
                toast("The wallpaper not show without permission")
            }
        }



        permissionLauncher.launch("android.permission.READ_EXTERNAL_STORAGE")

        appRepository = AppRepository(this)
        preferences = LauncherPreferences(this)

        // Register ActivityResultLauncher
        launcherRoleRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            handleLauncherRoleResult(result.resultCode)
        }


        setupViewPager()
        setupPageIndicators()
        loadApps()
        setupClickListeners()
        setupGestureListener()

        if (!isDefaultLauncher(this)) {
            requestDefaultLauncher()
        }

    }

    private fun showPage(page: Int) {
        if (page < 0 || page >= homePages.size) return

        currentPage = page

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(binding.homeContainer.id, homePages[page])
            .commit()

        updatePageIndicators(page)
    }


    private fun setupWallpaper() {
        loadWallpaper()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        window.setFlags(
            LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = TRANSPARENT
            window.navigationBarColor = TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            window.decorView.systemUiVisibility = flags
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.setFlags(
                LayoutParams.FLAG_TRANSLUCENT_STATUS,
                LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
            window.setFlags(
                LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
        }
    }

    private fun loadWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)
            var drawable: Drawable? = null

            // Try the standard drawable first (works on API 16+ in most devices)
            drawable = wm.drawable

            // If null OR live wallpaper → try system wallpaper stream
            if (drawable == null || wm.wallpaperInfo != null) {
                try {
                    val input: InputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.let {
                            ParcelFileDescriptor.AutoCloseInputStream(it)
                        }
                    } else {
                        null
                    }

                    input?.use {
                        val bitmap = BitmapFactory.decodeStream(it)
                        if (bitmap != null) {
                            drawable = BitmapDrawable(resources, bitmap)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (drawable != null) {
                applyWallpaper(drawable!!)
                Log.d("wallpaper", "Wallpaper loaded successfully")
            } else {
                setTransparentBackground()
                Log.e("wallpaper", "Wallpaper NULL - applied transparent fallback")
            }

        } catch (e: Exception) {
            Log.e("wallpaper", "Error: ${e.localizedMessage}")
            setTransparentBackground()
        }
    }

    private fun applyWallpaper(drawable: Drawable) {
        binding.wallpaperBackground.background = drawable
        binding.wallpaperBackground.visibility = View.VISIBLE
        window.setBackgroundDrawable(drawable)
    }

    private fun setTransparentBackground() {
        window.setBackgroundDrawableResource(R.color.transparent)
        binding.wallpaperBackground.background = null
        binding.wallpaperBackground.visibility = View.GONE
    }


    private fun setupViewPager() {
        val numPages = preferences.numPages
        pagerAdapter = HomeScreenPagerAdapter(
            numPages = numPages,
            gridColumns = preferences.gridColumns,
            iconSize = preferences.iconSize,
            preferences = preferences,
            onAppClick = { app -> launchApp(app) },
            onAppLongPress = { app, view -> showAppOptionsMenu(app, view) },
            onAppPositionChanged = { page, fromPos, toPos ->
                saveAppPosition(page, fromPos, toPos)
            },
            onAppMoveToPage = { fromPage, toPage, position ->
                handleAppMoveToPage(fromPage, toPage, position)
            },
            onPageSwitchRequest = { targetPage ->

            },
            onPageLongPress = { page, view -> showAddOptionsMenu(page, view) }
        )


        // Ensure ViewPager is enabled by default for normal page switching and drag operations

        pageChangeCallback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicators(position)
            }
        }
        pageChangeCallback?.let {
        }
    }

    private fun setupPageIndicators() {
        val numPages = preferences.numPages
        binding.pageIndicators.removeAllViews()

        val indicatorSizePx = (8 * resources.displayMetrics.density).toInt()

        for (i in 0 until numPages) {
            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    indicatorSizePx,
                    indicatorSizePx
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                background = createIndicatorDrawable(false)
            }
            binding.pageIndicators.addView(indicator)
        }
        updatePageIndicators(0)
    }

    private fun createIndicatorDrawable(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (selected) ContextCompat.getColor(this@LauncherActivity, R.color.white)
                else ContextCompat.getColor(this@LauncherActivity, R.color.darker_gray)
            )
        }
    }

    private fun updatePageIndicators(currentPage: Int) {
        for (i in 0 until binding.pageIndicators.childCount) {
            val indicator = binding.pageIndicators.getChildAt(i)
            indicator.background = createIndicatorDrawable(i == currentPage)
        }
    }

    private fun setupClickListeners() {

        binding.appDrawerButton.setOnClickListener {
            openAppDrawer()
            hideBottomBar()
        }

        binding.homeButton.setOnClickListener {
            hideBottomBar()
        }

        binding.wallpaperButton.setOnClickListener {
            openWallpaperPicker()
            hideBottomBar()
        }
    }

    private fun toggleBottomBar() {
        if (binding.bottomBar.visibility == View.VISIBLE) {
            hideBottomBar()
        } else {
            showBottomBar()
        }
    }

    private fun showBottomBar() {
        binding.bottomBar.visibility = View.VISIBLE
        binding.bottomBar.alpha = 0f
        binding.bottomBar.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideBottomBar() {
        binding.bottomBar.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.bottomBar.visibility = View.GONE
            }
            .start()
    }


    private var startX = 0f
    private var startY = 0f
    private val SWIPE_THRESHOLD = 80f

    private fun setupGestureListener() {
        binding.homeContainer.setOnTouchListener { _, e ->
            when (e.action) {

                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    false
                }

                MotionEvent.ACTION_UP -> {
                    val diffX = e.x - startX
                    val diffY = e.y - startY

                    val absDX = abs(diffX)
                    val absDY = abs(diffY)

                    val screenHeight = resources.displayMetrics.heightPixels
                    val bottomZone = screenHeight * 0.85f // bottom 15% area

                    val isSwipeUp =
                        absDY > absDX && diffY < 0 && absDY > SWIPE_THRESHOLD && startY > bottomZone

                    val isSwipeLeft =
                        absDX > absDY && diffX < 0 && absDX > SWIPE_THRESHOLD

                    val isSwipeRight =
                        absDX > absDY && diffX > 0 && absDX > SWIPE_THRESHOLD

                    if (isSwipeUp) {
                        showAppDrawer()
                        return@setOnTouchListener true
                    }

                    if (isSwipeLeft) {
                        showPage(currentPage + 1)
                        return@setOnTouchListener true
                    }

                    if (isSwipeRight) {
                        showPage(currentPage - 1)
                        return@setOnTouchListener true
                    }

                    false
                }

                else -> false
            }
        }
    }


    private fun isAppDrawerVisible(): Boolean {
        val fragment = supportFragmentManager.findFragmentByTag("AppDrawer")
        return fragment != null && fragment.isVisible
    }

    private fun loadApps() {
        lifecycleScope.launch {
            allApps = appRepository.getAllApps(preferences.showSystemApps)
            distributeAppsToPages()
        }
    }

    private fun distributeAppsToPages() {
        val numPages = preferences.numPages
        val gridColumns = preferences.gridColumns
        val gridRows = 5
        val appsPerPage = gridColumns * gridRows

        // Load saved positions
        val savedPositions = preferences.loadAppPositions()
        val pageApps = mutableMapOf<Int, MutableList<AppInfo>>()

        // Initialize pages
        for (i in 0 until numPages) {
            pageApps[i] = mutableListOf()
        }

        // Distribute apps based on saved positions
        val appsWithPositions = mutableListOf<Pair<AppInfo, AppPosition?>>()
        val appsWithoutPositions = mutableListOf<AppInfo>()

        for (app in allApps) {
            val position = savedPositions.firstOrNull { it.packageName == app.packageName }
            if (position != null && position.page < numPages) {
                appsWithPositions.add(Pair(app, position))
            } else {
                appsWithoutPositions.add(app)
            }
        }

        // Add apps with saved positions
        for ((app, position) in appsWithPositions) {
            if (position != null) {
                val page = position.page
                val pos = position.position
                val pageList = pageApps[page] ?: mutableListOf()
                // Ensure position is within bounds
                val insertPos = pos.coerceIn(0, pageList.size)
                pageList.add(insertPos, app)
                pageApps[page] = pageList
            }
        }

        // Distribute remaining apps to pages
        var currentPage = 0
        for (app in appsWithoutPositions) {
            while (currentPage < numPages &&
                (pageApps[currentPage]?.size ?: 0) >= appsPerPage
            ) {
                currentPage++
            }
            if (currentPage < numPages) {
                pageApps[currentPage]?.add(app)
            }
        }

        // Update adapter with apps for each page
        for (page in 0 until numPages) {
            pagerAdapter.setAppsForPage(page, pageApps[page] ?: emptyList())
        }
    }

    private fun saveAppPosition(page: Int, fromPos: Int, toPos: Int) {
        val apps = pagerAdapter.getAppsForPage(page)
        if (fromPos < apps.size && toPos < apps.size) {
            val app = apps[fromPos]
            val position = AppPosition(app.packageName, page, toPos)
            preferences.saveAppPosition(position)
        }
    }

    private fun handleAppMoveToPage(fromPage: Int, toPage: Int, position: Int) {
        val apps = pagerAdapter.getAppsForPage(toPage)
        if (position < apps.size) {
            val app = apps[position]
            val appPosition = AppPosition(app.packageName, toPage, position)
            preferences.saveAppPosition(appPosition)
        }
    }


    private fun showAppOptionsMenu(app: AppInfo, anchorView: View) {
        val popupMenu = android.widget.PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(
            com.lib.airox.launcher.R.menu.app_options_menu,
            popupMenu.menu
        )

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.lib.airox.launcher.R.id.menu_move_to_page -> {
                    showMoveToPageDialog(app)
                    true
                }

                com.lib.airox.launcher.R.id.menu_create_folder -> {
                    showCreateFolderDialog(app)
                    true
                }

                com.lib.airox.launcher.R.id.menu_app_info -> {
                    showAppInfo(app)
                    true
                }

                com.lib.airox.launcher.R.id.menu_remove_from_home -> {
                    removeAppFromHome(app)
                    true
                }

                com.lib.airox.launcher.R.id.menu_uninstall -> {
                    uninstallApp(app)
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun removeAppFromHome(app: AppInfo) {
        val savedPositions = preferences.loadAppPositions()
        val currentPos = savedPositions.firstOrNull { it.packageName == app.packageName }

        if (currentPos != null) {
            pagerAdapter.removeAppFromPage(currentPos.page, currentPos.position)
            preferences.removeAppPosition(app.packageName)
            Toast.makeText(this, "${app.name} removed from home screen", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "App not found on home screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMoveToPageDialog(app: AppInfo) {
        val pages = (0 until preferences.numPages).toList()
        val pageNames = pages.map { "Page ${it + 1}" }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Move ${app.name} to:")
            .setItems(pageNames) { _, which ->
                moveAppToPage(app, which)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveAppToPage(app: AppInfo, targetPage: Int) {
        // Find current page and position
        val savedPositions = preferences.loadAppPositions()
        val currentPos = savedPositions.firstOrNull { it.packageName == app.packageName }

        if (currentPos != null) {
            // Remove from current page
            pagerAdapter.removeAppFromPage(currentPos.page, currentPos.position)
            preferences.removeAppPosition(app.packageName)
        }

        // Add to target page
        val targetPageApps = pagerAdapter.getAppsForPage(targetPage)
        val newPosition = targetPageApps.size
        pagerAdapter.addAppToPage(targetPage, app, newPosition)

        // Save new position
        val newPos = AppPosition(app.packageName, targetPage, newPosition)
        preferences.saveAppPosition(newPos)

        // Switch to target page
    }

    private fun showCreateFolderDialog(app: AppInfo) {
        val input = android.widget.EditText(this)
        input.hint = "Folder name"

        android.app.AlertDialog.Builder(this)
            .setTitle("Create Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val folderName = input.text.toString().takeIf { it.isNotBlank() } ?: "New Folder"
                createFolderWithApp(app, folderName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolderWithApp(app: AppInfo, folderName: String) {
        // Find current position
        val savedPositions = preferences.loadAppPositions()
        val currentPos = savedPositions.firstOrNull { it.packageName == app.packageName }

        if (currentPos != null) {
            // TODO: Implement folder creation
            Toast.makeText(this, "Folder feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppInfo(app: AppInfo) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${app.packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open app info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = android.net.Uri.parse("package:${app.packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot uninstall app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(app: AppInfo) {
        appRepository.launchApp(app)
    }

    private fun showAddOptionsMenu(page: Int, anchorView: View) {
        val popupMenu = android.widget.PopupMenu(this, anchorView)
        popupMenu.menu.add(0, 1, 0, "Add Widget")
        popupMenu.menu.add(0, 2, 0, "Add App")
        popupMenu.menu.add(0, 3, 0, "Wallpaper")

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showWidgetPicker(page)
                    true
                }

                2 -> {
                    showAppPicker(page)
                    true
                }

                3 -> {
                    openWallpaperPicker()
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showWidgetPicker(page: Int) {
        try {
            val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK)
            intent.putExtra(
                android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
            )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Widget picker not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppPicker(page: Int) {
        showAppDrawer()
        Toast.makeText(
            this,
            "Select an app from drawer to add to page ${page + 1}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showAppDrawer() {
        if (isAppDrawerVisible()) return

        val fragment = FragmentAppDrawer()
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.fragmentContainer.bringToFront()

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                com.lib.airox.launcher.R.anim.slide_in_bottom,
                R.anim.fade_out,
                R.anim.fade_in,
                com.lib.airox.launcher.R.anim.slide_out_bottom
            )
            .add(binding.fragmentContainer.id, fragment, "AppDrawer")
            .commit()
    }


    private fun hideAppDrawer() {
        val fragment = supportFragmentManager.findFragmentByTag("AppDrawer")
        if (fragment != null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.fade_in,
                    com.lib.airox.launcher.R.anim.slide_out_bottom
                )
                .remove(fragment)
                .commit()
        }
        binding.fragmentContainer.visibility = View.GONE
    }

    fun hideAppDrawerFragment() {
        hideAppDrawer() // remove fragment + hide view
    }


    private fun openAppDrawer() {
        showAppDrawer()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openWallpaperPicker() {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            val chooser = Intent.createChooser(intent, "Select Wallpaper")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open wallpaper picker", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        loadWallpaper()
    }

    override fun onDestroy() {
        super.onDestroy()
        pageChangeCallback?.let {
        }
        pageChangeCallback = null
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val isLauncher = roleManager.isRoleHeld(RoleManager.ROLE_HOME)

            if (!isLauncher) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                launcherRoleRequestLauncher.launch(intent)
            }
        } else {
            requestDefaultLauncherLegacy()
        }
    }

    private fun requestDefaultLauncherLegacy() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    private fun handleLauncherRoleResult(resultCode: Int) {
        when (resultCode) {
            RESULT_OK -> {
                Toast.makeText(this, "Launcher set as default!", Toast.LENGTH_SHORT).show()
            }

            RESULT_CANCELED -> {
                Toast.makeText(this, "Denied by user!", Toast.LENGTH_SHORT).show()
            }

            else -> {
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val bottomZone = screenHeight * 0.85f // bottom 15%

        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (ev.y > bottomZone && !isAppDrawerVisible()) {
                // Temporarily disable ViewPager scroll
            }
        }

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
        }

        return super.dispatchTouchEvent(ev)
    }


}
