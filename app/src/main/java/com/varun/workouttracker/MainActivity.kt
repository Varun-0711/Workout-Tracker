package com.varun.workouttracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.View
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import androidx.core.content.ContextCompat

private val Canvas = Color(0xFFF5F3EF)
private val Ink = Color(0xFF292B2E)
private val Muted = Color(0xFF77777A)
private val Forest = Color(0xFF45494B)
private val Leaf = Color(0xFF737D78)
private val Line = Color(0xFFE0DDD7)
private val SoftGreen = Color(0xFFEAECE8)
private val CalendarGreen = Color(0xFF6E947A)
private val StarterExercises = listOf(
    "Bench Press", "Squat", "Deadlift", "Overhead Press", "Lat Pulldown",
    "Dumbbell Row", "Push-up", "Pull-up", "Chin-up", "Dips", "Dumbbell Bench Press",
    "Incline Bench Press", "Front Squat", "Romanian Deadlift", "Leg Press", "Leg Curl",
    "Leg Extension", "Lateral Raise", "Bicep Curl", "Hammer Curl", "Triceps Pushdown",
    "Seated Cable Row", "Chest Fly", "Hip Thrust", "Lunge", "Calf Raise", "Plank",
    "Bodyweight Squat", "Bodyweight Lunge", "Bodyweight Calf Raise",
    "Crunches", "Burpee", "Mountain Climber", "Jumping Jack", "Glute Bridge",
)
private val ActivityTypes = ActivityCalories.activities.map { it.name }
private val GpsActivityTypes = setOf("Running", "Jogging", "Walking", "Hiking", "Cycling")
private fun isGpsActivity(activity: String) = activity in GpsActivityTypes
private val BodyweightExerciseNames = setOf(
    "pushup", "pullup", "chinup", "dip", "dips", "plank", "burpee", "airsquat",
    "bodyweightsquat", "jumpingjack", "mountainclimber", "situp", "situps",
    "crunch", "crunches", "stepup", "boxjump", "glutebridge", "legraise",
    "mountainclimbers", "jumpingjacks", "highknees", "pushupknees", "wallsit", "superman",
)

private fun isBodyweightExercise(exercise: String): Boolean {
    val normalized = exercise.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    if (normalized.startsWith("weighted")) return false
    return normalized in BodyweightExerciseNames || "bodyweight" in normalized || normalized.startsWith("bw")
}

private fun WorkoutSet.needsWeight(): Boolean = !isBodyweightExercise(exercise)

private fun List<WorkoutSet>.hasMissingRequiredWeights(): Boolean =
    any { it.needsWeight() && it.weight.toDoubleOrNull() == null }

private enum class AppTab { HOME, WORKOUT, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { WorkoutTrackerApp() }
    }
}

@Composable
private fun WorkoutTrackerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var workout by remember { mutableStateOf(WorkoutStorage.load(context)) }
    var bodyWeightKg by remember { mutableStateOf(WorkoutStorage.loadBodyWeightKg(context)) }
    var stepCount by remember { mutableLongStateOf(StepCounterService.stepPreferences(context).getLong(StepCounterService.KEY_STEPS, 0L)) }
    var stepTracking by remember { mutableStateOf(StepCounterService.stepPreferences(context).getBoolean(StepCounterService.KEY_TRACKING, false)) }
    var stepGoal by remember { mutableStateOf(StepCounterService.stepPreferences(context).getInt(StepCounterService.KEY_GOAL_STEPS, 8_000)) }
    var pushupGoal by remember { mutableStateOf(DailyGoalStorage.pushupGoal(context)) }
    var profileName by remember { mutableStateOf(ProfileStorage.name(context)) }
    var profileHeight by remember { mutableStateOf(ProfileStorage.heightCm(context)?.toString().orEmpty()) }
    var profileAge by remember { mutableStateOf(ProfileStorage.age(context)?.toString().orEmpty()) }
    var manualPushupsToday by remember { mutableStateOf(DailyGoalStorage.manualPushupsToday(context)) }
    var gpsDistanceMeters by remember { mutableDoubleStateOf(RunGpsService.preferences(context).getFloat(RunGpsService.KEY_DISTANCE_METERS, 0f).toDouble()) }
    var gpsPaceSecondsPerKm by remember { mutableDoubleStateOf(RunGpsService.preferences(context).getFloat(RunGpsService.KEY_PACE_SECONDS_PER_KM, 0f).toDouble()) }
    var gpsTracking by remember { mutableStateOf(RunGpsService.preferences(context).getBoolean(RunGpsService.KEY_TRACKING, false)) }
    var gpsHasFix by remember { mutableStateOf(RunGpsService.preferences(context).getBoolean(RunGpsService.KEY_HAS_FIX, false)) }
    var gpsStatus by remember { mutableStateOf(RunGpsService.preferences(context).getString(RunGpsService.KEY_STATUS, "idle") ?: "idle") }
    var gpsActivityType by remember { mutableStateOf(RunGpsService.preferences(context).getString("activity_type", "Running") ?: "Running") }
    var pendingGpsStart by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var selectedDay by remember { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var calendarMonth by remember { mutableLongStateOf(monthStart(System.currentTimeMillis())) }
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }
    var customExercise by remember { mutableStateOf("") }
    var showActivityPicker by remember { mutableStateOf(false) }
    var templates by remember { mutableStateOf(WorkoutStorage.loadTemplates(context)) }
    var showTemplateNameDialog by remember { mutableStateOf(false) }
    var templateNameDraft by remember { mutableStateOf("") }
    var pendingTemplate by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var pendingTemplateDeletion by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var showRoutineBuilder by remember { mutableStateOf(false) }
    var routineBuilderStep by remember { mutableStateOf("details") }
    var routineNameDraft by remember { mutableStateOf("") }
    var routineExerciseDraft by remember { mutableStateOf("") }
    var routineSetsDraft by remember { mutableStateOf(emptyList<WorkoutSet>()) }
    var routineRestSecondsDraft by remember { mutableStateOf(90) }
    var requireActivityChoice by remember {
        mutableStateOf(workout.activityType == "Choose activity" || workout.finished)
    }
    val todaySteps = if (StepCounterService.stepPreferences(context).getString(StepCounterService.KEY_DATE, null) == StepCounterService.todayKey()) stepCount else 0L
    val estimatedStepCalories = bodyWeightKg?.let {
        ActivityCalories.estimateStepCalories(todaySteps, it, profileHeight.toIntOrNull(), profileAge.toIntOrNull())
    } ?: 0
    val estimatedManualPushupCalories = bodyWeightKg?.let {
        ActivityCalories.estimatePushupCalories(manualPushupsToday, it, profileHeight.toIntOrNull(), profileAge.toIntOrNull())
    } ?: 0
    val startStepService = {
        ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java).setAction(StepCounterService.ACTION_START))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startStepService() }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startStepService()
        }
    }
    val requestStepTracking = {
        if (!StepCounterService.isSupported(context)) {
            android.widget.Toast.makeText(context, "This phone does not have a step counter sensor.", android.widget.Toast.LENGTH_LONG).show()
        } else if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startStepService()
    }
    val toggleStepTracking = {
        if (stepTracking) context.startService(Intent(context, StepCounterService::class.java).setAction(StepCounterService.ACTION_STOP))
        else requestStepTracking()
    }
    val startRunGpsService: (String) -> Unit = { activity ->
        gpsActivityType = activity
        ContextCompat.startForegroundService(context, Intent(context, RunGpsService::class.java)
            .setAction(RunGpsService.ACTION_START).putExtra(RunGpsService.EXTRA_ACTIVITY_TYPE, activity))
    }
    val runLocationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (pendingGpsStart) {
            pendingGpsStart = false
            val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (allowed) {
                RunGpsService.resetSession(context)
                gpsDistanceMeters = 0.0
                gpsPaceSecondsPerKm = 0.0
                gpsHasFix = false
                workout = workout.startActivityTimer(now)
                startRunGpsService(workout.activityType)
            } else {
                android.widget.Toast.makeText(context, "Location permission was not granted. The run timer will start without GPS.", android.widget.Toast.LENGTH_LONG).show()
                workout = workout.startActivityTimer(now)
            }
        }
    }
    DisposableEffect(context) {
        val prefs = StepCounterService.stepPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { shared, _ ->
            stepCount = shared.getLong(StepCounterService.KEY_STEPS, 0L)
            stepTracking = shared.getBoolean(StepCounterService.KEY_TRACKING, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(context) {
        val prefs = RunGpsService.preferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { shared, _ ->
            gpsDistanceMeters = shared.getFloat(RunGpsService.KEY_DISTANCE_METERS, 0f).toDouble()
            gpsPaceSecondsPerKm = shared.getFloat(RunGpsService.KEY_PACE_SECONDS_PER_KM, 0f).toDouble()
            gpsTracking = shared.getBoolean(RunGpsService.KEY_TRACKING, false)
            gpsHasFix = shared.getBoolean(RunGpsService.KEY_HAS_FIX, false)
            gpsStatus = shared.getString(RunGpsService.KEY_STATUS, "idle") ?: "idle"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(workout) { WorkoutStorage.save(context, workout) }

    val update: ((WorkoutState) -> WorkoutState) -> Unit = { transform ->
        workout = transform(workout)
    }
    val startTemplate: (WorkoutTemplate) -> Unit = { template ->
        context.startService(Intent(context, RunGpsService::class.java).setAction(RunGpsService.ACTION_STOP))
        workout = WorkoutStorage.newWorkout(now, workout.history, template.activityType).copy(
            title = template.name,
            sets = template.sets.mapIndexed { index, set -> set.copy(id = index + 1, completed = false) },
            runtimeMinutes = template.runtimeMinutes,
            restMinutes = template.restMinutes,
            prepSeconds = template.prepSeconds,
            cardioMode = template.cardioMode,
            restTimerSeconds = template.restTimerSeconds,
        )
        selectedTab = AppTab.WORKOUT
        requireActivityChoice = false
        showActivityPicker = false
    }
    val addRoutineExercise: (String) -> Unit = { rawName ->
        val name = rawName.trim()
        if (name.isNotEmpty()) {
            val firstId = (routineSetsDraft.maxOfOrNull { it.id } ?: 0) + 1
            val bodyweight = isBodyweightExercise(name)
            val reps = if (bodyweight) "10" else "8"
            routineSetsDraft = routineSetsDraft + (0 until 3).map { offset ->
                WorkoutSet(firstId + offset, name, reps, "")
            }
            routineExerciseDraft = ""
            routineBuilderStep = "details"
        }
    }
    LaunchedEffect(now, workout.cardioMode, workout.intervalPhase, workout.intervalEndsAt, workout.pausedAt, workout.timerStopped) {
        if (
            workout.activityType != "Strength" && workout.cardioMode == "intervals" && workout.timerStarted && !workout.timerStopped &&
            workout.pausedAt == 0L && workout.intervalEndsAt > 0L && now >= workout.intervalEndsAt
        ) {
            val previousEnd = workout.intervalEndsAt
            val nextPhase = when (workout.intervalPhase) {
                "countdown" -> "work"
                "work" -> "rest"
                "rest" -> "work"
                else -> "idle"
            }
            val nextDuration = when (nextPhase) {
                "work" -> (workout.runtimeMinutes.toLongOrNull() ?: 3L).coerceIn(1L, 999L) * 60_000L
                "rest" -> (workout.restMinutes.toLongOrNull() ?: 2L).coerceIn(1L, 999L) * 60_000L
                else -> 0L
            }
            update { state ->
                state.copy(
                    intervalPhase = nextPhase,
                    intervalEndsAt = if (nextDuration > 0L) previousEnd + nextDuration else 0L,
                    intervalRound = if (state.intervalPhase == "rest") state.intervalRound + 1 else state.intervalRound,
                )
            }
            playIntervalBeep()
        }
    }
    val addExercise: (String) -> Unit = { exercise ->
        val name = exercise.trim()
        if (name.isNotEmpty() && !workout.finished && workout.activityType == "Strength") {
            val previous = workout.sets.lastOrNull { it.exercise.equals(name, ignoreCase = true) }
            val nextId = (workout.sets.maxOfOrNull { it.id } ?: 0) + 1
            val bodyweight = isBodyweightExercise(name)
            update { state ->
                state.copy(
                    sets = state.sets + WorkoutSet(
                        id = nextId,
                        exercise = name,
                        reps = previous?.reps ?: if (bodyweight) "10" else "8",
                        weight = previous?.weight ?: "",
                    ),
                )
            }
        }
        showExerciseDialog = false
        customExercise = ""
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (selectedTab == AppTab.WORKOUT) {
                    if (requireActivityChoice || workout.activityType == "Choose activity") {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("What are you training today?", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Choose an activity to get the right timer and logging tools.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                                Button(
                                    onClick = {
                                        routineNameDraft = ""
                                        routineSetsDraft = emptyList()
                                        routineExerciseDraft = ""
                                        routineRestSecondsDraft = 90
                                        routineBuilderStep = "details"
                                        showRoutineBuilder = true
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Forest),
                                ) { Text("＋  Create workout routine") }
                                Button(
                            onClick = { showActivityPicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                                ) { Text("Choose activity") }
                                if (templates.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        Text("SAVED ROUTINES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                        templates.forEach { template ->
                                            WorkoutTemplateCard(
                                                template = template,
                                                onStart = {
                                                    if (!workout.finished && workout.activityType != "Choose activity") {
                                                        pendingTemplate = template
                                                    } else {
                                                        startTemplate(template)
                                                    }
                                                },
                                                onDelete = { pendingTemplateDeletion = template },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else item {
                        WorkoutStartCard(
                            workout = workout,
                            now = now,
                            onChooseActivity = {
                                requireActivityChoice = true
                                showActivityPicker = true
                            },
                            onStartTimer = {
                                if (isGpsActivity(workout.activityType)) {
                                    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasLocation) {
                                        RunGpsService.resetSession(context)
                                        gpsDistanceMeters = 0.0
                                        gpsPaceSecondsPerKm = 0.0
                                        gpsHasFix = false
                                        update { it.startActivityTimer(now) }
                                        startRunGpsService(workout.activityType)
                                    } else {
                                        RunGpsService.resetSession(context)
                                        gpsDistanceMeters = 0.0
                                        gpsPaceSecondsPerKm = 0.0
                                        gpsHasFix = false
                                        pendingGpsStart = true
                                        runLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                } else update { it.startActivityTimer(now) }
                            },
                            onRename = {
                                draftTitle = workout.title
                                showRenameDialog = true
                            },
                            onTogglePause = {
                                val wasPaused = workout.pausedAt > 0L
                                update { it.togglePause(now) }
                                if (isGpsActivity(workout.activityType)) {
                                    if (wasPaused) startRunGpsService(workout.activityType)
                                    else context.startService(Intent(context, RunGpsService::class.java).setAction(RunGpsService.ACTION_STOP))
                                }
                            },
                            onStopTimer = {
                                update { it.stopActivityTimer(now) }
                                if (isGpsActivity(workout.activityType)) context.startService(Intent(context, RunGpsService::class.java).setAction(RunGpsService.ACTION_STOP))
                            },
                            gpsDistanceMeters = gpsDistanceMeters,
                            gpsPaceSecondsPerKm = gpsPaceSecondsPerKm,
                            gpsTracking = gpsTracking,
                            gpsHasFix = gpsHasFix,
                            gpsStatus = gpsStatus,
                            gpsActivityType = workout.activityType,
                            gpsCalories = bodyWeightKg?.let { ActivityCalories.estimateDistanceCalories(workout.activityType, gpsDistanceMeters, it) },
                        )
                    }
                    if (!requireActivityChoice && workout.activityType == "Strength") item {
                        RestTimerSettings(
                            seconds = workout.restTimerSeconds,
                            editable = !workout.finished && workout.restUntil <= now && workout.pausedRestRemaining == 0L,
                            onChange = { value -> update { it.copy(restTimerSeconds = value) } },
                        )
                    }
                    if (!requireActivityChoice && !workout.finished && workout.activityType == "Strength" && workout.sets.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick = {
                                    templateNameDraft = workout.title
                                    showTemplateNameDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save as routine", color = CalendarGreen, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    if (!requireActivityChoice && workout.activityType != "Strength" && workout.activityType != "Choose activity") item {
                        CardioTimerSettings(
                            cardioMode = workout.cardioMode,
                            runtimeMinutes = workout.runtimeMinutes,
                            restMinutes = workout.restMinutes,
                            prepSeconds = workout.prepSeconds,
                            editable = !workout.timerStarted || workout.timerStopped,
                            onRuntimeChange = { value -> update { it.copy(runtimeMinutes = value) } },
                            onRestChange = { value -> update { it.copy(restMinutes = value) } },
                            onPrepChange = { value -> update { it.copy(prepSeconds = value) } },
                            onModeChange = { mode ->
                                update {
                                    it.copy(
                                        cardioMode = mode,
                                        timerStarted = false,
                                        timerStopped = false,
                                        pausedAt = 0L,
                                        elapsedBeforePause = 0L,
                                        intervalPhase = "idle",
                                        intervalEndsAt = 0L,
                                        pausedIntervalRemaining = 0L,
                                        intervalRound = 1,
                                        restUntil = 0L,
                                        pausedRestRemaining = 0L,
                                    )
                                }
                            },
                        )
                    }
                    val restSeconds = when {
                        workout.pausedAt > 0L -> (workout.pausedRestRemaining / 1_000).toInt()
                        workout.restUntil > now -> ((workout.restUntil - now + 999) / 1_000).toInt()
                        else -> 0
                    }
                    if (!requireActivityChoice && workout.activityType == "Strength" && restSeconds > 0 && !workout.finished) {
                        item {
                            RestTimerCard(
                                seconds = restSeconds,
                                paused = workout.pausedAt > 0L,
                                onSkip = {
                                    update { state -> state.copy(restUntil = 0L, pausedRestRemaining = 0L) }
                                },
                                onAddTime = {
                                    update { state ->
                                        if (state.pausedAt > 0L) {
                                            state.copy(pausedRestRemaining = state.pausedRestRemaining + 30_000L)
                                        } else {
                                            state.copy(restUntil = state.restUntil + 30_000L)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (!requireActivityChoice && workout.activityType == "Strength") item {
                        SectionHeading(
                            title = "TODAY'S WORKOUT",
                            trailing = "${workout.sets.count { it.completed }} / ${workout.sets.size} sets",
                        )
                    }
                    if (!requireActivityChoice && workout.activityType == "Strength") {
                        items(workout.sets.map { it.exercise }.distinct(), key = { it }) { exercise ->
                            val exerciseSets = workout.sets.filter { it.exercise == exercise }
                            ExerciseGroupCard(
                                exercise = exercise,
                                sets = exerciseSets,
                                editable = !workout.finished,
                                onRepsChange = { set, value -> update { state -> state.replace(set.copy(reps = value)) } },
                                onWeightChange = { set, value -> update { state -> state.replace(set.copy(weight = value)) } },
                                onComplete = { set ->
                                    if (!workout.finished) {
                                        update { state ->
                                            val completing = !set.completed
                                            val changedState = state.replace(set.copy(completed = completing))
                                            val hasAnotherSet = changedState.sets.any { !it.completed }
                                            changedState.copy(
                                                restUntil = if (completing && hasAnotherSet && state.restTimerSeconds > 0 && state.pausedAt == 0L) System.currentTimeMillis() + state.restTimerSeconds * 1_000L else 0L,
                                                pausedRestRemaining = if (completing && hasAnotherSet && state.restTimerSeconds > 0 && state.pausedAt > 0L) state.restTimerSeconds * 1_000L else 0L,
                                            )
                                        }
                                    }
                                },
                                onRemoveSet = { set -> update { state -> state.copy(sets = state.sets.filterNot { it.id == set.id }) } },
                                onAddSet = {
                                    val last = exerciseSets.lastOrNull()
                                    val nextId = (workout.sets.maxOfOrNull { it.id } ?: 0) + 1
                                    update { state ->
                                        state.copy(sets = state.sets + WorkoutSet(nextId, exercise, last?.reps ?: "8", last?.weight ?: ""))
                                    }
                                },
                                onRemoveExercise = { update { state -> state.copy(sets = state.sets.filterNot { it.exercise == exercise }) } },
                            )
                        }
                    }

                    if (!requireActivityChoice && !workout.finished && workout.activityType == "Strength") {
                        item {
                            Button(
                                onClick = { showExerciseDialog = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Forest),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) { Text("＋   Add exercise", fontWeight = FontWeight.SemiBold) }
                        }
                    }

                        if (!requireActivityChoice && workout.activityType != "Choose activity") item {
                        SessionFooter(
                            workout = workout,
                            onFinish = {
                                val duration = workout.elapsedMillis(now)
                                val weightForSession = WorkoutStorage.bodyWeightBefore(context, workout.startedAt)
                                val savedRunDistance = RunGpsService.preferences(context).getFloat(RunGpsService.KEY_DISTANCE_METERS, 0f).toDouble()
                                val runDistance = savedRunDistance.takeIf { isGpsActivity(workout.activityType) && it > 0.0 }
                                val averagePace = runDistance?.let { distance -> duration.toDouble() / distance }
                                val record = WorkoutRecord(
                                    title = workout.title,
                                    startedAt = workout.startedAt,
                                    endedAt = now,
                                    elapsedMillis = duration,
                                    sets = workout.sets,
                                    activityType = workout.activityType,
                                    intensity = workout.intensity,
                                    weightKg = weightForSession,
                                    distanceMeters = runDistance,
                                    averagePaceSecondsPerKm = averagePace,
                                    estimatedCalories = weightForSession?.let {
                                        if (runDistance != null) ActivityCalories.estimateDistanceCalories(workout.activityType, runDistance, it)
                                        else ActivityCalories.estimateCalories(
                                                workout.activityType,
                                                workout.intensity,
                                                it,
                                                duration,
                                                heightCm = ProfileStorage.heightCm(context),
                                                ageYears = ProfileStorage.age(context),
                                            )
                                    },
                                )
                                if (isGpsActivity(workout.activityType)) context.startService(Intent(context, RunGpsService::class.java).setAction(RunGpsService.ACTION_STOP))
                                update { state ->
                                    state.copy(
                                        history = state.history + record,
                                        finished = true,
                                        elapsedBeforePause = duration,
                                        pausedAt = now,
                                        timerStopped = true,
                                        intervalPhase = "stopped",
                                        intervalEndsAt = 0L,
                                        pausedIntervalRemaining = 0L,
                                        restUntil = 0L,
                                        pausedRestRemaining = 0L,
                                    )
                                }
                            },
                        )
                    }
                    } else if (selectedTab == AppTab.HISTORY) {
                        item { HistoryHeader(workout.history.size) }
                        item { PersonalRecordsCard(workout.history) }
                        item {
                            WorkoutCalendar(
                                month = calendarMonth,
                                selectedDay = selectedDay,
                                history = workout.history,
                                today = dayStart(now),
                                onMonthChange = { calendarMonth = it },
                                onDaySelected = { selectedDay = it },
                            )
                        }
                        item {
                            DayHistory(
                                selectedDay = selectedDay,
                                history = workout.history,
                                today = dayStart(now),
                            )
                        }
                    } else if (selectedTab == AppTab.HOME) {
                        item { HomeHeader(now) }
                        item {
                            HomeTodayCard(
                                history = workout.history,
                                today = dayStart(now),
                                onStartWorkout = {
                                    selectedTab = AppTab.WORKOUT
                                    if (workout.finished || workout.activityType == "Choose activity") {
                                        requireActivityChoice = true
                                    } else {
                                        requireActivityChoice = false
                                    }
                                },
                                continueWorkout = !workout.finished && workout.activityType != "Choose activity",
                            )
                        }
                        item {
                            Text("TODAY'S GOALS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        }
                        item {
                            StepCounterCard(
                                steps = todaySteps,
                                goal = stepGoal,
                                sensorAvailable = StepCounterService.isSupported(context),
                                estimatedCalories = estimatedStepCalories,
                                hasBodyWeight = bodyWeightKg != null,
                            )
                        }
                        item {
                            PushupGoalCard(
                                completed = manualPushupsToday + workout.history
                                    .filter { dayStart(it.startedAt) == dayStart(now) }
                                    .sumOf { record -> pushupReps(record.sets) } +
                                    if (!workout.finished && dayStart(workout.startedAt) == dayStart(now)) pushupReps(workout.sets) else 0,
                                manuallyLogged = manualPushupsToday,
                                goal = pushupGoal,
                                onLogReps = { reps -> manualPushupsToday = DailyGoalStorage.logManualPushups(context, reps) },
                            )
                        }
                        item {
                            BodyWeightAndCaloriesCard(
                                weightKg = bodyWeightKg,
                                heightCm = profileHeight.toIntOrNull(),
                                ageYears = profileAge.toIntOrNull(),
                                history = workout.history,
                                today = dayStart(now),
                                additionalCalories = estimatedStepCalories + estimatedManualPushupCalories,
                            )
                        }
                    } else {
                        item { SettingsScreenHeader() }
                        item {
                            SettingsPersonalInfoCard(
                                name = profileName,
                                heightCm = profileHeight,
                                age = profileAge,
                                weightKg = bodyWeightKg,
                                onSave = { name, height, age, weight ->
                                    profileName = name
                                    profileHeight = height
                                    profileAge = age
                                    ProfileStorage.save(context, name, height.toIntOrNull(), age.toIntOrNull())
                                    bodyWeightKg = weight
                                    WorkoutStorage.saveBodyWeightKg(context, weight)
                                },
                            )
                        }
                        item {
                            SettingsGoalsCard(
                                stepGoal = stepGoal,
                                pushupGoal = pushupGoal,
                                onStepGoalChange = { value ->
                                    stepGoal = value.coerceIn(1_000, 50_000)
                                    StepCounterService.stepPreferences(context).edit().putInt(StepCounterService.KEY_GOAL_STEPS, stepGoal).apply()
                                },
                                onPushupGoalChange = { value ->
                                    pushupGoal = value.coerceIn(5, 1_000)
                                    DailyGoalStorage.savePushupGoal(context, pushupGoal)
                                },
                            )
                        }
                        item {
                            SettingsTrackingCard(
                                tracking = stepTracking,
                                sensorAvailable = StepCounterService.isSupported(context),
                                locationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                                activityPermission = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
                                notificationPermission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                                onToggleTracking = { toggleStepTracking() },
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onManagePermissions = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                                },
                            )
                        }
                    }
                }
                AppNavigation(selectedTab = selectedTab, onSelect = {
                    selectedTab = it
                    if (it == AppTab.WORKOUT) requireActivityChoice = true
                })
            }
        }
    }

        if (showActivityPicker) {
        AlertDialog(
            onDismissRequest = { showActivityPicker = false },
            title = { Text("Choose activity", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This starts a new session in that mode. Unsaved edits in the current session will be cleared.", color = Muted, fontSize = 13.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(ActivityTypes) { type ->
                            TextButton(
                                onClick = {
                                    workout = WorkoutStorage.newWorkout(now, workout.history, type).copy(title = if (type == "Strength") "Workout" else type)
                                    context.startService(Intent(context, RunGpsService::class.java).setAction(RunGpsService.ACTION_STOP))
                                    requireActivityChoice = false
                                    showActivityPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("${activityEmoji(type)}  $type", color = Forest, fontWeight = FontWeight.SemiBold)
                                    if (type == "Strength") Text("Rep and weight tracking", color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showRoutineBuilder) {
        AlertDialog(
            onDismissRequest = { showRoutineBuilder = false },
            title = {
                Text(
                    if (routineBuilderStep == "details") "Create workout routine" else "Add an exercise",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (routineBuilderStep == "details") {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = routineNameDraft,
                            onValueChange = { routineNameDraft = it.take(40) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Routine name") },
                        )
                        Text("Exercises", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        routineSetsDraft.map { it.exercise }.distinct().forEach { exercise ->
                            val count = routineSetsDraft.count { it.exercise == exercise }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(exercise, modifier = Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(
                                    onClick = {
                                        if (count > 1) {
                                            val lastId = routineSetsDraft.last { it.exercise == exercise }.id
                                            routineSetsDraft = routineSetsDraft.filterNot { it.id == lastId }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) { Text("−", color = Forest, fontSize = 17.sp) }
                                Text("$count sets", color = Muted, fontSize = 11.sp)
                                TextButton(
                                    onClick = {
                                        val last = routineSetsDraft.last { it.exercise == exercise }
                                        val nextId = (routineSetsDraft.maxOfOrNull { it.id } ?: 0) + 1
                                        routineSetsDraft = routineSetsDraft + last.copy(id = nextId, completed = false)
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) { Text("+", color = Forest, fontSize = 17.sp) }
                                TextButton(
                                    onClick = { routineSetsDraft = routineSetsDraft.filterNot { it.exercise == exercise } },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) { Text("Remove", color = Muted, fontSize = 10.sp) }
                            }
                            routineSetsDraft.filter { it.exercise == exercise }.forEachIndexed { setIndex, set ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${setIndex + 1}", color = Muted, fontSize = 10.sp)
                                    NumberField(
                                        label = "REPS",
                                        value = set.reps,
                                        editable = true,
                                        onValueChange = { value ->
                                            routineSetsDraft = routineSetsDraft.map { if (it.id == set.id) it.copy(reps = value) else it }
                                        },
                                        modifier = Modifier.weight(1f),
                                        onAdjust = { delta ->
                                            val next = ((set.reps.toIntOrNull() ?: 0) + delta).coerceAtLeast(0).toString()
                                            routineSetsDraft = routineSetsDraft.map { if (it.id == set.id) it.copy(reps = next) else it }
                                        },
                                    )
                                    NumberField(
                                        label = if (set.needsWeight()) "WEIGHT *" else "WEIGHT OPTIONAL",
                                        value = set.weight,
                                        editable = true,
                                        onValueChange = { value ->
                                            routineSetsDraft = routineSetsDraft.map { if (it.id == set.id) it.copy(weight = value) else it }
                                        },
                                        suffix = "kg",
                                        modifier = Modifier.weight(1f),
                                        allowDecimal = true,
                                        onAdjust = { delta ->
                                            val next = ((set.weight.toDoubleOrNull() ?: 0.0) + delta * 2.5).coerceAtLeast(0.0)
                                            val value = if (next % 1.0 == 0.0) next.toInt().toString() else "%.1f".format(Locale.US, next)
                                            routineSetsDraft = routineSetsDraft.map { if (it.id == set.id) it.copy(weight = value) else it }
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { routineBuilderStep = "exercise" }, modifier = Modifier.fillMaxWidth()) {
                            Text("＋  Add exercise", color = CalendarGreen, fontWeight = FontWeight.SemiBold)
                        }
                        NumberField(
                            label = "REST AFTER EACH SET",
                            value = routineRestSecondsDraft.toString(),
                            editable = true,
                            onValueChange = { value -> value.toIntOrNull()?.let { routineRestSecondsDraft = it.coerceIn(0, 999) } },
                            modifier = Modifier.fillMaxWidth(),
                            suffix = "sec",
                            onAdjust = { delta -> routineRestSecondsDraft = (routineRestSecondsDraft + delta * 15).coerceIn(0, 999) },
                        )
                        if (routineSetsDraft.hasMissingRequiredWeights()) {
                            Text("Enter a weight for every set marked * to save this routine.", color = Muted, fontSize = 11.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose an exercise. It starts with three sets; you can adjust the count next.", color = Muted, fontSize = 12.sp)
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(StarterExercises) { exercise ->
                                TextButton(onClick = { addRoutineExercise(exercise) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(exercise, color = Forest, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        OutlinedTextField(
                            value = routineExerciseDraft,
                            onValueChange = { routineExerciseDraft = it.take(40) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Custom exercise") },
                        )
                    }
                }
            },
            confirmButton = {
                if (routineBuilderStep == "details") {
                    TextButton(
                        onClick = {
                            val name = routineNameDraft.trim()
                            if (name.isNotEmpty() && routineSetsDraft.isNotEmpty()) {
                                templates = WorkoutStorage.saveTemplate(
                                    context,
                                    WorkoutTemplate(
                                        name = name,
                                        activityType = "Strength",
                                        sets = routineSetsDraft.map { it.copy(completed = false) },
                                        restTimerSeconds = routineRestSecondsDraft,
                                    ),
                                )
                                showRoutineBuilder = false
                            }
                        },
                        enabled = routineNameDraft.isNotBlank() && routineSetsDraft.isNotEmpty() && !routineSetsDraft.hasMissingRequiredWeights(),
                    ) { Text("Save routine", color = Forest) }
                } else {
                    TextButton(onClick = { routineBuilderStep = "details" }) { Text("Done", color = Forest) }
                }
            },
            dismissButton = {
                if (routineBuilderStep == "details") {
                    TextButton(onClick = { showRoutineBuilder = false }) { Text("Cancel", color = Muted) }
                } else {
                    TextButton(
                        onClick = { addRoutineExercise(routineExerciseDraft) },
                        enabled = routineExerciseDraft.isNotBlank(),
                    ) { Text("Add custom", color = Forest) }
                }
            },
        )
    }

    if (showExerciseDialog) {
        AlertDialog(
            onDismissRequest = { showExerciseDialog = false },
            title = { Text("Add an exercise", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose a starter or enter your own.", color = Muted, fontSize = 13.sp)
                    StarterExercises.forEach { exercise ->
                        TextButton(onClick = { addExercise(exercise) }, modifier = Modifier.fillMaxWidth()) {
                            Text(exercise, color = Forest, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    OutlinedTextField(
                        value = customExercise,
                        onValueChange = { customExercise = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your exercise") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { addExercise(customExercise) }, enabled = customExercise.isNotBlank()) {
                    Text("Add custom", color = Forest)
                }
            },
            dismissButton = { TextButton(onClick = { showExerciseDialog = false }) { Text("Cancel", color = Muted) } },
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Name this workout", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Workout name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = draftTitle.trim()
                        if (title.isNotEmpty()) update { it.copy(title = title) }
                        showRenameDialog = false
                    },
                ) { Text("Save", color = Forest) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Muted) } },
        )
    }

    if (showTemplateNameDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateNameDialog = false },
            title = { Text("Save routine", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = templateNameDraft,
                        onValueChange = { templateNameDraft = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Routine name") },
                    )
                    if (workout.sets.hasMissingRequiredWeights()) {
                        Text("Enter weights for weighted exercises before saving this routine.", color = Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = templateNameDraft.trim()
                        if (name.isNotEmpty()) {
                            templates = WorkoutStorage.saveTemplate(
                                context,
                                WorkoutTemplate(
                                    name = name,
                                    activityType = workout.activityType,
                                    sets = workout.sets.map { it.copy(completed = false) },
                                    runtimeMinutes = workout.runtimeMinutes,
                                    restMinutes = workout.restMinutes,
                                    prepSeconds = workout.prepSeconds,
                                    cardioMode = workout.cardioMode,
                                    restTimerSeconds = workout.restTimerSeconds,
                                ),
                            )
                            showTemplateNameDialog = false
                        }
                    },
                    enabled = templateNameDraft.isNotBlank() && !workout.sets.hasMissingRequiredWeights(),
                ) { Text("Save", color = Forest) }
            },
            dismissButton = { TextButton(onClick = { showTemplateNameDialog = false }) { Text("Cancel", color = Muted) } },
        )
    }

    pendingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            title = { Text("Start ${template.name}?", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("This will replace the current unsaved workout.", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        startTemplate(template)
                        pendingTemplate = null
                    },
                ) { Text("Start routine", color = Forest) }
            },
            dismissButton = { TextButton(onClick = { pendingTemplate = null }) { Text("Cancel", color = Muted) } },
        )
    }

    pendingTemplateDeletion?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingTemplateDeletion = null },
            title = { Text("Remove saved routine?", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("${template.name} will be removed from your saved workouts.", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        templates = WorkoutStorage.deleteTemplate(context, template.name)
                        pendingTemplateDeletion = null
                    },
                ) { Text("Remove", color = Color(0xFF9B3F3F)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplateDeletion = null }) { Text("Cancel", color = Muted) }
            },
        )
    }
}

private fun WorkoutState.replace(replacement: WorkoutSet) = copy(
    sets = sets.map { if (it.id == replacement.id) replacement else it },
)

private fun WorkoutState.elapsedMillis(now: Long): Long {
    if (activityType != "Strength" && !timerStarted) return elapsedBeforePause
    val frozen = pausedAt > 0L || timerStopped || finished
    return elapsedBeforePause + if (frozen) 0L else (now - resumedAt).coerceAtLeast(0L)
}

private fun WorkoutState.startActivityTimer(now: Long): WorkoutState = copy(
    startedAt = now,
    resumedAt = now,
    pausedAt = 0L,
    elapsedBeforePause = 0L,
    timerStarted = true,
    timerStopped = false,
    intervalPhase = if (cardioMode == "intervals") "countdown" else "stopwatch",
    intervalEndsAt = if (cardioMode == "intervals") now + (prepSeconds.toLongOrNull() ?: 10L).coerceIn(1L, 999L) * 1_000L else 0L,
    pausedIntervalRemaining = 0L,
    intervalRound = 1,
)

private fun WorkoutState.stopActivityTimer(now: Long): WorkoutState {
    if (!timerStarted || timerStopped || finished) return this
    return copy(
        elapsedBeforePause = elapsedMillis(now),
        pausedAt = now,
        timerStopped = true,
        intervalPhase = "stopped",
        intervalEndsAt = 0L,
        pausedIntervalRemaining = 0L,
        restUntil = 0L,
        pausedRestRemaining = 0L,
    )
}

private fun WorkoutState.togglePause(now: Long): WorkoutState {
    if (finished || timerStopped || (activityType != "Strength" && !timerStarted)) return this
    return if (pausedAt == 0L) {
        copy(
            pausedAt = now,
            elapsedBeforePause = elapsedMillis(now),
            pausedIntervalRemaining = (intervalEndsAt - now).coerceAtLeast(0L),
            intervalEndsAt = 0L,
            pausedRestRemaining = (restUntil - now).coerceAtLeast(0L),
            restUntil = 0L,
        )
    } else {
        copy(
            pausedAt = 0L,
            resumedAt = now,
            intervalEndsAt = if (pausedIntervalRemaining > 0L) now + pausedIntervalRemaining else 0L,
            pausedIntervalRemaining = 0L,
            restUntil = if (pausedRestRemaining > 0L) now + pausedRestRemaining else 0L,
            pausedRestRemaining = 0L,
        )
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.workout_logo),
            contentDescription = "Workout Tracker logo",
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text("FORM & FLOW", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun SettingsScreenHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Settings", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Personalize your profile, goals, and tracking.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsPersonalInfoCard(
    name: String,
    heightCm: String,
    age: String,
    weightKg: Double?,
    onSave: (String, String, String, Double?) -> Unit,
) {
    var draftName by remember(name) { mutableStateOf(name) }
    var draftHeight by remember(heightCm) { mutableStateOf(heightCm) }
    var draftAge by remember(age) { mutableStateOf(age) }
    var draftWeight by remember(weightKg) { mutableStateOf(weightKg?.toString().orEmpty()) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PERSONAL INFO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            OutlinedTextField(draftName, { draftName = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draftHeight, { draftHeight = it.filter(Char::isDigit).take(3) }, label = { Text("Height · cm") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(draftAge, { draftAge = it.filter(Char::isDigit).take(3) }, label = { Text("Age") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(draftWeight, { draftWeight = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { Text("Body weight · kg") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Text("Stored on this device. Weight is used for workout calorie estimates.", color = Muted, fontSize = 11.sp)
            Button(onClick = {
                val height = draftHeight.toIntOrNull()?.takeIf { it in 80..250 }?.toString().orEmpty()
                val ageValue = draftAge.toIntOrNull()?.takeIf { it in 13..120 }?.toString().orEmpty()
                draftHeight = height
                draftAge = ageValue
                onSave(draftName.trim(), height, ageValue, draftWeight.toDoubleOrNull()?.takeIf { it in 20.0..350.0 })
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                Text("Save personal info")
            }
        }
    }
}

@Composable
private fun SettingsGoalsCard(
    stepGoal: Int,
    pushupGoal: Int,
    onStepGoalChange: (Int) -> Unit,
    onPushupGoalChange: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DAILY GOALS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            GoalSettingRow("Steps", stepGoal, "steps", 500, 1_000, 50_000, onStepGoalChange)
            GoalSettingRow("Push-ups", pushupGoal, "reps", 5, 5, 1_000, onPushupGoalChange)
            Text("Progress stays visible on Home. Push-up sets in workouts count automatically.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GoalSettingRow(label: String, value: Int, unit: String, increment: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${value} $unit per day", color = Muted, fontSize = 12.sp)
        }
        TextButton(onClick = { onChange((value - increment).coerceAtLeast(minimum)) }) { Text("−", color = Forest, fontSize = 20.sp) }
        TextButton(onClick = { onChange((value + increment).coerceAtMost(maximum)) }) { Text("＋", color = Forest, fontSize = 20.sp) }
    }
}

@Composable
private fun SettingsTrackingCard(
    tracking: Boolean,
    sensorAvailable: Boolean,
    locationPermission: Boolean,
    activityPermission: Boolean,
    notificationPermission: Boolean,
    onToggleTracking: () -> Unit,
    onRequestNotifications: () -> Unit,
    onManagePermissions: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("STEP TRACKING & PERMISSIONS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text(if (tracking) "Step tracking is active in the background." else "Step tracking is paused.", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Step sensor: ${if (sensorAvailable) "Available" else "Not available on this device"}", color = Muted, fontSize = 12.sp)
            Text("Outdoor activity GPS: ${if (locationPermission) "Allowed" else "Ask when starting an activity"}", color = Muted, fontSize = 12.sp)
            Text("Physical activity permission: ${if (activityPermission) "Allowed" else "Not allowed"}", color = Muted, fontSize = 12.sp)
            Text("Notifications: ${if (notificationPermission) "Allowed / not required" else "Not allowed"}", color = Muted, fontSize = 12.sp)
            Button(onClick = onToggleTracking, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest), enabled = sensorAvailable) {
                Text(if (tracking) "Pause step tracking" else "Enable background step tracking")
            }
            if (!notificationPermission) TextButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                Text("Allow notifications", color = Forest)
            }
            TextButton(onClick = onManagePermissions, modifier = Modifier.fillMaxWidth()) {
                Text("Manage app permissions in Android settings", color = Forest)
            }
        }
    }
}

@Composable
private fun AppNavigation(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Canvas).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        NavigationButton("Home", selectedTab == AppTab.HOME, Modifier.weight(1f)) { onSelect(AppTab.HOME) }
        NavigationButton("Workout", selectedTab == AppTab.WORKOUT, Modifier.weight(1f)) { onSelect(AppTab.WORKOUT) }
        NavigationButton("History", selectedTab == AppTab.HISTORY, Modifier.weight(1f)) { onSelect(AppTab.HISTORY) }
        NavigationButton("Settings", selectedTab == AppTab.SETTINGS, Modifier.weight(1f)) { onSelect(AppTab.SETTINGS) }
    }
}

@Composable
private fun NavigationButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (selected) SoftGreen else Canvas, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Ink else Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun WorkoutTemplateCard(template: WorkoutTemplate, onStart: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(template.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${template.sets.map { it.exercise }.distinct().size} exercises · ${template.sets.size} sets · ${template.restTimerSeconds}s rest",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Remove", color = Muted, fontSize = 11.sp)
                }
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
            ) { Text("Start routine", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun WorkoutStartCard(
    workout: WorkoutState,
    now: Long,
    onChooseActivity: () -> Unit,
    onStartTimer: () -> Unit,
    onRename: () -> Unit,
    onTogglePause: () -> Unit,
    onStopTimer: () -> Unit,
    gpsDistanceMeters: Double,
    gpsPaceSecondsPerKm: Double,
    gpsTracking: Boolean,
    gpsHasFix: Boolean,
    gpsStatus: String,
    gpsCalories: Int?,
    gpsActivityType: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val intervalLabel = if (workout.cardioMode == "stopwatch") {
            when {
                workout.timerStopped -> "STOPWATCH STOPPED"
                !workout.timerStarted -> "STOPWATCH READY"
                workout.pausedAt > 0L -> "STOPWATCH PAUSED"
                else -> "STOPWATCH"
            }
        } else {
            when (workout.intervalPhase) {
                "countdown" -> "GET READY"
                "work" -> "WORK · ROUND ${workout.intervalRound}"
                "rest" -> "REST · ROUND ${workout.intervalRound}"
                "stopped" -> "TIMER STOPPED"
                else -> "READY TO START"
            }
        }
        val intervalRemaining = if (workout.pausedAt > 0L) {
            workout.pausedIntervalRemaining.coerceAtLeast(0L)
        } else {
            (workout.intervalEndsAt - now).coerceAtLeast(0L)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (workout.pausedAt > 0L) Muted else Leaf, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    workout.finished -> "WORKOUT COMPLETE"
                    workout.activityType != "Strength" && workout.timerStopped -> "TIMER STOPPED"
                    workout.pausedAt > 0L -> "SESSION PAUSED"
                    workout.activityType != "Strength" && !workout.timerStarted -> "READY TO START"
                    else -> "WORKOUT IN PROGRESS"
                },
                color = if (workout.pausedAt > 0L) Muted else Leaf,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
        TextButton(onClick = onChooseActivity) {
            Text(
                if (workout.finished) "＋  New workout" else "${workout.activityType.uppercase(Locale.getDefault())}  ▾  ·  Change activity",
                color = CalendarGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(workout.title, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!workout.finished) {
                TextButton(onClick = onRename) { Text("Edit", color = Leaf, fontSize = 12.sp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (workout.activityType != "Strength" && !workout.timerStarted) {
                Text("Ready when you are", color = Muted, fontSize = 13.sp)
            } else {
                Text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(workout.startedAt)), color = Muted, fontSize = 13.sp)
                Text("·", color = Muted)
                Text("Started ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(workout.startedAt))}", color = Muted, fontSize = 13.sp)
            }
        }
        if (workout.activityType != "Strength") Card(
            colors = CardDefaults.cardColors(containerColor = Forest),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(intervalLabel, color = Color(0xFFBFC3C5), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(5.dp))
                    val inIntervalPhase = workout.cardioMode == "intervals" && workout.intervalPhase in setOf("countdown", "work", "rest") && workout.timerStarted && !workout.timerStopped
                    val primaryTime = if (inIntervalPhase) intervalRemaining else workout.elapsedMillis(now)
                    Text(formatClock(primaryTime), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (workout.cardioMode == "intervals") {
                            "Elapsed ${formatClock(workout.elapsedMillis(now))}  ·  Round ${workout.intervalRound}"
                        } else {
                            "Continuous run · no rest intervals"
                        },
                        color = Color(0xFFCFD2D3),
                        fontSize = 11.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = when {
                            !workout.timerStarted -> onStartTimer
                            workout.pausedAt > 0L -> onTogglePause
                            else -> onTogglePause
                        },
                        enabled = !workout.finished && !workout.timerStopped,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6266), contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            when {
                                workout.timerStopped -> "Stopped"
                                !workout.timerStarted -> "Start"
                                workout.pausedAt > 0L -> "Resume"
                                else -> "Pause"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = onStopTimer,
                        enabled = workout.timerStarted && !workout.timerStopped && !workout.finished,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) { Text("Stop", color = Color.White, fontSize = 12.sp) }
                }
            }
            if (isGpsActivity(workout.activityType) && workout.timerStarted && !workout.timerStopped) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp).padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column {
                        Text("DISTANCE", color = Color(0xFFBFC3C5), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("${"%.2f".format(Locale.US, gpsDistanceMeters / 1_000.0)} km", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(if (gpsActivityType == "Cycling") "SPEED" else "PACE", color = Color(0xFFBFC3C5), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(if (gpsPaceSecondsPerKm > 0) formatSpeedOrPace(gpsPaceSecondsPerKm, gpsActivityType) else if (gpsActivityType == "Cycling") "— km/h" else "—:— /km", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("CALORIES", color = Color(0xFFBFC3C5), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(gpsCalories?.let { "~$it kcal" } ?: "— kcal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    when {
                        gpsHasFix && gpsDistanceMeters > 0 -> if (gpsTracking) "Location recording" else "Location paused"
                        gpsStatus == "location_disabled" -> "Turn on device location to track this activity"
                        gpsTracking -> "Waiting for GPS signal…"
                        else -> "GPS unavailable · timer still works"
                    },
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 13.dp),
                    color = Color(0xFFCFD2D3),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun formatPace(secondsPerKm: Double): String {
    if (!secondsPerKm.isFinite() || secondsPerKm <= 0) return "—:— /km"
    val totalSeconds = secondsPerKm.toInt()
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')} /km"
}

private fun formatSpeedOrPace(secondsPerKm: Double, activity: String): String =
    if (activity == "Cycling") String.format(Locale.US, "%.1f km/h", 3_600.0 / secondsPerKm)
    else "${formatPace(secondsPerKm)} /km"

@Composable
private fun CardioTimerSettings(
    cardioMode: String,
    runtimeMinutes: String,
    restMinutes: String,
    prepSeconds: String,
    editable: Boolean,
    onRuntimeChange: (String) -> Unit,
    onRestChange: (String) -> Unit,
    onPrepChange: (String) -> Unit,
    onModeChange: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TIMER MODE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavigationButton("Intervals", cardioMode == "intervals", Modifier.weight(1f), onClick = { if (editable) onModeChange("intervals") })
                NavigationButton("Stopwatch", cardioMode == "stopwatch", Modifier.weight(1f), onClick = { if (editable) onModeChange("stopwatch") })
            }
            if (cardioMode == "intervals") {
                Text("INTERVAL SETTINGS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "WORK TIME",
                    value = runtimeMinutes,
                    editable = editable,
                    onValueChange = onRuntimeChange,
                    suffix = "min",
                    modifier = Modifier.weight(1f),
                    onAdjust = { delta -> onRuntimeChange(((runtimeMinutes.toIntOrNull() ?: 30) + delta).coerceIn(1, 999).toString()) },
                )
                NumberField(
                    label = "REST TIME",
                    value = restMinutes,
                    editable = editable,
                    onValueChange = onRestChange,
                    suffix = "min",
                    modifier = Modifier.weight(1f),
                    onAdjust = { delta -> onRestChange(((restMinutes.toIntOrNull() ?: 5) + delta).coerceIn(1, 999).toString()) },
                )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "GET READY",
                    value = prepSeconds,
                    editable = editable,
                    onValueChange = onPrepChange,
                    suffix = "sec",
                    modifier = Modifier.weight(1f),
                    onAdjust = { delta -> onPrepChange(((prepSeconds.toIntOrNull() ?: 10) + delta).coerceIn(1, 999).toString()) },
                )
                Spacer(Modifier.weight(1f))
                }
                Text("Starts with a setup countdown, then repeats work and rest until you stop.", color = Muted, fontSize = 10.sp)
            } else {
                Text("Counts up continuously. No rest timer or automatic breaks.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(trailing, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ExerciseGroupCard(
    exercise: String,
    sets: List<WorkoutSet>,
    editable: Boolean,
    onRepsChange: (WorkoutSet, String) -> Unit,
    onWeightChange: (WorkoutSet, String) -> Unit,
    onComplete: (WorkoutSet) -> Unit,
    onRemoveSet: (WorkoutSet) -> Unit,
    onAddSet: () -> Unit,
    onRemoveExercise: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(19.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${sets.count { it.completed }} / ${sets.size} sets complete", color = Muted, fontSize = 10.sp)
                }
                TextButton(onClick = onRemoveExercise, enabled = editable, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Remove", color = Muted, fontSize = 11.sp)
                }
            }
            sets.forEachIndexed { index, set ->
                if (index > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier.size(24.dp).background(if (set.completed) SoftGreen else Canvas, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((index + 1).toString(), color = if (set.completed) Forest else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    NumberField(
                        label = "REPS",
                        value = set.reps,
                        editable = editable,
                        onValueChange = { onRepsChange(set, it) },
                        modifier = Modifier.weight(1f),
                        onAdjust = { delta -> onRepsChange(set, ((set.reps.toIntOrNull() ?: 0) + delta).coerceAtLeast(0).toString()) },
                    )
                    NumberField(
                        label = if (set.needsWeight()) "WEIGHT *" else "WEIGHT OPTIONAL",
                        value = set.weight,
                        editable = editable,
                        onValueChange = { onWeightChange(set, it) },
                        suffix = "kg",
                        modifier = Modifier.weight(1f),
                        allowDecimal = true,
                        onAdjust = { delta ->
                            val adjusted = ((set.weight.toDoubleOrNull() ?: 0.0) + delta * 2.5).coerceAtLeast(0.0)
                            onWeightChange(set, if (adjusted % 1.0 == 0.0) adjusted.toInt().toString() else "%.1f".format(Locale.US, adjusted))
                        },
                    )
                    Button(
                        onClick = { onComplete(set) },
                        enabled = editable && (!set.needsWeight() || set.weight.toDoubleOrNull() != null),
                        modifier = Modifier.size(width = 40.dp, height = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (set.completed) SoftGreen else Forest, contentColor = if (set.completed) Forest else Color.White),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("✓", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    TextButton(
                        onClick = { onRemoveSet(set) },
                        enabled = editable,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(width = 24.dp, height = 40.dp),
                    ) { Text("×", color = Muted, fontSize = 17.sp) }
                }
            }
            if (editable) TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                Text("＋ Add set", color = Forest, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    allowDecimal: Boolean = false,
    onAdjust: (Int) -> Unit,
) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { text ->
                    val valid = if (allowDecimal) text.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$")) else text.matches(Regex("^\\d{0,3}$"))
                    if (valid) onValueChange(text)
                },
                modifier = Modifier.weight(1f).height(55.dp),
                enabled = editable,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = Ink),
                keyboardOptions = KeyboardOptions(keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number),
                suffix = suffix?.let { { Text(it, color = Muted, fontSize = 11.sp) } },
                shape = RoundedCornerShape(13.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Leaf,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = Forest,
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                StepButton(label = "+", enabled = editable) { onAdjust(1) }
                StepButton(label = "−", enabled = editable) { onAdjust(-1) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 25.dp)
            .background(if (enabled) Canvas else Line, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Forest else Muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun RestTimerCard(seconds: Int, paused: Boolean, onSkip: () -> Unit, onAddTime: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3DE)),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(if (paused) "REST TIMER PAUSED" else "RESTING", color = Color(0xFF9B6B21), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text(formatClock(seconds * 1_000L), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddTime) { Text("+30s", color = Color(0xFF8B611E), fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onSkip) { Text("Skip", color = Forest, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun RestTimerSettings(seconds: Int, editable: Boolean, onChange: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("REST TIMER", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("After each set", color = Ink, fontSize = 12.sp)
            }
            NumberField(
                label = "SECONDS",
                value = seconds.toString(),
                editable = editable,
                onValueChange = { value -> value.toIntOrNull()?.let { onChange(it.coerceIn(0, 999)) } },
                modifier = Modifier.width(150.dp),
                suffix = "sec",
                onAdjust = { delta -> onChange((seconds + delta * 15).coerceIn(0, 999)) },
            )
        }
    }
}

@Composable
private fun SessionFooter(workout: WorkoutState, onFinish: () -> Unit) {
    val completed = workout.sets.count { it.completed }
    val strength = workout.activityType == "Strength"
    val missingWeights = strength && workout.sets.hasMissingRequiredWeights()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(SoftGreen, RoundedCornerShape(15.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✦", color = Leaf, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    workout.finished -> "Workout saved to your history."
                    strength -> "Steady progress. Every set counts."
                    else -> "Time is tracked as you move. Pause whenever you need."
                },
                color = Forest,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (!workout.finished) {
            Button(
                onClick = onFinish,
                enabled = !strength || (completed > 0 && !missingWeights),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
            ) {
                Text(
                    when {
                        !strength -> "Finish ${workout.activityType.lowercase(Locale.getDefault())}"
                        missingWeights -> "Enter weights for weighted exercises"
                        completed > 0 -> "Finish workout  ·  $completed / ${workout.sets.size} sets done"
                        else -> "Complete a set to finish"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text("Saved on this device", color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun HistoryHeader(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your calendar", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("$count ${if (count == 1) "workout" else "workouts"} saved on this device", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun PersonalRecordsCard(history: List<WorkoutRecord>) {
    val bestByExercise = history.asSequence()
        .filter { it.activityType == "Strength" }
        .flatMap { record -> record.sets.asSequence().filter { it.completed }.map { record to it } }
        .mapNotNull { (record, set) ->
            val weight = set.weight.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
            Triple(set.exercise, weight, set.reps to record.startedAt)
        }
        .groupBy { it.first }
        .mapValues { (_, attempts) -> attempts.maxBy { it.second } }
        .values
        .sortedByDescending { it.second }
        .take(5)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("PERSONAL RECORDS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            if (bestByExercise.isEmpty()) {
                Text("Complete a weighted set to start tracking your lift records.", color = Muted, fontSize = 12.sp)
            } else {
                bestByExercise.forEach { (exercise, weight, detail) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(exercise, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${detail.first} reps · ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(detail.second))}", color = Muted, fontSize = 10.sp)
                        }
                        Text("${"%.1f".format(Locale.US, weight).trimEnd('0').trimEnd('.')} kg", color = Forest, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(now: Long) {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(greeting, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HomeTodayCard(
    history: List<WorkoutRecord>,
    today: Long,
    onStartWorkout: () -> Unit,
    continueWorkout: Boolean,
) {
    val records = history.filter { dayStart(it.startedAt) == today }
    val setCount = records.sumOf { record -> record.sets.count { it.completed } }
    val elapsed = records.sumOf { it.elapsedMillis }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TODAY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text("Your activity", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HomeMetric(records.size.toString(), "workouts")
                HomeMetric(formatClock(elapsed), "active time")
                HomeMetric(setCount.toString(), "sets")
            }
            if (records.isEmpty()) {
                Text("Nothing logged yet today. Start whenever you’re ready.", color = Muted, fontSize = 12.sp)
            } else {
                records.forEach { record ->
                    Text("${record.activityType} · ${record.title}", color = Forest, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
            ) {
                Text(if (continueWorkout) "Continue workout" else "Start a workout", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HomeMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

private fun isPushupExercise(name: String): Boolean =
    name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.contains("pushup")

private fun pushupReps(sets: List<WorkoutSet>): Int = sets
    .filter { it.completed && isPushupExercise(it.exercise) }
    .sumOf { it.reps.toIntOrNull()?.coerceAtLeast(0) ?: 0 }

@Composable
private fun PushupGoalCard(
    completed: Int,
    manuallyLogged: Int,
    goal: Int,
    onLogReps: (Int) -> Unit,
) {
    val progress = (completed.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("PUSH-UPS TODAY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("$completed / $goal", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(Canvas, CircleShape)) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).background(CalendarGreen, CircleShape))
            }
            Text("${(progress * 100).toInt()}% of daily push-up goal", color = Muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onLogReps(5) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                ) { Text("＋ 5 reps") }
                Button(
                    onClick = { onLogReps(10) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                ) { Text("＋ 10 reps") }
                TextButton(onClick = { onLogReps(-5) }, enabled = manuallyLogged >= 5) { Text("Undo 5", color = Muted, fontSize = 10.sp) }
            }
            Text("Completed push-up sets in workouts count automatically. Use quick-add for reps done outside a workout.", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StepCounterCard(
    steps: Long,
    goal: Int,
    sensorAvailable: Boolean,
    estimatedCalories: Int,
    hasBodyWeight: Boolean,
) {
    val progress = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("STEPS TODAY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${steps.toString().reversed().chunked(3).joinToString(",").reversed()}", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(if (hasBodyWeight) "~$estimatedCalories kcal walked today" else "Add body weight in Settings to estimate walking calories", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(8.dp).background(Canvas, CircleShape)) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).background(CalendarGreen, CircleShape))
            }
            Text("${(progress * 100).toInt()}% of ${goal.toString().reversed().chunked(3).joinToString(",").reversed()} step goal", color = Muted, fontSize = 11.sp)
            if (!sensorAvailable) Text("This phone has no step counter sensor.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BodyWeightAndCaloriesCard(
    weightKg: Double?,
    heightCm: Int?,
    ageYears: Int?,
    history: List<WorkoutRecord>,
    today: Long,
    additionalCalories: Int,
) {
    val calories = history.filter { dayStart(it.startedAt) == today }.sumOf { it.estimatedCalories ?: 0 } + additionalCalories
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CALORIES BURNED TODAY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text(if (weightKg == null) "— kcal" else "~$calories kcal", color = Ink, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            val estimateBasis = when {
                weightKg == null -> "Add your weight in Settings to estimate calories."
                heightCm == null || ageYears == null -> "Add height and age in Settings for a personalized estimate."
                else -> "Estimate uses your saved weight, height, and age with activity MET values."
            }
            Text(estimateBasis, color = Muted, fontSize = 12.sp)
            Text("Today's total adds finished workouts, step-based walking, and quick-logged push-ups. Step estimates assume a height-based step length and brisk walking; push-ups assume 30 reps/min. Estimates are approximate and may overlap a separately logged walking workout.", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun activityEmoji(activity: String): String = when (activity) {
    "Strength" -> "🏋"
    "Running", "Jogging" -> "🏃"
    "Cycling" -> "🚴"
    "Swimming" -> "🏊"
    "Walking", "Hiking" -> "🚶"
    "Yoga" -> "🧘"
    "Basketball" -> "🏀"
    "Soccer" -> "⚽"
    "Tennis", "Badminton" -> "🏸"
    "Boxing" -> "🥊"
    "Volleyball" -> "🏐"
    "Table Tennis" -> "🏓"
    "Pilates" -> "🧘"
    "Cricket" -> "🏏"
    "Squash", "Racquetball" -> "🎾"
    "Martial Arts", "Kickboxing" -> "🥋"
    "Tai Chi" -> "🧘"
    "Dance" -> "💃"
    "Rowing" -> "🚣"
    "Jump Rope", "HIIT", "Elliptical", "Stair Climbing" -> "⏱"
    else -> "🏃"
}

private fun dayStart(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun monthStart(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonth(time: Long, amount: Int): Long = Calendar.getInstance().apply {
    timeInMillis = time
    add(Calendar.MONTH, amount)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

@Composable
private fun WorkoutCalendar(
    month: Long,
    selectedDay: Long,
    history: List<WorkoutRecord>,
    today: Long,
    onMonthChange: (Long) -> Unit,
    onDaySelected: (Long) -> Unit,
) {
    val daysWithWorkouts = history.map { dayStart(it.startedAt) }.toSet()
    val calendar = Calendar.getInstance().apply { timeInMillis = month }
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(month))
    val offset = (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onMonthChange(shiftMonth(month, -1)) }) { Text("‹", fontSize = 26.sp, color = Forest) }
                Text(monthLabel, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = { onMonthChange(shiftMonth(month, 1)) }) { Text("›", fontSize = 26.sp, color = Forest) }
            }
            Row(Modifier.fillMaxWidth()) {
                val dayLabels = (0..6).map { index ->
                    val day = (calendar.firstDayOfWeek - 1 + index) % 7
                    listOf("S", "M", "T", "W", "T", "F", "S")[day]
                }
                dayLabels.forEach { label ->
                    Text(label, modifier = Modifier.weight(1f), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            repeat(totalCells / 7) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { weekday ->
                        val dayNumber = week * 7 + weekday - offset + 1
                        if (dayNumber in 1..daysInMonth) {
                            val dayTime = Calendar.getInstance().apply {
                                timeInMillis = month
                                set(Calendar.DAY_OF_MONTH, dayNumber)
                            }.let { dayStart(it.timeInMillis) }
                            val isSelected = dayTime == selectedDay
                            val isToday = dayTime == today
                            val hasWorkout = dayTime in daysWithWorkouts
                            Column(
                                modifier = Modifier.weight(1f).height(43.dp).clickable { onDaySelected(dayTime) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier.size(30.dp)
                                        .background(if (isSelected) SoftGreen else Color.Transparent, CircleShape)
                                        .then(
                                            if (isToday) Modifier.border(1.dp, CalendarGreen, CircleShape) else Modifier,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        color = if (isSelected) Ink else Muted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                                Box(
                                    Modifier.size(5.dp).background(
                                        color = if (hasWorkout) CalendarGreen else Color.Transparent,
                                        shape = CircleShape,
                                    ),
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(43.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).background(CalendarGreen, CircleShape))
                    Text("Completed", color = Muted, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).border(1.dp, CalendarGreen, CircleShape))
                    Text("Today", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun DayHistory(selectedDay: Long, history: List<WorkoutRecord>, today: Long) {
    val records = history.filter { dayStart(it.startedAt) == selectedDay }.sortedBy { it.startedAt }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(selectedDay)), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(if (selectedDay == today) "Today" else "${records.size} ${if (records.size == 1) "workout" else "workouts"}", color = Muted, fontSize = 12.sp)
        }
        if (records.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Nothing logged", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Workouts for this day will appear here.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            records.forEach { record -> HistoryCard(record) }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your workouts will show up here", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Finish a session to save its sets, duration, and date.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HistoryCard(record: WorkoutRecord) {
    val hasSets = record.activityType == "Strength"
    val completedSets = record.sets.filter { it.completed }
    val volume = completedSets.sumOf { set ->
        val reps = set.reps.toDoubleOrNull() ?: 0.0
        val weight = set.weight.toDoubleOrNull() ?: 0.0
        reps * weight
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("${record.activityType} · ${record.title}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                if (hasSets) {
                    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(record.startedAt))
                } else {
                    "${SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(record.startedAt))}  ·  ${formatClock(record.elapsedMillis)}"
                },
                color = Muted,
                fontSize = 12.sp,
            )
            record.estimatedCalories?.let { kcal ->
                Text(
                    if (record.distanceMeters != null) "~$kcal kcal · distance-based estimate" else "~$kcal kcal · time-based estimate",
                    color = Forest,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                record.weightKg?.let { weight -> Text("Estimate used ${"%.1f".format(Locale.US, weight)} kg body weight", color = Muted, fontSize = 10.sp) }
            }
            record.distanceMeters?.takeIf { it > 0 }?.let { distance ->
                val pace = record.averagePaceSecondsPerKm
                Text(
                    "${"%.2f".format(Locale.US, distance / 1_000.0)} km${pace?.let { " · ${formatSpeedOrPace(it, record.activityType)} average" } ?: ""}",
                    color = Forest,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
            if (hasSets) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${completedSets.size} sets", color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    if (volume > 0) Text("${volume.toInt()} kg volume", color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            record.sets.forEach { set ->
                Text(
                    "${if (set.completed) "✓" else "·"}  ${set.exercise}  ·  ${set.reps} reps${set.weight.takeIf { it.isNotBlank() }?.let { " × $it kg" } ?: ""}",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun formatClock(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000).toInt()
    return "${(totalSeconds / 60).toString().padStart(2, '0')}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

private fun playIntervalBeep() {
    val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    val handler = Handler(Looper.getMainLooper())
    repeat(2) { index ->
        handler.postDelayed(
            { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 1_000) },
            index * 1_200L,
        )
    }
    handler.postDelayed({ tone.release() }, 2_400L)
}
