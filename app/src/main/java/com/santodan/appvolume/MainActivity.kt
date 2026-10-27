package com.santodan.appvolume

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    private lateinit var autoToggle: TextView
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var statusAction: TextView
    private lateinit var defaultCard: View
    private lateinit var defaultVolume: TextView
    private lateinit var defaultNote: TextView
    private lateinit var defaultBar: VolumeBarView
    private lateinit var appsLabel: TextView
    private lateinit var appList: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: View
    private lateinit var adapter: AppListAdapter
    private val apps = ArrayList<AppEntry>()

    private var isResumed = false
    private var initialFocusDone = false
    private var loadGeneration = 0
    private var lastServiceEnabled: Boolean? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED,
                -> loadApps()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        configureList()
        configureActions()
        configureDefault()
        registerPackageReceiver()

        refreshStatus()
        refreshDefault()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        refreshStatus()
        adapter.refreshVolumes()
        refreshDefault()
        maybeFocusInitial()
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onDestroy() {
        loadGeneration++
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
        super.onDestroy()
    }

    private fun bindViews() {
        autoToggle = findViewById(R.id.autoToggle)
        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusMessage = findViewById(R.id.statusMessage)
        statusAction = findViewById(R.id.statusAction)
        defaultCard = findViewById(R.id.defaultCard)
        defaultVolume = findViewById(R.id.defaultVolume)
        defaultNote = findViewById(R.id.defaultNote)
        defaultBar = findViewById(R.id.defaultBar)
        appsLabel = findViewById(R.id.appsLabel)
        appList = findViewById(R.id.appList)
        loadingView = findViewById(R.id.loadingView)
        emptyView = findViewById(R.id.emptyView)
    }

    private fun configureList() {
        adapter = AppListAdapter(
            percentProvider = { packageName -> VolumeRules.percentFor(this, packageName) },
            maxVolumeProvider = { AudioLevels.maxVolume(this) },
            currentIndexProvider = { AudioLevels.currentIndex(this) },
            onAdjust = { entry, delta -> adjustVolume(entry, delta) },
            onShowOptions = { entry -> showOptions(entry) },
        )

        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = adapter
        appList.itemAnimator = null
        appList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val gap = (7 * resources.displayMetrics.density).toInt()

            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                outRect.bottom = gap
            }
        })
    }

    private fun configureActions() {
        autoToggle.setOnClickListener {
            val newValue = !VolumeRules.isAutoApplyEnabled(this)
            VolumeRules.setAutoApplyEnabled(this, newValue)
            refreshStatus()
        }

        statusAction.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (error: Throwable) {
                Toast.makeText(
                    this,
                    R.string.toast_accessibility_settings,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun configureDefault() {
        defaultCard.setOnClickListener { showDefaultOptions() }
        defaultCard.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    adjustDefault(-1)
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    adjustDefault(1)
                    true
                }

                else -> false
            }
        }
    }

    private fun refreshDefault() {
        val max = AudioLevels.maxVolume(this).coerceAtLeast(1)
        val percent = VolumeRules.defaultPercent(this)

        if (percent == null) {
            defaultVolume.setText(R.string.row_not_set)
            defaultVolume.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            defaultNote.setText(R.string.default_volume_note_unset)
            defaultBar.setLevel(0f, false)
            defaultCard.contentDescription =
                getString(R.string.default_volume_content_unset)
        } else {
            val index = AudioLevels.percentToIndex(percent, max)
            defaultVolume.text = getString(R.string.row_level, index, max)
            defaultVolume.setTextColor(ContextCompat.getColor(this, R.color.accent))
            defaultNote.setText(R.string.default_volume_note_set)
            defaultBar.setLevel(index / max.toFloat(), true)
            defaultCard.contentDescription =
                getString(R.string.default_volume_content_set, index, max)
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun refreshStatus() {
        val serviceEnabled = AccessibilityStatus.isServiceEnabled(this)
        val autoEnabled = VolumeRules.isAutoApplyEnabled(this)
        val becameEnabled = lastServiceEnabled == false && serviceEnabled
        lastServiceEnabled = serviceEnabled

        autoToggle.isSelected = autoEnabled
        autoToggle.setText(
            if (autoEnabled) R.string.auto_apply_on else R.string.auto_apply_off,
        )

        when {
            !serviceEnabled -> {
                statusDot.setBackgroundResource(R.drawable.dot_amber)
                statusTitle.setText(R.string.status_off_title)
                statusMessage.setText(R.string.status_off_message)
                statusAction.setText(R.string.status_action_enable)
            }

            !autoEnabled -> {
                statusDot.setBackgroundResource(R.drawable.dot_grey)
                statusTitle.setText(R.string.status_paused_title)
                statusMessage.setText(R.string.status_paused_message)
                statusAction.setText(R.string.status_action_settings)
            }

            else -> {
                statusDot.setBackgroundResource(R.drawable.dot_green)
                statusTitle.setText(R.string.status_on_title)
                statusMessage.setText(R.string.status_on_message)
                statusAction.setText(R.string.status_action_settings)
            }
        }

        if (becameEnabled && isResumed && statusAction.isFocused) {
            initialFocusDone = true
            focusFirstRow()
        }
    }

    private fun loadApps() {
        val generation = ++loadGeneration
        if (apps.isEmpty()) {
            loadingView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }

        Thread({
            val loaded = AppRepository.loadLaunchableApps(applicationContext)
            runOnUiThread {
                if (generation != loadGeneration || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                apps.clear()
                apps.addAll(loaded)
                adapter.submitApps(apps)

                appsLabel.text = if (apps.isEmpty()) {
                    getString(R.string.apps_label)
                } else {
                    getString(R.string.apps_label_count, apps.size)
                }
                loadingView.visibility = View.GONE
                emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
                maybeFocusInitial()
            }
        }, "app-list-loader").start()
    }

    private fun maybeFocusInitial() {
        if (initialFocusDone || !isResumed) return

        val serviceEnabled = AccessibilityStatus.isServiceEnabled(this)
        if (serviceEnabled && apps.isEmpty()) return

        initialFocusDone = true
        if (serviceEnabled) {
            focusFirstRow()
        } else {
            statusAction.requestFocus()
        }
    }

    private fun focusFirstRow() {
        appList.post {
            appList.scrollToPosition(0)
            val holder = appList.findViewHolderForAdapterPosition(0)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                appList.post {
                    appList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
    }

    private fun adjustVolume(entry: AppEntry, delta: Int) {
        val max = AudioLevels.maxVolume(this)
        val existingPercent = VolumeRules.percentFor(this, entry.packageName)
        val baseIndex = existingPercent?.let { AudioLevels.percentToIndex(it, max) }
            ?: AudioLevels.currentIndex(this)
        val newIndex = (baseIndex + delta).coerceIn(0, max)
        val newPercent = AudioLevels.indexToPercent(newIndex, max)

        if (existingPercent != newPercent) {
            VolumeRules.setPercent(this, entry.packageName, newPercent)
            adapter.refreshPackage(entry.packageName)
        }
    }

    private fun setVolumeIndex(entry: AppEntry, index: Int) {
        val max = AudioLevels.maxVolume(this)
        val newPercent = AudioLevels.indexToPercent(index.coerceIn(0, max), max)

        if (VolumeRules.percentFor(this, entry.packageName) != newPercent) {
            VolumeRules.setPercent(this, entry.packageName, newPercent)
            adapter.refreshPackage(entry.packageName)
        }
    }

    private fun adjustDefault(delta: Int) {
        val max = AudioLevels.maxVolume(this)
        val existingPercent = VolumeRules.defaultPercent(this)
        val baseIndex = existingPercent?.let { AudioLevels.percentToIndex(it, max) }
            ?: AudioLevels.currentIndex(this)
        setDefaultIndex((baseIndex + delta).coerceIn(0, max))
    }

    private fun setDefaultIndex(index: Int) {
        val max = AudioLevels.maxVolume(this)
        val newPercent = AudioLevels.indexToPercent(index.coerceIn(0, max), max)

        if (VolumeRules.defaultPercent(this) != newPercent) {
            VolumeRules.setDefaultPercent(this, newPercent)
            refreshDefault()
        }
    }

    private fun showOptions(entry: AppEntry) {
        AppVolumeDialog(
            activity = this,
            icon = entry.icon,
            title = entry.label,
            subtitle = entry.packageName,
            targetPackage = entry.packageName,
            label = getString(R.string.dialog_starting_level),
            noteSet = getString(R.string.dialog_note_set),
            noteUnset = getString(R.string.dialog_note_unset),
            percentProvider = { VolumeRules.percentFor(this, entry.packageName) },
            onAdjust = { delta -> adjustVolume(entry, delta) },
            onSetIndex = { index -> setVolumeIndex(entry, index) },
            onClear = {
                VolumeRules.clear(this, entry.packageName)
                adapter.refreshPackage(entry.packageName)
            },
            onOpen = { launchApp(entry) },
        ).show()
    }

    private fun showDefaultOptions() {
        AppVolumeDialog(
            activity = this,
            icon = ContextCompat.getDrawable(this, R.drawable.ic_speaker),
            title = getString(R.string.default_volume_title),
            subtitle = getString(R.string.default_volume_subtitle),
            targetPackage = VolumeRules.defaultTarget(),
            label = getString(R.string.dialog_default_level),
            noteSet = getString(R.string.default_dialog_note_set),
            noteUnset = getString(R.string.default_dialog_note_unset),
            percentProvider = { VolumeRules.defaultPercent(this) },
            onAdjust = { delta -> adjustDefault(delta) },
            onSetIndex = { index -> setDefaultIndex(index) },
            onClear = {
                VolumeRules.clearDefault(this)
                refreshDefault()
            },
            onOpen = null,
        ).show()
    }

    private fun launchApp(entry: AppEntry) {
        if (!AppRepository.launch(this, entry)) {
            Toast.makeText(this, R.string.toast_no_launch_intent, Toast.LENGTH_SHORT).show()
        }
    }
}
