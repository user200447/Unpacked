package com.jassemsendi.unpacked.ingest.instagram

import kotlin.test.Test
import kotlin.test.assertEquals

class MojibakeTest {
    @Test
    fun utf8ReadBackAsLatin1IsRepaired() {
        assertEquals("Émilie Noël", repairMojibake("Ã\u0089milie NoÃ«l"))
        assertEquals("جاسم", repairMojibake("Ø¬Ø§Ø³Ù\u0085"))
        assertEquals("👠", repairMojibake("ð\u009f\u0091 "))
        assertEquals("Naji - الناجي", repairMojibake("Naji - Ø§Ù\u0084Ù\u0086Ø§Ø¬Ù\u008a"))
    }

    @Test
    fun textThatIsAlreadyValidIsUnchanged() {
        assertEquals("", repairMojibake(""))
        assertEquals("Example Hotels", repairMojibake("Example Hotels"))
        assertEquals("جاسم", repairMojibake("جاسم"), "characters beyond Latin-1 cannot be mojibake")
        assertEquals("Zoë", repairMojibake("Zoë"), "Latin-1 letters that form no UTF-8 sequence")
        assertEquals("café", repairMojibake("café"))
        assertEquals("20°C", repairMojibake("20°C"))
        assertEquals("👠 Émilie", repairMojibake("👠 Émilie"))
    }
}
