package itmo.goto

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Supplies the PostgreSQL container that repository tests run against.
 *
 * Spring caches the context that imports this configuration, so one container
 * serves every test sharing that context within a run. Each run starts from an
 * empty database, so edited migrations never meet a stale schema.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
}
