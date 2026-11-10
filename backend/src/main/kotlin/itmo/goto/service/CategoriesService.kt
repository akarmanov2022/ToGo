package itmo.goto.service

import itmo.goto.generated.api.CategoriesApiService
import itmo.goto.generated.model.Category
import itmo.goto.generated.model.EventCategory
import org.springframework.stereotype.Service

/**
 * Lists the fixed categories that profiles and events share, in their declaration order.
 */
@Service
class CategoriesService : CategoriesApiService {
    override fun getCategories(): List<Category> = EventCategory.entries.map {
        Category(
            it,
            when (it) {
                EventCategory.PICNIC -> "Пикник"
                EventCategory.RUN -> "Пробежка"
                EventCategory.EXCURSION -> "Поход на экскурсию"
                EventCategory.BOARD_GAMES -> "Настолки"
                EventCategory.QUEST -> "Квест"
                EventCategory.CINEMA -> "Кино"
                EventCategory.CAFE -> "Кафе/Ресторан"
            },
        )
    }
}
