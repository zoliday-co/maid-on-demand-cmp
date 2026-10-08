package app.android.mainondemand

import app.android.mainondemand.domain.CustomerValidator
import app.android.mainondemand.domain.formatInr
import app.android.mainondemand.domain.futureHours
import app.android.mainondemand.domain.model.CustomerDetails
import app.android.mainondemand.domain.slotStart
import app.android.mainondemand.domain.toIst
import app.android.mainondemand.ui.formatIstDate
import app.android.mainondemand.ui.formatIstSlot
import app.android.mainondemand.ui.formatRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomerValidatorTest {

    @Test
    fun acceptsCompleteDetails() {
        assertTrue(CustomerValidator.validate(VALID_CUSTOMER).isValid)
    }

    @Test
    fun mobileMustBeTenDigitsStartingSixToNine() {
        assertNull(CustomerValidator.mobileError("9876543210"))
        assertNull(CustomerValidator.mobileError("6000000000"))
        assertNotNull(CustomerValidator.mobileError(""))
        assertNotNull(CustomerValidator.mobileError("987654321"), "nine digits")
        assertNotNull(CustomerValidator.mobileError("98765432101"), "eleven digits")
        assertNotNull(CustomerValidator.mobileError("5876543210"), "starts with 5")
        assertNotNull(CustomerValidator.mobileError("98765 4321"), "contains a space")
        assertNotNull(CustomerValidator.mobileError("+919876543210"), "country code is not part of the number")
    }

    @Test
    fun sanitizeMobileStripsFormattingAndCountryCode() {
        assertEquals("9876543210", CustomerValidator.sanitizeMobile("+91 98765 43210"))
        assertEquals("9876543210", CustomerValidator.sanitizeMobile("098765-43210"))
        assertEquals("9198765432", CustomerValidator.sanitizeMobile("9198765432"), "a 10-digit number starting 91 is kept")
        assertEquals("9876543210", CustomerValidator.sanitizeMobile("98765432109999"))
        assertEquals("", CustomerValidator.sanitizeMobile("abc"))
    }

    @Test
    fun nameAndAddressAreRequired() {
        val errors = CustomerValidator.validate(CustomerDetails(name = " ", mobile = "9876543210", address = "Flat 2"))
        assertFalse(errors.isValid)
        assertNotNull(errors.name)
        assertNull(errors.mobile)
        assertNotNull(errors.address, "address is too short to find")
        assertNotNull(CustomerValidator.nameError("R2D2"))
        assertNull(CustomerValidator.nameError("Mary-Anne D'Souza"))
        assertNull(CustomerValidator.nameError("प्रिया नायर"))
    }
}

class FormattingTest {

    @Test
    fun rupeesUseIndianGrouping() {
        assertEquals("₹0", formatInr(0))
        assertEquals("₹249", formatInr(249))
        assertEquals("₹1,499", formatInr(1499))
        assertEquals("₹1,00,000", formatInr(100000))
        assertEquals("₹12,34,567", formatInr(1234567))
    }

    @Test
    fun timesAreShownInIstRegardlessOfDeviceZone() {
        // 04:30 UTC is 10:00 in Asia/Kolkata.
        assertEquals("10:00 AM – 11:00 AM IST", TEST_NOW.formatIstSlot())
        assertEquals("Thu, 8 Oct", TEST_NOW.formatIstDate())
        // 19:00 UTC is already half past midnight the next day in IST.
        val lateUtc = kotlin.time.Instant.parse("2026-10-08T19:00:00Z")
        assertEquals("Fri, 9 Oct", lateUtc.formatIstDate())
        assertEquals("12:30 AM – 1:30 AM IST", lateUtc.formatIstSlot())
    }

    @Test
    fun slotStartsAreBuiltInIst() {
        val today = TEST_NOW.toIst().date
        assertEquals(TEST_NOW, slotStart(today, 10))
        // At exactly 10:00 the 10:00 slot is no longer in the future.
        assertEquals((11..19).toList(), futureHours(today, TEST_NOW))
    }

    @Test
    fun ratingsKeepOneDecimal() {
        assertEquals("4.0", 4.0.formatRating())
        assertEquals("4.8", 4.8.formatRating())
    }
}
