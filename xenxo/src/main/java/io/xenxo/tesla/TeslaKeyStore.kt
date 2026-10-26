package io.xenxo.tesla

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de Criptografía de la Tarjeta Llave Tesla (Tesla Key Card) para Xiaomi Mi Watch.
 *
 * Especificación de seguridad oficial Tesla:
 * - Curva elíptica: NIST P-256 (secp256r1 / prime256v1).
 * - Clave pública: 65 bytes sin comprimir (0x04 || X_32B || Y_32B).
 * - Acuerdo de clave: ECDH (Elliptic-curve Diffie–Hellman).
 * - Derivación de clave simétrica: SHA-1(secreto_ecdh) truncado a los primeros 16 bytes (128 bits).
 * - Respuesta al reto: AES-128/ECB/NoPadding sobre el nonce de 16 bytes enviado por el coche.
 *
 * 100% Offline: No necesita internet ni conexión con el móvil para abrir ni arrancar el coche.
 */
class TeslaKeyStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tesla_key_store", Context.MODE_PRIVATE)

    private var keyPair: KeyPair? = null

    // Instancias criptográficas cacheadas para responder al reto APDU en <5 ms sin asignaciones
    private val keyFactory = KeyFactory.getInstance("EC")
    private val keyAgreement = KeyAgreement.getInstance("ECDH")
    private val sha1Digest = MessageDigest.getInstance("SHA-1")
    private val sha256Digest = MessageDigest.getInstance("SHA-256")
    private val aesCipher = Cipher.getInstance("AES/ECB/NoPadding")

    init {
        loadOrCreateKeyPair()
    }

    @Synchronized
    fun getOrCreatePublicKeyUncompressed(): ByteArray {
        val kp = keyPair ?: loadOrCreateKeyPair()
        val ecPub = kp.public as ECPublicKey
        val point = ecPub.w
        val xBytes = toExact32Bytes(point.affineX)
        val yBytes = toExact32Bytes(point.affineY)

        val uncompressed = ByteArray(65)
        uncompressed[0] = 0x04
        System.arraycopy(xBytes, 0, uncompressed, 1, 32)
        System.arraycopy(yBytes, 0, uncompressed, 33, 32)
        return uncompressed
    }

    /**
     * Responde al reto APDU INS 0x11 enviado por el lector NFC del pilar B o la consola central.
     * @param vehiclePubKey65 Clave pública efímera del coche (65 bytes, empieza por 0x04)
     * @param challenge16 Reto / Nonce de 16 bytes
     * @return 16 bytes de respuesta cifrada con AES-128
     */
    @Synchronized
    fun processChallenge(vehiclePubKey65: ByteArray, challenge16: ByteArray): ByteArray {
        val kp = keyPair ?: loadOrCreateKeyPair()
        val privateKey = kp.private as ECPrivateKey
        val myPubKey = kp.public as ECPublicKey

        // 1. Reconstruir la clave pública del vehículo
        val vx = BigInteger(1, vehiclePubKey65.copyOfRange(1, 33))
        val vy = BigInteger(1, vehiclePubKey65.copyOfRange(33, 65))
        val vehiclePoint = ECPoint(vx, vy)
        val vehiclePubKeySpec = ECPublicKeySpec(vehiclePoint, myPubKey.params)
        val vehiclePubKey = keyFactory.generatePublic(vehiclePubKeySpec)

        // 2. Acuerdo de clave ECDH
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(vehiclePubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // 3. Derivación SHA-1 de los primeros 128 bits para la clave AES
        sha1Digest.reset()
        val sha1 = sha1Digest.digest(sharedSecret)
        val aesKey = SecretKeySpec(sha1, 0, 16, "AES")

        // 4. Cifrado AES/ECB/NoPadding del reto
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
        return aesCipher.doFinal(challenge16)
    }

    @Synchronized
    fun getFingerprint(): String {
        val pub = getOrCreatePublicKeyUncompressed()
        sha256Digest.reset()
        val digest = sha256Digest.digest(pub)
        return digest.take(8).joinToString(":") { "%02X".format(it) }
    }

    fun regenerateKeys() {
        prefs.edit().clear().apply()
        keyPair = generateAndSaveKeyPair()
    }

    @Synchronized
    private fun loadOrCreateKeyPair(): KeyPair {
        val privBase64 = prefs.getString("ec_priv", null)
        val pubBase64 = prefs.getString("ec_pub", null)

        if (privBase64 != null && pubBase64 != null) {
            runCatching {
                val kf = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(privBase64, Base64.NO_WRAP)
                val pubBytes = Base64.decode(pubBase64, Base64.NO_WRAP)
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                val kp = KeyPair(pub, priv)
                keyPair = kp
                return kp
            }
        }
        val kp = generateAndSaveKeyPair()
        keyPair = kp
        return kp
    }

    private fun generateAndSaveKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec)
        val kp = kpg.generateKeyPair()

        val privBase64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val pubBase64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString("ec_priv", privBase64)
            .putString("ec_pub", pubBase64)
            .putLong("created_at", System.currentTimeMillis())
            .apply()

        return kp
    }

    private fun toExact32Bytes(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val exact = ByteArray(32)
        if (raw.size > 32) {
            System.arraycopy(raw, raw.size - 32, exact, 0, 32)
        } else {
            System.arraycopy(raw, 0, exact, 32 - raw.size, raw.size)
        }
        return exact
    }
}
