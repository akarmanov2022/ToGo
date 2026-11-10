package itmo.goto.domain

class Accepted(private val meeting: Meeting) : Decision {
    override fun <R> fold(accepted: (Meeting) -> R, refused: (Meeting, Refusal) -> R): R = accepted(meeting)
}
