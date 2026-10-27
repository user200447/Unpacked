package com.jassemsendi.unpacked.data

import android.content.ContextWrapper
import kotlin.time.Clock

/**
 * Room's Android builder requires a Context but, with a driver set and no multi-instance invalidation,
 * never calls it. Host tests run against stub Android classes that return defaults, so an empty wrapper
 * is enough. The same stubs make `android.database.SQLException` lose its message, which is why the
 * writer never relies on reading one. If Room ever starts using the Context, Robolectric is the next step.
 */
class LibraryPlatformTest : LibraryTest() {
    override fun openLibrary(clock: Clock): Library = Library.inMemory(ContextWrapper(null), clock)
}
