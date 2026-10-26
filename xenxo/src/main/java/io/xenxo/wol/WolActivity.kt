package io.xenxo.wol

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Wake-on-LAN (WOL) por Wi-Fi para Xiaomi Mi Watch (368×448 AMOLED).
 *
 * Envía paquetes mágicos UDP (102 bytes) a la dirección de difusión de la red local
 * para encender servidores, PCs de sobremesa o estaciones de trabajo del homelab con un toque.
 */
class WolActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private lateinit var txtStatus: TextView

    data class WolDevice(val name: String, val mac: String)

    private val defaultDevices = listOf(
        WolDevice("PC Sobremesa", "00:11:22:33:44:55"),
        WolDevice("Servidor NAS", "AA:BB:CC:DD:EE:FF"),
        WolDevice("Homelab", "10:20:30:40:50:60")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("wol_prefs", Context.MODE_PRIVATE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val d = resources.displayMetrics.density

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (20 * d).toInt())
        }

        val lblTitle = TextView(this).apply {
            text = "⚡ Wake-on-LAN xenxo"
            textSize = 15f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * d).toInt())
        }
        layout.addView(lblTitle)

        txtStatus = TextView(this).apply {
            text = "Toca para encender tu equipo por Wi-Fi"
            textSize = 10f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        layout.addView(txtStatus)

        // Lista de equipos configurables
        for (dev in defaultDevices) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(0xFF1C1C1E.toInt())
                    cornerRadius = 10 * d
                    setStroke((1 * d).toInt(), 0xFF5AC8FA.toInt()) // Cian
                }
                setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
                val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (8 * d).toInt()
                }
                layoutParams = p

                val t = TextView(this@WolActivity).apply {
                    text = "💻 ${dev.name}"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    paint.isFakeBoldText = true
                }
                addView(t)

                val sub = TextView(this@WolActivity).apply {
                    text = "MAC: ${dev.mac}"
                    textSize = 9.5f
                    setTextColor(0xFF8E8E93.toInt())
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }
                addView(sub)

                setOnClickListener {
                    sendWakeOnLan(dev.name, dev.mac)
                }
            }
            layout.addView(card)
        }

        scrollView.addView(layout)
        root.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
    }

    private fun sendWakeOnLan(name: String, macStr: String) {
        vibrator?.vibrate(longArrayOf(0, 60, 40, 60), -1)
        txtStatus.text = "Enviando paquete a $name..."
        txtStatus.setTextColor(0xFF5AC8FA.toInt())

        bgExecutor.execute {
            val success = runCatching {
                val macBytes = parseMac(macStr)
                val bytes = ByteArray(6 + 16 * macBytes.size)

                // 6 bytes de 0xFF
                for (i in 0 until 6) {
                    bytes[i] = 0xFF.toByte()
                }

                // 16 repeticiones de la dirección MAC
                for (i in 0 until 16) {
                    System.arraycopy(macBytes, 0, bytes, 6 + i * macBytes.size, macBytes.size)
                }

                val address = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(bytes, bytes.size, address, 9)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(packet)
                }
                true
            }.getOrDefault(false)

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                if (success) {
                    vibrator?.vibrate(longArrayOf(0, 100, 50, 150), -1)
                    txtStatus.text = "✨ ¡$name encendido con éxito!"
                    txtStatus.setTextColor(0xFF30D158.toInt())
                } else {
                    txtStatus.text = "Error al enviar por Wi-Fi"
                    txtStatus.setTextColor(0xFFFF453A.toInt())
                }
            }
        }
    }

    private fun parseMac(macStr: String): ByteArray {
        val clean = macStr.replace(":", "").replace("-", "")
        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }
}
