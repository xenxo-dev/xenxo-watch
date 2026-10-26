package io.xenxo.wristai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

enum class Provider(val label: String, val badge: String) {
    AUTO("Auto (Groq/Gemini)", "⚡"),
    GROQ("Groq", "Q"),
    GEMINI("Gemini", "G"),
    CHATGPT("ChatGPT", "O"),
    CLAUDE("Claude", "C");

    fun next(): Provider = values()[(ordinal + 1) % values().size]
}

/**
 * Ajustes guardados en SharedPreferences privadas de la app.
 * Se importan desde /sdcard/Android/data/io.xenxo.wristai/files/config.json y se borra el archivo.
 */
class Config(private val prefs: SharedPreferences) {

    val groqKey get() = s("groq_key")
    val geminiKey get() = s("gemini_key")
    val openaiKey get() = s("openai_key")
    val anthropicKey get() = s("anthropic_key")

    val groqModel get() = s("groq_model", "llama-3.3-70b-versatile")
    val groqSttModel get() = s("groq_stt_model", "whisper-large-v3-turbo")
    val geminiModel get() = s("gemini_model", "gemini-3.6-flash")
    val ttsVoice get() = s("tts_voice", "aoede")
    val openaiModel get() = s("openai_model", "gpt-5.6-luna")
    val openaiSttModel get() = s("openai_stt_model", "gpt-transcribe")
    val claudeModel get() = s("claude_model", "claude-haiku-5-5")

    val stt get() = s("stt", if (groqKey.isNotBlank()) "groq" else "gemini")
    val language get() = s("language", "es")
    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts", true)
        set(v) = prefs.edit().putBoolean("tts", v).apply()

    var cloudTts: Boolean
        get() = prefs.getBoolean("cloud_tts", false)
        set(v) = prefs.edit().putBoolean("cloud_tts", v).apply()

    var piperTts: Boolean
        get() = prefs.getBoolean("piper_tts", false)
        set(v) = prefs.edit().putBoolean("piper_tts", v).apply()

    var autoListenOnOpen: Boolean
        get() = prefs.getBoolean("auto_listen", true)
        set(v) = prefs.edit().putBoolean("auto_listen", v).apply()

    val systemPrompt get() = s(
        "system_prompt",
        "Eres el asistente inteligente de Xenxo en su reloj. Responde a cualquier consulta " +
            "general, de conocimiento, ciencia, cultura, actualidad, curiosidades, internet o del día a día, " +
            "además de consultas sobre salud, ejercicio y hábitos cuando el usuario lo pregunte. " +
            "Responde SIEMPRE en perfecto español de España (castellano peninsular neutro), con tono cercano, natural y directo " +
            "(máximo 45 palabras, sin listas largas ni formato complejo porque se reproduce en voz alta por el altavoz del reloj)."
    )

    var provider: Provider
        get() = runCatching { Provider.valueOf(s("provider", "AUTO").uppercase()) }.getOrDefault(Provider.AUTO)
        set(v) = prefs.edit().putString("provider", v.name).apply()

    fun hasAnyKey() = groqKey.isNotBlank() || geminiKey.isNotBlank() || openaiKey.isNotBlank() || anthropicKey.isNotBlank()

    private fun s(key: String, def: String = "") = prefs.getString(key, def)?.takeIf { it.isNotBlank() } ?: def

    companion object {
        private val STRING_KEYS = listOf(
            "groq_key", "gemini_key", "openai_key", "anthropic_key",
            "groq_model", "groq_stt_model", "gemini_model", "tts_voice", "openai_model", "openai_stt_model", "claude_model",
            "stt", "language", "system_prompt", "provider"
        )

        fun importIfPresent(ctx: Context, prefs: SharedPreferences): Boolean {
            val f = File(ctx.getExternalFilesDir(null), "config.json")
            if (!f.exists()) return false
            try {
                val j = JSONObject(f.readText())
                val e = prefs.edit()
                STRING_KEYS.forEach { k -> if (j.has(k)) e.putString(k, j.getString(k).trim()) }
                if (j.has("tts")) e.putBoolean("tts", j.getBoolean("tts"))
                if (j.has("auto_listen")) e.putBoolean("auto_listen", j.getBoolean("auto_listen"))
                e.apply()
                return true
            } finally {
                f.delete()
            }
        }
    }
}
