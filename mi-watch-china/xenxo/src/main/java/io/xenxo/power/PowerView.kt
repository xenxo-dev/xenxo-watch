package io.xenxo.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.xenxo.R
import io.xenxo.ui.XTheme

/**
 * Vista principal de Batería y Perfiles de Energía (:power).
 * Estética inspirada en openGym (Apple/iOS Dark) con tarjetas #1C1C1E y acento verde #30D158.
 * Diseñada para pantalla cuadrada 368×448 AMOLED (negro puro),
 * táctil con botones grandes y soporte para corona giratoria.
 */
class PowerView(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val profiles = PowerProfiles(context)
    private val batteryLog = BatteryLog(context)

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
        setPadding((12 * d).toInt(), (20 * d).toInt(), (12 * d).toInt(), (40 * d).toInt())
    }

    private var currentLevel = 100
    private var currentTemp = 32.0f
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent ?: return
            val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            currentLevel = if (scale > 0) (lvl * 100) / scale else lvl
            currentTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            batteryLog.record(currentLevel, currentTemp, isCharging)
            refresh()
        }
    }

    init {
        setBackgroundColor(XTheme.BG_BLACK)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content)
        addView(scroll, LayoutParams(-1, -1))
    }

    fun onResume() {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        refresh()
    }

    fun onPause() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
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

    fun refresh() {
        content.removeAllViews()

        // 1. Título de marca
        content.addView(XTheme.brandTitle(context, "XENXOPOWER"))

        // 2. Nivel de batería grande
        val drainRate = batteryLog.calculateDrainRatePerHour(3)
        val endTime = drainRate?.let { batteryLog.estimateEndTime(currentLevel, it) }

        val levelColor = when {
            isCharging -> XTheme.GREEN
            currentLevel <= 15 -> XTheme.RED
            currentLevel <= 30 -> XTheme.AMBER
            else -> XTheme.GREEN
        }

        content.addView(TextView(context).apply {
            text = "$currentLevel%"
            setTextColor(levelColor)
            textSize = 54f
            gravity = Gravity.CENTER
        })

        // Subtítulo con estado (Cargando / Temp / Gasto)
        val statusText = buildString {
            if (isCharging) append("⚡ Cargando · ")
            append(String.format("%.1f °C", currentTemp))
            if (drainRate != null && drainRate > 0) {
                append(String.format(" · -%.1f%%/h", drainRate))
            }
        }
        content.addView(TextView(context).apply {
            text = statusText
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (2 * d).toInt(), 0, (4 * d).toInt())
        })

        if (endTime != null && !isCharging) {
            content.addView(TextView(context).apply {
                text = "Llegas hasta las $endTime"
                setTextColor(XTheme.TEXT_TERTIARY)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (14 * d).toInt())
            })
        } else {
            content.addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, (10 * d).toInt())
            })
        }

        // 3. Aviso de permisos ADB si faltan
        if (!profiles.hasPermissions) {
            val alertCard = XTheme.card(context, normalColor = XTheme.RED_SUBTLE, strokeColor = XTheme.RED) {
                Toast.makeText(context, "Concede permisos por ADB (ver HANDOFF.md)", Toast.LENGTH_LONG).show()
            }
            alertCard.addView(TextView(context).apply {
                text = "⚠️ " + context.getString(R.string.perm_missing)
                setTextColor(XTheme.RED)
                textSize = 13f
            })
            content.addView(alertCard)
        }

        // 4. Selector de Perfiles
        content.addView(XTheme.sectionHeader(context, "PERFILES DE ENERGÍA"))

        val active = profiles.currentProfile

        content.addView(XTheme.iconRowCard(
            context = context,
            iconGlyph = "☀️",
            iconBg = XTheme.AMBER,
            title = context.getString(R.string.profile_day),
            subtitle = "Brillo 70 · Pantalla 10 s · Muñeca",
            isSelected = active == PowerProfiles.Profile.DAY
        ) {
            if (profiles.applyProfile(PowerProfiles.Profile.DAY)) {
                Toast.makeText(context, "Perfil Día activado", Toast.LENGTH_SHORT).show()
                refresh()
            }
        })

        content.addView(XTheme.iconRowCard(
            context = context,
            iconGlyph = "🌙",
            iconBg = XTheme.INDIGO,
            title = context.getString(R.string.profile_night),
            subtitle = "Brillo mín · Pantalla 5 s · Sin muñeca",
            isSelected = active == PowerProfiles.Profile.NIGHT
        ) {
            if (profiles.applyProfile(PowerProfiles.Profile.NIGHT)) {
                toastWithAodHint("Perfil Noche activado")
                refresh()
            }
        })

        content.addView(XTheme.iconRowCard(
            context = context,
            iconGlyph = "⚡",
            iconBg = XTheme.GREEN,
            title = context.getString(R.string.profile_workout),
            subtitle = "Brillo 150 · Pantalla 30 s · Muñeca",
            isSelected = active == PowerProfiles.Profile.WORKOUT
        ) {
            if (profiles.applyProfile(PowerProfiles.Profile.WORKOUT)) {
                Toast.makeText(context, "Perfil Entreno activado", Toast.LENGTH_SHORT).show()
                refresh()
            }
        })

        content.addView(XTheme.iconRowCard(
            context = context,
            iconGlyph = "🔋",
            iconBg = XTheme.AMBER,
            title = context.getString(R.string.profile_saving),
            subtitle = "Brillo mín · Pantalla 5 s · Sin muñeca",
            isSelected = active == PowerProfiles.Profile.SAVING
        ) {
            if (profiles.applyProfile(PowerProfiles.Profile.SAVING)) {
                toastWithAodHint("Ahorro extremo activado")
                refresh()
            }
        })

        // Pantalla siempre activa: solo se puede leer; el interruptor real está en Ajustes → Pantalla
        val aod = profiles.isAodEnabled
        if (aod != null) {
            content.addView(XTheme.iconRowCard(
                context = context,
                iconGlyph = "🌓",
                iconBg = XTheme.SURFACE_BTN,
                title = "Pantalla siempre activa",
                subtitle = "Se cambia en Ajustes ›",
                trailingText = if (aod) "Sí" else "No",
                trailingColor = if (aod) XTheme.GREEN else XTheme.TEXT_SECONDARY
            ) {
                runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            })
        }

        content.addView(XTheme.iconRowCard(
            context = context,
            iconGlyph = "🔄",
            iconBg = XTheme.SURFACE_BTN,
            title = context.getString(R.string.profile_restore),
            subtitle = "Restaurar ajustes originales",
            isSelected = active == PowerProfiles.Profile.CUSTOM
        ) {
            if (profiles.restoreOriginals()) {
                Toast.makeText(context, "Ajustes restaurados", Toast.LENGTH_SHORT).show()
                refresh()
            }
        })

        // 5. Estado de ADB por Wi-Fi
        content.addView(XTheme.sectionHeader(context, "CONECTIVIDAD"))
        val adbPort = profiles.adbWifiPort
        if (adbPort > 0 && profiles.isWifiEnabled) {
            content.addView(adbWarningCard(adbPort))
        } else {
            content.addView(XTheme.iconRowCard(
                context = context,
                iconGlyph = "📶",
                iconBg = if (profiles.isWifiEnabled) XTheme.BLUE else XTheme.SURFACE_BTN,
                title = "Wi-Fi",
                trailingText = if (profiles.isWifiEnabled) "Conectado" else "Apagado",
                trailingColor = if (profiles.isWifiEnabled) XTheme.GREEN else XTheme.TEXT_SECONDARY
            ) {
                val newState = !profiles.isWifiEnabled
                profiles.setWifiEnabled(newState)
                refresh()
            })
        }

        // 6. Sincronización con la app del móvil (xenxoHub)
        content.addView(XTheme.sectionHeader(context, "MÓVIL"))
        content.addView(XTheme.iconRowCard(
            context = context,
            iconGlyph = "🔄",
            iconBg = XTheme.BLUE,
            title = "xenxoHub móvil",
            subtitle = syncStatusSubtitle(),
            trailingText = "Sincronizar",
            trailingColor = XTheme.GREEN
        ) {
            io.xenxo.sync.XenxoSync.sync(context, force = true)
            Toast.makeText(context, "Sincronizando con el móvil…", Toast.LENGTH_SHORT).show()
            postDelayed({ refresh() }, 2500)
        })

        // 7. Exportar CSV
        content.addView(XTheme.sectionHeader(context, "HERRAMIENTAS"))
        content.addView(XTheme.pillButton(
            context = context,
            text = "📊  " + context.getString(R.string.export_csv),
            primary = false
        ) {
            val f = batteryLog.exportCsv()
            if (f != null) {
                Toast.makeText(context, context.getString(R.string.csv_exported), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error al exportar", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun syncStatusSubtitle(): String {
        val st = io.xenxo.sync.XenxoSync.status(context)
        val lastAck = st.getLong("lastAck", 0)
        val ago = if (lastAck == 0L) "nunca" else {
            val min = (System.currentTimeMillis() - lastAck) / 60_000
            when { min < 1 -> "ahora mismo"; min < 60 -> "hace $min min"; min < 1440 -> "hace ${min / 60} h"; else -> "hace ${min / 1440} d" }
        }
        val problem = when (st.getString("state", null)) {
            "offline" -> " · sin conexión"
            "error" -> " · error"
            else -> ""
        }
        return "Sincronizado $ago$problem"
    }

    /** En Noche y Ahorro, recuerda que el AOD (si está encendido) solo se apaga a mano en Ajustes. */
    private fun toastWithAodHint(msg: String) {
        val text = if (profiles.isAodEnabled == true) "$msg · apaga «Pantalla siempre activa» en Ajustes para ahorrar más" else msg
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private fun adbWarningCard(port: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        background = XTheme.cardStateList(d, radiusDp = 14f, normalColor = XTheme.AMBER_SUBTLE, strokeColor = XTheme.AMBER)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = (8 * d).toInt()
        }

        addView(TextView(context).apply {
            text = "⚡ ADB Wi-Fi ACTIVO (puerto $port)"
            setTextColor(XTheme.AMBER)
            textSize = 13f
        })

        addView(TextView(context).apply {
            text = "Gasta ~40-60 mAh/h en reposo. Apágalo al terminar."
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 11f
            setPadding(0, (3 * d).toInt(), 0, (8 * d).toInt())
        })

        addView(XTheme.pillButton(context, "Apagar Wi-Fi ahora", primary = false, minHeightDp = 36f, textSizeSp = 12f) {
            profiles.setWifiEnabled(false)
            Toast.makeText(context, "Wi-Fi apagado. ADB desconectado.", Toast.LENGTH_SHORT).show()
            refresh()
        }.apply {
            background = XTheme.roundRect(XTheme.RED_SUBTLE, 18f, XTheme.RED, 1f, d)
            setTextColor(XTheme.RED)
        })
    }
}
