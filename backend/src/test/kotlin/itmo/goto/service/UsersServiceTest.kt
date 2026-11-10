package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.UpdateProfileRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.web.server.ResponseStatusException
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
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        val category = EventCategory.entries.random()
        rows.interest(holder, category.name)
        assertThat(service.getCurrentUser().categories)
            .withFailMessage("Current user doesnt list the chosen categories")
            .containsExactly(category)
    }

    @Test
    fun `current user lists categories in declaration order`() {
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        EventCategory.entries.shuffled().forEach { rows.interest(holder, it.name) }
        assertThat(service.getCurrentUser().categories)
            .withFailMessage("Current user doesnt list categories in declaration order")
            .containsExactlyElementsOf(EventCategory.entries)
    }

    @Test
    fun `current user counts organized events in any state`() {
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        val count = Random.nextInt(1, 5)
        repeat(count) {
            rows.event(
                holder,
                listOf("recruiting", "cancelled_by_organizer", "registration_closed", "finished").random(),
            )
        }
        assertThat(service.getCurrentUser().createdEventsCount)
            .withFailMessage("Current user doesnt count organized events in any state")
            .isEqualTo(count)
    }

    @Test
    fun `current user counts only finished events they visited`() {
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        rows.participant(rows.event(rows.user(), "finished"), holder)
        rows.participant(rows.event(rows.user(), "registration_closed"), holder)
        rows.event(rows.user(), "finished")
        assertThat(service.getCurrentUser().visitedEventsCount)
            .withFailMessage("Current user doesnt count only finished events they visited")
            .isEqualTo(1)
    }

    @Test
    fun `unknown token holder answers unauthorized`() {
        assertThatThrownBy { service.getCurrentUser() }
            .extracting("code")
            .withFailMessage("Unknown token holder doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    @Test
    fun `profile update answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                service.updateCurrentUser(
                    UpdateProfileRequest("Имя ${Random.nextInt()}", setOf(EventCategory.entries.random())),
                )
            }?.statusCode,
        )
            .withFailMessage("Profile update doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `other profile answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                service.getUser(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Other profile doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    companion object {
        private val holder = UUID.randomUUID()

        @JvmStatic
        fun identity(): Identity = Identity { holder }
    }
}
