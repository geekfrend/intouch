package com.geekfrend.intouch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun usNumbersNormaliseWithDefaultCountryCode() {
        assertEquals("+15551234567", PhoneNumbers.normalize("(555) 123-4567", "US"))
        assertEquals("+15551234567", PhoneNumbers.normalize("+1 555-123-4567", "US"))
        assertEquals("US", PhoneNumbers.regionFor(1))
    }

    @Test fun sameLineMatchesAcrossFormats() {
        assertTrue(PhoneNumbers.same("555.123.4567", "+15551234567", "US"))
        assertTrue(PhoneNumbers.same("1-555-123-4567", "(555) 123-4567", "US"))
        assertFalse(PhoneNumbers.same("555-123-4567", "555-123-4568", "US"))
        assertFalse(PhoneNumbers.same("", "555-123-4567", "US"))
    }
}
