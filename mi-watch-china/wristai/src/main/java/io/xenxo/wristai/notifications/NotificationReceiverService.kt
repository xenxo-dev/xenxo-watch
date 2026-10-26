package io.xenxo.wristai.notifications

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationReceiverService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val n = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        // Ignorar nuestras propias notificaciones para evitar bucles
        if (pkg.startsWith("io.xenxo")) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: "Mensaje"

        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        if (text.isBlank()) return

        // Buscar si la notificación tiene una acción con RemoteInput (respuesta directa)
        val actions = n.actions ?: return
        var replyAction: Notification.Action? = null
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                replyAction = action
                break
            }
        }

        if (replyAction != null) {
            val item = ActionableNotification(
                key = sbn.key,
                packageName = pkg,
                sender = title,
                message = text,
                timestamp = sbn.postTime,
                replyAction = replyAction
            )
            ActiveNotificationStore.put(item)
            Log.d(TAG, "Notificación contestable guardada de $title ($pkg): $text")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.key?.let { ActiveNotificationStore.remove(it) }
    }

    companion object {
        private const val TAG = "xenxo-notif"
    }
}
