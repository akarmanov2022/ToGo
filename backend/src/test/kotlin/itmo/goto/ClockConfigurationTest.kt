package itmo.goto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

@SpringJUnitConfig(ClockConfiguration::class)
class ClockConfigurationTest(@Autowired private val clock: Clock) {
    @TestBean
    private lateinit var fixed: Clock

    @Test
    fun `server clock can be replaced with fixed clock`() {
        assertThat(clock.instant())
            .withFailMessage("Server clock cant be replaced with a fixed clock")
            .isEqualTo(moment)
    }

    @Test
    fun `server clock runs in utc`() {
        assertThat(ClockConfiguration().clock().zone)
            .withFailMessage("Server clock doesnt run in UTC")
            .isEqualTo(ZoneOffset.UTC)
    }

    companion object {
        private val moment = Instant.ofEpochSecond(Random.nextLong(0, 4_000_000_000))

        @JvmStatic
        fun fixed(): Clock = Clock.fixed(moment, ZoneOffset.UTC)
    }
}
