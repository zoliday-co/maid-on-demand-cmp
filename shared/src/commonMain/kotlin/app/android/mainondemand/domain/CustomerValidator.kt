package app.android.mainondemand.domain

import app.android.mainondemand.domain.model.CustomerDetails

data class CustomerErrors(
    val name: String? = null,
    val mobile: String? = null,
    val address: String? = null,
) {
    val isValid: Boolean get() = name == null && mobile == null && address == null
}

object CustomerValidator {
    const val MOBILE_LENGTH = 10
    private const val MIN_ADDRESS_LENGTH = 10
    // Letters plus combining marks, so names in Indian scripts (e.g. "प्रिया") are accepted.
    private val nameRegex = Regex("^\\p{L}[\\p{L}\\p{M} .'-]+$")
    private val mobileRegex = Regex("^[6-9][0-9]{9}$")

    fun validate(details: CustomerDetails) = CustomerErrors(
        name = nameError(details.name),
        mobile = mobileError(details.mobile),
        address = addressError(details.address),
    )

    fun nameError(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Enter your name"
            trimmed.length < 2 -> "Name is too short"
            !nameRegex.matches(trimmed) -> "Use letters only"
            else -> null
        }
    }

    /** Indian mobile numbers are exactly 10 digits and start with 6, 7, 8 or 9. */
    fun mobileError(mobile: String): String? = when {
        mobile.isEmpty() -> "Enter your mobile number"
        mobile.any { !it.isDigit() } -> "Digits only"
        mobile.length != MOBILE_LENGTH -> "Enter all 10 digits"
        !mobileRegex.matches(mobile) -> "Indian mobile numbers start with 6, 7, 8 or 9"
        else -> null
    }

    fun addressError(address: String): String? {
        val trimmed = address.trim()
        return when {
            trimmed.isEmpty() -> "Enter the service address"
            trimmed.length < MIN_ADDRESS_LENGTH -> "Add house number, street and area"
            else -> null
        }
    }

    /** Keeps digits only and drops a pasted +91 / 0 prefix, so "+91 98765 43210" becomes "9876543210". */
    fun sanitizeMobile(input: String): String {
        var digits = input.filter { it in '0'..'9' }
        if (digits.length > MOBILE_LENGTH && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length > MOBILE_LENGTH && digits.startsWith("0")) digits = digits.drop(1)
        return digits.take(MOBILE_LENGTH)
    }
}
