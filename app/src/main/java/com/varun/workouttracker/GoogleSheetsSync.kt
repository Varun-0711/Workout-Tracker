package com.varun.workouttracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** One-way full snapshot sync into a spreadsheet owned by the user's Google account. */
object GoogleSheetsSync {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS = "workout_google_sheets"
    private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    private const val KEY_LAST_SYNCED = "last_synced_at"
    private const val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
    private val tabNames = listOf("Workouts", "Sets", "Weigh-ins", "Routines")

    data class Result(val spreadsheetId: String, val spreadsheetUrl: String, val syncedAt: Long)

    fun spreadsheetId(context: Context): String? = preferences(context)
        .getString(KEY_SPREADSHEET_ID, null)
        ?.takeIf { it.isNotBlank() }

    fun lastSyncedAt(context: Context): Long = preferences(context).getLong(KEY_LAST_SYNCED, 0L)

    fun spreadsheetUrl(context: Context): String? = spreadsheetId(context)?.let(::urlFor)

    fun disconnect(context: Context) {
        preferences(context).edit().remove(KEY_SPREADSHEET_ID).remove(KEY_LAST_SYNCED).apply()
    }

    /** Called on an IO dispatcher. Access tokens are passed in-memory and never persisted. */
    fun sync(
        context: Context,
        accessToken: String,
        history: List<WorkoutRecord>,
        templates: List<WorkoutTemplate>,
    ): Result {
        require(accessToken.isNotBlank()) { "Google did not return an access token." }
        val id = spreadsheetId(context) ?: createSpreadsheet(context, accessToken)
        val snapshot = snapshotValues(context, history, templates)

        val updateData = JSONArray()
        snapshot.forEach { (tab, rows) ->
            updateData.put(
                JSONObject()
                    .put("range", "'$tab'!A1")
                    .put("majorDimension", "ROWS")
                    .put("values", rows),
            )
        }
        request(
            method = "POST",
            url = "$BASE_URL/$id/values:batchUpdate",
            accessToken = accessToken,
            body = JSONObject().put("valueInputOption", "RAW").put("data", updateData),
        )

        // Clear any stale rows only after the current snapshot has been written successfully.
        val clearRanges = JSONArray()
        snapshot.forEach { (tab, rows) ->
            clearRanges.put("'$tab'!A${rows.length() + 1}:Z")
        }
        request(
            method = "POST",
            url = "$BASE_URL/$id/values:batchClear",
            accessToken = accessToken,
            body = JSONObject().put("ranges", clearRanges),
        )

        val syncedAt = System.currentTimeMillis()
        preferences(context).edit().putLong(KEY_LAST_SYNCED, syncedAt).apply()
        return Result(id, urlFor(id), syncedAt)
    }

    private fun createSpreadsheet(context: Context, accessToken: String): String {
        val tabs = JSONArray().apply {
            tabNames.forEach { title -> put(JSONObject().put("properties", JSONObject().put("title", title))) }
        }
        val response = request(
            method = "POST",
            url = BASE_URL,
            accessToken = accessToken,
            body = JSONObject()
                .put("properties", JSONObject().put("title", "Workout Tracker"))
                .put("sheets", tabs),
        )
        val id = response.optString("spreadsheetId").takeIf { it.isNotBlank() }
            ?: throw IOException("Google created a spreadsheet but returned no ID.")
        preferences(context).edit().putString(KEY_SPREADSHEET_ID, id).apply()
        return id
    }

    private fun snapshotValues(
        context: Context,
        history: List<WorkoutRecord>,
        templates: List<WorkoutTemplate>,
    ): List<Pair<String, JSONArray>> {
        val workoutRows = rows(
            listOf(
                "Workout ID", "Started (UTC)", "Ended (UTC)", "Activity", "Name", "Duration (seconds)",
                "Estimated calories", "Body weight (kg)", "Height (cm)", "Distance (m)",
                "Average pace (seconds/km)", "Jump count",
            ),
        )
        history.sortedBy { it.startedAt }.forEach { record ->
            workoutRows.put(
                row(
                    record.startedAt.toString(),
                    timestamp(record.startedAt),
                    timestamp(record.endedAt),
                    record.activityType,
                    record.title,
                    record.elapsedMillis / 1_000.0,
                    record.estimatedCalories,
                    record.weightKg,
                    record.heightCm,
                    record.distanceMeters,
                    record.averagePaceSecondsPerKm,
                    record.jumpCount,
                ),
            )
        }

        val setRows = rows(listOf("Workout ID", "Set number", "Exercise", "Reps or jumps", "Weight (kg)", "Completed", "Active time (seconds)"))
        history.sortedBy { it.startedAt }.forEach { record ->
            record.sets.forEach { set ->
                setRows.put(
                    row(
                        record.startedAt.toString(),
                        set.id,
                        set.exercise,
                        set.reps,
                        set.weight,
                        set.completed,
                        set.durationMillis / 1_000.0,
                    ),
                )
            }
        }

        val weighInRows = rows(listOf("Timestamp (UTC)", "Weight (kg)"))
        WorkoutStorage.loadWeighIns(context).sortedBy { it.first }.forEach { (time, weight) ->
            weighInRows.put(row(timestamp(time), weight))
        }

        val routineRows = rows(
            listOf(
                "Routine", "Activity", "Timer mode", "Work minutes", "Rest minutes", "Setup seconds",
                "Strength rest seconds", "Set number", "Exercise", "Reps or jumps", "Weight (kg)",
            ),
        )
        templates.sortedBy { it.name.lowercase() }.forEach { template ->
            val routineSets = template.sets.ifEmpty { listOf(WorkoutSet(0, "", "", "")) }
            routineSets.forEach { set ->
                routineRows.put(
                    row(
                        template.name,
                        template.activityType,
                        template.cardioMode,
                        template.runtimeMinutes,
                        template.restMinutes,
                        template.prepSeconds,
                        template.restTimerSeconds,
                        set.id.takeIf { it > 0 }?.toString().orEmpty(),
                        set.exercise,
                        set.reps,
                        set.weight,
                    ),
                )
            }
        }
        return listOf(
            "Workouts" to workoutRows,
            "Sets" to setRows,
            "Weigh-ins" to weighInRows,
            "Routines" to routineRows,
        )
    }

    private fun request(method: String, url: String, accessToken: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val apiMessage = runCatching { JSONObject(responseText).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IOException(apiMessage?.takeIf { it.isNotBlank() } ?: "Google Sheets request failed (HTTP $status).")
            }
            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun rows(headers: List<String>) = JSONArray().put(row(*headers.toTypedArray()))

    private fun row(vararg values: Any?): JSONArray = JSONArray().apply {
        values.forEach { value -> put(value ?: "") }
    }

    private fun timestamp(value: Long): String = Instant.ofEpochMilli(value).toString()

    private fun urlFor(id: String) = "https://docs.google.com/spreadsheets/d/$id/edit"

    private fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
