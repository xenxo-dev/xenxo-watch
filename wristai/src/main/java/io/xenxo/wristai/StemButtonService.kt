package io.xenxo.wristai

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad para interceptar de forma global el botón físico inferior (STEM_1 / 265).
 * Se activa desde cualquier pantalla (esfera, panal o app), despierta la pantalla si está atenuada,
 * vibra inmediatamente y lanza o conmuta xenxoVoice (WristAI).
 */
class StemButtonService : AccessibilityService() {

    private var vibrator: Vibrator? = null
    private var powerManager: PowerManager? = null
    private var lastTriggerTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        Log.i("StemButtonService", "StemButtonService connected and listening for physical stem keys")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_STEM_1 ||
            keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
            keyCode == 265
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > 300) {
                    lastTriggerTime = now
                    handleStemPress()
                }
            }
            return true // Consume para evitar conflictos con el sistema o el launcher
        }
        return super.onKeyEvent(event)
    }

    private fun handleStemPress() {
        Log.i("StemButtonService", "STEM button pressed -> launching/triggering WristAI")
        runCatching { vibrator?.vibrate(40) }

        // Despertar la pantalla si está atenuada
        runCatching {
            @Suppress("DEPRECATION")
            powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "wristai:stem_wake"
            )?.acquire(2500)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from_stem_button", true)
        }
        startActivity(intent)
    }
}
