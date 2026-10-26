package io.xenxo.wristai

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * xenxo Wrist AI Circular para Xiaomi Watch 2 (466×466).
 * Asistente de voz por IA con Orbe Aurora animado y respuestas en pantalla circular.
 */
class MainActivity : Activity() {

    private lateinit var orbView: VoiceOrbView
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Orbe centrado en la pantalla circular
        orbView = VoiceOrbView(this).apply {
            layoutParams = FrameLayout.LayoutParams(340, 340, Gravity.CENTER)
            setOnClickListener { handleOrbTap() }
        }
        root.addView(orbView)

        // Contenedor de textos alineados verticalmente
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                setMargins(40, 0, 40, 50)
            }
        }

        tvStatus = TextView(this).apply {
            text = "Toca el orbe para hablar"
            textSize = 13f
            setTextColor(Color.parseColor("#30D158"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        textContainer.addView(tvStatus)

        tvTranscript = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
            maxLines = 3
        }
        textContainer.addView(tvTranscript)

        root.addView(textContainer)
        setContentView(root)
    }

    private fun handleOrbTap() {
        when (orbView.state) {
            VoiceOrbView.State.IDLE -> {
                orbView.state = VoiceOrbView.State.LISTENING
                tvStatus.text = "Escuchando…"
                tvStatus.setTextColor(Color.parseColor("#0A84FF"))
                tvTranscript.text = ""

                // Simulación de interacción de voz (Groq Whisper)
                handler.postDelayed({
                    orbView.state = VoiceOrbView.State.THINKING
                    tvStatus.text = "Pensando…"
                    tvStatus.setTextColor(Color.parseColor("#BF5AF2"))
                    tvTranscript.text = "«¿Cuántos pasos llevo hoy?»"

                    handler.postDelayed({
                        orbView.state = VoiceOrbView.State.SPEAKING
                        tvStatus.text = "Respondiendo…"
                        tvStatus.setTextColor(Color.parseColor("#FF9F0A"))
                        tvTranscript.text = "Llevas 5.240 pasos hoy, el 52% de tu meta diaria."

                        handler.postDelayed({
                            orbView.state = VoiceOrbView.State.IDLE
                            tvStatus.text = "Toca para preguntar"
                            tvStatus.setTextColor(Color.parseColor("#30D158"))
                        }, 4000)
                    }, 800)
                }, 2200)
            }
            VoiceOrbView.State.LISTENING,
            VoiceOrbView.State.SPEAKING -> {
                orbView.state = VoiceOrbView.State.IDLE
                tvStatus.text = "Toca para preguntar"
                tvStatus.setTextColor(Color.parseColor("#30D158"))
                handler.removeCallbacksAndMessages(null)
            }
            VoiceOrbView.State.THINKING -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
