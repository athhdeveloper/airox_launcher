package com.lib.airox.launcher.adapter

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lib.airox.launcher.databinding.PageHomeScreenBinding
import com.lib.airox.launcher.model.AppInfo
import com.lib.airox.launcher.model.LauncherPreferences

class HomeScreenPagerAdapter(
    private val numPages: Int,
    private val gridColumns: Int,
    private val iconSize: Int,
    private val preferences: LauncherPreferences,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongPress: (AppInfo, View) -> Unit,
    private val onAppPositionChanged: (Int, Int, Int) -> Unit,
    private val onAppMoveToPage: (Int, Int, Int) -> Unit,
    private val onPageSwitchRequest: (Int) -> Unit,
    private val onPageLongPress: ((Int, View) -> Unit)? = null,
) : RecyclerView.Adapter<HomeScreenPagerAdapter.PageViewHolder>() {

    private val pageAdapters = mutableMapOf<Int, DraggableAppGridAdapter>()
    private val pageApps = mutableMapOf<Int, MutableList<AppInfo>>()
    private val itemTouchHelpers = mutableMapOf<Int, ItemTouchHelper>()

    init {
        for (i in 0 until numPages) {
            pageApps[i] = mutableListOf()
        }
    }

    class PageViewHolder(val binding: PageHomeScreenBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = PageHomeScreenBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = position
        val layoutManager = GridLayoutManager(holder.itemView.context, gridColumns)
        holder.binding.appsRecyclerView.layoutManager = layoutManager

        val apps = pageApps[page] ?: mutableListOf()
        val adapter = DraggableAppGridAdapter(
            apps = apps.toMutableList(),
            onAppClick = onAppClick,
            onAppLongPress = onAppLongPress,
            onAppMove = { fromPos, toPos ->
                onAppPositionChanged(page, fromPos, toPos)
            },
            iconSize = iconSize
        )

        holder.binding.appsRecyclerView.adapter = adapter
        pageAdapters[page] = adapter

        // Add long press listener on empty areas of the page
        holder.binding.root.setOnLongClickListener {
            // Get the current page and show add options
            onPageLongPress?.invoke(page, holder.binding.root)
            true
        }

        // Setup drag and drop with cross-page support
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            private var dragStartX = 0f
            private var dragStartY = 0f

            private var draggedFromPosition = -1

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // Store initial drag position
                if (draggedFromPosition == -1) {
                    draggedFromPosition = fromPosition
                }

                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Disable long press during drag
                    adapter.setDragging(true)
                    viewHolder?.itemView?.let {
                        it.alpha = 0.5f
                        it.elevation = 8f
                        dragStartX = it.x
                        dragStartY = it.y
                    }
                    // Store initial position when drag starts
                    if (viewHolder != null && draggedFromPosition == -1) {
                        draggedFromPosition = viewHolder.adapterPosition
                    }
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    // Re-enable long press when drag ends
                    adapter.setDragging(false)
                    draggedFromPosition = -1
                }
            }

            private var lastPageSwitchTime = 0L
            private var targetPageForDrag: Int? = null

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )

                if (isCurrentlyActive && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    val view = viewHolder.itemView
                    val screenWidth = recyclerView.width
                    val edgeThreshold = 80f // pixels from edge to trigger page switch
                    val currentTime = System.currentTimeMillis()

                    // Check if dragging near left edge (switch to previous page)
                    if (view.x + dX < edgeThreshold && page > 0) {
                        val targetPage = page - 1
                        // Throttle page switches to avoid rapid switching
                        if (targetPageForDrag != targetPage && currentTime - lastPageSwitchTime > 300) {
                            targetPageForDrag = targetPage
                            lastPageSwitchTime = currentTime
                            onPageSwitchRequest(targetPage)
                        }
                    }
                    // Check if dragging near right edge (switch to next page)
                    else if (view.x + view.width + dX > screenWidth - edgeThreshold && page < numPages - 1) {
                        val targetPage = page + 1
                        // Throttle page switches to avoid rapid switching
                        if (targetPageForDrag != targetPage && currentTime - lastPageSwitchTime > 300) {
                            targetPageForDrag = targetPage
                            lastPageSwitchTime = currentTime
                            onPageSwitchRequest(targetPage)
                        }
                    } else {
                        // Reset target page if not near edge
                        targetPageForDrag = null
                    }
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.elevation = 0f
                adapter.setDragging(false)


                if (targetPageForDrag != null && targetPageForDrag != page && draggedFromPosition >= 0) {
                    val app = adapter.getAppAt(draggedFromPosition)
                    if (app != null) {

                        val targetPage = targetPageForDrag!!
                        val targetPageApps = pageApps[targetPage] ?: mutableListOf()
                        val insertPosition = targetPageApps.size


                        adapter.removeApp(draggedFromPosition)


                        addAppToPage(targetPage, app, insertPosition)


                        onAppMoveToPage(page, targetPage, insertPosition)
                    }
                }

                targetPageForDrag = null
                draggedFromPosition = -1
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                // Allow dragging in all directions
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                    0
                )
            }
        }

        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(holder.binding.appsRecyclerView)
        itemTouchHelpers[page] = itemTouchHelper
    }

    override fun getItemCount(): Int = numPages

    fun setAppsForPage(page: Int, apps: List<AppInfo>) {
        pageApps[page] = apps.toMutableList()
        pageAdapters[page]?.updateApps(apps)
    }

    fun getAppsForPage(page: Int): List<AppInfo> = pageApps[page] ?: emptyList()

    fun addAppToPage(page: Int, app: AppInfo, position: Int = -1) {
        val pageList = pageApps[page] ?: mutableListOf()
        val insertPos = if (position == -1) pageList.size else position.coerceIn(0, pageList.size)
        pageList.add(insertPos, app)
        pageApps[page] = pageList
        pageAdapters[page]?.addApp(app, insertPos)
    }

    fun removeAppFromPage(page: Int, position: Int): AppInfo? {
        val pageList = pageApps[page] ?: mutableListOf()
        if (position in 0 until pageList.size) {
            pageList.removeAt(position)
            pageApps[page] = pageList
            return pageAdapters[page]?.removeApp(position)
        }
        return null
    }

    fun moveAppBetweenPages(fromPage: Int, toPage: Int, fromPosition: Int, toPosition: Int) {
        val app = removeAppFromPage(fromPage, fromPosition)
        app?.let {
            addAppToPage(toPage, it, toPosition)
            onAppMoveToPage(fromPage, toPage, toPosition)
        }
    }

    fun refreshPage(page: Int) {
        pageAdapters[page]?.notifyDataSetChanged()
    }
}
