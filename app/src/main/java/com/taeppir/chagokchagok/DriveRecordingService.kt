package com.taeppir.chagokchagok

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs

internal class DriveRecordingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val samples = mutableListOf<Float>()
    private var speed: VehicleSpeed? = null
    private var latestKmh = 0f
    private var lastReadingMs = 0L
    private var recording = false
    private var previousKmh: Float? = null
    private var strongChanges = 0

    private val tick = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() - lastReadingMs <= 3_000 && latestKmh >= 0f) {
                val previous = previousKmh
                if (previous != null && abs(latestKmh - previous) >= 8f) strongChanges++
                previousKmh = latestKmh
                if (latestKmh >= 1f) samples.add(latestKmh)
            } else {
                previousKmh = null
            }
            main.postDelayed(this, 1_000)
        }
    }
    private val timeLimit = Runnable { finishRecording() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> finishRecording()
            START -> if (!recording) beginRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun beginRecording() {
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.drive_heading), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, DriveRecordingService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.drive_recording))
            .setContentText(getString(R.string.drive_recording_hint))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.drive_stop), stop).build())
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        samples.clear()
        latestKmh = 0f
        lastReadingMs = 0L
        previousKmh = null
        strongChanges = 0
        recording = true
        publish(RecordingStatus.Recording)
        speed = VehicleSpeed(
            context = this,
            onSpeed = { metersPerSecond ->
                latestKmh = metersPerSecond * 3.6f
                lastReadingMs = SystemClock.elapsedRealtime()
            },
            onStatus = { lastReadingMs = 0L },
        ).also { it.connect() }
        main.postDelayed(tick, 1_000)
        main.postDelayed(timeLimit, MAX_RECORDING_MS)
    }

    private fun finishRecording() {
        if (recording) {
            val profile = DriveProfile.summarize(samples, strongChanges)
            TripStore(this).profile = profile
            Log.i(TAG, "Drive saved: samples=${samples.size}, medianKmh=${profile?.medianKmh}, bpm=${profile?.tempo}, dynamic=${profile?.dynamic}")
            endRecording()
            publish(if (profile != null) RecordingStatus.Saved else RecordingStatus.NotEnoughData)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun endRecording() {
        main.removeCallbacks(tick)
        main.removeCallbacks(timeLimit)
        speed?.close()
        speed = null
        samples.clear()
        recording = false
    }

    override fun onDestroy() {
        if (recording) {
            endRecording()
            publish(RecordingStatus.Idle)
        }
        super.onDestroy()
    }

    internal enum class RecordingStatus { Idle, Recording, Saved, NotEnoughData }

    companion object {
        const val START = "com.taeppir.chagokchagok.START_DRIVE"
        const val STOP = "com.taeppir.chagokchagok.STOP_DRIVE"
        private const val TAG = "ChagokDrive"
        private const val CHANNEL = "drive_recording"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RECORDING_MS = 15 * 60 * 1_000L

        var status = RecordingStatus.Idle
            private set
        private val listeners = mutableSetOf<(RecordingStatus) -> Unit>()

        fun addListener(listener: (RecordingStatus) -> Unit) {
            listeners.add(listener)
            listener(status)
        }

        fun removeListener(listener: (RecordingStatus) -> Unit) {
            listeners.remove(listener)
        }

        private fun publish(state: RecordingStatus) {
            status = state
            listeners.toList().forEach { it(state) }
        }
    }
}
