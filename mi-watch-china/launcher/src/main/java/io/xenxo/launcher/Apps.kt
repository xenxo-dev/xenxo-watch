package io.xenxo.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import kotlin.math.cos
import kotlin.math.sin

/** Una app lanzable con su icono ya recortado en círculo. */
class AppEntry(
    val component: ComponentName,
    val label: String,
    var icon: Bitmap? = null,
) {
    val key: String get() = component.flattenToShortString()
}

/**
 * Lista de apps, orden (fijadas primero) y apps ocultas.
 * Todo en SharedPreferences: nada de bases de datos para esto.
 */
class AppRepository(private val ctx: Context) {

    private companion object {
        /** Apps chinas sin uso fuera de China (las mismas que desactiva scripts/debloat.ps1). */
        val DEFAULT_HIDDEN = setOf(
            "com.xiaomi.wear.tutorial", "com.xiaomi.mihome", "com.xiaomi.wear.card",
            "com.xiaomi.wear.lpa", "com.xiaomi.wear.market", "com.xiaomi.wear.xiaoai",
        )
    }

    private val prefs: SharedPreferences = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Fijadas al centro del panal, en este orden. */
    var pinned: List<String>
        get() = prefs.getString("pinned", "")!!.split('|').filter { it.isNotBlank() }
        private set(v) = prefs.edit().putString("pinned", v.joinToString("|")).apply()

    /** Claves de componente o nombres de paquete ocultos. Hasta que el usuario toque nada: [DEFAULT_HIDDEN]. */
    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", DEFAULT_HIDDEN)!!.toSet()
        private set(v) = prefs.edit().putStringSet("hidden", v).apply()

    /**
     * Orden manual del panal (mantener y arrastrar), todas las claves en el orden en que se ven. Vacío hasta
     * que el usuario mueve una app; desde entonces manda él y las apps nuevas se añaden al final.
     */
    var order: List<String>
        get() = prefs.getString("order", "")!!.split('|').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("order", v.joinToString("|")).apply()

    fun pin(key: String) {
        pinned = listOf(key) + pinned.filter { it != key }
        // Con orden manual, "Fijar al centro" = primera posición
        if (order.isNotEmpty()) order = listOf(key) + order.filter { it != key }
    }
    fun unpin(key: String) { pinned = pinned.filter { it != key } }
    fun hide(key: String) { hidden = hidden + key; unpin(key) }
    fun unhideAll() { hidden = emptySet() }
    fun isPinned(key: String) = key in pinned

    /** Una posición más cerca del centro entre las fijadas (orden manual). */
    fun moveIn(key: String) {
        val p = pinned.toMutableList()
        val i = p.indexOf(key)
        if (i > 0) { p[i] = p[i - 1]; p[i - 1] = key; pinned = p }
    }

    /**
     * Apps con actividad LAUNCHER, sin nosotros mismos ni las ocultas.
     * Orden: apps xenxo, fijadas (en su orden), resto A-Z.
     */
    fun load(): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hiddenNow = hidden
        val all = pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { AppEntry(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString()) }
            .filter { it.key !in hiddenNow && it.component.packageName !in hiddenNow }
            .distinctBy { it.key }
            .toList()

        val pinnedNow = pinned
        // La app xenxo es "io.xenxo" a secas (y sus secciones también): no solo "io.xenxo.*"
        val (own, others) = all.partition { it.component.packageName.let { p -> p == "io.xenxo" || p.startsWith("io.xenxo.") } }
        val (pins, rest) = others.partition { it.key in pinnedNow }
        val base = own.sortedBy { it.label.lowercase() } +
            pins.sortedBy { pinnedNow.indexOf(it.key) } +
            rest.sortedBy { it.label.lowercase() }
        // Orden manual: lo que el usuario ha colocado, en su sitio; lo nuevo, al final en el orden de base,
        // salvo las apps xenxo nuevas, que entran al principio (cerca del centro)
        val manual = order
        if (manual.isEmpty()) return base
        val pos = manual.withIndex().associate { (i, k) -> k to i }
        val ownKeys = own.map { it.key }.toSet()
        return base.withIndex().sortedBy { (i, a) ->
            pos[a.key] ?: if (a.key in ownKeys) i - base.size else manual.size + i
        }.map { it.value }
    }

    /**
     * Carga iconos (lento: llamar en hilo de fondo). Se guardan como bitmaps HARDWARE: viven solo en la
     * memoria gráfica, en vez de estar duplicados en la RAM del proceso y en la GPU.
     */
    fun loadIcons(apps: List<AppEntry>, sizePx: Int) {
        val pm = ctx.packageManager
        apps.forEach { a ->
            a.icon = runCatching {
                val soft = circleIcon(pm.getActivityIcon(a.component), sizePx)
                soft.copy(Bitmap.Config.HARDWARE, false).also { soft.recycle() }
            }.getOrNull()
        }
    }

    fun launch(a: AppEntry, options: Bundle? = null) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(a.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        ctx.startActivity(i, options)
    }

    /**
     * Icono en un círculo lleno, estilo Apple Watch. Se cachea como Bitmap para que pintar sea barato.
     *  - Adaptativo: sus capas a sangre (se ve la zona central de 72/108, como hace el sistema).
     *  - Redondo o cuadrado opaco: se amplía hasta quitarle el margen y se recorta en círculo lleno.
     *  - Con formas transparentes: reducido sobre un círculo gris oscuro (nada de fondos claros en AMOLED).
     */
    private fun circleIcon(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })

        if (d is AdaptiveIconDrawable) {
            val extra = size / 4
            for (layer in listOf(d.background, d.foreground)) {
                layer ?: continue
                layer.setBounds(-extra, -extra, size + extra, size + extra)
                layer.draw(c)
            }
            return bmp
        }

        val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(raw))
        val margin = fillMargin(raw)
        if (margin >= 0) {
            // Escala size/(size-2·margen): el círculo de contenido pasa a medir justo el bitmap entero
            val e = size * margin / (size - 2f * margin)
            c.drawBitmap(raw, null, RectF(-e, -e, size + e, size + e), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() })
            val inset = (size * 0.18f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(c)
        }
        raw.recycle()
        return bmp
    }

    /**
     * Si el círculo inscrito del icono es opaco (iconos redondos y cuadrados con fondo), devuelve el margen
     * transparente en px para ampliarlo a sangre; si el icono tiene huecos transparentes, -1.
     */
    private fun fillMargin(b: Bitmap): Int {
        val s = b.width
        val c = s / 2f
        fun opaque(x: Float, y: Float) = (b.getPixel(x.toInt().coerceIn(0, s - 1), y.toInt().coerceIn(0, s - 1)) ushr 24) > 200
        // Primer píxel opaco bajando por el centro = margen; la forma debe ocupar al menos el 70 %
        var top = 0
        while (top < s / 2 && !opaque(c, top.toFloat())) top++
        if (top > s * 0.15f) return -1
        // 16 puntos en un círculo justo dentro del contenido: todos opacos
        val r = (c - top) * 0.9f
        for (k in 0 until 16) {
            val a = k * Math.PI / 8
            if (!opaque(c + r * cos(a).toFloat(), c + r * sin(a).toFloat())) return -1
        }
        return if (opaque(c, c)) top else -1
    }
}
