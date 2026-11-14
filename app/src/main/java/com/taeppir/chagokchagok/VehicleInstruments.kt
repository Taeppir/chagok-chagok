package com.taeppir.chagokchagok

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

internal class VehicleInstruments(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val cleanup = mutableListOf<() -> Unit>()
    private var closed = false
    private val connected = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    var wipers = 0
        private set
    var windows = 0
        private set
    var doors = 0
        private set
    var signals = 0
        private set
    val status: String
        get() = listOf("와이퍼", "창문", "문", "방향지시등").joinToString(" · ") {
            "$it " + when (it) {
                in failed -> "연결 불가"
                in connected -> "연결됨"
                else -> "대기"
            }
        }

    fun connect() {
        try {
            vehicle.initialize()
            subscribeSignals()
            vehicle.getDoor().checkDoorPositionCapabilities(
                onComplete = { _, areas -> main.post { if (!closed) areas.keys.forEach(::subscribeDoor) } },
                onFailed = { error("문", it) },
            )
            vehicle.getWiper().checkWiperStateCapabilities(
                onComplete = { _, areas ->
                    main.post {
                        if (!closed) areas.keys.forEach { area -> subscribeWiper(area) }
                    }
                },
                onFailed = { error("와이퍼", it) },
            )
            vehicle.getWindow().checkWindowPositionCapabilities(
                onComplete = { _, areas ->
                    main.post {
                        if (!closed) areas.keys.forEach { area -> subscribeWindow(area) }
                    }
                },
                onFailed = { error("창문", it) },
            )
        } catch (e: Exception) {
            error("와이퍼", e)
            error("창문", e)
        }
    }

    private fun subscribeWiper(area: WindowArea) {
        var previous: WiperState? = null
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                main.post {
                    if (!closed && state != null) {
                        connected.add("와이퍼")
                        failed.remove("와이퍼")
                        if (previous != null && previous != state) {
                            wipers = (wipers + 1).coerceAtMost(64)
                            Log.i("ChagokInstruments", "Wiper transition count=$wipers")
                        }
                        previous = state
                    }
                }
            }
            override fun onFailed(e: Exception) { error("와이퍼", e) }
        }
        try {
            vehicle.getWiper().registerWiperState(area, listener)
            cleanup.add { vehicle.getWiper().unregisterWiperState(area, listener) }
        } catch (e: Exception) { error("와이퍼", e) }
    }

    private fun subscribeWindow(area: WindowArea) {
        var previous: Int? = null
        val listener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) {
                main.post {
                    if (!closed && position != null) {
                        connected.add("창문")
                        failed.remove("창문")
                        val old = previous
                        if (old != null && old != position) {
                            windows = (windows + 1).coerceAtMost(64)
                            Log.i("ChagokInstruments", "Window transition count=$windows")
                        }
                        previous = position
                    }
                }
            }
            override fun onFailed(e: Exception) { error("창문", e) }
        }
        try {
            vehicle.getWindow().registerWindowPosition(area, listener)
            cleanup.add { vehicle.getWindow().unregisterWindowPosition(area, listener) }
        } catch (e: Exception) { error("창문", e) }
    }

    private fun subscribeSignals() {
        var previous: TurnSignalValue? = null
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                main.post {
                    if (!closed && value != null) {
                        connected.add("방향지시등")
                        failed.remove("방향지시등")
                        if (previous != null && previous != value) signals = (signals + 1).coerceAtMost(64)
                        previous = value
                    }
                }
            }
            override fun onFailed(e: Exception) { error("방향지시등", e) }
        }
        try {
            vehicle.getTurnSignal().registerTurnSignalState(listener)
            cleanup.add { vehicle.getTurnSignal().unregisterTurnSignalState(listener) }
        } catch (e: Exception) { error("방향지시등", e) }
    }

    private fun subscribeDoor(area: DoorArea) {
        var previous: Int? = null
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(value: Int?) {
                main.post {
                    if (!closed && value != null) {
                        connected.add("문")
                        failed.remove("문")
                        if (previous != null && previous != value) doors = (doors + 1).coerceAtMost(64)
                        previous = value
                    }
                }
            }
            override fun onFailed(e: Exception) { error("문", e) }
        }
        try {
            vehicle.getDoor().registerDoorPosition(area, listener)
            cleanup.add { vehicle.getDoor().unregisterDoorPosition(area, listener) }
        } catch (e: Exception) { error("문", e) }
    }

    private fun error(name: String, e: Exception) {
        main.post {
            if (!closed) {
                failed.add(name)
                Log.w("ChagokInstruments", "$name unavailable: ${e.javaClass.simpleName}")
            }
        }
    }

    fun close() {
        closed = true
        cleanup.forEach { try { it() } catch (_: Exception) { } }
        cleanup.clear()
        try { vehicle.release() } catch (_: Exception) { }
    }
}
