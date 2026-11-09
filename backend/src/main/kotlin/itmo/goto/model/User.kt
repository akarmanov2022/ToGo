import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "users")
class User(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    var firstName: String,

    @Column(nullable = false)
    var lastName: String
)
