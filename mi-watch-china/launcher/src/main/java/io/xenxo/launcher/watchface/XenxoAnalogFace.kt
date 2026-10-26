// La librería wearable support está obsoleta para Wear OS 3+, pero es la que corresponde a Wear OS 2 (Android 9).
@file:Suppress("DEPRECATION")

package io.xenxo.launcher.watchface

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Esfera "xenxo Analógica". Diseño aprobado: docs/watchface-xenxo-analog.svg (368×448).
 * El dial sigue la forma del cristal: superelipse |x/a|^5 + |y/b|^5 = 1 centrada en (184, 224).
 *  - Borde = progreso de pasos: pista gris y tramo ámbar desde las 12 en sentido horario.
 *  - 60 marcas; las de 12/3/6/9 en ámbar. Sin números ni segundero.
 * Lo estático (marcas y pista) se pinta una vez en un Bitmap; cada minuto solo agujas, textos y tramo ámbar.
 */
class XenxoAnalogFace : XenxoFaceService() {

    // El indicador de notificaciones de Wear OS sale arriba en el centro, justo encima del inicio de la pista de pasos
    override val hideNotificationIndicator = true

    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun centered(size: Float, color: Int, face: Typeface = Typeface.DEFAULT, spacingPx: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face; letterSpacing = spacingPx / size
            textAlign = Paint.Align.CENTER
        }

    private val date = centered(17f, XenxoDigitalFace.GREY, spacingPx = 2f)
    private val stepsNum = centered(26f, XenxoDigitalFace.AMBER, medium)
    private val stepsLabel = centered(12f, XenxoDigitalFace.DIM, spacingPx = 1.5f)
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; color = XenxoDigitalFace.GREY }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; color = XenxoDigitalFace.AMBIENT_GREY }
    private val heart = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = XenxoDigitalFace.RED }
    private val ambientSteps = centered(18f, XenxoDigitalFace.AMBIENT_GREY)
    private val progress = stroke(6f, XenxoDigitalFace.AMBER)
    private val hand = stroke(10f, Color.WHITE)
    private val hub = Paint(Paint.ANTI_ALIAS_FLAG)

    private val heartPath = XenxoDigitalFace.heartPath()
    private val progressPath = Path()

    private var staticActive: Bitmap? = null
    private var staticAmbient: Bitmap? = null

    override fun onCreateEngine(): Engine = super.onCreateEngine().also {
        (it as FaceEngine).paints += listOf(date, stepsNum, stepsLabel, small, dot, heart, ambientSteps, progress, hand, hub)
    }

    override fun drawFace(e: FaceEngine, c: Canvas, now: Calendar) {
        val d = e.data
        val bmp = if (e.ambient) staticAmbient ?: buildStatic(true).also { staticAmbient = it }
                  else staticActive ?: buildStatic(false).also { staticActive = it }
        c.drawBitmap(bmp, 0f, 0f, null)

        if (!e.ambient && d.steps > 0) {
            // Tramo ámbar: de las 12 en sentido horario, proporcional a pasos/objetivo (por ángulo, como el SVG)
            val goal = if (d.stepGoal > 0) d.stepGoal else FaceData.STEP_GOAL
            val f = (d.steps.toFloat() / goal).coerceIn(0f, 1f)
            progressPath.reset()
            var deg = 0f
            val end = 360f * f
            while (true) {
                val (x, y) = superellipse(deg, TRACK_A, TRACK_B)
                if (deg == 0f) progressPath.moveTo(x, y) else progressPath.lineTo(x, y)
                if (deg >= end) break
                deg = (deg + 1f).coerceAtMost(end)
            }
            c.drawPath(progressPath, progress)
        }

        date.color = if (e.ambient) XenxoDigitalFace.AMBIENT_GREY else XenxoDigitalFace.GREY
        c.drawText(FaceFormat.dayAndNumber(now), CX, 162f, date)

        if (e.ambient) {
            if (d.steps >= 0) c.drawText("${FaceFormat.thousands(d.steps)} pasos", CX, 302f, ambientSteps)
        } else {
            c.drawText(if (d.steps >= 0) FaceFormat.thousands(d.steps) else "--", CX, 298f, stepsNum)
            c.drawText("PASOS", CX, 320f, stepsLabel)
            drawPulseAndBattery(c, d)
        }

        // Agujas: horas 92 px y minutos 150 px, ambas con 14 px de cola; minutos por encima
        val h = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f
        val m = now.get(Calendar.MINUTE).toFloat()
        drawHand(c, h * 30f, 92f, 10f, e.ambient)
        drawHand(c, m * 6f, 150f, 7f, e.ambient)
        hub.color = if (e.ambient) 0xFFBDBDBD.toInt() else XenxoDigitalFace.AMBER
        c.drawCircle(CX, CY, 8f, hub)
        hub.color = Color.BLACK
        c.drawCircle(CX, CY, 3f, hub)
    }

    /** "♥ 60 · 44%" centrado bajo los pasos. */
    private fun drawPulseAndBattery(c: Canvas, d: FaceData) {
        val bpm = if (d.bpm > 0) d.bpm.toString() else "--"
        val pct = d.batteryPercent().let { if (it >= 0) "$it%" else "--" }
        val heartW = 16f * 0.9f
        val wBpm = small.measureText(bpm)
        val wDot = dot.measureText("·")
        val total = heartW + 6f + wBpm + 12f + wDot + 12f + small.measureText(pct)
        var x = CX - total / 2f
        c.save(); c.translate(x, 339f); c.scale(0.9f, 0.9f); c.drawPath(heartPath, heart); c.restore()
        x += heartW + 6f
        c.drawText(bpm, x, 352f, small); x += wBpm + 12f
        c.drawText("·", x, 352f, dot); x += wDot + 12f
        c.drawText(pct, x, 352f, small)
    }

    private fun drawHand(c: Canvas, deg: Float, length: Float, width: Float, ambient: Boolean) {
        val r = Math.toRadians(deg.toDouble())
        val dx = sin(r).toFloat()
        val dy = -cos(r).toFloat()
        val x1 = CX - dx * 14f; val y1 = CY - dy * 14f
        val x2 = CX + dx * length; val y2 = CY + dy * length
        if (ambient) {
            // Contorno: trazo gris y, encima, uno negro 3,2 px más fino (1,6 px de borde, como el SVG)
            hand.color = 0xFFBDBDBD.toInt(); hand.strokeWidth = width
            c.drawLine(x1, y1, x2, y2, hand)
            hand.color = Color.BLACK; hand.strokeWidth = width - 3.2f
            c.drawLine(x1, y1, x2, y2, hand)
        } else {
            hand.color = Color.WHITE; hand.strokeWidth = width
            c.drawLine(x1, y1, x2, y2, hand)
        }
    }

    /** Marcas (y pista gris en activo) sobre fondo negro, a tamaño de diseño. */
    private fun buildStatic(ambient: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(DESIGN_W, DESIGN_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (!ambient) {
            val track = Path()
            for (deg in 0..360) {
                val (x, y) = superellipse(deg.toFloat(), TRACK_A, TRACK_B)
                if (deg == 0) track.moveTo(x, y) else track.lineTo(x, y)
            }
            track.close()
            c.drawPath(track, stroke(6f, 0xFF1C1C1C.toInt()))
        }
        val tick = stroke(2f, Color.WHITE)
        for (i in 0 until 60) {
            val hour = i % 5 == 0
            if (ambient && !hour) continue
            val cardinal = i % 15 == 0
            val (ox, oy) = superellipse(i * 6f, TICK_A, TICK_B)
            val len = when { ambient -> 18f; cardinal -> 18f; hour -> 21f; else -> 7f }
            tick.strokeWidth = when { ambient -> 4f; cardinal -> 5f; hour -> 4f; else -> 2f }
            tick.color = when { ambient -> 0xFF8A8A8A.toInt(); cardinal -> XenxoDigitalFace.AMBER; hour -> Color.WHITE; else -> 0xFF4A4A4A.toInt() }
            // Hacia el centro desde el punto del borde
            val vx = CX - ox; val vy = CY - oy
            val n = hypot(vx, vy)
            c.drawLine(ox, oy, ox + vx / n * len, oy + vy / n * len, tick)
        }
        return bmp
    }

    private fun stroke(w: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; this.color = color; strokeCap = Paint.Cap.ROUND
    }

    companion object {
        const val CX = 184f
        const val CY = 224f
        // Pista de pasos a 14 px del borde y marcas a 30 px (semiejes de la superelipse)
        const val TRACK_A = 170f; const val TRACK_B = 210f
        const val TICK_A = 154f; const val TICK_B = 194f
        private const val N = 5.0

        /** Punto de la superelipse en la dirección [deg] (0 = las 12, sentido horario). */
        fun superellipse(deg: Float, a: Float, b: Float): Pair<Float, Float> {
            val r = Math.toRadians(deg.toDouble())
            val dx = sin(r); val dy = -cos(r)
            val rho = (abs(dx / a).pow(N) + abs(dy / b).pow(N)).pow(-1.0 / N)
            return Pair((CX + dx * rho).toFloat(), (CY + dy * rho).toFloat())
        }
    }
}
