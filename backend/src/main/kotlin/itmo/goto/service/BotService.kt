package itmo.goto.service

import itmo.goto.bot.BotBlocked
import itmo.goto.bot.BotSender
import itmo.goto.bot.BotUnavailable
import itmo.goto.generated.api.BotApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.UUID

/**
 * Accepts authenticated Telegram updates: answers /start in private chats and saves ratings from the star buttons.
 */
@Service
class BotService(
    private val sender: BotSender,
    private val ratings: RatingsService,
    private val users: UserRepository,
    @Value("\${telegram.webhook-secret}") private val secret: String,
) : BotApiService {
    init {
        require(secret.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "Некорректный секрет webhook" }
    }

    override fun receiveBotUpdate(xTelegramBotApiSecretToken: String, requestBody: Map<String, Any>) {
        if (!MessageDigest.isEqual(secret.toByteArray(), xTelegramBotApiSecretToken.toByteArray())) {
            throw ApiFailure(ApiError.Code.FORBIDDEN, "Недопустимый запрос Telegram")
        }
        val callback = requestBody["callback_query"] as? Map<*, *>
        if (callback != null) {
            press(callback)
            return
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

    private fun press(callback: Map<*, *>) {
        val id = callback["id"] as? String
        val button = (callback["data"] as? String)?.let(star::matchEntire)
        val tg = ((callback["from"] as? Map<*, *>)?.get("id") as? Number)?.toLong()
        if (id == null || button == null || tg == null) return
        val message = callback["message"] as? Map<*, *>
        val chat = ((message?.get("chat") as? Map<*, *>)?.get("id") as? Number)?.toLong() ?: tg
        val failure = try {
            val user = users.findByTgId(tg)
                ?: throw ApiFailure(ApiError.Code.USER_NOT_FOUND, "Откройте ToGo, чтобы войти, и нажмите оценку снова")
            ratings.rate(UUID.fromString(button.groupValues[1]), checkNotNull(user.id), button.groupValues[2].toInt())
            null
        } catch (refusal: ApiFailure) {
            refusal.error()
        }
        quietly { sender.answer(id, failure?.message ?: "Оценка принята") }
        if (failure == null) {
            quietly { sender.message(chat, "Спасибо за вашу оценку! Вы помогаете нам становиться лучше") }
        }
        val messageId = (message?.get("message_id") as? Number)?.toLong()
        if (messageId != null && (failure == null || failure.code == ApiError.Code.ALREADY_RATED)) {
            quietly { sender.close(chat, messageId) }
        }
    }

    private fun quietly(call: () -> Unit) {
        try {
            call()
        } catch (_: BotBlocked) {
            // The user blocked the bot after pressing the button, so nobody is left to answer.
        } catch (_: BotUnavailable) {
            // Telegram would resend the whole update on a failure, which must not rate twice.
        }
    }

    companion object {
        private val star = Regex("rate:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}):([1-5])")
    }
}
