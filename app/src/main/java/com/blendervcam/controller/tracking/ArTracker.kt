package com.blendervcam.controller.tracking

import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Display
import android.view.Surface
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Optional 6-DoF tracking with ARCore. The camera feed is never shown; we only read the pose.
 * ARCore needs a GL context, so [createGlView] returns a tiny GLSurfaceView that must be attached
 * to the window while tracking is active.
 *
 * ARCore world: X right, Y up, -Z forward. Blender world: X right, Y forward, Z up.
 * Blender = (x, -z, y) for positions, and orientation = R(+90 deg about X) * arcoreOrientation.
 */
class ArTracker(private val context: Context) {
    /** [px, py, pz, qw, qx, qy, qz] in Blender world axes, metres. */
    @Volatile var pose: FloatArray? = null
        private set
    @Volatile var tracking: Boolean = false
        private set

    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val lock = Any()
    private var session: Session? = null

    /** Returns null on success, or a user-facing error message. */
    fun start(): String? = synchronized(lock) {
        try {
            if (session == null) {
                val s = Session(context)
                val cfg = Config(s)
                cfg.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                cfg.planeFindingMode = Config.PlaneFindingMode.DISABLED
                cfg.lightEstimationMode = Config.LightEstimationMode.DISABLED
                s.configure(cfg)
                session = s
            }
            session?.resume()
            null
        } catch (e: Exception) {
            "ARCore: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun stop() {
        synchronized(lock) {
            try {
                session?.pause()
            } catch (_: Exception) {
            }
            tracking = false
            pose = null
        }
    }

    fun release() {
        synchronized(lock) {
            try {
                session?.close()
            } catch (_: Exception) {
            }
            session = null
            tracking = false
            pose = null
        }
    }

    fun createGlView(ctx: Context): GLSurfaceView {
        val view = GLSurfaceView(ctx)
        view.preserveEGLContextOnPause = true
        view.setEGLContextClientVersion(2)
        view.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        view.setRenderer(renderer)
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        return view
    }

    private val renderer = object : GLSurfaceView.Renderer {
        private var texId = 0
        private var w = 0
        private var h = 0
        private var geometryDirty = true
        private var lastTimestamp = 0L
        private val half = 0.70710678f

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            texId = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            geometryDirty = true
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            w = width
            h = height
            geometryDirty = true
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            synchronized(lock) {
                val s = session ?: return
                if (w == 0 || h == 0) return
                try {
                    if (geometryDirty) {
                        val rot = dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
                        s.setDisplayGeometry(rot, w, h)
                        geometryDirty = false
                    }
                    s.setCameraTextureName(texId)
                    val frame = s.update()
                    if (frame.timestamp == lastTimestamp) return
                    lastTimestamp = frame.timestamp
                    val cam = frame.camera
                    if (cam.trackingState == TrackingState.TRACKING) {
                        val p = cam.displayOrientedPose
                        val t = p.translation                // x, y, z
                        val q = p.rotationQuaternion          // x, y, z, w
                        // Blender = C * ARCore, with C = +90 deg about X = (w=h, x=h, y=0, z=0)
                        val out = Quat.mul(floatArrayOf(half, half, 0f, 0f), floatArrayOf(q[3], q[0], q[1], q[2]))
                        pose = floatArrayOf(t[0], -t[2], t[1], out[0], out[1], out[2], out[3])
                        tracking = true
                    } else {
                        tracking = false
                    }
                } catch (_: Throwable) {
                    tracking = false
                }
            }
        }
    }
}
