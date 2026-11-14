package com.taeppir.chagokchagok

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** Private, bounded PCM segments. Recording continues while the driving UI is hidden. */
internal class TripConversation(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val directory = File(context.filesDir, "conversations/${System.currentTimeMillis()}").apply { mkdirs() }
    private val queue = ArrayDeque<File>()
    private val segments = JSONArray()
    private var sdk: SpeechToText? = null
    private var listener: ResultListener? = null
    private var busy = false
    private var drained: (() -> Unit)? = null
    private var closed = false
    @Volatile private var capturing = false
    @Volatile private var recorder: AudioRecord? = null
    private var sequence = 0
    private var latestText = ""
    private var errorCount = 0
    private val drainTimeout = Runnable {
        errorCount += queue.size
        queue.clear()
        if(busy) finishSegment(false) else next()
    }
    private val timeout = Runnable { finishSegment(false) }
    private val settle = Runnable { finishSegment(true) }
    private var current: File? = null
    private val initialStory = TripStore(context).story

    fun start() {
        capturing = true
        File(directory,"session.json").writeText(JSONObject().put("previousStory",initialStory).put("startedAt",System.currentTimeMillis()).toString())
        TripStore(context).conversationStatus = "대화 기록 중 · 원음은 기기 안에 저장"
        worker.execute {
            try {
                val size = maxOf(6400, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
                check(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                recorder = audio
                check(audio.state == AudioRecord.STATE_INITIALIZED)
                audio.startRecording()
                val buffer = ByteArray(3200)
                var file: File? = null
                var output: java.io.OutputStream? = null
                var bytes = 0
                // 12 seconds bounds Binder payload, heap use and recovery loss.
                while (capturing) {
                    val n = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    check(n >= 0)
                    if (n == 0) continue
                    if (output == null) {
                        file = File(directory, "%05d.pcm".format(sequence++))
                        output = file.outputStream(); bytes = 0
                    }
                    output.write(buffer, 0, n); bytes += n
                    if (bytes >= 384000) {
                        output.close(); output = null
                        val ready = file!!; main.post { enqueue(ready) }
                    }
                }
                output?.close()
                if (output != null && bytes > 3200) { val ready = file!!; main.post { enqueue(ready) } }
            } catch (e: Exception) {
                android.util.Log.w("ChagokConversation","Capture failed: ${e.javaClass.simpleName}")
                main.post { errorCount++; TripStore(context).conversationStatus = "마이크 입력을 확인해 주세요 · 차량 기록은 계속돼요" }
            } finally {
                runCatching { recorder?.stop() }; recorder?.release(); recorder = null
                main.post { capturing = false; next() }
            }
        }
    }

    private fun enqueue(file: File) {
        if(closed) return
        val pcm=file.readBytes()
        var peak=0
        for(i in 0 until pcm.size-1 step 2) {
            val sample=((pcm[i].toInt() and 255) or (pcm[i+1].toInt() shl 8)).toShort().toInt()
            peak=maxOf(peak,kotlin.math.abs(sample))
        }
        // Skip digital silence only; this is not a laughter/speaker detector.
        if(peak<32) {
            segments.put(JSONObject().put("file",file.name).put("text","").put("complete",true).put("silence",true))
            File(directory,"transcript.json").writeText(segments.toString())
            next()
        } else {queue.add(file);next()}
    }

    private fun next() {
        if (closed || busy) return
        val file = queue.removeFirstOrNull()
        if (file == null) { if (!capturing) drained?.also { drained = null; summarize(it) }; return }
        current = file; busy = true; latestText = ""
        main.postDelayed(timeout, 20000)
        try {
            val speech = SpeechToText(context, Mode.ON_DEVICE); sdk = speech
            val result = object : ResultListener {
                override fun onReady() = Unit
                override fun onStartedRecognition() = Unit
                override fun onUpdated(text: String, completed: Boolean) { main.post {
                    if (sdk !== speech || !busy) return@post
                    if (text.isNotBlank()) latestText = text
                    if (completed) { main.removeCallbacks(settle); main.postDelayed(settle, 500) }
                } }
                override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onEndedRecognition() { main.post { if (sdk === speech) main.postDelayed(settle, 1500) } }
                override fun onError(errorType: ErrorType) { main.post { if (sdk === speech) finishSegment(false) } }
            }
            listener = result
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() { main.post {
                    if (sdk !== speech || closed) return@post
                    try {
                        speech.addListener(result); speech.setLocale(Locale.KOREA)
                        speech.sendAudio(file.readBytes()); speech.completeAudioSend()
                    } catch (_: Exception) { finishSegment(false) }
                } }
                override fun onFailure(errorType: ErrorType) { main.post { if (sdk === speech) finishSegment(false) } }
            })
        } catch (_: Exception) { finishSegment(false) }
    }

    private fun finishSegment(success: Boolean) {
        if (!busy) return
        main.removeCallbacks(timeout); main.removeCallbacks(settle)
        if (!success) errorCount++
        android.util.Log.i("ChagokConversation","Segment done: complete=$success, characters=${latestText.length}, bytes=${current?.length()}")
        val file = current!!
        segments.put(JSONObject().put("file", file.name).put("startMs", file.nameWithoutExtension.toInt() * 12000L)
            .put("text", latestText).put("complete", success))
        File(directory, "transcript.json").writeText(segments.toString())
        TripStore(context).transcript = (0 until segments.length()).map { segments.getJSONObject(it).optString("text") }.filter { it.isNotBlank() }.joinToString("\n")
        releaseSdk(); busy = false; current = null
        main.postDelayed({ next() }, 100)
    }

    fun finish(done: () -> Unit) {
        drained = done; capturing = false
        main.postDelayed(drainTimeout,120000)
        // AudioRecord.read returns every 100 ms; do not block the main thread.
        if (recorder == null) next()
    }

    private fun summarize(done: () -> Unit) {
        main.removeCallbacks(drainTimeout)
        val trip = TripStore(context)
        val transcript = trip.transcript.take(60000)
        if (transcript.isBlank()) {
            trip.conversationStatus = "인식된 대화가 없어요 · 이야기 추가 또는 비트만 듣기"
            done(); return
        }
        trip.conversationStatus = "대화에서 이야기를 정리하고 있어요"
        worker.execute {
            var summary: String? = null
            try {
                val request = URL("http://127.0.0.1:8787/summaries").openConnection() as HttpURLConnection
                try {
                    request.connectTimeout = 5000; request.readTimeout = 45000
                    request.requestMethod = "POST"; request.doOutput = true
                    request.setRequestProperty("Content-Type", "application/json")
                    request.outputStream.use { it.write(JSONObject().put("transcript", transcript).toString().toByteArray()) }
                    check(request.responseCode == 200)
                    summary = JSONObject(request.inputStream.bufferedReader().readText()).getString("story").take(2000)
                } finally { request.disconnect() }
            } catch (_: Exception) { /* Keep the original transcript and offer editing. No paid retries. */ }
            main.post {
                if (closed) return@post
                val result = summary
                trip.suggestedStory = result?.takeIf {it.isNotBlank()} ?: transcript.take(2000)
                if (trip.story == initialStory) trip.story = trip.suggestedStory
                trip.conversationStatus = if (result != null) "대화 요약 완료" else "자동 요약 연결 실패 · 인식된 대화를 담았어요"
                if (errorCount > 0) trip.conversationStatus += " · 일부 구간 인식 누락"
                done()
            }
        }
    }

    private fun releaseSdk() {
        val speech = sdk; sdk = null
        runCatching { speech?.stop() }; runCatching { listener?.let { speech?.removeListener(it) } }
        runCatching { speech?.release() }; listener = null
    }
    fun close() {
        closed = true; capturing = false; main.removeCallbacks(drainTimeout); main.removeCallbacks(timeout); main.removeCallbacks(settle)
        releaseSdk(); worker.shutdown()
    }
}
