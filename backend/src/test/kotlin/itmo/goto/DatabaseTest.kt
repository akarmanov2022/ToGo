package itmo.goto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import javax.sql.DataSource

@DataJpaTest
@Import(PostgresConfiguration::class)
class DatabaseTest(@Autowired private val source: DataSource) {
    @Test
    fun `repository tests run against postgresql`() {
        assertThat(source.connection.use { it.metaData.databaseProductName })
            .withFailMessage("Repository tests dont run against PostgreSQL")
            .isEqualTo("PostgreSQL")
    }
}
