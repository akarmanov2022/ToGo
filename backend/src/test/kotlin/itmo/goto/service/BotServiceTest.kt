package itmo.goto.service

import itmo.goto.ClockConfiguration
import itmo.goto.auth.SecurityConfiguration
import itmo.goto.auth.TokenConfiguration
import itmo.goto.bot.BotSender
import itmo.goto.bot.BotUnavailable
import itmo.goto.generated.api.BotApiController
import itmo.goto.generated.model.ApiError
import itmo.goto.model.User
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.util.UUID
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

    @MockitoBean
    private lateinit var ratings: RatingsService

    @MockitoBean
    private lateinit var users: UserRepository

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

    @Test
    fun `pressed star rates the event as the telegram user`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        assertThat(update("bot-service-test-secret", press.json()))
            .withFailMessage("Pressed star was not acknowledged")
            .hasStatus(HttpStatus.NO_CONTENT)
        verify(ratings).rate(press.event, press.user.id!!, press.score)
    }

    @Test
    fun `accepted rating thanks the user`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        update("bot-service-test-secret", press.json()).exchange()
        verify(sender).message(press.chat, "Спасибо за вашу оценку! Вы помогаете нам становиться лучше")
    }

    @Test
    fun `accepted rating removes the buttons`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        update("bot-service-test-secret", press.json()).exchange()
        verify(sender).close(press.chat, press.message)
    }

    @Test
    fun `accepted rating answers the button`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        update("bot-service-test-secret", press.json()).exchange()
        verify(sender).answer(press.id, "Оценка принята")
    }

    @Test
    fun `repeated rating removes the buttons`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        willThrow(ApiFailure(ApiError.Code.ALREADY_RATED, "Вы уже оценили это событие"))
            .given(ratings).rate(press.event, press.user.id!!, press.score)
        update("bot-service-test-secret", press.json()).exchange()
        verify(sender).close(press.chat, press.message)
    }

    @Test
    fun `refused rating answers the button with the reason`() {
        val press = Press()
        val reason = "Оценить событие могут только его участники ${Random.nextInt()}"
        given(users.findByTgId(press.tg)).willReturn(press.user)
        willThrow(ApiFailure(ApiError.Code.FORBIDDEN, reason))
            .given(ratings).rate(press.event, press.user.id!!, press.score)
        update("bot-service-test-secret", press.json()).exchange()
        verify(sender).answer(press.id, reason)
    }

    @Test
    fun `refused rating keeps the buttons`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        willThrow(ApiFailure(ApiError.Code.INVALID_EVENT_STATE, "Действие недоступно в текущем состоянии события"))
            .given(ratings).rate(press.event, press.user.id!!, press.score)
        update("bot-service-test-secret", press.json()).exchange()
        verify(sender, never()).close(press.chat, press.message)
    }

    @Test
    fun `star of an unknown telegram user rates nothing`() {
        val press = Press()
        update("bot-service-test-secret", press.json()).exchange()
        verifyNoInteractions(ratings)
    }

    @Test
    fun `malformed button data rates nothing`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        update("bot-service-test-secret", press.json("rate:${press.event}:${Random.nextInt(6, 10)}")).exchange()
        verifyNoInteractions(ratings)
    }

    @Test
    fun `unavailable telegram still acknowledges the press`() {
        val press = Press()
        given(users.findByTgId(press.tg)).willReturn(press.user)
        willThrow(
            BotUnavailable(),
        ).given(sender).message(press.chat, "Спасибо за вашу оценку! Вы помогаете нам становиться лучше")
        assertThat(update("bot-service-test-secret", press.json()))
            .withFailMessage("Press was not acknowledged when Telegram is unavailable")
            .hasStatus(HttpStatus.NO_CONTENT)
    }

    /**
     * One press of a rating button by a random Telegram user in a random chat.
     */
    private class Press {
        val id = "callback${Random.nextInt(0, Int.MAX_VALUE)}"
        val tg = Random.nextLong(1, 10_000_000_000)
        val chat = Random.nextLong(1, 10_000_000_000)
        val message = Random.nextLong(1, 1_000_000)
        val event: UUID = UUID.randomUUID()
        val score = Random.nextInt(1, 6)
        val user = User(tg, null, "Пользователь ${Random.nextInt()}", null).apply { id = UUID.randomUUID() }

        fun json(data: String = "rate:$event:$score"): String =
            """{"callback_query":{"id":"$id","from":{"id":$tg},"data":"$data",""" +
                """"message":{"message_id":$message,"chat":{"id":$chat,"type":"private"}}}}"""
    }
}
