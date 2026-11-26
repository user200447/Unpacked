package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The one mapping from what the database throws to the DataError the library answers with. */
class LocalCallTest {
    @Test
    fun aFullDatabaseIsDiskFullHoweverTheDriverSpellsIt() {
        val bundled = "SQLiteException"
        assertEquals(
            DataError.Local.DISK_FULL,
            localErrorOf(bundled, "Error code: 13, message: database or disk is full"),
        )
        assertEquals(DataError.Local.DISK_FULL, localErrorOf(bundled, "Error code: 13"))
        assertEquals(DataError.Local.DISK_FULL, localErrorOf("SQLiteFullException", null))
        assertEquals(DataError.Local.UNKNOWN, localErrorOf(bundled, "Error code: 1, message: no such table"))
        assertEquals(DataError.Local.UNKNOWN, localErrorOf(bundled, "Error code: 130"))
        assertEquals(DataError.Local.UNKNOWN, localErrorOf("IllegalStateException", null))
    }

    @Test
    fun anythingElseIsUnknownAndCancellationIsNeverSwallowed() {
        assertEquals(Result.Success(3), localCall { 3 })
        assertEquals(Result.Error(DataError.Local.UNKNOWN), localCall<Int> { error("disk is full of something else") })
        assertFailsWith<CancellationException> { localCall<Int> { throw CancellationException("stopped") } }
    }
}
