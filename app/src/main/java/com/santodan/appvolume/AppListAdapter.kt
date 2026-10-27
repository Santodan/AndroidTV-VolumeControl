package com.santodan.appvolume

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val percentProvider: (String) -> Int?,
    private val maxVolumeProvider: () -> Int,
    private val currentIndexProvider: () -> Int,
    private val onAdjust: (AppEntry, Int) -> Unit,
    private val onShowOptions: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private val items = ArrayList<AppEntry>()

    init {
        setHasStableIds(true)
    }

    fun submitApps(apps: List<AppEntry>) {
        items.clear()
        items.addAll(apps)
        notifyDataSetChanged()
    }

    fun refreshPackage(packageName: String) {
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            notifyItemChanged(index, PAYLOAD_VOLUME)
        }
    }

    fun refreshVolumes() {
        if (items.isNotEmpty()) {
            notifyItemRangeChanged(0, items.size, PAYLOAD_VOLUME)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return items[position].packageName.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_volume, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_VOLUME)) {
            holder.bindVolume(items[position])
        } else {
            holder.bind(items[position])
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameView: TextView = itemView.findViewById(R.id.appName)
        private val metaView: TextView = itemView.findViewById(R.id.appMeta)
        private val volumeView: TextView = itemView.findViewById(R.id.appVolume)
        private val barView: VolumeBarView = itemView.findViewById(R.id.appBar)

        private val accentColor = ContextCompat.getColor(itemView.context, R.color.accent)
        private val unsetColor = ContextCompat.getColor(itemView.context, R.color.text_tertiary)

        private var entry: AppEntry? = null

        init {
            itemView.setOnClickListener {
                entry?.let(onShowOptions)
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        entry?.let { onAdjust(it, -1) }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        entry?.let { onAdjust(it, 1) }
                        true
                    }

                    else -> false
                }
            }
        }

        fun bind(appEntry: AppEntry) {
            entry = appEntry
            iconView.setImageDrawable(appEntry.icon)
            nameView.text = appEntry.label
            bindVolume(appEntry)
        }

        fun bindVolume(appEntry: AppEntry) {
            val context = itemView.context
            val max = maxVolumeProvider().coerceAtLeast(1)
            val percent = percentProvider(appEntry.packageName)

            if (percent == null) {
                val currentIndex = currentIndexProvider().coerceIn(0, max)
                volumeView.text = context.getString(R.string.row_not_set)
                volumeView.setTextColor(unsetColor)
                metaView.text = context.getString(R.string.row_uses_current, currentIndex, max)
                barView.setLevel(0f, false)
                itemView.contentDescription = context.getString(
                    R.string.row_content_unset,
                    appEntry.label,
                )
            } else {
                val index = AudioLevels.percentToIndex(percent, max)
                volumeView.text = context.getString(R.string.row_level, index, max)
                volumeView.setTextColor(accentColor)
                metaView.setText(R.string.row_set_meta)
                barView.setLevel(index / max.toFloat(), true)
                itemView.contentDescription = context.getString(
                    R.string.row_content_set,
                    appEntry.label,
                    index,
                    max,
                )
            }
        }
    }

    private companion object {
        const val PAYLOAD_VOLUME = "volume"
    }
}
