package com.jassemsendi.unpacked.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves that common tests compile and run on every target of the module skeleton.
 * Disposable: the first real model test (ticket #3) replaces it.
 */
class SkeletonSmokeTest {
    @Test
    fun commonTestsRunOnThisTarget() {
        assertEquals(4, 2 + 2)
    }
}
