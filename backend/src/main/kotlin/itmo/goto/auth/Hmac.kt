package itmo.goto.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA-256 under one secret key, the primitive behind Telegram launch signatures
 * and the key that signs application access tokens.
 */
class Hmac(private val key: ByteArray) {
    fun sign(data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
}
