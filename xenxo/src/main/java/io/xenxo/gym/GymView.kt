package io.xenxo.gym

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.xenxo.ui.XTheme
import java.util.Calendar

/**
 * Vista principal de entrenamiento de gimnasio (:gym).
 * Estética inspirada en openGym con tarjetas #1C1C1E, esquinas redondeadas y acento verde #30D158.
 * Carga la rutina del día según planning-gym.md o permite elegir cualquier día.
 */
class GymView(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val db = GymDb(context)

    private val scroll = object : ScrollView(context) {
        override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                smoothScrollBy(0, (-ev.getAxisValue(MotionEvent.AXIS_SCROLL) * 60 * d).toInt())
                return true
            }
            return super.onGenericMotionEvent(ev)
        }
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt(), (40 * d).toInt())
    }

    private val overlayContainer = FrameLayout(context)

    // Día seleccionado (por defecto el día actual de la semana)
    private var selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    private var syncReceiverRegistered = false
    private val syncReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            post { refresh() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!syncReceiverRegistered) {
            try {
                val filter = android.content.IntentFilter().apply {
                    addAction("io.xenxo.PLAN_UPDATED")
                    addAction("io.xenxo.PROFILE_UPDATED")
                }
                context.registerReceiver(syncReceiver, filter)
                syncReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (syncReceiverRegistered) {
            try {
                context.unregisterReceiver(syncReceiver)
                syncReceiverRegistered = false
            } catch (_: Exception) {}
        }
    }

    init {
        setBackgroundColor(XTheme.BG_BLACK)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content)
        addView(scroll, LayoutParams(-1, -1))
        addView(overlayContainer, LayoutParams(-1, -1))
        refresh()
    }

    fun refresh() {
        content.removeAllViews()

        // 1. Título de sección y selector de día
        content.addView(XTheme.brandTitle(context, "XENXOGYM"))

        // Carrusel de días de la semana (L M X J V S D)
        content.addView(dayCarousel())

        // Fila Día con flechas para cambiar de rutina
        val dayName = getDayTitle(selectedDay)
        val dayRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * d).toInt())
        }
        dayRow.addView(arrowBtn("◀") { prevDay() })
        dayRow.addView(TextView(context).apply {
            text = dayName
            setTextColor(XTheme.TEXT_PRIMARY)
            textSize = 15f
            gravity = Gravity.CENTER
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        dayRow.addView(arrowBtn("▶") { nextDay() })
        content.addView(dayRow)

        // Acceso al marcador de pádel
        content.addView(padelCard())

        // 2. Tarjeta de Peso en Ayunas
        val bodyWeight = db.getLastBodyWeight()
        content.addView(bodyWeightCard(bodyWeight))

        // 3. Lista de ejercicios del día
        val exercises = db.getExercisesForDay(selectedDay)

        if (!db.hasExercises()) {
            content.addView(emptyPlanCard())
        } else if (exercises.isEmpty()) {
            content.addView(restDayCard())
        } else {
            content.addView(XTheme.sectionHeader(context, "EJERCICIOS (${exercises.size})"))

            for (ex in exercises) {
                content.addView(exerciseCard(ex))
            }
        }

        // Gestión y opciones de rutina al final
        content.addView(routineOptionsCard())
    }

    private fun dayCarousel(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * d).toInt())
        }
        val days = listOf(
            Pair(Calendar.MONDAY, "L"),
            Pair(Calendar.TUESDAY, "M"),
            Pair(Calendar.WEDNESDAY, "X"),
            Pair(Calendar.THURSDAY, "J"),
            Pair(Calendar.FRIDAY, "V"),
            Pair(Calendar.SATURDAY, "S"),
            Pair(Calendar.SUNDAY, "D")
        )
        for ((calDay, letter) in days) {
            val hasRoutine = db.hasExercisesForDay(calDay)
            val isSelected = selectedDay == calDay
            val btn = TextView(context).apply {
                text = letter
                textSize = 10f
                gravity = Gravity.CENTER
                val size = (20 * d).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    leftMargin = (1.5f * d).toInt()
                    rightMargin = (1.5f * d).toInt()
                }
                setTextColor(when {
                    isSelected -> Color.BLACK
                    hasRoutine -> XTheme.TEXT_PRIMARY
                    else -> XTheme.TEXT_TERTIARY
                })
                background = when {
                    isSelected -> GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(XTheme.GREEN) }
                    hasRoutine -> GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(XTheme.CARD_BG)
                        setStroke((1 * d).toInt(), XTheme.CARD_STROKE)
                    }
                    else -> GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF141416.toInt())
                    }
                }
                setOnClickListener {
                    if (selectedDay != calDay) {
                        selectedDay = calDay
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                        scroll.scrollTo(0, 0)
                        refresh()
                    }
                }
            }
            row.addView(btn)
        }
        return row
    }

    private fun bodyWeightCard(currentWeight: Float) = XTheme.iconRowCard(
        context = context,
        iconGlyph = "⚖️",
        iconBg = XTheme.AMBER,
        title = "Peso en ayunas",
        trailingText = "${kg(currentWeight)} kg",
        trailingColor = XTheme.GREEN
    ) {
        openBodyWeightDialog(currentWeight)
    }

    private fun padelCard() = XTheme.iconRowCard(
        context = context,
        iconGlyph = "🎾",
        iconBg = XTheme.BLUE,
        title = "Pádel",
        subtitle = "Abrir marcador oficial",
        trailingText = "›",
        trailingColor = XTheme.BLUE
    ) {
        context.startActivity(Intent(context, io.xenxo.padel.PadelActivity::class.java))
    }

    private fun emptyPlanCard() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding((14 * d).toInt(), (18 * d).toInt(), (14 * d).toInt(), (18 * d).toInt())
        background = XTheme.cardStateList(d, radiusDp = 14f, normalColor = XTheme.CARD_BG, strokeColor = XTheme.GREEN_STROKE)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (8 * d).toInt()
            bottomMargin = (12 * d).toInt()
        }

        addView(TextView(context).apply {
            text = "Sin rutina programada"
            setTextColor(XTheme.TEXT_PRIMARY)
            textSize = 15f
            gravity = Gravity.CENTER
        })

        addView(TextView(context).apply {
            text = "Sincroniza tus rutinas desde xenxoHub en el móvil, o carga la rutina de ejemplo."
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (12 * d).toInt())
        })

        addView(XTheme.pillButton(context, "Cargar rutina de ejemplo (4 días)", primary = true, minHeightDp = 38f) {
            db.seedExampleRoutine()
            refresh()
        })
    }

    private fun restDayCard() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding((14 * d).toInt(), (18 * d).toInt(), (14 * d).toInt(), (18 * d).toInt())
        background = XTheme.cardStateList(d, radiusDp = 14f, normalColor = XTheme.CARD_BG, strokeColor = XTheme.GREEN_STROKE)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (8 * d).toInt()
            bottomMargin = (12 * d).toInt()
        }

        addView(TextView(context).apply {
            text = "●  Día de Descanso"
            setTextColor(XTheme.GREEN)
            textSize = 17f
            gravity = Gravity.CENTER
        })

        val goal = io.xenxo.profile.UserProfile.get(context).stepGoal
        addView(TextView(context).apply {
            text = "Objetivo: ${goal} pasos\nMovilidad suave o recuperación"
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (12 * d).toInt())
        })

        val nextRoutineDay = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
            .firstOrNull { it != selectedDay && db.hasExercisesForDay(it) }
        if (nextRoutineDay != null) {
            addView(XTheme.pillButton(context, "Ver rutina del ${getDayName(nextRoutineDay)}", primary = true, minHeightDp = 38f) {
                selectedDay = nextRoutineDay
                refresh()
            })
        }
    }

    private fun routineOptionsCard() = XTheme.iconRowCard(
        context = context,
        iconGlyph = "⚙️",
        iconBg = XTheme.CARD_BG_PRESS,
        title = "Opciones de rutina",
        subtitle = "Cargar ejemplo o vaciar",
        trailingText = "›",
        trailingColor = XTheme.TEXT_SECONDARY
    ) {
        openRoutineOptionsDialog()
    }

    private fun openRoutineOptionsDialog() {
        val dialog = FrameLayout(context).apply {
            setBackgroundColor(XTheme.BG_BLACK)
            isClickable = true
            isFocusable = true
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt())
            }
            col.addView(TextView(context).apply {
                text = "GESTIÓN DE RUTINAS"
                setTextColor(XTheme.TEXT_SECONDARY)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (14 * d).toInt())
            })
            col.addView(XTheme.pillButton(context, "Cargar rutina de ejemplo", primary = true, minHeightDp = 38f) {
                db.seedExampleRoutine()
                overlayContainer.removeAllViews()
                refresh()
            })
            col.addView(LinearLayout(context).apply { layoutParams = LinearLayout.LayoutParams(-1, (8 * d).toInt()) })
            col.addView(XTheme.pillButton(context, "Vaciar rutinas", primary = false, minHeightDp = 38f) {
                db.clearAllExercises()
                overlayContainer.removeAllViews()
                refresh()
            })
            col.addView(LinearLayout(context).apply { layoutParams = LinearLayout.LayoutParams(-1, (8 * d).toInt()) })
            col.addView(TextView(context).apply {
                text = "DESCANSO POR DEFECTO"
                setTextColor(XTheme.TEXT_SECONDARY)
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, (6 * d).toInt(), 0, (4 * d).toInt())
            })
            val restRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            listOf(60, 90, 120, 180).forEach { secs ->
                restRow.addView(XTheme.pillButton(context, "${secs}s", primary = false, minHeightDp = 30f, textSizeSp = 11f) {
                    db.updateAllExercisesRest(secs)
                    Toast.makeText(context, "Descanso fijado a ${secs} s", Toast.LENGTH_SHORT).show()
                    overlayContainer.removeAllViews()
                    refresh()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (4 * d).toInt() }
                })
            }
            col.addView(restRow)
            col.addView(LinearLayout(context).apply { layoutParams = LinearLayout.LayoutParams(-1, (10 * d).toInt()) })
            col.addView(XTheme.pillButton(context, "Cerrar", primary = false, minHeightDp = 36f) {
                overlayContainer.removeAllViews()
            })
            addView(col, LayoutParams(-1, -1))
        }
        overlayContainer.removeAllViews()
        overlayContainer.addView(dialog)
    }

    private fun exerciseCard(ex: Exercise): LinearLayout {
        val todaySets = db.getTodaySetsForExercise(ex.id)
        val isDone = todaySets.size >= ex.targetSets
        val bg = if (isDone) XTheme.GREEN_SUBTLE else XTheme.CARD_BG
        val stroke = if (isDone) XTheme.GREEN_STROKE else XTheme.CARD_STROKE

        val card = XTheme.card(context, normalColor = bg, strokeColor = stroke) {
            val nextSetNum = todaySets.size + 1
            val lastSet = db.getLastSetForExercise(ex.id)
            val prefillReps = lastSet?.reps ?: 10
            val prefillWeight = lastSet?.weightKg ?: ex.defaultWeightKg
            openSetLogger(ex, nextSetNum, prefillReps, prefillWeight)
        }

        // Fila 1: [Grupo] Nombre [✓]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeColor = when (ex.group) {
            "Pecho" -> XTheme.AMBER
            "Espalda" -> XTheme.TEAL
            "Hombros" -> XTheme.AMBER
            "Pierna" -> XTheme.PURPLE
            "Bíceps", "Tríceps", "Brazos" -> XTheme.GREEN
            else -> XTheme.BLUE
        }

        topRow.addView(TextView(context).apply {
            text = ex.group.uppercase()
            setTextColor(badgeColor)
            textSize = 10f
            setPadding(0, 0, (6 * d).toInt(), 0)
        })

        topRow.addView(TextView(context).apply {
            text = ex.name
            setTextColor(XTheme.TEXT_PRIMARY)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })

        if (isDone) {
            topRow.addView(TextView(context).apply {
                text = "✓"
                setTextColor(XTheme.GREEN)
                textSize = 16f
            })
        }
        card.addView(topRow)

        // Fila 2: Objetivo y progreso de series
        val lastSet = db.getLastSetForExercise(ex.id)
        val infoText = buildString {
            append("${ex.targetSets} × ${ex.targetReps} · ${kg(ex.defaultWeightKg)} kg · ⏱ ${ex.restSeconds}s")
            if (todaySets.isNotEmpty()) {
                append("  (${todaySets.size}/${ex.targetSets})")
            } else if (lastSet != null) {
                append("  [Prev: ${kg(lastSet.weightKg)} kg×${lastSet.reps}]")
            }
        }

        card.addView(TextView(context).apply {
            text = infoText
            setTextColor(if (isDone) XTheme.GREEN else XTheme.TEXT_SECONDARY)
            textSize = 12f
            setPadding(0, (3 * d).toInt(), 0, 0)
        })

        if (todaySets.isNotEmpty()) {
            val setsSummary = todaySets.joinToString(" · ") { "S${it.setNum}: ${it.reps}×${kg(it.weightKg)}kg" }
            card.addView(TextView(context).apply {
                text = setsSummary
                setTextColor(XTheme.GREEN)
                textSize = 10f
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        }

        val nextSetNum = todaySets.size + 1
        val prefillReps = lastSet?.reps ?: 10
        val prefillWeight = lastSet?.weightKg ?: ex.defaultWeightKg
        val btnLabel = if (!isDone) "＋ Serie $nextSetNum (${kg(prefillWeight)} kg × $prefillReps)"
                       else "＋ Serie extra ($nextSetNum) (${kg(prefillWeight)} kg × $prefillReps)"
        val repeatBtn = XTheme.pillButton(
            context = context,
            text = btnLabel,
            primary = !isDone && todaySets.isNotEmpty(),
            minHeightDp = 30f,
            textSizeSp = 11f
        ) {
            db.logSet(ex.id, nextSetNum, prefillReps, prefillWeight)
            io.xenxo.sync.XenxoSync.sync(context)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            Toast.makeText(context, "Serie $nextSetNum guardada (${kg(prefillWeight)} kg × $prefillReps)", Toast.LENGTH_SHORT).show()
            refresh()
            openRestTimer(ex.restSeconds)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (7 * d).toInt()
            }
        }
        card.addView(repeatBtn)

        return card
    }

    fun handleBack(): Boolean {
        if (overlayContainer.childCount > 0) {
            val child = overlayContainer.getChildAt(0)
            if (child is RestTimerView) {
                child.cancelTimer()
            }
            overlayContainer.removeAllViews()
            (context as? io.xenxo.MainActivity)?.setTabBarVisible(true)
            refresh()
            return true
        }
        return false
    }

    private fun openSetLogger(ex: Exercise, setNum: Int, reps: Int, weight: Float) {
        val best1RM = db.getBest1RM(ex.id)
        val loggerView = SetLoggerView(
            context = context,
            exercise = ex,
            setNum = setNum,
            initialReps = reps,
            initialWeight = weight,
            best1RM = best1RM,
            onSaved = { loggedReps, loggedWeight, loggedRestSec ->
                overlayContainer.removeAllViews()
                db.updateExerciseRest(ex.id, loggedRestSec)
                val setLog = db.logSet(ex.id, setNum, loggedReps, loggedWeight)
                io.xenxo.sync.XenxoSync.sync(context)
                Toast.makeText(context, "Serie $setNum guardada (${kg(loggedWeight)} kg × $loggedReps)", Toast.LENGTH_SHORT).show()
                refresh()

                // Arrancar descanso
                openRestTimer(loggedRestSec)
            },
            onCancel = {
                overlayContainer.removeAllViews()
                (context as? io.xenxo.MainActivity)?.setTabBarVisible(true)
            }
        )
        (context as? io.xenxo.MainActivity)?.setTabBarVisible(false)
        overlayContainer.addView(loggerView, LayoutParams(-1, -1))
        loggerView.requestFocus()
    }

    private fun openRestTimer(seconds: Int) {
        val timerView = RestTimerView(
            context = context,
            totalSeconds = seconds,
            onFinished = {
                overlayContainer.removeAllViews()
                (context as? io.xenxo.MainActivity)?.setTabBarVisible(true)
                refresh()
            }
        )
        (context as? io.xenxo.MainActivity)?.setTabBarVisible(false)
        overlayContainer.addView(timerView, LayoutParams(-1, -1))
    }

    private fun openBodyWeightDialog(currentWeight: Float) {
        val dialog = BodyWeightDialog(
            context = context,
            initialWeight = currentWeight,
            onSaved = { w ->
                overlayContainer.removeAllViews()
                (context as? io.xenxo.MainActivity)?.setTabBarVisible(true)
                db.logBodyWeight(w)
                io.xenxo.sync.XenxoSync.sync(context)
                Toast.makeText(context, String.format("Peso %.1f kg guardado", w), Toast.LENGTH_SHORT).show()
                refresh()
            },
            onCancel = {
                overlayContainer.removeAllViews()
                (context as? io.xenxo.MainActivity)?.setTabBarVisible(true)
            }
        )
        (context as? io.xenxo.MainActivity)?.setTabBarVisible(false)
        overlayContainer.addView(dialog, LayoutParams(-1, -1))
        dialog.requestFocus()
    }

    fun hasOverlay(): Boolean = overlayContainer.childCount > 0

    private var rotaryScrollAccumulator = 0f

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (overlayContainer.childCount > 0) {
            val child = overlayContainer.getChildAt(0)
            if (child.onGenericMotionEvent(ev)) return true
        }
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val scrollDelta = (-delta * 40 * d).toInt()
            scroll.scrollBy(0, scrollDelta)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    fun prevDay() {
        selectedDay = when (selectedDay) {
            Calendar.SUNDAY -> Calendar.SATURDAY
            Calendar.MONDAY -> Calendar.SUNDAY
            Calendar.TUESDAY -> Calendar.MONDAY
            Calendar.WEDNESDAY -> Calendar.TUESDAY
            Calendar.THURSDAY -> Calendar.WEDNESDAY
            Calendar.FRIDAY -> Calendar.THURSDAY
            Calendar.SATURDAY -> Calendar.FRIDAY
            else -> Calendar.MONDAY
        }
        scroll.scrollTo(0, 0)
        refresh()
    }

    fun nextDay() {
        selectedDay = when (selectedDay) {
            Calendar.SUNDAY -> Calendar.MONDAY
            Calendar.MONDAY -> Calendar.TUESDAY
            Calendar.TUESDAY -> Calendar.WEDNESDAY
            Calendar.WEDNESDAY -> Calendar.THURSDAY
            Calendar.THURSDAY -> Calendar.FRIDAY
            Calendar.FRIDAY -> Calendar.SATURDAY
            Calendar.SATURDAY -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
        scroll.scrollTo(0, 0)
        refresh()
    }

    private fun getDayName(day: Int) = when (day) {
        Calendar.MONDAY -> "Lunes"
        Calendar.TUESDAY -> "Martes"
        Calendar.WEDNESDAY -> "Miércoles"
        Calendar.THURSDAY -> "Jueves"
        Calendar.FRIDAY -> "Viernes"
        Calendar.SATURDAY -> "Sábado"
        Calendar.SUNDAY -> "Domingo"
        else -> "Día"
    }

    private fun getDayTitle(day: Int): String {
        val name = getDayName(day)
        val custom = db.getDayTitle(day)
        return if (!custom.isNullOrBlank()) {
            "$name · $custom"
        } else if (db.hasExercisesForDay(day)) {
            "$name · Entrenamiento"
        } else {
            "$name · Descanso"
        }
    }

    private fun arrowBtn(symbol: String, onClick: () -> Unit) = TextView(context).apply {
        text = symbol
        setTextColor(XTheme.TEXT_PRIMARY)
        textSize = 16f
        gravity = Gravity.CENTER
        val sz = (36 * d).toInt()
        layoutParams = LinearLayout.LayoutParams(sz, sz)
        background = XTheme.circleButtonDrawable(d, normalColor = XTheme.CARD_BG, pressedColor = XTheme.CARD_BG_PRESS)
        setOnClickListener { onClick() }
    }
}
