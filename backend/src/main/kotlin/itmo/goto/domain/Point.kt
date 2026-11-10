package itmo.goto.domain

/**
 * Необязательная точка встречи на карте: обе координаты либо заданы вместе, либо отсутствуют.
 */
class Point(private val latitude: Double?, private val longitude: Double?) {
    /**
     * Называет недостающую координату, когда задана только одна; пустой список, если точка корректна.
     */
    fun violations(): List<Violation> = listOfNotNull(
        Violation("longitude", "Долгота передаётся вместе с широтой").takeIf { latitude != null && longitude == null },
        Violation("latitude", "Широта передаётся вместе с долготой").takeIf { latitude == null && longitude != null },
    )
}
