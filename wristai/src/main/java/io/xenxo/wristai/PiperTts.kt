package io.xenxo.wristai

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

object PiperTts {
    private const val TAG = "PiperTTS"
    private var tts: OfflineTts? = null
    private var initialized = false

    fun init(ctx: Context): Boolean {
        if (initialized) return tts != null
        initialized = true

        val candidates = listOf(
            File("/sdcard/piper"),
            File("/storage/emulated/0/piper"),
            File(ctx.filesDir, "piper"),
            File(ctx.getExternalFilesDir(null), "piper")
        )
        val piperDir = candidates.firstOrNull {
            File(it, "es_ES-carlfm-x_low.onnx").exists() && File(it, "tokens.txt").exists()
        }

        if (piperDir == null) {
            Log.w(TAG, "Piper model files no encontrados en rutas candidatas")
            return false
        }

        val modelFile = File(piperDir, "es_ES-carlfm-x_low.onnx")
        val tokensFile = File(piperDir, "tokens.txt")
        val dataDir = File(piperDir, "espeak-ng-data")

        return try {
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = dataDir.absolutePath
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 4,
                debug = false
            )
            val config = OfflineTtsConfig(
                model = modelConfig
            )

            tts = OfflineTts(config = config)
            Log.i(TAG, "Piper TTS VITS inicializado con éxito! sampleRate=${tts?.sampleRate()}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error inicializando Piper TTS: ${e.message}", e)
            tts = null
            false
        }
    }

    fun isAvailable(): Boolean = tts != null

    fun getSampleRate(): Int = tts?.sampleRate() ?: 22050

    /**
     * Sintetiza el texto usando Piper VITS e inserta pausas reales de silencio entre frases.
     * Devuelve audio PCM de 16-bit mono listo para PcmAudioPlayer.
     */
    fun synthesizeWithPauses(text: String, speed: Float = 1.0f): ByteArray? {
        val engine = tts ?: return null
        return try {
            val sampleRate = engine.sampleRate()
            val cleaned = AiClient.prepareForSpeech(text)
            val phrases = cleaned.split(Regex("(?<=[.,!?:;])\\s+")).filter { it.isNotBlank() }
            if (phrases.isEmpty()) return null

            val pcmParts = mutableListOf<ByteArray>()

            for (i in phrases.indices) {
                val phrase = phrases[i]
                val audio = engine.generate(phrase, sid = 0, speed = speed)
                val samples = audio.samples
                if (samples != null && samples.isNotEmpty()) {
                    val partBytes = ByteArray(samples.size * 2)
                    for (j in samples.indices) {
                        val s = (samples[j].coerceIn(-1f, 1f) * 32767).toInt()
                        partBytes[j * 2] = (s and 0xFF).toByte()
                        partBytes[j * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    pcmParts.add(partBytes)

                    // Micro-pausa física real entre frases
                    if (i < phrases.size - 1) {
                        val pauseSeconds = if (phrase.endsWith(".") || phrase.endsWith("!") || phrase.endsWith("?")) 0.28f else 0.16f
                        val silenceBytesCount = (sampleRate * pauseSeconds * 2).toInt() and -2
                        pcmParts.add(ByteArray(silenceBytesCount))
                    }
                }
            }

            val totalSize = pcmParts.sumOf { it.size }
            if (totalSize == 0) return null

            val result = ByteArray(totalSize)
            var offset = 0
            for (part in pcmParts) {
                System.arraycopy(part, 0, result, offset, part.size)
                offset += part.size
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error sintetizando con Piper: ${e.message}", e)
            null
        }
    }
}
