package itmo.goto.bot

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
import kotlin.random.Random

class TelegramBotSenderTest {
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
}
