package com.santodan.appvolume

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class ScheduleDialog(
    private val activity: Activity,
    private val existing: VolumeRules.Schedule?,
    private val onSave: (startMinutes: Int, endMinutes: Int, percent: Int) -> Unit,
    private val onDelete: ((Long) -> Unit)?,
) {

    private val dialog = Dialog(activity, R.style.Theme_AppVolume_Dialog)
    private val content: View = LayoutInflater.from(activity).inflate(R.layout.dialog_schedule, null, false)

    private val startTime: TextView = content.findViewById(R.id.startTime)
    private val endTime: TextView = content.findViewById(R.id.endTime)
    private val volValue: TextView = content.findViewById(R.id.volValue)

    private var startMinutes = existing?.startMinutes ?: (8 * 60)
    private var endMinutes = existing?.endMinutes ?: (10 * 60)
    private var percent = existing?.percent ?: 50

    init {
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.72f)

        content.findViewById<TextView>(R.id.dialogTitle).text =
            activity.getString(if (existing == null) R.string.schedules_add else R.string.schedules_edit)

        content.findViewById<View>(R.id.startMinus).setOnClickListener {
            startMinutes = ((startMinutes - 30).mod(24 * 60))
            render()
        }
        content.findViewById<View>(R.id.startPlus).setOnClickListener {
            startMinutes = (startMinutes + 30).mod(24 * 60)
            render()
        }
        content.findViewById<View>(R.id.endMinus).setOnClickListener {
            endMinutes = ((endMinutes - 30).mod(24 * 60))
            render()
        }
        content.findViewById<View>(R.id.endPlus).setOnClickListener {
            endMinutes = (endMinutes + 30).mod(24 * 60)
            render()
        }
        content.findViewById<View>(R.id.volMinus).setOnClickListener {
            percent = (percent - 5).coerceIn(0, 100)
            render()
        }
        content.findViewById<View>(R.id.volPlus).setOnClickListener {
            percent = (percent + 5).coerceIn(0, 100)
            render()
        }

        val deleteBtn = content.findViewById<TextView>(R.id.deleteButton)
        if (existing == null || onDelete == null) {
            deleteBtn.visibility = View.GONE
        } else {
            deleteBtn.setOnClickListener {
                onDelete(existing.id)
                dialog.dismiss()
            }
        }

        content.findViewById<View>(R.id.cancelButton).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.saveButton).setOnClickListener {
            onSave(startMinutes, endMinutes, percent)
            dialog.dismiss()
        }

        dialog.setOnShowListener { applyWindowSize() }
        render()
    }

    fun show() {
        dialog.show()
    }

    private fun render() {
        val sh = startMinutes / 60
        val sm = startMinutes % 60
        val eh = endMinutes / 60
        val em = endMinutes % 60
        startTime.text = String.format("%02d:%02d", sh, sm)
        endTime.text = String.format("%02d:%02d", eh, em)
        val max = AudioLevels.maxVolume(activity).coerceAtLeast(1)
        val index = AudioLevels.percentToIndex(percent, max)
        volValue.text = activity.getString(R.string.schedule_range_level, index, max)
    }

    private fun applyWindowSize() {
        val metrics = activity.resources.displayMetrics
        val desiredWidth = (620f * metrics.density).toInt()
        val maxWidth = (metrics.widthPixels * 0.9f).toInt()
        val width = desiredWidth.coerceAtMost(maxWidth)
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun Int.mod(m: Int): Int = ((this % m) + m) % m
}
