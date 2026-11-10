package itmo.goto.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class CategoriesServiceTest {
    @Test
    fun `category listing answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                CategoriesService().getCategories()
            }?.statusCode,
        )
            .withFailMessage("Category listing doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
}
