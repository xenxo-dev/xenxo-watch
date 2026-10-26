package io.xenxo.launcher.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.util.Calendar
import java.util.Locale

/**
 * Esfera Circular Oficial xenxo para Xiaomi Watch 2 / 2 Pro (466×466 AMOLED).
 * Basada en WallpaperService nativo de Android: cero dependencias, máxima compatibilidad en Wear OS 4.
 */
class XenxoCircularFaceService : WallpaperService() {

    override fun onCreateEngine(): Engine = CircularEngine()

    inner class CircularEngine : Engine() {

        private val cal = Calendar.getInstance()
        private var isVisible = false

        private val bgPaint = Paint().apply { color = Color.BLACK }
        private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 76f
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E8E93")
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#30D158")
            textSize = 24f
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        private val arcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#1C1C1E")
        }
        private val arcStepsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#30D158") // Verde xenxo
        }
        private val arcBatteryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#0A84FF") // Azul batería
        }

        private val arcBoundsOuter = RectF()
        private val arcBoundsInner = RectF()

        private var batteryPct = 85
        private var steps = 5240
        private val stepGoal = 10000

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100) / scale
                    drawFrame()
                }
            }
        }
        private var registeredBattery = false

        private val handler = Handler(Looper.getMainLooper())
        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (isVisible) {
                    val delay = 1000 - (System.currentTimeMillis() % 1000)
                    handler.postDelayed(this, delay)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            if (visible) {
                if (!registeredBattery) {
                    registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    registeredBattery = true
                }
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
                if (registeredBattery) {
                    unregisterReceiver(batteryReceiver)
                    registeredBattery = false
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val pad1 = 28f
            arcBoundsOuter.set(pad1, pad1, width - pad1, height - pad1)

            val pad2 = 48f
            arcBoundsInner.set(pad2, pad2, width - pad2, height - pad2)
            drawFrame()
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val bounds = holder.surfaceFrame
                    onDraw(canvas, bounds)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun onDraw(canvas: Canvas, bounds: Rect) {
            cal.timeInMillis = System.currentTimeMillis()
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()

            // Fondo negro puro AMOLED (#000000)
            canvas.drawRect(bounds, bgPaint)

            // Anillos perimetrales circulares
            // Pasos (exterior)
            canvas.drawArc(arcBoundsOuter, 135f, 270f, false, arcBgPaint)
            val stepAngle = (steps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f) * 270f
            canvas.drawArc(arcBoundsOuter, 135f, stepAngle, false, arcStepsPaint)

            // Batería (interior)
            canvas.drawArc(arcBoundsInner, 135f, 270f, false, arcBgPaint)
            val batAngle = (batteryPct.toFloat() / 100f).coerceIn(0f, 1f) * 270f
            canvas.drawArc(arcBoundsInner, 135f, batAngle, false, arcBatteryPaint)

            // Fecha superior
            val dayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())?.uppercase() ?: ""
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())?.uppercase() ?: ""
            canvas.drawText("$dayName $dayNum $monthName", cx, cy - 65f, datePaint)

            // Hora digital central
            val hour = String.format(Locale.ROOT, "%02d", cal.get(Calendar.HOUR_OF_DAY))
            val min = String.format(Locale.ROOT, "%02d", cal.get(Calendar.MINUTE))
            canvas.drawText("$hour:$min", cx, cy + 24f, timePaint)

            // Métricas inferiores (pasos y batería)
            metricPaint.color = Color.parseColor("#30D158")
            canvas.drawText("👟 $steps  ·  ⚡ $batteryPct%", cx, cy + 95f, metricPaint)
        }
    }
}
