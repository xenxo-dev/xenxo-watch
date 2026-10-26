package io.xenxo.remote

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Mando Multimedia y de Presentaciones para Xiaomi Mi Watch (368×448 AMOLED).
 *
 * Características:
 * 1. Control de diapositivas o pistas musicales (Siguiente, Anterior, Play/Pause).
 * 2. Control de volumen mediante la corona rotatoria con clic háptico en cada paso.
 * 3. Botones táctiles grandes y contraste alto para pulsar sin mirar la pantalla.
 */
class RemoteActivity : Activity() {

    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val d = resources.displayMetrics.density

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (18 * d).toInt())
        }

        // TÍTULO
        val lblTitle = TextView(this).apply {
            text = "📱 Mando Multimedia"
            textSize = 15f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * d).toInt())
        }
        layout.addView(lblTitle)

        val subTitle = TextView(this).apply {
            text = "Gira la corona para el volumen"
            textSize = 9.5f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (14 * d).toInt())
        }
        layout.addView(subTitle)

        // BOTÓN PLAY / PAUSE CENTRAL GIGANTE
        val btnPlayPause = TextView(this).apply {
            text = "⏯️ PLAY / PAUSA"
            textSize = 14f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF2C2C2E.toInt())
                cornerRadius = 12 * d
                setStroke((1.5f * d).toInt(), 0xFFAF52DE.toInt()) // Púrpura
            }
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = p

            setOnClickListener {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
        layout.addView(btnPlayPause)

        // FILA ANTERIOR / SIGUIENTE
        val rowNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * d).toInt()
            }
            layoutParams = p
        }

        val btnPrev = TextView(this).apply {
            text = "⏮️ Anterior"
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadius = 10 * d
                setStroke((1 * d).toInt(), 0xFF333333.toInt())
            }
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            setOnClickListener {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }
        val p1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = (5 * d).toInt()
        }
        rowNav.addView(btnPrev, p1)

        val btnNext = TextView(this).apply {
            text = "Siguiente ⏭️"
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadius = 10 * d
                setStroke((1 * d).toInt(), 0xFF333333.toInt())
            }
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            setOnClickListener {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        }
        val p2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (5 * d).toInt()
        }
        rowNav.addView(btnNext, p2)
        layout.addView(rowNav)

        // FILA VOLUMEN RÁPIDO
        val rowVol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * d).toInt()
            }
            layoutParams = p
        }

        val btnVolDown = TextView(this).apply {
            text = "🔉 -"
            textSize = 13f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadius = 8 * d
            }
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            setOnClickListener {
                adjustVolume(AudioManager.ADJUST_LOWER)
            }
        }
        val p3 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = (5 * d).toInt()
        }
        rowVol.addView(btnVolDown, p3)

        val btnVolUp = TextView(this).apply {
            text = "🔊 +"
            textSize = 13f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF1C1C1E.toInt())
                cornerRadius = 8 * d
            }
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            setOnClickListener {
                adjustVolume(AudioManager.ADJUST_RAISE)
            }
        }
        val p4 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (5 * d).toInt()
        }
        rowVol.addView(btnVolUp, p4)
        layout.addView(rowVol)

        root.addView(layout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
    }

    private fun sendMediaKey(keyCode: Int) {
        vibrator?.vibrate(25)
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager?.dispatchMediaKeyEvent(down)
        audioManager?.dispatchMediaKeyEvent(up)
    }

    private fun adjustVolume(direction: Int) {
        vibrator?.vibrate(15)
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    /** Corona rotatoria para subir/bajar volumen */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            (event.source and InputDevice.SOURCE_ROTARY_ENCODER) != 0
        ) {
            val axis = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (axis > 0.1f) {
                adjustVolume(AudioManager.ADJUST_RAISE)
                return true
            } else if (axis < -0.1f) {
                adjustVolume(AudioManager.ADJUST_LOWER)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
