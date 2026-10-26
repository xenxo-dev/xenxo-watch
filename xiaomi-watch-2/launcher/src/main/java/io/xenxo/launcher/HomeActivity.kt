package io.xenxo.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.atan2

/**
 * xenxo Launcher Circular para Xiaomi Watch 2 / 2 Pro (466×466 AMOLED).
 * Integra detector de rueda táctil perimetral (Touch-Wheel): deslizar el dedo
 * por el borde circular de la pantalla desplaza la lista de forma ultra-fluida.
 */
class HomeActivity : Activity() {

    private lateinit var scroll: ScrollView
    private lateinit var container: LinearLayout
    private var lastAngle = 0.0
    private var isDraggingPerimeter = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Padding generoso arriba y abajo para adaptarse a la pantalla circular 466x466
            setPadding(32, 96, 32, 96)
        }

        scroll.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        container.removeAllViews()

        val header = TextView(this).apply {
            text = "xenxo OS"
            textSize = 15f
            setTextColor(Color.parseColor("#30D158"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(header)

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        for (app in apps) {
            val label = app.loadLabel(pm).toString()
            val icon = app.loadIcon(pm)
            val pkg = app.activityInfo.packageName
            val cls = app.activityInfo.name

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 16, 24, 16)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C1C1E"))
                    cornerRadius = 28f
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 8, 16, 8)
                }
                layoutParams = lp

                val img = ImageView(this@HomeActivity).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(54, 54).apply {
                        setMargins(0, 0, 16, 0)
                    }
                }
                addView(img)

                val txt = TextView(this@HomeActivity).apply {
                    text = label
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                addView(txt)

                setOnClickListener {
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        setClassName(pkg, cls)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    startActivity(launchIntent)
                }
            }
            container.addView(row)
        }
    }

    /**
     * Emulación de rueda rotatoria virtual para Xiaomi Watch 2:
     * Al tocar el perímetro exterior (>180 px del centro), el ángulo polar mueve la lista.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val cx = scroll.width / 2f
        val cy = scroll.height / 2f
        val dx = ev.x - cx
        val dy = ev.y - cy
        val dist = Math.hypot(dx.toDouble(), dy.toDouble())

        if (dist > 160) {
            val angle = atan2(dy.toDouble(), dx.toDouble())
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastAngle = angle
                    isDraggingPerimeter = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingPerimeter) {
                        var delta = angle - lastAngle
                        if (delta > Math.PI) delta -= 2 * Math.PI
                        if (delta < -Math.PI) delta += 2 * Math.PI
                        scroll.scrollBy(0, (delta * 220).toInt())
                        lastAngle = angle
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingPerimeter = false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
