package itmo.goto.service

import itmo.goto.generated.api.CategoriesApiService
import itmo.goto.generated.model.Category
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class CategoriesService : CategoriesApiService {
    override fun getCategories(): List<Category> =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Category listing is not implemented yet")
}
