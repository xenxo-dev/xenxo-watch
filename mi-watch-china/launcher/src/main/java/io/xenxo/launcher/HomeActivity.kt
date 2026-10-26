package io.xenxo.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.Toast
import io.xenxo.launcher.MenuView.Item

/**
 * Cajón de apps de xenxo, estilo Apple Watch. Ocupa el sitio de T-Launcher: la esfera, las notificaciones,
 * las tarjetas y los ajustes rápidos siguen siendo de la pantalla de inicio de Wear OS.
 *  - Se abre con el intent HOME (deslizar a la derecha en la esfera o botón), directamente en el panal.
 *  - Tocar = abrir (el icono se amplía), mantener un icono = menú (fijar / ordenar / ocultar / info), corona = zoom.
 *  - Mantener en un hueco = menú del launcher: Ajustes (brillo, pantalla, levantar muñeca, idioma) y apps ocultas.
 *  - Botón o atrás: cierra lo que esté abierto o, con el panal delante, vuelve a la esfera de Wear OS.
 */
class HomeActivity : Activity() {

    private lateinit var repo: AppRepository
    private lateinit var settings: WatchSettings
    private lateinit var honeycomb: HoneycombView
    private lateinit var menu: MenuView
    private lateinit var level: LevelView
    private var appsDirty = true
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val pkgChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == "io.xenxo.launcher.SET_WATCHFACE") {
                val f = i.getStringExtra("face") ?: "io.xenxo.launcher.watchface.XenxoUltraFace"
                setWatchFace(f)
                return
            }
            appsDirty = true
            if (hasWindowFocus()) reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        settings = WatchSettings(this)
        honeycomb = HoneycombView(this).apply {
            onTap = { app, bounds -> launch(app, bounds) }
            onLongPress = { app -> appMenu(app) }
            onLongPressEmpty = { launcherMenu() }
            onReorder = { keys -> repo.order = keys } // el panal ya se ha reordenado en pantalla
        }
        menu = MenuView(this)
        level = LevelView(this)
        setContentView(FrameLayout(this).apply {
            addView(honeycomb, FrameLayout.LayoutParams(-1, -1))
            addView(menu, FrameLayout.LayoutParams(-1, -1))
            addView(level, FrameLayout.LayoutParams(-1, -1))
        })

        if (intent?.hasExtra("set_watchface") == true) {
            intent.getStringExtra("set_watchface")?.let { setWatchFace(it) }
        }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val f = intent.getStringExtra("face") ?: "io.xenxo.launcher.watchface.XenxoUltraFace"
                setWatchFace(f)
            }
        }, IntentFilter("io.xenxo.launcher.SET_WATCHFACE"))

        registerReceiver(pkgChanged, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        onScreen = true
        if (appsDirty) reloadApps()
        when {
            level.isShowing -> level.requestFocus()
            menu.isShowing -> if (inSettings) settingsMenu() // valores al día al volver de Ajustes del sistema
            else -> honeycomb.requestFocus()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pkgChanged)
        super.onDestroy()
    }

    /**
     * true mientras el panal está a la vista (de onResume a onStop). El botón pausa la actividad antes de
     * llamar a onNewIntent, así que hasWindowFocus() no sirve para saber si ya estaba delante.
     */
    private var onScreen = false

    override fun onStop() {
        onScreen = false
        super.onStop()
    }

    /** Intent HOME (botón): cierra lo abierto; con el panal delante → a la esfera; desde otra pantalla → panal centrado. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra("set_watchface")) {
            intent.getStringExtra("set_watchface")?.let { setWatchFace(it) }
            return
        }
        when {
            !onScreen -> { level.hide(); closeMenu(); honeycomb.recenter() }
            level.isShowing || menu.isShowing -> back()
            else -> toWatchFace()
        }
    }

    @Deprecated("API antigua, suficiente en Android 9")
    override fun onBackPressed() = back()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_STEM_1,
                KeyEvent.KEYCODE_STEM_2,
                KeyEvent.KEYCODE_STEM_3,
                KeyEvent.KEYCODE_STEM_PRIMARY,
                KeyEvent.KEYCODE_BUTTON_1 -> {
                    runCatching {
                        startActivity(Intent().setComponent(ComponentName("io.xenxo.wristai", "io.xenxo.wristai.MainActivity"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_STEM_3,
            KeyEvent.KEYCODE_STEM_PRIMARY,
            KeyEvent.KEYCODE_BUTTON_1 -> {
                runCatching {
                    startActivity(Intent().setComponent(ComponentName("io.xenxo.wristai", "io.xenxo.wristai.MainActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Un paso atrás: selector de nivel o submenú → Ajustes → panal → esfera. */
    private fun back() = when {
        level.isShowing -> { level.hide(); settingsMenu() }
        menu.isShowing && inSubmenu -> settingsMenu()
        menu.isShowing -> closeMenu()
        else -> toWatchFace()
    }

    /**
     * Vuelve a la esfera. Ahora que este launcher es la app de inicio, moveTaskToBack() solo dejaba ver la
     * última app abierta (p. ej. Chrome) y la esfera quedaba inalcanzable: se abre explícitamente la pantalla
     * de inicio de Wear OS (esfera, notificaciones, tarjetas) y el panal se queda en segundo plano con los
     * iconos ya cargados.
     */
    private fun toWatchFace() {
        // Sin moveTaskToBack: mandar el panal al fondo destapaba la última app (p. ej. Chrome) antes que la esfera
        val ok = runCatching { startActivity(Intent().setComponent(WEAR_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) moveTaskToBack(true)
    }

    private companion object {
        /** Pantalla de inicio de Wear OS (esfera). Comprobado en el reloj: se puede abrir desde fuera. */
        val WEAR_HOME = ComponentName("com.google.android.wearable.app",
            "com.google.android.clockwork.home2.activity.HomeActivity2")
    }

    private fun reloadApps() {
        appsDirty = false
        val list = repo.load()
        honeycomb.setApps(list)
        val px = honeycomb.iconSizePx()
        bgExecutor.execute {
            repo.loadIcons(list, px)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    honeycomb.invalidate()
                }
            }
        }
    }

    /** Abre la app con la animación del sistema que amplía el icono hasta llenar la pantalla. */
    private fun launch(app: AppEntry, bounds: RectF) {
        val opts = ActivityOptions.makeScaleUpAnimation(
            honeycomb, bounds.left.toInt(), bounds.top.toInt(), bounds.width().toInt(), bounds.height().toInt())
        runCatching { repo.launch(app, opts.toBundle()) }
    }

    // ---------- menús ----------

    /** true mientras el menú abierto es el de Ajustes (para refrescarlo al volver a la app). */
    private var inSettings = false
    /** true en los submenús de Ajustes (idioma): atrás vuelve a Ajustes, no al panal. */
    private var inSubmenu = false

    private fun closeMenu() {
        inSettings = false
        inSubmenu = false
        menu.hide()
        honeycomb.requestFocus()
    }

    private fun appMenu(app: AppEntry) {
        val key = app.key
        val items = mutableListOf<Item>()
        if (repo.isPinned(key)) {
            items += Item(getString(R.string.app_move_in)) { repo.moveIn(key); reloadApps() }
            items += Item(getString(R.string.app_unpin)) { repo.unpin(key); reloadApps() }
        } else {
            items += Item(getString(R.string.app_pin)) { repo.pin(key); reloadApps() }
        }
        items += Item(getString(R.string.app_hide)) { repo.hide(key); reloadApps() }
        items += Item(getString(R.string.app_info)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.component.packageName, null)))
            }
        }
        items += Item(getString(R.string.menu_close)) { closeMenu() }
        menu.show(app.label, items, message = getString(R.string.app_drag_hint))
    }

    private fun launcherMenu() {
        inSettings = false
        menu.show(getString(R.string.menu_title), listOf(
            Item(getString(R.string.menu_settings)) { settingsMenu() },
            Item(getString(R.string.menu_show_hidden)) {
                repo.unhideAll(); reloadApps()
                Toast.makeText(this, R.string.hidden_shown, Toast.LENGTH_SHORT).show()
            },
            Item(getString(R.string.menu_close)) { closeMenu() },
        ))
    }

    /** Ajustes reales del reloj; cada fila muestra el valor actual y se repinta tras tocarla. */
    private fun settingsMenu(keepScroll: Boolean = false) {
        inSettings = true
        inSubmenu = false
        val yesNo = { on: Boolean -> getString(if (on) R.string.yes else R.string.no) }
        val items = mutableListOf<Item>()
        val notes = mutableListOf(getString(R.string.set_battery, settings.batteryPercent))
        if (settings.adbWifiPort > 0) notes += getString(R.string.set_adb_wifi)
        if (settings.canWrite) {
            items += Item(getString(R.string.set_brightness, settings.brightnessPercent)) { brightnessLevel() }
            items += Item(getString(R.string.set_auto, yesNo(settings.autoBrightness))) {
                settings.autoBrightness = !settings.autoBrightness; settingsMenu(keepScroll = true)
            }
            items += Item(getString(R.string.set_timeout, settings.screenTimeoutSec)) {
                val t = WatchSettings.TIMEOUTS
                val i = t.indexOfFirst { it > settings.screenTimeoutSec }
                settings.screenTimeoutSec = if (i < 0) t[0] else t[i]
                settingsMenu(keepScroll = true)
            }
        } else notes += getString(R.string.set_no_permission)
        if (settings.canWriteSecure) {
            items += Item(getString(R.string.set_wake, yesNo(settings.wakeGesture))) {
                settings.wakeGesture = !settings.wakeGesture; settingsMenu(keepScroll = true)
            }
            items += Item(getString(R.string.set_scans, yesNo(settings.backgroundScans))) {
                settings.backgroundScans = !settings.backgroundScans; settingsMenu(keepScroll = true)
            }
            items += Item(getString(R.string.set_wifi_wakeup, yesNo(settings.wifiWakeup))) {
                settings.wifiWakeup = !settings.wifiWakeup; settingsMenu(keepScroll = true)
            }
        } else notes += getString(R.string.set_no_secure)
        items += Item("Esferas xenxo") { watchfacesMenu() }
        items += Item(getString(R.string.set_language,
            SystemLocale.describe(SystemLocale.current(), resources.configuration.locales[0]))) { languageMenu() }
        items += Item(getString(R.string.set_display_more)) { openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS) }
        items += Item(getString(R.string.set_all)) { openSystemSettings(Settings.ACTION_SETTINGS) }
        items += Item(getString(R.string.menu_close)) { closeMenu() }
        menu.show(getString(R.string.menu_settings), items, message = notes.joinToString("\n\n"), keepScroll = keepScroll)
    }

    private fun watchfacesMenu() {
        inSubmenu = true
        val items = listOf(
            Item("★ xenxo Ultra (Modular)") { setWatchFace("io.xenxo.launcher.watchface.XenxoUltraFace") },
            Item("xenxo Digital") { setWatchFace("io.xenxo.launcher.watchface.XenxoDigitalFace") },
            Item("xenxo Analógica") { setWatchFace("io.xenxo.launcher.watchface.XenxoAnalogFace") },
            Item(getString(R.string.menu_close)) { settingsMenu() }
        )
        menu.show("Esferas xenxo", items)
    }

    fun setWatchFace(className: String) {
        try {
            android.util.Log.i("HomeActivity", "setWatchFace called with $className")
            val wm = getSystemService(Context.WALLPAPER_SERVICE) as android.app.WallpaperManager
            val m = wm.javaClass.getMethod("setWallpaperComponent", ComponentName::class.java)
            val comp = ComponentName(this, className)
            m.invoke(wm, comp)
            android.util.Log.i("HomeActivity", "setWatchFace SUCCESS for $className")
            Toast.makeText(this, "Esfera cambiada ✓", Toast.LENGTH_SHORT).show()
            closeMenu()
            toWatchFace()
        } catch (e: Throwable) {
            android.util.Log.e("HomeActivity", "setWatchFace error: ${e.message}", e)
            Toast.makeText(this, "Error al cambiar esfera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun brightnessLevel() {
        val hint = getString(if (settings.autoBrightness) R.string.set_brightness_hint_auto else R.string.set_brightness_hint)
        level.show(getString(R.string.set_brightness_title), settings.brightnessPercent, 5, 100, 5, hint,
            format = { "$it %" }, onChange = { settings.brightnessPercent = it })
    }

    /**
     * Abre una pantalla de Ajustes del sistema (si este reloj no la tiene, la principal) y deja nuestro
     * menú de Ajustes debajo, para volver a él con atrás.
     */
    private fun openSystemSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        settingsMenu()
    }

    private fun languageMenu() {
        inSettings = false
        inSubmenu = true
        val now = getString(R.string.lang_current,
            SystemLocale.describe(SystemLocale.current(), resources.configuration.locales[0]))
        val close = Item(getString(R.string.menu_close)) { settingsMenu() }
        if (!SystemLocale.canChange(this)) {
            menu.show(getString(R.string.menu_language), listOf(close),
                message = now + "\n\n" + getString(R.string.lang_no_permission))
            return
        }
        fun set(tags: String) {
            runCatching { SystemLocale.apply(tags) }.onFailure {
                Toast.makeText(this, getString(R.string.lang_failed, it.cause?.message ?: it.message), Toast.LENGTH_LONG).show()
            }
            settingsMenu()
        }
        menu.show(getString(R.string.menu_language), listOf(
            Item(getString(R.string.lang_spanish)) { set(SystemLocale.SPANISH) },
            Item(getString(R.string.lang_english)) { set(SystemLocale.ENGLISH) },
            close,
        ), message = now)
    }

    /** Cambio de idioma (declarado en el manifiesto): sin recrear la actividad, solo se repintan los textos. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (inSettings) settingsMenu(keepScroll = true)
    }
}
