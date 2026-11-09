package itmo.goto

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Supplies the server clock in UTC, so every deadline check reads time from one place
 * that tests can replace with fixed clocks.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
