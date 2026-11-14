package com.taeppir.chagokchagok

import android.app.Activity
import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var speedStatus: TextView
    private lateinit var speedButton: Button
    private lateinit var speechStatus: TextView
    private lateinit var speechText: TextView
    private lateinit var speechButton: Button

    private var speed: VehicleSpeed? = null
    private var speech: KoreanSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        content.addView(label(R.string.app_name, 28f))
        content.addView(label(R.string.speed_heading, 22f))
        speedStatus = label(R.string.speed_disconnected)
        content.addView(speedStatus)
        speedButton = Button(this).apply {
            setText(R.string.speed_start)
            setOnClickListener {
                speed?.close()
                speed = null
                connectSpeed()
            }
        }
        content.addView(speedButton)
        content.addView(label(R.string.speech_heading, 22f))
        speechStatus = label(R.string.speech_idle)
        speechText = TextView(this).apply { textSize = 22f }
        content.addView(speechStatus)
        content.addView(speechText)
        speechButton = Button(this).apply {
            setText(R.string.speech_start)
            setOnClickListener { toggleSpeech() }
        }
        content.addView(speechButton)
        setContentView(ScrollView(this).apply { addView(content) })
        Log.i(TAG, "Activity started")
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED) {
            connectSpeed()
        }
    }

    override fun onPause() {
        if (speech != null) finishSpeech(R.string.speech_stopped)
        super.onPause()
    }

    override fun onStop() {
        speed?.close()
        speed = null
        speedStatus.setText(R.string.speed_disconnected)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != SPEED_PERMISSION_REQUEST) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            connectSpeed()
        } else {
            speedStatus.setText(R.string.speed_denied)
        }
    }

    private fun connectSpeed() {
        if (speed != null) return
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Car.PERMISSION_SPEED), SPEED_PERMISSION_REQUEST)
            return
        }
        speedStatus.setText(R.string.speed_waiting)
        val connection = VehicleSpeed(
            context = this,
            onSpeed = { metersPerSecond ->
                speedStatus.text = getString(
                    R.string.speed_value, metersPerSecond * 3.6, metersPerSecond,
                )
            },
            onStatus = { message -> speedStatus.setText(message) },
        )
        speed = connection
        connection.connect()
    }

    private fun toggleSpeech() {
        if (speech != null) {
            finishSpeech(R.string.speech_stopped)
            return
        }
        speechText.text = ""
        speechStatus.setText(R.string.speech_connecting)
        speechButton.setText(R.string.speech_stop)
        val session = KoreanSpeech(
            context = applicationContext,
            onListening = { speechStatus.setText(R.string.speech_listening) },
            onResult = { result ->
                speechText.text = result
                finishSpeech(R.string.speech_complete)
            },
            onFinished = { status -> finishSpeech(status) },
            onError = { error ->
                finishSpeech(R.string.speech_error, error)
            },
        )
        speech = session
        session.start()
    }

    private fun finishSpeech(status: Int, vararg args: String) {
        speech?.close()
        speech = null
        speechStatus.text = getString(status, *args)
        speechButton.setText(R.string.speech_start)
    }

    private fun label(textId: Int, size: Float = 18f) = TextView(this).apply {
        setText(textId)
        textSize = size
    }

    private companion object {
        const val TAG = "ChagokChagok"
        const val SPEED_PERMISSION_REQUEST = 1
    }
}
