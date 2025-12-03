package com.lib.airox.launcher.fragment

import android.R.attr.startY
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.lib.airox.launcher.activity.LauncherActivity
import com.lib.airox.launcher.databinding.FragmentAppDrawerBinding
import com.lib.airox.launcher.model.AppInfo
import com.lib.airox.launcher.model.LauncherPreferences
import com.lib.airox.launcher.repository.AppRepository
import com.lib.airox.launcher.adapter.AppAdapter
import kotlinx.coroutines.launch
import kotlin.math.abs


interface AppDrawerFragment {
    fun exitDrawer()
}

class FragmentAppDrawer : Fragment() {

    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!
    private lateinit var appRepository: AppRepository
    private lateinit var preferences: LauncherPreferences
    private lateinit var appAdapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appRepository = AppRepository(requireContext())
        preferences = LauncherPreferences(requireContext())

        setupViews()
        loadApps()
        setupClickListeners()
        setupSearch()
        setupSwipeGesture()
    }

    private fun setupSwipeGesture() {
        val gestureDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 != null) {
                        val deltaY = e2.y - e1.y
                        val deltaX = kotlin.math.abs(e2.x - e1.x)

                        // Swipe DOWN to close drawer
                        if (deltaY > 120 && kotlin.math.abs(velocityY) > 500 && deltaX < 200) {
                            exitDrawer()
                            return true
                        }
                    }
                    return false
                }
            })

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        binding.appsRecyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun exitDrawer() {
        parentFragmentManager.beginTransaction()
            .remove(this)
            .commit()

        // Hide fragment container in parent activity
        val activity = requireActivity()
        if (activity is com.lib.airox.launcher.activity.LauncherActivity) {
            activity.hideAppDrawerFragment()
        }
    }

    private fun setupViews() {
        binding.appsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 4)

        appAdapter = AppAdapter(
            apps = filteredApps,
            onAppClick = { app -> launchApp(app) },
            iconSize = preferences.iconSize
        )
        binding.appsRecyclerView.adapter = appAdapter
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            exitDrawer()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterApps(query: String) {
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        appAdapter = AppAdapter(
            apps = filteredApps,
            onAppClick = { app -> launchApp(app) },
            iconSize = preferences.iconSize
        )
        binding.appsRecyclerView.adapter = appAdapter
    }

    private fun loadApps() {
        lifecycleScope.launch {
            allApps = appRepository.getAllApps(preferences.showSystemApps)
            filteredApps = allApps
            appAdapter = AppAdapter(
                apps = filteredApps,
                onAppClick = { app -> launchApp(app) },
                iconSize = preferences.iconSize
            )
            binding.appsRecyclerView.adapter = appAdapter
        }
    }

    private fun launchApp(app: AppInfo) {
        appRepository.launchApp(app)
        exitDrawer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

    }
}