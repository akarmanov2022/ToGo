package itmo.goto.domain


enum class Refusal(private val text: String) {
    FORBIDDEN("Действие доступно только организатору события"),
    INVALID_EVENT_STATE("Действие недоступно в текущем состоянии события"),
    REGISTRATION_CLOSED("Регистрация на событие закрыта"),
    EVENT_FULL("В событии не осталось свободных мест"),
    CAPACITY_BELOW_PARTICIPANTS("Максимум участников не может быть меньше их текущего числа"),
    ORGANIZER_CANNOT_LEAVE("Организатор не может покинуть своё событие, его можно только отменить"),
    SCHEDULE_CONFLICT("Вы уже участвуете в другом событии, которое начинается меньше чем за три часа от этого"),
    ;

    fun message(): String = text
}
