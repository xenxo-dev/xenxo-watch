package io.xenxo.wristai

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Un turno de conversación ya resuelto (texto). */
data class Turn(val user: String, val assistant: String)

/** Resultado de una pregunta: lo que se entendió, la respuesta y el proveedor que contestó. */
data class Answer(
    val question: String,
    val text: String,
    val providerBadge: String = "⚡",
    val isAction: Boolean = false,
    val isEmergency: Boolean = false,
    val audioBytes: ByteArray? = null
)

class ApiException(msg: String) : Exception(msg)

/**
 * Cliente HTTP directo para Groq, Gemini, OpenAI y Claude.
 * Sin SDKs: HttpURLConnection + org.json nativos de Android.
 */
class AiClient(private val cfg: Config) {

    // ---------- Punto de entrada inteligente ----------

    fun ask(
        provider: Provider,
        audio: File,
        history: List<Turn>,
        healthContextJson: String = "",
        ctx: Context? = null
    ): Answer {
        return when (provider) {
            Provider.AUTO -> askAuto(audio, history, healthContextJson, ctx)
            Provider.GROQ -> {
                val q = groqTranscribe(audio)
                if (ctx != null) {
                    val localAction = LocalIntentParser.tryExecute(ctx, q)
                    if (localAction != null) {
                        return Answer(q, localAction.text, "⚡", isAction = localAction.isAction, isEmergency = localAction.isEmergency)
                    }
                }
                Answer(q, groqChat(q, history, buildPrompt(healthContextJson)), "Q")
            }
            Provider.GEMINI -> {
                val ans = geminiAskAudio(audio, history, buildPrompt(healthContextJson))
                if (ctx != null) {
                    val localAction = LocalIntentParser.tryExecute(ctx, ans.question)
                    if (localAction != null) {
                        return Answer(ans.question, localAction.text, "⚡", isAction = localAction.isAction, isEmergency = localAction.isEmergency)
                    }
                }
                ans
            }
            Provider.CHATGPT -> {
                val q = transcribe(audio)
                Answer(q, openAiChat(q, history), "O")
            }
            Provider.CLAUDE -> {
                val q = transcribe(audio)
                Answer(q, claudeChat(q, history), "C")
            }
        }
    }

    /**
     * Cadena inteligente:
     * 1. Transcripción: Groq Whisper (ultra-rápido ~200 ms) ➔ Gemini de respaldo.
     * 2. Comandos locales rápidos (10 ms): peso, perfiles, 112 sin llamar a la IA.
     * 3. Inferencia LLM con snapshot de salud en 1 solo salto: Groq Llama 3.3 ➔ Gemini Flash ➔ Modo Básico.
     */
    private fun askAuto(
        audio: File,
        history: List<Turn>,
        healthContextJson: String,
        ctx: Context?
    ): Answer {
        // Si solo tenemos Gemini (sin Groq), hacer transcripción + respuesta en 1 solo salto HTTP
        if (cfg.groqKey.isBlank() && cfg.geminiKey.isNotBlank()) {
            val prompt = buildPrompt(healthContextJson)
            val geminiAns = geminiAskAudio(audio, history, prompt)
            if (ctx != null) {
                val localRes = LocalIntentParser.tryExecute(ctx, geminiAns.question)
                if (localRes != null) {
                    return Answer(
                        question = geminiAns.question,
                        text = localRes.text,
                        providerBadge = "⚡",
                        isAction = localRes.isAction,
                        isEmergency = localRes.isEmergency
                    )
                }
            }
            return geminiAns
        }

        // Paso 1: Transcribir audio
        var question = ""
        if (cfg.groqKey.isNotBlank()) {
            question = runCatching { groqTranscribe(audio) }.getOrDefault("")
        }
        if (question.isBlank() && cfg.geminiKey.isNotBlank()) {
            question = runCatching { geminiTranscribe(audio) }.getOrDefault("")
        }
        if (question.isBlank()) {
            throw ApiException("No se ha entendido el audio (comprueba conexión y claves de API)")
        }

        // Paso 2: Atajo local en 10 ms (sin tocar LLM ni gastar tokens)
        if (ctx != null) {
            val localRes = LocalIntentParser.tryExecute(ctx, question)
            if (localRes != null) {
                return Answer(
                    question = question,
                    text = localRes.text,
                    providerBadge = "⚡",
                    isAction = localRes.isAction,
                    isEmergency = localRes.isEmergency
                )
            }
        }

        // Paso 3: LLM con contexto de datos locales en 1 solo viaje HTTP
        val prompt = buildPrompt(healthContextJson)

        // Intento 1: Groq Cloud (LPU dedicada, ~250 ms)
        if (cfg.groqKey.isNotBlank()) {
            val groqResp = runCatching { groqChat(question, history, prompt) }.getOrNull()
            if (!groqResp.isNullOrBlank()) {
                return Answer(question, groqResp, "Q")
            }
        }

        // Intento 2: Gemini Flash de respaldo
        if (cfg.geminiKey.isNotBlank()) {
            val geminiResp = runCatching { geminiChat(question, history, prompt) }.getOrNull()
            if (!geminiResp.isNullOrBlank()) {
                val audioBytes = if (cfg.ttsEnabled) geminiTts(geminiResp) else null
                return Answer(question, geminiResp, "G", audioBytes = audioBytes)
            }
        }

        // Intento 3: Modo Básico 100% offline
        return Answer(
            question = question,
            text = generateBasicAnswer(question, healthContextJson),
            providerBadge = "⚙️"
        )
    }

    private fun buildPrompt(healthContextJson: String): String = buildString {
        append(cfg.systemPrompt)
        append("\nREGLAS IMPORTANTES: Responde siempre en castellano peninsular neutro. Sé conciso y claro (máximo 45 palabras). ")
        append("Si la pregunta es de cultura general, internet, ciencia, historia, noticias o del día a día, contéstala directamente con tu mejor conocimiento. ")
        append("Si el usuario pregunta específicamente por sus entrenamientos, peso, pasos, batería o salud, apóyate en los datos de su reloj. ")
        append("Ante urgencias médicas graves (dolor en el pecho, asfixia), ordena llamar de inmediato al 112.\n")
        if (healthContextJson.isNotBlank()) {
            append("[CONTEXTO DEL RELOJ (USAR SOLO SI EL USUARIO CONSULTA SUS MÉTRICAS O SALUD)]\n")
            append(healthContextJson)
        }
    }

    private fun generateBasicAnswer(question: String, jsonStr: String): String {
        val lower = question.lowercase()
        return runCatching {
            val j = if (jsonStr.isNotBlank()) JSONObject(jsonStr) else null
            when {
                lower.contains("peso") -> {
                    val p = j?.optJSONObject("peso")
                    "Hoy pesas ${p?.optDouble("hoy")} kg (media semanal: ${p?.optDouble("media_7d")} kg)."
                }
                lower.contains("gym") || lower.contains("entreno") -> {
                    val g = j?.optJSONObject("ultimo_gym")
                    if (g != null) "Último gym: ${g.optString("fecha")} (${g.optInt("total_series")} series). ${g.optString("destacado")}."
                    else "No hay entrenos registrados recientes."
                }
                lower.contains("pádel") || lower.contains("padel") -> {
                    val pad = j?.optJSONObject("ultimo_padel")
                    if (pad != null) "Último pádel: ${pad.optString("resultado")} (${pad.optString("estado")})."
                    else "No hay partidos de pádel recientes."
                }
                lower.contains("paso") || lower.contains("kcal") || lower.contains("pulso") -> {
                    val h = j?.optJSONObject("hoy")
                    "Hoy: ${h?.optInt("pasos")} pasos, ${h?.optInt("kcal")} kcal y ${h?.optInt("ppm")} ppm."
                }
                lower.contains("batería") || lower.contains("bateria") -> {
                    val b = j?.optJSONObject("bateria")
                    "Batería al ${b?.optInt("nivel")}% (gasto: ${b?.optDouble("gasto_pct_h")}%/h)."
                }
                else -> "Sin conexión a internet en este momento para responder a esta consulta."
            }
        }.getOrDefault("Modo básico local activo. Sin conexión.")
    }

    /** Voz → texto con el proveedor configurado. */
    private fun transcribe(audio: File): String {
        val text = when (cfg.stt) {
            "groq" -> groqTranscribe(audio)
            "openai" -> openAiTranscribe(audio)
            else -> geminiTranscribe(audio)
        }
        if (text.isBlank()) throw ApiException("No se ha entendido el audio")
        return text.trim()
    }

    // ---------- Groq Cloud ----------

    private fun groqTranscribe(audio: File): String {
        needKey(cfg.groqKey, "groq_key")
        val fields = mutableMapOf(
            "model" to cfg.groqSttModel,
            "response_format" to "json"
        )
        if (cfg.language.isNotBlank()) fields["language"] = cfg.language
        val resp = Http.postMultipart(
            "https://api.groq.com/openai/v1/audio/transcriptions",
            mapOf("Authorization" to "Bearer ${cfg.groqKey}"),
            fields, "file", audio, "audio/mp4"
        )
        return resp.optString("text").trim()
    }

    private fun groqChat(question: String, history: List<Turn>, systemPrompt: String): String {
        needKey(cfg.groqKey, "groq_key")
        val msgs = JSONArray().put(msg("system", systemPrompt))
        history.forEach { msgs.put(msg("user", it.user)); msgs.put(msg("assistant", it.assistant)) }
        msgs.put(msg("user", question))

        val body = JSONObject()
            .put("model", cfg.groqModel)
            .put("messages", msgs)
            .put("max_tokens", 150)
            .put("temperature", 0.4)

        val resp = Http.postJson(
            "https://api.groq.com/openai/v1/chat/completions",
            mapOf("Authorization" to "Bearer ${cfg.groqKey}"), body
        )
        return resp.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").trim()
    }

    // ---------- Gemini ----------

    private fun geminiUrl(model: String): String {
        needKey(cfg.geminiKey, "gemini_key")
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${cfg.geminiKey}"
    }

    private fun geminiHeaders(): Map<String, String> {
        needKey(cfg.geminiKey, "gemini_key")
        return mapOf("x-goog-api-key" to cfg.geminiKey)
    }

    private fun audioPart(audio: File): JSONObject = JSONObject().put(
        "inline_data", JSONObject()
            .put("mime_type", "audio/mp4")
            .put("data", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP))
    )

    private fun geminiAskAudio(audio: File, history: List<Turn>, systemPrompt: String): Answer {
        val system = systemPrompt +
            " Recibirás la pregunta del usuario como audio (puede ser sobre cualquier tema: cultura general, ciencia, internet, o sobre sus datos de salud). " +
            "Devuelve SIEMPRE Y ÚNICAMENTE un JSON con dos claves: " +
            "\"pregunta\" (transcripción literal) y \"respuesta\" (tu respuesta corta y concisa en castellano peninsular, redactada con frases directas y naturales pensadas para ser leídas por voz alta, sin listas, viñetas ni paréntesis, máximo 30 palabras)."

        val contents = JSONArray()
        history.forEach { t ->
            contents.put(textTurn("user", t.user))
            contents.put(textTurn("model", t.assistant))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(audioPart(audio))))

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        // Prioridad absoluta al modelo más rápido y disponible (gemini-3.5-flash-lite ~500ms)
        val models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.5-flash", cfg.geminiModel).distinct()
        var lastEx: Exception? = null
        for (m in models) {
            try {
                val raw = geminiText(Http.postJson(geminiUrl(m), geminiHeaders(), body))
                var q = "(audio)"
                var r = raw
                try {
                    val j = JSONObject(raw)
                    q = j.optString("pregunta", "(audio)")
                    r = j.optString("respuesta", raw)
                } catch (_: Exception) {}

                // Devolver inmediatamente para que el texto y la voz empiecen sin esperar a Cloud TTS
                return Answer(q, r, "G", audioBytes = null)
            } catch (e: Exception) {
                lastEx = e
                android.util.Log.w("AiClient", "geminiAskAudio with $m failed: ${e.message}, trying next...")
            }
        }
        throw lastEx ?: ApiException("Gemini no pudo procesar la consulta")
    }

    /** Sintetiza voz en castellano directamente con Gemini Cloud TTS (24kHz 16-bit Mono PCM). */
    fun geminiTts(text: String): ByteArray? {
        if (cfg.geminiKey.isBlank() || text.isBlank()) return null
        val clean = prepareForSpeech(text)
        if (clean.isBlank()) return null

        val voice = cfg.ttsVoice
        val models = listOf("gemini-2.5-flash-preview-tts", "gemini-3.8-flash-lite-tts")

        for (m in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=${cfg.geminiKey}"
                val speechConfig = JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))
                val genConfig = JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", speechConfig)

                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", clean)))))
                    .put("generationConfig", genConfig)

                val resp = Http.postJson(url, geminiHeaders(), body)
                val cand = resp.optJSONArray("candidates")?.optJSONObject(0)
                val part = cand?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                val b64 = part?.optJSONObject("inlineData")?.optString("data") ?: continue
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val pcm = if (bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
                    bytes.copyOfRange(44, bytes.size)
                } else {
                    bytes
                }
                if (pcm.isNotEmpty()) return pcm
            } catch (e: Exception) {
                android.util.Log.w("AiClient", "geminiTts model $m failed: ${e.message}, trying next...")
            }
        }
        return null
    }

    private fun geminiChat(question: String, history: List<Turn>, systemPrompt: String): String {
        val contents = JSONArray()
        history.forEach { t ->
            contents.put(textTurn("user", t.user))
            contents.put(textTurn("model", t.assistant))
        }
        contents.put(textTurn("user", question))

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)

        val models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.5-flash", cfg.geminiModel).distinct()
        var lastEx: Exception? = null
        for (m in models) {
            try {
                return geminiText(Http.postJson(geminiUrl(m), geminiHeaders(), body))
            } catch (e: Exception) {
                lastEx = e
                android.util.Log.w("AiClient", "geminiChat with $m failed: ${e.message}, trying next...")
            }
        }
        throw lastEx ?: ApiException("Gemini no pudo responder")
    }

    private fun geminiTranscribe(audio: File): String {
        val parts = JSONArray()
            .put(JSONObject().put("text",
                "Transcribe literalmente este audio en su idioma original. Devuelve solo la transcripción, sin comillas."))
            .put(audioPart(audio))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))

        val models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.5-flash", cfg.geminiModel).distinct()
        for (m in models) {
            try {
                return geminiText(Http.postJson(geminiUrl(m), geminiHeaders(), body))
            } catch (e: Exception) {
                android.util.Log.w("AiClient", "geminiTranscribe with $m failed: ${e.message}")
            }
        }
        throw ApiException("No se pudo transcribir el audio con Gemini")
    }

    private fun textTurn(role: String, text: String) = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun geminiText(resp: JSONObject): String {
        val cand = resp.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw ApiException("Gemini no devolvió respuesta")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        return sb.toString().trim()
    }

    // ---------- OpenAI ----------

    private fun openAiTranscribe(audio: File): String {
        needKey(cfg.openaiKey, "openai_key")
        val fields = mutableMapOf("model" to cfg.openaiSttModel)
        if (cfg.language.isNotBlank()) fields["language"] = cfg.language
        val resp = Http.postMultipart(
            "https://api.openai.com/v1/audio/transcriptions",
            mapOf("Authorization" to "Bearer ${cfg.openaiKey}"),
            fields, "file", audio, "audio/mp4"
        )
        return resp.optString("text").trim()
    }

    private fun openAiChat(question: String, history: List<Turn>): String {
        needKey(cfg.openaiKey, "openai_key")
        val msgs = JSONArray().put(msg("system", cfg.systemPrompt))
        history.forEach { msgs.put(msg("user", it.user)); msgs.put(msg("assistant", it.assistant)) }
        msgs.put(msg("user", question))

        val body = JSONObject().put("model", cfg.openaiModel).put("messages", msgs)
        val resp = Http.postJson(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer ${cfg.openaiKey}"), body
        )
        return resp.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").trim()
    }

    // ---------- Anthropic (Claude) ----------

    private fun claudeChat(question: String, history: List<Turn>): String {
        needKey(cfg.anthropicKey, "anthropic_key")
        val msgs = JSONArray()
        history.forEach { msgs.put(msg("user", it.user)); msgs.put(msg("assistant", it.assistant)) }
        msgs.put(msg("user", question))

        val body = JSONObject()
            .put("model", cfg.claudeModel)
            .put("max_tokens", 400)
            .put("system", cfg.systemPrompt)
            .put("messages", msgs)
        val resp = Http.postJson(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to cfg.anthropicKey, "anthropic-version" to "2023-06-01"), body
        )
        val content = resp.optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val c = content.getJSONObject(i)
            if (c.optString("type") == "text") sb.append(c.optString("text"))
        }
        return sb.toString().trim()
    }

    // ---------- utilidades ----------

    private fun msg(role: String, content: String) = JSONObject().put("role", role).put("content", content)

    private fun needKey(key: String, name: String) {
        if (key.isBlank()) throw ApiException("Falta \"$name\" en config.json")
    }

    companion object {
        fun prepareForSpeech(text: String): String {
            return text
                .replace(Regex("[*#`_~•·|]"), " ")
                .replace(Regex("[\\[\\]{}()]"), " ")
                .replace(Regex("(\\d{4})-(\\d{2})-(\\d{2})")) { m ->
                    val d = m.groupValues[3].toIntOrNull() ?: 1
                    val month = when (m.groupValues[2]) {
                        "01" -> "enero"; "02" -> "febrero"; "03" -> "marzo"; "04" -> "abril"
                        "05" -> "mayo"; "06" -> "junio"; "07" -> "julio"; "08" -> "agosto"
                        "09" -> "septiembre"; "10" -> "octubre"; "11" -> "noviembre"; "12" -> "diciembre"
                        else -> "mes ${m.groupValues[2]}"
                    }
                    "$d de $month"
                }
                .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*km/h\\b"), "$1 kilómetros por hora")
                .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*km\\b"), "$1 kilómetros")
                .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*kg\\b"), "$1 kilos")
                .replace(Regex("(?i)(\\d+)\\s*kcal\\b"), "$1 calorías")
                .replace(Regex("(?i)(\\d+)\\s*ppm\\b"), "$1 pulsaciones")
                .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*%/h(?:ora)?\\b"), "$1 por ciento por hora")
                .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*%"), "$1 por ciento")
                .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*º?C\\b"), "$1 grados")
                .replace(Regex("([0-9]+)\\.([0-9]+)"), "$1 coma $2")
                .replace(Regex("(?i)\\b(\\d+)x(\\d+(?:[.,]\\d+)?)(?:kg)?\\b"), "$1 repeticiones")
                .replace(Regex("[\\p{So}\\p{Cn}]"), " ")
                .replace(Regex("(?i)^\\s*(sí|no|claro|exacto|hola)\\s+"), "$1, ")
                .replace(Regex("(?i)(?<=[\\wáéíóúñ])\\s+(pero|porque|ya que|aunque|además|sin embargo|por tanto|por lo que|por ejemplo|es decir|en cambio|así que|de modo que)\\b"), ", $1")
                .replace(Regex("([.,!?:;])(?=[A-Za-zÁÉÍÓÚáéíóúñÑ0-9])"), "$1 ")
                .replace(Regex(",\\s*,"), ",")
                .replace(Regex("\\.\\s*,"), ".")
                .replace(Regex(",\\s*\\."), ".")
                .replace(Regex("\\.{2,}"), ".")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}

/** HTTP mínimo con HttpURLConnection nativo de Android. */
object Http {
    private const val CONNECT_TIMEOUT = 8_000
    private const val READ_TIMEOUT = 12_000

    fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val c = open(url, headers)
        return try {
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            read(c)
        } finally {
            c.disconnect()
        }
    }

    fun postMultipart(
        url: String, headers: Map<String, String>, fields: Map<String, String>,
        fileField: String, file: File, mime: String
    ): JSONObject {
        val boundary = "----xenxo${System.currentTimeMillis()}"
        val c = open(url, headers)
        return try {
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.outputStream.use { out ->
                fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                fields.forEach { (k, v) ->
                    w("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
                }
                w("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"${file.name}\"\r\n")
                w("Content-Type: $mime\r\n\r\n")
                out.write(file.readBytes())
                w("\r\n--$boundary--\r\n")
            }
            read(c)
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = CONNECT_TIMEOUT
        c.readTimeout = READ_TIMEOUT
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    private fun read(c: HttpURLConnection): JSONObject {
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException("HTTP $code: ${extractError(text)}")
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun extractError(text: String): String = try {
        val j = JSONObject(text)
        val e = j.opt("error")
        when (e) {
            is JSONObject -> e.optString("message", text)
            is String -> e
            else -> text
        }
    } catch (e: Exception) {
        text
    }.take(160)
}
