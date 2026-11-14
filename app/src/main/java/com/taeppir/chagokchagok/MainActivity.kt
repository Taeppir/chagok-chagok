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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private lateinit var beatPattern: BeatPatternView
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

        val compact = resources.configuration.screenWidthDp < 950
        val gutter = dp(if (compact) 24 else 56)
        val content = column().apply {
            setPadding(gutter, dp(32), gutter, dp(48))
            isFocusableInTouchMode = true
        }
        val masthead = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chagok)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(heading(R.string.app_name, 31f).apply { letterSpacing = -0.06f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(12) })
        }
        content.addView(masthead)
        content.addSpaced(rule(), 20, 2)

        val panels = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val storyPanel = buildStoryPanel()
        val musicPanel = buildMusicPanel(compact)
        if (compact) {
            panels.addView(storyPanel)
            panels.addSpaced(musicPanel, 40)
        } else {
            panels.addView(storyPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
            panels.addView(musicPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                marginStart = dp(56)
            })
        }
        content.addSpaced(panels, 36)
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

    private fun buildStoryPanel() = column().apply {
        addView(heading(R.string.drive_heading, 32f))
        driveStatus = status(R.string.drive_idle)
        addSpaced(driveStatus, 8)
        driveButton = action(R.string.drive_start, ButtonStyle.Outline, R.drawable.ic_track_record) { toggleRecording() }
        addSpaced(driveButton, 16)
        addSpaced(rule(), 32, 1)

        story = EditText(this@MainActivity).apply {
            id = View.generateViewId()
            setText(trip.story)
            setHint(R.string.story_hint)
            setTextColor(INK)
            setHintTextColor(MUTED)
            textSize = 23f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 5
            minHeight = dp(110)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(SURFACE, 10, INK, 3))
                addState(intArrayOf(), shape(SURFACE, 10, CONTROL_BORDER))
            }
            setSelectAllOnFocus(false)
        }
        addSpaced(heading(R.string.story_heading, 32f).apply { labelFor = story.id }, 24)
        addSpaced(story, 12)
        speechButton = action(R.string.speech_start, ButtonStyle.Plain, R.drawable.ic_track_mic) { toggleSpeech() }
        addSpaced(speechButton, 12)
        speechStatus = status(R.string.speech_idle)
        addSpaced(speechStatus, 4)
        weatherButton = action(R.string.weather_unknown, ButtonStyle.Plain, R.drawable.ic_track_weather) { chooseWeather() }
        addSpaced(weatherButton, 16)
    }

    private fun buildMusicPanel(compact: Boolean) = column().apply {
        addView(heading(R.string.track_heading, 32f))
        val sleeve = column().apply {
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = shape(ACCENT, 16)
            genreLabel = label(R.string.genre_acoustic, 25f, INK, bold = true)
            addView(genreLabel)
            tempo = label(R.string.track_tempo_placeholder, if (compact) 54f else 84f, INK, bold = true).apply {
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                letterSpacing = -0.025f
                includeFontPadding = false
            }
            addSpaced(tempo, 6)
            tempoSource = label(R.string.track_default, 20f, INK)
            addSpaced(tempoSource, 4)
            beatPattern = BeatPatternView(this@MainActivity, INK).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addSpaced(beatPattern, 16, if (compact) 65 else 86)
            rhythmLabel = label(R.string.rhythm_steady, 19f, INK)
            addSpaced(rhythmLabel, 12)
        }
        addSpaced(sleeve, 16)

        playButton = action(R.string.track_play, ButtonStyle.Solid, R.drawable.ic_track_play) {
            if (track.active) stopTrack() else if (!restricted) {
                if (speech != null) finishSpeech(R.string.speech_stopped)
                track.play(currentArrangement())
            }
            updateControls()
        }
        addSpaced(playButton, 24)
        trackStatus = status(R.string.track_hint)
        addSpaced(trackStatus, 8)

        addSpaced(heading(R.string.track_examples, 21f), 24)
        slowButton = action(R.string.track_example_slow, ButtonStyle.Outline) { selectExample(SLOW_EXAMPLE) }
        fastButton = action(R.string.track_example_fast, ButtonStyle.Outline) { selectExample(FAST_EXAMPLE) }
        val examples = LinearLayout(this@MainActivity).apply {
            orientation = if (compact || resources.configuration.fontScale > 1.25f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (orientation == LinearLayout.VERTICAL) {
                addView(slowButton)
                addSpaced(fastButton, 12)
            } else {
                addView(slowButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(fastButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(16)
                })
            }
        }
        addSpaced(examples, 12)
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
        val choices = column().apply {
            setPadding(dp(24), dp(12), dp(24), dp(24))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.weather_heading)
            .setView(ScrollView(this).apply { addView(choices) })
            .create()
        Weather.entries.forEach { option ->
            choices.addSpaced(action(option.label, ButtonStyle.Outline) {
                if (!restricted) {
                    stopTrack()
                    trip.weather = option
                    updateControls()
                }
                dialog.dismiss()
            }.apply { isSelected = trip.weather == option }, 8)
        }
        choices.addSpaced(action(android.R.string.cancel, ButtonStyle.Plain) { dialog.dismiss() }, 16)
        dialog.show()
    }

    private fun currentArrangement(): Arrangement {
        val profile = trip.profile
        val bpm = selectedExample?.let { DriveProfile(it.toFloat(), 0).tempo }
            ?: profile?.tempo ?: DriveProfile.DEFAULT_TEMPO
        return Arrangement.recommend(bpm, trip.weather, story.text.toString(), selectedExample == null && profile?.dynamic == true)
    }

    private fun updateControls() {
        val activeRecording = recording == DriveRecordingService.RecordingStatus.Recording
        driveButton.setText(if (activeRecording) R.string.drive_stop else R.string.drive_start)
        driveButton.isSelected = activeRecording
        driveButton.setIcon(if (activeRecording) R.drawable.ic_track_stop else R.drawable.ic_track_record)
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
        beatPattern.setPattern(arrangement.syncopated)
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
        playButton.setIcon(if (track.active) R.drawable.ic_track_stop else R.drawable.ic_track_play)
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
        speechButton.setIcon(R.drawable.ic_track_stop)
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
        speechButton.setIcon(R.drawable.ic_track_mic)
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun heading(id: Int, size: Float) = label(id, size, INK, bold = true).apply {
        isAccessibilityHeading = true
    }

    private fun status(id: Int) = label(id, 19f, MUTED).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun label(id: Int, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setText(id)
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private enum class ButtonStyle { Outline, Plain, Solid }

    private fun action(id: Int, style: ButtonStyle, icon: Int? = null, click: () -> Unit) = Button(this).apply {
        setText(id)
        isAllCaps = false
        textSize = if (style == ButtonStyle.Solid) 25f else 21f
        gravity = Gravity.CENTER_VERTICAL or if (icon == null) Gravity.CENTER_HORIZONTAL else Gravity.START
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(76)
        minimumWidth = dp(76)
        stateListAnimator = null
        setPadding(dp(20), dp(12), dp(20), dp(12))
        val disabled = intArrayOf(-android.R.attr.state_enabled)
        val focused = intArrayOf(android.R.attr.state_focused)
        val selected = intArrayOf(android.R.attr.state_selected)
        val fill = when (style) {
            ButtonStyle.Outline -> SURFACE
            ButtonStyle.Plain -> Color.TRANSPARENT
            ButtonStyle.Solid -> INK
        }
        val foreground = if (style == ButtonStyle.Solid) Color.WHITE else INK
        val outline = if (style == ButtonStyle.Outline) CONTROL_BORDER else null
        val inkStates = ColorStateList(
            arrayOf(disabled, focused, selected, intArrayOf()),
            intArrayOf(MUTED, INK, INK, foreground),
        )
        setTextColor(inkStates)
        compoundDrawableTintList = inkStates
        compoundDrawablePadding = dp(12)
        icon?.let { setIcon(it) }
        val states = StateListDrawable().apply {
            addState(disabled, shape(BACKGROUND, 10, RULE))
            addState(focused, shape(ACCENT, 10, INK, 3))
            addState(selected, shape(ACCENT, 10, INK, 2))
            addState(intArrayOf(), shape(fill, 10, outline))
        }
        background = RippleDrawable(ColorStateList.valueOf(0x38748167), states, shape(Color.WHITE, 10))
        setOnClickListener { click() }
    }

    private fun Button.setIcon(id: Int) {
        val icon = context.getDrawable(id)?.mutate()
        icon?.setBounds(0, 0, dp(24), dp(24))
        setCompoundDrawablesRelative(icon, null, null, null)
    }

    private fun rule() = View(this).apply {
        setBackgroundColor(RULE)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun shape(color: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 1) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(strokeWidth), stroke)
    }

    private fun LinearLayout.addSpaced(view: View, top: Int, heightDp: Int? = null) {
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            heightDp?.let(::dp) ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val SPEED_PERMISSION_REQUEST = 1
        const val SLOW_EXAMPLE = 20
        const val FAST_EXAMPLE = 72
        val BACKGROUND = Color.rgb(249, 248, 243)
        val SURFACE = Color.WHITE
        val RULE = Color.rgb(211, 212, 200)
        val CONTROL_BORDER = Color.rgb(121, 126, 111)
        val INK = Color.rgb(24, 29, 19)
        val MUTED = Color.rgb(76, 82, 69)
        val ACCENT = Color.rgb(194, 249, 57)
    }
}
