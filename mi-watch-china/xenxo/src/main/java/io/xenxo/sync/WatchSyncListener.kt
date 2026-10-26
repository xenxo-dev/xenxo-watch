package io.xenxo.sync

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.xenxo.music.MusicStore
import io.xenxo.music.MusicSync
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Receptor del reloj para respuestas del móvil (/xenxo/v1/ack) y transferencias
 * de archivos binarios (canciones de Spotube por ChannelClient).
 */
class WatchSyncListener : WearableListenerService() {

    companion object {
        private const val TAG = "xenxo-watch-sync"
        private val pendingTransfers = ConcurrentHashMap<String, Pair<File, String>>()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(e: MessageEvent) {
        when (e.path) {
            XenxoSync.PATH_ACK -> runCatching { XenxoSync.onAck(this, String(e.data)) }
            MusicSync.PATH_MUSIC_STATUS -> {
                runCatching {
                    val json = JSONObject(String(e.data))
                    val sending = json.optInt("sending", 0)
                    val found = json.optInt("found", 0)
                    mainHandler.post {
                        if (sending > 0) {
                            Toast.makeText(this, "Encontradas $found en Spotube. Transfiriendo $sending canciones…", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Música al día ($found encontradas)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        if (path.startsWith(MusicSync.PATH_TRACK_PREFIX)) {
            val encodedName = path.substring(MusicSync.PATH_TRACK_PREFIX.length)
            val fileName = Uri.decode(encodedName)
            val tmpFile = File(cacheDir, "recv_${System.currentTimeMillis()}_$fileName")
            pendingTransfers[channel.path] = Pair(tmpFile, fileName)

            Log.i(TAG, "Recibiendo canción por Bluetooth: $fileName -> ${tmpFile.name}")
            val channelClient = Wearable.getChannelClient(this)
            channelClient.receiveFile(channel, Uri.fromFile(tmpFile), false)
        }
    }

    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        val transfer = pendingTransfers.remove(channel.path) ?: return
        val tmpFile = transfer.first
        val originalName = transfer.second
        if (closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL && tmpFile.exists() && tmpFile.length() > 0) {
            Log.i(TAG, "Transferencia de canción completada: ${tmpFile.name} (${tmpFile.length()} bytes)")
            val store = MusicStore.get(this)
            val track = store.importSingleFile(tmpFile, originalName)
            tmpFile.delete()

            if (track != null) {
                sendBroadcast(Intent("io.xenxo.MUSIC_UPDATED"))
                mainHandler.post {
                    Toast.makeText(this, "🎵 Nueva canción: ${track.title}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.w(TAG, "Fallo o cancelación en transferencia de canal: ${channel.path}, reason=$closeReason")
            tmpFile.delete()
        }
    }

    override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        val transfer = pendingTransfers.remove(channel.path)
        val tmpFile = transfer?.first
        if (tmpFile != null && tmpFile.exists() && closeReason != ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL) {
            tmpFile.delete()
        }
    }
}
