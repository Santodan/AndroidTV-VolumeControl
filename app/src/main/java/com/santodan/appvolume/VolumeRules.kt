package com.santodan.appvolume

import android.content.Context
import java.util.Calendar

/**
 * Per-app volume rules, schedules, and the global auto-apply switch.
 *
 * Rules are stored as percentages (0-100) instead of raw stream indexes so they
 * keep their meaning if a TV reports a different number of media volume steps.
 */
object VolumeRules {

    private const val RULE_PREFS = "app_volume_rules_v1"
    private const val SETTINGS_PREFS = "app_volume_settings_v1"
    private const val SCHEDULE_PREFS = "app_volume_schedules_v2"
    private const val RULE_PREFIX = "pkg:"
    private const val KEY_AUTO_APPLY = "auto_apply"
    private const val KEY_DEFAULT_PERCENT = "default_percent"
    private const val DEFAULT_TARGET = "__default__"

    private fun rules(context: Context) = context.applicationContext
        .getSharedPreferences(RULE_PREFS, Context.MODE_PRIVATE)

    private fun settings(context: Context) = context.applicationContext
        .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    private fun schedules(context: Context) = context.applicationContext
        .getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)

    data class Schedule(
        val id: Long,
        val targetPackage: String,
        val startMinutes: Int,
        val endMinutes: Int,
        val percent: Int,
        val enabled: Boolean = true,
    ) {
        fun containsTime(minutes: Int): Boolean {
            if (startMinutes == endMinutes) return false
            return if (startMinutes < endMinutes) {
                minutes >= startMinutes && minutes < endMinutes
            } else {
                minutes >= startMinutes || minutes < endMinutes
            }
        }

        fun overlaps(other: Schedule): Boolean {
            if (id == other.id) return false
            if (startMinutes == endMinutes || other.startMinutes == other.endMinutes) return false
            if (startMinutes < endMinutes && other.startMinutes < other.endMinutes) {
                return startMinutes < other.endMinutes && other.startMinutes < endMinutes
            }
            if (startMinutes >= endMinutes && other.startMinutes >= other.endMinutes) {
                // both wrap around midnight
                return true
            }
            if (startMinutes >= endMinutes && other.startMinutes < other.endMinutes) {
                // this wraps, other is in day range
                return other.startMinutes < endMinutes || other.endMinutes > startMinutes
            }
            if (startMinutes < endMinutes && other.startMinutes >= other.endMinutes) {
                return startMinutes < other.endMinutes || endMinutes > other.startMinutes
            }
            return false
        }
    }

    fun percentFor(context: Context, packageName: String): Int? {
        val prefs = rules(context)
        val key = RULE_PREFIX + packageName
        if (!prefs.contains(key)) return null
        return prefs.getInt(key, 0).coerceIn(0, 100)
    }

    fun setPercent(context: Context, packageName: String, percent: Int) {
        rules(context).edit()
            .putInt(RULE_PREFIX + packageName, percent.coerceIn(0, 100))
            .apply()
    }

    fun clear(context: Context, packageName: String) {
        rules(context).edit().remove(RULE_PREFIX + packageName).apply()
    }

    fun clearAll(context: Context) {
        rules(context).edit().clear().apply()
    }

    fun isAutoApplyEnabled(context: Context): Boolean {
        return settings(context).getBoolean(KEY_AUTO_APPLY, true)
    }

    fun setAutoApplyEnabled(context: Context, enabled: Boolean) {
        settings(context).edit().putBoolean(KEY_AUTO_APPLY, enabled).apply()
    }

    /**
     * Volume used for apps without their own rule, including the launcher.
     * A missing value means no fallback: the current TV volume is left alone.
     */
    fun defaultPercent(context: Context): Int? {
        val prefs = settings(context)
        if (!prefs.contains(KEY_DEFAULT_PERCENT)) return null
        return prefs.getInt(KEY_DEFAULT_PERCENT, 0).coerceIn(0, 100)
    }

    fun setDefaultPercent(context: Context, percent: Int) {
        settings(context).edit()
            .putInt(KEY_DEFAULT_PERCENT, percent.coerceIn(0, 100))
            .apply()
    }

    fun clearDefault(context: Context) {
        settings(context).edit().remove(KEY_DEFAULT_PERCENT).apply()
    }

    fun defaultTarget(): String = DEFAULT_TARGET

    fun getSchedules(context: Context, targetPackage: String): List<Schedule> {
        val prefs = schedules(context)
        val count = prefs.getInt(keyCount(targetPackage), 0)
        val result = ArrayList<Schedule>(count)
        for (i in 0 until count) {
            val id = prefs.getLong(keyId(targetPackage, i), i.toLong())
            val start = prefs.getInt(keyStart(targetPackage, i), 0)
            val end = prefs.getInt(keyEnd(targetPackage, i), 0)
            val percent = prefs.getInt(keyPercent(targetPackage, i), 50).coerceIn(0, 100)
            val enabled = prefs.getBoolean(keyEnabled(targetPackage, i), true)
            result.add(Schedule(id, targetPackage, start, end, percent, enabled))
        }
        return result
    }

    fun addSchedule(context: Context, targetPackage: String, startMinutes: Int, endMinutes: Int, percent: Int): Schedule? {
        val candidate = Schedule(
            System.currentTimeMillis(),
            targetPackage,
            startMinutes.coerceIn(0, 23 * 60 + 59),
            endMinutes.coerceIn(0, 23 * 60 + 59),
            percent.coerceIn(0, 100),
            true,
        )
        val existing = getSchedules(context, targetPackage)
        if (existing.any { it.enabled && it.overlaps(candidate) }) {
            return null
        }
        val prefs = schedules(context)
        val count = prefs.getInt(keyCount(targetPackage), 0)
        val id = candidate.id
        prefs.edit()
            .putLong(keyId(targetPackage, count), id)
            .putInt(keyStart(targetPackage, count), candidate.startMinutes)
            .putInt(keyEnd(targetPackage, count), candidate.endMinutes)
            .putInt(keyPercent(targetPackage, count), candidate.percent)
            .putBoolean(keyEnabled(targetPackage, count), true)
            .putInt(keyCount(targetPackage), count + 1)
            .apply()
        return candidate
    }

    fun updateSchedule(context: Context, schedule: Schedule): Boolean {
        val candidate = schedule.copy(
            startMinutes = schedule.startMinutes.coerceIn(0, 23 * 60 + 59),
            endMinutes = schedule.endMinutes.coerceIn(0, 23 * 60 + 59),
            percent = schedule.percent.coerceIn(0, 100),
        )
        val existing = getSchedules(context, schedule.targetPackage)
        if (existing.any { it.enabled && it.overlaps(candidate) }) {
            return false
        }
        val prefs = schedules(context)
        val target = schedule.targetPackage
        val count = prefs.getInt(keyCount(target), 0)
        for (i in 0 until count) {
            if (prefs.getLong(keyId(target, i), -1) == schedule.id) {
                prefs.edit()
                    .putInt(keyStart(target, i), candidate.startMinutes)
                    .putInt(keyEnd(target, i), candidate.endMinutes)
                    .putInt(keyPercent(target, i), candidate.percent)
                    .putBoolean(keyEnabled(target, i), candidate.enabled)
                    .apply()
                break
            }
        }
        return true
    }

    fun removeSchedule(context: Context, targetPackage: String, id: Long) {
        val prefs = schedules(context)
        val count = prefs.getInt(keyCount(targetPackage), 0)
        val list = ArrayList<Schedule>()
        for (i in 0 until count) {
            val sid = prefs.getLong(keyId(targetPackage, i), -1)
            if (sid == id) continue
            list.add(
                Schedule(
                    sid,
                    targetPackage,
                    prefs.getInt(keyStart(targetPackage, i), 0),
                    prefs.getInt(keyEnd(targetPackage, i), 0),
                    prefs.getInt(keyPercent(targetPackage, i), 50).coerceIn(0, 100),
                    prefs.getBoolean(keyEnabled(targetPackage, i), true),
                ),
            )
        }
        val editor = prefs.edit()
        // clear old keys for this target
        for (i in 0 until count) {
            editor.remove(keyId(targetPackage, i))
            editor.remove(keyStart(targetPackage, i))
            editor.remove(keyEnd(targetPackage, i))
            editor.remove(keyPercent(targetPackage, i))
            editor.remove(keyEnabled(targetPackage, i))
        }
        editor.putInt(keyCount(targetPackage), list.size)
        for ((index, schedule) in list.withIndex()) {
            editor.putLong(keyId(targetPackage, index), schedule.id)
            editor.putInt(keyStart(targetPackage, index), schedule.startMinutes)
            editor.putInt(keyEnd(targetPackage, index), schedule.endMinutes)
            editor.putInt(keyPercent(targetPackage, index), schedule.percent)
            editor.putBoolean(keyEnabled(targetPackage, index), schedule.enabled)
        }
        editor.apply()
    }

    fun clearSchedules(context: Context, targetPackage: String) {
        val prefs = schedules(context)
        val count = prefs.getInt(keyCount(targetPackage), 0)
        val editor = prefs.edit()
        for (i in 0 until count) {
            editor.remove(keyId(targetPackage, i))
            editor.remove(keyStart(targetPackage, i))
            editor.remove(keyEnd(targetPackage, i))
            editor.remove(keyPercent(targetPackage, i))
            editor.remove(keyEnabled(targetPackage, i))
        }
        editor.remove(keyCount(targetPackage))
        editor.apply()
    }

    fun scheduledPercentFor(context: Context, targetPackage: String): Int? {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val list = getSchedules(context, targetPackage)
        for (schedule in list) {
            if (schedule.enabled && schedule.containsTime(minutes)) {
                return schedule.percent
            }
        }
        return null
    }

    private fun keyCount(target: String) = "cnt:$target"
    private fun keyId(target: String, i: Int) = "id:$target:$i"
    private fun keyStart(target: String, i: Int) = "st:$target:$i"
    private fun keyEnd(target: String, i: Int) = "en:$target:$i"
    private fun keyPercent(target: String, i: Int) = "pc:$target:$i"
    private fun keyEnabled(target: String, i: Int) = "enb:$target:$i"
}
