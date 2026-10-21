package com.varun.workouttracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class WorkoutSet(
    val id: Int,
    val exercise: String,
    val reps: String,
    val weight: String,
    val completed: Boolean = false,
)

data class WorkoutRecord(
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedMillis: Long,
    val sets: List<WorkoutSet>,
    val activityType: String = "Strength",
    val intensity: String = "moderate",
    val weightKg: Double? = null,
    val estimatedCalories: Int? = null,
    val distanceMeters: Double? = null,
    val averagePaceSecondsPerKm: Double? = null,
)

data class WorkoutTemplate(
    val name: String,
    val activityType: String,
    val sets: List<WorkoutSet>,
    val runtimeMinutes: String = "30",
    val restMinutes: String = "5",
    val prepSeconds: String = "10",
    val cardioMode: String = "intervals",
    val restTimerSeconds: Int = 90,
)

data class WorkoutState(
    val startedAt: Long,
    val sets: List<WorkoutSet>,
    val title: String = "Workout",
    val resumedAt: Long = startedAt,
    val pausedAt: Long = 0L,
    val elapsedBeforePause: Long = 0L,
    val restUntil: Long = 0L,
    val pausedRestRemaining: Long = 0L,
    val restTimerSeconds: Int = 90,
    val history: List<WorkoutRecord> = emptyList(),
    val finished: Boolean = false,
    val activityType: String = "Strength",
    val timerStarted: Boolean = false,
    val timerStopped: Boolean = false,
    val runtimeMinutes: String = "30",
    val restMinutes: String = "5",
    val prepSeconds: String = "10",
    val intervalPhase: String = "idle",
    val intervalEndsAt: Long = 0L,
    val pausedIntervalRemaining: Long = 0L,
    val intervalRound: Int = 1,
    val cardioMode: String = "intervals",
    val intensity: String = "moderate",
)

object WorkoutStorage {
    private const val PREFS = "workout_tracker_local"
    private const val STATE_KEY = "active_workout"
    private const val TEMPLATES_KEY = "workout_templates"
    private const val BODY_WEIGHT_KEY = "body_weight_kg"
    private const val WEIGH_INS_KEY = "body_weight_entries"

    fun loadBodyWeightKg(context: Context): Double? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(BODY_WEIGHT_KEY, null)?.toDoubleOrNull()?.takeIf { it > 0.0 }
        ?: loadWeighIns(context).maxByOrNull { it.first }?.second

    fun saveBodyWeightKg(context: Context, value: Double?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val validWeight = value?.takeIf { it in 20.0..350.0 }
        val editor = prefs.edit().putString(BODY_WEIGHT_KEY, validWeight?.toString())
        if (validWeight != null) {
            val now = System.currentTimeMillis()
            val day = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val entries = loadWeighIns(context).filterNot { entry ->
                val entryDay = Calendar.getInstance().apply { timeInMillis = entry.first; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                entryDay == day
            } + (now to validWeight)
            val rows = JSONArray().apply { entries.sortedBy { it.first }.forEach { (timestamp, kg) -> put(JSONObject().put("time", timestamp).put("kg", kg)) } }
            editor.putString(WEIGH_INS_KEY, rows.toString())
        }
        editor.apply()
    }

    fun bodyWeightBefore(context: Context, time: Long): Double? =
        loadWeighIns(context).filter { it.first <= time }.maxByOrNull { it.first }?.second

    private fun loadWeighIns(context: Context): List<Pair<Long, Double>> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(WEIGH_INS_KEY, null) ?: return emptyList()
        val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            runCatching {
                val row = rows.getJSONObject(index)
                val kg = row.getDouble("kg")
                row.getLong("time") to kg
            }.getOrNull()
        }
    }

    fun loadTemplates(context: Context): List<WorkoutTemplate> {
        val rows = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TEMPLATES_KEY, null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            runCatching {
                val row = rows.getJSONObject(index)
                WorkoutTemplate(
                    name = row.getString("name"),
                    activityType = row.optString("activityType", "Strength"),
                    sets = readSets(row.optJSONArray("sets") ?: JSONArray()),
                    runtimeMinutes = row.optString("runtimeMinutes", "30"),
                    restMinutes = row.optString("restMinutes", "5"),
                    prepSeconds = row.optString("prepSeconds", "10"),
                    cardioMode = row.optString("cardioMode", "intervals"),
                    restTimerSeconds = row.optInt("restTimerSeconds", 90),
                )
            }.getOrNull()
        }
    }

    fun saveTemplate(context: Context, template: WorkoutTemplate): List<WorkoutTemplate> {
        val current = loadTemplates(context)
        val updated = current.filterNot { it.name.equals(template.name, ignoreCase = true) } + template
        writeTemplates(context, updated)
        return updated
    }

    fun deleteTemplate(context: Context, name: String): List<WorkoutTemplate> {
        val updated = loadTemplates(context).filterNot { it.name == name }
        writeTemplates(context, updated)
        return updated
    }

    private fun writeTemplates(context: Context, templates: List<WorkoutTemplate>) {
        val rows = JSONArray()
        templates.forEach { template ->
            rows.put(
                JSONObject()
                    .put("name", template.name)
                    .put("activityType", template.activityType)
                    .put("sets", writeSets(template.sets))
                    .put("runtimeMinutes", template.runtimeMinutes)
                    .put("restMinutes", template.restMinutes)
                    .put("prepSeconds", template.prepSeconds)
                    .put("cardioMode", template.cardioMode)
                    .put("restTimerSeconds", template.restTimerSeconds),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TEMPLATES_KEY, rows.toString())
            .apply()
    }

    fun load(context: Context): WorkoutState {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STATE_KEY, null)
        if (raw != null) {
            runCatching {
                val root = JSONObject(raw)
                val startedAt = root.getLong("startedAt")
                val storedSets = readSets(root.getJSONArray("sets"))
                val defaultSets = listOf(
                    WorkoutSet(1, "Bench Press", "8", "60"),
                    WorkoutSet(2, "Bench Press", "8", "60"),
                    WorkoutSet(3, "Bench Press", "8", "60"),
                )
                val sets = if (storedSets == defaultSets) emptyList() else storedSets
                if (sets.isNotEmpty() || root.has("activityType")) {
                    val records = root.optJSONArray("history") ?: JSONArray()
                    return WorkoutState(
                        startedAt = startedAt,
                        sets = sets,
                        title = root.optString("title", "Workout"),
                        resumedAt = root.optLong("resumedAt", startedAt),
                        pausedAt = root.optLong("pausedAt", 0L),
                        elapsedBeforePause = root.optLong("elapsedBeforePause", 0L),
                        restUntil = root.optLong("restUntil", 0L),
                        pausedRestRemaining = root.optLong("pausedRestRemaining", 0L),
                        restTimerSeconds = root.optInt("restTimerSeconds", 90),
                        history = (0 until records.length()).map { index ->
                            val record = records.getJSONObject(index)
                            WorkoutRecord(
                                title = record.optString("title", "Workout"),
                                startedAt = record.getLong("startedAt"),
                                endedAt = record.getLong("endedAt"),
                                elapsedMillis = record.optLong("elapsedMillis", record.getLong("endedAt") - record.getLong("startedAt")),
                                sets = readSets(record.getJSONArray("sets")),
                                activityType = record.optString("activityType", "Strength"),
                                intensity = record.optString("intensity", "moderate"),
                                weightKg = if (record.has("weightKg") && !record.isNull("weightKg")) record.optDouble("weightKg") else null,
                                estimatedCalories = if (record.has("estimatedCalories") && !record.isNull("estimatedCalories")) record.optInt("estimatedCalories") else null,
                                distanceMeters = if (record.has("distanceMeters") && !record.isNull("distanceMeters")) record.optDouble("distanceMeters") else null,
                                averagePaceSecondsPerKm = if (record.has("averagePaceSecondsPerKm") && !record.isNull("averagePaceSecondsPerKm")) record.optDouble("averagePaceSecondsPerKm") else null,
                            )
                        },
                        finished = root.optBoolean("finished", false),
                        activityType = root.optString("activityType", "Strength"),
                        timerStarted = root.optBoolean("timerStarted", false),
                        timerStopped = root.optBoolean("timerStopped", false),
                        runtimeMinutes = root.optString("runtimeMinutes", "30"),
                        restMinutes = root.optString("restMinutes", "5"),
                        prepSeconds = root.optString("prepSeconds", "10"),
                        intervalPhase = root.optString("intervalPhase", "idle"),
                        intervalEndsAt = root.optLong("intervalEndsAt", 0L),
                        pausedIntervalRemaining = root.optLong("pausedIntervalRemaining", 0L),
                        intervalRound = root.optInt("intervalRound", 1),
                        cardioMode = root.optString("cardioMode", "intervals"),
                        intensity = root.optString("intensity", "moderate"),
                    )
                }
            }
        }
        return newWorkout()
    }

    fun save(context: Context, state: WorkoutState) {
        val records = JSONArray()
        state.history.forEach { record ->
            records.put(
                JSONObject()
                    .put("title", record.title)
                    .put("startedAt", record.startedAt)
                    .put("endedAt", record.endedAt)
                    .put("elapsedMillis", record.elapsedMillis)
                    .put("sets", writeSets(record.sets))
                    .put("activityType", record.activityType)
                    .put("intensity", record.intensity)
                    .put("weightKg", record.weightKg)
                    .put("estimatedCalories", record.estimatedCalories)
                    .put("distanceMeters", record.distanceMeters)
                    .put("averagePaceSecondsPerKm", record.averagePaceSecondsPerKm),
            )
        }
        val value = JSONObject()
            .put("startedAt", state.startedAt)
            .put("title", state.title)
            .put("resumedAt", state.resumedAt)
            .put("pausedAt", state.pausedAt)
            .put("elapsedBeforePause", state.elapsedBeforePause)
            .put("restUntil", state.restUntil)
            .put("pausedRestRemaining", state.pausedRestRemaining)
            .put("restTimerSeconds", state.restTimerSeconds)
            .put("finished", state.finished)
            .put("activityType", state.activityType)
            .put("timerStarted", state.timerStarted)
            .put("timerStopped", state.timerStopped)
            .put("runtimeMinutes", state.runtimeMinutes)
            .put("restMinutes", state.restMinutes)
            .put("prepSeconds", state.prepSeconds)
            .put("intervalPhase", state.intervalPhase)
            .put("intervalEndsAt", state.intervalEndsAt)
            .put("pausedIntervalRemaining", state.pausedIntervalRemaining)
            .put("intervalRound", state.intervalRound)
            .put("cardioMode", state.cardioMode)
            .put("intensity", state.intensity)
            .put("sets", writeSets(state.sets))
            .put("history", records)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(STATE_KEY, value.toString())
            .apply()
    }

    fun newWorkout(
        now: Long = System.currentTimeMillis(),
        history: List<WorkoutRecord> = emptyList(),
        activityType: String = "Choose activity",
    ) = WorkoutState(
        startedAt = now,
        resumedAt = now,
        title = "Workout",
        history = history,
        activityType = activityType,
        sets = emptyList(),
        timerStarted = activityType == "Strength",
        cardioMode = if (activityType in setOf("Jump Rope", "HIIT")) "intervals" else "stopwatch",
        runtimeMinutes = if (activityType == "Jump Rope") "3" else "30",
        restMinutes = if (activityType == "Jump Rope") "2" else "5",
    )

    private fun writeSets(sets: List<WorkoutSet>) = JSONArray().apply {
        sets.forEach { set ->
            put(
                JSONObject()
                    .put("id", set.id)
                    .put("exercise", set.exercise)
                    .put("reps", set.reps)
                    .put("weight", set.weight)
                    .put("completed", set.completed),
            )
        }
    }

    private fun readSets(rows: JSONArray) = (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        WorkoutSet(
            id = row.getInt("id"),
            exercise = row.getString("exercise"),
            reps = row.getString("reps"),
            weight = row.optString("weight", ""),
            completed = row.optBoolean("completed", false),
        )
    }
}
