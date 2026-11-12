package itmo.goto.bot

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

interface BotSender {
    fun welcome(chatId: Long)

    fun message(chatId: Long, text: String)

    fun comment(chatId: Long, text: String, eventId: UUID)

    fun rating(chatId: Long, text: String, eventId: UUID)

    fun answer(callbackId: String, text: String)

    fun close(chatId: Long, messageId: Long)
}

class BotBlocked : RuntimeException()

class BotUnavailable : RuntimeException()

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
            error("Не удалось отправить приветствие через Telegram")
        }
    }

    override fun message(chatId: Long, text: String) = call("/sendMessage", mapOf("chat_id" to chatId, "text" to text))

    override fun comment(chatId: Long, text: String, eventId: UUID) {
        val url = "${appUrl.trimEnd('/')}/events/$eventId#comments"
        call(
            "/sendMessage",
            mapOf(
                "chat_id" to chatId,
                "text" to text,
                "reply_markup" to mapOf(
                    "inline_keyboard" to listOf(
                        listOf(mapOf("text" to "Открыть комментарии", "web_app" to mapOf("url" to url))),
                    ),
                ),
            ),
        )
    }

    override fun rating(chatId: Long, text: String, eventId: UUID) = call(
        "/sendMessage",
        mapOf(
            "chat_id" to chatId,
            "text" to text,
            "reply_markup" to mapOf(
                "inline_keyboard" to listOf(
                    stars.map { mapOf("text" to "$it ⭐", "callback_data" to "rate:$eventId:$it") },
                ),
            ),
        ),
    )

    override fun answer(callbackId: String, text: String) =
        call("/answerCallbackQuery", mapOf("callback_query_id" to callbackId, "text" to text))

    override fun close(chatId: Long, messageId: Long) = call(
        "/editMessageReplyMarkup",
        mapOf(
            "chat_id" to chatId,
            "message_id" to messageId,
            "reply_markup" to mapOf("inline_keyboard" to emptyList<Any>()),
        ),
    )

    private fun call(path: String, body: Map<String, Any>) {
        try {
            client.post().uri(path).body(body).retrieve().toBodilessEntity()
        } catch (error: org.springframework.web.client.RestClientResponseException) {
            throw failure(error)
        } catch (_: RestClientException) {
            // Exception messages may contain the bot token in the request URL.
            throw BotUnavailable()
        }
    }

    private fun failure(error: org.springframework.web.client.RestClientResponseException): RuntimeException =
        if (error.statusCode == HttpStatus.FORBIDDEN) BotBlocked() else BotUnavailable()

    companion object {
        private val stars = 1..5
    }
}
