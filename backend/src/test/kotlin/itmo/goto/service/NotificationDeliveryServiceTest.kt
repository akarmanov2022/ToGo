package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.bot.BotBlocked
import itmo.goto.bot.BotSender
import itmo.goto.bot.BotUnavailable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, NotificationDeliveryService::class)
class NotificationDeliveryServiceTest(
    @Autowired private val delivery: NotificationDeliveryService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var clock: Clock

    @MockitoBean
    private lateinit var sender: BotSender

    @Test
    fun `due notification is sent and closed`() {
        val id = due("joined")
        delivery.deliver()
        entities.flush()
        assertThat(sent(id))
            .withFailMessage("Delivered notification stays pending")
            .isNotNull()
        verify(sender).message(anyLong(), anyString())
    }

    @Test
    fun `temporary failure is retried later`() {
        val id = due("joined")
        doThrow(BotUnavailable()).`when`(sender).message(anyLong(), anyString())
        delivery.deliver()
        entities.flush()
        assertThat(jdbc.queryForObject<Int>("SELECT attempts FROM notifications WHERE id = ?", id))
            .withFailMessage("Temporary Telegram failure was not counted")
            .isEqualTo(1)
    }

    @Test
    fun `blocked bot closes notification without retry`() {
        val id = due("joined")
        doThrow(BotBlocked()).`when`(sender).message(anyLong(), anyString())
        delivery.deliver()
        entities.flush()
        assertThat(sent(id))
            .withFailMessage("403 did not close the notification")
            .isNotNull()
    }

    @Test
    fun `cancelled event does not send a reminder`() {
        val id = due("reminder_hour")
        jdbc.update("UPDATE events SET state = 'cancelled_by_organizer' WHERE id = (SELECT event_id FROM notifications WHERE id = ?)", id)
        delivery.deliver()
        entities.flush()
        assertThat(sent(id))
            .withFailMessage("Cancelled event reminder stayed pending")
            .isNotNull()
        verifyNoInteractions(sender)
    }

    @Test
    fun `expired recruiting event without minimum does not send a reminder`() {
        val id = due("reminder_hour")
        jdbc.update(
            "UPDATE events SET min_users = 2, registration_closes_at = ? " +
                "WHERE id = (SELECT event_id FROM notifications WHERE id = ?)",
            java.sql.Timestamp.from(now.minusSeconds(60)), id,
        )
        delivery.deliver()
        entities.flush()
        assertThat(sent(id))
            .withFailMessage("Reminder for an expired event stayed pending")
            .isNotNull()
        verifyNoInteractions(sender)
    }

    @Test
    fun `comment notification uses event button and excludes comment text`() {
        val id = due("comment")
        delivery.deliver()
        entities.flush()
        assertThat(sent(id))
            .withFailMessage("Comment notification was not sent")
            .isNotNull()
        verify(sender).comment(anyLong(), contains("Новый комментарий"), any(UUID::class.java))
        verify(sender, never()).message(anyLong(), anyString())
    }

    @Test
    fun `third failed attempt stops retries`() {
        val id = due("joined")
        jdbc.update("UPDATE notifications SET attempts = 2 WHERE id = ?", id)
        doThrow(BotUnavailable()).`when`(sender).message(anyLong(), anyString())
        delivery.deliver()
        entities.flush()
        assertThat(jdbc.queryForObject<Int>("SELECT attempts FROM notifications WHERE id = ?", id))
            .withFailMessage("Third Telegram failure did not exhaust retries")
            .isEqualTo(3)
    }

    private fun due(kind: String): UUID {
        val rows = Rows(jdbc)
        val user = rows.user()
        val event = rows.event()
        val storedKind = if (kind == "comment") "joined" else kind
        return rows.notification(user, event, storedKind).also {
            jdbc.update("UPDATE notifications SET due_at = ? WHERE id = ?", now.atOffset(ZoneOffset.UTC), it)
            if (kind == "comment") {
                val comment = rows.comment(event, user, now)
                jdbc.update("UPDATE notifications SET kind = 'comment', comment_id = ? WHERE id = ?", comment, it)
            }
        }
    }

    private fun sent(id: UUID): Instant? =
        jdbc.queryForObject("SELECT sent_at FROM notifications WHERE id = ?", java.sql.Timestamp::class.java, id)
            ?.toInstant()

    companion object {
        private val now = Instant.now()

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
