package app.pastille.qr

import io.nayuki.qrcodegen.DataTooLongException
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SnippetQrTest {

    @Test
    fun `a short snippet gets the smallest code and the strongest correction that fits`() {
        val code = SnippetQr.encode("Hello")!!
        assertEquals(1, code.version)
        assertEquals(21, code.size)
        assertEquals(QrCode.Ecc.HIGH, code.errorCorrectionLevel)
    }

    @Test
    fun `correction never drops below M`() {
        listOf(1, 40, 300, 1000, SnippetQr.MAX_BYTES).forEach { n ->
            val code = SnippetQr.encode("a".repeat(n))!!
            assertTrue("$n bytes", code.errorCorrectionLevel.ordinal >= QrCode.Ecc.MEDIUM.ordinal)
        }
    }

    @Test
    fun `the version grows with the text and the module count follows it`() {
        val small = SnippetQr.encode("a".repeat(100))!!
        val large = SnippetQr.encode("a".repeat(1000))!!
        assertTrue(large.version > small.version)
        listOf(small, large).forEach { assertEquals(it.version * 4 + 17, it.size) }
    }

    @Test
    fun `the limit is version 40 at M in byte mode`() {
        val full = SnippetQr.encode("a".repeat(SnippetQr.MAX_BYTES))!!
        assertEquals(40, full.version)
        assertEquals(QrCode.Ecc.MEDIUM, full.errorCorrectionLevel)
        // One byte more and the library itself refuses, so MAX_BYTES is the real edge, not a guess.
        val over = QrSegment.makeBytes(ByteArray(SnippetQr.MAX_BYTES + 1) { 'a'.code.toByte() })
        assertThrows(DataTooLongException::class.java) {
            QrCode.encodeSegments(listOf(over), QrCode.Ecc.MEDIUM, 1, 40, -1, false)
        }
    }

    @Test
    fun `too long means no code at all, never a broken one`() {
        val text = "a".repeat(SnippetQr.MAX_BYTES + 1)
        assertFalse(SnippetQr.fits(text))
        assertNull(SnippetQr.encode(text))
    }

    @Test
    fun `an empty snippet has no code`() {
        assertFalse(SnippetQr.fits(""))
        assertNull(SnippetQr.encode(""))
    }

    @Test
    fun `capacity counts UTF-8 bytes, not characters`() {
        assertEquals(9, SnippetQr.byteCount("café ☕"))
        assertEquals(4, SnippetQr.byteCount("😀"))
        // 1,166 two-byte letters are 2,332 bytes: one over, though far fewer characters.
        assertFalse(SnippetQr.fits("é".repeat(1166)))
        assertTrue(SnippetQr.fits("é".repeat(1165)))
        assertEquals(40, SnippetQr.encode("é".repeat(1165))!!.version)
    }

    @Test
    fun `the payload is the snippet's UTF-8 bytes in byte mode`() {
        val text = "Café ☕ à 8h, ok?"
        val expected = QrCode.encodeSegments(
            listOf(QrSegment.makeBytes(text.toByteArray(Charsets.UTF_8))),
            QrCode.Ecc.MEDIUM, 1, 40, -1, true,
        )
        assertEquals(fingerprint(expected), fingerprint(SnippetQr.encode(text)!!))
    }

    // Known vectors: these grids were decoded by an independent reader (ZXing's C++ port) back to
    // exactly their text when this test was written. A change here means the codes changed.
    @Test
    fun `known vectors stay the same`() {
        VECTORS.forEach { (text, hash) -> assertEquals(text, hash, fingerprint(SnippetQr.encode(text)!!)) }
    }

    private companion object {
        val VECTORS = listOf(
            "Hello" to "5a691e95b457fd51685d329ad3f8ccca0f35e5c3e5ca5dff98dc58e0ded6e210",
            "Café ☕ à 8h, ok? 😀" to "738294eb19094059657b88c86247e287d306822a03071f9986b7fd08189ec92a",
            "https://example.org/a?b=c" to "9171c4488da9850df4aa6a2d0ce53aace92003c5edd931a472172c1f0d8de781",
            // The biggest code there is: version 40 at M, 1,165 two-byte letters.
            "é".repeat(1165) to "4f834be08341f8be936e6b20d29807ec31088bdb029452bb5415fb4507963076",
        )

        /** SHA-256 of the module grid, row by row, '1' dark and '0' light. */
        fun fingerprint(code: QrCode): String {
            val grid = buildString {
                for (y in 0 until code.size) {
                    for (x in 0 until code.size) append(if (code.getModule(x, y)) '1' else '0')
                    append('\n')
                }
            }
            return MessageDigest.getInstance("SHA-256").digest(grid.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
