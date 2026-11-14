package com.taeppir.chagokchagok

import android.content.Context

internal data class DriveProfile(val medianKmh: Float, val sampleCount: Int, val dynamic: Boolean = false, val wipers: Int = 0, val windows: Int = 0, val signalStatus: String = "", val doors: Int = 0, val signals: Int = 0) {
    val tempo: Int
        get() = when {
            medianKmh < 30 -> 84
            medianKmh < 60 -> 94
            else -> 104
        }

    companion object {
        const val DEFAULT_TEMPO = 94
        const val MIN_SAMPLES = 3

        fun summarize(samples: List<Float>, strongChanges: Int): DriveProfile? {
            if (samples.size < MIN_SAMPLES) return null
            val sorted = samples.sorted()
            val middle = sorted.size / 2
            val median = (sorted[middle] + sorted[(sorted.size - 1) / 2]) / 2
            return DriveProfile(median, sorted.size, strongChanges >= 2)
        }
    }
}

internal class TripStore(context: Context) {
    private val preferences = context.getSharedPreferences("trip", Context.MODE_PRIVATE)

    var genre: Genre?
        get() = Genre.entries.firstOrNull { it.name == preferences.getString("genre", null) }
        set(value) { preferences.edit().putString("genre", value?.name).apply() }

    var punchy: Boolean
        get() = preferences.getBoolean("punchy", true)
        set(value) { preferences.edit().putBoolean("punchy", value).apply() }

    var rapMode: Boolean
        get() = preferences.getBoolean("rapMode", false)
        set(value) { preferences.edit().putBoolean("rapMode", value).apply() }

    var story: String
        get() = preferences.getString("story", "").orEmpty()
        set(value) {
            preferences.edit().putString("story", value).apply()
        }

    var weather: Weather
        get() = Weather.entries.firstOrNull { it.name == preferences.getString("weather", null) }
            ?: Weather.Unknown
        set(value) {
            preferences.edit().putString("weather", value.name).apply()
        }

    var profile: DriveProfile?
        get() = preferences.getInt("sampleCount", 0).takeIf { it >= DriveProfile.MIN_SAMPLES }
            ?.let {
                DriveProfile(
                    preferences.getFloat("medianKmh", 0f), it, preferences.getBoolean("dynamic", false),
                    preferences.getInt("wipers", 0), preferences.getInt("windows", 0), preferences.getString("signalStatus", "").orEmpty(), preferences.getInt("doors", 0), preferences.getInt("signals", 0),
                )
            }
        set(value) {
            preferences.edit()
                .putInt("sampleCount", value?.sampleCount ?: 0)
                .putFloat("medianKmh", value?.medianKmh ?: 0f)
                .putBoolean("dynamic", value?.dynamic ?: false)
                .putInt("wipers", value?.wipers ?: 0)
                .putInt("windows", value?.windows ?: 0)
                .putString("signalStatus", value?.signalStatus.orEmpty())
                .putInt("doors", value?.doors ?: 0).putInt("signals", value?.signals ?: 0)
                .apply()
        }
}
