package itmo.goto.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mini App launch data signed with a test bot token the way Telegram signs it,
 * so tests can feed the server genuine, stale, or tampered launch strings.
 */
class SignedLaunch(private val token: String, private val fields: Map<String, String>) {
    constructor(token: String, issued: Instant, user: String) :
        this(token, mapOf("auth_date" to issued.epochSecond.toString(), "query_id" to "AAH${issued.nano}", "user" to user))

    fun text(): String =
        (fields + ("hash" to HexFormat.of().formatHex(hash()))).entries
            .joinToString("&") { "${URLEncoder.encode(it.key, UTF_8)}=${URLEncoder.encode(it.value, UTF_8)}" }

    private fun hash(): ByteArray =
        hmac(
            hmac("WebAppData".toByteArray(), token.toByteArray()),
            fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }.toByteArray(),
        )

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
}
