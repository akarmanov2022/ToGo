package itmo.goto.domain

/**
 * Границы числа участников встречи.
 */
class Limits(private val min: Int, private val max: Int) {
    /**
     * Перечисляет поля, нарушающие `1 <= min <= max <= 50`; пустой список, если лимиты корректны.
     */
    fun violations(): List<Violation> = listOfNotNull(
        Violation("minUsers", "Минимум участников должен быть не меньше 1").takeIf { min < 1 },
        Violation("minUsers", "Минимум участников не может превышать максимум").takeIf { min > max },
        Violation("maxUsers", "Максимум участников не может превышать $CEILING").takeIf { max > CEILING },
    )

    /**
     * Сообщает, набран ли минимум при [count] участниках.
     */
    fun reached(count: Int): Boolean = count >= min

    /**
     * Сообщает, что при [count] участниках не осталось места ещё для одного.
     */
    fun full(count: Int): Boolean = count >= max

    /**
     * Сообщает, вмещает ли максимум [count] участников; уменьшенный максимум обязан их вмещать.
     */
    fun holds(count: Int): Boolean = count <= max

    /**
     * Число свободных мест при [count] участниках, которое клиенты видят как доступные места.
     */
    fun free(count: Int): Int = max - count

    private companion object {
        const val CEILING = 50
    }
}
