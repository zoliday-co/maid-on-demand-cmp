package app.android.mainondemand.domain

/** Typed failures crossing the repository boundary. Anything else is a bug. */
sealed class AppException(message: String) : Exception(message) {
    /** Transient and recoverable: retrying the same call may succeed. */
    class Network(message: String = "We couldn't reach the server. Check your connection and try again.") :
        AppException(message)

    class NotFound(message: String = "We couldn't find that.") : AppException(message)

    class SlotUnavailable(message: String = "That slot was just taken.") : AppException(message)

    class PaymentDeclined(message: String = "Your payment didn't go through. You have not been charged.") :
        AppException(message)

    class CancellationNotAllowed(message: String = "This booking can no longer be cancelled.") :
        AppException(message)

    class InvalidRequest(message: String) : AppException(message)
}
