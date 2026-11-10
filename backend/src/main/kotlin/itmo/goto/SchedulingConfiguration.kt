package itmo.goto

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Turns on scheduled tasks such as the processing of event deadlines.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration
