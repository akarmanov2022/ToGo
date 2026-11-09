package itmo.goto.service

import itmo.goto.generated.api.UsersApiService
import itmo.goto.generated.model.CurrentUser
import itmo.goto.generated.model.UpdateProfileRequest
import itmo.goto.generated.model.UserProfile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class UsersService : UsersApiService {
    override fun getCurrentUser(): CurrentUser =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "User profile is not implemented yet")

    override fun updateCurrentUser(updateProfileRequest: UpdateProfileRequest): CurrentUser =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Profile update is not implemented yet")

    override fun getUser(userId: UUID): UserProfile =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Viewing another profile is not implemented yet")
}
