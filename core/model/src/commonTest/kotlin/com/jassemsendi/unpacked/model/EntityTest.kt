package com.jassemsendi.unpacked.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EntityTest {
    @Test
    fun idIsSourceKindAndKey() {
        val entity = Entity(Source.Instagram, EntityKind.Advertiser, key = "fcbarcelona", displayName = "FC Barcelona")
        assertEquals("instagram:advertiser:fcbarcelona", entity.id)
    }

    @Test
    fun normalizedKeyCollapsesCaseAndWhitespace() {
        assertEquals("fc barcelona", Entity.normalizeKey("  FC   Barcelona "))
    }
}
