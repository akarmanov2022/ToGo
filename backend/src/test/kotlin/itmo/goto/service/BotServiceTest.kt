package itmo.goto.service

import itmo.goto.ClockConfiguration
import itmo.goto.auth.SecurityConfiguration
import itmo.goto.auth.TokenConfiguration
import itmo.goto.bot.BotSender
import itmo.goto.generated.api.BotApiController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import kotlin.random.Random

@WebMvcTest(
    controllers = [BotApiController::class],
    properties = [
        "telegram.bot-token=7000000004:AAbot-service-test-token",
        "telegram.webhook-secret=bot-service-test-secret",
    ],
)
@Import(BotService::class, SecurityConfiguration::class, TokenConfiguration::class, ClockConfiguration::class)
class BotServiceTest(@Autowired private val mvc: MockMvcTester) {
    @MockitoBean
    private lateinit var sender: BotSender

    private fun update(secret: String, body: String) = mvc.post().uri("/api/v1/bot/webhook")
        .header("X-Telegram-Bot-Api-Secret-Token", secret)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    @Test
    fun `start in a private chat sends welcome`() {
        val chatId = Random.nextLong(1, 10_000_000_000)
        assertThat(
            update(
                "bot-service-test-secret",
                """{"message":{"text":"/start","chat":{"id":$chatId,"type":"private"}}}""",
            ),
        )
            .withFailMessage("Private /start was not accepted")
            .hasStatus(HttpStatus.NO_CONTENT)
        verify(sender).welcome(chatId)
    }

    @Test
    fun `wrong secret refuses the update`() {
        val chatId = Random.nextLong(1, 10_000_000_000)
        assertThat(update("wrong-secret", """{"message":{"text":"/start","chat":{"id":$chatId,"type":"private"}}}"""))
            .withFailMessage("Update with a wrong secret was accepted")
            .hasStatus(HttpStatus.FORBIDDEN)
        verifyNoInteractions(sender)
    }

    @Test
    fun `non start messages do not send welcome`() {
        val chatId = Random.nextLong(1, 10_000_000_000)
        assertThat(
            update(
                "bot-service-test-secret",
                """{"message":{"text":"привет","chat":{"id":$chatId,"type":"private"}}}""",
            ),
        )
            .withFailMessage("Non-start message was not acknowledged")
            .hasStatus(HttpStatus.NO_CONTENT)
        verifyNoInteractions(sender)
    }

    @Test
    fun `group start does not send welcome`() {
        val chatId = Random.nextLong(1, 10_000_000_000)
        assertThat(
            update("bot-service-test-secret", """{"message":{"text":"/start","chat":{"id":$chatId,"type":"group"}}}"""),
        )
            .withFailMessage("Group /start was not acknowledged")
            .hasStatus(HttpStatus.NO_CONTENT)
        verifyNoInteractions(sender)
    }
}
