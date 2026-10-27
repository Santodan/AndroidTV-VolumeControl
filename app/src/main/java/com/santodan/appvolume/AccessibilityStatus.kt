package com.santodan.appvolume

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityStatus {

    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppVolumeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (service in splitter) {
            if (ComponentName.unflattenFromString(service) == expected) {
                return true
            }
        }
        return false
    }
}
