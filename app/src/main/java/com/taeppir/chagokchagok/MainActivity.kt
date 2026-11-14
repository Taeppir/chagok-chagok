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
import android.widget.FrameLayout
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
    private lateinit var audioPulse: AudioPulseView
    private lateinit var storyPreview: TextView
    private val pages = mutableListOf<View>()
    private val tabs = mutableListOf<Button>()
    private var currentPage = 0
    private lateinit var pageScroll: ScrollView
    private lateinit var pageTitle: TextView
    private lateinit var environmentStatus: TextView
    private lateinit var repeatButton: Button
    private val suit by lazy { resources.getFont(R.font.suit_variable) }
    private lateinit var driveStatus: TextView
    private lateinit var driveButton: Button
    private lateinit var tempo: TextView
    private lateinit var tempoSource: TextView
    private lateinit var playButton: Button
    private val instrumentTiles = mutableListOf<Button>()
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
            ) else "16마디 · 준비 완료"
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

        val compact = resources.configuration.screenWidthDp < 1100 || resources.configuration.fontScale > 1.4f
        val content = column().apply {
            setPadding(dp(if (compact) 24 else 56), dp(36), dp(if (compact) 24 else 56), dp(32))
            isFocusableInTouchMode = true
        }
        val top = LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            pageTitle=heading(R.string.screen_player,36f)
            addView(pageTitle,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            environmentStatus=status(R.string.parked_status).apply {
                setPadding(dp(20),dp(12),dp(20),dp(12))
                background=shape(SURFACE,24)
            }
            addView(environmentStatus)
        }
        content.addView(top)
        val storyPanel=buildStoryPanel()
        val musicPanel=buildMusicPanel(compact)
        val soundPanel=buildSoundPanel()
        pages.addAll(listOf(musicPanel,storyPanel,soundPanel))
        val panels=FrameLayout(this)
        pages.forEach { panels.addView(it,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)) }
        content.addSpaced(panels,40)
        pageScroll=ScrollView(this).apply { isFillViewport=true; addView(content) }
        val navigationButtons=listOf(
            action(R.string.nav_player,ButtonStyle.Navigation,R.drawable.ic_music) { selectPage(0) },
            action(R.string.nav_story,ButtonStyle.Navigation,R.drawable.ic_car) { selectPage(1) },
            action(R.string.nav_sound,ButtonStyle.Navigation,R.drawable.ic_mixer) { selectPage(2) },
        )
        tabs.addAll(navigationButtons)
        val root=LinearLayout(this).apply {
            orientation=if(compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            background=AuroraDrawable()
        }
        if(compact) {
            val topNav=buttonRow(navigationButtons).apply { setPadding(dp(24),dp(16),dp(24),0) }
            root.addView(topNav)
            root.addView(pageScroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        } else {
            val rail=column().apply {
                setPadding(dp(24),dp(40),dp(24),dp(32))
                background=shape(0xBFFFFFFF.toInt(),0)
                gravity=Gravity.CENTER_HORIZONTAL
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_chagok_mark)
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },LinearLayout.LayoutParams(dp(60),dp(60)))
                addSpaced(heading(R.string.app_name,27f).apply { gravity=Gravity.CENTER },14)
                navigationButtons.forEachIndexed { i,button ->
                    button.gravity=Gravity.CENTER
                    val icon=button.compoundDrawablesRelative[0]
                    button.setCompoundDrawablesRelative(null,icon,null,null)
                    button.compoundDrawablePadding=dp(14)
                    addSpaced(button,if(i==0) 72 else 16,112)
                }
                addView(View(this@MainActivity),LinearLayout.LayoutParams(1,0,1f))
                addSpaced(label(R.string.brand_tagline,18f,MUTED).apply { gravity=Gravity.CENTER },24)
            }
            root.addView(rail,LinearLayout.LayoutParams(dp(216),ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(pageScroll,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f))
        }
        setContentView(root)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if(currentPage!=0) selectPage(0) else finish()
        }
        selectPage(savedInstanceState?.getInt("page") ?: 0)
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
        val compact=resources.configuration.screenWidthDp<1100 || resources.configuration.fontScale>1.4f
        val panels=LinearLayout(this@MainActivity).apply { orientation=if(compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        val journal=column().apply {
            setPadding(dp(32),dp(32),dp(32),dp(32))
            background=GlassDrawable(dp(28).toFloat())
            story=EditText(this@MainActivity).apply {
                id=View.generateViewId()
                setText(trip.story)
                setHint(R.string.story_hint)
                setTextColor(INK)
                setHintTextColor(MUTED)
                textSize=28f
                typeface=suit
                fontVariationSettings="'wght' 450"
                gravity=Gravity.TOP or Gravity.START
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines=4; maxLines=8; minHeight=dp(260)
                setPadding(dp(24),dp(24),dp(24),dp(24))
                background=StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),shape(CockpitTheme.SELECTED,20))
                    addState(intArrayOf(),shape(CockpitTheme.SOFT,20))
                }
            }
            addView(heading(R.string.story_heading,34f).apply { labelFor=story.id })
            addSpaced(story,24)
            speechButton=action(R.string.speech_start,ButtonStyle.Plain,R.drawable.ic_track_mic) { toggleSpeech() }
            addSpaced(speechButton,20)
            speechStatus=status(R.string.speech_idle)
            addSpaced(speechStatus,12)
        }
        val drive=column().apply {
            setPadding(dp(32),dp(32),dp(32),dp(32))
            background=GlassDrawable(dp(28).toFloat())
            addView(heading(R.string.drive_heading,34f))
            driveStatus=status(R.string.drive_idle)
            addSpaced(driveStatus,16)
            driveButton=action(R.string.drive_start,ButtonStyle.Drive,R.drawable.ic_car) { toggleRecording() }
            addSpaced(driveButton,28)
            weatherButton=action(R.string.weather_unknown,ButtonStyle.Plain,R.drawable.ic_track_weather) { chooseWeather() }
            addSpaced(weatherButton,24)
        }
        if(compact) {
            panels.addView(drive)
            panels.addSpaced(journal,24)
        } else {
            panels.addView(journal,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.25f))
            panels.addView(drive,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,0.75f).apply { marginStart=dp(24) })
        }
        addView(panels)
        addSpaced(action(R.string.back_to_player,ButtonStyle.Plain,R.drawable.ic_music) { selectPage(0) },24)
    }

    private fun selectPage(index: Int) {
        currentPage = index.coerceIn(0, 2)
        pages.forEachIndexed { i, page -> page.visibility = if (i == currentPage) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, tab ->
            tab.isSelected = i == currentPage
            tab.stateDescription = if (i == currentPage) "선택됨" else null
        }
        pageTitle.setText(listOf(R.string.screen_player,R.string.nav_story,R.string.sound_heading)[currentPage])
        if (::pageScroll.isInitialized) pageScroll.post { pageScroll.scrollTo(0, 0) }
        if (::story.isInitialized) {
            story.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(story.windowToken, 0)
        }
    }

    private fun buildMusicPanel(compact: Boolean) = column().apply {
        val hero=LinearLayout(this@MainActivity).apply {
            orientation=if(compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(if(compact) 24 else 48),dp(32),dp(if(compact) 24 else 32),dp(32))
            background=GlassDrawable(dp(32).toFloat())
        }
        audioPulse=AudioPulseView(this@MainActivity,track).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val controls=column().apply {
            addView(label(R.string.family_soundtrack,18f,MUTED,true).apply { letterSpacing=0.12f })
            addSpaced(heading(R.string.track_heading,64f),24)
            storyPreview=label(R.string.story_preview_empty,26f,INK).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END }
            addSpaced(storyPreview,20)
            genreLabel=label(R.string.genre_pop,22f,INK,true)
            tempo=label(R.string.track_tempo_placeholder,22f,INK,true)
            addSpaced(LinearLayout(this@MainActivity).apply {
                gravity=Gravity.CENTER_VERTICAL
                addView(genreLabel)
                addView(tempo,LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart=dp(32) })
            },32)
            rhythmLabel=label(R.string.rhythm_steady,20f,MUTED)
            addSpaced(rhythmLabel,8)
            playProgress=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=1000
                progressTintList=ColorStateList.valueOf(CockpitTheme.BLUE)
                progressBackgroundTintList=ColorStateList.valueOf(0xFFDCE4F0.toInt())
                contentDescription="믹스 재생 진행률"
            }
            addSpaced(playProgress,32,5)
            playTime=status(R.string.track_hint)
            addSpaced(playTime,12)
            playButton=action(R.string.track_play,ButtonStyle.Solid,R.drawable.ic_track_play) {
                if(track.active) stopTrack() else if(!restricted) {
                    if(speech!=null) finishSpeech(R.string.speech_stopped)
                    track.play(currentArrangement())
                }
                updateControls()
            }
            repeatButton=action(R.string.loop_mode,ButtonStyle.Plain,R.drawable.ic_repeat) {
                track.looping=!track.looping
                updateControls()
            }.apply {
                text=""
                gravity=Gravity.CENTER
                val icon=compoundDrawablesRelative[0]
                setCompoundDrawablesRelative(null,icon,null,null)
                setPadding(dp(24),dp(24),dp(24),dp(24))
            }
            val transport=LinearLayout(this@MainActivity).apply {
                gravity=Gravity.CENTER_VERTICAL
                addView(playButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                addView(repeatButton,LinearLayout.LayoutParams(dp(88),dp(88)).apply { marginStart=dp(16) })
            }
            addSpaced(transport,24)
            trackStatus=status(R.string.track_hint)
            addSpaced(trackStatus,12)
        }
        if(compact) {
            hero.addSpaced(audioPulse,0,320)
            hero.addSpaced(controls,16)
        } else {
            hero.addView(controls,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,0.85f))
            hero.addView(audioPulse,LinearLayout.LayoutParams(0,dp(620),1.15f).apply { marginStart=dp(40) })
        }
        addView(hero)
        val footer=LinearLayout(this@MainActivity).apply {
            orientation=if(compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            tempoSource=label(R.string.track_default,22f,MUTED)
            if(compact) addView(tempoSource) else addView(tempoSource,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            val edit=action(R.string.edit_story,ButtonStyle.Plain,R.drawable.ic_track_mic) { selectPage(1) }
            if(compact) addSpaced(edit,16) else addView(edit,LinearLayout.LayoutParams(dp(300),ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addSpaced(footer,24)
        addSpaced(status(R.string.vocal_pending),20)
    }

    private fun buildSoundPanel() = column().apply {
        setPadding(dp(36), dp(32), dp(36), dp(32))
        background = GlassDrawable(dp(36).toFloat())
        addView(heading(R.string.sound_heading, 36f))
        genreButton = action(R.string.choose_genre, ButtonStyle.Plain, R.drawable.ic_music) { chooseGenre() }
        mixButton = action(R.string.rap_mode, ButtonStyle.Plain, R.drawable.ic_mixer) { chooseMix() }
        addSpaced(buttonRow(listOf(genreButton, mixButton)), 24)
        beatPattern = BeatPatternView(this@MainActivity, INK).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addSpaced(beatPattern, 24, 70)
        addSpaced(heading(R.string.instrument_title, 28f), 28)
        val instrumentNames = listOf("와이퍼", "창문", "문", "깜빡이")
        val instrumentIcons = listOf(R.drawable.ic_wiper, R.drawable.ic_window, R.drawable.ic_door, R.drawable.ic_signal)
        instrumentNames.chunked(2).forEachIndexed { row, names ->
            addSpaced(buttonRow(names.mapIndexed { col, name ->
                val index = row * 2 + col
                action(R.string.instrument_demo, ButtonStyle.Plain, instrumentIcons[index]) { showInstrumentDetails() }
                    .apply { text = name; instrumentTiles.add(this) }
            }), 12)
        }
        signalLabel = status(R.string.instrument_hint)
        addSpaced(signalLabel, 12)
        val remix = action(R.string.remix, ButtonStyle.Plain, R.drawable.ic_shuffle) {
            stopTrack(); variation = (variation + 1) % 16; updateControls()
        }
        val examples = action(R.string.track_examples, ButtonStyle.Plain, R.drawable.ic_car) {
            choiceSheet("속도 미리듣기", listOf(
                "느린 길 · 20 km/h" to { selectedExample = SLOW_EXAMPLE },
                "빠른 길 · 72 km/h" to { selectedExample = FAST_EXAMPLE },
                "실제 주행 기록 사용" to { selectedExample = null },
            ))
        }
        addSpaced(buttonRow(listOf(remix, examples)), 24)
        addSpaced(action(R.string.back_to_player, ButtonStyle.Plain, R.drawable.ic_music) { selectPage(0) }, 20)
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
        outState.putInt("page", currentPage)
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
            else -> profile?.let { getString(R.string.drive_summary, it.medianKmh) }
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

    private fun showInstrumentDetails() {
        if (restricted) return
        AlertDialog.Builder(this)
            .setTitle("차 안의 악기들")
            .setMessage("와이퍼 → 셰이커\n창문 → 벨과 신스\n문 → 클랩\n깜빡이 → 림샷\n\n" +
                (trip.profile?.signalStatus?.takeIf { it.isNotEmpty() } ?: "기록을 마치면 차량 신호를 확인할 수 있어요.") +
                "\n\n편곡 ${variation + 1}")
            .setPositiveButton("악기 미리듣기") { _, _ -> chooseInstruments() }
            .setNegativeButton("닫기", null).show()
    }

    private fun buttonRow(buttons: List<Button>) = LinearLayout(this).apply {
        orientation = if (resources.configuration.fontScale > 1.25f ||
            resources.configuration.screenWidthDp < 500) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            if (orientation == LinearLayout.VERTICAL) addSpaced(button, if (index == 0) 0 else 10)
            else addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(12)
            })
        }
    }

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
        driveButton.setIcon(if (activeRecording) R.drawable.ic_track_stop else R.drawable.ic_car)
        story.isEnabled = !restricted
        driveButton.isEnabled = !restricted
        speechButton.isEnabled = !restricted
        weatherButton.isEnabled = !restricted
        weatherButton.text = getString(R.string.weather_value, getString(trip.weather.label))
        playButton.isEnabled = !restricted && !activeRecording
        val arrangement = currentArrangement()
        genreButton.text = (trip.genre?.let { getString(it.label) } ?: "자동 장르") + " ▾"
        mixButton.text = "믹스 설정 ▾"
        genreButton.isEnabled = !restricted
        mixButton.isEnabled = !restricted
        val counts = listOf(arrangement.wipers, arrangement.windows, arrangement.doors, arrangement.signals)
        val names = listOf("와이퍼", "창문", "문", "깜빡이")
        instrumentTiles.forEachIndexed { index, tile ->
            tile.text = "${names[index]}  ${counts[index]}"
            tile.isSelected = counts[index] > 0
            tile.isEnabled = !restricted
            tile.contentDescription = "${names[index]} 변화 ${counts[index]}회, 악기 상세 보기"
        }
        signalLabel.text = if (preview) "가상 신호 · 미리듣기" else "주행 신호 · 악기를 누르면 자세히"
        tempo.text = getString(R.string.track_tempo, arrangement.bpm)
        beatPattern.setPattern(arrangement)
        genreLabel.text = getString(arrangement.genre.label)
        storyPreview.text = story.text.toString().trim().ifEmpty { getString(R.string.story_preview_empty) }
        audioPulse.invalidate()
        repeatButton.isSelected=track.looping
        repeatButton.contentDescription=if(track.looping) "반복 재생 끄기" else "반복 재생 켜기"
        repeatButton.stateDescription=if(track.looping) "켜짐" else "꺼짐"
        repeatButton.isEnabled=!restricted
        environmentStatus.setText(if(activeRecording) R.string.drive_recording else if(restricted) R.string.park_required else R.string.parked_status)
        rhythmLabel.text = (if (trip.punchy) "둥둥 베이스" else "소프트") +
            (if (trip.rapMode) " · 랩 모드" else " · 멜로디") + (if (track.looping) " · 반복" else "")
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
        letterSpacing = -0.025f
    }

    private fun status(id: Int) = label(id, 19f, MUTED).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun label(id: Int, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setText(id)
        textSize = size
        setTextColor(color)
        typeface = suit
        fontVariationSettings = "'wght' ${if (bold) 650 else 450}"
        setLineSpacing(dp(4).toFloat(), 1f)
    }

    private enum class ButtonStyle { Outline, Plain, Solid, Drive, Navigation }

    private fun action(id: Int, style: ButtonStyle, icon: Int? = null, click: () -> Unit) = Button(this).apply {
        setText(id)
        isAllCaps = false
        textSize = if (style == ButtonStyle.Solid || style == ButtonStyle.Drive) 29f else 23f
        gravity = Gravity.CENTER_VERTICAL or if (icon == null) Gravity.CENTER_HORIZONTAL else Gravity.START
        typeface = suit
        fontVariationSettings = "'wght' 600"
        minHeight = dp(if (style == ButtonStyle.Solid || style == ButtonStyle.Drive) 100 else 76)
        minimumWidth = dp(76)
        stateListAnimator = null
        setPadding(dp(20), dp(12), dp(20), dp(12))
        val disabled = intArrayOf(-android.R.attr.state_enabled)
        val focused = intArrayOf(android.R.attr.state_focused)
        val selected = intArrayOf(android.R.attr.state_selected)
        val fill = when (style) {
            ButtonStyle.Outline -> SURFACE
            ButtonStyle.Plain -> CockpitTheme.SOFT
            ButtonStyle.Navigation -> Color.TRANSPARENT
            ButtonStyle.Drive -> CockpitTheme.BLUE
            ButtonStyle.Solid -> CockpitTheme.BLUE
        }
        val foreground = if (style == ButtonStyle.Solid || style == ButtonStyle.Drive) Color.WHITE else INK
        val inkStates = ColorStateList(
            arrayOf(disabled, focused, selected, intArrayOf()),
            intArrayOf(MUTED, Color.WHITE, CockpitTheme.BLUE, foreground),
        )
        setTextColor(inkStates)
        compoundDrawableTintList = inkStates
        compoundDrawablePadding = dp(12)
        icon?.let { setIcon(it) }
        val states = StateListDrawable().apply {
            addState(disabled, shape(BACKGROUND, 20))
            addState(focused, shape(CockpitTheme.BLUE, 20))
            addState(selected, shape(ACCENT, 20))
            addState(intArrayOf(), shape(fill, 20))
        }
        background = RippleDrawable(ColorStateList.valueOf(0x224875E0), states, shape(Color.WHITE, 20))
        setOnClickListener { click() }
    }

    private fun Button.setIcon(id: Int) {
        val icon = context.getDrawable(id)?.mutate()
        icon?.setBounds(0, 0, dp(32), dp(32))
        setCompoundDrawablesRelative(icon, null, null, null)
    }

    private fun rule() = View(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
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
        const val BACKGROUND = CockpitTheme.BACKGROUND
        const val SURFACE = CockpitTheme.SURFACE
        const val INK = CockpitTheme.INK
        const val MUTED = CockpitTheme.MUTED
        const val ACCENT = CockpitTheme.SELECTED
    }
}
