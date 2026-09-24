package com.blendervcam.controller.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Wire protocol shared with the Blender add-on.
 * Every message: [1 byte type][4 byte big-endian length][payload].
 */
object Proto {
    const val DEFAULT_PORT = 9876
    const val MSG_JSON = 1
    const val MSG_POSE = 2   // phone -> Blender: 7 x float32 (px py pz qw qx qy qz)
    const val MSG_FRAME = 3  // Blender -> phone: JPEG / PNG bytes
    const val DISCOVER = "BLENDER_VCAM_DISCOVER"
}

/** TCP client. Reader and writer run on their own threads; poses are latest-value-wins. */
class BlenderClient(private val listener: Listener) {

    interface Listener {
        fun onConnected()
        /** [reason] is null when the user disconnected on purpose. */
        fun onDisconnected(reason: String?)
        fun onFrame(bitmap: Bitmap)
        fun onMessage(json: JSONObject)
    }

    @Volatile private var conn: Conn? = null

    val isConnected: Boolean get() = conn != null

    fun connect(host: String, port: Int) {
        conn?.close()
        val c = Conn(host, port)
        conn = c
        c.start()
    }

    fun disconnect() {
        val c = conn
        conn = null
        c?.close()
    }

    fun send(json: JSONObject) {
        conn?.out?.offer(packet(Proto.MSG_JSON, json.toString().toByteArray(Charsets.UTF_8)))
    }

    fun sendPose(px: Float, py: Float, pz: Float, qw: Float, qx: Float, qy: Float, qz: Float) {
        val bb = ByteBuffer.allocate(5 + 28)
        bb.put(Proto.MSG_POSE.toByte())
        bb.putInt(28)
        bb.putFloat(px).putFloat(py).putFloat(pz)
        bb.putFloat(qw).putFloat(qx).putFloat(qy).putFloat(qz)
        conn?.pose?.set(bb.array())
    }

    private fun packet(type: Int, payload: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(5 + payload.size)
        bb.put(type.toByte())
        bb.putInt(payload.size)
        bb.put(payload)
        return bb.array()
    }

    private inner class Conn(private val host: String, private val port: Int) {
        @Volatile var alive = true
        private val socket = Socket()
        val out = LinkedBlockingQueue<ByteArray>()
        val pose = AtomicReference<ByteArray?>(null)

        fun start() {
            Thread({ main() }, "vcam-net").start()
        }

        fun close() {
            alive = false
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }

        private fun main() {
            var reason: String? = null
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 4000)
                if (!alive) return
                listener.onConnected()
                val tx = Thread({ writeLoop() }, "vcam-tx")
                tx.isDaemon = true
                tx.start()
                readLoop()
            } catch (e: Exception) {
                reason = e.message ?: e.javaClass.simpleName
            } finally {
                val wasAlive = alive
                close()
                if (this@BlenderClient.conn === this) {
                    this@BlenderClient.conn = null
                    listener.onDisconnected(if (wasAlive) (reason ?: "Connection closed") else null)
                }
            }
        }

        private fun readLoop() {
            val din = DataInputStream(socket.getInputStream().buffered(64 * 1024))
            while (alive) {
                val type = din.readUnsignedByte()
                val len = din.readInt()
                if (len < 0 || len > 16_000_000) throw IOException("Bad message length")
                val payload = ByteArray(len)
                din.readFully(payload)
                when (type) {
                    Proto.MSG_FRAME -> {
                        val bmp = BitmapFactory.decodeByteArray(payload, 0, len)
                        if (bmp != null) listener.onFrame(bmp)
                    }
                    Proto.MSG_JSON -> {
                        try {
                            listener.onMessage(JSONObject(String(payload, Charsets.UTF_8)))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        private fun writeLoop() {
            try {
                val os = BufferedOutputStream(socket.getOutputStream(), 8192)
                var lastPing = 0L
                while (alive) {
                    var m = out.poll(16, TimeUnit.MILLISECONDS)
                    while (m != null) {
                        os.write(m)
                        m = out.poll()
                    }
                    val p = pose.getAndSet(null)
                    if (p != null) os.write(p)
                    val now = System.currentTimeMillis()
                    if (now - lastPing > 2000) {
                        lastPing = now
                        val ping = JSONObject().put("cmd", "ping").put("t", now)
                        os.write(packet(Proto.MSG_JSON, ping.toString().toByteArray(Charsets.UTF_8)))
                    }
                    os.flush()
                }
            } catch (_: Exception) {
                close()
            }
        }
    }
}
