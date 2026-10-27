package com.santodan.appvolume

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

/**
 * All conversions between the TV's media stream steps and the 0-100 percentages
 * that are persisted for each app.
 */
object AudioLevels {

    private const val TAG = "AppVolume"
    private const val STREAM = AudioManager.STREAM_MUSIC

    private fun audioManager(context: Context): AudioManager {
        return context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun maxVolume(context: Context): Int {
        return audioManager(context).getStreamMaxVolume(STREAM).coerceAtLeast(1)
    }

    fun currentIndex(context: Context): Int {
        val manager = audioManager(context)
        return manager.getStreamVolume(STREAM).coerceIn(0, manager.getStreamMaxVolume(STREAM))
    }

    fun percentToIndex(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        return (percent.coerceIn(0, 100) / 100f * max).roundToInt().coerceIn(0, max)
    }

    fun indexToPercent(index: Int, max: Int): Int {
        if (max <= 0) return 0
        return (index.coerceIn(0, max) / max.toFloat() * 100f).roundToInt().coerceIn(0, 100)
    }

    fun applyPercent(context: Context, percent: Int): Boolean {
        val max = maxVolume(context)
        return applyIndex(context, percentToIndex(percent, max))
    }

    fun applyIndex(context: Context, index: Int): Boolean {
        val manager = audioManager(context)
        val max = manager.getStreamMaxVolume(STREAM)
        if (max <= 0) return false

        val target = index.coerceIn(0, max)
        return try {
            if (manager.getStreamVolume(STREAM) != target) {
                manager.setStreamVolume(STREAM, target, 0)
            }
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to set STREAM_MUSIC to index $target", error)
            false
        }
    }
}
