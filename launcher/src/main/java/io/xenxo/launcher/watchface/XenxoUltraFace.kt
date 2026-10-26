// La librería wearable support está obsoleta para Wear OS 3+, pero es la que corresponde a Wear OS 2 (Android 9).
@file:Suppress("DEPRECATION")

package io.xenxo.launcher.watchface

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Esfera "xenxo Ultra": Estilo Apple Watch Ultra / Modular Ultra adaptado a 368×448 AMOLED.
 *
 * Elementos:
 *  - Perímetro: Reglas laterales VITALS (izq, pill de rango normal, "TYPICAL") y TRAINING (dcha, slider, "STEADY").
 *  - Superior: Fecha en castellano y hora digital grande en sans-serif bold.
 *  - Centro: Tarjeta redondeada Readiness (arco medidor cian, score y "Go For It").
 *  - Inferior: 3 complicaciones circulares (Pulsómetro con BPM en rojo, Corredor de gym en verde neón, y Anillo de actividad con sol).
 *  - Modo ambiente: Silueta AMOLED de bajo consumo y protección anti-quemado.
 */
class XenxoUltraFace : XenxoFaceService() {

    private val boldTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val lightTypeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    private fun textPaint(size: Float, color: Int, tf: Typeface = Typeface.DEFAULT, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = tf
            textAlign = align
        }

    // Colores
    private val CYAN = 0xFF00E5FF.toInt()
    private val TEAL = 0xFF30D158.toInt()
    private val NEON_GREEN = 0xFF30D158.toInt()
    private val RED = 0xFFFF453A.toInt()
    private val GOLD = 0xFFFFD60A.toInt()
    private val CARD_BG = 0xFF1C1C1E.toInt()
    private val TRACK_GREY = 0xFF2C2C2E.toInt()
    private val TICK_GREY = 0xFF48484A.toInt()
    private val TEXT_MUTED = 0xFF8E8E93.toInt()
    private val TEXT_LIGHT = 0xFFE5E5EA.toInt()
    private val AMBIENT_DIM = 0xFF707070.toInt()

    // Paints
    private val pDate = textPaint(15f, TEXT_LIGHT, mediumTypeface)
    private val pTime = textPaint(78f, Color.WHITE, boldTypeface)
    private val pTimeAmbient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 78f
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFCCCCCC.toInt()
    }

    private val pPerimeterTitle = textPaint(11f, TEXT_MUTED, boldTypeface)
    private val pPerimeterStatus = textPaint(11f, CYAN, boldTypeface)
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TICK_GREY
        strokeWidth = 1.6f
    }
    private val pCyanLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CYAN
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val pCyanFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CYAN
        style = Paint.Style.FILL
    }
    private val pCyanStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val pCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_BG
        style = Paint.Style.FILL
    }
    private val pCardStrokeAmbient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3A3C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val pArcTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRACK_GREY
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val pArcProgress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CYAN
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val pReadinessScore = textPaint(28f, CYAN, boldTypeface)
    private val pReadinessTitle = textPaint(15f, Color.WHITE, boldTypeface, Paint.Align.LEFT)
    private val pReadinessSub = textPaint(14f, CYAN, mediumTypeface, Paint.Align.LEFT)

    private val pCircleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_BG
        style = Paint.Style.FILL
    }
    private val pCircleAmbient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2C2C2E.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val pHeart = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; style = Paint.Style.FILL }
    private val pBpmVal = textPaint(21f, Color.WHITE, boldTypeface)
    private val pBpmLabel = textPaint(10f, RED, boldTypeface)

    private val pRunner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_GREEN
        style = Paint.Style.FILL
    }

    private val pSun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD
        style = Paint.Style.FILL
    }
    private val pActivityVal = textPaint(19f, Color.WHITE, boldTypeface)

    private val pRingRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val pRingGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val pRingYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val cardRect = RectF(66f, 184f, 302f, 282f)
    private val arcRect = RectF(82f, 198f, 158f, 274f)
    private val ringRect = RectF(234f, 302f, 286f, 354f)

    private val heartPath = XenxoDigitalFace.heartPath()
    private val runnerPath = createRunnerPath()

    override fun onCreateEngine(): Engine = super.onCreateEngine().also {
        (it as FaceEngine).paints += listOf(
            pDate, pTime, pTimeAmbient, pPerimeterTitle, pPerimeterStatus, pTick,
            pCyanLine, pCyanFill, pCyanStroke, pCardBg, pCardStrokeAmbient,
            pArcTrack, pArcProgress, pReadinessScore, pReadinessTitle, pReadinessSub,
            pCircleBg, pCircleAmbient, pHeart, pBpmVal, pBpmLabel, pRunner, pSun,
            pActivityVal, pRingRed, pRingGreen, pRingYellow
        )
    }

    override fun drawFace(e: FaceEngine, c: Canvas, now: Calendar) {
        val d = e.data

        if (e.ambient) {
            drawAmbientFace(c, now, d)
            return
        }

        // 1. BEZEL PERÍMETRO (VITALS & TRAINING)
        drawPerimeter(c)

        // 2. CABECERA Y HORA
        // Fecha superior centrada: "VIE 23 OCT"
        val monthStr = now.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale("es", "ES"))?.uppercase(Locale.ROOT) ?: "OCT"
        val dateStr = "${FaceFormat.dayAndNumber(now)} $monthStr"
        c.drawText(dateStr, 184f, 84f, pDate)

        // Pequeño indicador central superior (icono satélite/batería)
        val batt = d.batteryPercent()
        drawTopSatellite(c, 184f, 54f, batt)

        // Hora digital gigante moderna
        c.drawText(FaceFormat.hhmm(now), 184f, 164f, pTime)

        // 3. TARJETA CENTRAL: READINESS (RECUPERACIÓN / ENERGÍA)
        drawReadinessCard(c, d)

        // 4. FILA INFERIOR: 3 COMPLICACIONES CIRCULARES
        // Izquierda: Pulso cardíaco (BPM)
        drawPulseCircle(c, 110f, 328f, d.bpm)

        // Centro: Corredor de entrenamiento (Pasos / Gym)
        drawRunnerCircle(c, 184f, 328f, d.steps)

        // Derecha: Anillo de Actividad & Carga
        drawActivityCircle(c, 260f, 328f, d)
    }

    private fun drawPerimeter(c: Canvas) {
        // Títulos de perímetro
        c.save()
        c.rotate(-30f, 62f, 68f)
        c.drawText("VITALS", 62f, 68f, pPerimeterTitle)
        c.restore()

        c.save()
        c.rotate(30f, 306f, 68f)
        c.drawText("TRAINING", 306f, 68f, pPerimeterTitle)
        c.restore()

        // Regla izquierda (ticks)
        val leftX = 38f
        for (i in 0..14) {
            val y = 115f + i * 14.5f
            val len = if (i % 5 == 0) 9f else 5f
            c.drawLine(leftX, y, leftX + len, y, pTick)
        }

        // Pill / Barra cian de VITALS (Normal range indicator)
        val pillTop = 180f
        val pillBottom = 224f
        c.drawLine(leftX + 4f, pillTop + 6f, leftX + 4f, pillBottom - 6f, pCyanLine)
        c.drawCircle(leftX + 4f, pillTop + 6f, 4f, pCyanStroke)
        c.drawCircle(leftX + 4f, pillBottom - 6f, 4f, pCyanStroke)

        // Etiqueta inferior izquierda: "TYPICAL"
        c.save()
        c.rotate(30f, 74f, 368f)
        c.drawText("TYPICAL", 74f, 368f, pPerimeterStatus)
        c.restore()

        // Regla derecha (ticks)
        val rightX = 330f
        for (i in 0..14) {
            val y = 115f + i * 14.5f
            val len = if (i % 5 == 0) 9f else 5f
            c.drawLine(rightX - len, y, rightX, y, pTick)
        }

        // Slider cian de TRAINING con perilla indicadora
        c.drawLine(rightX - 4f, 212f, rightX - 4f, 248f, pCyanLine)
        c.drawCircle(rightX - 4f, 238f, 5f, pCyanFill)
        c.drawCircle(rightX - 4f, 238f, 6.5f, pCyanStroke)

        // Etiqueta inferior derecha: "STEADY"
        c.save()
        c.rotate(-30f, 294f, 368f)
        c.drawText("STEADY", 294f, 368f, pPerimeterStatus)
        c.restore()
    }

    private fun drawTopSatellite(c: Canvas, cx: Float, cy: Float, batteryPct: Int) {
        c.drawCircle(cx, cy, 10f, pCardBg)
        pCyanStroke.strokeWidth = 1.5f
        c.drawCircle(cx, cy, 10f, pCyanStroke)
        // Icono satélite / batería central
        pCyanFill.alpha = 255
        c.drawCircle(cx, cy, 3.5f, pCyanFill)
    }

    private fun drawReadinessCard(c: Canvas, d: FaceData) {
        // Fondo redondeado de la tarjeta (#1C1C1E)
        c.drawRoundRect(cardRect, 18f, 18f, pCardBg)

        // Arco medidor tipo velocímetro a la izquierda de la tarjeta
        // Fondo gris de arco
        c.drawArc(arcRect, 140f, 260f, false, pArcTrack)

        // Valor de recuperación (score de 1 a 10 basado en batería/descanso)
        val score = if (d.batteryPercent() >= 80) 8 else if (d.batteryPercent() >= 40) 7 else 5
        val sweep = (score / 10f) * 260f
        c.drawArc(arcRect, 140f, sweep, false, pArcProgress)

        // Punto / perilla brillante al final del arco
        val endAngleRad = Math.toRadians((140f + sweep).toDouble())
        val arcRadius = (arcRect.width() / 2f)
        val arcCenterX = arcRect.centerX()
        val arcCenterY = arcRect.centerY()
        val dotX = arcCenterX + (arcRadius * cos(endAngleRad)).toFloat()
        val dotY = arcCenterY + (arcRadius * sin(endAngleRad)).toFloat()
        c.drawCircle(dotX, dotY, 4.5f, pCyanFill)

        // Puntuación en el centro del arco
        c.drawText(score.toString(), arcCenterX, arcCenterY + 10f, pReadinessScore)

        // Textos a la derecha de la tarjeta
        c.drawText("Readiness", 170f, 226f, pReadinessTitle)
        val subtext = if (score >= 8) "Go For It" else if (score >= 6) "Moderado" else "Descansa"
        c.drawText(subtext, 170f, 252f, pReadinessSub)
    }

    private fun drawPulseCircle(c: Canvas, cx: Float, cy: Float, bpm: Int) {
        c.drawCircle(cx, cy, 32f, pCircleBg)

        // Corazón rojo centrado arriba
        c.save()
        c.translate(cx - 8f, cy - 22f)
        c.drawPath(heartPath, pHeart)
        c.restore()

        // Pulso cardíaco numérico
        val bpmStr = if (bpm > 30) bpm.toString() else "--"
        c.drawText(bpmStr, cx, cy + 9f, pBpmVal)

        // Etiqueta "BPM" en rojo
        c.drawText("BPM", cx, cy + 22f, pBpmLabel)
    }

    private fun drawRunnerCircle(c: Canvas, cx: Float, cy: Float, steps: Int) {
        c.drawCircle(cx, cy, 32f, pCircleBg)

        // Silueta del atleta corriendo en verde neón (#30D158)
        c.save()
        c.translate(cx - 10f, cy - 18f)
        c.drawPath(runnerPath, pRunner)
        c.restore()

        // Pasos compactos si hay dato
        if (steps > 0) {
            val compactSteps = if (steps >= 1000) String.format(Locale.ROOT, "%.1fk", steps / 1000f) else steps.toString()
            val pStepsMini = textPaint(9f, NEON_GREEN, boldTypeface)
            c.drawText(compactSteps, cx, cy + 24f, pStepsMini)
        }
    }

    private fun drawActivityCircle(c: Canvas, cx: Float, cy: Float, d: FaceData) {
        c.drawCircle(cx, cy, 32f, pCircleBg)

        // Anillos concéntricos de actividad (estilo Apple Activity)
        c.drawArc(ringRect, -90f, 180f, false, pRingYellow)
        c.drawArc(ringRect, 90f, 120f, false, pRingGreen)
        c.drawArc(ringRect, 210f, 60f, false, pRingRed)

        // Número central (horas de pie o factor de actividad)
        val activeValue = if (d.km > 0f) Math.max(1, d.km.toInt()).toString() else "4"
        c.drawText(activeValue, cx, cy + 7f, pActivityVal)

        // Pequeño icono de sol o fuego en la base
        drawSunIcon(c, cx, cy + 18f)
    }

    private fun drawSunIcon(c: Canvas, cx: Float, cy: Float) {
        c.drawCircle(cx, cy, 2.8f, pSun)
        val pRay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            strokeWidth = 1.2f
        }
        for (i in 0 until 8) {
            val rad = Math.toRadians((i * 45).toDouble())
            val x1 = cx + (4.2f * cos(rad)).toFloat()
            val y1 = cy + (4.2f * sin(rad)).toFloat()
            val x2 = cx + (5.8f * cos(rad)).toFloat()
            val y2 = cy + (5.8f * sin(rad)).toFloat()
            c.drawLine(x1, y1, x2, y2, pRay)
        }
    }

    private fun drawAmbientFace(c: Canvas, now: Calendar, d: FaceData) {
        // En modo ambiente: fondo negro puro y trazados de bajo consumo
        val monthStr = now.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale("es", "ES"))?.uppercase(Locale.ROOT) ?: "OCT"
        val dateStr = "${FaceFormat.dayAndNumber(now)} $monthStr"
        pDate.color = AMBIENT_DIM
        c.drawText(dateStr, 184f, 84f, pDate)

        // Hora silueteada sin relleno para proteger AMOLED
        c.drawText(FaceFormat.hhmm(now), 184f, 164f, pTimeAmbient)

        // Tarjeta con contorno sutil
        c.drawRoundRect(cardRect, 18f, 18f, pCardStrokeAmbient)
        c.drawText("Readiness", 170f, 235f, textPaint(15f, AMBIENT_DIM, boldTypeface, Paint.Align.LEFT))

        // Círculos simplificados
        c.drawCircle(110f, 328f, 32f, pCircleAmbient)
        val bpmStr = if (d.bpm > 30) "${d.bpm}" else "--"
        c.drawText(bpmStr, 110f, 332f, textPaint(16f, AMBIENT_DIM, boldTypeface))

        c.drawCircle(184f, 328f, 32f, pCircleAmbient)
        c.drawCircle(260f, 328f, 32f, pCircleAmbient)
    }

    /**
     * Zonas táctiles interactivas:
     * - Mitad superior: Panal del launcher
     * - Tarjeta central: Energía xenxo (batería, perfiles, rendimiento)
     * - Círculo izquierdo (BPM): Salud xenxo (pulsómetro en vivo)
     * - Círculo central (Runner): Gym xenxo (rutina y series)
     * - Círculo derecho (Actividad): Salud xenxo
     */
    override fun tapZone(x: Int, y: Int): Intent? = when {
        y in 184..282 -> energyIntent()
        y in 290..366 && x in 76..144 -> healthIntent()
        y in 290..366 && x in 150..218 -> gymIntent()
        y in 290..366 && x in 226..294 -> healthIntent()
        y < 184 -> launcherIntent()
        else -> launcherIntent()
    }

    companion object {
        /** Icono del corredor vectorizado exacto (20×24 px) */
        fun createRunnerPath(): Path = Path().apply {
            // Cabeza
            addCircle(14f, 3f, 2.8f, Path.Direction.CW)
            // Torso y extremidades dinámicas
            moveTo(13f, 7f)
            lineTo(10f, 13f)
            lineTo(5f, 12f)
            lineTo(4f, 14f)
            lineTo(10f, 15f)
            lineTo(12f, 17f)
            lineTo(14f, 22f)
            lineTo(16f, 21.5f)
            lineTo(14.5f, 16.5f)
            lineTo(13.5f, 13f)
            lineTo(16f, 10f)
            lineTo(19f, 12f)
            lineTo(20f, 10f)
            lineTo(16f, 8f)
            close()
            // Pierna trasera
            moveTo(11f, 14f)
            lineTo(7f, 17f)
            lineTo(6f, 20f)
            lineTo(8f, 20f)
            lineTo(9f, 18f)
            lineTo(12f, 15f)
            close()
        }
    }
}
