package com.santodan.appvolume

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Focus-friendly editor for a per-app starting level or the global default
 * level. Quick actions mirror the remote: fine steps, +/-10, and min/max.
 */
class AppVolumeDialog(
    private val activity: Activity,
    icon: Drawable?,
    title: String,
    subtitle: String,
    private val targetPackage: String?,
    label: String,
    private val noteSet: String,
    private val noteUnset: String,
    private val percentProvider: () -> Int?,
    private val onAdjust: (Int) -> Unit,
    private val onSetIndex: (Int) -> Unit,
    private val onClear: () -> Unit,
    private val onOpen: (() -> Unit)?,
) {

    private val dialog = Dialog(activity, R.style.Theme_AppVolume_Dialog)
    private val content: View = LayoutInflater.from(activity)
        .inflate(R.layout.dialog_app_volume, null, false)

    private val iconView: ImageView = content.findViewById(R.id.dialogIcon)
    private val titleView: TextView = content.findViewById(R.id.dialogTitle)
    private val packageView: TextView = content.findViewById(R.id.dialogPackage)
    private val labelView: TextView = content.findViewById(R.id.dialogLabel)
    private val valueView: TextView = content.findViewById(R.id.dialogValue)
    private val levelView: TextView = content.findViewById(R.id.dialogLevel)
    private val noteView: TextView = content.findViewById(R.id.dialogNote)
    private val decreaseView: TextView = content.findViewById(R.id.decreaseButton)
    private val increaseView: TextView = content.findViewById(R.id.increaseButton)
    private val minView: TextView = content.findViewById(R.id.minButton)
    private val minusTenView: TextView = content.findViewById(R.id.minusTenButton)
    private val plusTenView: TextView = content.findViewById(R.id.plusTenButton)
    private val maxView: TextView = content.findViewById(R.id.maxButton)
    private val clearView: TextView = content.findViewById(R.id.clearButton)
    private val scheduleAdd: TextView = content.findViewById(R.id.scheduleAdd)
    private val scheduleClear: TextView = content.findViewById(R.id.scheduleClear)
    private val scheduleList: RecyclerView = content.findViewById(R.id.scheduleList)
    private val scheduleEmpty: TextView = content.findViewById(R.id.scheduleEmpty)
    private lateinit var scheduleAdapter: ScheduleAdapter
    private val schedules = ArrayList<VolumeRules.Schedule>()

    init {
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.72f)

        if (icon != null) {
            iconView.setImageDrawable(icon)
        } else {
            iconView.visibility = View.GONE
        }
        titleView.text = title
        packageView.text = subtitle
        labelView.text = label

        decreaseView.setOnClickListener {
            onAdjust(-1)
            render()
        }

        increaseView.setOnClickListener {
            onAdjust(1)
            render()
        }

        minView.setOnClickListener {
            onSetIndex(0)
            render()
        }

        minusTenView.setOnClickListener {
            onAdjust(-10)
            render()
        }

        plusTenView.setOnClickListener {
            onAdjust(10)
            render()
        }

        maxView.setOnClickListener {
            onSetIndex(AudioLevels.maxVolume(activity))
            render()
        }

        clearView.setOnClickListener {
            onClear()
            render()
            increaseView.requestFocus()
        }

        content.findViewById<TextView>(R.id.openButton).apply {
            if (onOpen == null) {
                visibility = View.GONE
            } else {
                setOnClickListener {
                    dialog.dismiss()
                    onOpen()
                }
            }
        }

        content.findViewById<TextView>(R.id.doneButton).setOnClickListener {
            dialog.dismiss()
        }

        configureSchedules()
        dialog.setOnShowListener {
            applyWindowSize()
            increaseView.requestFocus()
        }

        render()
    }

    fun show() {
        dialog.show()
    }

    private fun render() {
        val max = AudioLevels.maxVolume(activity).coerceAtLeast(1)
        val currentIndex = AudioLevels.currentIndex(activity).coerceIn(0, max)
        val percent = percentProvider()
        val index = percent?.let { AudioLevels.percentToIndex(it, max) } ?: currentIndex

        if (percent == null) {
            valueView.text = activity.getString(R.string.dialog_not_set)
            valueView.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            levelView.text = activity.getString(R.string.dialog_current_level, index, max)
            noteView.setText(noteUnset)
        } else {
            valueView.text = activity.getString(R.string.dialog_value_level, index)
            valueView.setTextColor(ContextCompat.getColor(activity, R.color.accent))
            levelView.text = activity.getString(R.string.dialog_of_max, max)
            noteView.setText(noteSet)
        }

        clearView.isEnabled = percent != null
        clearView.isFocusable = percent != null
        clearView.alpha = if (percent != null) 1f else 0.35f
        refreshSchedules()
    }

    private fun configureSchedules() {
        if (targetPackage == null) {
            scheduleAdd.visibility = View.GONE
            scheduleClear.visibility = View.GONE
            scheduleList.visibility = View.GONE
            scheduleEmpty.visibility = View.GONE
            return
        }
        scheduleAdapter = ScheduleAdapter(
            maxVolumeProvider = { AudioLevels.maxVolume(activity) },
            onEdit = { schedule -> showScheduleOptions(schedule) },
        )
        scheduleList.layoutManager = LinearLayoutManager(activity)
        scheduleList.adapter = scheduleAdapter
        scheduleList.itemAnimator = null
        scheduleList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val gap = (7 * activity.resources.displayMetrics.density).toInt()

            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                outRect.bottom = gap
            }
        })
        scheduleAdd.setOnClickListener { showScheduleOptions(null) }
        scheduleClear.setOnClickListener {
            VolumeRules.clearSchedules(activity, targetPackage)
            refreshSchedules()
        }
        refreshSchedules()
    }

    private fun refreshSchedules() {
        if (targetPackage == null) return
        schedules.clear()
        schedules.addAll(VolumeRules.getSchedules(activity, targetPackage))
        scheduleAdapter.submit(schedules)
        scheduleList.visibility = if (schedules.isEmpty()) View.GONE else View.VISIBLE
        scheduleEmpty.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
        val hasAny = schedules.isNotEmpty()
        scheduleClear.isEnabled = hasAny
        scheduleClear.isFocusable = hasAny
        scheduleClear.alpha = if (hasAny) 1f else 0.35f
    }

    private fun showScheduleOptions(schedule: VolumeRules.Schedule?) {
        if (targetPackage == null) return
        ScheduleDialog(
            activity = activity,
            existing = schedule,
            onSave = { start, end, percent ->
                if (schedule == null) {
                    val added = VolumeRules.addSchedule(activity, targetPackage, start, end, percent)
                    if (added == null) {
                        android.widget.Toast.makeText(activity, "Time range overlaps existing schedule", android.widget.Toast.LENGTH_SHORT).show()
                        return@ScheduleDialog
                    }
                } else {
                    val updated = VolumeRules.updateSchedule(activity, schedule.copy(startMinutes = start, endMinutes = end, percent = percent))
                    if (!updated) {
                        android.widget.Toast.makeText(activity, "Time range overlaps existing schedule", android.widget.Toast.LENGTH_SHORT).show()
                        return@ScheduleDialog
                    }
                }
                refreshSchedules()
            },
            onDelete = if (schedule == null) {
                null
            } else {
                { id ->
                    VolumeRules.removeSchedule(activity, targetPackage, id)
                    refreshSchedules()
                }
            },
        ).show()
    }

    private fun applyWindowSize() {
        val metrics = activity.resources.displayMetrics
        val desiredWidth = (620f * metrics.density).toInt()
        val maxWidth = (metrics.widthPixels * 0.9f).toInt()
        val minWidth = (320f * metrics.density).toInt().coerceAtMost(maxWidth)
        val width = desiredWidth.coerceAtMost(maxWidth).coerceAtLeast(minWidth)

        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
