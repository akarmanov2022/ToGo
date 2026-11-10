package itmo.goto.service

import itmo.goto.generated.model.EventCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CategoriesServiceTest {
    @Test
    fun `category listing follows declaration order`() {
        assertThat(CategoriesService().getCategories().map { it.code })
            .withFailMessage("Category listing doesnt follow the declaration order")
            .containsExactlyElementsOf(EventCategory.entries)
    }

    @Test
    fun `category listing names categories in russian`() {
        assertThat(CategoriesService().getCategories().map { it.name })
            .withFailMessage("Category listing doesnt name categories in Russian")
            .containsExactly("Пикник", "Пробежка", "Поход на экскурсию", "Настолки", "Квест", "Кино", "Кафе/Ресторан")
    }
}
