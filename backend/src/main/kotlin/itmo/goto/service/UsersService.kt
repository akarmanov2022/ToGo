package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.generated.api.UsersApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CurrentUser
import itmo.goto.generated.model.UpdateProfileRequest
import itmo.goto.generated.model.UserProfile
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Serves user profiles.
 */
@Service
class UsersService(private val users: UserRepository, private val identity: Identity) : UsersApiService {
    @Transactional(readOnly = true)
    override fun getCurrentUser(): CurrentUser =
        users.findById(identity.user()).map { it.current() }
            .orElseThrow { ApiFailure(ApiError.Code.UNAUTHORIZED, "Учётная запись не найдена, войдите заново") }

    override fun updateCurrentUser(updateProfileRequest: UpdateProfileRequest): CurrentUser =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Profile update is not implemented yet")

    override fun getUser(userId: UUID): UserProfile =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Viewing another profile is not implemented yet")
}
