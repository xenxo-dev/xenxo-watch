package io.xenxo.wristai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Orbe de Voz Circular con efecto Aurora para Xiaomi Watch 2 (466×466).
 * Diseñado para pantalla redonda: aurora concéntrica con pulsación viva sin cortes laterales.
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    var state: State = State.IDLE
        set(value) {
            field = value
            updateStateAnimation()
            invalidate()
        }

    private var phase = 0f
    private var pulseAnimator: ValueAnimator? = null

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        pulseAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }
        pulseAnimator?.start()
    }

    private fun updateStateAnimation() {
        when (state) {
            State.IDLE -> pulseAnimator?.duration = 3000
            State.LISTENING -> pulseAnimator?.duration = 1400
            State.THINKING -> pulseAnimator?.duration = 800
            State.SPEAKING -> pulseAnimator?.duration = 1800
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 2f) * 0.42f
        val pulse = (sin(phase.toDouble()).toFloat() + 1f) * 0.5f

        // Radio dinámico según estado
        val currentRadius = when (state) {
            State.IDLE -> baseRadius * (1f + pulse * 0.04f)
            State.LISTENING -> baseRadius * (1.05f + pulse * 0.12f)
            State.THINKING -> baseRadius * (0.95f + pulse * 0.08f)
            State.SPEAKING -> baseRadius * (1.08f + pulse * 0.18f)
        }

        // Colores de la Aurora
        val (coreColor, auraColor) = when (state) {
            State.IDLE -> Pair(Color.parseColor("#30D158"), Color.parseColor("#1530D158"))
            State.LISTENING -> Pair(Color.parseColor("#0A84FF"), Color.parseColor("#350A84FF"))
            State.THINKING -> Pair(Color.parseColor("#BF5AF2"), Color.parseColor("#35BF5AF2"))
            State.SPEAKING -> Pair(Color.parseColor("#FF9F0A"), Color.parseColor("#40FF9F0A"))
        }

        // 1. Resplandor exterior (Aurora concéntrica suave)
        val auraRadius = currentRadius * 1.6f
        auraPaint.shader = RadialGradient(
            cx, cy, auraRadius,
            intArrayOf(auraColor, Color.TRANSPARENT),
            floatArrayOf(0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, auraRadius, auraPaint)

        // 2. Núcleo brillante
        orbPaint.shader = RadialGradient(
            cx, cy - currentRadius * 0.25f, currentRadius,
            intArrayOf(Color.WHITE, coreColor),
            floatArrayOf(0.1f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, currentRadius, orbPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
