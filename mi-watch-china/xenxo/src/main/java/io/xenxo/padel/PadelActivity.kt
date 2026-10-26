package io.xenxo.padel

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import com.google.android.wearable.compat.WearableActivityController
import io.xenxo.R
import io.xenxo.padel.PadelMatch.Config
import io.xenxo.padel.PadelMatch.Event
import io.xenxo.padel.PadelMatch.Team
import io.xenxo.padel.PadelMenu.Item

/**
 * Marcador de pádel a pantalla completa (sección :padel de xenxo).
 *
 * Batería: no mantiene la pantalla encendida. Con el modo ambiente de Wear OS activado
 * (WearableActivityController, clase del propio reloj), al apagarse la pantalla el marcador se queda
 * visible en contorno gris en vez de volver a la esfera; un toque despierta. Sin sensores ni temporizadores:
 * en ambiente solo se repinta una vez por minuto para mover unos px los trazos (anti-quemado).
 */
class PadelActivity : Activity() {

    private lateinit var store: PadelStore
    private lateinit var board: ScoreboardView
    private lateinit var menu: PadelMenu
    private var match: PadelMatch? = null
    private var ambient: WearableActivityController? = null

    // Opciones del partido nuevo (por defecto: ventaja, tercer set normal, sacamos nosotros)
    private var golden = false
    private var superTb = false
    private var firstServer = Team.US

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PadelStore(this)
        board = ScoreboardView(this).apply {
            onPoint = { t -> point(t) }
            onUndo = { undo() }
            onMenu = { pauseMenu() }
        }
        menu = PadelMenu(this)
        setContentView(FrameLayout(this).apply {
            addView(board, FrameLayout.LayoutParams(-1, -1))
            addView(menu, FrameLayout.LayoutParams(-1, -1))
        })
        setupAmbient()

        match = store.loadCurrent()
        if (match == null) setupMenu() else showBoard()
    }

    // ---------- modo ambiente ----------

    private fun setupAmbient() {
        ambient = runCatching {
            WearableActivityController(TAG, this, object : WearableActivityController.AmbientCallback() {
                override fun onEnterAmbient(details: Bundle?) {
                    board.lowBit = details?.getBoolean(WearableActivityController.EXTRA_LOWBIT_AMBIENT) ?: false
                    board.burnIn = details?.getBoolean(WearableActivityController.EXTRA_BURN_IN_PROTECTION) ?: false
                    board.ambient = true
                    // screencap no captura la capa de ambiente en este reloj: esta línea es la forma de comprobarlo
                    Log.i(TAG, "ambiente ON lowBit=${board.lowBit} burnIn=${board.burnIn}")
                }
                override fun onUpdateAmbient() {
                    board.shift = (board.shift + 1) % 5
                    board.invalidate()
                }
                override fun onExitAmbient() {
                    board.shift = 0
                    board.ambient = false
                    Log.i(TAG, "ambiente OFF")
                }
            }).also { it.onCreate(); it.setAmbientEnabled() }
        }.onFailure { Log.w(TAG, "Sin modo ambiente: ${it.message}") }.getOrNull()
    }

    override fun onResume() { super.onResume(); ambient?.onResume() }
    override fun onPause() { ambient?.onPause(); super.onPause() }
    override fun onStop() { ambient?.onStop(); super.onStop() }
    override fun onDestroy() { ambient?.onDestroy(); super.onDestroy() }

    // ---------- partido ----------

    private fun point(t: Team) {
        val m = match ?: return
        if (m.winner != null) return
        when (m.point(t)) {
            Event.GAME -> vibrate(longArrayOf(0, 60))
            Event.SET -> vibrate(longArrayOf(0, 120, 120, 120))
            Event.MATCH -> vibrate(longArrayOf(0, 300, 150, 300, 150, 300))
            Event.POINT -> {}
        }
        store.saveCurrent(m)
        board.invalidate()
        if (m.winner != null) endMenu()
    }

    private fun undo() {
        val m = match ?: return
        if (!m.undo()) return
        vibrate(longArrayOf(0, 25))
        store.saveCurrent(m)
        if (menu.isShowing) { menu.hide(); board.requestFocus() } // deshacer el punto final vuelve al marcador
        board.invalidate()
    }

    private fun showBoard() {
        menu.hide()
        board.match = match
        board.requestFocus()
    }

    // ---------- menús ----------

    private fun yesNo(b: Boolean) = getString(if (b) R.string.yes else R.string.no)

    private fun setupMenu(keepScroll: Boolean = false) {
        menu.show(getString(R.string.padel_new_match), listOf(
            Item(getString(R.string.padel_opt_golden, yesNo(golden))) { golden = !golden; setupMenu(true) },
            Item(getString(R.string.padel_opt_third, getString(if (superTb) R.string.padel_third_stb else R.string.padel_third_set))) {
                superTb = !superTb; setupMenu(true)
            },
            Item(getString(R.string.padel_opt_server, getString(if (firstServer == Team.US) R.string.padel_us else R.string.padel_them))) {
                firstServer = firstServer.other; setupMenu(true)
            },
            Item(getString(R.string.padel_start), primary = true) {
                val cfg = Config(golden, superTb, firstServer)
                store.startNew(cfg)
                match = PadelMatch(cfg)
                showBoard()
            },
            // "Pádel xenxo" es su propia app en el panal: el historial y el CSV se abren desde aquí
            Item(getString(R.string.padel_history_open)) {
                startActivity(android.content.Intent(this, io.xenxo.PadelHistoryActivity::class.java))
            },
        ), keepScroll = keepScroll)
    }

    private fun pauseMenu() {
        val m = match ?: return
        menu.show(getString(R.string.padel_paused), listOf(
            Item(getString(R.string.padel_continue), primary = true) { showBoard() },
            Item(getString(R.string.padel_undo)) { undo(); showBoard() },
            Item(getString(R.string.padel_finish_save)) { store.finish(m); match = null; finish() },
            Item(getString(R.string.padel_discard)) { confirmDiscard() },
        ), big = m.resultText(withCurrent = true).ifEmpty { null })
    }

    private fun confirmDiscard() {
        menu.show(getString(R.string.padel_discard_q), listOf(
            Item(getString(R.string.padel_discard_yes)) { store.clearCurrent(); match = null; setupMenu() },
            Item(getString(R.string.padel_discard_no), primary = true) { showBoard() },
        ))
    }

    private fun endMenu() {
        val m = match ?: return
        val minutes = ((System.currentTimeMillis() - store.currentStart) / 60_000).toInt()
        val title = getString(if (m.winner == Team.US) R.string.padel_we_won else R.string.padel_they_won)
        menu.show(title, listOf(
            Item(getString(R.string.padel_save), primary = true) { store.finish(m); match = null; setupMenu() },
            Item(getString(R.string.padel_undo_last)) { undo() },
        ), big = m.resultText(), message = getString(R.string.padel_duration, minutes / 60, minutes % 60))
    }

    /**
     * Estado para depurar sin capturas (screencap no ve la capa de ambiente en este reloj):
     *   adb shell dumpsys activity io.xenxo/.padel.PadelActivity | grep xenxo-padel
     */
    override fun dump(prefix: String, fd: java.io.FileDescriptor?, writer: java.io.PrintWriter, args: Array<out String>?) {
        super.dump(prefix, fd, writer, args)
        val m = match
        writer.println("${prefix}xenxo-padel ambient=${board.ambient} lowBit=${board.lowBit} burnIn=${board.burnIn} " +
            "controller=${ambient != null} points=${m?.points?.size} score=${m?.resultText(true)} " +
            "pts=${m?.let { "${it.pointLabel(Team.US)}-${it.pointLabel(Team.THEM)}" }}")
    }

    private var downX = 0f
    private var downY = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val d = resources.displayMetrics.density
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (menu.isShowing && dx > 50f * d && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f) {
                    onBackPressed()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)) {
            if (menu.isShowing) {
                if (menu.onGenericMotionEvent(ev)) return true
            } else {
                if (board.onGenericMotionEvent(ev)) return true
            }
        }
        return super.onGenericMotionEvent(ev)
    }

    @Deprecated("API antigua, suficiente en Android 9")
    override fun onBackPressed() {
        // Atrás desde un menú vuelve al marcador; desde el marcador sale (el partido queda guardado)
        if (menu.isShowing && match != null && match?.winner == null) showBoard() else super.onBackPressed()
    }

    private fun vibrate(pattern: LongArray) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        @Suppress("DEPRECATION")
        if (v.hasVibrator()) v.vibrate(pattern, -1)
    }

    private companion object { const val TAG = "xenxo-padel" }
}
