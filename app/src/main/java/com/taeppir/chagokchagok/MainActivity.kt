package com.taeppir.chagokchagok

import android.app.Activity
import android.app.AlertDialog
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var trip: TripStore
    private lateinit var track: BackingTrack
    private lateinit var story: EditText
    private lateinit var speechStatus: TextView
    private lateinit var speechButton: Button
    private lateinit var weatherButton: Button
    private lateinit var genreLabel: TextView
    private lateinit var rhythmLabel: TextView
    private lateinit var driveStatus: TextView
    private lateinit var driveButton: Button
    private lateinit var tempo: TextView
    private lateinit var tempoSource: TextView
    private lateinit var playButton: Button
    private lateinit var slowButton: Button
    private lateinit var fastButton: Button
    private lateinit var trackStatus: TextView

    private var speech: KoreanSpeech? = null
    private var selectedExample: Int? = null
    private var car: Car? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var restricted = true
    private var watchingRestrictions = false
    private var recording = DriveRecordingService.RecordingStatus.Idle
    private val driveListener: (DriveRecordingService.RecordingStatus) -> Unit = { status ->
        recording = status
        if (status == DriveRecordingService.RecordingStatus.Saved ||
            status == DriveRecordingService.RecordingStatus.NotEnoughData
        ) {
            selectedExample = null
            stopTrack()
        }
        updateDrive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trip = TripStore(this)
        track = BackingTrack(this) { status ->
            trackStatus.setText(status)
            updateControls()
        }
        selectedExample = savedInstanceState?.getInt("example")?.takeIf { it > 0 }

        val compact = resources.configuration.screenWidthDp < 700
        val content = column().apply {
            setPadding(dp(if (compact) 24 else 48), dp(32), dp(if (compact) 24 else 48), dp(32))
            isFocusableInTouchMode = true
        }
        val title = label(R.string.app_name, 52f, INK, bold = true).apply {
            background = shape(ACCENT, 24)
            setPadding(dp(22), dp(4), dp(22), dp(6))
        }
        content.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addSpaced(label(R.string.app_subtitle, 23f, MUTED), 5)

        val panels = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val storyPanel = buildStoryPanel()
        val musicPanel = buildMusicPanel()
        if (compact) {
            panels.addView(storyPanel)
            panels.addSpaced(musicPanel, 20)
        } else {
            panels.addView(storyPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            panels.addView(musicPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(24)
            })
        }
        content.addSpaced(panels, 28)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            addView(content)
        })
        content.requestFocus()
        story.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                trip.story = s?.toString().orEmpty()
                stopTrack()
                updateControls()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateDrive()
    }

    private fun buildStoryPanel() = panel().apply {
        addView(label(R.string.story_heading, 27f, INK, bold = true))
        story = EditText(this@MainActivity).apply {
            setText(trip.story)
            setHint(R.string.story_hint)
            setTextColor(INK)
            setHintTextColor(MUTED)
            textSize = 22f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = shape(EDITOR, 16, BORDER)
            setSelectAllOnFocus(false)
        }
        addSpaced(story, 18)
        speechButton = action(R.string.speech_start) { toggleSpeech() }
        addSpaced(speechButton, 16)
        speechStatus = label(R.string.speech_idle, 17f, MUTED)
        addSpaced(speechStatus, 12)
        weatherButton = action(R.string.weather_unknown) { chooseWeather() }
        addSpaced(weatherButton, 16)

        addSpaced(label(R.string.drive_heading, 24f, INK, bold = true), 24)
        driveStatus = label(R.string.drive_idle, 18f, MUTED)
        addSpaced(driveStatus, 8)
        driveButton = action(R.string.drive_start) { toggleRecording() }
        addSpaced(driveButton, 14)
    }

    private fun buildMusicPanel() = panel().apply {
        addView(label(R.string.track_heading, 27f, INK, bold = true))
        tempo = label(R.string.track_tempo_placeholder, 68f, INK, bold = true)
        addSpaced(tempo, 10)
        tempoSource = label(R.string.track_default, 19f, MUTED)
        addSpaced(tempoSource, 1)
        genreLabel = label(R.string.genre_acoustic, 25f, INK, bold = true)
        addSpaced(genreLabel, 16)
        rhythmLabel = label(R.string.rhythm_steady, 18f, MUTED)
        addSpaced(rhythmLabel, 5)
        playButton = action(R.string.track_play, primary = true) {
            if (track.active) stopTrack() else if (!restricted) {
                if (speech != null) finishSpeech(R.string.speech_stopped)
                track.play(currentArrangement())
            }
            updateControls()
        }
        addSpaced(playButton, 20)
        trackStatus = label(R.string.track_hint, 17f, MUTED)
        addSpaced(trackStatus, 12)
        addSpaced(label(R.string.track_examples, 20f, INK, bold = true), 24)
        slowButton = action(R.string.track_example_slow) { selectExample(SLOW_EXAMPLE) }
        fastButton = action(R.string.track_example_fast) { selectExample(FAST_EXAMPLE) }
        val examples = LinearLayout(this@MainActivity).apply {
            orientation = if (resources.configuration.screenWidthDp < 700) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (orientation == LinearLayout.VERTICAL) {
                addView(slowButton)
                addSpaced(fastButton, 8)
            } else {
                addView(slowButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(fastButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                })
            }
        }
        addSpaced(examples, 10)
    }

    override fun onStart() {
        super.onStart()
        DriveRecordingService.addListener(driveListener)
        watchingRestrictions = true
        car = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connection, ready ->
            if (!watchingRestrictions) return@createCar
            if (ready) {
                restrictions = connection.getCarManager(CarUxRestrictionsManager::class.java)
                restrictions?.registerListener { updateRestrictions(it.isRequiresDistractionOptimization) }
                updateRestrictions(restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization != false)
            } else {
                restrictions = null
                updateRestrictions(true)
            }
        }
    }

    override fun onPause() {
        trip.story = story.text.toString()
        if (speech != null) finishSpeech(R.string.speech_stopped)
        stopTrack()
        super.onPause()
    }

    override fun onStop() {
        DriveRecordingService.removeListener(driveListener)
        watchingRestrictions = false
        restrictions?.unregisterListener()
        restrictions = null
        car?.disconnect()
        car = null
        updateRestrictions(true)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("example", selectedExample ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != SPEED_PERMISSION_REQUEST) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && !restricted) {
            startRecording()
        } else {
            driveStatus.setText(R.string.speed_denied)
        }
    }

    private fun updateRestrictions(value: Boolean) {
        restricted = value
        if (value) {
            if (speech != null) finishSpeech(R.string.speech_stopped)
            stopTrack()
        }
        updateControls()
    }

    private fun toggleRecording() {
        if (restricted) return
        if (recording == DriveRecordingService.RecordingStatus.Recording) {
            startService(Intent(this, DriveRecordingService::class.java).setAction(DriveRecordingService.STOP))
        } else if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Car.PERMISSION_SPEED), SPEED_PERMISSION_REQUEST)
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            startForegroundService(Intent(this, DriveRecordingService::class.java).setAction(DriveRecordingService.START))
        } catch (e: RuntimeException) {
            Log.w("ChagokDrive", "Could not start drive recording", e)
            driveStatus.setText(R.string.drive_failed)
        }
    }

    private fun updateDrive() {
        val profile = trip.profile
        val status = when (recording) {
            DriveRecordingService.RecordingStatus.Recording -> getString(R.string.drive_recording_hint)
            DriveRecordingService.RecordingStatus.NotEnoughData -> getString(R.string.drive_empty)
            else -> profile?.let { getString(R.string.drive_summary, it.medianKmh, it.sampleCount) }
                ?: getString(R.string.drive_idle)
        }
        driveStatus.text = status
        updateControls()
    }

    private fun selectExample(speed: Int) {
        stopTrack()
        selectedExample = if (selectedExample == speed) null else speed
        updateControls()
    }

    private fun chooseWeather() {
        if (restricted) return
        val choices = Weather.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.weather_heading)
            .setSingleChoiceItems(choices.map { getString(it.label) }.toTypedArray(), trip.weather.ordinal) { dialog, index ->
                if (!restricted) {
                    stopTrack()
                    trip.weather = choices[index]
                    updateControls()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun currentArrangement(): Arrangement {
        val profile = trip.profile
        val bpm = selectedExample?.let { DriveProfile(it.toFloat(), 0).tempo }
            ?: profile?.tempo ?: DriveProfile.DEFAULT_TEMPO
        return Arrangement.recommend(bpm, trip.weather, story.text.toString(), profile?.dynamic == true)
    }

    private fun updateControls() {
        val activeRecording = recording == DriveRecordingService.RecordingStatus.Recording
        driveButton.setText(if (activeRecording) R.string.drive_stop else R.string.drive_start)
        story.isEnabled = !restricted
        driveButton.isEnabled = !restricted
        speechButton.isEnabled = !restricted
        weatherButton.isEnabled = !restricted
        weatherButton.text = getString(R.string.weather_value, getString(trip.weather.label))
        playButton.isEnabled = !restricted && !activeRecording
        slowButton.isEnabled = !restricted && !activeRecording
        fastButton.isEnabled = !restricted && !activeRecording
        slowButton.isSelected = selectedExample == SLOW_EXAMPLE
        fastButton.isSelected = selectedExample == FAST_EXAMPLE
        val arrangement = currentArrangement()
        tempo.text = getString(R.string.track_tempo, arrangement.bpm)
        genreLabel.text = getString(R.string.genre_value, getString(arrangement.genre.label))
        rhythmLabel.text = getString(
            R.string.rhythm_value,
            getString(if (arrangement.syncopated) R.string.rhythm_dynamic else R.string.rhythm_steady),
            getString(arrangement.genre.reason),
        )
        tempoSource.text = selectedExample?.let { getString(R.string.track_example_source, it) }
            ?: trip.profile?.let { getString(R.string.track_drive_source, it.medianKmh) }
            ?: getString(R.string.track_default)
        playButton.setText(if (track.active) R.string.track_stop else R.string.track_play)
    }

    private fun stopTrack() {
        if (!track.active) return
        track.stop()
        trackStatus.setText(R.string.track_hint)
    }

    private fun toggleSpeech() {
        if (restricted) return
        if (speech != null) {
            finishSpeech(R.string.speech_stopped)
            return
        }
        stopTrack()
        updateControls()
        speechStatus.setText(R.string.speech_connecting)
        speechButton.setText(R.string.speech_stop)
        speech = KoreanSpeech(
            context = applicationContext,
            onListening = { speechStatus.setText(R.string.speech_listening) },
            onResult = { result ->
                story.setText(result)
                finishSpeech(R.string.speech_complete)
            },
            onFinished = { finishSpeech(it) },
            onError = { finishSpeech(R.string.speech_error, it) },
        ).also { it.start() }
    }

    private fun finishSpeech(status: Int, vararg args: String) {
        speech?.close()
        speech = null
        speechStatus.text = getString(status, *args)
        speechButton.setText(R.string.speech_start)
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun panel() = column().apply {
        setPadding(dp(24), dp(24), dp(24), dp(24))
        background = shape(PANEL, 26, BORDER)
    }

    private fun label(id: Int, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setText(id)
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun action(id: Int, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        setText(id)
        isAllCaps = false
        textSize = 18f
        minHeight = dp(76)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        val colors = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_selected), intArrayOf())
        setTextColor(ColorStateList(colors, intArrayOf(MUTED, INK, INK)))
        val fill = StateListDrawable().apply {
            addState(colors[0], shape(PANEL, 14, BORDER))
            addState(colors[1], shape(ACCENT, 14, ACCENT_BORDER))
            addState(colors[2], shape(if (primary) ACCENT else BUTTON, 14, if (primary) ACCENT_BORDER else BORDER))
        }
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), fill, null)
        setOnClickListener { click() }
    }

    private fun shape(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun LinearLayout.addSpaced(view: View, top: Int) {
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val SPEED_PERMISSION_REQUEST = 1
        const val SLOW_EXAMPLE = 20
        const val FAST_EXAMPLE = 72
        val BACKGROUND = Color.rgb(246, 248, 235)
        val PANEL = Color.WHITE
        val EDITOR = Color.rgb(247, 249, 240)
        val BUTTON = Color.rgb(236, 242, 221)
        val BORDER = Color.rgb(209, 221, 189)
        val INK = Color.rgb(27, 43, 15)
        val MUTED = Color.rgb(75, 88, 65)
        val ACCENT = Color.rgb(194, 249, 57)
        val ACCENT_BORDER = Color.rgb(112, 157, 34)
    }
}
