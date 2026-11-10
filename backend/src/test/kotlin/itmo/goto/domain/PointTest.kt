package itmo.goto.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PointTest {
    @Test
    fun `latitude alone breaks the longitude`() {
        assertThat(Point(Random.nextDouble(-90.0, 90.0), null).violations().map { it.field() })
            .withFailMessage("Latitude alone doesnt break the longitude field")
            .containsExactly("longitude")
    }

    @Test
    fun `longitude alone breaks the latitude`() {
        assertThat(Point(null, Random.nextDouble(-180.0, 180.0)).violations().map { it.field() })
            .withFailMessage("Longitude alone doesnt break the latitude field")
            .containsExactly("latitude")
    }

    @Test
    fun `both coordinates break nothing`() {
        assertThat(Point(Random.nextDouble(-90.0, 90.0), Random.nextDouble(-180.0, 180.0)).violations())
            .withFailMessage("Both coordinates break a field")
            .isEmpty()
    }

    @Test
    fun `no coordinates break nothing`() {
        assertThat(Point(null, null).violations())
            .withFailMessage("No coordinates break a field")
            .isEmpty()
    }
}
