package io.xenxo.padel

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Marcador de Pádel Circular xenxo para Xiaomi Watch 2 (466×466).
 * Botones gigantes táctiles divididos simétricamente para marcar puntos sin mirar la pantalla.
 */
class PadelActivity : Activity() {

    private var scoreUs = 0
    private var scoreThem = 0
    private var gamesUs = 0
    private var gamesThem = 0

    private val pointLabels = arrayOf("0", "15", "30", "40", "V")
    private lateinit var tvScoreUs: TextView
    private lateinit var tvScoreThem: TextView
    private lateinit var tvGames: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(24, 40, 24, 40)
        }

        // Título y juegos
        tvGames = TextView(this).apply {
            text = "SET 1 · $gamesUs - $gamesThem"
            textSize = 16f
            setTextColor(Color.parseColor("#FF9F0A"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(tvGames)

        // Contenedor de puntos (dos tarjetas circulares gemelas)
        val scoreRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // Tarjeta Nosotros
        val cardUs = createTeamCard("NOSOTROS", "#30D158") {
            addPointUs()
        }
        tvScoreUs = cardUs.findViewById(101)
        scoreRow.addView(cardUs, LinearLayout.LayoutParams(0, 220, 1f).apply { setMargins(8, 0, 8, 0) })

        // Tarjeta Rivales
        val cardThem = createTeamCard("RIVALES", "#0A84FF") {
            addPointThem()
        }
        tvScoreThem = cardThem.findViewById(101)
        scoreRow.addView(cardThem, LinearLayout.LayoutParams(0, 220, 1f).apply { setMargins(8, 0, 8, 0) })

        root.addView(scoreRow)

        // Botón Deshacer
        val btnUndo = Button(this).apply {
            text = "↺ Deshacer punto"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            background = null
            setOnClickListener {
                if (scoreUs > 0) scoreUs--
                else if (scoreThem > 0) scoreThem--
                updateUI()
            }
        }
        root.addView(btnUndo)

        setContentView(root)
    }

    private fun createTeamCard(label: String, colorHex: String, onPoint: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor(colorHex))
            }
            setPadding(12, 16, 12, 16)

            val tvLabel = TextView(this@PadelActivity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor(colorHex))
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(tvLabel)

            val tvScore = TextView(this@PadelActivity).apply {
                id = 101
                text = "0"
                textSize = 58f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(tvScore)

            setOnClickListener { onPoint() }
        }
    }

    private fun addPointUs() {
        if (scoreUs < 3) {
            scoreUs++
        } else {
            // Juego ganado
            gamesUs++
            scoreUs = 0
            scoreThem = 0
        }
        updateUI()
    }

    private fun addPointThem() {
        if (scoreThem < 3) {
            scoreThem++
        } else {
            gamesThem++
            scoreUs = 0
            scoreThem = 0
        }
        updateUI()
    }

    private fun updateUI() {
        tvScoreUs.text = pointLabels[scoreUs.coerceIn(0, 3)]
        tvScoreThem.text = pointLabels[scoreThem.coerceIn(0, 3)]
        tvGames.text = "SET 1 · $gamesUs - $gamesThem"
    }
}
