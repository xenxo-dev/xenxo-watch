package io.xenxo.tesla

import android.content.Context
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Vibrator
import android.util.Log

/**
 * Servicio Host Card Emulation (HCE) para emulación oficial de la Tarjeta Llave Tesla (Tesla Key Card).
 *
 * AID oficial: "teslaLogic" (0x7465736C614C6F676963) y alternate "F465736C614C6F676963".
 * Funciona de manera 100% OFFLINE y pasiva.
 * Al aproximar el reloj al pilar B o a la consola central, el coche envía los comandos APDU
 * de autenticación y el reloj responde inmediatamente (<15 ms).
 */
class TeslaKeyApduService : HostApduService() {

    private lateinit var keyStore: TeslaKeyStore
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "TeslaKeyHce"

        // Status Words (SW) estándar ISO 7816-4
        private val SW_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
        private val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        // Instrucciones APDU Tesla
        private const val INS_SELECT: Byte = 0xA4.toByte()
        private const val INS_GET_PUBLIC_KEY: Byte = 0x04.toByte()
        private const val INS_AUTH_CHALLENGE: Byte = 0x11.toByte()
        private const val INS_GET_FORM_FACTOR: Byte = 0x14.toByte()
        private const val INS_GET_VERSION: Byte = 0x17.toByte()

        const val ACTION_TESLA_TAP = "io.xenxo.tesla.ACTION_TAP"
    }

    override fun onCreate() {
        super.onCreate()
        keyStore = TeslaKeyStore(this)
        // Precargar clave pública y calentar algoritmos criptográficos antes de cualquier APDU
        keyStore.getOrCreatePublicKeyUncompressed()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        Log.i(TAG, "TeslaKeyApduService inicializado con huella: ${keyStore.getFingerprint()}")
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return SW_WRONG_LENGTH
        }

        val cla = commandApdu[0]
        val ins = commandApdu[1]
        val p1 = commandApdu[2]
        val p2 = commandApdu[3]

        return try {
            when (ins) {
                // 1. SELECT AID (teslaLogic)
                INS_SELECT -> {
                    Log.i(TAG, "APDU -> SELECT AID teslaLogic recibido del vehículo")
                    SW_SUCCESS
                }

                // 2. GET PUBLIC KEY (0x80 0x04 ...)
                INS_GET_PUBLIC_KEY -> {
                    Log.i(TAG, "APDU -> GET PUBLIC KEY solicitado por el vehículo")
                    val pubKey = keyStore.getOrCreatePublicKeyUncompressed()
                    concat(pubKey, SW_SUCCESS)
                }

                // 3. GET FORM FACTOR / VERSION (0x80 0x14 / 0x17)
                INS_GET_FORM_FACTOR, INS_GET_VERSION -> {
                    Log.i(TAG, "APDU -> GET FORM FACTOR / VERSION solicitado")
                    // 0x02 = Wearable / Watch token
                    byteArrayOf(0x02, 0x90.toByte(), 0x00.toByte())
                }

                // 4. AUTHENTICATION CHALLENGE (0x80 0x11 0x00 0x00 0x51 [PubKey 65B] [Challenge 16B])
                INS_AUTH_CHALLENGE -> {
                    Log.i(TAG, "APDU -> AUTHENTICATION CHALLENGE recibido del vehículo")
                    if (commandApdu.size < 5 + 65 + 16) {
                        Log.w(TAG, "Tamaño de reto insuficiente: ${commandApdu.size}")
                        return SW_WRONG_LENGTH
                    }
                    val vehiclePubKey = commandApdu.copyOfRange(5, 5 + 65)
                    val challenge = commandApdu.copyOfRange(5 + 65, 5 + 65 + 16)

                    val response = keyStore.processChallenge(vehiclePubKey, challenge)

                    // Doble vibración háptica de confirmación: el coche ha reconocido la llave
                    notifyCarInteraction()

                    Log.i(TAG, "Autenticación Tesla completada con éxito. Respondiendo al vehículo.")
                    concat(response, SW_SUCCESS)
                }

                else -> {
                    Log.d(TAG, "APDU CLA=%02X INS=%02X no manejada explícitamente".format(cla, ins))
                    SW_SUCCESS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando APDU Tesla", e)
            SW_UNKNOWN
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "TeslaKeyApduService desactivado (razón: $reason)")
    }

    private fun notifyCarInteraction() {
        runCatching {
            // Vibración física de éxito
            vibrator?.vibrate(longArrayOf(0, 80, 50, 80), -1)

            // Difusión para actualizar la UI si TeslaActivity está abierta
            val intent = Intent(ACTION_TESLA_TAP).apply {
                setPackage(packageName)
                putExtra("timestamp", System.currentTimeMillis())
            }
            sendBroadcast(intent)
        }
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }
}
