package app.android.mainondemand.data.persistence

import app.android.mainondemand.domain.model.Booking
import app.android.mainondemand.domain.model.BookingStatus
import app.android.mainondemand.domain.model.CustomerDetails
import app.android.mainondemand.domain.model.PaymentMethod
import app.android.mainondemand.domain.model.ServiceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal val snapshotJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class CustomerDto(
    val name: String = "",
    val mobile: String = "",
    val address: String = "",
) {
    fun toDomain() = CustomerDetails(name, mobile, address)
}

internal fun CustomerDetails.toDto() = CustomerDto(name, mobile, address)

@Serializable
internal data class BookingDto(
    val id: String,
    val professionalId: String,
    val professionalName: String,
    val localityName: String,
    val service: String,
    val startEpochMs: Long,
    val customer: CustomerDto,
    val amount: Int,
    val paymentMethod: String,
    val paymentRef: String,
    val status: String,
    val createdAtEpochMs: Long,
    val cancelledAtEpochMs: Long? = null,
    val refundAmount: Int? = null,
) {
    fun toDomain() = Booking(
        id = id,
        professionalId = professionalId,
        professionalName = professionalName,
        localityName = localityName,
        service = ServiceType.valueOf(service),
        start = Instant.fromEpochMilliseconds(startEpochMs),
        customer = customer.toDomain(),
        amount = amount,
        paymentMethod = PaymentMethod.valueOf(paymentMethod),
        paymentRef = paymentRef,
        status = BookingStatus.valueOf(status),
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        cancelledAt = cancelledAtEpochMs?.let(Instant::fromEpochMilliseconds),
        refundAmount = refundAmount,
    )
}

internal fun Booking.toDto() = BookingDto(
    id = id,
    professionalId = professionalId,
    professionalName = professionalName,
    localityName = localityName,
    service = service.name,
    startEpochMs = start.toEpochMilliseconds(),
    customer = customer.toDto(),
    amount = amount,
    paymentMethod = paymentMethod.name,
    paymentRef = paymentRef,
    status = status.name,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    cancelledAtEpochMs = cancelledAt?.toEpochMilliseconds(),
    refundAmount = refundAmount,
)

/** Everything the fake backend needs to come back identical after an app restart. */
@Serializable
internal data class BackendSnapshot(
    val bookings: List<BookingDto> = emptyList(),
    /** Slots taken by "other customers" through the slot-conflict control. */
    val takenSlots: List<String> = emptyList(),
)

@Serializable
internal data class DraftSnapshot(
    val customer: CustomerDto = CustomerDto(),
    val paymentMethod: String = PaymentMethod.UPI.name,
)
