package io.xenxo.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val force = intent?.getBooleanExtra("force", true) ?: true
        XenxoSync.sync(context, force = force)
    }
}
