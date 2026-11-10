package itmo.goto.service

import itmo.goto.bot.BotBlocked
import itmo.goto.bot.BotSender
import itmo.goto.bot.BotUnavailable
import itmo.goto.domain.State
import itmo.goto.model.Notification
import itmo.goto.repository.NotificationRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Delivers due messages outside user operations and bounds Telegram retries. */
@Service
class NotificationDeliveryService(
    private val notifications: NotificationRepository,
    private val sender: BotSender,
    private val clock: Clock,
) {
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    @Transactional
    fun deliver() {
        val now = clock.instant()
        notifications.due(now).forEach { notice ->
            val state = notice.event.meeting().state()
            if (notice.kind.startsWith("reminder_") && state in cancelled) {
                notice.sentAt = now
            } else {
                try {
                    sender.message(notice.user.tgId, text(notice))
                    notice.sentAt = now
                } catch (_: BotBlocked) {
                    notice.sentAt = now
                } catch (_: BotUnavailable) {
                    notice.attempts++
                    notice.dueAt = now.plus(Duration.ofMinutes(1))
                }
            }
        }
    }

    private fun text(notice: Notification): String {
        val event = notice.event
        val time = timestamp.format(event.startsAt)
        return when (notice.kind) {
            "joined" -> "Вы записаны на «${event.title}». Начало: $time UTC."
            "confirmed" -> "Встреча «${event.title}» состоится. Начало: $time UTC."
            "cancelled" -> if (event.meeting().state() == State.CANCELLED_BY_SYSTEM) {
                "Встреча «${event.title}» отменена: не набран минимум участников."
            } else {
                "Встреча «${event.title}» отменена организатором."
            }
            "reminder_day" -> "Напоминание: встреча «${event.title}» начнётся через сутки, $time UTC."
            "reminder_hour" -> "Напоминание: встреча «${event.title}» начнётся через час, $time UTC."
            else -> error("Неизвестный вид уведомления")
        }
    }

    companion object {
        private val cancelled = setOf(State.CANCELLED_BY_ORGANIZER, State.CANCELLED_BY_SYSTEM)
        private val timestamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneOffset.UTC)
    }
}
