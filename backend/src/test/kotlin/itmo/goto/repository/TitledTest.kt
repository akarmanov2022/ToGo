package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.random.Random

@DataJpaTest
@Import(PostgresConfiguration::class)
class TitledTest(@Autowired private val events: EventRepository, @Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `title is found regardless of case and surrounding spaces`() {
        val word = "настолок${Random.nextInt(1000)}"
        val event = titled("Вечер ${word.uppercase()} у Анны")
        assertThat(events.findAll(Titled("  $word ")).map { it.id })
            .withFailMessage("Title isnt found regardless of case and surrounding spaces")
            .contains(event)
    }

    @Test
    fun `title without the text isnt found`() {
        val event = titled("Пробежка ${Random.nextInt(1000)}")
        assertThat(events.findAll(Titled("настолки")).map { it.id })
            .withFailMessage("Title without the text is found")
            .doesNotContain(event)
    }

    @Test
    fun `percent sign matches only itself`() {
        val number = Random.nextInt(10, 100)
        val event = titled("Скидка ${number}0 рублей")
        assertThat(events.findAll(Titled("$number%")).map { it.id })
            .withFailMessage("Percent sign matches more than itself")
            .doesNotContain(event)
    }

    @Test
    fun `underscore matches only itself`() {
        val event = titled("Квест ${Random.nextInt(1000)}")
        assertThat(events.findAll(Titled("Квест_")).map { it.id })
            .withFailMessage("Underscore matches more than itself")
            .doesNotContain(event)
    }

    private fun titled(title: String): UUID = Rows(jdbc).event().also {
        jdbc.update("UPDATE events SET title = ? WHERE id = ?", title, it)
    }
}
