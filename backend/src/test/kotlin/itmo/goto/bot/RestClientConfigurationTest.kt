package itmo.goto.bot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class RestClientConfigurationTest {
    @Test
    fun `boot provides the builder required by the bot sender`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration::class.java))
            .withUserConfiguration(TelegramBotSender::class.java)
            .withPropertyValues("telegram.bot-token=7000000006:AAbot-context-test-token")
            .run { context ->
                assertThat(context)
                    .withFailMessage("The bot sender cannot start without RestClient.Builder")
                    .hasSingleBean(BotSender::class.java)
            }
    }
}
