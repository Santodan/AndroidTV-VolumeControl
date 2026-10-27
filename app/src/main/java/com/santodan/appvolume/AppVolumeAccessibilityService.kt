package com.santodan.appvolume

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Watches app switches and applies the foreground app's saved media volume.
 *
 * Accessibility is used because Android does not give third-party apps a
 * lightweight way to observe the foreground package in real time. Only the
 * package name from window events is read; screen content is never inspected.
 */
class AppVolumeAccessibilityService : AccessibilityService() {

    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastForegroundPackage = null
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                val packageName = resolvePackage(event) ?: return
                onForegroundPackage(packageName)
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun resolvePackage(event: AccessibilityEvent): String? {
        val eventPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (eventPackage != null && eventPackage != SYSTEM_FRAMEWORK_PACKAGE) {
            return eventPackage
        }

        return rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: eventPackage
    }

    private fun onForegroundPackage(packageName: String) {
        if (IGNORED_PACKAGES.contains(packageName)) return
        if (packageName == lastForegroundPackage) return

        // Track transitions even while paused so resuming auto-apply does not
        // accidentally skip the app that is currently open.
        lastForegroundPackage = packageName

        if (!VolumeRules.isAutoApplyEnabled(this)) return

        val appPercent = VolumeRules.percentFor(this, packageName)
        val appSchedule = VolumeRules.scheduledPercentFor(this, packageName)
        val defaultSchedule = VolumeRules.scheduledPercentFor(this, VolumeRules.defaultTarget())
        val defaultPercent = VolumeRules.defaultPercent(this)
        val effectivePercent = appPercent ?: appSchedule ?: defaultPercent ?: defaultSchedule ?: return
        val applied = AudioLevels.applyPercent(this, effectivePercent)
        val source = when {
            appPercent != null -> "app"
            appSchedule != null -> "app-schedule"
            defaultPercent != null -> "default"
            defaultSchedule != null -> "default-schedule"
            else -> "none"
        }
        Log.i(TAG, "Foreground=$packageName, $source=$effectivePercent%, applied=$applied")
    }

    private companion object {
        const val TAG = "AppVolume"
        const val SYSTEM_FRAMEWORK_PACKAGE = "android"

        // These appear over the current app (volume panel, power menu, etc.).
        // Ignoring them prevents a return from the overlay from re-applying a
        // volume level the user just changed with the remote.
        val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
        )
    }
}
