package itmo.goto

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Server time source, injected wherever rules depend on the current moment so tests can freeze it.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
