package itmo.goto.domain

/**
 * Поле запроса, значение которого нарушает правило встречи; о нём сообщают ошибкой валидации с именем поля.
 */
class Violation(private val field: String, private val text: String) {
    /**
     * Имя поля так, как его пишет контракт, например `minUsers` или `registrationClosesAt`.
     */
    fun field(): String = field

    /**
     * Текст, объясняющий, что не так со значением.
     */
    fun message(): String = text
}
