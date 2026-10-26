package io.xenxo.health

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.xenxo.sync.XenxoSync
import io.xenxo.ui.XTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HealthView(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val manager = HealthManager(context)
    private val db = HealthDb(context)

    private val scroll = object : ScrollView(context) {
        override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                smoothScrollBy(0, (-ev.getAxisValue(MotionEvent.AXIS_SCROLL) * 60 * d).toInt())
                return true
            }
            return super.onGenericMotionEvent(ev)
        }
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt(), (40 * d).toInt())
    }

    // Vistas dinámicas
    private lateinit var tvStepsNum: TextView
    private lateinit var tvStepsPct: TextView
    private lateinit var vProgressBar: View
    private lateinit var tvKcal: TextView
    private lateinit var tvKm: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var tvHrStatus: TextView
    private lateinit var btnMeasureHr: TextView
    private lateinit var tvSyncStatus: TextView

    private val dateFmt = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))

    init {
        setBackgroundColor(XTheme.BG_BLACK)

        setupUI()
        scroll.addView(content)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        manager.onStepsChanged = { _, _, _ -> refreshData() }
        manager.onHeartRateUpdate = { bpm, status ->
            if (bpm > 0) {
                tvHeartRate.text = "$bpm"
                tvHrStatus.text = "Pulso detectado"
            } else {
                tvHrStatus.text = status
            }
            btnMeasureHr.text = if (manager.isMeasuringHr) "Detener" else "Medir pulso"
            btnMeasureHr.background = pillBg(if (manager.isMeasuringHr) 0xFF8E8E93.toInt() else 0xFFFF2D55.toInt())

            val act = context as? android.app.Activity
            if (manager.isMeasuringHr) {
                act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun setupUI() {
        // Título superior
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        val title = TextView(context).apply {
            text = "Salud xenxo"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(context).apply {
            val fecha = dateFmt.format(Date())
            text = fecha.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() }
            textSize = 12f
            setTextColor(XTheme.TEXT_SECONDARY)
        }
        header.addView(title)
        header.addView(subtitle)
        content.addView(header)

        // 1. Tarjeta Pasos
        content.addView(createStepsCard())
        content.addView(spacer(10))

        // 2. Fila Dual: KCAL y KM
        content.addView(createKcalKmRow())
        content.addView(spacer(10))

        // 3. Tarjeta Frecuencia Cardíaca
        content.addView(createHeartRateCard())
        content.addView(spacer(12))

        // 4. Botón de Sincronización con openGym
        content.addView(createSyncCard())
    }

    private fun createStepsCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding((14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt())
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(context).apply {
            text = "PASOS HOY"
            textSize = 11f
            setTextColor(0xFFFFB300.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        tvStepsPct = TextView(context).apply {
            textSize = 11f
            setTextColor(XTheme.TEXT_SECONDARY)
            gravity = Gravity.END
        }
        topRow.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        topRow.addView(tvStepsPct)
        card.addView(topRow)

        tvStepsNum = TextView(context).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setPadding(0, (4 * d).toInt(), 0, (6 * d).toInt())
        }
        card.addView(tvStepsNum)

        // Barra de progreso
        val barBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 3 * d
                setColor(0xFF2C2C2E.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6 * d).toInt())
        }
        vProgressBar = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 3 * d
                setColor(0xFFFFB300.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        barBg.addView(vProgressBar)
        card.addView(barBg)

        return card
    }

    private fun createKcalKmRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Tarjeta KCAL
        val cardKcal = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        val lblKcal = TextView(context).apply {
            text = "CALORÍAS"
            textSize = 10f
            setTextColor(0xFFFF7043.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        tvKcal = TextView(context).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val subKcal = TextView(context).apply {
            text = "kcal activas"
            textSize = 10f
            setTextColor(XTheme.TEXT_TERTIARY)
        }
        cardKcal.addView(lblKcal)
        cardKcal.addView(tvKcal)
        cardKcal.addView(subKcal)

        // Tarjeta KM
        val cardKm = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        val lblKm = TextView(context).apply {
            text = "DISTANCIA"
            textSize = 10f
            setTextColor(0xFF26C6DA.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        tvKm = TextView(context).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val subKm = TextView(context).apply {
            text = "km recorridos"
            textSize = 10f
            setTextColor(XTheme.TEXT_TERTIARY)
        }
        cardKm.addView(lblKm)
        cardKm.addView(tvKm)
        cardKm.addView(subKm)

        row.addView(cardKcal, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams((8 * d).toInt(), 1) })
        row.addView(cardKm, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun createHeartRateCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding((14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt())
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(context).apply {
            text = "❤️ FRECUENCIA CARDÍACA"
            textSize = 11f
            setTextColor(0xFFFF2D55.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        topRow.addView(label)
        card.addView(topRow)

        val middleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
        }
        tvHeartRate = TextView(context).apply {
            text = "--"
            textSize = 32f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val ppmUnit = TextView(context).apply {
            text = " PPM"
            textSize = 14f
            setTextColor(XTheme.TEXT_SECONDARY)
            setPadding(0, 0, 0, (6 * d).toInt())
        }
        middleRow.addView(tvHeartRate)
        middleRow.addView(ppmUnit)
        card.addView(middleRow)

        tvHrStatus = TextView(context).apply {
            text = "Sensor óptico de muñeca"
            textSize = 11f
            setTextColor(XTheme.TEXT_SECONDARY)
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        card.addView(tvHrStatus)

        btnMeasureHr = TextView(context).apply {
            text = "Medir pulso"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillBg(0xFFFF2D55.toInt())
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            setOnClickListener {
                if (manager.isMeasuringHr) {
                    manager.stopHeartRateMeasurement()
                } else {
                    manager.startHeartRateMeasurement()
                }
            }
        }
        card.addView(btnMeasureHr)

        return card
    }

    private fun createSyncCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        }

        val btnSync = TextView(context).apply {
            text = "Sincronizar con el móvil"
            textSize = 13f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillBg(XTheme.GREEN)
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            setOnClickListener {
                XenxoSync.sync(context, force = true)
                Toast.makeText(context, "Sincronizando con openGym…", Toast.LENGTH_SHORT).show()
                refreshSyncStatus()
            }
        }
        card.addView(btnSync)

        tvSyncStatus = TextView(context).apply {
            textSize = 11f
            setTextColor(XTheme.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, 0)
        }
        card.addView(tvSyncStatus)
        refreshSyncStatus()

        return card
    }

    fun onResume() {
        manager.startListeningSteps()
        refreshData()
        refreshSyncStatus()
    }

    fun onPause() {
        manager.stopListeningSteps()
        manager.stopHeartRateMeasurement()
        (context as? android.app.Activity)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onPause()
    }

    private fun refreshData() {
        val steps = manager.currentSteps
        val kcal = manager.currentKcal
        val km = manager.currentKm

        tvStepsNum.text = String.format(Locale.ROOT, "%,d", steps).replace(',', '.')
        val goal = io.xenxo.profile.UserProfile.get(context).stepGoal.coerceAtLeast(1000)
        val pct = (steps * 100 / goal).coerceAtMost(100)
        tvStepsPct.text = "$pct %"

        // Ajustar ancho de la barra de progreso
        post {
            val totalW = (vProgressBar.parent as View).width
            val w = (totalW * (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)).toInt()
            val lp = vProgressBar.layoutParams
            lp.width = w.coerceAtLeast((6 * d).toInt())
            vProgressBar.layoutParams = lp
        }

        tvKcal.text = "$kcal"
        tvKm.text = String.format(Locale.ROOT, "%.1f", km).replace('.', ',')

        val todayRecord = db.getDaily(manager.todayStr())
        if (todayRecord != null && todayRecord.lastBpm > 0 && !manager.isMeasuringHr) {
            tvHeartRate.text = "${todayRecord.lastBpm}"
            tvHrStatus.text = "Última medición registrada"
        }
    }

    private fun refreshSyncStatus() {
        val sp = XenxoSync.status(context)
        val lastPush = sp.getLong("lastPush", 0)
        if (lastPush > 0) {
            val min = (System.currentTimeMillis() - lastPush) / 60000
            tvSyncStatus.text = if (min <= 1) "Sincronizado hace un momento" else "Sincronizado hace $min min"
        } else {
            tvSyncStatus.text = "Bluetooth con el móvil listo"
        }
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val scrollDelta = (-delta * 40 * d).toInt()
            scroll.scrollBy(0, scrollDelta)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun cardBg() = GradientDrawable().apply {
        cornerRadius = 14 * d
        setColor(XTheme.CARD_BG)
        setStroke((1 * d).toInt(), XTheme.CARD_STROKE)
    }

    private fun pillBg(color: Int) = GradientDrawable().apply {
        cornerRadius = 20 * d
        setColor(color)
    }

    private fun spacer(dp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, (dp * d).toInt())
    }
}
