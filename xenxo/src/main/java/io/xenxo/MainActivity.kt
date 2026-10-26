package io.xenxo

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.xenxo.gym.GymView
import io.xenxo.padel.PadelView
import io.xenxo.power.PowerView

/**
 * Actividad principal de la app xenxo.
 * Integra las secciones :power (Batería), :gym (Gimnasio) y :padel (Pádel; el marcador va en PadelActivity).
 * En el panal cada sección es su propia "app" (Energía xenxo, Gym xenxo…): subclases con [fixedSection]
 * (ver Sections.kt) que abren directamente esa sección, sin barra de pestañas.
 */
open class MainActivity : Activity() {

    enum class Section { POWER, GYM, PADEL, MUSIC, HEALTH }

    /** Sección fija de la "app" (sin pestañas), o null para el modo con pestañas. */
    protected open val fixedSection: Section? = null

    private var currentSection = Section.POWER
    private lateinit var root: LinearLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var container: FrameLayout
    private lateinit var tabPower: TextView
    private lateinit var tabGym: TextView
    private lateinit var tabPadel: TextView

    private var powerView: PowerView? = null
    private var gymView: GymView? = null
    private var padelView: PadelView? = null
    private var musicView: io.xenxo.music.MusicView? = null
    private var healthView: io.xenxo.health.HealthView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // Barra de pestañas superior elegante y compacta
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
        }

        tabPower = tabButton("⚡ Energía") { switchSection(Section.POWER) }
        tabGym = tabButton("🏋️ Gym") { switchSection(Section.GYM) }
        tabPadel = tabButton("🎾 Pádel") { switchSection(Section.PADEL) }

        tabBar.addView(tabPower, LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f))
        tabBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((6 * d).toInt(), 1) })
        tabBar.addView(tabGym, LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f))
        tabBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((6 * d).toInt(), 1) })
        tabBar.addView(tabPadel, LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f))

        root.addView(tabBar)

        container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(-1, -1))

        setContentView(root)
        fixedSection?.let { tabBar.visibility = View.GONE }
        switchSection(fixedSection ?: Section.POWER)
    }

    fun switchSection(section: Section) {
        powerView?.onPause()
        healthView?.onPause()
        musicView?.onPause()

        currentSection = section
        container.removeAllViews()

        tabPower.setTextColor(if (section == Section.POWER) Color.BLACK else io.xenxo.ui.XTheme.TEXT_SECONDARY)
        tabPower.background = tabBg(section == Section.POWER)

        tabGym.setTextColor(if (section == Section.GYM) Color.BLACK else io.xenxo.ui.XTheme.TEXT_SECONDARY)
        tabGym.background = tabBg(section == Section.GYM)

        tabPadel.setTextColor(if (section == Section.PADEL) Color.BLACK else io.xenxo.ui.XTheme.TEXT_SECONDARY)
        tabPadel.background = tabBg(section == Section.PADEL)

        when (section) {
            Section.POWER -> {
                if (powerView == null) powerView = PowerView(this)
                container.addView(powerView)
                powerView?.onResume()
            }
            Section.GYM -> {
                powerView?.onPause()
                if (gymView == null) gymView = GymView(this)
                container.addView(gymView)
                gymView?.refresh()
            }
            Section.PADEL -> {
                powerView?.onPause()
                if (padelView == null) padelView = PadelView(this)
                container.addView(padelView)
                padelView?.refresh()
            }
            Section.MUSIC -> {
                powerView?.onPause()
                if (musicView == null) musicView = io.xenxo.music.MusicView(this)
                container.addView(musicView)
                musicView?.refresh()
            }
            Section.HEALTH -> {
                powerView?.onPause()
                if (healthView == null) healthView = io.xenxo.health.HealthView(this)
                container.addView(healthView)
                healthView?.onResume()
            }
        }
    }

    private fun tabButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 11f
        maxLines = 1 // con tres pestañas, "⚡ Energía" a 12 sp se partía en dos líneas
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun tabBg(active: Boolean) = GradientDrawable().apply {
        val d = resources.displayMetrics.density
        cornerRadius = 16 * d
        if (active) {
            setColor(io.xenxo.ui.XTheme.GREEN)
        } else {
            setColor(io.xenxo.ui.XTheme.CARD_BG)
            setStroke((1 * d).toInt(), io.xenxo.ui.XTheme.CARD_STROKE)
        }
    }

    fun setTabBarVisible(visible: Boolean) {
        tabBar.visibility = if (visible && fixedSection == null) View.VISIBLE else View.GONE
    }

    private var downX = 0f
    private var downY = 0f
    private var gestureIntercepted = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val d = resources.displayMetrics.density
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                gestureIntercepted = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)
                // Si el gesto es claramente horizontal desde el inicio, cancelamos el toque en los hijos
                // (evita que el ScrollView vibre verticalmente o que se queden botones pulsados)
                if (!gestureIntercepted && absDx > 24f * d && absDx > absDy * 1.4f) {
                    gestureIntercepted = true
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                if (gestureIntercepted) {
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)
                val minDistance = 45f * d

                if (absDx > minDistance && absDx > absDy * 1.3f) {
                    if (dx > 0) {
                        // Gesto hacia la DERECHA (ATRÁS)
                        if (downX < 28f * d) {
                            // Deslizar desde el borde izquierdo (bezel / <28dp): siempre vuelve atrás (cerrar app o diálogo)
                            vibrateTick()
                            onBackPressed()
                            return true
                        } else {
                            // Deslizar en el cuerpo de la pantalla
                            if (currentSection == Section.GYM) {
                                if (gymView?.hasOverlay() == true) {
                                    vibrateTick()
                                    gymView?.handleBack()
                                    return true
                                }
                                vibrateTick()
                                gymView?.prevDay()
                                return true
                            } else if (currentSection == Section.MUSIC) {
                                if (musicView?.isShowingLibrary() == true) {
                                    vibrateTick()
                                    musicView?.showPlayer()
                                    return true
                                } else {
                                    vibrateTick()
                                    onBackPressed()
                                    return true
                                }
                            } else if (fixedSection == null) {
                                // Pestañas: ir a la sección previa
                                if (currentSection == Section.PADEL) {
                                    vibrateTick()
                                    switchSection(Section.GYM)
                                    return true
                                } else if (currentSection == Section.GYM) {
                                    vibrateTick()
                                    switchSection(Section.POWER)
                                    return true
                                }
                            } else {
                                // Apps individuales (Energía xenxo, Pádel historial): deslizar a la derecha vuelve
                                vibrateTick()
                                onBackPressed()
                                return true
                            }
                        }
                    } else {
                        // Gesto hacia la IZQUIERDA (DELANTE)
                        if (currentSection == Section.MUSIC) {
                            if (musicView?.isShowingLibrary() != true) {
                                vibrateTick()
                                musicView?.showLibrary()
                                return true
                            }
                        } else if (currentSection == Section.GYM) {
                            if (gymView?.hasOverlay() != true) {
                                vibrateTick()
                                gymView?.nextDay()
                                return true
                            }
                        } else if (fixedSection == null) {
                            // Pestañas: ir a la sección siguiente
                            if (currentSection == Section.POWER) {
                                vibrateTick()
                                switchSection(Section.GYM)
                                return true
                            } else if (currentSection == Section.GYM) {
                                vibrateTick()
                                switchSection(Section.PADEL)
                                return true
                            }
                        }
                    }
                }
                if (gestureIntercepted) {
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val handled = when (currentSection) {
                Section.POWER -> powerView?.onGenericMotionEvent(ev) ?: false
                Section.GYM -> gymView?.onGenericMotionEvent(ev) ?: false
                Section.PADEL -> padelView?.onGenericMotionEvent(ev) ?: false
                Section.MUSIC -> musicView?.onGenericMotionEvent(ev) ?: false
                Section.HEALTH -> healthView?.onGenericMotionEvent(ev) ?: false
            }
            if (handled) return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun vibrateTick() {
        window.decorView.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    override fun onBackPressed() {
        if (gymView?.handleBack() == true) {
            return
        }
        if (musicView?.handleBack() == true) {
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        io.xenxo.sync.XenxoSync.syncOnOpen(this)
        if (currentSection == Section.POWER) powerView?.onResume()
        if (currentSection == Section.PADEL) padelView?.refresh() // al volver del marcador
        if (currentSection == Section.MUSIC) musicView?.refresh()
        if (currentSection == Section.HEALTH) healthView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (currentSection == Section.POWER) powerView?.onPause()
        if (currentSection == Section.HEALTH) healthView?.onPause()
        if (currentSection == Section.MUSIC) musicView?.onPause()
    }
}
