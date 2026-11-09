package itmo.goto.service

import itmo.goto.generated.api.UsersApiService
import itmo.goto.generated.model.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UsersService : UsersApiService {
    override fun getCurrentUser(): CurrentUser =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "User profile is not implemented yet")
}
