package io.xenxo.wristai.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Almacén en memoria de las notificaciones activas que admiten respuesta directa (RemoteInput).
 */
data class ActionableNotification(
    val key: String,
    val packageName: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val replyAction: Notification.Action
)

object ActiveNotificationStore {
    private const val MAX_ENTRIES = 20
    private val store = ConcurrentHashMap<String, ActionableNotification>()

    fun put(notif: ActionableNotification) {
        store[notif.key] = notif
        if (store.size > MAX_ENTRIES) {
            val oldestKey = store.values.minByOrNull { it.timestamp }?.key
            if (oldestKey != null) {
                store.remove(oldestKey)
            }
        }
    }

    fun remove(key: String) {
        store.remove(key)
    }

    fun get(key: String): ActionableNotification? = store[key]

    fun getLatest(): ActionableNotification? {
        return store.values.maxByOrNull { it.timestamp }
    }

    fun getAll(): List<ActionableNotification> {
        return store.values.sortedByDescending { it.timestamp }
    }

    fun clear() {
        store.clear()
    }
}
