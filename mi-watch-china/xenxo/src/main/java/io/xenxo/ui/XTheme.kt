package io.xenxo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Paleta de colores y componentes comunes con estética inspirada en openGym (Apple/iOS Dark).
 * Cero dependencias externas. Diseñado para pantalla AMOLED cuadrada 368×448 (densidad 320 / 2.0x).
 */
object XTheme {

    // --- Paleta de colores ---
    const val BG_BLACK = 0xFF000000.toInt()        // Fondo negro puro AMOLED (0% consumo)
    const val CARD_BG = 0xFF1C1C1E.toInt()         // Gris oscuro estándar de tarjetas openGym/iOS
    const val CARD_BG_PRESS = 0xFF2C2C2E.toInt()   // Estado pulsado de tarjeta
    const val CARD_STROKE = 0xFF2C2C2E.toInt()     // Borde sutil de tarjeta
    const val SURFACE_BTN = 0xFF2C2C2E.toInt()     // Fondo de botón secundario
    const val SURFACE_BTN_PRESS = 0xFF3A3A3C.toInt()

    const val GREEN = 0xFF30D158.toInt()           // Acento principal openGym / iOS green
    const val GREEN_DARK = 0xFF28B84D.toInt()      // Estado pulsado del verde
    const val GREEN_SUBTLE = 0xFF122416.toInt()    // Fondo verde sutil para tarjetas seleccionadas/completadas
    const val GREEN_STROKE = 0xFF30D158.toInt()

    const val AMBER = 0xFFFF9F0A.toInt()           // Naranja/ámbar iOS (batería media, avisos, récord 1RM)
    const val AMBER_DARK = 0xFFD98200.toInt()
    const val AMBER_SUBTLE = 0xFF291D00.toInt()

    const val BLUE = 0xFF0A84FF.toInt()            // Azul iOS (pádel)
    const val BLUE_SUBTLE = 0xFF0A1E33.toInt()

    const val RED = 0xFFFF453A.toInt()             // Rojo iOS (batería baja, borrar, cancelar)
    const val RED_SUBTLE = 0xFF2E0D0B.toInt()

    const val PURPLE = 0xFFBF5AF2.toInt()          // Púrpura iOS (pierna)
    const val TEAL = 0xFF64D2FF.toInt()            // Cian/teal iOS (espalda)
    const val INDIGO = 0xFF5E5CE6.toInt()          // Índigo iOS (modo noche)
    const val PINK = 0xFFFF2D55.toInt()            // Rosa/magenta iOS / Apple Music (música)
    const val PINK_DARK = 0xFFD61A40.toInt()
    const val PINK_SUBTLE = 0xFF2A0D14.toInt()

    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()    // Blanco nítido
    const val TEXT_SECONDARY = 0xFF8E8E93.toInt()  // Gris secundario estándar iOS (#8E8E93)
    const val TEXT_TERTIARY = 0xFF636366.toInt()   // Gris terciario para notas discretas

    // --- Generadores de fondos y Drawables ---

    /** Crea un GradientDrawable con esquinas redondeadas. */
    fun roundRect(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeWidthDp: Float = 1f, d: Float): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radiusDp * d
            setColor(color)
            if (strokeColor != null) {
                setStroke((strokeWidthDp * d).toInt(), strokeColor)
            }
        }
    }

    /** Fondo para tarjetas con estado pulsado opcional. */
    fun cardStateList(
        d: Float,
        radiusDp: Float = 14f,
        normalColor: Int = CARD_BG,
        pressedColor: Int = CARD_BG_PRESS,
        strokeColor: Int? = CARD_STROKE,
        strokeWidthDp: Float = 1f
    ): StateListDrawable {
        return StateListDrawable().apply {
            val pressed = roundRect(pressedColor, radiusDp, strokeColor, strokeWidthDp, d)
            val normal = roundRect(normalColor, radiusDp, strokeColor, strokeWidthDp, d)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /** Fondo de botón píldora (esquinas completamente redondeadas). */
    fun pillButtonDrawable(
        d: Float,
        primary: Boolean = true,
        normalColor: Int = if (primary) GREEN else SURFACE_BTN,
        pressedColor: Int = if (primary) GREEN_DARK else SURFACE_BTN_PRESS
    ): StateListDrawable {
        return StateListDrawable().apply {
            val pressed = roundRect(pressedColor, 22f, null, 0f, d)
            val normal = roundRect(normalColor, 22f, null, 0f, d)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /** Botón circular (ej. stepper +, -). */
    fun circleButtonDrawable(
        d: Float,
        normalColor: Int = SURFACE_BTN,
        pressedColor: Int = SURFACE_BTN_PRESS
    ): StateListDrawable {
        return StateListDrawable().apply {
            val pressed = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pressedColor) }
            val normal = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(normalColor) }
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // --- Constructores de UI comunes ---

    /** Título de marca superior ("XENXO · GIMNASIO", etc.). */
    fun brandTitle(context: Context, text: String): TextView {
        val d = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            setTextColor(TEXT_SECONDARY)
            textSize = 11f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * d).toInt())
        }
    }

    /** Cabecera de sección en mayúsculas ("PERFILES DE ENERGÍA", etc.). */
    fun sectionHeader(context: Context, text: String): TextView {
        val d = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text.uppercase()
            setTextColor(TEXT_SECONDARY)
            textSize = 11f
            letterSpacing = 0.12f
            setPadding((4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
        }
    }

    /** Botón píldora grande estilo openGym (verde con texto negro, o gris con texto blanco). */
    fun pillButton(
        context: Context,
        text: String,
        primary: Boolean = true,
        minHeightDp: Float = 42f,
        textSizeSp: Float = 14f,
        onClick: () -> Unit
    ): TextView {
        val d = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = textSizeSp
            gravity = Gravity.CENTER
            setTextColor(if (primary) Color.BLACK else TEXT_PRIMARY)
            minimumHeight = (minHeightDp * d).toInt()
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            background = pillButtonDrawable(d, primary)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (6 * d).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    /** Badge cuadrado redondeado con icono/emoji (estilo iOS/openGym). */
    fun iconBadge(
        context: Context,
        glyph: String,
        bgColor: Int,
        sizeDp: Float = 32f,
        radiusDp: Float = 8f,
        textSizeSp: Float = 15f
    ): TextView {
        val d = context.resources.displayMetrics.density
        val sz = (sizeDp * d).toInt()
        return TextView(context).apply {
            text = glyph
            textSize = textSizeSp
            gravity = Gravity.CENTER
            background = roundRect(bgColor, radiusDp, null, 0f, d)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                rightMargin = (10 * d).toInt()
            }
        }
    }

    /** Tarjeta estándar con esquinas redondeadas 14dp y fondo #1C1C1E. */
    fun card(
        context: Context,
        normalColor: Int = CARD_BG,
        strokeColor: Int? = CARD_STROKE,
        radiusDp: Float = 14f,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val d = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = cardStateList(d, radiusDp = radiusDp, normalColor = normalColor, strokeColor = strokeColor)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (6 * d).toInt()
            }
            if (onClick != null) {
                setOnClickListener { onClick() }
            }
        }
    }

    /** Fila de tarjeta con icono en cuadrado redondeado, título y subtítulo opcional. */
    fun iconRowCard(
        context: Context,
        iconGlyph: String,
        iconBg: Int,
        title: String,
        subtitle: String? = null,
        trailingText: String? = null,
        trailingColor: Int = TEXT_SECONDARY,
        badgeSizeDp: Float = 32f,
        isSelected: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val d = context.resources.displayMetrics.density
        val stroke = if (isSelected) GREEN_STROKE else CARD_STROKE
        val bg = if (isSelected) GREEN_SUBTLE else CARD_BG
        val root = card(context, normalColor = bg, strokeColor = stroke, onClick = onClick)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(iconBadge(context, iconGlyph, iconBg, sizeDp = badgeSizeDp))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        textCol.addView(TextView(context).apply {
            text = title
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
        })

        if (!subtitle.isNullOrEmpty()) {
            textCol.addView(TextView(context).apply {
                text = subtitle
                setTextColor(TEXT_SECONDARY)
                textSize = 12f
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        }

        row.addView(textCol)

        if (!trailingText.isNullOrEmpty()) {
            row.addView(TextView(context).apply {
                text = trailingText
                setTextColor(trailingColor)
                textSize = 13f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding((6 * d).toInt(), 0, 0, 0)
            })
        } else if (isSelected) {
            row.addView(TextView(context).apply {
                text = "●"
                setTextColor(GREEN)
                textSize = 12f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding((6 * d).toInt(), 0, 0, 0)
            })
        }

        root.addView(row)
        return root
    }
}
