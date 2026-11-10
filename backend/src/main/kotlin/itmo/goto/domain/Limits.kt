package itmo.goto.domain

class Limits(private val min: Int, private val max: Int) {
    fun violations(): List<Violation> = listOfNotNull(
        Violation("minUsers", "Минимум участников должен быть не меньше 1").takeIf { min < 1 },
        Violation("minUsers", "Минимум участников не может превышать максимум").takeIf { min > max },
        Violation("maxUsers", "Максимум участников не может превышать $CEILING").takeIf { max > CEILING },
    )

    fun reached(count: Int): Boolean = count >= min

    fun full(count: Int): Boolean = count >= max

    fun holds(count: Int): Boolean = count <= max

    fun free(count: Int): Int = max - count

    private companion object {
        const val CEILING = 50
    }
}
