package itmo.goto.domain

/**
 * Решение, которое пропускает запрос.
 *
 * Встреча внутри — та, что нужно сохранить: изменённая запросом или прежняя, если запрос повторяет уже
 * сделанное, например повторное вступление.
 */
class Accepted(private val meeting: Meeting) : Decision {
    /**
     * Передаёт встречу для сохранения в [accepted] и никогда не вызывает [refused].
     */
    override fun <R> fold(accepted: (Meeting) -> R, refused: (Meeting, Refusal) -> R): R = accepted(meeting)
}
