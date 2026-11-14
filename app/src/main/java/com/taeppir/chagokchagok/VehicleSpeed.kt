package com.taeppir.chagokchagok

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

internal class VehicleSpeed(
    private val context: Context,
    private val onSpeed: (Float) -> Unit,
    private val onStatus: (Int) -> Unit,
) {
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var closed = false
    private var lastSpeed: Float? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (closed || value.propertyId != PROPERTY_ID) return
            val speed = value.value as? Float
            if (value.status != CarPropertyValue.STATUS_AVAILABLE ||
                speed == null || !speed.isFinite()
            ) {
                unavailable()
                return
            }
            if (speed != lastSpeed) {
                lastSpeed = speed
                Log.i(TAG, "PERF_VEHICLE_SPEED=${speed} m/s")
            }
            onSpeed(speed)
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            if (!closed && propertyId == PROPERTY_ID) unavailable()
        }
    }

    fun connect() {
        try {
            car = Car.createCar(
                context,
                Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
            ) { connection, ready ->
                if (closed) return@createCar
                if (ready) {
                    subscribe(connection)
                } else {
                    properties = null
                    lastSpeed = null
                    onStatus(R.string.speed_disconnected)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Car service connection failed", e)
            unavailable()
        }
    }

    private fun subscribe(connection: Car) {
        try {
            val manager = connection.getCarManager(CarPropertyManager::class.java)
            if (manager == null ||
                !manager.registerCallback(callback, PROPERTY_ID, CarPropertyManager.SENSOR_RATE_NORMAL)
            ) {
                unavailable()
                return
            }
            // Registration delivers an initial value and subsequent speed updates.
            properties = manager
        } catch (e: SecurityException) {
            Log.w(TAG, "Speed permission denied", e)
            onStatus(R.string.speed_denied)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Speed subscription failed", e)
            unavailable()
        }
    }

    private fun unavailable() {
        lastSpeed = null
        onStatus(R.string.speed_unavailable)
    }

    fun close() {
        closed = true
        try {
            properties?.unregisterCallback(callback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Speed callback cleanup failed", e)
        }
        try {
            car?.disconnect()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Car service disconnect failed", e)
        }
        properties = null
        car = null
    }

    private companion object {
        const val TAG = "ChagokSpeed"
        const val PROPERTY_ID = VehiclePropertyIds.PERF_VEHICLE_SPEED
    }
}
