package com.blendervcam.controller.net

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class BlenderHost(val name: String, val address: String, val port: Int, val blender: String)

object Discovery {
    /** Blocking. Broadcasts a discovery datagram on the LAN and reports every Blender that answers. */
    @Suppress("DEPRECATION")
    fun scan(
        context: Context,
        port: Int = Proto.DEFAULT_PORT,
        durationMs: Long = 2500,
        onFound: (BlenderHost) -> Unit,
    ) {
        val targets = mutableListOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val d = wm.dhcpInfo
            if (d != null && d.netmask != 0) {
                val b = (d.ipAddress and d.netmask) or d.netmask.inv()
                val bytes = ByteArray(4) { i -> ((b shr (8 * i)) and 0xFF).toByte() }
                targets.add(InetAddress.getByAddress(bytes))
            }
        } catch (_: Exception) {
        }

        val sock = DatagramSocket()
        try {
            sock.broadcast = true
            sock.soTimeout = 300
            val msg = Proto.DISCOVER.toByteArray()
            val end = System.currentTimeMillis() + durationMs
            var lastSend = 0L
            val seen = HashSet<String>()
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() - lastSend > 600) {
                    lastSend = System.currentTimeMillis()
                    for (t in targets) {
                        try {
                            sock.send(DatagramPacket(msg, msg.size, t, port))
                        } catch (_: Exception) {
                        }
                    }
                }
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val json = JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8))
                    if (json.optString("app") == "blender-vcam") {
                        val addr = pkt.address.hostAddress
                        if (addr != null && seen.add(addr)) {
                            onFound(
                                BlenderHost(
                                    json.optString("name", "Blender"),
                                    addr,
                                    json.optInt("port", port),
                                    json.optString("blender", ""),
                                )
                            )
                        }
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                }
            }
        } finally {
            sock.close()
        }
    }
}
