package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.bot.BotSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, NotificationDeliveryService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationDeliveryRaceTest(
    @Autowired private val delivery: NotificationDeliveryService,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @TestBean
    private lateinit var clock: Clock

    @MockitoBean
    private lateinit var sender: BotSender

    @Test
    fun `parallel delivery skips a notification already being sent`() {
        val rows = Rows(jdbc)
        val id = rows.notification(rows.user(), rows.event(), "joined")
        jdbc.update("UPDATE notifications SET due_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now()), id)
        val sending = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            sending.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            null
        }.`when`(sender).message(anyLong(), anyString())
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit { delivery.deliver() }
            check(sending.await(5, TimeUnit.SECONDS))
            val second = workers.submit { delivery.deliver() }
            second.get(5, TimeUnit.SECONDS)
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
            verify(sender).message(anyLong(), anyString())
            assertThat(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM notifications WHERE id = ?", Boolean::class.java, id))
                .withFailMessage("Concurrent delivery left the notification pending")
                .isTrue()
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    companion object {
        @JvmStatic
        fun clock(): Clock = Clock.systemUTC()
    }
}
