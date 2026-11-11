package itmo.goto.bot

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class TelegramBotSenderTest {
    @Test
    fun `configured local bot api receives the notification`() {
        val body = AtomicReference<String>()
        withLocalBotApi(200, body) { sender -> sender.message(123, "Вы записаны на встречу") }
        assertThat(body.get())
            .withFailMessage("The configured Bot API did not receive the notification")
            .contains("\"chat_id\":123", "Вы записаны на встречу")
    }

    @Test
    fun `temporary bot api failure stays retryable`() {
        val failure = catchThrowableOfType(BotUnavailable::class.java) {
            withLocalBotApi(503) { sender -> sender.message(123, "Встреча состоится") }
        }
        assertThat(failure)
            .withFailMessage("A temporary Bot API failure did not remain retryable")
            .isNotNull()
    }

    @Test
    fun `forbidden bot api response stops retries`() {
        val failure = catchThrowableOfType(BotBlocked::class.java) {
            withLocalBotApi(403) { sender -> sender.message(123, "Встреча отменена") }
        }
        assertThat(failure)
            .withFailMessage("A blocked bot was treated as a temporary failure")
            .isNotNull()
    }

    @Test
    fun `comment sends a button to the comments section without comment text`() {
        val event = UUID.randomUUID()
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.test/bot7000000005:AAbot-sender-test-token/sendMessage"))
            .andExpect(
                content().json(
                    """
                    {
                      "text": "Новый комментарий к событию «Настолки».",
                      "reply_markup": {
                        "inline_keyboard": [[{
                          "text": "Открыть комментарии",
                          "web_app": {"url": "https://dev-togo.ru/events/$event#comments"}
                        }]]
                      }
                    }
                    """.trimIndent(),
                    false,
                ),
            )
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        TelegramBotSender(
            builder,
            "7000000005:AAbot-sender-test-token",
            "https://dev-togo.ru/",
            "https://api.telegram.test",
        ).comment(123, "Новый комментарий к событию «Настолки».", event)
        server.verify()
    }

    @Test
    fun `welcome sends a web app button to the private chat`() {
        val chatId = Random.nextLong(1, 10_000_000_000)
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.test/bot7000000005:AAbot-sender-test-token/sendMessage"))
            .andExpect(
                content().json(
                    """
                    {
                      "chat_id": $chatId,
                  "text": "Привет! ToGo помогает находить события и компанию рядом. Открой приложение, чтобы начать.",
                      "reply_markup": {
                        "inline_keyboard": [[{"text": "Открыть ToGo", "web_app": {"url": "https://dev-togo.ru/"}}]]
                      }
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        TelegramBotSender(
            builder,
            "7000000005:AAbot-sender-test-token",
            "https://dev-togo.ru/",
            "https://api.telegram.test",
        ).welcome(chatId)
        server.verify()
    }

    @Test
    fun `bot api failure does not expose the bot token`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.test/bot7000000005:AAbot-sender-test-token/sendMessage"))
            .andRespond(withServerError())
        val failure = catchThrowableOfType(IllegalStateException::class.java) {
            TelegramBotSender(
                builder,
                "7000000005:AAbot-sender-test-token",
                "https://dev-togo.ru/",
                "https://api.telegram.test",
            ).welcome(Random.nextLong(1, 10_000_000_000))
        }
        assertThat(failure?.toString())
            .withFailMessage("Bot API failure exposed the bot token")
            .doesNotContain("7000000005:AAbot-sender-test-token")
    }

    private fun withLocalBotApi(status: Int, body: AtomicReference<String>? = null, send: (BotSender) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bot7000000005:AAbot-sender-test-token/sendMessage") { exchange ->
            val request = exchange.requestBody.bufferedReader().use { it.readText() }
            body?.set(request)
            val response = if (status == 200) "{\"ok\":true}" else "{\"ok\":false}"
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            send(
                TelegramBotSender(
                    RestClient.builder(),
                    "7000000005:AAbot-sender-test-token",
                    "https://dev-togo.ru/",
                    "http://127.0.0.1:${server.address.port}",
                ),
            )
        } finally {
            server.stop(0)
        }
    }
}
