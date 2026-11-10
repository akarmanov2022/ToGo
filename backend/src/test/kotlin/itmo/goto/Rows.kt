package itmo.goto

import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * Inserts raw rows into the test database so constraint tests can probe the schema
 * without going through entities or services.
 */
class Rows(private val jdbc: JdbcTemplate) {
    fun user(): UUID =
        jdbc.queryForObject(
            "INSERT INTO users (tg_id, name) VALUES (?, ?) RETURNING id",
            UUID::class.java,
            Random.nextLong(1, Long.MAX_VALUE),
            "Пользователь ${Random.nextInt()}",
        )!!

    fun user(id: UUID, tg: Long, login: String?): UUID =
        jdbc.queryForObject(
            "INSERT INTO users (id, tg_id, tg_login, name) VALUES (?, ?, ?, ?) RETURNING id",
            UUID::class.java,
            id,
            tg,
            login,
            "Пользователь ${Random.nextInt()}",
        )!!

    fun event(
        closes: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Random.nextLong(10, 600)),
        starts: OffsetDateTime = closes.plusMinutes(Random.nextLong(0, 600)),
        ends: OffsetDateTime = starts.plusMinutes(Random.nextLong(1, 600)),
        maximum: Int = Random.nextInt(2, 51),
        latitude: Double? = null,
        longitude: Double? = null,
    ): UUID =
        jdbc.queryForObject(
            """
            INSERT INTO events (starts_at, ends_at, registration_closes_at, title, description, location, state,
                                min_users, max_users, participants_count, organizer_id, latitude, longitude)
            VALUES (?, ?, ?, ?, '', ?, 'recruiting', 1, ?, 1, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            UUID::class.java,
            starts,
            ends,
            closes,
            "Событие ${Random.nextInt()}",
            "Место ${Random.nextInt()}",
            maximum,
            user(),
            latitude,
            longitude,
        )!!
}
