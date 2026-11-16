package com.mafaz.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class KeysTest {
    // Stand-in for PhoneNumberUtils.normalizeNumber (keeps digits and a leading plus).
    private val norm: (String) -> String = { s -> s.filter { it.isDigit() || it == '+' } }

    private fun k(raw: String, len: Int = 9) = Keys.keyOf(raw, len, norm)

    @Test
    fun sameLocalNumberInDifferentFormatsGivesOneKey() {
        assertEquals("775551234", k("+94 77 555 1234"))
        assertEquals("775551234", k("077-5551234"))
        assertEquals("775551234", k("(077) 555 1234"))
    }

    @Test
    fun internationalNumberKeepsLastNineDigits() {
        assertEquals("155550134", k("+1 415 555 0134"))
    }

    @Test
    fun shortCodesAreKeptWhole() {
        assertEquals("911", k("911"))
    }

    @Test
    fun blankInputGivesEmptyKey() {
        assertEquals("", k(""))
        assertEquals("", k("   "))
    }

    @Test
    fun lengthIsClampedToSevenToTwelve() {
        assertEquals("5551234", k("+94 77 555 1234", 5))
        assertEquals("94775551234", k("+94 77 555 1234", 20))
        assertEquals("75551234", k("+94 77 555 1234", 8))
    }
}
