package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.generated.model.EventCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@DataJpaTest
@Import(PostgresConfiguration::class)
class CategorizedTest(@Autowired private val events: EventRepository, @Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `event of the category is found`() {
        val category = EventCategory.entries.random()
        val event = categorized(category)
        assertThat(events.findAll(Categorized(category)).map { it.id })
            .withFailMessage("Event of the category isnt found")
            .contains(event)
    }

    @Test
    fun `event of another category isnt found`() {
        val category = EventCategory.entries.random()
        val event = categorized((EventCategory.entries - category).random())
        assertThat(events.findAll(Categorized(category)).map { it.id })
            .withFailMessage("Event of another category is found")
            .doesNotContain(event)
    }

    private fun categorized(category: EventCategory): UUID = Rows(jdbc).event().also {
        jdbc.update("UPDATE events SET category = ? WHERE id = ?", category.name, it)
    }
}
