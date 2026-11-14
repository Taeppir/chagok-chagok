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
    private var audition=false
    private var generation = 0
    var looping = false
        set(value) {
            field = value
            track?.setLoopPoints(0, frameCount, if (value && !audition) -1 else 0)
        }
    private var envelope = FloatArray(0)
    fun visualLevels(): FloatArray {
        val player = track ?: return FloatArray(48)
        if (!active || envelope.isEmpty()) return FloatArray(48)
        val center = (player.playbackHeadPosition % frameCount) / 480
        return FloatArray(48) { offset ->
            val index = center + offset - 24
            if (looping) envelope[Math.floorMod(index, envelope.size)] else envelope.getOrElse(index) { 0f }
        }
    }
    private var frameCount = 0
    val progress: Float
        get() = track?.let { if (frameCount > 0) (it.playbackHeadPosition % frameCount).toFloat() / frameCount else 0f } ?: 0f
    val durationSeconds: Int
        get() = frameCount / SAMPLE_RATE
    var active = false
        private set

    fun play(arrangement: Arrangement, solo: Int? = null) {
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onStatus(R.string.track_focus_unavailable)
            return
        }
        active = true
        audition = solo != null
        val request = generation
        onStatus(R.string.track_loading)
        Thread({
            val pcm = try { renderBackingTrack(arrangement,solo) } catch (e: RuntimeException) {
                main.post { if (request == generation) { stop(); onStatus(R.string.track_failed) } }
                return@Thread
            }
            val levels = FloatArray((pcm.size + 479) / 480) { block ->
                val start = block * 480
                val end = min(start + 480, pcm.size)
                var sum = 0.0
                for (i in start until end) { val value = pcm[i] / 32768.0; sum += value * value }
                (kotlin.math.sqrt(sum / (end - start)) * 3.2).toFloat().coerceIn(0f, 1f)
            }
            main.post {
                if (request == generation) {
                    envelope = levels
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
                    if (track !== finished || looping) return
                    stop()
                    onStatus(R.string.track_finished)
                }

                override fun onPeriodicNotification(player: AudioTrack) = Unit
            }, main)
            frameCount = pcm.size
            player.setLoopPoints(0, pcm.size, if (looping && !audition) -1 else 0)
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
        envelope = FloatArray(0)
    }

    companion object {
        private const val SAMPLE_RATE = 24_000

        internal fun renderBackingTrack(arrangement: Arrangement, solo: Int? = null): ShortArray {
            val beat = 60.0 / arrangement.bpm
            val bars = if(solo==null) 16 else 1
            val buffer = DoubleArray((bars * 4 * beat * SAMPLE_RATE).toInt())
            val random = Random(84 + arrangement.variation)
            val roots = intArrayOf(48, 45, 41, 43)
            val hiphop = arrangement.genre == Genre.HipHop
            val house = arrangement.genre == Genre.House
            val soft = arrangement.genre == Genre.LoFi || arrangement.genre == Genre.Acoustic

            fun tone(midi: Int, time: Double, duration: Double, gain: Double, voice: Int) {
                val offset = (time * SAMPLE_RATE).toInt()
                val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
                val length = min((duration * SAMPLE_RATE).toInt(), buffer.size - offset)
                for (i in 0 until length) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val phase = 2 * PI * frequency * t
                    val fade = min(1.0, t / 0.008) * min(1.0, (duration - t) / 0.06)
                    val wave = when (voice) {
                        0 -> sin(phase) + 0.22 * sin(phase * 2) + 0.08 * sin(phase * 3)
                        1 -> (sin(phase) + 0.32 * sin(phase * 2.003) + 0.12 * sin(phase * 3)) * exp(-2.8 * t)
                        else -> (sin(phase) + 0.42 * sin(phase * 2.76)) * exp(-7 * t)
                    }
                    val duck = if (voice == 1 && house) 0.4 + 0.6 * min(1.0, t / (beat * 0.4)) else 1.0
                    buffer[offset + i] += wave * fade * gain * duck
                }
            }

            fun drum(time: Double, kind: Int, gain: Double) {
                val duration = when (kind) { 0 -> 0.32; 1 -> 0.19; 2 -> 0.06; else -> 0.24 }
                val offset = (time * SAMPLE_RATE).toInt()
                val length = min((duration * SAMPLE_RATE).toInt(), buffer.size - offset)
                var old = 0.0
                for (i in 0 until length) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val noise = random.nextDouble(-1.0, 1.0)
                    val high = noise - old
                    old = noise
                    val wave = when (kind) {
                        0 -> sin(2 * PI * (46 * t + 2.8 * (1 - exp(-35 * t)))) * exp(-13 * t)
                        1 -> (noise * 0.8 + sin(2 * PI * 185 * t) * 0.2) * exp(-23 * t)
                        2 -> high * exp(-75 * t)
                        else -> high * exp(-15 * t)
                    }
                    buffer[offset + i] += wave * gain * min(1.0, t / 0.002) * min(1.0, (duration - t) / 0.02)
                }
            }

            if(solo!=null) {
                when(solo) {
                    0 -> for(t in listOf(0.0,.5,1.0,1.5)) drum(t,3,.24)
                    1 -> for((i,note) in intArrayOf(72,76,79).withIndex()) tone(note,i*.45,.8,.22,2)
                    2 -> for(t in listOf(.0,1.0)) {drum(t,1,.3);drum(t+.025,1,.2);drum(t+.045,1,.12)}
                    3 -> for(t in listOf(.0,.5,1.0,1.5)) tone(84,t,.055,.4,2)
                    4 -> for(note in intArrayOf(60,64,67)) tone(note,0.0,2.0,.13,1)
                    5 -> for(t in listOf(.0,.25,1.0,1.25)) drum(t,2,.25)
                    6 -> for(step in listOf(0,3,4,6,7)) tone(36+if(step==7) 7 else 0,step*beat/2,beat*.65,if(arrangement.punchy) .42 else .25,0)
                    7 -> {for(step in DrumPattern.kickSteps(arrangement)) drum(step*beat/2,0,.55);for(t in listOf(1.0,3.0)) drum(t*beat,1,.24);for(step in 0..7) drum(step*beat/2,2,.065)}
                }
            }
            if(solo==null) for (bar in 0 until bars) {
                val chordIndex = (bar + arrangement.variation) % roots.size
                val root = roots[chordIndex]
                val offsets = when (chordIndex) {
                    1 -> intArrayOf(0, 3, 7, 10)
                    3 -> intArrayOf(0, 4, 7, 10)
                    else -> intArrayOf(0, 4, 7, 11)
                } + if (arrangement.genre == Genre.CityPop) intArrayOf(14) else intArrayOf()
                val start = bar * 4 * beat
                val chorus = bar in 8..13
                val sparse = bar == 0 || bar == 15
                val volume = if (sparse) 0.7 else 1.0
                for (chordBeat in listOf(0.0, 1.5, 3.0)) {
                    for ((index, note) in offsets.withIndex()) {
                        val strum = if (arrangement.genre == Genre.Acoustic) index * 0.018 else 0.0
                        tone(root + note + 12, start + chordBeat * beat + strum, beat * 1.25, 0.055 * volume,
                            if (arrangement.genre == Genre.Acoustic) 2 else 1)
                    }
                }
                val bassSteps = if (house) listOf(1, 3, 5, 7) else listOf(0, 3, 4, 6, 7)
                for (step in bassSteps) {
                    tone(root - 12 + if (step == 7) 7 else 0, start + step * beat / 2,
                        beat * if (hiphop) 0.9 else 0.43, (if (hiphop) 0.34 else 0.26) * (if (arrangement.punchy) 1.4 else 0.8), 0)
                }
                val kicks = DrumPattern.kickSteps(arrangement)
                for (step in kicks) drum(start + step * beat / 2, 0, (if (arrangement.punchy) 0.64 else 0.40) * volume)
                for (position in if (hiphop) listOf(2.0) else listOf(1.0, 3.0)) {
                    drum(start + position * beat, 1, if (soft) 0.16 else 0.27)
                    if (chorus) drum(start + position * beat + 0.018, 1, 0.13)
                }
                for (step in 0..15) {
                    if (step % 2 == 0 || hiphop || arrangement.wipers > 12) {
                        val swing = if (arrangement.genre == Genre.LoFi && step % 2 == 1) 0.05 else 0.0
                        drum(start + (step / 4.0 + swing) * beat, 2, if (step % 4 == 0) 0.085 else 0.035)
                    }
                }
                if (house || arrangement.wipers > 0) {
                    val positions = when {
                        arrangement.wipers > 12 -> listOf(0.25,0.75,1.25,1.75,2.25,2.75,3.25,3.75)
                        arrangement.wipers > 4 || house -> listOf(0.5,1.5,2.5,3.5)
                        else -> listOf(0.5,2.5)
                    }
                    for (position in positions) drum(start + position * beat, 3, 0.06)
                }
                if (arrangement.doors > 0 && (arrangement.doors > 12 || bar % (if(arrangement.doors > 4) 2 else 4) == 1)) {
                    for(position in if(arrangement.doors > 12) listOf(1.0,3.0) else listOf(3.0)) {
                        drum(start + position * beat, 1, 0.2)
                        drum(start + position * beat + 0.025, 1, 0.12)
                    }
                }
                if (arrangement.signals > 0) {
                    val positions=when {
                        arrangement.signals > 12 -> listOf(0.75,1.75,2.75,3.75)
                        arrangement.signals > 4 -> listOf(0.75,2.75)
                        else -> listOf(2.75)
                    }
                    for (position in positions) tone(84, start + position * beat, 0.045, 0.15, 2)
                }
                if (arrangement.temperatureChanges > 0) {
                    val bright=arrangement.temperatureChanges>4
                    tone(root + if(bright) 24 else 12, start, beat*3.8, 0.045, if(bright) 2 else 1)
                }
                if(arrangement.recirculationChanges>0 && bar % (if(arrangement.recirculationChanges>4) 1 else 2)==0) {
                    drum(start+1.75*beat, 2, 0.09)
                    drum(start+3.75*beat, 2, 0.07)
                }
                if (arrangement.windows > 0) {
                    tone(root + 19, start, beat * 3.8, 0.045, 1)
                    val notes=if(arrangement.windows>12) 4 else if(arrangement.windows>4) 2 else 1
                    for (step in 0 until notes) {
                        tone(root + 24 + offsets[step], start + (step + 0.25) * beat, beat * 1.4, 0.09, 2)
                    }
                }
                if (!arrangement.rapMode && !sparse) {
                    val hook = if (chorus) intArrayOf(7, offsets[3], 12, 7) else intArrayOf(offsets[1], 7, offsets[3], 7)
                    for ((step, pitch) in hook.withIndex()) {
                        tone(root + 24 + pitch, start + (step + 0.5) * beat, beat * 0.7, if (soft) 0.065 else 0.11, 1)
                    }
                }
                if (bar % 4 == 3 && !sparse) {
                    for (step in 0..3) drum(start + (3 + step / 4.0) * beat, 1, 0.07 + step * 0.025)
                }
            }
            // A short fade at both ends avoids clicks, including repeated playback.
            return ShortArray(buffer.size) { i ->
                val fade = min(1.0, min(i, buffer.lastIndex - i) / (SAMPLE_RATE * 0.008))
                (kotlin.math.tanh(buffer[i] * 1.2) * fade * 29_000).toInt().toShort()
            }
        }
    }
}
