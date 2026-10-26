package io.xenxo.wristai

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.regex.Pattern

/**
 * Analizador local de intenciones rápidas (10 ms).
 * Si la petición del usuario es un comando directo conocido (peso, perfil, etc.),
 * se ejecuta localmente sin gastar tokens ni latencia en la nube.
 */
object LocalIntentParser {

    data class LocalResult(
        val text: String,
        val isAction: Boolean = false,
        val isEmergency: Boolean = false
    )

    fun tryExecute(ctx: Context, text: String): LocalResult? {
        val lower = text.lowercase().trim()

        // 1. Detección prioritaria de urgencias médicas graves: orden directa al 112
        if (isEmergency(lower)) {
            return LocalResult(
                "🚨 ¡ALERTA DE URGENCIA! Por favor, llama de inmediato al 112 o pide ayuda médica urgente.",
                isAction = true,
                isEmergency = true
            )
        }

        // 2. Registro rápido de peso matutino: "apunta peso 78,5", "peso 78 con 4", "he pesado 78 kilos"
        val weight = extractWeight(lower)
        if (weight != null && weight in 30.0f..250.0f) {
            val uri = Uri.parse("content://io.xenxo.health.provider")
            val res = runCatching {
                ctx.contentResolver.call(uri, "logWeight", weight.toString(), null)
            }.getOrNull()
            val success = res?.getBoolean("success", false) ?: false
            return if (success) {
                LocalResult("Peso guardado: ${"%.1f".format(weight)} kg en tu historial ✓", isAction = true)
            } else {
                LocalResult("No se pudo guardar el peso en la base de datos.", isAction = true)
            }
        }

        // 3. Control de perfiles de energía de :power
        if (lower.contains("modo entreno") || lower.contains("perfil entreno")) {
            return LocalResult(setPowerProfile(ctx, "entreno"), isAction = true)
        }
        if (lower.contains("modo noche") || lower.contains("perfil noche")) {
            return LocalResult(setPowerProfile(ctx, "noche"), isAction = true)
        }
        if (lower.contains("modo dia") || lower.contains("modo día") || lower.contains("perfil dia") || lower.contains("perfil día")) {
            return LocalResult(setPowerProfile(ctx, "dia"), isAction = true)
        }
        if (lower.contains("modo ahorro") || lower.contains("perfil ahorro")) {
            return LocalResult(setPowerProfile(ctx, "ahorro"), isAction = true)
        }

        // 4. Lanzadores rápidos de aplicaciones (solo si se pide explícitamente abrir/iniciar)
        if (isOpenCommand(lower, "pádel", "padel")) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.padel.PadelActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                LocalResult("Abriendo marcador de pádel...", isAction = true)
            }.getOrNull()
        }

        if (isOpenCommand(lower, "gym", "gimnasio", "entreno", "rutina")) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.GymActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                LocalResult("Abriendo rutina de gimnasio...", isAction = true)
            }.getOrNull()
        }

        // 5. Navegador Web y Portal Cautivo
        if (isOpenCommand(lower, "navegador", "web", "internet", "portal cautivo", "portal", "login wifi")) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.browser.BrowserActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (lower.contains("portal") || lower.contains("login")) {
                    data = Uri.parse("http://neverssl.com")
                }
            }
            return runCatching {
                ctx.startActivity(intent)
                val msg = if (lower.contains("portal") || lower.contains("login")) {
                    "Abriendo portal cautivo Wi-Fi..."
                } else {
                    "Abriendo navegador web..."
                }
                LocalResult(msg, isAction = true)
            }.getOrNull()
        }

        // 6. Tesla Suite (:tesla y :nfc) - requiere comando explícito de apertura
        if (isOpenCommand(lower, "tesla", "coche", "llave tesla", "frunk", "maletero") ||
            lower.contains("abrir coche") || lower.contains("abrir maletero") || lower.contains("abrir frunk")
        ) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.tesla.TeslaActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                val msg = if (lower.contains("frunk") || lower.contains("maletero")) {
                    "Abriendo maletero delantero (Frunk)..."
                } else {
                    "Abriendo app de Tesla..."
                }
                LocalResult(msg, isAction = true)
            }.getOrNull()
        }

        // 7. Entrenador de Intervalos (:intervals)
        if (isOpenCommand(lower, "tabata", "hiit", "intervalos", "emom", "cronómetro")) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.intervals.IntervalsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                LocalResult("Abriendo entrenador de intervalos...", isAction = true)
            }.getOrNull()
        }

        // 8. Montaña, Brújula y Altímetro (:outdoor)
        if (isOpenCommand(lower, "brújula", "brujula", "altitud", "altímetro", "altimetro", "montaña", "outdoor")) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.outdoor.OutdoorActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                LocalResult("Abriendo brújula y altímetro barométrico...", isAction = true)
            }.getOrNull()
        }

        // 9. Mando Multimedia (:remote)
        if (isOpenCommand(lower, "mando", "control remoto", "multimedia")) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.remote.RemoteActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                LocalResult("Abriendo mando multimedia...", isAction = true)
            }.getOrNull()
        }

        // 10. Wake-on-LAN (:wol)
        if (isOpenCommand(lower, "wake on lan", "wol") || lower.contains("encender pc") ||
            lower.contains("despertar pc") || lower.contains("encender servidor")
        ) {
            val intent = Intent().apply {
                setClassName("io.xenxo", "io.xenxo.wol.WolActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return runCatching {
                ctx.startActivity(intent)
                LocalResult("Abriendo Wake-on-LAN para encender equipo...", isAction = true)
            }.getOrNull()
        }

        return null
    }

    /**
     * Valida que la orden contenga una petición explícita de apertura:
     * Ej: "abre la aplicación de tesla", "abreme la app de tesla", "puedes abrirme la app de...", "abre tesla", etc.
     */
    private fun isOpenCommand(input: String, vararg appKeywords: String): Boolean {
        val openPrefix = "(?:abre(?:me)?|abrir|puedes\\s+abrir(?:me)?|inicia(?:r)?|lanza(?:r)?)"
        val appNoun = "(?:(?:la|el)\\s+)?(?:(?:app|aplicaci[oó]n)\\s+)?(?:de\\s+)?"
        val kwPattern = appKeywords.joinToString("|") { Regex.escape(it) }
        val regex = Regex("(?i)\\b$openPrefix\\s+$appNoun($kwPattern)\\b")
        return regex.containsMatchIn(input)
    }

    private fun extractWeight(s: String): Float? {
        val normalized = s.replace(" con ", ".")
        val p = Pattern.compile("(?:apunta\\s+peso|peso\\s+hoy|he\\s+pesado|guardar\\s+peso|apunta)\\s*(\\d{2,3}(?:[.,]\\d+)?)")
        val m = p.matcher(normalized)
        if (m.find()) {
            val raw = m.group(1)?.replace(',', '.') ?: return null
            return raw.toFloatOrNull()
        }
        return null
    }

    private fun isEmergency(s: String): Boolean {
        val keywords = listOf(
            "dolor de pecho", "dolor en el pecho", "infarto",
            "no puedo respirar", "asfixia", "desmayo", "sin fuerza en el brazo"
        )
        return keywords.any { s.contains(it) }
    }

    private fun setPowerProfile(ctx: Context, profile: String): String {
        return runCatching {
            val intent = Intent("io.xenxo.action.SET_PROFILE").apply {
                putExtra("profile", profile)
                setPackage("io.xenxo")
            }
            ctx.sendBroadcast(intent)
            "Perfil $profile activado ✓"
        }.getOrDefault("Perfil $profile solicitado ✓")
    }
}
