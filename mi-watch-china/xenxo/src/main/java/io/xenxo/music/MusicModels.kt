package io.xenxo.music

import java.util.Locale

/**
 * Representa una pista de audio guardada en el almacenamiento local del reloj.
 * Se gestiona en una cola rotativa con un máximo estricto de 20 canciones.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val filePath: String,
    val addedAt: Long,
    val lastPlayedAt: Long = 0L,
    val fileSizeBytes: Long = 0L
) {
    /** Formato de duración amigable m:ss (ej. 3:45). */
    val formattedDuration: String
        get() {
            if (durationSec <= 0) return "--:--"
            val m = durationSec / 60
            val s = durationSec % 60
            return String.format(Locale.ROOT, "%d:%02d", m, s)
        }

    /** Tamaño del archivo formateado (ej. 3.4 MB). */
    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0L) return ""
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            return String.format(Locale.ROOT, "%.1f MB", mb)
        }
}

/** Estado de reproducción del motor de audio. */
enum class PlayState {
    STOPPED,
    PLAYING,
    PAUSED,
    ERROR
}
