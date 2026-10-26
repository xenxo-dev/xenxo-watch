package io.xenxo.wristai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * Orbe celestial etéreo con textura de nebulosa brumosa y aurora boreal exterior.
 * Inspirado en la estética orgánica de ChatGPT y vapores cósmicos:
 *  - 0 allocations en onDraw (sin pausas GC).
 *  - Base de gradiente azul profundo y eléctrico.
 *  - Nubes volumétricas brumosas en tonos blancos, celestes y cian en continuo movimiento orgánico.
 *  - Aurora exterior resplandeciente cuando habla o escucha.
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING
    }

    var state: State = State.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private var targetAmplitude = 0f
    private var smoothedAmplitude = 0f

    private var timeSec = 0f
    private var lastFrameTime = 0L

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 10_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val now = System.currentTimeMillis()
            if (lastFrameTime > 0) {
                val dt = (now - lastFrameTime) / 1000f
                timeSec += dt
            }
            lastFrameTime = now

            // Suavizado dinámico de amplitud
            smoothedAmplitude += (targetAmplitude - smoothedAmplitude) * 0.25f
            invalidate()
        }
    }

    // Shaders y pinturas preasignadas
    private val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint3 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val orbPath = Path()
    private val clipPath = Path()

    // Matrices de transformación para mover las nubes sin recrear shaders (0 GC)
    private val matrixCloud1 = Matrix()
    private val matrixCloud2 = Matrix()
    private val matrixCloud3 = Matrix()
    private val matrixAurora = Matrix()

    private var baseShader: RadialGradient? = null
    private var cloudShader1: RadialGradient? = null
    private var cloudShader2: RadialGradient? = null
    private var cloudShader3: RadialGradient? = null
    private var rimShader: RadialGradient? = null
    private var auroraShader: RadialGradient? = null

    private var baseR = 0f
    private var maxR = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val dx = event.x - (width / 2f)
            val dy = event.y - (height / 2f)
            val dist = kotlin.math.hypot(dx, dy)
            val density = resources.displayMetrics.density
            val touchRadius = 68f * density
            if (dist > touchRadius) {
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    fun setAmplitude(raw: Float) {
        targetAmplitude = raw.coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameTime = System.currentTimeMillis()
        if (!animator.isRunning) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            lastFrameTime = System.currentTimeMillis()
            if (!animator.isRunning) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val density = resources.displayMetrics.density
        baseR = 53.5f * density
        maxR = baseR * 1.08f

        // 1. Resplandor Aurora Exterior Boreal
        auroraShader = RadialGradient(
            cx, cy, baseR * 1.55f,
            intArrayOf(
                Color.parseColor("#7500E5FF"), // cian eléctrico vivo
                Color.parseColor("#50007AFF"), // azul real luminoso
                Color.parseColor("#287C4DFF"), // violeta cósmico
                Color.parseColor("#00000000")  // transparente
            ),
            floatArrayOf(0f, 0.40f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        auroraPaint.shader = auroraShader

        // 2. Base de la esfera: Azul eléctrico vivo en la parte inferior y derecha
        baseShader = RadialGradient(
            cx - maxR * 0.10f, cy + maxR * 0.15f, maxR * 1.30f,
            intArrayOf(
                Color.parseColor("#0080FF"), // azul vivo eléctrico
                Color.parseColor("#005BEA"), // azul zafiro
                Color.parseColor("#002A99")  // azul profundo
            ),
            floatArrayOf(0f, 0.60f, 1f),
            Shader.TileMode.CLAMP
        )
        basePaint.shader = baseShader

        // 3. Nube Nebulosa 1: Masa de niebla blanca luminosa superior-derecha (exacto como la imagen)
        cloudShader1 = RadialGradient(
            cx + maxR * 0.18f, cy - maxR * 0.22f, maxR * 0.95f,
            intArrayOf(
                Color.parseColor("#FFFFFFFF"), // blanco puro sólido
                Color.parseColor("#F0FCFF"),   // blanco vapor
                Color.parseColor("#C3EFFF"),   // celeste bruma
                Color.parseColor("#0080D8FF")  // transparente
            ),
            floatArrayOf(0f, 0.42f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        cloudPaint1.shader = cloudShader1

        // 4. Nube Nebulosa 2: Velo brumoso diagonal que cruza el centro
        cloudShader2 = RadialGradient(
            cx - maxR * 0.22f, cy - maxR * 0.02f, maxR * 1.0f,
            intArrayOf(
                Color.parseColor("#FAFFFFFF"), // blanco bruma
                Color.parseColor("#C8F2FF"), // celeste claro
                Color.parseColor("#50CAFF"), // cian cielo
                Color.parseColor("#0040C4FF") // transparente
            ),
            floatArrayOf(0f, 0.38f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        cloudPaint2.shader = cloudShader2

        // 5. Nube Nebulosa 3: Volutas secundarias superiores
        cloudShader3 = RadialGradient(
            cx - maxR * 0.05f, cy - maxR * 0.45f, maxR * 0.75f,
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#B3E5FC"),
                Color.parseColor("#0081D4FA")
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        cloudPaint3.shader = cloudShader3

        // 6. Sombra y viñeteado esférico en el perímetro interior (profundidad 3D)
        rimShader = RadialGradient(
            cx, cy, maxR,
            intArrayOf(
                Color.parseColor("#00000000"),
                Color.parseColor("#10001C66"),
                Color.parseColor("#45001040")
            ),
            floatArrayOf(0f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )
        rimGlowPaint.shader = rimShader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        if (baseR <= 0f) {
            baseR = 53.5f * resources.displayMetrics.density
            maxR = baseR * 1.08f
        }

        val t = timeSec
        val amp = smoothedAmplitude

        // 1. Escala y respiración del orbe según el estado
        var scale = 1.0f
        when (state) {
            State.IDLE -> {
                scale = 1.0f + 0.025f * sin(t * 1.5f)
            }
            State.LISTENING -> {
                scale = 1.0f + amp * 0.16f + 0.02f * sin(t * 4.5f)
            }
            State.THINKING -> {
                scale = 0.98f + 0.04f * sin(t * 3.0f)
            }
            State.SPEAKING -> {
                scale = 1.0f + 0.06f * abs(sin(t * 4.0f)) + amp * 0.08f
            }
        }

        val r = baseR * scale

        // 2. Aurora de alrededor: resplandor activo cuando habla o escucha
        val isAuroraActive = (state == State.SPEAKING || (state == State.LISTENING && amp > 0.08f) || state == State.THINKING)
        if (isAuroraActive) {
            // Ondas de emisión aurora concéntricas
            val rippleCount = if (state == State.SPEAKING) 3 else 2
            val speed = if (state == State.SPEAKING) 1.1f else 0.7f
            for (i in 0 until rippleCount) {
                val phase = ((t * speed + i.toFloat() / rippleCount) % 1.0f)
                val rippleR = r + phase * (baseR * 0.45f)
                val alpha = ((1.0f - phase) * 110f * (if (state == State.SPEAKING) 1.3f else 1.0f)).toInt().coerceIn(0, 255)
                // Color aurora: cian que vira a azul eléctrico
                val cianAlpha = (alpha * 0.9f).toInt()
                ripplePaint.color = Color.argb(cianAlpha, 0x00, 0xE5, 0xFF)
                ripplePaint.strokeWidth = 3.2f * (1.0f - phase * 0.65f)
                canvas.drawCircle(cx, cy, rippleR, ripplePaint)
            }

            // Resplandor aurora difuso alrededor del orbe
            val auroraScale = 1.0f + 0.08f * sin(t * 2.5f) + amp * 0.15f
            matrixAurora.reset()
            matrixAurora.postScale(auroraScale, auroraScale, cx, cy)
            auroraShader?.setLocalMatrix(matrixAurora)
            canvas.drawCircle(cx, cy, r * 1.50f, auroraPaint)
        } else {
            // En reposo, resplandor suave y tranquilo
            val idleScale = 1.0f + 0.03f * sin(t * 1.2f)
            matrixAurora.reset()
            matrixAurora.postScale(idleScale, idleScale, cx, cy)
            auroraShader?.setLocalMatrix(matrixAurora)
            canvas.drawCircle(cx, cy, r * 1.25f, auroraPaint)
        }

        // 3. Perímetro orgánico circular perfecto
        orbPath.reset()
        orbPath.addCircle(cx, cy, r, Path.Direction.CW)

        // 4. Dibujar base azul zafiro profunda
        canvas.drawPath(orbPath, basePaint)

        // 5. Recorte esférico para las capas de niebla/nubes
        canvas.save()
        clipPath.reset()
        clipPath.set(orbPath)
        canvas.clipPath(clipPath)

        // Si está pensando, rotación lenta y fluida de las nebulosas
        if (state == State.THINKING) {
            val rot = (t * 60f) % 360f
            canvas.rotate(rot, cx, cy)
        }

        // 6. Animación de nubes volumétricas y vapores cósmicos (efecto nebulosa de la imagen)
        // Desplazamiento orgánico Lissajous para simular niebla en movimiento
        val billowSpeed = if (state == State.SPEAKING) 1.5f else 1.0f

        val drift1X = sin(t * 0.6f * billowSpeed) * (r * 0.09f)
        val drift1Y = cos(t * 0.45f * billowSpeed) * (r * 0.07f)
        matrixCloud1.reset()
        matrixCloud1.postTranslate(drift1X, drift1Y)
        cloudShader1?.setLocalMatrix(matrixCloud1)
        canvas.drawCircle(cx, cy, r * 1.15f, cloudPaint1)

        val drift2X = cos(t * 0.5f * billowSpeed) * (r * 0.11f)
        val drift2Y = sin(t * 0.7f * billowSpeed) * (r * 0.08f)
        matrixCloud2.reset()
        matrixCloud2.postTranslate(drift2X, drift2Y)
        cloudShader2?.setLocalMatrix(matrixCloud2)
        canvas.drawCircle(cx, cy, r * 1.20f, cloudPaint2)

        val drift3X = sin(t * 0.85f * billowSpeed) * (r * 0.07f)
        val drift3Y = cos(t * 0.65f * billowSpeed) * (r * 0.06f)
        matrixCloud3.reset()
        matrixCloud3.postTranslate(drift3X, drift3Y)
        cloudShader3?.setLocalMatrix(matrixCloud3)
        canvas.drawCircle(cx, cy, r * 1.10f, cloudPaint3)

        // 7. Sombra de curvatura 3D periférica
        canvas.drawCircle(cx, cy, r, rimGlowPaint)

        canvas.restore()
    }
}
