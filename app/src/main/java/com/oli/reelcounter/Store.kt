package com.oli.reelcounter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("reels", Context.MODE_PRIVATE)

    private fun dayKey(daysAgo: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun count(c: Context, daysAgo: Int = 0) = prefs(c).getInt("c_" + dayKey(daysAgo), 0)
    fun seconds(c: Context, daysAgo: Int = 0) = prefs(c).getLong("s_" + dayKey(daysAgo), 0L)

    fun addCount(c: Context) {
        prefs(c).edit().putInt("c_" + dayKey(), count(c) + 1).apply()
    }

    fun addSeconds(c: Context, s: Long) {
        prefs(c).edit().putLong("s_" + dayKey(), seconds(c) + s).apply()
    }

    fun label(daysAgo: Int): String = dayKey(daysAgo)

    fun fmt(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun fmtShort(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h${m}m" else "${m}m"
    }
}
