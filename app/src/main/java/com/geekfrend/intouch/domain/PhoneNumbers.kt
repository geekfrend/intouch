package com.geekfrend.intouch.domain

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Normalises and compares phone numbers so call-history entries match tracked contacts. */
object PhoneNumbers {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /** Region for a country calling code, e.g. 1 -> "US". Falls back to US. */
    fun regionFor(countryCode: Int): String =
        util.getRegionCodeForCountryCode(countryCode).takeIf { it != "ZZ" } ?: "US"

    /** E.164 form (+15551234567) when the number parses, otherwise its digits. */
    fun normalize(raw: String, region: String): String {
        if (raw.isBlank()) return ""
        return try {
            util.format(util.parse(raw, region), PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: NumberParseException) {
            raw.filter { it.isDigit() || it == '+' }
        }
    }

    /** True when two already-normalised numbers are the same line. */
    fun matches(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val ta = a.filter { it.isDigit() }.takeLast(10)
        val tb = b.filter { it.isDigit() }.takeLast(10)
        return ta.length >= 7 && ta == tb
    }

    fun same(a: String, b: String, region: String): Boolean = matches(normalize(a, region), normalize(b, region))
}
