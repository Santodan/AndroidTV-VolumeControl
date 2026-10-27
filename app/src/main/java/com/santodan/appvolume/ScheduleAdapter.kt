package com.santodan.appvolume

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private val maxVolumeProvider: () -> Int,
    private val onEdit: (VolumeRules.Schedule) -> Unit,
) : RecyclerView.Adapter<ScheduleAdapter.Holder>() {

    private val items = ArrayList<VolumeRules.Schedule>()

    fun submit(list: List<VolumeRules.Schedule>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], maxVolumeProvider(), onEdit)
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeView: TextView = itemView.findViewById(R.id.scheduleTime)
        private val metaView: TextView = itemView.findViewById(R.id.scheduleMeta)
        private val volumeView: TextView = itemView.findViewById(R.id.scheduleVolume)

        fun bind(
            schedule: VolumeRules.Schedule,
            max: Int,
            onEdit: (VolumeRules.Schedule) -> Unit,
        ) {
            val context = itemView.context
            val startH = schedule.startMinutes / 60
            val startM = schedule.startMinutes % 60
            val endH = schedule.endMinutes / 60
            val endM = schedule.endMinutes % 60
            timeView.text = context.getString(R.string.schedule_range, startH, startM, endH, endM)
            val index = AudioLevels.percentToIndex(schedule.percent, max.coerceAtLeast(1))
            volumeView.text = context.getString(R.string.schedule_range_level, index, max.coerceAtLeast(1))
            volumeView.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (schedule.enabled) R.color.accent else R.color.text_tertiary,
                ),
            )
            metaView.text = context.getString(R.string.schedules_subtitle)
            itemView.isEnabled = schedule.enabled
            itemView.alpha = if (schedule.enabled) 1f else 0.6f
            itemView.setOnClickListener { onEdit(schedule) }
        }
    }
}
