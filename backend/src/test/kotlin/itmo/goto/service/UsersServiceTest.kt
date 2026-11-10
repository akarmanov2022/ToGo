package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.UpdateProfileRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.convention.TestBean
import java.util.UUID
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, UsersService::class)
class UsersServiceTest(
    @Autowired private val service: UsersService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
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
    fun `current user doesnt count events they left`() {
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        val participation = rows.participant(rows.event(rows.user(), "finished"), holder)
        jdbc.update("DELETE FROM event_users WHERE id = ?", participation)
        assertThat(service.getCurrentUser().visitedEventsCount)
            .withFailMessage("Current user counts an event they left as visited")
            .isZero()
    }

    @Test
    fun `current user counts finished events they organized as visited`() {
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        rows.participant(rows.event(holder, "finished"), holder)
        assertThat(service.getCurrentUser().visitedEventsCount)
            .withFailMessage("Current user doesnt count a finished event they organized as visited")
            .isEqualTo(1)
    }

    @Test
    fun `profile update saves name`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val name = "Аня ${Random.nextInt()}"
        service.updateCurrentUser(UpdateProfileRequest(name, emptySet()))
        entities.flush()
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String::class.java, holder))
            .withFailMessage("Profile update doesnt save the name")
            .isEqualTo(name)
    }

    @Test
    fun `profile update saves city`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val city = "Город ${Random.nextInt()}"
        service.updateCurrentUser(UpdateProfileRequest("Имя ${Random.nextInt()}", emptySet(), city))
        entities.flush()
        assertThat(jdbc.queryForObject("SELECT city FROM users WHERE id = ?", String::class.java, holder))
            .withFailMessage("Profile update doesnt save the city")
            .isEqualTo(city)
    }

    @Test
    fun `profile update without city clears it`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        jdbc.update("UPDATE users SET city = ? WHERE id = ?", "Город ${Random.nextInt()}", holder)
        service.updateCurrentUser(UpdateProfileRequest("Имя ${Random.nextInt()}", emptySet()))
        entities.flush()
        assertThat(jdbc.queryForObject("SELECT city FROM users WHERE id = ?", String::class.java, holder))
            .withFailMessage("Profile update without a city doesnt clear it")
            .isNull()
    }

    @Test
    fun `profile update saves exactly chosen categories`() {
        val rows = Rows(jdbc)
        rows.user(holder, Random.nextLong(1, 10_000_000_000), null)
        val categories = EventCategory.entries.shuffled()
        val split = Random.nextInt(1, categories.size)
        categories.drop(split).forEach { rows.interest(holder, it.name) }
        service.updateCurrentUser(UpdateProfileRequest("Имя ${Random.nextInt()}", categories.take(split).toSet()))
        entities.flush()
        assertThat(
            jdbc.queryForList("SELECT category FROM user_categories WHERE user_id = ?", String::class.java, holder),
        )
            .withFailMessage("Profile update doesnt save exactly the chosen categories")
            .containsExactlyInAnyOrderElementsOf(categories.take(split).map { it.name })
    }

    @Test
    fun `profile update returns updated profile`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val name = "Аня ${Random.nextInt()}"
        assertThat(service.updateCurrentUser(UpdateProfileRequest(name, emptySet())).name)
            .withFailMessage("Profile update doesnt return the updated profile")
            .isEqualTo(name)
    }

    @Test
    fun `profile update by unknown token holder answers unauthorized`() {
        assertThatThrownBy { service.updateCurrentUser(UpdateProfileRequest("Имя ${Random.nextInt()}", emptySet())) }
            .extracting("code")
            .withFailMessage("Profile update by an unknown token holder doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    @Test
    fun `other profile shows username`() {
        val login = "boris_${Random.nextInt(1000)}"
        val other = Rows(jdbc).user(UUID.randomUUID(), Random.nextLong(1, 10_000_000_000), login)
        assertThat(service.getUser(other).tgLogin)
            .withFailMessage("Other profile doesnt show the username")
            .isEqualTo(login)
    }

    @Test
    fun `other profile shows city`() {
        val other = Rows(jdbc).user(UUID.randomUUID(), Random.nextLong(1, 10_000_000_000), null)
        val city = "Город ${Random.nextInt()}"
        jdbc.update("UPDATE users SET city = ? WHERE id = ?", city, other)
        assertThat(service.getUser(other).city)
            .withFailMessage("Other profile doesnt show the city")
            .isEqualTo(city)
    }

    @Test
    fun `other profile lists categories`() {
        val rows = Rows(jdbc)
        val other = rows.user(UUID.randomUUID(), Random.nextLong(1, 10_000_000_000), null)
        val category = EventCategory.entries.random()
        rows.interest(other, category.name)
        assertThat(service.getUser(other).categories)
            .withFailMessage("Other profile doesnt list the categories")
            .containsExactly(category)
    }

    @Test
    fun `other profile counts organized events`() {
        val rows = Rows(jdbc)
        val other = rows.user(UUID.randomUUID(), Random.nextLong(1, 10_000_000_000), null)
        val count = Random.nextInt(1, 5)
        repeat(count) { rows.event(other, listOf("recruiting", "cancelled_by_system", "finished").random()) }
        assertThat(service.getUser(other).createdEventsCount)
            .withFailMessage("Other profile doesnt count organized events")
            .isEqualTo(count)
    }

    @Test
    fun `other profile counts visited events`() {
        val rows = Rows(jdbc)
        val other = rows.user(UUID.randomUUID(), Random.nextLong(1, 10_000_000_000), null)
        val count = Random.nextInt(1, 5)
        repeat(count) { rows.participant(rows.event(rows.user(), "finished"), other) }
        assertThat(service.getUser(other).visitedEventsCount)
            .withFailMessage("Other profile doesnt count visited events")
            .isEqualTo(count)
    }

    @Test
    fun `unknown profile answers user not found`() {
        assertThatThrownBy { service.getUser(UUID.randomUUID()) }
            .extracting("code")
            .withFailMessage("Unknown profile doesnt answer USER_NOT_FOUND")
            .isEqualTo(ApiError.Code.USER_NOT_FOUND)
    }

    companion object {
        private val holder = UUID.randomUUID()

        @JvmStatic
        fun identity(): Identity = Identity { holder }
    }
}
