package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.convention.TestBean
import java.util.UUID
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, UsersService::class)
class UsersServiceTest(@Autowired private val service: UsersService, @Autowired private val jdbc: JdbcTemplate) {
    @TestBean
    private lateinit var identity: Identity

    @Test
    fun `current user is the token holder`() {
        val tg = Random.nextLong(1, 10_000_000_000)
        Rows(jdbc).user(holder, tg, "user${Random.nextInt()}")
        assertThat(service.getCurrentUser().tgId)
            .withFailMessage("Current user isnt the token holder")
            .isEqualTo(tg)
    }

    @Test
    fun `current user without username has no login`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        assertThat(service.getCurrentUser().tgLogin)
            .withFailMessage("Current user without a username has a login")
            .isNull()
    }

    @Test
    fun `current user lists chosen categories`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val category = jdbc.queryForList("SELECT name FROM categories", String::class.java).random()
        jdbc.update("INSERT INTO user_categories (user_id, category_id) SELECT ?, id FROM categories WHERE name = ?", holder, category)
        assertThat(service.getCurrentUser().categories.map { it.name })
            .withFailMessage("Current user doesnt list the chosen categories")
            .containsExactly(category)
    }

    @Test
    fun `unknown token holder answers unauthorized`() {
        assertThatThrownBy { service.getCurrentUser() }
            .extracting("code")
            .withFailMessage("Unknown token holder doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    companion object {
        private val holder = UUID.randomUUID()

        @JvmStatic
        fun identity(): Identity = Identity { holder }
    }
}
