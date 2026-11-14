package com.taeppir.chagokchagok

import java.util.Locale

internal enum class Weather(val label: Int) {
    Unknown(R.string.weather_unknown),
    Sunny(R.string.weather_sunny),
    Rain(R.string.weather_rain),
    Cloudy(R.string.weather_cloudy),
    Snow(R.string.weather_snow),
}

internal enum class Genre(val label: Int, val reason: Int) {
    Pop(R.string.genre_pop, R.string.genre_reason_pop),
    HipHop(R.string.genre_hiphop, R.string.genre_reason_hiphop),
    House(R.string.genre_house, R.string.genre_reason_house),
    CityPop(R.string.genre_city_pop, R.string.genre_reason_city_pop),
    LoFi(R.string.genre_lo_fi, R.string.genre_reason_lo_fi),
    Acoustic(R.string.genre_acoustic, R.string.genre_reason_acoustic),
}

internal data class Arrangement(val bpm: Int, val genre: Genre, val syncopated: Boolean, val wipers: Int = 0, val windows: Int = 0, val rapMode: Boolean = false, val variation: Int = 0, val doors: Int = 0, val signals: Int = 0, val punchy: Boolean = true, val temperatureChanges: Int = 0, val recirculationChanges: Int = 0) {
    companion object {
        fun recommend(tempo: Int, weather: Weather, story: String, dynamic: Boolean): Arrangement {
            val text = story.lowercase(Locale.ROOT)
            fun mentions(vararg words: String) = words.any(text::contains)
            val genre = when {
                weather == Weather.Rain || weather == Weather.Cloudy ||
                    mentions("비가", "빗소리", "빗방울", "비 오는", "조용", "차분", "야경", "밤길") -> Genre.LoFi
                weather == Weather.Snow ||
                    mentions("눈이", "눈길", "눈 내", "겨울", "따뜻", "포근", "캠핑") -> Genre.Acoustic
                weather == Weather.Sunny ||
                    mentions("신나", "햇살", "바다", "설레", "드라이브", "웃었", "축제") -> Genre.CityPop
                else -> Genre.Pop
            }
            return Arrangement(tempo, genre, dynamic)
        }
    }
}

internal object DrumPattern {
    const val STEPS_PER_BAR = 8
    private val steadyKicks = listOf(0, 4)
    private val syncopatedKicks = listOf(0, 3, 5, 7)

    fun kickSteps(syncopated: Boolean): List<Int> = if (syncopated) syncopatedKicks else steadyKicks

    fun kickSteps(arrangement: Arrangement): List<Int> = when {
        arrangement.genre == Genre.House -> listOf(0, 2, 4, 6)
        arrangement.genre == Genre.HipHop || arrangement.syncopated -> syncopatedKicks
        else -> listOf(0, 4, 7)
    }

    fun isBackbeat(step: Int): Boolean = step == 2 || step == 6
}
