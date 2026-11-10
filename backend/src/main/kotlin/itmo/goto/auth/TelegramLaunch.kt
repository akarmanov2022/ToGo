package itmo.goto.auth

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

/**
 * Checks Mini App launch data against the bot token signature and the server clock, following
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app, so only
 * fresh data that Telegram signed can identify a user.
 */
class TelegramLaunch(private val token: String, private val clock: Clock) {
    private val mapper = JsonMapper.builder().build()

    fun user(data: String): TelegramUser {
        val fields = fields(data)
        val hash = fields["hash"] ?: throw InvalidLaunch("Launch data carries no hash")
        if (!MessageDigest.isEqual(signature(fields - "hash"), hex(hash))) {
            throw InvalidLaunch("Launch data signature does not match the bot token")
        }
        fresh(fields["auth_date"])
        return signed(fields["user"])
    }

    private fun fields(data: String): Map<String, String> {
        val pairs = data.split("&").map { pair(it) }
        if (pairs.distinctBy { it.first }.size != pairs.size) {
            throw InvalidLaunch("Launch data repeats a key")
        }
        return pairs.toMap()
    }

    private fun pair(text: String): Pair<String, String> {
        val parts = text.split("=", limit = 2)
        if (parts.size != 2) {
            throw InvalidLaunch("Launch data holds a field without a value")
        }
        return decode(parts[0]) to decode(parts[1])
    }

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, UTF_8)
    } catch (exception: IllegalArgumentException) {
        throw InvalidLaunch("Launch data holds a malformed escape", exception)
    }

    private fun hex(hash: String): ByteArray = try {
        HexFormat.of().parseHex(hash)
    } catch (exception: IllegalArgumentException) {
        throw InvalidLaunch("Launch data hash is not hexadecimal", exception)
    }

    private fun signature(fields: Map<String, String>): ByteArray =
        Hmac(Hmac("WebAppData".toByteArray()).sign(token.toByteArray()))
            .sign(fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }.toByteArray())

    private fun fresh(date: String?) {
        val issued =
            date?.toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }
                ?: throw InvalidLaunch("Launch data carries no valid auth_date")
        val age = Duration.between(issued, clock.instant())
        if (age > lifetime || age < skew.negated()) {
            throw InvalidLaunch("Launch data auth_date is outside the accepted window")
        }
    }

    private fun signed(json: String?): TelegramUser {
        val node =
            json?.let { tree(it) }?.takeIf { it.get("id")?.let(::identified) == true }
                ?: throw InvalidLaunch("Launch data carries no user with a valid Telegram id")
        return TelegramUser(node).takeIf { it.name().isNotEmpty() }
            ?: throw InvalidLaunch("Launch data user has no name")
    }

    private fun tree(json: String): JsonNode = try {
        mapper.readTree(json)
    } catch (exception: JacksonException) {
        throw InvalidLaunch("Launch data user is not valid JSON", exception)
    }

    private fun identified(id: JsonNode): Boolean = id.isIntegralNumber && id.canConvertToLong() && id.longValue() > 0

    private companion object {
        val lifetime: Duration = Duration.ofMinutes(5)
        val skew: Duration = Duration.ofSeconds(30)
    }
}
