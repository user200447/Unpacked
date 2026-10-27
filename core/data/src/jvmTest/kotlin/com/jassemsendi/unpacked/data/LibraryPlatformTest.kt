package com.jassemsendi.unpacked.data

import kotlin.time.Clock

class LibraryPlatformTest : LibraryTest() {
    override fun openLibrary(clock: Clock): Library = Library.inMemory(clock)
}
