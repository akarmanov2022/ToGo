package itmo.goto.web

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.UpdateEventRequest
import org.springframework.boot.jackson.JacksonMixin
import java.time.OffsetDateTime

/**
 * Rejects an explicit null in an event change, which the contract forbids but the generated request skips.
 *
 * Jackson lets mix-in annotations replace those of the generated fields, so each field here turns the generated
 * null skipping into a failure that the error handler reports as a validation error naming the field.
 */
@JacksonMixin(UpdateEventRequest::class)
abstract class UpdateEventNulls {
    @field:JsonSetter(nulls = Nulls.FAIL)
    private val title: String? = null

    @field:JsonSetter(nulls = Nulls.FAIL)
    private val description: String? = null

    @field:JsonSetter(nulls = Nulls.FAIL)
    private val registrationClosesAt: OffsetDateTime? = null

    @field:JsonSetter(nulls = Nulls.FAIL)
    private val icon: String? = null

    @field:JsonSetter(nulls = Nulls.FAIL)
    private val minUsers: Int? = null

    @field:JsonSetter(nulls = Nulls.FAIL)
    private val maxUsers: Int? = null

    @field:JsonSetter(nulls = Nulls.FAIL)
    private val category: EventCategory? = null
}
