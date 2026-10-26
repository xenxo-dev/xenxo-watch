package io.xenxo

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.xenxo.gym.GymActivity
import io.xenxo.health.HealthActivity
import io.xenxo.padel.PadelActivity
import io.xenxo.tesla.TeslaActivity

/**
 * Hub Central xenxo OS para Xiaomi Watch 2 (466×466 Circular AMOLED).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 88, 32, 88)
        }

        val title = TextView(this).apply {
            text = "xenxo"
            textSize = 18f
            setTextColor(Color.parseColor("#30D158"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)

        container.addView(createSectionCard("🏋️ Gimnasio", "Rutinas, 1RM y series", "#30D158") {
            startActivity(Intent(this, GymActivity::class.java))
        })

        container.addView(createSectionCard("🎾 Pádel", "Marcador táctil con AOD", "#FF9F0A") {
            startActivity(Intent(this, PadelActivity::class.java))
        })

        container.addView(createSectionCard("❤️ Salud", "Pulso 12-canales y pasos", "#FF453A") {
            startActivity(Intent(this, HealthActivity::class.java))
        })

        container.addView(createSectionCard("🚗 Tesla", "Climatización y control", "#0A84FF") {
            startActivity(Intent(this, TeslaActivity::class.java))
        })

        scroll.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)
    }

    private fun createSectionCard(
        titleText: String,
        subText: String,
        accentColor: String,
        onClick: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 28f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 10, 20, 10)
            }
            layoutParams = lp

            val t = TextView(this@MainActivity).apply {
                text = titleText
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(t)

            val s = TextView(this@MainActivity).apply {
                text = subText
                textSize = 12f
                setTextColor(Color.parseColor(accentColor))
                setPadding(0, 4, 0, 0)
            }
            addView(s)

            setOnClickListener { onClick() }
        }
    }
}
