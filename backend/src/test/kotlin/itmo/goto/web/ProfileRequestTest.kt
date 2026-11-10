package itmo.goto.web

import itmo.goto.generated.api.UsersApiController
import itmo.goto.generated.api.UsersApiService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import kotlin.random.Random

@WebMvcTest(UsersApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileRequestTest(@Autowired private val mvc: MockMvcTester) {
    @MockitoBean
    private lateinit var users: UsersApiService

    @Test
    fun `unknown category gives validation error`() {
        assertThat(
            mvc.put().uri("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Аня ${Random.nextInt()}","categories":["SPORT${Random.nextInt(1000)}"]}"""),
        )
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Unknown category doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `unknown category is named in details`() {
        assertThat(
            mvc.put().uri("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Аня ${Random.nextInt()}","categories":["SPORT${Random.nextInt(1000)}"]}"""),
        )
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Unknown category isnt named in details")
            .isEqualTo("categories[0]")
    }

    @Test
    fun `blank name gives validation error`() {
        assertThat(
            mvc.put().uri("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"${" ".repeat(Random.nextInt(1, 10))}","categories":[]}"""),
        )
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Blank name doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `blank name is named in details`() {
        assertThat(
            mvc.put().uri("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"${" ".repeat(Random.nextInt(1, 10))}","categories":[]}"""),
        )
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Blank name isnt named in details")
            .isEqualTo("name")
    }
}
