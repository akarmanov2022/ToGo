package itmo.goto

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * PostgreSQL in a container that every test context importing it shares, so tests run against the production database engine.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
}
