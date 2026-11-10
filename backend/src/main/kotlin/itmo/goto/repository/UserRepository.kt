package itmo.goto.repository

import itmo.goto.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByTgId(tgId: Long): User?
}
