package com.santodan.appvolume

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.Locale

/** Loads every app that can be started from the TV launcher. */
object AppRepository {

    private const val TAG = "AppVolume"

    fun loadLaunchableApps(context: Context): List<AppEntry> {
        val packageManager = context.packageManager
        val seenPackages = HashSet<String>()
        val entries = ArrayList<AppEntry>()

        val queryIntents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        )

        for (intent in queryIntents) {
            val activities = try {
                packageManager.queryIntentActivities(intent, 0)
            } catch (error: Throwable) {
                Log.w(TAG, "Could not query launchable activities", error)
                emptyList()
            }

            for (activity in activities) {
                val activityInfo = activity.activityInfo ?: continue
                val packageName = activityInfo.packageName ?: continue
                if (packageName == context.packageName) continue
                if (!activityInfo.enabled) continue
                if (!seenPackages.add(packageName)) continue

                val appInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (error: Throwable) {
                    null
                } ?: continue

                if (!appInfo.enabled) continue

                val label = try {
                    appInfo.loadLabel(packageManager).toString()
                } catch (error: Throwable) {
                    packageName
                }

                val icon = try {
                    appInfo.loadIcon(packageManager)
                } catch (error: Throwable) {
                    null
                }

                entries += AppEntry(
                    packageName = packageName,
                    label = label.ifBlank { packageName },
                    icon = icon,
                )
            }
        }

        return entries.sortedWith(
            compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName },
        )
    }

    fun launch(context: Context, entry: AppEntry): Boolean {
        val packageManager = context.packageManager
        val intent = try {
            packageManager.getLeanbackLaunchIntentForPackage(entry.packageName)
                ?: packageManager.getLaunchIntentForPackage(entry.packageName)
        } catch (error: Throwable) {
            Log.w(TAG, "Could not create launch intent for ${entry.packageName}", error)
            null
        }

        if (intent == null) {
            Log.w(TAG, "No launch intent for ${entry.packageName}")
            return false
        }

        return try {
            context.startActivity(intent)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Could not launch ${entry.packageName}", error)
            false
        }
    }
}
