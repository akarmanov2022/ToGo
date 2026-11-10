package itmo.goto.domain

interface Decision {
    fun <R> fold(accepted: (Meeting) -> R, refused: (Meeting, Refusal) -> R): R
}
