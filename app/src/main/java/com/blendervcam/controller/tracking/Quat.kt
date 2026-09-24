package com.blendervcam.controller.tracking

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Tiny quaternion helpers. Quaternions are float[4] in (w, x, y, z) order, vectors float[3]. */
object Quat {
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val aw = a[0]; val ax = a[1]; val ay = a[2]; val az = a[3]
        val bw = b[0]; val bx = b[1]; val by = b[2]; val bz = b[3]
        return floatArrayOf(
            aw * bw - ax * bx - ay * by - az * bz,
            aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
        )
    }

    /** Rotation of [angle] radians about the world Z axis. */
    fun axisZ(angle: Float): FloatArray = floatArrayOf(cos(angle / 2f), 0f, 0f, sin(angle / 2f))

    fun rotate(q: FloatArray, v: FloatArray): FloatArray {
        val w = q[0]; val ux = q[1]; val uy = q[2]; val uz = q[3]
        // t = 2 * (u x v)
        val tx = 2f * (uy * v[2] - uz * v[1])
        val ty = 2f * (uz * v[0] - ux * v[2])
        val tz = 2f * (ux * v[1] - uy * v[0])
        // v' = v + w * t + u x t
        return floatArrayOf(
            v[0] + w * tx + (uy * tz - uz * ty),
            v[1] + w * ty + (uz * tx - ux * tz),
            v[2] + w * tz + (ux * ty - uy * tx),
        )
    }

    fun normalize(q: FloatArray): FloatArray {
        val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (n < 1e-9f) return floatArrayOf(1f, 0f, 0f, 0f)
        return floatArrayOf(q[0] / n, q[1] / n, q[2] / n, q[3] / n)
    }

    /** Row-major 3x3 rotation matrix -> quaternion (w, x, y, z). */
    fun fromMatrix(m: FloatArray): FloatArray {
        val m00 = m[0]; val m01 = m[1]; val m02 = m[2]
        val m10 = m[3]; val m11 = m[4]; val m12 = m[5]
        val m20 = m[6]; val m21 = m[7]; val m22 = m[8]
        val tr = m00 + m11 + m22
        val w: Float; val x: Float; val y: Float; val z: Float
        if (tr > 0f) {
            val s = sqrt(tr + 1f) * 2f
            w = 0.25f * s; x = (m21 - m12) / s; y = (m02 - m20) / s; z = (m10 - m01) / s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            w = (m21 - m12) / s; x = 0.25f * s; y = (m01 + m10) / s; z = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            w = (m02 - m20) / s; x = (m01 + m10) / s; y = 0.25f * s; z = (m12 + m21) / s
        } else {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            w = (m10 - m01) / s; x = (m02 + m20) / s; y = (m12 + m21) / s; z = 0.25f * s
        }
        return normalize(floatArrayOf(w, x, y, z))
    }
}
