package com.blendervcam.controller.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface

/**
 * Gyro/accelerometer orientation.
 *
 * Android's world frame (X east, Y north, Z up) is the same as Blender's, and after remapping for the
 * screen rotation the device frame (X right, Y up, Z out of the screen) is the same as a Blender camera
 * (looks down -Z, +Y up). So the rotation matrix maps straight onto the Blender camera orientation.
 * Yaw is arbitrary with the game rotation vector; the add-on's "recenter" absorbs it.
 */
class OrientationTracker(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val sensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = sensor != null

    /** Camera orientation in Blender world axes, (w, x, y, z). */
    @Volatile var quat: FloatArray = floatArrayOf(1f, 0f, 0f, 0f)
        private set
    @Volatile var hasData: Boolean = false
        private set

    private val r = FloatArray(9)
    private val r2 = FloatArray(9)

    fun start() {
        val s = sensor ?: return
        sm.registerListener(this, s, 10_000) // ~100 Hz
    }

    fun stop() {
        sm.unregisterListener(this)
        hasData = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val v = e.values
        val vec = if (v.size >= 4) v.copyOf(4) else v.copyOf(3)
        SensorManager.getRotationMatrixFromVector(r, vec)
        val rotation = dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        val axes = when (rotation) {
            Surface.ROTATION_90 -> Pair(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X)
            Surface.ROTATION_180 -> Pair(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y)
            Surface.ROTATION_270 -> Pair(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
            else -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Y)
        }
        SensorManager.remapCoordinateSystem(r, axes.first, axes.second, r2)
        quat = Quat.fromMatrix(r2)
        hasData = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
