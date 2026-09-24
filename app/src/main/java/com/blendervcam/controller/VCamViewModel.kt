package com.blendervcam.controller

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blendervcam.controller.net.BlenderClient
import com.blendervcam.controller.net.BlenderHost
import com.blendervcam.controller.net.Discovery
import com.blendervcam.controller.net.Proto
import com.blendervcam.controller.tracking.ArTracker
import com.blendervcam.controller.tracking.OrientationTracker
import com.blendervcam.controller.tracking.Quat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

enum class Screen { CONNECT, CAMERA }
enum class TrackingMode { GYRO, ARCORE }

data class BlenderStatus(
    val camera: String = "",
    val focal: Float = 50f,
    val recording: Boolean = false,
    val frame: Int = 1,
    val sceneFps: Float = 24f,
    val resX: Int = 1920,
    val resY: Int = 1080,
    val pillow: Boolean = true,
    val dofOn: Boolean = false,
    val focusDistance: Float = 10f,
    val fstop: Float = 2.8f,
)

class VCamViewModel(app: Application) : AndroidViewModel(app), BlenderClient.Listener {

    private val prefs = app.getSharedPreferences("vcam", Context.MODE_PRIVATE)
    private val client = BlenderClient(this)
    val orientation = OrientationTracker(app)
    val ar = ArTracker(app)

    // ---- connect screen ----
    var screen by mutableStateOf(Screen.CONNECT); private set
    var connecting by mutableStateOf(false); private set
    var scanning by mutableStateOf(false); private set
    val hosts = mutableStateListOf<BlenderHost>()
    var hostText by mutableStateOf(prefs.getString("host", "") ?: "")
    var portText by mutableStateOf(prefs.getInt("port", Proto.DEFAULT_PORT).toString())
    var message by mutableStateOf<String?>(null)

    // ---- live state from Blender ----
    var frame by mutableStateOf<ImageBitmap?>(null); private set
    var status by mutableStateOf(BlenderStatus()); private set
    var cameras by mutableStateOf<List<String>>(emptyList()); private set
    var previewFps by mutableIntStateOf(0); private set
    var pingMs by mutableIntStateOf(-1); private set

    // ---- controls ----
    var trackingMode by mutableStateOf(TrackingMode.GYRO); private set
    var arTracking by mutableStateOf(false); private set
    var motionScale by mutableFloatStateOf(1f); private set
    var moveSpeed by mutableFloatStateOf(2f)
    var smoothing by mutableFloatStateOf(0.25f)
    var levelHorizon by mutableStateOf(false)
    var showGrid by mutableStateOf(true)
    var previewWidth by mutableIntStateOf(640)
    var previewTargetFps by mutableIntStateOf(30)
    var previewQuality by mutableIntStateOf(70)
    var focal by mutableFloatStateOf(50f)
    var dofOn by mutableStateOf(false)
    var focusDistance by mutableFloatStateOf(5f)
    var fstop by mutableFloatStateOf(2.8f)
    var settingsOpen by mutableStateOf(false)

    // Joystick inputs, written by the UI thread and read by the pose loop.
    @Volatile var stickStrafe = 0f
    @Volatile var stickForward = 0f
    @Volatile var stickLift = 0f
    @Volatile var stickTurn = 0f

    private var offset = floatArrayOf(0f, 0f, 0f)
    private var yawTrim = 0f
    private var pendingRecenter = false
    private var lastFocalEdit = 0L
    private var lastDofEdit = 0L
    private var poseJob: Job? = null
    private var fpsWindowStart = 0L
    private var fpsCount = 0

    // ---------------------------------------------------------------- connection

    fun scan() {
        if (scanning) return
        scanning = true
        hosts.clear()
        Thread({
            try {
                Discovery.scan(getApplication<Application>()) { h -> hosts.add(h) }
            } finally {
                scanning = false
            }
        }, "vcam-scan").start()
    }

    fun connect(host: String, port: Int) {
        val h = host.trim()
        if (h.isEmpty()) {
            message = "Enter the IP address shown in Blender's VCam panel"
            return
        }
        prefs.edit().putString("host", h).putInt("port", port).apply()
        hostText = h
        portText = port.toString()
        connecting = true
        client.connect(h, port)
    }

    fun connectManual() {
        val port = portText.trim().toIntOrNull() ?: Proto.DEFAULT_PORT
        connect(hostText, port)
    }

    fun disconnect() {
        client.disconnect()
        handleDisconnected(null)
    }

    override fun onConnected() {
        connecting = false
        screen = Screen.CAMERA
        offset = floatArrayOf(0f, 0f, 0f)
        yawTrim = 0f
        pendingRecenter = false
        orientation.start()
        if (trackingMode == TrackingMode.ARCORE) ar.start()
        sendHello()
        startPoseLoop()
    }

    override fun onDisconnected(reason: String?) = handleDisconnected(reason)

    private fun handleDisconnected(reason: String?) {
        connecting = false
        screen = Screen.CONNECT
        settingsOpen = false
        poseJob?.cancel()
        poseJob = null
        orientation.stop()
        ar.stop()
        frame = null
        pingMs = -1
        previewFps = 0
        if (reason != null) message = "Disconnected: $reason"
    }

    fun onResume() {
        if (screen == Screen.CAMERA) {
            orientation.start()
            if (trackingMode == TrackingMode.ARCORE) ar.start()
        }
    }

    fun onPause() {
        orientation.stop()
        ar.stop()
    }

    override fun onCleared() {
        client.disconnect()
        orientation.stop()
        ar.release()
        super.onCleared()
    }

    // ---------------------------------------------------------------- messages from Blender

    override fun onFrame(bitmap: Bitmap) {
        frame = bitmap.asImageBitmap()
        fpsCount++
        val now = SystemClock.elapsedRealtime()
        if (now - fpsWindowStart >= 1000) {
            previewFps = fpsCount
            fpsCount = 0
            fpsWindowStart = now
        }
    }

    override fun onMessage(json: JSONObject) {
        when (json.optString("type")) {
            "status" -> {
                val res = json.optJSONArray("res")
                val dof = json.optJSONObject("dof")
                val s = BlenderStatus(
                    camera = json.optString("camera", ""),
                    focal = json.optDouble("focal", 50.0).toFloat(),
                    recording = json.optBoolean("recording", false),
                    frame = json.optInt("frame", 1),
                    sceneFps = json.optDouble("fps", 24.0).toFloat(),
                    resX = res?.optInt(0, 1920) ?: 1920,
                    resY = res?.optInt(1, 1080) ?: 1080,
                    pillow = json.optBoolean("pillow", true),
                    dofOn = dof?.optBoolean("on", false) ?: false,
                    focusDistance = dof?.optDouble("distance", 10.0)?.toFloat() ?: 10f,
                    fstop = dof?.optDouble("fstop", 2.8)?.toFloat() ?: 2.8f,
                )
                status = s
                val now = SystemClock.elapsedRealtime()
                // Don't fight the slider while the user is dragging it.
                if (now - lastFocalEdit > 1200) focal = s.focal
                if (now - lastDofEdit > 1200) {
                    dofOn = s.dofOn
                    focusDistance = s.focusDistance
                    fstop = s.fstop
                }
                levelHorizon = json.optBoolean("level", levelHorizon)
            }
            "cameras" -> {
                val arr = json.optJSONArray("items")
                val list = ArrayList<String>()
                if (arr != null) for (i in 0 until arr.length()) list.add(arr.optString(i))
                cameras = list
            }
            "pong" -> {
                val t = json.optLong("t", 0L)
                if (t > 0) pingMs = (System.currentTimeMillis() - t).toInt().coerceAtLeast(0)
            }
            "toast" -> message = json.optString("text")
        }
    }

    // ---------------------------------------------------------------- commands to Blender

    private fun cmd(name: String, block: JSONObject.() -> Unit = {}) {
        client.send(JSONObject().put("cmd", name).apply(block))
    }

    private fun sendHello() {
        cmd("hello") {
            put("version", 1)
            put("device", Build.MODEL ?: "Android")
            put(
                "stream",
                JSONObject()
                    .put("width", previewWidth)
                    .put("fps", previewTargetFps)
                    .put("quality", previewQuality)
                    .put("enabled", true),
            )
        }
        cmd("smoothing") { put("value", smoothing.toDouble()) }
        cmd("level") { put("on", levelHorizon) }
    }

    fun updateFocal(mm: Float) {
        focal = mm
        lastFocalEdit = SystemClock.elapsedRealtime()
        cmd("set") { put("focal", mm.toDouble()) }
    }

    fun applySmoothing() = cmd("smoothing") { put("value", smoothing.toDouble()) }

    fun setLevel(on: Boolean) {
        levelHorizon = on
        cmd("level") { put("on", on) }
    }

    fun applyStream() = cmd("stream") {
        put("width", previewWidth)
        put("fps", previewTargetFps)
        put("quality", previewQuality)
    }

    fun applyDof() {
        lastDofEdit = SystemClock.elapsedRealtime()
        cmd("set") {
            put("dof", dofOn)
            put("focus_distance", focusDistance.toDouble())
            put("fstop", fstop.toDouble())
        }
    }

    fun toggleRecord() = cmd("record") { put("on", !status.recording) }
    fun recenter() = cmd("recenter")
    fun selectCamera(name: String) = cmd("set_camera") { put("name", name) }

    // ---------------------------------------------------------------- tracking

    /** Keeps the camera from jumping when the motion scale changes while walking in AR. */
    fun updateMotionScale(newScale: Float) {
        val p = ar.pose
        if (p != null && trackingMode == TrackingMode.ARCORE) {
            val d = motionScale - newScale
            offset[0] += p[0] * d
            offset[1] += p[1] * d
            offset[2] += p[2] * d
        }
        motionScale = newScale
    }

    fun setTracking(mode: TrackingMode) {
        if (mode == trackingMode) return
        if (mode == TrackingMode.ARCORE) {
            val err = ar.start()
            if (err != null) {
                message = err
                return
            }
        } else {
            ar.stop()
        }
        trackingMode = mode
        offset = floatArrayOf(0f, 0f, 0f)
        pendingRecenter = true
    }

    private fun startPoseLoop() {
        poseJob?.cancel()
        poseJob = viewModelScope.launch(Dispatchers.Default) {
            var last = System.nanoTime()
            while (isActive) {
                delay(16)
                val now = System.nanoTime()
                val dt = min((now - last) / 1e9f, 0.1f)
                last = now
                poseStep(dt)
            }
        }
    }

    private fun shape(x: Float) = x * abs(x)

    private fun poseStep(dt: Float) {
        var q: FloatArray
        var bx = 0f
        var by = 0f
        var bz = 0f
        if (trackingMode == TrackingMode.ARCORE) {
            val p = ar.pose
            val tracking = ar.tracking
            if (tracking != arTracking) arTracking = tracking
            if (p == null || !tracking) return
            val s = motionScale
            bx = p[0] * s; by = p[1] * s; bz = p[2] * s
            q = floatArrayOf(p[3], p[4], p[5], p[6])
        } else {
            if (!orientation.hasData) return
            q = orientation.quat
        }

        yawTrim += stickTurn * 1.2f * dt
        if (yawTrim != 0f) q = Quat.mul(Quat.axisZ(yawTrim), q)

        val fwd = Quat.rotate(q, floatArrayOf(0f, 0f, -1f))
        val right = Quat.rotate(q, floatArrayOf(1f, 0f, 0f))
        val sx = shape(stickStrafe)
        val sy = shape(stickForward)
        val sz = shape(stickLift)
        val step = moveSpeed * dt
        offset[0] += (right[0] * sx + fwd[0] * sy) * step
        offset[1] += (right[1] * sx + fwd[1] * sy) * step
        offset[2] += (right[2] * sx + fwd[2] * sy + sz) * step

        if (pendingRecenter) {
            pendingRecenter = false
            recenter()
        }
        client.sendPose(bx + offset[0], by + offset[1], bz + offset[2], q[0], q[1], q[2], q[3])
    }
}
