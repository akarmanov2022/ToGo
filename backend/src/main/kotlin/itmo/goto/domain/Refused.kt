package itmo.goto.domain

/**
 * Решение, которое отклоняет запрос.
 */
class Refused(private val meeting: Meeting, private val refusal: Refusal) : Decision {
    /**
     * Передаёт актуальную встречу и причину отказа в [refused] и никогда не вызывает [accepted].
     */
    override fun <R> fold(accepted: (Meeting) -> R, refused: (Meeting, Refusal) -> R): R = refused(meeting, refusal)
}
