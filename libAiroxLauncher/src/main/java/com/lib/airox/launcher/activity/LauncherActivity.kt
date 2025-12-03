package com.lib.airox.launcher.activity

import android.R
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.lib.airox.launcher.databinding.ActivityLauncherBinding
import com.lib.airox.launcher.fragment.FragmentAppDrawer
import com.lib.airox.launcher.model.AppInfo
import com.lib.airox.launcher.model.AppPosition
import com.lib.airox.launcher.model.LauncherPreferences
import com.lib.airox.launcher.repository.AppRepository
import com.lib.airox.launcher.ui.adapter.HomeScreenPagerAdapter
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


    private var pageChangeCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? =
        null

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

        setupWallpaper()



        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Log.d("Permission", "Permission Granted")
            } else {
                Log.d("Permission", "Permission Denied")
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
        setupGestureDetector()

        if (!isDefaultLauncher(this)) {
            requestDefaultLauncher()
        }


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
            val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)

            if (pfd != null) {
                val inputStream: InputStream =
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ParcelFileDescriptor.AutoCloseInputStream(pfd)
                    } else {
                        pfd
                    }) as InputStream

                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(resources, bitmap)
                    applyWallpaper(drawable)
                    Log.d("wallpaper", "Loaded via FLAG_SYSTEM - bitmap OK")
                    return
                }
            }

            val fallback = wm.drawable
            if (fallback != null) {
                applyWallpaper(fallback)
                Log.d("wallpaper", "Loaded via drawable fallback")
                return
            }

            setTransparentBackground()

        } catch (e: Exception) {
            Log.e("wallpaper", "Error: ${e.localizedMessage}")
            setTransparentBackground()
        }
    }

    private fun applyWallpaper(drawable: Drawable) {
        binding.wallpaperBackground.setImageDrawable(drawable)
        binding.wallpaperBackground.visibility = View.VISIBLE
        window.setBackgroundDrawable(drawable)
    }

    private fun setTransparentBackground() {
        window.setBackgroundDrawableResource(R.color.transparent)
        binding.wallpaperBackground.setImageDrawable(null)
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
                val currentPage = binding.homeViewPager.currentItem
                if (currentPage != targetPage) {
                    binding.homeViewPager.currentItem = targetPage
                }
            },
            onPageLongPress = { page, view -> showAddOptionsMenu(page, view) }
        )

        binding.homeViewPager.adapter = pagerAdapter
        
        // Ensure ViewPager is enabled by default for normal page switching and drag operations
        binding.homeViewPager.isUserInputEnabled = true

        pageChangeCallback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicators(position)
            }
        }
        pageChangeCallback?.let {
            binding.homeViewPager.registerOnPageChangeCallback(it)
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
                if (selected) ContextCompat.getColor(this@LauncherActivity, android.R.color.white)
                else ContextCompat.getColor(this@LauncherActivity, android.R.color.darker_gray)
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
            binding.homeViewPager.currentItem = 0
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

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isVerticalSwipe = false
    private var viewPagerInputDisabled = false
    private var touchStartTime = 0L
    private var isDragOperation = false

    private fun setupGestureDetector() {
        val gestureDetector =
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 != null && !isAppDrawerVisible() && !isDragOperation) {
                        val deltaY = e1.y - e2.y
                        val screenHeight = resources.displayMetrics.heightPixels
                        val bottomAreaHeight = screenHeight * 0.82f // bottom 18% area (82% from top)

                        // Check if gesture started from bottom area and is upward swipe
                        if (e1.y > bottomAreaHeight &&
                            deltaY > 100 &&
                            abs(velocityY) > 500
                        ) {
                            showAppDrawer()
                            return true  // Consume the event
                        }
                    }
                    return false
                }
            })

        // Handle swipe down to hide drawer
        val hideGestureDetector =
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 != null && isAppDrawerVisible()) {
                        val deltaY = e1.y - e2.y
                        // Swipe down to hide drawer
                        if (deltaY < -100 && abs(velocityY) > 500) {
                            hideAppDrawer()
                            return true
                        }
                    }
                    return false
                }
            })

        // Intercept touches on ViewPager to detect vertical swipes early
        // But allow long press and drag operations to work normally
        binding.homeViewPager.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartTime = System.currentTimeMillis()
                    isVerticalSwipe = false
                    isDragOperation = false
                    viewPagerInputDisabled = false
                    gestureDetector.onTouchEvent(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.x - touchStartX)
                    val deltaY = abs(event.y - touchStartY)
                    val screenHeight = resources.displayMetrics.heightPixels
                    val bottomAreaHeight = screenHeight * 0.82f
                    val touchDuration = System.currentTimeMillis() - touchStartTime

                    // If significant movement detected, it's likely a drag operation
                    // Allow drag operations (ItemTouchHelper) to work normally
                    if (deltaX > 30 || deltaY > 30) {
                        isDragOperation = true
                        isVerticalSwipe = false
                        // Re-enable ViewPager to allow cross-page dragging
                        if (viewPagerInputDisabled) {
                            binding.homeViewPager.isUserInputEnabled = true
                            viewPagerInputDisabled = false
                        }
                        // Don't consume - let drag operation handle it
                        return@setOnTouchListener false
                    }

                    // If long press detected (touch duration > 500ms), don't interfere
                    if (touchDuration > 500) {
                        isVerticalSwipe = false
                        if (viewPagerInputDisabled) {
                            binding.homeViewPager.isUserInputEnabled = true
                            viewPagerInputDisabled = false
                        }
                        return@setOnTouchListener false // Let long press work
                    }

                    // Detect vertical swipe early (before ViewPager processes it)
                    // Only if not a drag or long press
                    if (!isDragOperation && deltaY > deltaX && deltaY > 50 && !isAppDrawerVisible()) {
                        // Check if swipe up from bottom area
                        if (touchStartY > bottomAreaHeight && event.y < touchStartY) {
                            isVerticalSwipe = true
                            // Disable ViewPager input to prevent page switching
                            if (!viewPagerInputDisabled) {
                                binding.homeViewPager.isUserInputEnabled = false
                                viewPagerInputDisabled = true
                            }
                            gestureDetector.onTouchEvent(event)
                            return@setOnTouchListener true // Consume event - prevent ViewPager from handling
                        }
                    } else if (deltaX > deltaY && deltaX > 50) {
                        // Horizontal swipe detected - enable ViewPager
                        isVerticalSwipe = false
                        if (viewPagerInputDisabled) {
                            binding.homeViewPager.isUserInputEnabled = true
                            viewPagerInputDisabled = false
                        }
                    }
                    gestureDetector.onTouchEvent(event)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gestureDetector.onTouchEvent(event)
                    // Re-enable ViewPager input
                    if (viewPagerInputDisabled) {
                        binding.homeViewPager.isUserInputEnabled = true
                        viewPagerInputDisabled = false
                    }
                    isVerticalSwipe = false
                    isDragOperation = false
                }
            }
            // Return true to consume vertical swipes, false for horizontal, drag, and long press
            isVerticalSwipe && !isDragOperation
        }

        // Handle gestures on fragment container when drawer is visible
        binding.fragmentContainer.setOnTouchListener { _, event ->
            if (isAppDrawerVisible()) {
                hideGestureDetector.onTouchEvent(event)
            }
            false
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
        binding.homeViewPager.currentItem = targetPage
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
            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE)
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
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Widget picker not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showAppPicker(page: Int) {
        showAppDrawer()
        Toast.makeText(this, "Select an app from drawer to add to page ${page + 1}", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAppDrawer() {
        if (isAppDrawerVisible()) {
            return // Already visible
        }

        val fragment = FragmentAppDrawer()

        // Make fragment container visible and bring to front
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.fragmentContainer.bringToFront()

        try {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    com.lib.airox.launcher.R.anim.slide_in_bottom,
                    R.anim.fade_out,
                    R.anim.fade_in,
                    com.lib.airox.launcher.R.anim.slide_out_bottom
                )
                .add(binding.fragmentContainer.id, fragment, "AppDrawer")
                .commit()
        } catch (e: Exception) {
            // Fallback: try without state loss
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    com.lib.airox.launcher.R.anim.slide_in_bottom,
                    R.anim.fade_out
                )
                .add(binding.fragmentContainer.id, fragment, "AppDrawer")
                .commitAllowingStateLoss()
        }
    }

    private fun hideAppDrawer() {
        val fragment = supportFragmentManager.findFragmentByTag("AppDrawer")
        if (fragment != null) {
            try {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        com.lib.airox.launcher.R.anim.slide_out_bottom
                    )
                    .remove(fragment)
                    .commit()
            } catch (e: Exception) {
                supportFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss()
            }

            binding.fragmentContainer.visibility = View.GONE
        }
    }

    fun hideAppDrawerFragment() {
        binding.fragmentContainer.visibility = View.GONE
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
            binding.homeViewPager.unregisterOnPageChangeCallback(it)
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


}
