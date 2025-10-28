package github.oftx.smsforwarder.worker

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    fun encrypt(
        payload: String,
        algorithm: String, // e.g., "AES/CBC"
        key: String,
        iv: String
    ): String {
        // Validate inputs
        if (algorithm != "AES/CBC" && algorithm != "AES/ECB") {
            throw IllegalArgumentException("Unsupported algorithm: $algorithm")
        }
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        if (keyBytes.size !in listOf(16, 24, 32)) {
            throw IllegalArgumentException("Invalid key size. Must be 16, 24, or 32 bytes.")
        }

        val ivBytes = iv.toByteArray(Charsets.UTF_8)
        if (algorithm == "AES/CBC" && ivBytes.size != 16) {
            throw IllegalArgumentException("Invalid IV size. Must be 16 bytes for CBC mode.")
        }
        
        // Cipher transformation string uses PKCS5Padding, which is compatible with PKCS7 for AES
        val transformation = "$algorithm/PKCS5Padding"
        val cipher = Cipher.getInstance(transformation)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")

        if (algorithm == "AES/CBC") {
            val ivParameterSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        } else { // ECB mode does not use an IV
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        }

        val encryptedBytes = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }
}
