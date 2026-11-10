package itmo.goto.bot

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** Sends the one welcome message supported by the bot. */
interface BotSender {
    fun welcome(chatId: Long)
}

@Component
class TelegramBotSender(
    builder: RestClient.Builder,
    @Value("\${telegram.bot-token}") token: String,
    @Value("\${telegram.app-url:https://dev-togo.ru/}") private val appUrl: String,
    @Value("\${telegram.api-base-url:https://api.telegram.org}") apiBaseUrl: String,
) : BotSender {
    private val client = builder.baseUrl("$apiBaseUrl/bot$token").build()

    override fun welcome(chatId: Long) {
        try {
            client.post().uri("/sendMessage").body(
                mapOf(
                    "chat_id" to chatId,
                    "text" to
                        "Привет! ToGo помогает находить события и компанию рядом. Открой приложение, чтобы начать.",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(mapOf("text" to "Открыть ToGo", "web_app" to mapOf("url" to appUrl))),
                        ),
                    ),
                ),
            ).retrieve().toBodilessEntity()
        } catch (_: RestClientException) {
            // Client exceptions can contain the request URL, which includes the bot token.
            error("Не удалось отправить приветствие через Telegram")
        }
    }
}
