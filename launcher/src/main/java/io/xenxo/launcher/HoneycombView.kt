package io.xenxo.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Panal de apps estilo Apple Watch, pintado a mano en un solo View.
 *
 * - Iconos en espiral hexagonal por columnas: la app nº 0 en el centro, luego anillos.
 * - Ojo de pez adaptado a la pantalla cuadrada: los iconos encogen hacia los bordes y se acercan
 *   al centro, para que el panal siga compacto aunque encojan.
 * - Arrastrar para moverse, inercia al soltar, corona para hacer zoom.
 * - Solo repinta cuando algo se mueve: sin animaciones en bucle.
 */
class HoneycombView(context: Context) : View(context) {

    var onTap: ((AppEntry, RectF) -> Unit)? = null
    var onLongPress: ((AppEntry) -> Unit)? = null
    var onLongPressEmpty: (() -> Unit)? = null

    private var apps: List<AppEntry> = emptyList()
    private var cells: FloatArray = FloatArray(0) // x,y por app en coordenadas de "mundo"
    private var screen: FloatArray = FloatArray(0) // x,y,radio por app en pantalla (último pintado)

    /** Distancia entre centros de iconos a zoom 1 (~92 px en el reloj, como T-Launcher). */
    private val spacing = resources.displayMetrics.density * 46f
    private val baseRadius = spacing * 0.47f

    private var offX = 0f
    private var offY = 0f
    private var zoom = 1f
    private var maxExtent = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
    private val dst = RectF()
    private val scroller = OverScroller(context)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            moveBy(dx / zoom, dy / zoom)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val lim = maxExtent.toInt()
            scroller.fling(offX.toInt(), offY.toInt(), (-vx / zoom).toInt(), (-vy / zoom).toInt(), -lim, lim, -lim, lim)
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val i = hit(e.x, e.y)
            if (i >= 0) {
                val r = screen[i * 3 + 2]
                onTap?.invoke(apps[i], RectF(screen[i * 3] - r, screen[i * 3 + 1] - r, screen[i * 3] + r, screen[i * 3 + 1] + r))
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val i = hit(e.x, e.y)
            if (i < 0) { onLongPressEmpty?.invoke(); return }
            // El icono se "levanta": se puede arrastrar a otra posición; si se suelta sin mover, menú de la app
            dragIndex = i; dragStartX = e.x; dragStartY = e.y; dragX = e.x; dragY = e.y
            dragMoved = false; dropIndex = -1
            invalidate()
        }
    })

    // ---------- arrastrar para reordenar ----------

    /** Nuevo orden tras soltar un icono en otra posición (las claves en el orden en que se ven). */
    var onReorder: ((List<String>) -> Unit)? = null

    private var dragIndex = -1
    private var dropIndex = -1
    private var dragMoved = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private val dropRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFF30D158.toInt()
    }
    private val lifted = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }

    private fun dragTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragX = e.x; dragY = e.y
                if (!dragMoved && hypot(dragX - dragStartX, dragY - dragStartY) > DRAG_SLOP) dragMoved = true
                if (dragMoved) {
                    // Cerca del borde, el panal se desplaza para poder llevar la app lejos
                    val edge = width * 0.12f
                    val px = when { dragX < edge -> -1f; dragX > width - edge -> 1f; else -> 0f }
                    val py = when { dragY < edge -> -1f; dragY > height - edge -> 1f; else -> 0f }
                    if (px != 0f || py != 0f) moveBy(px * 6f / zoom, py * 6f / zoom)
                    val target = nearest(dragX, dragY)
                    if (target != dropIndex) {
                        dropIndex = target
                        if (target >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val from = dragIndex; val to = dropIndex; val moved = dragMoved
                endDrag()
                if (!moved) onLongPress?.invoke(apps[from])
                else if (to >= 0 && to != from) {
                    val list = apps.toMutableList()
                    list.add(to, list.removeAt(from))
                    apps = list
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onReorder?.invoke(list.map { it.key })
                }
            }
            MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun endDrag() {
        dragIndex = -1; dropIndex = -1; dragMoved = false
        invalidate()
    }

    /** Posición (índice) más cercana al dedo entre los iconos visibles, o -1. */
    private fun nearest(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in apps.indices) {
            if (screen[i * 3 + 2] <= 0f) continue
            val d = hypot(x - screen[i * 3], y - screen[i * 3 + 1])
            if (d < bestD) { best = i; bestD = d }
        }
        return best
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setApps(list: List<AppEntry>) {
        apps = list
        cells = HexSpiral.positions(list.size, spacing)
        screen = FloatArray(list.size * 3)
        maxExtent = 0f
        for (i in 0 until list.size) maxExtent = max(maxExtent, hypot(cells[i * 2], cells[i * 2 + 1]))
        invalidate()
    }

    /** Lado en px del bitmap de cada icono: el diámetro con el zoom máximo, para que no se vea borroso. */
    fun iconSizePx() = (baseRadius * 2 * MAX_ZOOM).toInt()

    /** Vuelve al centro (app nº 0) con zoom normal. */
    fun recenter() {
        scroller.forceFinished(true)
        offX = 0f; offY = 0f; zoom = 1f
        invalidate()
    }

    // ---------- pintado ----------

    override fun onDraw(canvas: Canvas) {
        project()
        for (i in apps.indices) {
            if (i == dragIndex) continue // el que se arrastra se pinta al final, sobre el dedo
            val r = screen[i * 3 + 2]
            if (r <= 0f) continue
            drawIcon(canvas, i, screen[i * 3], screen[i * 3 + 1], r, paint)
        }
        if (dragIndex >= 0) {
            if (dragMoved && dropIndex >= 0) {
                val r = screen[dropIndex * 3 + 2]
                canvas.drawCircle(screen[dropIndex * 3], screen[dropIndex * 3 + 1], r + 5f, dropRing)
            }
            val r = baseRadius * zoom * 1.12f
            drawIcon(canvas, dragIndex, if (dragMoved) dragX else screen[dragIndex * 3],
                if (dragMoved) dragY else screen[dragIndex * 3 + 1], r, lifted)
        }
    }

    private fun drawIcon(canvas: Canvas, i: Int, sx: Float, sy: Float, r: Float, p: Paint) {
        val icon = apps[i].icon
        if (icon != null) {
            dst.set(sx - r, sy - r, sx + r, sy + r)
            canvas.drawBitmap(icon, null, dst, p)
        } else {
            canvas.drawCircle(sx, sy, r, placeholder)
        }
    }

    /**
     * Calcula posición y radio en pantalla de cada icono. La distancia al centro se normaliza con el
     * semiancho y la semialtura, así el ojo de pez sigue la forma cuadrada del cristal: unos 88 px en el
     * centro, ~55 px en los bordes y ~25 px en las esquinas. Un radio 0 significa "no se pinta".
     */
    private fun project() {
        val cx = width / 2f
        val cy = height / 2f
        if (cx <= 0f || cy <= 0f) return
        for (i in apps.indices) {
            val dx = (cells[i * 2] - offX) * zoom
            val dy = (cells[i * 2 + 1] - offY) * zoom
            val d = hypot(dx / cx, dy / cy)
            val t = ((d - FISHEYE_START) / (FISHEYE_END - FISHEYE_START)).coerceIn(0f, 1f)
            val smooth = t * t * (3 - 2 * t)
            val scale = 1f - smooth * 0.85f
            val pull = 1f - smooth * 0.18f
            val sx = cx + dx * pull
            val sy = cy + dy * pull
            var r = baseRadius * zoom * scale
            if (r < 3f || sx + r < 0 || sx - r > width || sy + r < 0 || sy - r > height) r = 0f
            screen[i * 3] = sx
            screen[i * 3 + 1] = sy
            screen[i * 3 + 2] = r
        }
    }

    /** Índice del icono bajo el dedo, o -1. Usa las posiciones del último pintado. */
    private fun hit(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in apps.indices) {
            val r = screen[i * 3 + 2]
            if (r <= 0f) continue
            val d = hypot(x - screen[i * 3], y - screen[i * 3 + 1])
            if (d <= r * 1.1f && d < bestD) { best = i; bestD = d }
        }
        return best
    }

    // ---------- movimiento ----------

    private fun moveBy(dx: Float, dy: Float) {
        offX += dx; offY += dy
        clamp()
        invalidate()
    }

    private fun clamp() {
        val d = hypot(offX, offY)
        if (d > maxExtent && d > 0f) {
            offX *= maxExtent / d
            offY *= maxExtent / d
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offX = scroller.currX.toFloat()
            offY = scroller.currY.toFloat()
            clamp()
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Tras mantener pulsado un icono, el resto del gesto es arrastrarlo, no mover el panal
        if (dragIndex >= 0 && event.actionMasked != MotionEvent.ACTION_DOWN) return dragTouch(event)
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }

    /**
     * Corona: zoom suave y fluido entre 0,6x y 1,5x.
     * Girar hacia arriba (adelante, delta > 0) -> acerca (zoom in).
     * Girar hacia abajo (hacia el usuario, delta < 0) -> aleja (zoom out).
     * Sin vibración continua en la corona para evitar vibración excesiva o lag táctil.
     */
    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val next = (zoom * (1f + delta * 0.15f)).coerceIn(MIN_ZOOM, MAX_ZOOM)
            if (kotlin.math.abs(next - zoom) > 0.001f) {
                zoom = next
                invalidate()
            }
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private companion object {
        const val MIN_ZOOM = 0.6f
        /** Px que hay que mover el dedo, con el icono levantado, para que cuente como arrastre y no como menú. */
        const val DRAG_SLOP = 24f
        const val MAX_ZOOM = 1.5f // también fija el tamaño de los bitmaps: subirlo cuesta RAM
        // Distancia normalizada (0 = centro, ~1 = borde) donde empieza y termina el encogimiento
        const val FISHEYE_START = 0.3f
        const val FISHEYE_END = 1.45f
    }
}

/** Posiciones de una espiral hexagonal por columnas (centro, anillo 1 con 6, anillo 2 con 12…). */
object HexSpiral {
    // Direcciones axiales de un hexágono
    private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(0, -1),
        intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, 1))

    fun positions(count: Int, spacing: Float): FloatArray {
        val out = FloatArray(count * 2)
        if (count == 0) return out
        val sq3 = sqrt(3f)
        var n = 0
        fun put(q: Int, r: Int) {
            if (n >= count) return
            // Ejes cambiados respecto a la rejilla "pointy-top": vecinos justo encima y debajo, como en el Apple Watch
            out[n * 2] = spacing * (sq3 / 2f) * r
            out[n * 2 + 1] = spacing * (q + r / 2f)
            n++
        }
        put(0, 0)
        var ring = 1
        while (n < count) {
            var q = DIRS[4][0] * ring
            var r = DIRS[4][1] * ring
            for (side in 0 until 6) {
                for (step in 0 until ring) {
                    put(q, r)
                    q += DIRS[side][0]
                    r += DIRS[side][1]
                }
            }
            ring++
        }
        return out
    }
}
