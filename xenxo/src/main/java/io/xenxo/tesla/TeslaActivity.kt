package io.xenxo.tesla

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Suite Tesla para Xiaomi Mi Watch (368×448 AMOLED).
 *
 * Incluye:
 * 1. Tarjeta Llave Tesla Oficial (NFC HCE secp256r1 NIST P-256): 100% offline, para abrir en pilar B y arrancar.
 * 2. Control remoto de climatización con ajuste de temperatura mediante la corona rotatoria.
 * 3. Acciones rápidas con un toque: Frunk (maletero delantero), maletero trasero, bloqueo y claxon/ráfaga.
 * 4. Monitor de batería (%) y autonomía estimada (km).
 */
class TeslaActivity : Activity() {

    private lateinit var controller: TeslaController
    private lateinit var keyStore: TeslaKeyStore
    private var vibrator: Vibrator? = null

    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout

    private lateinit var txtHeaderStatus: TextView
    private lateinit var txtNfcStatus: TextView
    private lateinit var txtTempDisplay: TextView
    private lateinit var btnClimateToggle: TextView
    private lateinit var btnLockToggle: TextView

    private var targetTemp = 21.0f
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            controller.recordNfcTap()
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = TeslaController(this)
        keyStore = TeslaKeyStore(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val d = resources.displayMetrics.density

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (24 * d).toInt())
        }

        // TÍTULO TESLA CON LOGO
        val isDemo = !controller.hasApiConfig()
        val lblTitle = TextView(this).apply {
            text = if (isDemo) "🚗 Tesla (Simulación)" else "🚗 Tesla xenxo"
            textSize = 15f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        contentLayout.addView(lblTitle)

        if (isDemo) {
            val lblDemoNotice = TextView(this).apply {
                text = "Modo simulación · Sin vehículo vinculado"
                textSize = 9f
                setTextColor(0xFFFF9F0A.toInt()) // Ámbar
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (2 * d).toInt())
            }
            contentLayout.addView(lblDemoNotice)
        }

        // ESTADO BATERÍA / AUTONOMÍA
        txtHeaderStatus = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFF30D158.toInt()) // Verde Apple/Tesla
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, (2 * d).toInt(), 0, (8 * d).toInt())
        }
        contentLayout.addView(txtHeaderStatus)

        // CARD 1: TARJETA LLAVE OFICIAL NFC (HCE)
        val cardNfc = createNfcCard(d)
        contentLayout.addView(cardNfc)

        // CARD 2: CLIMATIZACIÓN Y SELECTOR DE TEMPERATURA CON CORONA
        val cardClimate = createClimateCard(d)
        contentLayout.addView(cardClimate)

        // CARD 3: ACCIONES RÁPIDAS (Frunk, Trunk, Bloqueo, Claxon)
        val cardActions = createActionsCard(d)
        contentLayout.addView(cardActions)

        scrollView.addView(contentLayout)
        root.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)

        controller.onStateChanged = {
            updateUi()
        }

        registerReceiver(tapReceiver, IntentFilter(TeslaKeyApduService.ACTION_TESLA_TAP))
        updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(tapReceiver)
    }

    private fun createNfcCard(d: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E0A0A.toInt()) // Fondo rojo oscuro sutil
                cornerRadius = 10 * d
                setStroke((1.5f * d).toInt(), 0xFFE82127.toInt()) // Borde rojo Tesla
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * d).toInt()
            }
            layoutParams = p

            val t = TextView(this@TeslaActivity).apply {
                text = "💳 Tarjeta Llave Tesla (HCE)"
                textSize = 12f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            addView(t)

            txtNfcStatus = TextView(this@TeslaActivity).apply {
                text = "✅ 100% OFFLINE · Huella: ${keyStore.getFingerprint()}"
                textSize = 9.5f
                setTextColor(0xFFFF453A.toInt())
                setPadding(0, (3 * d).toInt(), 0, 0)
            }
            addView(txtNfcStatus)

            val desc = TextView(this@TeslaActivity).apply {
                text = "Apoya el reloj en el pilar B para abrir o en la consola central para arrancar."
                textSize = 8.5f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, (3 * d).toInt(), 0, 0)
            }
            addView(desc)
        }
    }

    private fun createClimateCard(d: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadius = 10 * d
                setStroke((1 * d).toInt(), 0xFF333333.toInt())
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * d).toInt()
            }
            layoutParams = p

            val lbl = TextView(this@TeslaActivity).apply {
                text = "❄️ CLIMATIZACIÓN (Corona)"
                textSize = 10f
                setTextColor(0xFF0A84FF.toInt())
                paint.isFakeBoldText = true
            }
            addView(lbl)

            // Selector de temperatura
            val tempRow = LinearLayout(this@TeslaActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
            }

            val btnMinus = TextView(this@TeslaActivity).apply {
                text = "➖"
                textSize = 16f
                setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
                setOnClickListener {
                    adjustTemperature(-0.5f)
                }
            }
            tempRow.addView(btnMinus)

            txtTempDisplay = TextView(this@TeslaActivity).apply {
                text = "21.0 °C"
                textSize = 22f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
            }
            tempRow.addView(txtTempDisplay)

            val btnPlus = TextView(this@TeslaActivity).apply {
                text = "➕"
                textSize = 16f
                setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
                setOnClickListener {
                    adjustTemperature(+0.5f)
                }
            }
            tempRow.addView(btnPlus)
            addView(tempRow)

            btnClimateToggle = TextView(this@TeslaActivity).apply {
                text = "Encender Clima"
                textSize = 12f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0xFF2C2C2E.toInt())
                    cornerRadius = 8 * d
                }
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                setOnClickListener {
                    vibrator?.vibrate(30)
                    controller.toggleClimate { success ->
                        updateUi()
                    }
                }
            }
            addView(btnClimateToggle)
        }
    }

    private fun createActionsCard(d: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadius = 10 * d
                setStroke((1 * d).toInt(), 0xFF333333.toInt())
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * d).toInt()
            }
            layoutParams = p

            val lbl = TextView(this@TeslaActivity).apply {
                text = "⚡ ACCIONES RÁPIDAS"
                textSize = 10f
                setTextColor(0xFFFF9F0A.toInt())
                paint.isFakeBoldText = true
            }
            addView(lbl)

            // Fila 1: Frunk y Trunk
            val row1 = LinearLayout(this@TeslaActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (6 * d).toInt(), 0, 0)
            }

            val btnFrunk = createActionButtonCompact("📦 Frunk", 0xFF0A84FF.toInt(), d) {
                controller.openFrunk { showToast("Frunk abierto ✓") }
            }
            val p1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (4 * d).toInt()
            }
            row1.addView(btnFrunk, p1)

            val btnTrunk = createActionButtonCompact("🚪 Trunk", 0xFF5856D6.toInt(), d) {
                controller.openTrunk { showToast("Maletero abierto ✓") }
            }
            val p2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (4 * d).toInt()
            }
            row1.addView(btnTrunk, p2)
            addView(row1)

            // Fila 2: Bloqueo y Claxon
            val row2 = LinearLayout(this@TeslaActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (6 * d).toInt(), 0, 0)
            }

            btnLockToggle = createActionButtonCompact("🔒 Bloquear", 0xFF30D158.toInt(), d) {
                controller.toggleLock { updateUi() }
            }
            val p3 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (4 * d).toInt()
            }
            row2.addView(btnLockToggle, p3)

            val btnHonk = createActionButtonCompact("🔊 Ráfaga", 0xFFFF9F0A.toInt(), d) {
                controller.flashLights { showToast("Luces destelladas ✓") }
            }
            val p4 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (4 * d).toInt()
            }
            row2.addView(btnHonk, p4)
            addView(row2)
        }
    }

    private fun createActionButtonCompact(textStr: String, accentColor: Int, d: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = textStr
            textSize = 11f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF2C2C2E.toInt())
                cornerRadius = 8 * d
                setStroke((1 * d).toInt(), accentColor)
            }
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            setOnClickListener {
                vibrator?.vibrate(25)
                onClick()
            }
        }
    }

    private fun adjustTemperature(delta: Float) {
        targetTemp = (targetTemp + delta).coerceIn(15.0f, 28.0f)
        vibrator?.vibrate(15)
        txtTempDisplay.text = "%.1f °C".format(targetTemp)
        controller.setTargetTemperature(targetTemp)
    }

    private fun updateUi() {
        if (isFinishing || isDestroyed) return
        val s = controller.getState()
        targetTemp = s.targetTemp
        txtHeaderStatus.text = "🔋 ${s.batteryPercent}% · 🛣️ ${s.rangeKm} km · ${if (s.isLocked) "🔒 Cerrado" else "🔓 Abierto"}"
        txtTempDisplay.text = "%.1f °C".format(s.targetTemp)

        if (s.isClimateOn) {
            btnClimateToggle.text = "Climatizador ENCENDIDO (Apagar)"
            (btnClimateToggle.background as? GradientDrawable)?.setColor(0xFF007AFF.toInt())
        } else {
            btnClimateToggle.text = "Encender Climatizador"
            (btnClimateToggle.background as? GradientDrawable)?.setColor(0xFF2C2C2E.toInt())
        }

        btnLockToggle.text = if (s.isLocked) "🔓 Desbloquear" else "🔒 Bloquear"

        if (s.lastTapTime > 0) {
            val dateStr = timeFormat.format(Date(s.lastTapTime))
            txtNfcStatus.text = "✨ LLAVE RECONOCIDA ($dateStr)"
            txtNfcStatus.setTextColor(0xFF30D158.toInt())
        } else {
            txtNfcStatus.text = "100% OFFLINE · Huella: ${keyStore.getFingerprint()}"
            txtNfcStatus.setTextColor(0xFFFF9F0A.toInt())
        }
    }

    private fun showToast(msg: String) {
        if (isFinishing || isDestroyed) return
        vibrator?.vibrate(longArrayOf(0, 40, 30, 40), -1)
    }

    /** Manejo fluido de la corona rotatoria para temperatura y scroll vertical */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            (event.source and InputDevice.SOURCE_ROTARY_ENCODER) != 0
        ) {
            val axis = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (axis > 0.1f) {
                adjustTemperature(+0.5f)
                return true
            } else if (axis < -0.1f) {
                adjustTemperature(-0.5f)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
