package io.xenxo.wristai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Reproductor de audio PCM para TTS en directo (Gemini 24kHz 16-bit Mono).
 * Transmite audio directamente al altavoz del reloj Wear OS vía AudioTrack
 * y emite amplitudes normalizadas para animar el VoiceOrbView en tiempo real.
 */
class PcmAudioPlayer(private val onAmplitude: (Float) -> Unit) {

    private var track: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    fun play(pcm: ByteArray, sampleRate: Int = 22050, onStart: () -> Unit, onDone: () -> Unit) {
        stop()
        if (pcm.isEmpty()) {
            onDone()
            return
        }

        isPlaying = true
        Thread({
            var audioTrack: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufSize = maxOf(minBuf, 4096)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                audioTrack = AudioTrack(
                    audioAttributes,
                    audioFormat,
                    bufSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                track = audioTrack
                audioTrack.play()
                onStart()

                var offset = 0
                val chunkSize = 2400 // ~50 ms por bloque (24000 muestras/s * 2 bytes/muestra * 0.05s)
                while (isPlaying && offset < pcm.size) {
                    val toWrite = minOf(chunkSize, pcm.size - offset)
                    val written = audioTrack.write(pcm, offset, toWrite)
                    if (written <= 0) break

                    // Calcular amplitud máxima del bloque para el orbe
                    var maxAmp = 0
                    var i = offset
                    while (i < offset + written - 1) {
                        val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                        val absVal = kotlin.math.abs(sample.toShort().toInt())
                        if (absVal > maxAmp) maxAmp = absVal
                        i += 2
                    }
                    val norm = (maxAmp / 22000f).coerceIn(0f, 1f)
                    onAmplitude(norm)

                    offset += written
                }

                if (isPlaying) {
                    // Dar margen a que termine de salir el buffer de audio
                    Thread.sleep(150)
                }
            } catch (e: Exception) {
                Log.e("PcmAudioPlayer", "Error reproduciendo PCM TTS: ${e.message}")
            } finally {
                onAmplitude(0f)
                try {
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
                if (track == audioTrack) track = null
                isPlaying = false
                onDone()
            }
        }, "PcmAudioPlayerThread").start()
    }

    fun stop() {
        isPlaying = false
        try {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        } catch (_: Exception) {}
        track = null
        onAmplitude(0f)
    }
}
