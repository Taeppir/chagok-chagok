package com.taeppir.chagokchagok

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Plays the saved song and measures its decoded PCM for the same truthful visualizer as local beats. */
internal class SongPlayer(context: Context, private val changed: (String) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) { stop(); changed("다른 오디오가 시작되어 노래를 멈췄어요.") } }, main).build()
    private var player: MediaPlayer? = null
    private var generation = 0
    private var envelope = FloatArray(0)
    private var cachedPath = ""
    var active = false
        private set
    var looping = false
        set(value) { field = value; player?.isLooping = value }
    val durationSeconds get() = runCatching { (player?.duration ?: 0) / 1000 }.getOrDefault(0)
    val progress get() = runCatching { player?.let { it.currentPosition.toFloat() / it.duration.coerceAtLeast(1) } ?: 0f }.getOrDefault(0f)

    fun play(file: File) {
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            changed("다른 오디오가 재생 중이에요."); return
        }
        active = true
        val request = generation
        changed("우리 노래를 준비하고 있어요.")
        Thread({
            val levels = if (cachedPath == file.path) envelope else runCatching { decodeEnvelope(file) }.getOrDefault(FloatArray(0))
            android.util.Log.d("ChagokSong", "Waveform frames=${levels.size}, peak=${levels.maxOrNull() ?: 0f}")
            main.post {
                if (generation != request) return@post
                envelope = levels
                cachedPath = file.path
                try {
                    val media = MediaPlayer()
                    player = media
                    media.setAudioAttributes(attributes)
                    media.setDataSource(file.path)
                    media.isLooping = looping
                    media.setOnPreparedListener {
                        if (generation == request) { it.start(); changed("AI 보컬곡 재생 중") }
                    }
                    media.setOnCompletionListener { stop(); changed("우리 노래 재생 완료") }
                    media.setOnErrorListener { _, _, _ -> stop(); changed("노래를 재생하지 못했어요."); true }
                    media.prepareAsync()
                } catch (_: Exception) { stop(); changed("노래를 재생하지 못했어요.") }
            }
        }, "Song waveform decoder").start()
    }

    fun visualLevels(): FloatArray {
        val center = runCatching { (player?.currentPosition ?: 0) / 20 }.getOrDefault(0)
        return FloatArray(48) { if (active) envelope.getOrElse(center + it - 24) { 0f } else 0f }
    }

    fun stop() {
        generation++
        player?.release()
        player = null
        if (active) audio.abandonAudioFocusRequest(focus)
        active = false
    }

    private fun decodeEnvelope(file: File): FloatArray {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val index = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val values = ArrayList<Float>()
            var block = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) * format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) / 50
            var sum = 0.0
            var count = 0
            var inputDone = false
            var floatPcm = false
            val deadline = System.nanoTime() + 30_000_000_000L
            while (System.nanoTime() < deadline) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(1000)
                    if (i >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val i = codec.dequeueOutputBuffer(info, 1000)
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    block = out.getInteger(MediaFormat.KEY_SAMPLE_RATE) * out.getInteger(MediaFormat.KEY_CHANNEL_COUNT) / 50
                    floatPcm = out.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (i >= 0) {
                    val buffer = codec.getOutputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    while (buffer.remaining() >= if (floatPcm) 4 else 2) {
                        val value = if (floatPcm) buffer.float.toDouble() else buffer.short / 32768.0
                        sum += value * value
                        count++
                        if (count >= block) {
                            values.add((sqrt(sum / count) * 3.2).toFloat().coerceIn(0f, 1f))
                            sum = 0.0; count = 0
                        }
                    }
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return values.toFloatArray()
        } finally { decoder?.release(); extractor.release() }
    }
}
