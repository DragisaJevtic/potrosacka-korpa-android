package dev.intelisystems.potrosackakorpa

import org.junit.Assert.*
import org.junit.Test

class BarcodeCodeTest {
    @Test fun acceptsStandardProductCodes() {
        listOf("4006381333931", "036000291452", "0036000291452", "00036000291452", "96385074")
            .forEach { assertTrue(it, validProductCode(it)) }
    }
    @Test fun rejectsWrongCheckDigitAndUntrustedQrPayloads() {
        listOf("4006381333932", "", "00000000", "123", "https://example.com/4006381333931",
            "4006381333931 OR 1=1", "４００６３８１３３３９３１")
            .forEach { assertFalse(it, validProductCode(it)) }
    }
    @Test fun rejectsStoreInternalCodesEvenWithCorrectChecksum() {
        // EAN-13 checksum is valid, but prefix 20 denotes restricted circulation.
        assertFalse(validProductCode("2000000000008"))
    }
}
