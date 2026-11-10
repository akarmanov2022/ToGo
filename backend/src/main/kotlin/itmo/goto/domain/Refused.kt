package itmo.goto.domain

class Refused(private val meeting: Meeting, private val refusal: Refusal) : Decision {
    override fun <R> fold(accepted: (Meeting) -> R, refused: (Meeting, Refusal) -> R): R = refused(meeting, refusal)
}
