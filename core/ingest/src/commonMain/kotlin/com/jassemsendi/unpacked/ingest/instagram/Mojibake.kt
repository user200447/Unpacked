package com.jassemsendi.unpacked.ingest.instagram

/**
 * Repairs text the Export wrote as UTF-8 bytes read back as Latin-1 (`Ã‰milie` for `Émilie`),
 * which is how Instagram serialises every non-ASCII name. Text is mojibake only when every
 * character fits in a byte and those bytes form valid UTF-8; `Zoë` or `جاسم` come back as they are.
 * Instagram's quirk, so it lives in the Instagram adapter and nowhere else.
 */
internal fun repairMojibake(text: String): String {
    if (!fitsInLatin1WithHighBytes(text)) return text
    val bytes = ByteArray(text.length) { text[it].code.toByte() }
    return runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrDefault(text)
}

private const val FIRST_NON_ASCII = 0x80
private const val LAST_LATIN1 = 0xFF

private fun fitsInLatin1WithHighBytes(text: String): Boolean {
    var high = false
    for (char in text) {
        if (char.code > LAST_LATIN1) return false
        if (char.code >= FIRST_NON_ASCII) high = true
    }
    return high
}
