package itmo.goto.domain

class Violation(private val field: String, private val text: String) {
    fun field(): String = field

    fun message(): String = text
}
