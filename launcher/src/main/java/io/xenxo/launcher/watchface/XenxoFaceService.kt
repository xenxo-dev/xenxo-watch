// La librería wearable support está obsoleta para Wear OS 3+, pero es la que corresponde a Wear OS 2 (Android 9).
@file:Suppress("DEPRECATION")

package io.xenxo.launcher.watchface

import android.content.ComponentName
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.wearable.complications.ComplicationData
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import io.xenxo.launcher.HomeActivity
import java.util.Calendar

/**
 * Base de las esferas xenxo. Batería ante todo:
 *  - Sin segundos: solo se repinta en onTimeTick (una vez por minuto, también en ambiente) o al llegar datos.
 *  - Sin Handler ni animaciones; los datos llegan por complicaciones y el podómetro solo escucha con la esfera visible.
 *  - En ambiente: sin antialias si la pantalla es low-bit y desplazamiento de unos px si pide protección anti-quemado.
 * Cada esfera solo implementa [drawFace] y, si quiere, [tapZone].
 */
abstract class XenxoFaceService : CanvasWatchFaceService() {

    /** Pinta la esfera completa en coordenadas de 368×448 (el diseño de docs/ está en esos px). */
    abstract fun drawFace(e: FaceEngine, c: Canvas, now: Calendar)

    /** true = no pintar el indicador de notificaciones sin leer de Wear OS (círculo arriba en el centro). */
    open val hideNotificationIndicator = false

    /** Qué abrir al tocar (x, y): por defecto, mitad superior = launcher, inferior = Salud xenxo. */
    open fun tapZone(x: Int, y: Int): Intent? = if (y < 200) launcherIntent() else healthIntent()

    override fun onCreateEngine(): Engine = FaceEngine()

    protected fun launcherIntent() = Intent(this, HomeActivity::class.java)
    protected fun healthIntent() = Intent().setComponent(ComponentName("io.xenxo", "io.xenxo.HealthActivity"))
    protected fun gymIntent() = Intent().setComponent(ComponentName("io.xenxo", "io.xenxo.GymActivity"))
    protected fun energyIntent() = Intent().setComponent(ComponentName("io.xenxo", "io.xenxo.EnergyActivity"))
    protected fun fitnessIntent(activity: String = ".ui.MainActivity") =
        Intent().setComponent(ComponentName(FaceData.FITNESS,
            if (activity.startsWith(".")) FaceData.FITNESS + activity else activity))

    inner class FaceEngine : CanvasWatchFaceService.Engine() {
        lateinit var data: FaceData
        var ambient = false; private set
        private var lowBit = false
        private var burnIn = false
        /** Paints de la esfera: se les quita el antialias en ambiente low-bit. */
        val paints = mutableListOf<Paint>()

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setWatchFaceStyle(WatchFaceStyle.Builder(this@XenxoFaceService)
                .setAcceptsTapEvents(true)
                .setHideNotificationIndicator(hideNotificationIndicator)
                .build())
            data = FaceData(this@XenxoFaceService) { invalidate() }
            data.attach(this)
        }

        override fun onPropertiesChanged(p: Bundle) {
            super.onPropertiesChanged(p)
            lowBit = p.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            burnIn = p.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onAmbientModeChanged(inAmbient: Boolean) {
            super.onAmbientModeChanged(inAmbient)
            ambient = inAmbient
            val aa = !(inAmbient && lowBit)
            paints.forEach { it.isAntiAlias = aa }
            invalidate()
        }

        override fun onTimeTick() {
            super.onTimeTick()
            data.refresh()
            invalidate()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                data.start()
                data.refresh()
                invalidate()
            } else {
                data.stop()
            }
        }

        override fun onComplicationDataUpdate(id: Int, d: ComplicationData) {
            data.onComplication(id, d)
        }

        override fun onDestroy() {
            data.destroy()
            super.onDestroy()
        }

        override fun onTapCommand(type: Int, x: Int, y: Int, eventTime: Long) {
            if (type != WatchFaceService.TAP_TYPE_TAP) return
            val sx = x * DESIGN_W / surfaceHolder.surfaceFrame.width().coerceAtLeast(1)
            val sy = y * DESIGN_H / surfaceHolder.surfaceFrame.height().coerceAtLeast(1)
            tapZone(sx, sy)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }

        override fun onDraw(c: Canvas, bounds: Rect) {
            c.drawColor(android.graphics.Color.BLACK)
            val now = Calendar.getInstance()
            c.save()
            c.scale(bounds.width() / DESIGN_W.toFloat(), bounds.height() / DESIGN_H.toFloat())
            if (ambient && burnIn) {
                // Desplazamiento de ±4 px que cambia cada minuto para no quemar siempre los mismos píxeles
                val m = now.get(Calendar.MINUTE)
                c.translate(((m % 5) - 2) * 2f, (((m / 5) % 3) - 1) * 2f)
            }
            drawFace(this, c, now)
            c.restore()
        }
    }

    companion object {
        const val DESIGN_W = 368
        const val DESIGN_H = 448
    }
}
