package itmo.goto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

@SpringJUnitConfig(ClockConfiguration::class)
class ClockConfigurationTest {
    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `fixed clock replaces server clock`(context: ApplicationContext) {
        assertThat(context.getBean<Clock>())
            .withFailMessage("Context does not use the fixed clock supplied by the test")
            .isSameAs(clock)
    }

    companion object {
        @JvmStatic
        fun clock(): Clock = Clock.fixed(Instant.ofEpochSecond(Random.nextLong(0, 4_000_000_000)), ZoneOffset.UTC)
    }
}
