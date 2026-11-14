package com.taeppir.chagokchagok

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
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
    private val DRIVE=0
    private val REVIEW=1
    private val STORY=2
    private val PLAYER=3
    private lateinit var trip: TripStore
    private lateinit var track: BackingTrack
    private lateinit var songs: AiSongs
    private lateinit var songPlayer: SongPlayer
    private var usingSong = false
    private lateinit var songStatus: TextView
    private lateinit var generateButton: Button
    private lateinit var lyricsButton: Button
    private lateinit var renameButton: Button
    private lateinit var inlineLyrics: TextView
    private lateinit var lyricsCaption: TextView
    private lateinit var homeBeat: Button
    private lateinit var homeStory: Button
    private lateinit var homeSummary: TextView
    private var expandedLyrics = false
    private val localPrefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private lateinit var localButton: Button
    private lateinit var songHeading: TextView
    private lateinit var story: EditText
    private lateinit var newTripButton: Button
    private lateinit var speechStatus: TextView
    private lateinit var speechButton: Button
    private lateinit var weatherButton: Button
    private lateinit var genreLabel: TextView
    private lateinit var rhythmLabel: TextView
    private lateinit var genreButton: Button
    private val generationGenres = mutableListOf<Button>()
    private lateinit var generationGenreHint: TextView
    private lateinit var mixButton: Button
    private lateinit var signalLabel: TextView
    private lateinit var drivingRecipe: TextView
    private var variation = 0
    private var previewWipers = 0
    private var previewWindows = 0
    private var previewDoors = 0
    private var previewSignals = 0
    private var openSheet: Dialog? = null
    private var preview = false
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
            val progress = if (usingSong) songPlayer.progress else track.progress
            playProgress.progress = (progress * 1000).toInt()
            val total = if (usingSong) songPlayer.durationSeconds else track.durationSeconds
            val current = (progress * total).toInt()
            val section = when {
                progress < 0.125f -> "인트로"
                progress < 0.5f -> "벌스"
                progress < 0.875f -> "코러스"
                else -> "아웃트로"
            }
            val timeLabel = if (track.active || songPlayer.active) "%d:%02d / %d:%02d · %s".format(
                current / 60, current % 60, total / 60, total % 60, if (usingSong) "우리 노래" else section
            ) else if (usingSong) "AI 보컬곡 · 재생 준비" else "16마디 · 준비 완료"
            if(playTime.text.toString()!=timeLabel) playTime.text=timeLabel
            updateInlineLyrics()
            main.postDelayed(this, 500)
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
        val previousStatus=recording
        recording = status
        if (status == DriveRecordingService.RecordingStatus.Saved ||
            status == DriveRecordingService.RecordingStatus.NotEnoughData
        ) {
            selectedExample = null
            preview = false
            stopTrack()
            if (::story.isInitialized && story.text.toString() != trip.story) story.setText(trip.story)
        }
        updateDrive()
        if(previousStatus==DriveRecordingService.RecordingStatus.Recording && status==DriveRecordingService.RecordingStatus.Processing) selectPage(REVIEW)
        if((previousStatus==DriveRecordingService.RecordingStatus.Recording || previousStatus==DriveRecordingService.RecordingStatus.Processing) && (status==DriveRecordingService.RecordingStatus.Saved || status==DriveRecordingService.RecordingStatus.NotEnoughData)) selectPage(REVIEW)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trip = TripStore(this)
        track = BackingTrack(this) { status ->
            trackStatus.setText(status)
            updateControls()
        }
        songPlayer = SongPlayer(this) { status ->
            trackStatus.text = status
            updateControls()
        }
        songs = AiSongs(this) {
            if (songs.file != null && !track.active) usingSong = true
            updateControls()
        }
        usingSong = songs.file != null
        selectedExample = savedInstanceState?.getInt("example")?.takeIf { it > 0 }

        val compact = resources.configuration.screenWidthDp < 1100
        val content = column().apply {
            setPadding(dp(if (compact) 24 else 56), dp(20), dp(if (compact) 24 else 56), dp(32))
            isFocusableInTouchMode = true
        }
        val top = LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            pageTitle=heading(R.string.screen_player,36f)
            addView(pageTitle,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            environmentStatus=status(R.string.parked_status).apply {
                setOnClickListener {selectPage(DRIVE)}
                contentDescription="주행 상태 · 홈에서 기록 관리"
                setPadding(dp(20),dp(12),dp(20),dp(12))
                background=shape(SURFACE,24)
            }
            addView(environmentStatus)
        }
        content.addView(top)
        pageTitle.visibility=View.GONE
        top.gravity=Gravity.END
        val storyPanel=buildStoryPanel()
        val musicPanel=buildMusicPanel(compact)
        val soundPanel=buildSoundPanel()
        val homePanel = buildHomePanel(compact)
        pages.addAll(listOf(homePanel,soundPanel,storyPanel,musicPanel))
        val panels=FrameLayout(this)
        pages.forEach { panels.addView(it,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)) }
        content.addSpaced(panels,24)
        pageScroll=ScrollView(this).apply { isFillViewport=true; addView(content) }
        val navigationButtons=listOf(
            action(R.string.nav_home,ButtonStyle.Navigation,R.drawable.ic_car) { selectPage(DRIVE) },
            action(R.string.nav_sound,ButtonStyle.Navigation,R.drawable.ic_mixer) { selectPage(REVIEW) },
            action(R.string.nav_story,ButtonStyle.Navigation,R.drawable.ic_track_mic) { selectPage(STORY) },
            action(R.string.nav_player,ButtonStyle.Navigation,R.drawable.ic_music) { selectPage(PLAYER) },
        )
        tabs.addAll(navigationButtons)
        val root=LinearLayout(this).apply {
            orientation=if(compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            background=AuroraDrawable()
        }
        if(compact) {
            root.addView(buttonRow(navigationButtons).apply { setPadding(dp(24),dp(12),dp(24),0) })
            root.addView(pageScroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        } else {
            val rail=column().apply {
                setPadding(dp(20),dp(32),dp(20),dp(24)); background=shape(0xDBFFFFFF.toInt(),0)
                gravity=Gravity.CENTER_HORIZONTAL
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_chagok_mark); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },LinearLayout.LayoutParams(dp(128),dp(76)))
                addSpaced(heading(R.string.app_name,27f).apply { gravity=Gravity.CENTER },12)
                navigationButtons.forEachIndexed { i,button ->
                    button.gravity=Gravity.CENTER
                    button.background=RippleDrawable(ColorStateList.valueOf(0x226232E6),StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_selected),shape(CockpitTheme.PRIMARY,24))
                        addState(intArrayOf(android.R.attr.state_focused),shape(CockpitTheme.PRIMARY,24))
                        addState(intArrayOf(),shape(Color.TRANSPARENT,24))
                    },shape(Color.WHITE,24))
                    val icon=button.compoundDrawablesRelative[0]
                    button.setCompoundDrawablesRelative(null,icon,null,null); button.compoundDrawablePadding=dp(12)
                    addSpaced(button,if(i==0) 40 else 16,(104*resources.configuration.fontScale.coerceAtLeast(1f)).toInt())
                }
                addView(View(this@MainActivity),LinearLayout.LayoutParams(1,0,1f))
                addSpaced(copy("여행을 담다.\n음악이 되다.",18f).apply { gravity=Gravity.CENTER },20)
            }
            root.addView(rail,LinearLayout.LayoutParams(dp(208),ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(pageScroll,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f))
        }
        setContentView(root)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if(currentPage!=0) selectPage(DRIVE) else finish()
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
        addView(copy("이야기 노트", 44f, INK).apply { typeface = resources.getFont(R.font.gugi_regular) })
        addSpaced(copy("한마디를 남기면, 그 이야기가 노래의 가사가 돼요.", 25f), 12)
        val journal = column().apply {
            setPadding(dp(32),dp(32),dp(32),dp(32)); background=GlassDrawable(dp(28).toFloat())
            story=EditText(this@MainActivity).apply {
                id=View.generateViewId(); setText(trip.story); setHint(R.string.story_hint)
                setTextColor(INK); setHintTextColor(MUTED); textSize=28f; typeface=suit; fontVariationSettings="'wght' 500"
                gravity=Gravity.TOP or Gravity.START
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                filters=arrayOf(android.text.InputFilter.LengthFilter(2000))
                minLines=3; maxLines=6; minHeight=dp(220)
                setPadding(dp(28),dp(24),dp(28),dp(24))
                background=StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),shape(CockpitTheme.SELECTED,20))
                    addState(intArrayOf(),shape(CockpitTheme.SOFT,20))
                }
            }
            addView(copy("가사에 담을 이야기",26f,INK).apply { labelFor=story.id; isAccessibilityHeading=true })
            addSpaced(story,16)
            addSpaced(copy("자동 저장 · 최대 2,000자 · 말로 추가하거나 직접 고칠 수 있어요",20f),12)
            weatherButton=action(R.string.weather_unknown,ButtonStyle.Navigation,R.drawable.ic_track_weather) { chooseWeather() }
            addSpaced(weatherButton,24)
        }
        val voice=column().apply {
            gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(32),dp(32),dp(32),dp(32))
            background=shape(0xCFFFFFFF.toInt(),28)
            addView(copy("말로 남겨도 좋아요",30f,INK))
            speechButton=action(R.string.speech_start,ButtonStyle.Solid,R.drawable.ic_track_mic) { toggleSpeech() }
            makeOrb(speechButton,64)
            addView(speechButton,LinearLayout.LayoutParams(dp(216),dp(216)).apply { topMargin=dp(28) })
            speechStatus=status(R.string.speech_idle).apply { gravity=Gravity.CENTER; maxLines=3 }
            addSpaced(speechStatus,24)
            addSpaced(copy("버튼을 누르고 한두 문장씩.\n인식된 말은 왼쪽 노트에 쌓여요.",23f,INK).apply { gravity=Gravity.CENTER },20)

        }
        addSpaced(split(journal,voice,1.35f,0.65f),28)
        generationGenreHint=copy("",25f,INK)
        val selectedGenre=LinearLayout(this@MainActivity).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(generationGenreHint,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            addView(action(R.string.app_name,ButtonStyle.Navigation) {selectPage(REVIEW)}.apply {text="장르 변경"},LinearLayout.LayoutParams(dp(200),dp(72)))
        }
        addSpaced(selectedGenre,20)
        generateButton=action(R.string.song_generate,ButtonStyle.Solid,R.drawable.ic_music) { confirmSong() }.apply {
            textSize=32f; minHeight=dp(112); setPadding(dp(32),dp(24),dp(32),dp(24))
            compoundDrawablesRelative[0]?.setBounds(0,0,dp(44),dp(44)); compoundDrawablePadding=dp(20)
        }
        songStatus=status(R.string.song_idle)
        val next=LinearLayout(this@MainActivity).apply {
            gravity=Gravity.CENTER_VERTICAL
            if(resources.configuration.fontScale>1.4f || resources.configuration.screenWidthDp<1100) {
                orientation=LinearLayout.VERTICAL; addSpaced(songStatus,0); addSpaced(generateButton,16)
            } else {
                addView(songStatus,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                addView(generateButton,LinearLayout.LayoutParams(dp(440),ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart=dp(24) })
            }
        }
        addSpaced(next,28)
        addSpaced(action(R.string.app_name,ButtonStyle.Navigation) {
            showSheet("대화 기록",trip.conversationStatus) { body,_ ->
                body.addSpaced(copy(trip.transcript.ifBlank { "아직 인식된 대화가 없어요." }),20)
                if(trip.suggestedStory.isNotBlank()) body.addSpaced(action(R.string.app_name,ButtonStyle.Solid) {
                    story.setText(trip.suggestedStory); openSheet?.dismiss()
                }.apply {text="이번 주행 요약 가져오기"},20)
                body.addSpaced(action(R.string.app_name,ButtonStyle.Plain) {
                    if(recording!=DriveRecordingService.RecordingStatus.Recording && recording!=DriveRecordingService.RecordingStatus.Processing) {
                        java.io.File(filesDir,"conversations").deleteRecursively();trip.transcript="";trip.suggestedStory="";trip.conversationStatus="대화 기록 삭제됨";openSheet?.dismiss()
                    }
                }.apply {text="대화 원음·전사 삭제"},20)
            }
        }.apply {text="주행 대화 기록 보기"},16)
    }

    private fun split(left: View, right: View, lw: Float=1f, rw: Float=1f): LinearLayout = LinearLayout(this).apply {
        val compact=resources.configuration.screenWidthDp<1100 || resources.configuration.fontScale>1.4f
        orientation=if(compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if(compact) { addSpaced(left,0); addSpaced(right,24) }
        else {
            addView(left,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,lw))
            addView(right,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,rw).apply { marginStart=dp(28) })
        }
    }

    private fun buildHomePanel(compact: Boolean) = column().apply {
        addView(copy("드라이브",52f,INK).apply {typeface=resources.getFont(R.font.gugi_regular)})
        addSpaced(copy("달린 길은 비트로, 나눈 말은 노래로.",30f,INK),16)
        val hero=column().apply {
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding(dp(40),dp(24),dp(40),dp(32))
            background=GlassDrawable(dp(32).toFloat())
            addView(PortalArtView(this@MainActivity,0),LinearLayout.LayoutParams(dp(480),dp(300)))
            driveStatus=copy("",28f,INK).apply {gravity=Gravity.CENTER;maxLines=3}
            addSpaced(driveStatus,12)
            driveButton=action(R.string.drive_start,ButtonStyle.Drive,R.drawable.ic_car) {
                if(trip.hasResult && recording!=DriveRecordingService.RecordingStatus.Recording && recording!=DriveRecordingService.RecordingStatus.Processing) selectPage(REVIEW) else toggleRecording()
            }.apply {textSize=38f}
            addView(driveButton,LinearLayout.LayoutParams(dp(480),dp(120)).apply {topMargin=dp(28)})
            newTripButton=action(R.string.app_name,ButtonStyle.Navigation,R.drawable.ic_car) {toggleRecording()}.apply {text="새 주행 시작"}
            addView(newTripButton,LinearLayout.LayoutParams(dp(320),ViewGroup.LayoutParams.WRAP_CONTENT).apply {topMargin=dp(16)})
        }
        addSpaced(hero,32)
        homeSummary=copy("",23f)
        addSpaced(homeSummary,20)
        // These entry points are intentionally not mounted: the primary action owns the journey.
        homeStory=action(R.string.app_name,ButtonStyle.Plain) {selectPage(STORY)}
        homeBeat=action(R.string.app_name,ButtonStyle.Plain) {selectPage(REVIEW)}
        addSpaced(copy("주행 → 리포트 → 이야기 → 내 노래",25f,INK),20)
        addView(action(R.string.app_name,ButtonStyle.Plain) {showGuide()}.apply {text="사용법 보기"},LinearLayout.LayoutParams(dp(240),ViewGroup.LayoutParams.WRAP_CONTENT).apply {topMargin=dp(24)})
    }

    private fun showGuide() = showSheet("길에서 시작해, 한 곡으로", "출발 전에 시작하고, 도착해서 음악을 완성해요.") { body,_ ->
        listOf("01  주행 기록" to "출발 전 주행 시작을 눌러요. 속도와 와이퍼·창문·문·깜빡이의 변화를 모아요. 최대 30분 뒤 자동 저장돼요.",
            "02  비트 듣기" to "주차 후 기록을 마치면 속도가 템포로, 차량 신호가 악기로 바뀌어요. 비트는 기기에서 바로 만들어요.",
            "03  이야기와 음악 만들기" to "기억할 말을 입력하거나 짧게 말해 주세요. 이름과 장르를 확인한 뒤 음악 만들기를 누르면 AI가 가사와 보컬곡을 만들어요.",
            "04  다시 듣기" to "완성곡은 저장해서 들어요. 제목도 바꿀 수 있어요. 공개 기능은 서버 연결 후 사용할 수 있어요.").forEach { (title,detail) ->
            body.addSpaced(copy(title,28f,INK),28); body.addSpaced(copy(detail,24f),12)
        }
        body.addSpaced(copy("대화 기록은 출발 전에 선택해요. Pleos가 전사하고 Gemini가 요약합니다. 노래에는 AI 보컬이 들어가며 실제 목소리는 아직 삽입하지 않아요. 가사 없이 비트만 들어도 좋아요.",22f),28)
    }

    private fun selectPage(index: Int) {
        currentPage = index.coerceIn(0, 3)
        pages.forEachIndexed { i, page -> page.visibility = if (i == currentPage) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, tab ->
            tab.isSelected = i == currentPage
            tab.stateDescription = if (i == currentPage) "선택됨" else null
        }
        pageTitle.visibility=View.GONE
        pageTitle.setText(listOf(R.string.nav_home,R.string.nav_story,R.string.sound_heading,R.string.screen_player)[currentPage])
        if(index==REVIEW && android.animation.ValueAnimator.areAnimatorsEnabled()) instrumentTiles.forEachIndexed {i,tile ->
            tile.animate().cancel();tile.alpha=0.35f;tile.translationY=dp(12).toFloat()
            tile.animate().alpha(1f).translationY(0f).setStartDelay(i*90L).setDuration(260).start()
        }
        if (::pageScroll.isInitialized) pageScroll.post { pageScroll.scrollTo(0, 0) }
        if (::story.isInitialized) {
            story.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(story.windowToken, 0)
        }
    }

    private fun buildMusicPanel(compact: Boolean) = column().apply {
        addView(LinearLayout(this@MainActivity).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(copy("사운드트랙",44f,INK).apply {typeface=resources.getFont(R.font.gugi_regular)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            addView(action(R.string.app_name,ButtonStyle.Plain,R.drawable.ic_music) {showLibrary()}.apply {text="내 음악 · 시연 3곡"},LinearLayout.LayoutParams(dp(340),dp(76)))
        })
        storyPreview=copy("")
        rhythmLabel=copy("")
        tempoSource=copy("")
        val deck=column().apply {
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding(dp(32),dp(20),dp(32),dp(32))
            background=GlassDrawable(dp(32).toFloat())
            audioPulse=AudioPulseView(this@MainActivity,{track.active || songPlayer.active},{if(usingSong) songPlayer.visualLevels() else track.visualLevels()}).apply {
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addSpaced(audioPulse,0,if(compact) 340 else 480)
            songHeading=heading(R.string.track_heading,48f).apply { gravity=Gravity.CENTER; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END }
            addSpaced(songHeading,0)
            genreLabel=copy("",23f,INK); tempo=copy("",23f)
            addSpaced(LinearLayout(this@MainActivity).apply {
                gravity=Gravity.CENTER
                addView(genreLabel);addView(tempo,LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply {marginStart=dp(24)})
            },12)
            playProgress=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=1000;progressTintList=ColorStateList.valueOf(CockpitTheme.PRIMARY)
                progressBackgroundTintList=ColorStateList.valueOf(CockpitTheme.SOFT);contentDescription="재생 진행률"
            }
            addSpaced(playProgress,24,5)
            playTime=status(R.string.track_hint).apply { gravity=Gravity.CENTER }
            addSpaced(playTime,12)
            playButton=action(R.string.track_play,ButtonStyle.Solid,R.drawable.ic_track_play) {
                if(track.active || songPlayer.active) stopTrack() else if(!restricted) {
                    if(speech!=null) finishSpeech(R.string.speech_stopped)
                    val file=songs.file
                    if(usingSong && file!=null) songPlayer.play(file) else track.play(currentArrangement())
                }
                updateControls()
            };makeOrb(playButton,48)
            repeatButton=action(R.string.loop_mode,ButtonStyle.Navigation,R.drawable.ic_repeat) {
                track.looping=!track.looping;songPlayer.looping=track.looping;updateControls()
            }.apply {
                text="반복";gravity=Gravity.CENTER
                val icon=compoundDrawablesRelative[0];setCompoundDrawablesRelative(null,icon,null,null)
            }
            localButton=action(R.string.song_local,ButtonStyle.Navigation,R.drawable.ic_mixer) {
                stopTrack();usingSong=!usingSong && songs.file!=null;updateControls()
            }.apply {
                gravity=Gravity.CENTER; textSize=21f
                val icon=compoundDrawablesRelative[0];setCompoundDrawablesRelative(null,icon,null,null)
            }
            addSpaced(LinearLayout(this@MainActivity).apply {
                gravity=Gravity.CENTER
                addView(repeatButton,LinearLayout.LayoutParams(dp(156),dp(112)))
                addView(playButton,LinearLayout.LayoutParams(dp(144),dp(144)).apply { marginStart=dp(24);marginEnd=dp(24) })
                addView(action(R.string.app_name,ButtonStyle.Plain) {shareDraft()}.apply {text="저장·공유"},LinearLayout.LayoutParams(dp(180),dp(112)))
            },16)
            trackStatus=status(R.string.track_hint).apply {gravity=Gravity.CENTER};addSpaced(trackStatus,8)
        }
        val words=column().apply {
            setPadding(dp(40),dp(36),dp(40),dp(36))
            background=GlassDrawable(dp(32).toFloat())
            lyricsCaption=copy("가사",32f,INK).apply { isAccessibilityHeading=true }
            lyricsButton=action(R.string.song_lyrics,ButtonStyle.Navigation) {expandedLyrics=!expandedLyrics;updateInlineLyrics()}.apply {textSize=22f}
            addView(LinearLayout(this@MainActivity).apply {
                gravity=Gravity.CENTER_VERTICAL
                addView(lyricsCaption,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                addView(lyricsButton,LinearLayout.LayoutParams(dp(200),dp(76)))
            })
            inlineLyrics=copy("",32f,INK).apply {setTextIsSelectable(true);setLineSpacing(dp(18).toFloat(),1.18f)}
            addSpaced(ScrollView(this@MainActivity).apply { addView(inlineLyrics) },24,if(compact) 320 else 560)
            renameButton=action(R.string.app_name,ButtonStyle.Navigation) {renameSong()}.apply {text="제목 수정"}
            addView(renameButton,LinearLayout.LayoutParams(dp(240),ViewGroup.LayoutParams.WRAP_CONTENT).apply {topMargin=dp(24)})
        }
        addSpaced(split(deck,words,1f,1.12f),32)
    }

    private fun showLibrary() {
        stopTrack()
        choiceSheet("음악 보관함", "시연 3곡은 실제 에뮬레이터 주행 기록과 창작 이야기로 만든 AI 음원입니다. 선택·재생에 생성 API를 쓰지 않아요.",songs.library().map {item ->
            SheetOption(item.optString("title"),"${item.optString("genre")} · ${item.optInt("bpm")} BPM · ${if(item.optBoolean("demo")) "데모" else "내 곡"}\n${item.optString("story").take(52)}") {
                usingSong=true;songs.select(item)
            }
        })
    }

    private fun songTitle():String {
        val id=songs.song?.optString("id").orEmpty()
        return localPrefs.getString("title_$id",null) ?: songs.song?.optString("title")?.takeIf { it.isNotBlank() } ?: "길 위의 우리"
    }

    private fun textField(value:String,hintText:String,limit:Int)=EditText(this).apply {
        id=View.generateViewId(); setText(value); hint=hintText; textSize=27f; typeface=suit; fontVariationSettings="'wght' 500"
        setTextColor(INK); setHintTextColor(MUTED); isSingleLine=true
        filters=arrayOf(android.text.InputFilter.LengthFilter(limit)); minHeight=dp(88)
        setPadding(dp(24),dp(16),dp(24),dp(16)); background=shape(CockpitTheme.SOFT,20)
    }

    private fun renameSong() {
        if(!usingSong || songs.song==null) return
        val input=textField(songTitle(),"노래 제목",80)
        showSheet("노래에 이름을 붙여요", "제목을 바꿔도 음악을 다시 만들거나 비용을 쓰지 않아요.") { body,dialog ->
            body.addSpaced(copy("제목",24f,INK).apply { labelFor=input.id },24); body.addSpaced(input,12)
            body.addSpaced(action(R.string.app_name,ButtonStyle.Solid) {
                val title=input.text.toString().trim()
                if(title.isEmpty()) input.error="제목을 입력해 주세요."
                else { localPrefs.edit().putString("title_${songs.song?.optString("id")}",title).apply(); dialog.dismiss(); updateControls() }
            }.apply { text="제목 저장" },24)
        }
    }

    private var exporting=false
    private fun shareDraft() {
        if(restricted || exporting) return
        showSheet("저장과 공유", "한 번 만든 음악은 다시 생성하지 않아요. 파일로 저장하거나 연결된 앱으로 보낼 수 있어요.") { body,dialog ->
            body.addSpaced(action(R.string.app_name,ButtonStyle.Solid) {dialog.dismiss();exportMusic(false)}.apply {text="다운로드 폴더에 저장"},24)
            body.addSpaced(action(R.string.app_name,ButtonStyle.Plain) {dialog.dismiss();exportMusic(true)}.apply {text="다른 앱으로 공유"},16)
            body.addSpaced(copy("Bluetooth 전송은 기기가 파일 공유를 지원하고 공유 목록에 나타날 때 사용할 수 있어요. 온라인 커뮤니티 공개는 서버 연결 후 제공됩니다.",22f),24)
            if(usingSong) body.addSpaced(copy("AI 보컬 음원입니다. 가사에 이름이나 개인적인 이야기가 있는지 확인하고 공유해 주세요. 실제 탑승자의 원음은 포함되지 않아요.",22f),16)
        }
    }

    private fun exportMusic(share:Boolean) {
        if(exporting || restricted) return
        exporting=true
        val source=if(usingSong) songs.file else null
        val arrangement=currentArrangement()
        val title=if(source!=null) songTitle() else "내 차의 비트"
        android.widget.Toast.makeText(this,"음악 파일을 준비하고 있어요",android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val extension=if(source!=null) "mp3" else "wav"
                val folder=java.io.File(cacheDir,"shared-music").apply {mkdirs()}
                val file=java.io.File(folder,java.util.UUID.randomUUID().toString().replace("-","")+".$extension")
                if(source!=null) source.copyTo(file,true) else {
                    val pcm=BackingTrack.renderBackingTrack(arrangement)
                    java.io.DataOutputStream(file.outputStream().buffered()).use {out ->
                        fun i(value:Int) {out.writeInt(Integer.reverseBytes(value))}
                        fun h(value:Int) {out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())}
                        out.writeBytes("RIFF");i(36+pcm.size*2);out.writeBytes("WAVEfmt ");i(16);h(1);h(1);i(24000);i(48000);h(2);h(16)
                        out.writeBytes("data");i(pcm.size*2);pcm.forEach {h(it.toInt())}
                    }
                }
                val mime=if(extension=="mp3") "audio/mpeg" else "audio/wav"
                if(share) {
                    val uri=android.net.Uri.parse("content://$packageName.music/${file.name}")
                    main.post {
                        exporting=false
                        if(!restricted && !isFinishing) try {
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type=mime;putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_TITLE,title)
                                clipData=android.content.ClipData.newRawUri(title,uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },"음악 공유"))
                        } catch(_:android.content.ActivityNotFoundException) {android.widget.Toast.makeText(this,"이 기기에 파일을 받을 앱이 없어요. 다운로드 저장을 사용해 주세요.",android.widget.Toast.LENGTH_LONG).show()}
                    }
                } else {
                    val values=android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,title.replace(Regex("[^가-힣a-zA-Z0-9 _-]"),"").take(70)+".$extension")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE,mime)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/Chagok")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
                    }
                    val uri=contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("No downloads")
                    try {
                        contentResolver.openOutputStream(uri)!!.use {output ->file.inputStream().use {it.copyTo(output)}}
                        contentResolver.update(uri,android.content.ContentValues().apply {put(android.provider.MediaStore.MediaColumns.IS_PENDING,0)},null,null)
                    } catch(e:Exception) {contentResolver.delete(uri,null,null);throw e}
                    file.delete()
                    main.post {exporting=false;android.widget.Toast.makeText(this,"Download/Chagok에 저장했어요",android.widget.Toast.LENGTH_LONG).show()}
                }
            } catch(_:Exception) {main.post {exporting=false;android.widget.Toast.makeText(this,"음악 파일을 저장하지 못했어요. 저장 공간을 확인해 주세요.",android.widget.Toast.LENGTH_LONG).show()}}
        }.start()
    }

    private fun updateInlineLyrics() {
        if(!::inlineLyrics.isInitialized) return
        val song=songs.song
        val caption:String
        val words:String
        if(!usingSong || song==null) {
            caption="INSTRUMENTAL"
            words="차량 신호로 만든 비트예요.\n이야기를 더하면 가사와 보컬이 있는 노래를 만들 수 있어요."
        } else {
            val text=song.optString("lyrics").lines().filterNot { it.trim().startsWith("[[") && it.trim().endsWith("]]") }.joinToString("\n") { it.removePrefix("[:]").trim() }.trim()
            val cues=song.optJSONArray("timedLyrics")
            if(expandedLyrics || cues==null || cues.length()==0) {
                caption="가사"
                words=text.ifBlank { "이 곡에는 제공된 가사가 없어요." }
            } else {
                val position=(songPlayer.progress * songPlayer.durationSeconds * 1000).toLong()
                val cue=(0 until cues.length()).lastOrNull { cues.getJSONObject(it).optLong("startMs") <= position }
                caption="지금 흐르는 가사"
                words=if(cue==null) "전주 · 잠시 후 노래가 시작돼요" else (cue..minOf(cue+1,cues.length()-1)).joinToString("\n") { cues.getJSONObject(it).optString("text") }
            }
        }
        lyricsButton.visibility=if(usingSong && (song?.optJSONArray("timedLyrics")?.length() ?: 0)>0) View.VISIBLE else View.GONE
        if(lyricsCaption.text.toString()!=caption) lyricsCaption.text=caption
        if(inlineLyrics.text.toString()!=words) inlineLyrics.text=words
        val buttonText=if(expandedLyrics) "가사 따라보기" else "전체 가사"
        if(lyricsButton.text.toString()!=buttonText) lyricsButton.text=buttonText
    }

    private fun buildSoundPanel() = column().apply {
        addView(copy("주행 리포트",44f,INK).apply {typeface=resources.getFont(R.font.gugi_regular)})
        addSpaced(copy("장르를 고르면 드럼·베이스 위에 내 차량 신호가 악기로 더해져요.",25f),12)
        drivingRecipe=copy("",27f,INK)
        addSpaced(drivingRecipe,28)
        val instruments=LinearLayout(this@MainActivity).apply {
            orientation=if(resources.configuration.fontScale>1.4f || resources.configuration.screenWidthDp<1100) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
        }
        for(i in 0..5) {
            val button=action(R.string.instrument_demo,ButtonStyle.Navigation) {auditionInstrument(i)}.apply {
                gravity=Gravity.CENTER;minHeight=dp(220);textSize=28f
                val art=InstrumentArt(i).apply {setBounds(0,0,dp(160),dp(120))}
                setCompoundDrawablesRelative(null,art,null,null);compoundDrawablePadding=dp(28)
                background=RippleDrawable(ColorStateList.valueOf(0x226232E6),shape(0xAFFFFFFF.toInt(),32),shape(Color.WHITE,32))
            }
            instrumentTiles.add(button)
            if(instruments.orientation==LinearLayout.VERTICAL) instruments.addSpaced(button,16)
            else instruments.addView(button,LinearLayout.LayoutParams(0,dp(240),1f).apply { if(i>0) marginStart=dp(16) })
        }
        addSpaced(instruments,32)
        signalLabel=status(R.string.instrument_hint);addSpaced(signalLabel,16)
        val genreGroup=column().apply {setPadding(dp(24),dp(20),dp(24),dp(20));background=GlassDrawable(dp(24).toFloat())}
        genreGroup.addView(copy("장르 · 하나 선택",27f,INK))
        (listOf<Genre?>(null)+Genre.entries).forEach {genre ->
            generationGenres.add(action(R.string.app_name,ButtonStyle.Plain) {
                if(!restricted) {stopTrack();trip.genre=genre;updateControls()}
            }.apply {text=genre?.let {getString(it.label)} ?: "자동 추천";textSize=25f})
        }
        genreGroup.addSpaced(buttonRow(generationGenres),16)
        addSpaced(genreGroup,24)
        genreButton=action(R.string.choose_genre,ButtonStyle.Plain,R.drawable.ic_music) {chooseGenre()}
        mixButton=action(R.string.rap_mode,ButtonStyle.Plain,R.drawable.ic_mixer) {chooseMix()}
        val listen=action(R.string.app_name,ButtonStyle.Solid,R.drawable.ic_track_play) {if(track.active) stopTrack() else previewBeat()}.apply {text="내 차의 비트 · 재생 / 정지"}
        val continueStory=action(R.string.app_name,ButtonStyle.Drive,R.drawable.ic_track_mic) {trip.genre=trip.genre ?: currentArrangement().genre;trip.vocals=true;stopTrack();selectPage(STORY);updateControls()}.apply {text="이야기를 더해 노래 만들기"}
        val instrumental=action(R.string.app_name,ButtonStyle.Plain,R.drawable.ic_music) {trip.genre=trip.genre ?: currentArrangement().genre;trip.vocals=false;usingSong=false;selectPage(PLAYER);updateControls()}.apply {text="가사 없이 비트만 듣기"}
        addSpaced(buttonRow(listOf(mixButton,listen)),20)
        addSpaced(buttonRow(listOf(instrumental,continueStory)),16)
        val remix=action(R.string.remix,ButtonStyle.Navigation,R.drawable.ic_shuffle) {stopTrack();variation=(variation+1)%16;updateControls()}
        val examples=action(R.string.track_examples,ButtonStyle.Navigation,R.drawable.ic_car) {
            choiceSheet("속도 미리듣기","선택한 속도로 비트를 바로 들어볼 수 있어요.",listOf(
                SheetOption("느린 길 · 20 km/h","여유로운 템포",selectedExample==SLOW_EXAMPLE) {selectedExample=SLOW_EXAMPLE;previewBeat()},
                SheetOption("빠른 길 · 72 km/h","경쾌한 템포",selectedExample==FAST_EXAMPLE) {selectedExample=FAST_EXAMPLE;previewBeat()},
                SheetOption("실제 주행 기록 사용","내 기록으로 재생",selectedExample==null) {selectedExample=null;previewBeat()},
            ))
        }
        // Keep advanced example controls off the main trip journey.
        remix.visibility=View.GONE;examples.visibility=View.GONE
    }

    override fun onStart() {
        super.onStart()
        main.post(playbackTick)
        if(songs.file==null || songs.pending) songs.refresh()
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

    override fun onDestroy() {
        songs.close()
        songPlayer.stop()
        super.onDestroy()
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
        if (checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED && !restricted) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) trip.recordConversation=false
            startRecording()
        } else {
            driveStatus.setText(R.string.speed_denied)
        }
    }

    private fun updateRestrictions(value: Boolean) {
        restricted = value
        if (value) {
            openSheet?.dismiss()
            if (speech != null) finishSpeech(R.string.speech_stopped)
            stopTrack()
        }
        updateControls()
    }

    private fun toggleRecording() {
        if (restricted) return
        if (recording == DriveRecordingService.RecordingStatus.Recording) {
            startService(Intent(this, DriveRecordingService::class.java).setAction(DriveRecordingService.STOP))
        } else if (recording != DriveRecordingService.RecordingStatus.Processing) {
            choiceSheet("주행 기록", "출발 전에 시작해 주세요. 도착 후 P에서 기록을 끝내면 비트가 완성돼요.", listOf(
                SheetOption("대화와 차량 데이터", "동승자에게 녹음을 알리고 시작해 주세요. 최대 30분 동안 기기에 녹음하며, 종료 후 인식한 텍스트를 Gemini에 보내 요약합니다.") { trip.recordConversation=true; requestTripPermissions() },
                SheetOption("차량 데이터만", "마이크를 사용하지 않아요. 가사 없이 비트만 즐기거나, 나중에 이야기를 직접 추가할 수 있어요.") { trip.recordConversation=false; requestTripPermissions() }
            ))
        }
    }

    private fun requestTripPermissions() {
        if (speech != null) finishSpeech(R.string.speech_stopped)
        stopTrack()
        val wanted=mutableListOf(Car.PERMISSION_SPEED)
        if(trip.recordConversation) wanted.add(android.Manifest.permission.RECORD_AUDIO)
        val missing=wanted.filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) requestPermissions(missing.toTypedArray(),SPEED_PERMISSION_REQUEST) else startRecording()
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
            DriveRecordingService.RecordingStatus.Recording -> "기록 중 · ${((System.currentTimeMillis()-trip.startedAt)/60000).coerceAtLeast(0)}분 · ${if(trip.recordConversation) "대화와 차량 데이터" else "차량 데이터"}"
            DriveRecordingService.RecordingStatus.Processing -> "대화 정리 중 · 잠시만 기다려 주세요"
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

    private data class SheetOption(val title: String, val detail: String, val selected: Boolean = false, val choose: () -> Unit = {})

    private fun copy(text: String, size: Float = 24f, color: Int = MUTED) = label(R.string.app_name, size, color).apply {
        this.text = text
    }

    /** All sheets share touch-sized controls, a fixed header and a scrollable body. */
    private fun showSheet(
        title: String,
        description: String,
        primary: String? = null,
        onPrimary: (() -> Unit)? = null,
        content: (LinearLayout, Dialog) -> Unit,
    ) {
        if (restricted) return
        openSheet?.dismiss()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = column().apply {
            setPadding(dp(32), dp(28), dp(32), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, 0xFFF5F4FC.toInt(), 0xFFEFF9F7.toInt())).apply { cornerRadius = dp(28).toFloat() }
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(heading(R.string.app_name, 34f).apply {
                text = title; typeface = suit; fontVariationSettings = "'wght' 700"
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(action(R.string.app_name, ButtonStyle.Plain) { dialog.dismiss() }.apply {
                text = "닫기"; contentDescription = "$title 닫기"
            }, LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16) })
        }
        root.addView(header)
        val body = column().apply { setPadding(0, 0, dp(8), dp(12)) }
        body.addSpaced(copy(description, 23f), 16)
        content(body, dialog)
        val bounds = windowManager.currentWindowMetrics.bounds
        val sheetWidth = minOf(dp(1120), bounds.width() - dp(64))
        body.measure(View.MeasureSpec.makeMeasureSpec(sheetWidth-dp(64),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        header.measure(View.MeasureSpec.makeMeasureSpec(sheetWidth-dp(64),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val desiredHeight=(body.measuredHeight+header.measuredHeight+dp(if(primary!=null) 192 else 80)).coerceAtLeast(dp(360))
        root.addView(ScrollView(this).apply {
            isFillViewport = false; addView(body)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        if (primary != null) root.addSpaced(action(R.string.app_name, ButtonStyle.Solid) {
            dialog.dismiss()
            if (!restricted) onPrimary?.invoke()
        }.apply { text = primary }, 20)
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x6617253D)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            clipChildren = false
            addView(root, FrameLayout.LayoutParams(sheetWidth,
                minOf(desiredHeight, dp(1000), (bounds.height() * 0.86f).toInt()), Gravity.CENTER))
        }
        overlay.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                root.layoutParams = root.layoutParams.apply { height = minOf(desiredHeight, dp(1000), ((bottom - top) * 0.86f).toInt()) }
            }
        }
        root.isFocusableInTouchMode = true
        root.elevation = dp(36).toFloat()
        root.outlineAmbientShadowColor = 0xAA142846.toInt()
        root.outlineSpotShadowColor = 0xCC142846.toInt()
        root.clipToOutline = true
        dialog.setContentView(overlay)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { if (openSheet === dialog) openSheet = null }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        openSheet = dialog
        dialog.show()
        root.requestFocus()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun optionCard(option: SheetOption, click: () -> Unit) = action(R.string.app_name, ButtonStyle.Plain, R.drawable.ic_music, click).apply {
        text = android.text.SpannableString((if (option.selected) "✓  " else "") + option.title + "\n" + option.detail).apply {
            setSpan(android.text.style.RelativeSizeSpan(0.82f), indexOf("\n") + 1, length, 0)
        }
        setCompoundDrawablesRelative(null, null, null, null)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minHeight = dp(112)
        setPadding(dp(24), dp(24), dp(24), dp(24))
        isSelected = option.selected
        stateDescription = if (option.selected) "선택됨" else "선택 안 됨"
    }

    private fun chooseWeather() = choiceSheet("여행의 날씨", "날씨와 여행 이야기를 장르 추천에 함께 반영해요.",
        Weather.entries.map { option ->
            SheetOption(getString(option.label), when (option) {
                Weather.Unknown -> "날씨 없이 이야기로 추천"
                Weather.Sunny -> "햇살처럼 경쾌한 분위기"
                Weather.Rain -> "빗소리와 어울리는 차분함"
                Weather.Cloudy -> "여유롭고 부드러운 분위기"
                Weather.Snow -> "따뜻하고 포근한 분위기"
            }, trip.weather == option) { trip.weather = option }
        })

    private fun choiceSheet(title: String, description: String, items: List<SheetOption>) {
        showSheet(title, description) { body, dialog ->
            items.forEach { option ->
                body.addSpaced(optionCard(option) {
                    if (!restricted) { stopTrack(); option.choose(); updateControls() }
                    dialog.dismiss()
                }, 12)
            }
        }
    }

    private fun chooseGenre() = choiceSheet("장르 선택", "로컬 비트에 바로 적용해요. 다음 보컬곡을 만들 때도 이 장르를 사용해요.",
        listOf(SheetOption("자동 추천", "날씨와 이야기에서 분위기를 찾아요", trip.genre == null) { trip.genre = null }) +
            Genre.entries.map { genre -> SheetOption(getString(genre.label), when (genre) {
                Genre.Pop -> "밝은 멜로디와 탄탄한 드럼"
                Genre.HipHop -> "둥둥 베이스와 랩을 위한 그루브"
                Genre.House -> "일정한 킥과 댄서블한 리듬"
                Genre.CityPop -> "반짝이는 신스와 드라이브 감성"
                Genre.LoFi -> "차분한 비트와 부드러운 질감"
                Genre.Acoustic -> "따뜻하고 편안한 멜로디"
            }, trip.genre == genre) { trip.genre = genre } })

    private fun chooseMix() {
        showSheet("비트 꾸미기", "저음과 멜로디를 취향에 맞춰 조절해요. 선택 즉시 로컬 비트에 저장돼요.", "설정한 비트 듣기", { previewBeat() }) { body, _ ->
            body.addSpaced(copy("기본 악기 · 각각 들어보기",27f,INK),20)
            body.addSpaced(buttonRow(listOf(
                action(R.string.app_name,ButtonStyle.Navigation,R.drawable.ic_music) {auditionInstrument(6)}.apply {text="베이스 · 둥둥 저음"},
                action(R.string.app_name,ButtonStyle.Navigation,R.drawable.ic_mixer) {auditionInstrument(7)}.apply {text="드럼 · 킥과 스네어"}
            )),12)

            fun group(title: String, values: List<Pair<String, String>>, selected: () -> Int, change: (Int) -> Unit) {
                body.addSpaced(heading(R.string.app_name, 26f).apply { text = title; typeface = suit; fontVariationSettings = "'wght' 650" }, 28)
                val controls = mutableListOf<Button>()
                fun refresh() = controls.forEachIndexed { index, button ->
                    val option = values[index]
                    button.text = (if (selected() == index) "✓  " else "") + option.first + "\n" + option.second
                    button.isSelected = selected() == index
                    button.stateDescription = if (button.isSelected) "선택됨" else "선택 안 됨"
                }
                values.forEachIndexed { index, option ->
                    controls.add(optionCard(SheetOption(option.first, option.second)) {
                        if (!restricted) { stopTrack(); change(index); refresh(); updateControls() }
                    })
                }
                body.addSpaced(buttonRow(controls), 12)
                refresh()
            }
            group("베이스", listOf("둥둥" to "킥과 저음을 선명하게", "소프트" to "편안하고 부드럽게"), { if (trip.punchy) 0 else 1 }) { trip.punchy = it == 0 }
            group("멜로디", listOf("멜로디" to "신스 훅을 더해요", "랩 모드" to "비트에 더 집중해요"), { if (trip.rapMode) 1 else 0 }) { trip.rapMode = it == 1 }
            group("반복 재생", listOf("반복 켜기" to "곡이 끝나면 다시 재생", "한 번만" to "끝나면 자동 정지"), { if (track.looping) 0 else 1 }) {
                track.looping = it == 0; songPlayer.looping = it == 0
            }
        }
    }

    private fun auditionInstrument(index:Int) {
        if(restricted || recording==DriveRecordingService.RecordingStatus.Recording) return
        stopTrack();usingSong=false
        track.play(currentArrangement(),solo=index)
        signalLabel.text="${listOf("셰이커","벨","클랩","림샷","신스","퍼커션","베이스","드럼")[index]} 소리만 듣는 중 · 약 3초 후 자동 정지"
    }

    private fun showInstrumentDetails(index: Int) {
        val names = listOf("와이퍼", "창문", "문", "깜빡이")
        val instruments = listOf("셰이커", "벨과 신스", "클랩", "림샷")
        val descriptions = listOf("와이퍼를 움직이면 촘촘한 리듬이 더해져요.", "창문을 여닫으면 반짝이는 멜로디가 더해져요.", "문을 열고 닫은 기록이 손뼉 같은 박자가 돼요.", "방향지시등을 쓴 기록이 짧고 선명한 타격음이 돼요.")
        val a = currentArrangement()
        val counts = listOf(a.wipers, a.windows, a.doors, a.signals)
        showSheet("${names[index]} → ${instruments[index]}", descriptions[index], "${names[index]} 비트 미리듣기", {
            preview = true
            previewWipers = if (index == 0) 4 else 0
            previewWindows = if (index == 1) 4 else 0
            previewDoors = if (index == 2) 4 else 0
            previewSignals = if (index == 3) 4 else 0
            previewBeat()
        }) { body, _ ->
            body.addSpaced(copy("${if (preview) "가상 신호" else "주행 기록"}  ·  ${counts[index]}회", 36f, INK), 24)
            body.addSpaced(copy("미리듣기는 기본 비트에 이 악기를 더해 재생해요. 실제 차량을 조작하거나 새 AI 곡을 만들지 않아요."), 24)
            body.addSpaced(copy(trip.profile?.signalStatus?.takeIf { it.isNotBlank() } ?: "여행 이야기에서 주행을 기록하면 실제 차량 신호를 모을 수 있어요.", 22f), 24)
            body.addSpaced(action(R.string.app_name, ButtonStyle.Plain) {
                if (!restricted) { preview = false; updateControls(); openSheet?.dismiss() }
            }.apply { text = "실제 주행 기록으로 돌아가기" }, 24)
        }
    }

    private fun previewBeat() {
        if (restricted || recording == DriveRecordingService.RecordingStatus.Recording) return
        stopTrack()
        if (speech != null) finishSpeech(R.string.speech_stopped)
        usingSong = false
        track.play(currentArrangement())
        if(currentPage!=REVIEW) selectPage(PLAYER)
        updateControls()
    }

    private fun buttonRow(buttons: List<Button>) = LinearLayout(this).apply {
        orientation = if (resources.configuration.fontScale > 1.25f ||
            resources.configuration.screenWidthDp < 500) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            if (orientation == LinearLayout.VERTICAL) addSpaced(button, if (index == 0) 0 else 16)
            else addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (index > 0) marginStart = dp(16)
            })
        }
    }

    private fun currentArrangement(): Arrangement {
        val profile = trip.profile
        val bpm = selectedExample?.let { DriveProfile(it.toFloat(), DriveProfile.MIN_SAMPLES).tempo }
            ?: profile?.tempo ?: DriveProfile.DEFAULT_TEMPO
        return Arrangement.recommend(bpm, trip.weather, story.text.toString(), selectedExample == null && profile?.dynamic == true)
            .copy(temperatureChanges=trip.profile?.temperatureChanges ?: 0, recirculationChanges=trip.profile?.recirculationChanges ?: 0, genre = trip.genre ?: Arrangement.recommend(bpm, trip.weather, story.text.toString(), false).genre,
                wipers = if (preview) previewWipers else profile?.wipers ?: 0,
                windows = if (preview) previewWindows else profile?.windows ?: 0,
                rapMode = trip.rapMode, variation = variation,
                doors = if (preview) previewDoors else profile?.doors ?: 0,
                signals = if (preview) previewSignals else profile?.signals ?: 0, punchy = trip.punchy)
    }

    private fun updateControls() {
        val activeRecording = recording == DriveRecordingService.RecordingStatus.Recording
        tabs.getOrNull(REVIEW)?.isEnabled=!restricted && trip.hasResult && !activeRecording
        tabs.getOrNull(STORY)?.isEnabled=!restricted && trip.hasResult && !activeRecording && recording!=DriveRecordingService.RecordingStatus.Processing
        driveButton.text=when {
            recording==DriveRecordingService.RecordingStatus.Processing -> "기록 정리 중"
            activeRecording -> if(restricted) "주행 기록 중" else "주행 마치기"
            trip.hasResult -> "주행 결과 확인"
            else -> "주행 시작"
        }
        newTripButton.visibility=if(trip.hasResult && !activeRecording && recording!=DriveRecordingService.RecordingStatus.Processing) View.VISIBLE else View.GONE
        newTripButton.isEnabled=!restricted
        driveButton.isSelected = activeRecording
        driveButton.setIcon(if (activeRecording) R.drawable.ic_track_stop else R.drawable.ic_car)
        story.isEnabled = !restricted
        driveButton.isEnabled = !restricted && recording != DriveRecordingService.RecordingStatus.Processing
        speechButton.isEnabled = !restricted && !activeRecording && recording != DriveRecordingService.RecordingStatus.Processing
        weatherButton.isEnabled = !restricted
        weatherButton.text = getString(R.string.weather_value, getString(trip.weather.label))
        playButton.isEnabled = !restricted && !activeRecording
        val arrangement = currentArrangement()
        genreButton.text = "장르 선택  ·  " + (trip.genre?.let { getString(it.label) } ?: "자동 추천") + "  ›"
        mixButton.text = "비트 꾸미기  ·  베이스와 멜로디  ›"
        generationGenres.forEachIndexed { index,button ->
            val selected=if(index==0) trip.genre==null else trip.genre==Genre.entries[index-1]
            button.isSelected=selected;button.isEnabled=!restricted
            button.stateDescription=if(selected) "선택됨" else "선택 안 됨"
            button.text=(if(selected) "✓ " else "")+(if(index==0) "자동 추천" else getString(Genre.entries[index-1].label))
        }
        generationGenreHint.text=if(trip.genre==null) "이야기와 날씨로 추천 · ${getString(arrangement.genre.label)}" else "선택한 장르 · ${getString(arrangement.genre.label)}"
        genreButton.isEnabled = !restricted
        mixButton.isEnabled = !restricted
        val counts = listOf(arrangement.wipers, arrangement.windows, arrangement.doors, arrangement.signals, arrangement.temperatureChanges, arrangement.recirculationChanges)
        val names = listOf("와이퍼", "창문", "문", "깜빡이", "설정 온도", "내기 순환")
        instrumentTiles.forEachIndexed { index, tile ->
            tile.text = "${names[index]} ${counts[index]}회\n▶ ${listOf("셰이커", "벨", "클랩", "림샷", "신스", "퍼커션")[index]} 듣기"
            tile.minHeight = dp(220)
            tile.isSelected = false
            tile.isEnabled = !restricted
            tile.contentDescription = "${names[index]} 변화 ${counts[index]}회, 해당 악기만 짧게 듣기"
        }
        val speedSource = selectedExample?.let { "속도 예시 ${it} km/h" }
            ?: trip.profile?.let { if(it.sampleCount>=DriveProfile.MIN_SAMPLES) "주행 속도 ${"%.0f".format(it.medianKmh)} km/h" else "속도 표본 부족 · 기본 템포" } ?: "주행 기록 없음 · 기본 템포"
        drivingRecipe.text = "$speedSource → ${arrangement.bpm} BPM  ·  기록 ${trip.durationSeconds/60}분 ${trip.durationSeconds%60}초 · 유효 속도 ${trip.profile?.sampleCount ?: 0}개\n가속·감속 → ${if (arrangement.syncopated) "변화 있는 엇박 리듬" else "고른 리듬"}"
        signalLabel.text = if (preview) "예시 신호로 듣는 중 · 실제 기록은 바뀌지 않아요" else if(trip.profile==null) "아직 주행 기록이 없어요 · 카드를 눌러 악기를 체험해 보세요" else "실제 주행 기록 · 차량 신호와 악기의 연결을 확인해 보세요"
        tempo.text = getString(R.string.track_tempo, arrangement.bpm)
        genreLabel.text = getString(arrangement.genre.label)
        storyPreview.text = story.text.toString().trim().ifEmpty { getString(R.string.story_preview_empty) }
        homeSummary.text="$speedSource → ${arrangement.bpm} BPM"
        homeBeat.isEnabled=!restricted && !activeRecording
        homeStory.isEnabled=!restricted && !activeRecording && recording!=DriveRecordingService.RecordingStatus.Processing
        renameButton.visibility=if(usingSong && songs.song!=null) View.VISIBLE else View.GONE
        songHeading.text=if(usingSong) songTitle() else "내 차의 비트"
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
        val playing = track.active || songPlayer.active
        playProgress.visibility=if(playing) View.VISIBLE else View.INVISIBLE
        playButton.text=if(playing) "멈추기" else "재생"
        playButton.setIcon(if (playing) R.drawable.ic_track_stop else R.drawable.ic_track_play)
        generateButton.isEnabled = !restricted && !activeRecording && recording != DriveRecordingService.RecordingStatus.Processing && !songs.pending
        generateButton.text=if(songs.pending) getString(R.string.song_generating) else if(trip.vocals) "노래 만들기" else "비트 듣기"
        lyricsButton.isEnabled = !restricted && songs.song != null
        localButton.isEnabled = !restricted && songs.file != null
        localButton.text=if(usingSong) "비트로 전환" else "노래로 전환"
        songStatus.text = if(songs.pending || songs.message.contains("못") || songs.message.contains("오류")) songs.message else if(story.text.toString().trim().length<3) "이야기를 남기면 노래를 만들 수 있어요." else "내용을 확인한 뒤 음악 만들기를 눌러 주세요."
        trackStatus.visibility=if(trackStatus.text.toString().let { it.contains("못") || it.contains("없") || it.contains("다른") }) View.VISIBLE else View.GONE
        makeOrb(playButton,48)
        updateInlineLyrics()
        if (usingSong) {
            storyPreview.text = songs.song?.optString("story")?.takeIf { it.isNotBlank() }
                ?: getString(R.string.story_preview_empty)
            tempo.text = "AI VOCAL"
            genreLabel.text = Genre.entries.firstOrNull { it.name == songs.song?.optString("genre") }?.let { getString(it.label) } ?: "우리 노래"
            rhythmLabel.text = "차 안의 이야기 · 한국어 보컬"
            tempoSource.text = "기기에 저장된 완성곡 · Lyria 3.5"
        }
    }

    private fun stopTrack() {
        track.stop()
        songPlayer.stop()
        trackStatus.setText(R.string.track_hint)
    }

    private fun confirmSong() {
        if (restricted || songs.pending) return
        if (!trip.vocals) { preview=false; previewBeat(); return }
        val memory = story.text.toString().trim()
        if (memory.length !in 3..2000) {
            selectPage(STORY)
            story.error = "여행 이야기를 3~2,000자로 남겨 주세요."
            story.requestFocus()
            return
        }
        val arrangement = currentArrangement()
        val request = org.json.JSONObject().put("story", memory)
            .put("genre", arrangement.genre.name).put("weather", trip.weather.name)
            .put("bpm", arrangement.bpm).put("wipers", arrangement.wipers)
            .put("windows", arrangement.windows).put("doors", arrangement.doors)
            .put("signals", arrangement.signals).put("rapMode", arrangement.rapMode)
            .put("punchy", arrangement.punchy).put("syncopated", arrangement.syncopated)
            .put("temperatureChanges",arrangement.temperatureChanges).put("recirculationChanges",arrangement.recirculationChanges)
        val names = EditText(this).apply {
            id = View.generateViewId(); setText(trip.names)
            hint = "예: 하나, 두리 · 비워도 괜찮아요"
            textSize = 25f; typeface = suit; fontVariationSettings="'wght' 500"; setTextColor(INK); setHintTextColor(MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isSingleLine = true; filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            minHeight = dp(80); setPadding(dp(24), dp(12), dp(24), dp(12))
            background = shape(CockpitTheme.SOFT, 20)
        }
        showSheet("음악 만들기", "이야기를 확인하고, 가사와 보컬이 있는 노래를 만들어 보세요.", "노래 만들기 · $0.08", {
            if (!songs.pending && recording != DriveRecordingService.RecordingStatus.Recording) {
                stopTrack()
                if (speech != null) finishSpeech(R.string.speech_stopped)
                trip.names = names.text.toString().trim()
                if (trip.names.isNotEmpty()) request.put("names", trip.names)
                songs.create(request)
                selectPage(PLAYER)
            }
        }) { body, _ ->
            body.addSpaced(copy("노래의 주인공 · 선택", 25f, INK).apply { labelFor = names.id }, 20)
            body.addSpaced(names, 12)
            body.addSpaced(copy("입력한 이름도 가사에 담도록 요청해요. 보내기 전에 함께 확인해 주세요.", 21f), 8)
            body.addSpaced(copy(memory, 28f, INK).apply {
                setPadding(dp(24), dp(24), dp(24), dp(24)); background = shape(CockpitTheme.SOFT, 20)
            }, 24)
            body.addSpaced(copy("${getString(arrangement.genre.label)} · 한국어 가사와 보컬", 26f, INK), 24)
            body.addSpaced(copy("음악 재료 · ${arrangement.bpm} BPM\n와이퍼 ${arrangement.wipers} → 셰이커 · 창문 ${arrangement.windows} → 벨·신스\n문 ${arrangement.doors} → 클랩 · 깜빡이 ${arrangement.signals} → 림샷", 23f, INK), 16)
            body.addSpaced(copy("이 재료를 AI에 전달해 새로 작곡해요. 미리듣기의 비트 음원을 그대로 사용하는 방식은 아니에요.", 21f), 12)
            body.addSpaced(copy("이야기·입력한 이름·날씨·주행 요약을 Google에 보내요. 새 곡 생성은 $0.08이며 세금·환율에 따라 달라집니다. 같은 내용으로 만든 곡은 다시 불러와요.", 23f), 20)
            body.addSpaced(copy("한 번 만든 곡은 저장돼요. 다시 재생할 때 생성 API를 사용하지 않아요.", 23f), 16)
        }
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
        makeOrb(speechButton,64)
        speech = KoreanSpeech(
            context = applicationContext,
            onListening = { speechStatus.setText(R.string.speech_listening) },
            onResult = { result ->
                val combined = listOf(story.text.toString().trim(), result.trim()).filter { it.isNotEmpty() }.joinToString("\n")
                if (combined.length <= 2000) {
                    story.setText(combined)
                    finishSpeech(R.string.speech_complete)
                } else finishSpeech(R.string.speech_story_full)
            },
            onPartial = { partial -> speechStatus.text = "듣는 중 · $partial" },
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
        makeOrb(speechButton,64)
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun heading(id: Int, size: Float) = label(id, size, INK, bold = true).apply {
        isAccessibilityHeading = true
        if (id == R.string.track_heading || id == R.string.app_name) {
            typeface = resources.getFont(R.font.gugi_regular)
            fontVariationSettings = null
            letterSpacing = 0.01f
        } else letterSpacing = -0.025f
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
        minHeight = dp(if (style == ButtonStyle.Solid || style == ButtonStyle.Drive) 104 else 88)
        minimumWidth = dp(76)
        stateListAnimator = null
        elevation = 0f
        setPadding(dp(20), dp(12), dp(20), dp(12))
        val disabled = intArrayOf(-android.R.attr.state_enabled)
        val focused = intArrayOf(android.R.attr.state_focused)
        val selected = intArrayOf(android.R.attr.state_selected)
        val fill = when (style) {
            ButtonStyle.Outline -> SURFACE
            ButtonStyle.Plain -> CockpitTheme.SOFT
            ButtonStyle.Navigation -> CockpitTheme.SOFT
            ButtonStyle.Drive -> CockpitTheme.MINT
            ButtonStyle.Solid -> CockpitTheme.PRIMARY
        }
        val foreground = if (style == ButtonStyle.Solid) Color.WHITE else INK
        val inkStates = ColorStateList(
            arrayOf(disabled, focused, selected, intArrayOf()),
            intArrayOf(MUTED, Color.WHITE, Color.WHITE, foreground),
        )
        setTextColor(inkStates)
        compoundDrawableTintList = inkStates
        compoundDrawablePadding = dp(12)
        icon?.let { setIcon(it) }
        val states = StateListDrawable().apply {
            addState(disabled, shape(BACKGROUND, 20))
            addState(focused, shape(CockpitTheme.PRIMARY, 20))
            addState(selected, shape(CockpitTheme.PRIMARY, 20))
            addState(intArrayOf(), if (style == ButtonStyle.Drive) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xFFA0FFE4.toInt(),CockpitTheme.MINT)).apply { cornerRadius=dp(24).toFloat() }
                else if (style == ButtonStyle.Solid)
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CockpitTheme.PRIMARY, 0xFF784DFF.toInt())).apply { cornerRadius = dp(24).toFloat() }
                else shape(fill, 20))
        }
        background = RippleDrawable(ColorStateList.valueOf(0x224875E0), states, shape(Color.WHITE, 20))
        setOnClickListener { click() }
    }

    private fun Button.setIcon(id: Int) {
        val icon = context.getDrawable(id)?.mutate()
        icon?.setBounds(0, 0, dp(32), dp(32))
        setCompoundDrawablesRelative(icon, null, null, null)
        icon?.setTintList(compoundDrawableTintList)
    }

    private fun makeOrb(button:Button,iconSize:Int) {
        val icon=button.compoundDrawablesRelative.firstOrNull { it!=null }
        icon?.setBounds(0,0,dp(iconSize),dp(iconSize))
        button.setCompoundDrawablesRelative(null,icon,null,null)
        button.gravity=Gravity.CENTER; button.textSize=23f; button.compoundDrawablePadding=dp(16)
        button.setPadding(dp(16),dp(20),dp(16),dp(20))
        val states=StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),shape(CockpitTheme.SOFT,120))
            addState(intArrayOf(android.R.attr.state_focused),shape(CockpitTheme.PRIMARY,120))
            addState(intArrayOf(),GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7852DD.toInt(),CockpitTheme.PRIMARY,0xFF5122CF.toInt())).apply { cornerRadius=dp(120).toFloat() })
        }
        button.background=RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),states,shape(Color.WHITE,120))
        button.elevation=0f
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
