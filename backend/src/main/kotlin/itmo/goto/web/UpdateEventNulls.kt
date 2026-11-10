package itmo.goto.web

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.UpdateEventRequest
import org.springframework.boot.jackson.JacksonMixin
import java.time.OffsetDateTime

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
