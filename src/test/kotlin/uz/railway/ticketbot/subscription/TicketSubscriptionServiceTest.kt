package uz.railway.ticketbot.subscription

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.search.TicketSearchOutcome
import uz.railway.ticketbot.search.sampleOffer
import uz.railway.ticketbot.search.sampleRequest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals

class TicketSubscriptionServiceTest {

    private val fixedToday = LocalDate.of(2026, 7, 25)
    private val clock = Clock.fixed(fixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    private lateinit var repository: TicketSubscriptionRepository
    private lateinit var service: TicketSubscriptionService

    @BeforeEach
    fun setUp() {
        repository = mockk()
        service = TicketSubscriptionService(repository, objectMapper, clock)
        var nextId = 1L
        every { repository.save(any()) } answers {
            val entity = firstArg<TicketSubscriptionEntity>()
            if (entity.id == null) entity.id = nextId++
            entity
        }
    }

    // Test 1 (spec section 27): a match on startDate itself means nothing can be earlier -> COMPLETED immediately.
    @Test
    fun `subscription is COMPLETED when the match is on startDate itself`() {
        val request = sampleRequest(fixedToday, fixedToday.plusDays(30))
        val outcome = TicketSearchOutcome(request, listOf(sampleOffer(fixedToday, listOf(9))))
        val slot = slot<TicketSubscriptionEntity>()
        every { repository.save(capture(slot)) } answers {
            val entity = firstArg<TicketSubscriptionEntity>()
            entity.id = 99L
            entity
        }

        val subscription = service.create(telegramUserId = 42L, outcome = outcome)

        assertEquals(SubscriptionStatus.COMPLETED, subscription.status)
        assertEquals(fixedToday, subscription.currentBestDate)
        assertEquals(null, slot.captured.nextCheckAt)
    }

    // Test 2: a match after startDate leaves an ACTIVE subscription to monitor for something earlier.
    @Test
    fun `subscription stays ACTIVE when the match is after startDate`() {
        val request = sampleRequest(fixedToday, fixedToday.plusDays(30))
        val matchDate = fixedToday.plusDays(3)
        val outcome = TicketSearchOutcome(request, listOf(sampleOffer(matchDate, listOf(15))))

        val subscription = service.create(telegramUserId = 42L, outcome = outcome)

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertEquals(matchDate, subscription.currentBestDate)
    }

    // Test 10: nothing found anywhere -> still ACTIVE, but with a null currentBestDate (spec section 11's recommended variant).
    @Test
    fun `subscription is ACTIVE with null currentBestDate when nothing was found`() {
        val request = sampleRequest(fixedToday, fixedToday.plusDays(30))
        val outcome = TicketSearchOutcome(request, emptyList())

        val subscription = service.create(telegramUserId = 42L, outcome = outcome)

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertEquals(null, subscription.currentBestDate)
    }

    // applyEarlierResult must flip the subscription to COMPLETED once the new best date reaches startDate (spec section 10.1).
    @Test
    fun `applyEarlierResult completes the subscription once currentBestDate reaches startDate`() {
        val entity = TicketSubscriptionEntity(
            id = 7L,
            telegramUserId = 42L,
            fromStationCode = "TASHKENT",
            fromStationName = "Toshkent",
            toStationCode = "URGANCH",
            toStationName = "Urganch",
            startDate = fixedToday,
            endDate = fixedToday.plusDays(30),
            currentBestDate = fixedToday.plusDays(3),
            status = SubscriptionStatus.ACTIVE
        )
        every { repository.findById(7L) } returns java.util.Optional.of(entity)

        service.applyEarlierResult(7L, fixedToday, listOf(sampleOffer(fixedToday, listOf(9))))

        assertEquals(SubscriptionStatus.COMPLETED, entity.status)
        assertEquals(fixedToday, entity.currentBestDate)
    }
}
