package io.xenxo.outdoor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vista de Rosa de los Vientos / Brújula Digital para 368×448 AMOLED.
 * Rota suavemente en función del rumbo (azimuth) con cero píxeles innecesarios encendidos.
 */
class CompassRoseView(context: Context) : View(context) {

    private var headingDegrees = 0f

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }

    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f
        textAlign = Paint.Align.CENTER
        color = 0xFFFF453A.toInt() // Rojo Norte
        isFakeBoldText = true
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF453A.toInt()
        style = Paint.Style.FILL
    }

    private val trianglePath = Path()

    fun setHeading(degrees: Float) {
        headingDegrees = degrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - 16f
        if (radius <= 0) return

        // 1. Círculo exterior sutil
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // 2. Dial rotatorio de la brújula
        canvas.save()
        // Rotamos en sentido contrario al rumbo para que el Norte apunte a su dirección física real
        canvas.rotate(-headingDegrees, cx, cy)

        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg.toDouble())
            val cosVal = cos(rad).toFloat()
            val sinVal = sin(rad).toFloat()

            val outerX = cx + radius * sinVal
            val outerY = cy - radius * cosVal
            val tickLen = if (deg % 90 == 0) 14f else 8f
            val innerX = cx + (radius - tickLen) * sinVal
            val innerY = cy - (radius - tickLen) * cosVal

            canvas.drawLine(innerX, innerY, outerX, outerY, tickPaint)

            // Letras cardinales (sin alocaciones de Pair en onDraw)
            if (deg % 90 == 0) {
                val textRadius = radius - 26f
                val textX = cx + textRadius * sinVal
                val textY = cy - textRadius * cosVal + 5f

                when (deg) {
                    0 -> canvas.drawText("N", textX, textY, northPaint)
                    90 -> canvas.drawText("E", textX, textY, cardinalPaint)
                    180 -> canvas.drawText("S", textX, textY, cardinalPaint)
                    270 -> canvas.drawText("O", textX, textY, cardinalPaint)
                }
            }
        }
        canvas.restore()

        // 3. Puntero superior fijo (indica hacia dónde mira el usuario)
        trianglePath.reset()
        trianglePath.moveTo(cx, cy - radius - 10f)
        trianglePath.lineTo(cx - 7f, cy - radius + 2f)
        trianglePath.lineTo(cx + 7f, cy - radius + 2f)
        trianglePath.close()
        canvas.drawPath(trianglePath, pointerPaint)
    }
}
