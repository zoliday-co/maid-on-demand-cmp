package app.android.mainondemand.data.fake

import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.domain.model.CustomerDetails
import app.android.mainondemand.domain.model.Locality
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.domain.model.Professional
import app.android.mainondemand.domain.model.ServiceType
import app.android.mainondemand.domain.model.ServiceType.CLEANING
import app.android.mainondemand.domain.model.ServiceType.COOKING
import app.android.mainondemand.domain.model.ServiceType.DISHWASHING
import app.android.mainondemand.domain.slotStart
import app.android.mainondemand.domain.toIst
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal object SeedData {
    private const val INDIRANAGAR = "indiranagar"
    private const val KORAMANGALA = "koramangala"
    private const val HSR = "hsr-layout"

    val localities = listOf(
        Locality(INDIRANAGAR, "Indiranagar", "Bengaluru"),
        Locality(KORAMANGALA, "Koramangala", "Bengaluru"),
        Locality(HSR, "HSR Layout", "Bengaluru"),
    )

    val professionals = listOf(
        pro("p1", "Lakshmi Devi", INDIRANAGAR, 4.8, 312, 7, 1840, listOf("Kannada", "Hindi"),
            "Careful, quick and great with delicate surfaces. Brings her own microfibre cloths.",
            CLEANING to 249, DISHWASHING to 149),
        pro("p2", "Sunita Yadav", INDIRANAGAR, 4.6, 198, 5, 960, listOf("Hindi", "English"),
            "North Indian home cooking — rotis, dal and sabzi the way your family makes them.",
            COOKING to 349, DISHWASHING to 179),
        pro("p3", "Meena Kumari", INDIRANAGAR, 4.2, 87, 3, 410, listOf("Tamil", "Kannada"),
            "Reliable everyday help for small homes. Happy to follow your routine.",
            CLEANING to 199, COOKING to 299),
        pro("p4", "Rekha Naik", INDIRANAGAR, 4.9, 540, 11, 3120, listOf("Kannada", "Hindi", "English"),
            "A decade of experience across cleaning, cooking and kitchen care. Top rated in the area.",
            CLEANING to 329, COOKING to 399, DISHWASHING to 199),
        pro("p5", "Fatima Sheikh", KORAMANGALA, 4.7, 265, 8, 1510, listOf("Hindi", "Urdu", "English"),
            "Known for biryani and meal prep for the week. Keeps the kitchen spotless afterwards.",
            COOKING to 379, CLEANING to 279),
        pro("p6", "Anjali Patil", KORAMANGALA, 4.0, 54, 2, 230, listOf("Marathi", "Hindi"),
            "Friendly and punctual. Best for regular sweeping, mopping and utensils.",
            CLEANING to 179, DISHWASHING to 129),
        pro("p7", "Kavita Reddy", KORAMANGALA, 4.5, 176, 6, 1105, listOf("Telugu", "Kannada", "English"),
            "Andhra and South Indian meals, mild or spicy. Leaves the sink empty.",
            COOKING to 319, DISHWASHING to 159),
        pro("p8", "Pooja Gowda", KORAMANGALA, 3.8, 41, 1, 120, listOf("Kannada"),
            "New on the platform and eager to help with all household chores.",
            CLEANING to 229, COOKING to 289, DISHWASHING to 169),
        pro("p9", "Shanthi Murthy", HSR, 4.4, 133, 4, 720, listOf("Kannada", "Tamil"),
            "Deep-cleans kitchens and bathrooms, and cooks simple vegetarian meals.",
            CLEANING to 219, COOKING to 339),
        pro("p10", "Geeta Pawar", HSR, 4.3, 98, 3, 505, listOf("Hindi", "Marathi"),
            "Fast with utensils and everyday tidying. Good with pets around.",
            DISHWASHING to 139, CLEANING to 189),
    )

    /** One upcoming, one cancelled and one completed booking, relative to first launch. */
    fun bookings(now: Instant): List<Booking> {
        val today = now.toIst().date
        val customer = CustomerDetails(
            name = "Aarav Sharma",
            mobile = "9876543210",
            address = "Flat 204, Sunrise Residency, 12th Main, Indiranagar, Bengaluru 560038",
        )
        fun booking(
            id: String, proId: String, service: ServiceType, start: Instant, method: PaymentMethod,
            cancelled: Boolean = false,
        ): Booking {
            val pro = professionals.first { it.id == proId }
            val amount = pro.prices.getValue(service)
            val createdAt = minOf(now, start) - 2.days
            return Booking(
                id = id,
                professionalId = pro.id,
                professionalName = pro.name,
                localityName = localities.first { it.id == pro.localityId }.name,
                service = service,
                start = start,
                customer = customer,
                amount = amount,
                paymentMethod = method,
                paymentRef = "${method.name}-SEED$id".replace("MB-", ""),
                status = if (cancelled) BookingStatus.CANCELLED else BookingStatus.CONFIRMED,
                createdAt = createdAt,
                cancelledAt = if (cancelled) createdAt + 3.hours else null,
                refundAmount = if (cancelled) amount else null,
            )
        }
        return listOf(
            booking("MB-7KQ2XA", "p1", CLEANING, slotStart(today.plus(1, DateTimeUnit.DAY), 10), PaymentMethod.UPI),
            booking(
                "MB-3HV9RD", "p5", COOKING, slotStart(today.plus(2, DateTimeUnit.DAY), 18), PaymentMethod.CARD,
                cancelled = true,
            ),
            booking("MB-5TN4WE", "p2", DISHWASHING, slotStart(today.minus(1, DateTimeUnit.DAY), 9), PaymentMethod.UPI),
        )
    }

    private fun pro(
        id: String, name: String, localityId: String, rating: Double, reviews: Int, years: Int, jobs: Int,
        languages: List<String>, about: String, vararg prices: Pair<ServiceType, Int>,
    ) = Professional(
        id = id,
        name = name,
        localityId = localityId,
        prices = prices.toMap(),
        rating = rating,
        reviewCount = reviews,
        experienceYears = years,
        jobsCompleted = jobs,
        languages = languages,
        about = about,
    )
}
