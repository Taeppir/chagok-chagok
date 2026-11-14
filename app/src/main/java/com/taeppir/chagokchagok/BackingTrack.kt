package com.taeppir.chagokchagok

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

internal class BackingTrack(context: Context, private val onStatus: (Int) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0 && active) {
                stop()
                onStatus(R.string.track_interrupted)
            }
        }, main)
        .build()

    private var track: AudioTrack? = null
    private var generation = 0
    var active = false
        private set

    fun play(arrangement: Arrangement) {
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onStatus(R.string.track_focus_unavailable)
            return
        }
        active = true
        val request = generation
        onStatus(R.string.track_loading)
        Thread({
            val pcm = renderBackingTrack(arrangement)
            main.post {
                if (request == generation) {
                    startPlayback(pcm)
                    if (track != null) {
                        Log.i("ChagokTrack", "Playback started: bpm=${arrangement.bpm}, genre=${arrangement.genre}, syncopated=${arrangement.syncopated}, frames=${pcm.size}")
                    }
                }
            }
        }, "Backing track renderer").start()
    }

    private fun startPlayback(pcm: ShortArray) {
        try {
            val player = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
                .build()
            track = player
            check(player.write(pcm, 0, pcm.size) == pcm.size)
            player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(finished: AudioTrack) {
                    if (track !== finished) return
                    stop()
                    onStatus(R.string.track_finished)
                }

                override fun onPeriodicNotification(player: AudioTrack) = Unit
            }, main)
            player.notificationMarkerPosition = pcm.size - 1
            player.play()
            onStatus(R.string.track_playing)
        } catch (e: RuntimeException) {
            stop()
            onStatus(R.string.track_failed)
        }
    }

    fun stop() {
        generation++
        val player = track
        track = null
        player?.release()
        if (active) audio.abandonAudioFocusRequest(focus)
        active = false
    }

    companion object {
        private const val SAMPLE_RATE = 24_000

        private fun renderBackingTrack(arrangement: Arrangement): ShortArray {
            val beat = 60.0 / arrangement.bpm
            val chords = arrayOf(
                intArrayOf(48, 55, 59, 64), // Cmaj7
                intArrayOf(45, 52, 55, 60), // Am7
                intArrayOf(41, 48, 52, 57), // Fmaj7
                intArrayOf(43, 50, 53, 59), // G7
                intArrayOf(41, 48, 52, 57), // Fmaj7
                intArrayOf(43, 50, 53, 59), // G7
                intArrayOf(45, 52, 55, 60), // Am7
                intArrayOf(48, 55, 59, 64), // Cmaj7
            )
            val melody = arrayOf(
                intArrayOf(72, 76, 79, 74),
                intArrayOf(72, 69, 76, 72),
                intArrayOf(69, 72, 76, 77),
                intArrayOf(74, 71, 67, 72),
                intArrayOf(77, 81, 84, 81),
                intArrayOf(79, 77, 74, 71),
                intArrayOf(76, 79, 76, 69),
                intArrayOf(72, 76, 74, 72),
            )
            val buffer = DoubleArray(((chords.size * 4 * beat + 0.65) * SAMPLE_RATE).toInt())

            fun note(midi: Int, start: Double, duration: Double, gain: Double, decay: Double) {
                val offset = (start * SAMPLE_RATE).toInt()
                val length = min((duration * SAMPLE_RATE).toInt(), buffer.size - offset)
                val angular = 2 * PI * 440.0 * 2.0.pow((midi - 69) / 12.0)
                for (i in 0 until length) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = min(1.0, t / 0.015) * min(1.0, (duration - t) / 0.09) * exp(-t * decay)
                    val tone = when (arrangement.genre) {
                        Genre.CityPop -> sin(angular * t) + 0.27 * sin(angular * 2 * t) + 0.1 * sin(angular * 4 * t)
                        Genre.LoFi -> sin(angular * t) + 0.12 * sin(angular * 2 * t)
                        Genre.Acoustic -> sin(angular * t) + 0.31 * sin(angular * 2 * t) + 0.15 * sin(angular * 3 * t)
                    }
                    buffer[offset + i] += tone * gain * envelope
                }
            }

            val random = Random(84)
            fun percussion(start: Double, duration: Double, gain: Double, kick: Boolean) {
                val offset = (start * SAMPLE_RATE).toInt()
                val length = min((duration * SAMPLE_RATE).toInt(), buffer.size - offset)
                var previousNoise = 0.0
                for (i in 0 until length) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = min(1.0, t / 0.003) * min(1.0, (duration - t) / 0.025)
                    val sample = if (kick) {
                        sin(2 * PI * (47 * t + 2.4 * (1 - exp(-27 * t)))) * exp(-16 * t)
                    } else {
                        val noise = random.nextDouble(-1.0, 1.0)
                        val transient = noise - previousNoise
                        previousNoise = noise
                        transient * exp(-33 * t)
                    }
                    buffer[offset + i] += sample * envelope * gain
                }
            }

            for ((bar, chord) in chords.withIndex()) {
                val start = bar * 4 * beat
                for (step in 0..7) {
                    note(chord[step % 4] + 12, start + step * beat / 2, beat * 0.72, 0.12, 5.0)
                }
                note(chord[0] - 12, start, beat * 1.6, 0.22, 2.8)
                note(chord[0] - 12, start + 2 * beat, beat * 1.6, 0.19, 2.8)
                val kickBeats = if (arrangement.syncopated) {
                    doubleArrayOf(0.0, 1.5, 2.5, 3.5)
                } else {
                    doubleArrayOf(0.0, 2.0)
                }
                val drumGain = when (arrangement.genre) {
                    Genre.CityPop -> 0.31
                    Genre.LoFi -> 0.22
                    Genre.Acoustic -> 0.12
                }
                for (position in kickBeats) percussion(start + position * beat, 0.24, drumGain, kick = true)
                for (step in 0..7) {
                    val position = step / 2.0 + if (arrangement.genre == Genre.LoFi && step % 2 == 1) 0.08 else 0.0
                    val accent = if (step == 2 || step == 6) 0.15 else 0.045
                    percussion(start + position * beat, 0.12, accent, kick = false)
                }
                for ((step, pitch) in melody[bar].withIndex()) {
                    val duration = if (bar == melody.lastIndex && step == 3) beat + 0.45 else beat * 1.05
                    note(pitch, start + step * beat, duration, 0.22, 4.1)
                    if (bar >= 4 && step % 2 == 0) {
                        val harmony = chord[if (step == 0) 2 else 1] + 12
                        note(harmony, start + step * beat + 0.12, beat * 1.4, 0.07, 2.3)
                    }
                }
            }
            return ShortArray(buffer.size) { (buffer[it].coerceIn(-1.0, 1.0) * 32_767).toInt().toShort() }
        }
    }
}
