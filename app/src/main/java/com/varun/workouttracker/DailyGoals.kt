package com.varun.workouttracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailyGoalStorage {
    private const val PREFS = "workout_daily_goals"
    private const val PUSHUP_GOAL = "pushup_goal"
    private const val PUSHUP_LOG_DATE = "pushup_log_date"
    private const val PUSHUP_LOG_COUNT = "pushup_log_count"

    fun pushupGoal(context: Context): Int = preferences(context).getInt(PUSHUP_GOAL, 50)

    fun savePushupGoal(context: Context, goal: Int) {
        preferences(context).edit().putInt(PUSHUP_GOAL, goal.coerceIn(5, 1_000)).apply()
    }

    fun manualPushupsToday(context: Context): Int {
        val prefs = preferences(context)
        return if (prefs.getString(PUSHUP_LOG_DATE, null) == todayKey()) prefs.getInt(PUSHUP_LOG_COUNT, 0) else 0
    }

    fun logManualPushups(context: Context, reps: Int): Int {
        val prefs = preferences(context)
        val today = todayKey()
        val current = if (prefs.getString(PUSHUP_LOG_DATE, null) == today) prefs.getInt(PUSHUP_LOG_COUNT, 0) else 0
        val updated = (current + reps).coerceIn(0, 100_000)
        prefs.edit().putString(PUSHUP_LOG_DATE, today).putInt(PUSHUP_LOG_COUNT, updated).apply()
        return updated
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

object ProfileStorage {
    private const val PREFS = "workout_profile"
    private const val NAME = "name"
    private const val HEIGHT_CM = "height_cm"
    private const val AGE = "age"

    fun name(context: Context): String = preferences(context).getString(NAME, "").orEmpty()
    fun heightCm(context: Context): Int? = preferences(context).getInt(HEIGHT_CM, 0).takeIf { it in 80..250 }
    fun age(context: Context): Int? = preferences(context).getInt(AGE, 0).takeIf { it in 13..120 }

    fun save(context: Context, name: String, heightCm: Int?, age: Int?) {
        preferences(context).edit()
            .putString(NAME, name.trim())
            .putInt(HEIGHT_CM, heightCm?.takeIf { it in 80..250 } ?: 0)
            .putInt(AGE, age?.takeIf { it in 13..120 } ?: 0)
            .apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
