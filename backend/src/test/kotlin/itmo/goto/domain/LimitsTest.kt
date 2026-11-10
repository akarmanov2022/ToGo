package itmo.goto.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class LimitsTest {
    @Test
    fun `minimum above maximum breaks minimum`() {
        val max = Random.nextInt(1, 50)
        assertThat(Limits(Random.nextInt(max + 1, 51), max).violations().map { it.field() })
            .withFailMessage("Minimum above maximum doesnt break the minUsers field")
            .containsExactly("minUsers")
    }

    @Test
    fun `zero minimum breaks minimum`() {
        assertThat(Limits(0, Random.nextInt(1, 51)).violations().map { it.field() })
            .withFailMessage("Zero minimum doesnt break the minUsers field")
            .containsExactly("minUsers")
    }

    @Test
    fun `maximum of fifty one breaks maximum`() {
        assertThat(Limits(Random.nextInt(1, 51), 51).violations().map { it.field() })
            .withFailMessage("Maximum of 51 doesnt break the maxUsers field")
            .containsExactly("maxUsers")
    }

    @Test
    fun `limits within the contract break nothing`() {
        val min = Random.nextInt(1, 51)
        assertThat(Limits(min, Random.nextInt(min, 51)).violations())
            .withFailMessage("Limits within the contract break a field")
            .isEmpty()
    }

    @Test
    fun `meeting for one breaks nothing`() {
        assertThat(Limits(1, 1).violations())
            .withFailMessage("Meeting for one person breaks a field")
            .isEmpty()
    }

    @Test
    fun `meeting for one has no free places with its organizer`() {
        assertThat(Limits(1, 1).free(1))
            .withFailMessage("Meeting for one person has free places with its organizer inside")
            .isZero()
    }

    @Test
    fun `maximum below participants does not hold them`() {
        val max = Random.nextInt(1, 50)
        assertThat(Limits(1, max).holds(Random.nextInt(max + 1, 51)))
            .withFailMessage("Maximum below the participants still holds them")
            .isFalse()
    }
}
