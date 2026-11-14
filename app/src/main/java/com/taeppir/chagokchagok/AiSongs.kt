package com.taeppir.chagokchagok

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Connects only to the loopback bridge. Google credentials never enter the APK. */
internal class AiSongs(private val context: Context, private val changed: () -> Unit) {
    private val directory = File(context.filesDir, "songs").apply { mkdirs() }
    private val metadata = File(directory, "current.json")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var working = false
    private val poll = Runnable { refresh() }
    var song: JSONObject? = runCatching { JSONObject(metadata.readText()) }.getOrNull()
        private set
    var pending = false
        private set
    var message = "이야기를 가사와 보컬이 있는 노래로 만들어요."
        private set
    val file: File? get() = song?.optString("id")?.takeIf { it.matches(Regex("[a-f0-9]{32}")) }
        ?.let { File(directory, "$it.mp3") }?.takeIf { it.exists() }

    private fun connection(path: String) = (URL("http://127.0.0.1:8787$path").openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 20000
        instanceFollowRedirects = false
    }

    private fun json(path: String, body: JSONObject? = null): JSONObject {
        val request = connection(path)
        try {
            if (body != null) {
                request.requestMethod = "POST"
                request.doOutput = true
                request.setRequestProperty("Content-Type", "application/json")
                request.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = request.responseCode
            val stream = if (code in 200..299) request.inputStream else request.errorStream
            val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (code !in 200..299) throw IllegalArgumentException(result.optString("error", "서버 응답을 확인해 주세요."))
            return result
        } finally { request.disconnect() }
    }

    fun library(): List<JSONObject> {
        val demos=context.assets.list("demo-songs").orEmpty().filter {it.endsWith(".json")}.mapNotNull {name ->
            runCatching {JSONObject(context.assets.open("demo-songs/$name").bufferedReader().use {it.readText()})}.getOrNull()
        }
        val saved=directory.listFiles().orEmpty().filter {it.extension=="json"}.mapNotNull {runCatching {JSONObject(it.readText())}.getOrNull()}
        val currentDemoIds=demos.map {it.optString("id")}.toSet()
        // Bundled metadata may gain corrected lyric timing in an app update.
        // User titles live separately; never let an older demo cache hide the update.
        return (demos+saved.filter { !it.optBoolean("demo") && it.optString("id") !in currentDemoIds }).distinctBy {it.optString("id")}.filter {it.optString("status")=="ready"}
    }

    fun select(item: JSONObject) {
        if(working || pending) return
        working=true
        worker.execute {
            try {
                val id=item.getString("id");require(id.matches(Regex("[a-f0-9]{32}")))
                val target=File(directory,"$id.mp3")
                if(!target.exists() && item.optBoolean("demo")) context.assets.open("demo-songs/$id.mp3").use {input ->target.outputStream().use {input.copyTo(it)}}
                check(target.isFile)
                song?.let {File(directory,it.getString("id")+".json").writeText(it.toString())}
                metadata.writeText(item.toString())
                main.post {if(!closed) {working=false;song=item;message="저장된 음악을 불러왔어요";changed()}}
            } catch(_:Exception) {main.post {working=false;message="음악을 불러오지 못했어요";changed()}}
        }
    }

    fun refresh() = execute(null)
    fun create(body: JSONObject) = execute(body)

    private fun execute(body: JSONObject?) {
        if (closed || working) return
        working = true
        if (body != null) { pending = true; message = "노래 만들기를 요청하고 있어요."; changed() }
        worker.execute {
            try {
                val job = json(if (body == null) "/songs/latest" else "/songs", body)
                if (job.optString("status") == "ready") {
                    val id = job.getString("id")
                    require(id.matches(Regex("[a-f0-9]{32}")))
                    val audio = File(directory, "$id.mp3")
                    if (!audio.exists()) {
                        val request = connection("/songs/$id/audio")
                        val temp = File(directory, "$id.part")
                        try {
                            check(request.responseCode == 200)
                            request.inputStream.use { input -> temp.outputStream().use { output ->
                                val buffer = ByteArray(16384)
                                var total = 0
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n == -1) break
                                    total += n
                                    check(total <= 40 * 1024 * 1024)
                                    output.write(buffer, 0, n)
                                }
                                check(total >= 1024)
                            } }
                            check(temp.renameTo(audio))
                        } finally { request.disconnect(); temp.delete() }
                    }
                    File(directory,job.getString("id")+".json").writeText(job.toString())
                    val temp = File(directory, "current.tmp")
                    temp.writeText(job.toString())
                    check(temp.renameTo(metadata))
                }
                main.post {
                    if (closed) return@post
                    working = false
                    pending = job.optString("status") == "generating"
                    message = job.optString("message", "이야기를 가사와 보컬이 있는 노래로 만들어요.")
                    if (job.optString("status") == "ready") song = job
                    changed()
                    main.removeCallbacks(poll)
                    if (pending) main.postDelayed(poll, 4000)
                }
            } catch (e: Exception) {
                main.post {
                    if (closed) return@post
                    working = false
                    pending = false
                    message = if (e is IllegalArgumentException) e.message.orEmpty()
                        else "음악 서버에 연결하지 못했어요. Mac에서 서버를 켠 뒤 상태를 확인해 주세요."
                    changed()
                }
            }
        }
    }

    fun close() {
        closed = true
        main.removeCallbacks(poll)
        worker.shutdownNow()
    }
}
