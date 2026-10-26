package io.xenxo.padel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import io.xenxo.R
import io.xenxo.padel.PadelMatch.Team

/**
 * Marcador de pádel pintado a mano en coordenadas de diseño 368×448 (se escala si la pantalla difiere).
 * Mitad de arriba = "Ellos", mitad de abajo = "Nosotros". Un toque en una mitad = punto para ese equipo;
 * corona hacia atrás = deshacer; mantener pulsado = menú.
 *
 * Modo ambiente: mismos datos en contorno gris, sin colores ni rellenos, sin antialias en pantallas
 * low-bit y desplazado unos px cada minuto si el reloj pide protección anti-quemado.
 */
class ScoreboardView(context: Context) : View(context) {

    var match: PadelMatch? = null
        set(v) { field = v; invalidate() }

    var onPoint: ((Team) -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    var ambient = false
        set(v) { field = v; applyAntiAlias(); invalidate() }
    var lowBit = false
        set(v) { field = v; applyAntiAlias() }
    var burnIn = false
    /** Desplazamiento anti-quemado en ambiente: lo cambia la actividad una vez por minuto. */
    var shift = 0

    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, face: Typeface = Typeface.DEFAULT, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; letterSpacing = spacing }

    private val label = paint(14f, spacing = 0.15f)
    private val points = paint(108f, light).apply { textAlign = Paint.Align.CENTER }
    private val games = paint(64f, medium).apply { textAlign = Paint.Align.CENTER }
    private val setsPaint = paint(18f, medium, 0.1f).apply { textAlign = Paint.Align.RIGHT }
    private val status = paint(12f, medium, 0.12f).apply { textAlign = Paint.Align.CENTER }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint().apply { color = 0xFF1E1E1E.toInt(); strokeWidth = 1.5f }
    private val statusBg = Paint().apply { color = Color.BLACK }
    private val allPaints = listOf(label, points, games, setsPaint, status, dot)

    private val us = context.getString(R.string.padel_us).uppercase()
    private val them = context.getString(R.string.padel_them).uppercase()

    private var lastUndo = 0L

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (ambient) return true // el primer toque en ambiente solo despierta
            val tapX = e.x * (W / width)
            val tapY = e.y * (H / height)
            if (tapY >= 385f) {
                if (tapX <= 110f) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                    onUndo?.invoke()
                    return true
                }
                if (tapX >= 258f) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                    onMenu?.invoke()
                    return true
                }
            }
            onPoint?.invoke(if (e.y < height / 2f) Team.THEM else Team.US)
            return true
        }
        override fun onLongPress(e: MotionEvent) { if (!ambient) onMenu?.invoke() }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onTouchEvent(event: MotionEvent) = gestures.onTouchEvent(event) || super.onTouchEvent(event)

    /** Corona hacia atrás = deshacer, como mucho una vez cada 350 ms (un giro manda muchos eventos). */
    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL) // mismo signo que :gym (adelante = positivo)
            val now = ev.eventTime
            if (delta < 0 && now - lastUndo > 350) { lastUndo = now; onUndo?.invoke() }
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    override fun onDraw(c: Canvas) {
        val m = match ?: return
        c.save()
        c.scale(width / W, height / H)
        if (ambient && burnIn) c.translate(shift * 2f, (shift % 3 - 1) * 2f)
        drawHalf(c, m, Team.THEM, 0f)
        drawHalf(c, m, Team.US, H / 2)
        drawMiddle(c, m)
        drawBottomControls(c)
        c.restore()
    }

    private val controlBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1E.toInt() }
    private val controlStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2C2C2E.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val controlText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8E8E93.toInt()
        textSize = 15f
        textAlign = Paint.Align.CENTER
    }
    private val undoRect = RectF(24f, 396f, 96f, 432f)
    private val pauseRect = RectF(272f, 396f, 344f, 432f)

    private fun drawBottomControls(c: Canvas) {
        if (ambient) return
        c.drawRoundRect(undoRect, 18f, 18f, controlBg)
        c.drawRoundRect(undoRect, 18f, 18f, controlStroke)
        c.drawText("↶", 60f, 420f, controlText)

        c.drawRoundRect(pauseRect, 18f, 18f, controlBg)
        c.drawRoundRect(pauseRect, 18f, 18f, controlStroke)
        c.drawText("⏸", 308f, 419f, controlText)
    }

    private fun drawHalf(c: Canvas, m: PadelMatch, t: Team, y0: Float) {
        val grey = if (ambient) AMBIENT_GREY else GREY
        // Nombre del equipo y punto del saque
        label.color = grey
        val name = if (t == Team.US) us else them
        c.drawText(name, 24f, y0 + 36f, label)
        if (m.winner == null && m.server == t) {
            val x = 24f + label.measureText(name) + 14f
            if (ambient) { dot.style = Paint.Style.STROKE; dot.strokeWidth = 1.6f; dot.color = AMBIENT_LINE }
            else { dot.style = Paint.Style.FILL; dot.color = AMBER }
            c.drawCircle(x, y0 + 31f, 6f, dot)
        }

        // Sets anteriores (juegos de este equipo); blanco los ganados
        var x = 344f
        for (s in m.sets.asReversed()) {
            val mine = if (t == Team.US) s.us else s.them
            val theirs = if (t == Team.US) s.them else s.us
            setsPaint.color = if (ambient) AMBIENT_GREY else if (mine > theirs) Color.WHITE else GREY
            val txt = mine.toString()
            c.drawText(txt, x, y0 + 37f, setsPaint)
            x -= setsPaint.measureText(txt) + 14f
        }

        // Puntos del juego (o del tie-break) en grande
        stroke(points, if (ambient) null else Color.WHITE)
        c.drawText(m.pointLabel(t), 150f, y0 + 150f, points)

        // Juegos del set en curso (no hay en el super tie-break)
        if (!m.inSuperTieBreak) {
            stroke(games, if (ambient) null else AMBER)
            c.drawText(m.games[t.ordinal].toString(), 308f, y0 + 150f, games)
        }
    }

    /** Línea central con el aviso que toque (punto de oro, tie-break, cambio de lado). */
    private fun drawMiddle(c: Canvas, m: PadelMatch) {
        c.drawLine(24f, H / 2, 344f, H / 2, line)
        val text = when {
            m.winner != null -> null
            m.changeEnds -> context.getString(R.string.padel_change_ends)
            m.isGoldenPoint -> context.getString(R.string.padel_golden_point)
            m.inSuperTieBreak -> context.getString(R.string.padel_super_tiebreak)
            m.inTieBreak -> context.getString(R.string.padel_tiebreak)
            else -> null
        } ?: return
        status.color = if (ambient) AMBIENT_GREY else AMBER
        val w = status.measureText(text) + 20f
        c.drawRect(W / 2 - w / 2, H / 2 - 12f, W / 2 + w / 2, H / 2 + 12f, statusBg)
        c.drawText(text, W / 2, H / 2 + 4.5f, status)
    }

    /** Relleno de color en activo; contorno gris fino en ambiente. */
    private fun stroke(p: Paint, fill: Int?) {
        if (fill == null) { p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.color = AMBIENT_LINE }
        else { p.style = Paint.Style.FILL; p.color = fill }
    }

    private fun applyAntiAlias() {
        val aa = !(ambient && lowBit)
        allPaints.forEach { it.isAntiAlias = aa }
    }

    private companion object {
        const val W = 368f
        const val H = 448f
        const val AMBER = 0xFFFFB300.toInt()
        const val GREY = 0xFF9E9E9E.toInt()
        const val AMBIENT_GREY = 0xFF5A5A5A.toInt()
        const val AMBIENT_LINE = 0xFFBDBDBD.toInt()
    }
}
