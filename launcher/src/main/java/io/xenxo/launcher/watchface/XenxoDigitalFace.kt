// La librería wearable support está obsoleta para Wear OS 3+, pero es la que corresponde a Wear OS 2 (Android 9).
@file:Suppress("DEPRECATION")

package io.xenxo.launcher.watchface

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar

/**
 * Esfera "xenxo" digital. Diseño aprobado: docs/watchface-xenxo.svg (coordenadas copiadas tal cual, 368×448).
 * Activa: fecha + batería, hora grande, pasos con barra hacia el objetivo y kcal / km / pulso.
 * Ambiente: fecha, hora en contorno y pasos en gris; sin colores ni barra.
 */
class XenxoDigitalFace : XenxoFaceService() {

    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun text(size: Float, color: Int, face: Typeface = Typeface.DEFAULT, spacingPx: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face; letterSpacing = spacingPx / size
        }

    private val date = text(17f, GREY, spacingPx = 0.3f)
    private val battText = text(16f, GREY)
    private val battStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = GREY }
    private val battFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREY }
    private val time = text(112f, Color.WHITE, light, spacingPx = -3f)
    private val stepsNum = text(40f, AMBER, medium)
    private val goal = text(17f, DIM)
    private val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() }
    private val barFg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER }
    private val separator = Paint().apply { color = 0xFF1E1E1E.toInt(); strokeWidth = 1.5f }
    private val kcal = text(32f, 0xFFFF7043.toInt(), medium)
    private val km = text(32f, 0xFF26C6DA.toInt(), medium)
    private val bpm = text(32f, RED, medium)
    private val label = text(14f, DIM, spacingPx = 1.2f)
    private val heart = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
    private val brand = text(12f, 0xFF3A3A3A.toInt(), spacingPx = 2f).apply { textAlign = Paint.Align.CENTER }
    private val ambientSteps = text(22f, AMBIENT_GREY)

    private val heartPath = heartPath()
    private val rect = RectF()

    override fun onCreateEngine(): Engine = super.onCreateEngine().also {
        (it as FaceEngine).paints += listOf(date, battText, battStroke, battFill, time, stepsNum, goal, barBg, barFg,
            kcal, km, bpm, label, heart, brand, ambientSteps)
    }

    override fun drawFace(e: FaceEngine, c: Canvas, now: Calendar) {
        val d = e.data
        if (e.ambient) {
            date.color = AMBIENT_GREY
            c.drawText(FaceFormat.shortDate(now), 34f, 52f, date)
            time.style = Paint.Style.STROKE; time.strokeWidth = 1.6f; time.color = 0xFFBDBDBD.toInt()
            c.drawText(FaceFormat.hhmm(now), 30f, 168f, time)
            if (d.steps >= 0) c.drawText("${FaceFormat.thousands(d.steps)} pasos", 34f, 236f, ambientSteps)
            return
        }

        // Fila superior: fecha a la izquierda, batería alineada a la derecha (margen 34 px)
        date.color = GREY
        c.drawText(FaceFormat.shortDate(now), 34f, 52f, date)
        val pct = d.batteryPercent()
        val pctText = if (pct >= 0) "$pct%" else "--"
        val textX = 334f - battText.measureText(pctText)
        c.drawText(pctText, textX, 51f, battText)
        val bx = textX - 40f
        rect.set(bx, 38f, bx + 28f, 53f); c.drawRoundRect(rect, 3f, 3f, battStroke)
        rect.set(bx + 28.5f, 42.5f, bx + 31f, 48.5f); c.drawRoundRect(rect, 1f, 1f, battFill)
        if (pct > 0) { rect.set(bx + 2.5f, 40.5f, bx + 2.5f + 23f * pct / 100f, 50.5f); c.drawRoundRect(rect, 1.5f, 1.5f, battFill) }

        // Hora
        time.style = Paint.Style.FILL; time.color = Color.WHITE
        c.drawText(FaceFormat.hhmm(now), 30f, 168f, time)

        // Pasos + objetivo + barra
        val steps = d.steps.coerceAtLeast(0)
        val stepsText = if (d.steps >= 0) FaceFormat.thousands(steps) else "--"
        c.drawText(stepsText, 34f, 236f, stepsNum)
        val goalNum = if (d.stepGoal > 0) d.stepGoal else FaceData.STEP_GOAL
        c.drawText("/ ${FaceFormat.thousands(goalNum)} pasos", 34f + stepsNum.measureText(stepsText) + 14f, 236f, goal)
        rect.set(34f, 252f, 334f, 258f); c.drawRoundRect(rect, 3f, 3f, barBg)
        val fill = 300f * (steps.toFloat() / goalNum).coerceIn(0f, 1f)
        if (fill > 0f) { rect.set(34f, 252f, 34f + fill.coerceAtLeast(6f), 258f); c.drawRoundRect(rect, 3f, 3f, barFg) }

        c.drawLine(34f, 292f, 334f, 292f, separator)

        // Tres datos
        c.drawText(if (d.kcal >= 0) d.kcal.toString() else "--", 34f, 348f, kcal)
        c.drawText("KCAL", 34f, 374f, label)
        c.drawText(if (d.km >= 0f) FaceFormat.oneDecimal(d.km) else "--", 146f, 348f, km)
        c.drawText("KM", 146f, 374f, label)
        c.save(); c.translate(246f, 322f); c.drawPath(heartPath, heart); c.restore()
        c.drawText(if (d.bpm > 0) d.bpm.toString() else "--", 268f, 348f, bpm)
        c.drawText("PPM", 246f, 374f, label)

        c.drawText("XENXO", 184f, 420f, brand)
    }

    /** Hora → panal del launcher; pasos → Gym xenxo; pulso/kcal/km → Salud xenxo. */
    override fun tapZone(x: Int, y: Int): Intent? = when {
        y < 180 -> launcherIntent()
        y in 180..280 -> gymIntent()
        y > 280 && x >= 230 -> healthIntent()
        y > 280 -> healthIntent()
        else -> launcherIntent()
    }

    companion object {
        const val AMBER = 0xFFFFB300.toInt()
        const val RED = 0xFFEF5350.toInt()
        const val GREY = 0xFF9E9E9E.toInt()
        const val DIM = 0xFF757575.toInt()
        const val AMBIENT_GREY = 0xFF5A5A5A.toInt()

        /** Corazón del SVG (16×15 px): M8 4.5c-1.8-3-6.5-2.6-7.3 1… */
        fun heartPath() = Path().apply {
            moveTo(8f, 4.5f)
            rCubicTo(-1.8f, -3f, -6.5f, -2.6f, -7.3f, 1f)
            rCubicTo(-0.6f, 2.7f, 1.2f, 5f, 7.3f, 9.8f)
            rCubicTo(6.1f, -4.8f, 7.9f, -7.1f, 7.3f, -9.8f)
            rCubicTo(-0.8f, -3.6f, -5.5f, -4f, -7.3f, -1f)
            close()
        }
    }
}
