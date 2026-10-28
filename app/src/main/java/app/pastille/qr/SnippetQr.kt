package app.pastille.qr

import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment

/**
 * A text snippet as a QR code that any camera app or Google Lens can read.
 *
 * Byte mode over the UTF-8 bytes, no ECI header (camera apps read UTF-8 without one, and some
 * readers mishandle it). Error correction starts at M and is raised to Q or H when that fits in
 * the same version, so short snippets get the sturdier code for free. Encoding is Project Nayuki's
 * generator, vendored under `io.nayuki.qrcodegen` (MIT, see docs/third-party-notices.md).
 */
object SnippetQr {
    /** Byte-mode capacity of a version 40 code at level M: 2,334 data codewords less the 20-bit header. */
    const val MAX_BYTES = 2331

    /** Modules of white around the code on every side, as the standard asks. */
    const val QUIET_ZONE = 4

    fun byteCount(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    fun fits(text: String): Boolean = text.isNotEmpty() && byteCount(text) <= MAX_BYTES

    /** The code for [text], or null when it is empty or longer than [MAX_BYTES] in UTF-8. */
    fun encode(text: String): QrCode? {
        if (!fits(text)) return null
        val segment = QrSegment.makeBytes(text.toByteArray(Charsets.UTF_8))
        return QrCode.encodeSegments(listOf(segment), QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -1, true)
    }
}
