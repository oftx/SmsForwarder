package github.oftx.smsforwarder.worker

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    fun encrypt(
        payload: String,
        mode: String, // e.g., "CBC", "GCM"
        key: String,
        iv: String
    ): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val ivBytes = iv.toByteArray(Charsets.UTF_8)

        val padding = if (mode == "GCM") "NoPadding" else "PKCS5Padding"
        val transformation = "AES/$mode/$padding"

        val cipher = Cipher.getInstance(transformation)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")

        when (mode) {
            "CBC" -> {
                if (ivBytes.size != 16) throw IllegalArgumentException("IV size must be 16 bytes for CBC mode.")
                val ivParameterSpec = IvParameterSpec(ivBytes)
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            }
            "GCM" -> {
                // GCM standard nonce size is 12 bytes, tag length is 128 bits
                if (ivBytes.size != 12) throw IllegalArgumentException("IV size must be 12 bytes for GCM mode.")
                val gcmParameterSpec = GCMParameterSpec(128, ivBytes)
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
            }
            "ECB" -> {
                // ECB does not use an IV
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            }
            else -> throw IllegalArgumentException("Unsupported mode: $mode")
        }

        val encryptedBytes = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }
}
