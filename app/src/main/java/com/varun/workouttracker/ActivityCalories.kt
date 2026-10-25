package com.varun.workouttracker

/** Typical MET values from the 2024 Adult Compendium of Physical Activities. */
data class ActivityDefinition(
    val name: String,
    val lightMet: Double,
    val moderateMet: Double,
    val vigorousMet: Double,
) {
    fun metFor(intensity: String): Double = when (intensity.lowercase()) {
        "light" -> lightMet
        "vigorous" -> vigorousMet
        else -> moderateMet
    }
}

object ActivityCalories {
    private const val PUSHUP_VIGOROUS_MET = 8.0
    private const val WALKING_SPEED_KMH = 4.8
    private const val DEFAULT_STEP_LENGTH_METERS = 0.72
    val activities = listOf(
        ActivityDefinition("Strength", 3.0, 3.5, 6.0),
        ActivityDefinition("Running", 6.5, 9.0, 11.8),
        ActivityDefinition("Cycling", 3.5, 7.0, 10.0),
        ActivityDefinition("Jogging", 3.3, 7.5, 9.0),
        ActivityDefinition("Jump Rope", 8.3, 11.8, 12.3),
        ActivityDefinition("Walking", 2.8, 3.8, 4.8),
        ActivityDefinition("Hiking", 3.8, 5.3, 6.0),
        ActivityDefinition("Swimming", 5.8, 5.8, 9.8),
        ActivityDefinition("Rowing", 5.0, 7.5, 11.0),
        ActivityDefinition("Elliptical", 5.0, 5.0, 9.0),
        ActivityDefinition("Yoga", 2.0, 2.3, 4.0),
        ActivityDefinition("HIIT", 5.0, 7.0, 11.0),
        ActivityDefinition("Dance", 4.8, 5.5, 9.8),
        ActivityDefinition("Basketball", 5.0, 7.5, 8.0),
        ActivityDefinition("Soccer", 7.0, 7.0, 9.5),
        ActivityDefinition("Tennis", 5.0, 6.8, 8.0),
        ActivityDefinition("Badminton", 5.5, 7.0, 9.0),
        ActivityDefinition("Stair Climbing", 4.5, 6.8, 9.3),
        ActivityDefinition("Boxing", 5.8, 7.8, 12.3),
        ActivityDefinition("Volleyball", 3.0, 4.0, 6.0),
        ActivityDefinition("Pilates", 1.8, 2.8, 2.8),
        ActivityDefinition("Cricket", 4.8, 4.8, 4.8),
        ActivityDefinition("Squash", 7.3, 7.3, 12.0),
        ActivityDefinition("Racquetball", 7.0, 7.0, 10.0),
        ActivityDefinition("Table Tennis", 4.0, 4.0, 4.0),
        ActivityDefinition("Martial Arts", 5.3, 10.3, 14.3),
        ActivityDefinition("Tai Chi", 1.5, 3.3, 6.0),
        ActivityDefinition("Kickboxing", 5.3, 7.3, 10.3),
    )

    fun estimateCalories(
        activity: String,
        intensity: String,
        weightKg: Double,
        durationMillis: Long,
        heightCm: Int? = null,
        ageYears: Int? = null,
    ): Int? {
        if (weightKg <= 0.0 || durationMillis <= 0L) return null
        val met = defaultMet(activity) ?: activities.firstOrNull { it.name == activity }?.metFor(intensity) ?: return null
        return estimateMetCalories(met, weightKg, durationMillis, heightCm, ageYears)
    }

    /** Uses total jumps / active time to select the Compendium rope-jumping intensity band. */
    fun estimateJumpRopeCalories(jumps: Int, durationMillis: Long, weightKg: Double, heightCm: Int?, ageYears: Int?): Int? {
        if (jumps <= 0 || durationMillis <= 0L || weightKg <= 0.0) return null
        val skipsPerMinute = jumps / (durationMillis / 60_000.0)
        val met = when {
            skipsPerMinute < 100.0 -> 8.3
            skipsPerMinute < 120.0 -> 11.8
            else -> 12.3
        }
        return estimateMetCalories(met, weightKg, durationMillis, heightCm, ageYears)
    }

    /** Estimates outdoor energy from observed speed and time; pace is seconds per km. */
    fun estimateOutdoorCalories(activity: String, speedOrPace: Double, weightKg: Double, durationMillis: Long, heightCm: Int?, ageYears: Int?): Int? {
        if (speedOrPace <= 0.0 || weightKg <= 0.0 || durationMillis <= 0L) return null
        val kmh = if (activity == "Cycling") 3_600.0 / speedOrPace else 3_600.0 / speedOrPace
        val met = when (activity) {
            "Cycling" -> when {
                kmh < 16.0 -> 4.0
                kmh < 19.0 -> 6.8
                kmh < 22.5 -> 8.0
                else -> 10.0
            }
            "Walking" -> when {
                kmh < 3.2 -> 2.8
                kmh < 4.8 -> 3.5
                kmh < 5.6 -> 4.3
                else -> 5.0
            }
            "Hiking" -> when {
                kmh < 3.2 -> 3.8
                kmh < 4.8 -> 5.3
                else -> 6.0
            }
            "Jogging", "Running" -> when {
                kmh < 6.4 -> 6.5
                kmh < 8.0 -> 8.3
                kmh < 9.7 -> 9.8
                kmh < 11.3 -> 11.0
                else -> 11.8
            }
            else -> return null
        }
        return estimateMetCalories(met, weightKg, durationMillis, heightCm, ageYears)
    }

    private fun defaultMet(activity: String): Double? = when (activity) {
        "Strength" -> 3.5
        "Running" -> 8.3
        "Jogging" -> 7.5
        "Cycling" -> 6.8
        "Walking" -> 3.5
        "Hiking" -> 5.3
        "Jump Rope" -> 11.8
        "Swimming" -> 5.8
        "Rowing" -> 7.5
        "Elliptical" -> 5.0
        "Yoga" -> 2.3
        "HIIT" -> 7.0
        "Dance" -> 5.5
        else -> activities.firstOrNull { it.name == activity }?.moderateMet
    }

    /** Estimates brisk walking energy from steps, using height for approximate step length. */
    fun estimateStepCalories(steps: Long, weightKg: Double, heightCm: Int?, ageYears: Int?): Int? {
        if (steps <= 0L || weightKg <= 0.0) return null
        val height = heightCm?.takeIf { it in 80..250 }
        val stepLengthMeters = height?.let { it / 100.0 * 0.414 } ?: DEFAULT_STEP_LENGTH_METERS
        val distanceKm = steps * stepLengthMeters / 1_000.0
        val minutes = distanceKm / WALKING_SPEED_KMH * 60.0
        return estimateMetCalories(activities.first { it.name == "Walking" }.moderateMet, weightKg, (minutes * 60_000).toLong(), heightCm, ageYears)
    }

    /** Estimates quick-logged push-ups at an assumed 30 reps per minute. */
    fun estimatePushupCalories(reps: Int, weightKg: Double, heightCm: Int?, ageYears: Int?): Int? {
        if (reps <= 0 || weightKg <= 0.0) return null
        val durationMillis = reps * 2_000L
        return estimateMetCalories(PUSHUP_VIGOROUS_MET, weightKg, durationMillis, heightCm, ageYears)
    }

    private fun estimateMetCalories(met: Double, weightKg: Double, durationMillis: Long, heightCm: Int?, ageYears: Int?): Int {
        val minutes = durationMillis / 60_000.0
        val height = heightCm?.takeIf { it in 80..250 }
        val age = ageYears?.takeIf { it in 13..120 }
        if (height != null && age != null) {
            // Mifflin-St Jeor's male/female constants are averaged here because
            // the app does not collect sex. Use that resting-energy estimate to
            // scale the activity MET value by this person's height, age, and weight.
            val restingKcalPerDay = 10.0 * weightKg + 6.25 * height - 5.0 * age - 78.0
            // MET is defined relative to body mass; use the standard MET equation and
            // apply a modest resting-rate adjustment from height/age when available.
            val base = met * 3.5 * weightKg / 200.0 * minutes
            val referenceRmr = 10.0 * weightKg + 6.25 * 170.0 - 5.0 * 35.0 - 78.0
            val adjustment = (restingKcalPerDay / referenceRmr).coerceIn(0.8, 1.2)
            return (base * adjustment).toInt().coerceAtLeast(0)
        }
        // Without height and age, fall back to the standard MET × 3.5 ml/kg/min conversion.
        return ((met * 3.5 * weightKg / 200.0) * minutes).toInt()
    }
}
