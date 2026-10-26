package io.xenxo.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Motor de reproducción de audio nativo para "Música xenxo".
 * Utiliza [MediaPlayer] optimizado para Wear OS / Android 9 API 28.
 * Dirige automáticamente el audio a auriculares Bluetooth (AirPods) o altavoz,
 * y permite controlar el volumen del sistema mediante la corona giratoria del reloj.
 */
class MusicPlayer(private val context: Context) {

    companion object {
        private const val TAG = "xenxo-player"
        private const val PROGRESS_INTERVAL_MS = 400L

        @Volatile
        private var instance: MusicPlayer? = null

        fun get(context: Context): MusicPlayer {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayer(context.applicationContext).also { instance = it }
            }
        }
    }

    interface Listener {
        fun onStateChanged(state: PlayState, track: Track?)
        fun onProgress(currentMs: Int, totalMs: Int)
    }

    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentPlaylist: List<Track> = emptyList()
    private var currentIndex: Int = -1

    var currentTrack: Track? = null
        private set

    var state: PlayState = PlayState.STOPPED
        private set

    private val listeners = mutableListOf<Listener>()

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && state == PlayState.PLAYING) {
                try {
                    val pos = mp.currentPosition
                    val dur = mp.duration.coerceAtLeast(1)
                    notifyProgress(pos, dur)
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.w(TAG, "Error consultando progreso", e)
                }
            }
        }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onStateChanged(state, currentTrack)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Establece la lista de reproducción activa y reproduce desde el índice indicado.
     */
    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            stop()
            return
        }
        currentPlaylist = tracks
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        playIndex(safeIndex)
    }

    fun playTrack(track: Track) {
        // Si ya está en la lista, usamos su índice; si no, creamos una lista de uno o la actualizamos
        val idx = currentPlaylist.indexOfFirst { it.id == track.id }
        if (idx != -1) {
            playIndex(idx)
        } else {
            currentPlaylist = listOf(track)
            playIndex(0)
        }
    }

    private fun playIndex(index: Int) {
        if (index !in currentPlaylist.indices) return
        currentIndex = index
        val track = currentPlaylist[index]

        val file = File(track.filePath)
        if (!file.exists()) {
            Log.e(TAG, "Archivo de audio no existe: ${track.filePath}")
            state = PlayState.ERROR
            notifyState(state, track)
            return
        }

        try {
            stopInternal()
            requestAudioFocus()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    currentTrack = track
                    state = PlayState.PLAYING
                    MusicStore.get(context).updateLastPlayed(track.id)
                    notifyState(state, track)
                    handler.removeCallbacks(progressRunnable)
                    handler.post(progressRunnable)
                }
                setOnCompletionListener {
                    handler.removeCallbacks(progressRunnable)
                    next() // Reproducción continua automática
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    state = PlayState.ERROR
                    notifyState(state, currentTrack)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando reproducción", e)
            state = PlayState.ERROR
            notifyState(state, track)
        }
    }

    fun togglePlayPause() {
        when (state) {
            PlayState.PLAYING -> pause()
            PlayState.PAUSED -> resume()
            PlayState.STOPPED, PlayState.ERROR -> {
                if (currentTrack != null && currentIndex in currentPlaylist.indices) {
                    playIndex(currentIndex)
                } else if (currentPlaylist.isNotEmpty()) {
                    playIndex(0)
                }
            }
        }
    }

    fun pause() {
        if (state == PlayState.PLAYING && mediaPlayer?.isPlaying == true) {
            try {
                mediaPlayer?.pause()
                state = PlayState.PAUSED
                handler.removeCallbacks(progressRunnable)
                notifyState(state, currentTrack)
            } catch (e: Exception) {
                Log.w(TAG, "Error al pausar", e)
            }
        }
    }

    fun resume() {
        if (state == PlayState.PAUSED && mediaPlayer != null) {
            try {
                requestAudioFocus()
                mediaPlayer?.start()
                state = PlayState.PLAYING
                notifyState(state, currentTrack)
                handler.removeCallbacks(progressRunnable)
                handler.post(progressRunnable)
            } catch (e: Exception) {
                Log.w(TAG, "Error al reanudar", e)
            }
        } else if (currentTrack != null) {
            playTrack(currentTrack!!)
        }
    }

    fun stop() {
        stopInternal()
        currentTrack = null
        state = PlayState.STOPPED
        notifyState(state, null)
    }

    private fun stopInternal() {
        handler.removeCallbacks(progressRunnable)
        abandonAudioFocus()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando MediaPlayer", e)
        }
        mediaPlayer = null
    }

    fun next() {
        if (currentPlaylist.isEmpty()) return
        val nextIdx = (currentIndex + 1) % currentPlaylist.size
        playIndex(nextIdx)
    }

    fun previous() {
        if (currentPlaylist.isEmpty()) return
        val mp = mediaPlayer
        if (mp != null && mp.isPlaying && mp.currentPosition > 3000) {
            // Si lleva más de 3 segundos, rebobinar al inicio de la misma canción
            mp.seekTo(0)
        } else {
            val prevIdx = if (currentIndex - 1 < 0) currentPlaylist.lastIndex else currentIndex - 1
            playIndex(prevIdx)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Error al hacer seek", e)
        }
    }

    /**
     * Ajusta el volumen del sistema para STREAM_MUSIC con la ruleta / corona.
     * @param direction +1 para subir, -1 para bajar
     * @return Porcentaje actual de volumen (0..100)
     */
    fun adjustVolume(direction: Int): Int {
        val flag = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
        return getVolumePercent()
    }

    /**
     * Obtiene el porcentaje de volumen multimedia actual (0..100).
     */
    fun getVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((cur.toFloat() / max.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Comprueba si el audio se está reproduciendo a través de auriculares Bluetooth (AirPods)
     * o altavoz integrado.
     */
    @Suppress("DEPRECATION")
    fun isAirPodsOrBluetoothConnected(): Boolean {
        return audioManager.isBluetoothA2dpOn
    }

    private var focusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun notifyState(state: PlayState, track: Track?) {
        for (l in listeners) {
            try {
                l.onStateChanged(state, track)
            } catch (e: Exception) {
                Log.w(TAG, "Listener error", e)
            }
        }
    }

    private fun notifyProgress(cur: Int, total: Int) {
        for (l in listeners) {
            try {
                l.onProgress(cur, total)
            } catch (e: Exception) {
                Log.w(TAG, "Listener progress error", e)
            }
        }
    }
}
