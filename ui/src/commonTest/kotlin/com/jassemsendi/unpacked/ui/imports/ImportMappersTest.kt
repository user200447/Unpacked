package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.RawKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a file's result reads on the Import screen: its counts, and why records were dropped when the Import can say. */
class ImportMappersTest {
    private val path = "your_instagram_activity/messages/inbox/t_1/message_1.json"

    @Test
    fun aParsedFileDescribesItsCountsAndDroppedRecordsWithTheirReasonWhenThereIsOne() {
        assertEquals(
            FileResultUi(path, "4 records: 4 Events, 0 Claims"),
            FileResult.Parsed(path, RawKind("message_1"), 4, 4, 0).toUi(),
        )
        assertEquals(
            FileResultUi(path, "4 records: 3 Events, 0 Claims, 1 dropped"),
            FileResult.Parsed(path, RawKind("message_1"), 4, 3, 0, droppedCount = 1).toUi(),
        )
        val ownerUnknown =
            FileResult.Parsed(path, RawKind("message_1"), 4, 0, 0, droppedCount = 4, droppedReason = "owner unknown")
        assertEquals(FileResultUi(path, "4 records: 0 Events, 0 Claims, 4 dropped: owner unknown"), ownerUnknown.toUi())
    }
}
