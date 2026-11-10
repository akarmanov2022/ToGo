package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.generated.api.UsersApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CurrentUser
import itmo.goto.generated.model.UpdateProfileRequest
import itmo.goto.generated.model.UserProfile
import itmo.goto.model.User
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Serves user profiles.
 */
@Service
class UsersService(private val users: UserRepository, private val identity: Identity) : UsersApiService {
    @Transactional(readOnly = true)
    override fun getCurrentUser(): CurrentUser = holder().current()

    @Transactional
    override fun updateCurrentUser(updateProfileRequest: UpdateProfileRequest): CurrentUser = holder().apply {
        name = updateProfileRequest.name
        city = updateProfileRequest.city
        categories.clear()
        categories.addAll(updateProfileRequest.categories)
    }.current()

    @Transactional(readOnly = true)
    override fun getUser(userId: UUID): UserProfile = users.findById(userId).map { it.profile() }
        .orElseThrow { ApiFailure(ApiError.Code.USER_NOT_FOUND, "Пользователь не найден") }

    private fun holder(): User = users.findById(identity.user())
        .orElseThrow { ApiFailure(ApiError.Code.UNAUTHORIZED, "Учётная запись не найдена, войдите заново") }
}
