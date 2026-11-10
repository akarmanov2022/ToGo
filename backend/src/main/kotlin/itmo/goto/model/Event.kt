package itmo.goto.model

import itmo.goto.domain.Limits
import itmo.goto.domain.Meeting
import itmo.goto.domain.Role
import itmo.goto.domain.Schedule
import itmo.goto.domain.State
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.UserSummary
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import itmo.goto.generated.model.Event as Card

/**
 * Stored event whose rules live in the domain [Meeting] built from it.
 */
@Entity
@Table(name = "events")
class Event(
    var title: String,
    var description: String,
    @Column(updatable = false)
    val location: String,
    var icon: String?,
    @Column(updatable = false)
    val startsAt: Instant,
    @Column(updatable = false)
    val endsAt: Instant,
    var registrationClosesAt: Instant,
    var minUsers: Int,
    var maxUsers: Int,
    @Enumerated(EnumType.STRING)
    var category: EventCategory,
    @Column(updatable = false)
    val latitude: Double?,
    @Column(updatable = false)
    val longitude: Double?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", updatable = false)
    val organizer: User,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    private var state: String = State.RECRUITING.name.lowercase()

    private var participantsCount: Int = 1

    fun meeting(): Meeting = Meeting(
        Schedule(registrationClosesAt, startsAt, endsAt),
        Limits(minUsers, maxUsers),
        participantsCount,
        State.valueOf(state.uppercase()),
    )

    fun settle(meeting: Meeting) {
        state = meeting.state().name.lowercase()
        participantsCount = meeting.participants()
    }

    fun role(user: UUID, participant: Boolean): Role = when {
        organizer.id == user -> Role.ORGANIZER
        participant -> Role.PARTICIPANT
        else -> Role.OUTSIDER
    }

    fun card(viewer: UUID, participant: Boolean): Card = Card(
        checkNotNull(id) { "Event has no id before it is saved" },
        title,
        description,
        location,
        startsAt.atOffset(ZoneOffset.UTC),
        endsAt.atOffset(ZoneOffset.UTC),
        registrationClosesAt.atOffset(ZoneOffset.UTC),
        EventState.forValue(state),
        icon,
        latitude,
        longitude,
        minUsers,
        maxUsers,
        UserSummary(checkNotNull(organizer.id) { "Organizer has no id before it is saved" }, organizer.tgLogin),
        category,
        participantsCount,
        meeting().free(),
        participant || organizer.id == viewer,
        organizer.id == viewer,
    )
}
