package io.xenxo.wristai.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

object NotificationReplyHelper {

    /**
     * Inyecta el texto en el RemoteInput y dispara el PendingIntent original de la app emisora.
     */
    fun sendReply(context: Context, action: Notification.Action, replyText: String): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()
        for (input in remoteInputs) {
            bundle.putCharSequence(input.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
        return try {
            action.actionIntent.send(context, 0, intent)
            Log.d("xenxo-notif", "Respuesta enviada con éxito: $replyText")
            true
        } catch (e: Exception) {
            Log.e("xenxo-notif", "Error enviando respuesta", e)
            false
        }
    }
}
