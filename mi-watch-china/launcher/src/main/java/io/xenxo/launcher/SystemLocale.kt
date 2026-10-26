package io.xenxo.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.provider.Settings
import java.util.Locale

/**
 * Cambia la lista de idiomas de TODO el sistema (como hace Ajustes), sin root.
 * Requiere conceder una vez por ADB (comprobado en el reloj: sin el segundo, el sistema lo rechaza en silencio):
 *   adb shell pm grant io.xenxo.launcher android.permission.CHANGE_CONFIGURATION
 *   adb shell appops set io.xenxo.launcher WRITE_SETTINGS allow
 * Usa la API interna IActivityManager.updatePersistentConfiguration (lista gris en Android 9:
 * accesible, con aviso en logcat). apply() lanza excepción si falla o si el idioma no cambia.
 */
object SystemLocale {

    /** Castellano y, para las apps que no lo traen, inglés antes que el chino de fábrica. */
    const val SPANISH = "es-ES,en-US"
    /** Valor de fábrica de este reloj (persist.sys.locale = en-US). */
    const val ENGLISH = "en-US"

    /**
     * Lista de idiomas tal como la tiene el sistema. Resources.getSystem() no sirve: pone primero el idioma
     * que trae el propio sistema (aquí en-US, la ROM china no trae castellano) aunque la lista sea es-ES,en-US.
     */
    fun current(): LocaleList = runCatching {
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        (am.javaClass.getMethod("getConfiguration").invoke(am) as Configuration).locales
    }.getOrElse { Resources.getSystem().configuration.locales }

    /** "Castellano → inglés", en el idioma de [inLocale]. */
    fun describe(list: LocaleList, inLocale: Locale): String =
        (0 until list.size()).joinToString(" → ") { i ->
            list[i].getDisplayLanguage(inLocale).let { if (i == 0) it.replaceFirstChar { c -> c.titlecase(inLocale) } else it }
        }

    fun canChange(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.CHANGE_CONFIGURATION) == PackageManager.PERMISSION_GRANTED &&
            Settings.System.canWrite(ctx)

    fun apply(tags: String) {
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        val getConfig = am.javaClass.getMethod("getConfiguration")
        val config = getConfig.invoke(am) as Configuration
        val wanted = LocaleList.forLanguageTags(tags)
        config.setLocales(wanted)
        // Marca el cambio como elegido por el usuario: así se guarda en persist.sys.locale y sobrevive a reinicios
        Configuration::class.java.getField("userSetLocale").setBoolean(config, true)
        am.javaClass.getMethod("updatePersistentConfiguration", Configuration::class.java).invoke(am, config)
        // Si falta un permiso el sistema solo lo apunta en logcat: lo comprobamos aquí
        val now = (getConfig.invoke(am) as Configuration).locales
        check(now == wanted) { "el sistema sigue en ${now.toLanguageTags()}" }
    }
}
