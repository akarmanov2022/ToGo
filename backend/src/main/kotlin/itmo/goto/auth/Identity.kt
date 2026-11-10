package itmo.goto.auth

import java.util.UUID

/**
 * Account on whose behalf the current request runs.
 */
fun interface Identity {
    fun user(): UUID
}
