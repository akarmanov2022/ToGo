package itmo.goto.auth

/**
 * Refusal to trust Mini App launch data, raised before any account is touched.
 *
 * Messages describe the defect only and never quote the launch data itself.
 */
class InvalidLaunch(message: String) : RuntimeException(message)
