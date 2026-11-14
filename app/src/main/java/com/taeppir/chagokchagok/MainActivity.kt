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
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
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
    private lateinit var genreButton: Button
    private lateinit var mixButton: Button
    private lateinit var signalLabel: TextView
    private var variation = 0
    private var previewWipers = 0
    private var previewWindows = 0
    private var preview = false
    private lateinit var beatPattern: BeatPatternView
    private lateinit var driveStatus: TextView
    private lateinit var driveButton: Button
    private lateinit var tempo: TextView
    private lateinit var tempoSource: TextView
    private lateinit var playButton: Button
    private lateinit var slowButton: Button
    private lateinit var fastButton: Button
    private lateinit var trackStatus: TextView
    private lateinit var playProgress: ProgressBar
    private lateinit var playTime: TextView
    private val main = Handler(Looper.getMainLooper())
    private val playbackTick = object : Runnable {
        override fun run() {
            val progress = track.progress
            playProgress.progress = (progress * 1000).toInt()
            val total = track.durationSeconds
            val current = (progress * total).toInt()
            val section = when {
                progress < 0.125f -> "인트로"
                progress < 0.5f -> "벌스"
                progress < 0.875f -> "코러스"
                else -> "아웃트로"
            }
            playTime.text = if (track.active) "%d:%02d / %d:%02d · %s".format(
                current / 60, current % 60, total / 60, total % 60, section
            ) else "16마디 · 인트로 / 벌스 / 코러스 / 아웃트로"
            main.postDelayed(this, 1_500)
        }
    }

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
            preview = false
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
            background = AuroraDrawable()
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
        setPadding(dp(28), dp(28), dp(28), dp(28))
        background = shape(0xCCFFFFFF.toInt(), 28)
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
        addSpaced(heading(R.string.instrument_title, 25f), 20)
        signalLabel = status(R.string.instrument_hint)
        addSpaced(signalLabel, 12)
        addSpaced(action(R.string.instrument_demo, ButtonStyle.Plain) { chooseInstruments() }, 8)
        addSpaced(action(R.string.remix, ButtonStyle.Plain) {
            stopTrack()
            variation = (variation + 1) % 16
            updateControls()
        }, 8)


    }

    private fun buildMusicPanel(compact: Boolean) = column().apply {
        setPadding(dp(28), dp(28), dp(28), dp(28))
        background = shape(0xBFFFFFFF.toInt(), 28)
        addView(heading(R.string.track_heading, 32f))
        val sleeve = column().apply {
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF96C6FF.toInt(), 0xFFCAB4FF.toInt(), 0xFFF5C5E7.toInt(), 0xFFA9EFE0.toInt())
            ).apply { cornerRadius = dp(22).toFloat() }
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
        genreButton = action(R.string.choose_genre, ButtonStyle.Plain) { chooseGenre() }
        addSpaced(genreButton, 12)

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
        playProgress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Color.rgb(76, 66, 193))
            progressBackgroundTintList = ColorStateList.valueOf(0x335B67B0)
            contentDescription = "반주 재생 진행률"
        }
        addSpaced(playProgress, 12, 6)
        playTime = status(R.string.track_hint)
        addSpaced(playTime, 8)
        mixButton = action(R.string.rap_mode, ButtonStyle.Plain) { chooseMix() }
        addSpaced(mixButton, 12)
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
        main.post(playbackTick)
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
        main.removeCallbacks(playbackTick)
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

    private fun choiceSheet(title: String, items: List<Pair<String, () -> Unit>>) {
        if (restricted) return
        val content = column().apply { setPadding(dp(24), dp(8), dp(24), dp(24)) }
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(content) }).create()
        items.forEach { (name, choose) ->
            content.addSpaced(action(R.string.choose_genre, ButtonStyle.Plain) {
                if (!restricted) { stopTrack(); choose(); updateControls() }
                dialog.dismiss()
            }.apply { text = name }, 8)
        }
        content.addSpaced(action(android.R.string.cancel, ButtonStyle.Plain) { dialog.dismiss() }, 8)
        dialog.show()
    }

    private fun chooseGenre() = choiceSheet("오늘의 장르",
        listOf("자동 추천" to { trip.genre = null }) +
            Genre.entries.map { genre -> getString(genre.label) to { trip.genre = genre } })

    private fun chooseMix() = choiceSheet("믹스 설정", listOf(
        "둥둥 베이스 · 킥과 저음 강조" to { trip.punchy = true },
        "소프트 믹스 · 부드러운 저음" to { trip.punchy = false },
        "랩 모드 · 멜로디를 비우고 비트에 집중" to { trip.rapMode = true },
        "멜로디 모드 · 신스 훅 더하기" to { trip.rapMode = false },
        "반복 재생 켜기" to { track.looping = true },
        "반복 재생 끄기" to { track.looping = false },
    ))

    private fun chooseInstruments() = choiceSheet("악기 미리듣기 · 가상 신호", listOf(
        "와이퍼 → 촘촘한 셰이커" to { preview = true; previewWipers = 4 },
        "창문 → 벨과 공기감 있는 신스" to { preview = true; previewWindows = 4 },
        "모든 악기" to { preview = true; previewWipers = 4; previewWindows = 4 },
        "기본 비트" to { preview = true; previewWipers = 0; previewWindows = 0 },
        "실제 주행 기록 사용" to { preview = false },
    ))

    private fun currentArrangement(): Arrangement {
        val profile = trip.profile
        val bpm = selectedExample?.let { DriveProfile(it.toFloat(), 0).tempo }
            ?: profile?.tempo ?: DriveProfile.DEFAULT_TEMPO
        return Arrangement.recommend(bpm, trip.weather, story.text.toString(), selectedExample == null && profile?.dynamic == true)
            .copy(genre = trip.genre ?: Arrangement.recommend(bpm, trip.weather, story.text.toString(), false).genre,
                wipers = if (preview) previewWipers else profile?.wipers ?: 0,
                windows = if (preview) previewWindows else profile?.windows ?: 0,
                rapMode = trip.rapMode, variation = variation,
                doors = if (preview) previewWindows else profile?.doors ?: 0,
                signals = if (preview) previewWipers else profile?.signals ?: 0, punchy = trip.punchy)
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
        genreButton.text = "장르 · " + (trip.genre?.let { getString(it.label) } ?: "자동 추천")
        mixButton.text = "믹스 · " + (if (trip.punchy) "둥둥 베이스 · " else "소프트 · ") + (if (trip.rapMode) "랩 모드" else "멜로디") + (if (track.looping) " · 반복" else "")
        genreButton.isEnabled = !restricted
        mixButton.isEnabled = !restricted
        signalLabel.text = (if (preview) "악기 미리듣기" else "주행에서 모은 소리") +
            " · 와이퍼 ${arrangement.wipers} · 창문 ${arrangement.windows} · 문 ${arrangement.doors} · 방향지시등 ${arrangement.signals}" +
            (if (!preview && trip.profile?.signalStatus?.isNotEmpty() == true) "\n${trip.profile?.signalStatus}" else "") +
            "\n편곡 ${variation + 1} · 와이퍼 → 셰이커 · 창문 → 벨 · 문 → 클랩 · 방향지시등 → 림샷"
        tempo.text = getString(R.string.track_tempo, arrangement.bpm)
        beatPattern.setPattern(arrangement)
        genreLabel.text = getString(arrangement.genre.label) + " · DRIVE MIX"
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
            ButtonStyle.Solid -> Color.rgb(76, 66, 193)
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
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun shape(color: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 1) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()

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
        val BACKGROUND = Color.rgb(241, 245, 255)
        val SURFACE = 0xDFFFFFFF.toInt()
        val RULE = Color.rgb(211, 212, 200)
        val CONTROL_BORDER = Color.rgb(121, 126, 111)
        val INK = Color.rgb(31, 35, 74)
        val MUTED = Color.rgb(70, 77, 110)
        val ACCENT = Color.rgb(196, 211, 255)
    }
}
