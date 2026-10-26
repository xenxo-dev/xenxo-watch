package io.xenxo.launcher.watchface

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import java.util.Calendar

/**
 * Actividad de previsualización en vivo e interactiva de la esfera xenxo Ultra.
 */
class UltraFacePreviewActivity : Activity() {

    private lateinit var face: XenxoUltraFace
    private lateinit var engine: XenxoFaceService.FaceEngine
    private lateinit var imageView: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        face = XenxoUltraFace()
        val attachBase = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBase.isAccessible = true
        attachBase.invoke(face, this)

        engine = face.onCreateEngine() as XenxoFaceService.FaceEngine
        engine.data = FaceData(this) { renderFace() }
        engine.data.start()

        val w = XenxoFaceService.DESIGN_W
        val h = XenxoFaceService.DESIGN_H
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)

        imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        imageView.setOnTouchListener { _, ev ->
            if (ev.action == android.view.MotionEvent.ACTION_UP) {
                val sx = (ev.x * w / imageView.width).toInt()
                val sy = (ev.y * h / imageView.height).toInt()
                face.tapZone(sx, sy)?.let {
                    runCatching { startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            true
        }

        setContentView(imageView)
    }

    override fun onResume() {
        super.onResume()
        engine.data.refresh()
        renderFace()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.data.stop()
    }

    private fun renderFace() {
        val w = XenxoFaceService.DESIGN_W
        val h = XenxoFaceService.DESIGN_H
        canvas.drawColor(Color.BLACK)

        face.drawFace(engine, canvas, Calendar.getInstance())
        imageView.setImageBitmap(bitmap)
    }
}
