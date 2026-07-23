package uz.railway.ticketbot.station

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "stations")
class StationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "code", nullable = false, unique = true)
    var code: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "name_normalized", nullable = false)
    var nameNormalized: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) {
    fun toDomain(): Station = Station(code = code, name = name)
}
