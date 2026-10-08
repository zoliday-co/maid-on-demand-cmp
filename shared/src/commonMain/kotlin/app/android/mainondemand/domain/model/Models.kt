package app.android.mainondemand.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Every visit is a fixed 60 minutes. */
val BOOKING_DURATION: Duration = 60.minutes

enum class ServiceType(val label: String, val emoji: String, val tagline: String) {
    CLEANING("Cleaning", "🧹", "Sweep, mop & dust"),
    COOKING("Cooking", "🍳", "Home-style meals"),
    DISHWASHING("Dishwashing", "🍽️", "Sparkling utensils"),
}

data class Locality(val id: String, val name: String, val city: String)

data class Professional(
    val id: String,
    val name: String,
    val localityId: String,
    /** INR for one 60-minute visit, per service offered. */
    val prices: Map<ServiceType, Int>,
    val rating: Double,
    val reviewCount: Int,
    val experienceYears: Int,
    val jobsCompleted: Int,
    val languages: List<String>,
    val about: String,
) {
    val services: List<ServiceType> get() = prices.keys.sortedBy { it.ordinal }
}

data class Slot(
    val professionalId: String,
    val start: Instant,
    val isAvailable: Boolean,
) {
    val end: Instant get() = start + BOOKING_DURATION
}

data class SearchCriteria(
    val localityId: String,
    val service: ServiceType,
    val date: LocalDate,
    /** Hour of day in IST (24h), or null for "any time". */
    val startHour: Int? = null,
    val minRating: Double? = null,
    val maxPrice: Int? = null,
)

data class SearchResult(
    val professional: Professional,
    val price: Int,
    /** Earliest open slot that matches the criteria. */
    val matchedSlotStart: Instant,
    val openSlotCount: Int,
)

data class CustomerDetails(
    val name: String = "",
    val mobile: String = "",
    val address: String = "",
)

enum class PaymentMethod(val label: String, val hint: String) {
    UPI("UPI", "Pay with any UPI app"),
    CARD("Card", "Credit or debit card"),
}

enum class BookingStatus { CONFIRMED, CANCELLED }

data class Booking(
    val id: String,
    val professionalId: String,
    val professionalName: String,
    val localityName: String,
    val service: ServiceType,
    val start: Instant,
    val customer: CustomerDetails,
    val amount: Int,
    val paymentMethod: PaymentMethod,
    val paymentRef: String,
    val status: BookingStatus,
    val createdAt: Instant,
    val cancelledAt: Instant? = null,
    val refundAmount: Int? = null,
) {
    val end: Instant get() = start + BOOKING_DURATION
}

data class BookingRequest(
    val professionalId: String,
    val service: ServiceType,
    val slotStart: Instant,
    val customer: CustomerDetails,
    val paymentMethod: PaymentMethod,
)

/** What the customer picked on the profile screen. */
data class DraftSelection(
    val professionalId: String,
    val professionalName: String,
    val localityName: String,
    val service: ServiceType,
    val price: Int,
    val slotStart: Instant,
)

/** In-progress booking. Survives navigation and failed payments; customer details survive restarts. */
data class BookingDraft(
    val selection: DraftSelection? = null,
    val customer: CustomerDetails = CustomerDetails(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
)
