package itmo.goto.service

import itmo.goto.bot.BotSender
import itmo.goto.generated.api.BotApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.web.ApiFailure
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest

/** Accepts authenticated Telegram updates and handles /start in private chats. */
@Service
class BotService(private val sender: BotSender, @Value("\${telegram.webhook-secret}") private val secret: String) :
    BotApiService {
    init {
        require(secret.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "Некорректный секрет webhook" }
    }

    override fun receiveBotUpdate(xTelegramBotApiSecretToken: String, requestBody: Map<String, Any>) {
        if (!MessageDigest.isEqual(secret.toByteArray(), xTelegramBotApiSecretToken.toByteArray())) {
            throw ApiFailure(ApiError.Code.FORBIDDEN, "Недопустимый запрос Telegram")
        }
        val message = requestBody["message"] as? Map<*, *>
        val chat = message?.get("chat") as? Map<*, *>
        val text = message?.get("text") as? String
        val chatId = chat?.get("id") as? Number
        val isStart = text == "/start" || text?.startsWith("/start ") == true
        if (chat?.get("type") == "private" && isStart && chatId != null) {
            sender.welcome(chatId.toLong())
        }
    }
}
