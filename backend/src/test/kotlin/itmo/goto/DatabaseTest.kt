package itmo.goto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresConfiguration::class)
class DatabaseTest(@Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `context connects to postgresql`() {
        assertThat(jdbc.queryForObject<String>("select version()"))
            .withFailMessage("Test context does not run against PostgreSQL 18")
            .startsWith("PostgreSQL 18")
    }
}
