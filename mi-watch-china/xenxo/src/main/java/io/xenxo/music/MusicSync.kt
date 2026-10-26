package io.xenxo.music

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente de sincronización de música entre el Xiaomi Mi Watch y el móvil vía Bluetooth Data Layer.
 * Permite que el reloj solicite al móvil las últimas canciones descargadas en Spotube (/Downloads/Spotube)
 * y las reciba en segundo plano sin intervención manual.
 */
object MusicSync {
    private const val TAG = "xenxo-music-sync"
    const val PATH_MUSIC_SYNC = "/xenxo/v1/music/sync"
    const val PATH_MUSIC_STATUS = "/xenxo/v1/music/status"
    const val PATH_TRACK_PREFIX = "/xenxo/v1/music/track/"

    fun requestSync(context: Context) {
        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Toast.makeText(context, "Móvil no conectado por Bluetooth", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val store = MusicStore.get(context)
            val existingTracks = store.getAllTracks()
            val existingArray = JSONArray()
            for (t in existingTracks) {
                existingArray.put(t.title.lowercase())
            }

            val req = JSONObject().apply {
                put("v", 1)
                put("existing", existingArray)
                put("maxNeeded", MusicStore.MAX_TRACKS)
            }

            val msg = req.toString().toByteArray(Charsets.UTF_8)
            val msgClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                msgClient.sendMessage(node.id, PATH_MUSIC_SYNC, msg)
                    .addOnSuccessListener {
                        Log.i(TAG, "Petición de música enviada al nodo ${node.displayName} (${node.id})")
                        Toast.makeText(context, "Buscando canciones en Spotube móvil…", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Fallo al enviar petición de música a ${node.id}", e)
                        Toast.makeText(context, "Error conectando con móvil", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Error obteniendo nodos conectados", e)
            Toast.makeText(context, "Error Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }
}
