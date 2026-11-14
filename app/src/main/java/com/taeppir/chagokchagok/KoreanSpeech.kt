package com.taeppir.chagokchagok

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

internal class KoreanSpeech(
    private val context: Context,
    private val onListening: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinished: (Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { onFinished(R.string.speech_timeout) }
    private val noResult = Runnable { onFinished(R.string.speech_no_result) }
    private var sdk: SpeechToText? = null
    private var listenerAdded = false
    private var requested = false
    private var closed = false

    private val listener = object : ResultListener {
        override fun onReady() = dispatch {
            Log.i(TAG, "On-device recognition ready (ko-KR)")
            onListening()
        }
        override fun onStartedRecognition() = dispatch { onListening() }
        override fun onUpdated(stt: String, completed: Boolean) {
            if (completed && stt.isNotBlank()) dispatch {
                Log.i(TAG, "Final Korean transcript received")
                onResult(stt)
            } else if (stt.isNotBlank()) dispatch { onPartial(stt) }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = dispatch {
            // Speech Service may report the end of audio before sending the final transcript.
            main.removeCallbacks(noResult)
            main.postDelayed(noResult, FINAL_RESULT_WAIT_MS)
        }
        override fun onError(errorType: ErrorType) = dispatch {
            reportError(errorType.toString())
        }
    }

    fun start() {
        main.postDelayed(timeout, SESSION_TIMEOUT_MS)
        try {
            val speech = SpeechToText(context, Mode.ON_DEVICE)
            sdk = speech
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() = dispatch {
                    try {
                        speech.addListener(listener)
                        listenerAdded = true
                        speech.setLocale(Locale.KOREA)
                        requested = true
                        speech.request()
                    } catch (e: RuntimeException) {
                        reportError(e.javaClass.simpleName)
                    }
                }

                override fun onFailure(errorType: ErrorType) = dispatch {
                    reportError(errorType.toString())
                }
            })
        } catch (e: RuntimeException) {
            reportError(e.javaClass.simpleName)
        }
    }

    fun close() {
        closed = true
        main.removeCallbacks(timeout)
        main.removeCallbacks(noResult)
        val speech = sdk ?: return
        sdk = null
        if (requested) cleanup { speech.stop() }
        if (listenerAdded) cleanup { speech.removeListener(listener) }
        cleanup { speech.release() }
    }

    private fun cleanup(action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Speech SDK cleanup failed", e)
        }
    }

    private fun reportError(error: String) {
        Log.w(TAG, "Speech recognition failed: $error")
        onError(error)
    }

    private fun dispatch(action: () -> Unit) {
        main.post { if (!closed) action() }
    }

    private companion object {
        const val TAG = "ChagokSpeech"
        const val SESSION_TIMEOUT_MS = 20_000L
        const val FINAL_RESULT_WAIT_MS = 1_500L
    }
}
