package itmo.goto.auth

import tools.jackson.databind.JsonNode

/**
 * Telegram user whose identity signed Mini App launch data proves.
 */
class TelegramUser(private val json: JsonNode) {
    fun id(): Long = json.get("id").longValue()

    fun name(): String = listOfNotNull(text("first_name"), text("last_name")).joinToString(" ").take(100).trim()

    fun login(): String? = text("username")

    fun photo(): String? = text("photo_url")

    private fun text(field: String): String? = json.get(field)?.takeIf { it.isString }?.stringValue()?.trim()?.takeIf { it.isNotEmpty() }
}
