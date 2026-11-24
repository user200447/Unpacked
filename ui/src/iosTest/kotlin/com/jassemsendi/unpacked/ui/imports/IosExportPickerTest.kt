package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.model.ExportLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import platform.Foundation.NSURL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iOS document picker binding, driven through a fake presenter standing in for UIKit. */
class IosExportPickerTest {
    private val presenter = FakeDocumentPickerPresenter()
    private val picker = IosExportPicker(presenter)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun offersAZipAndNoFolder() {
        assertTrue(picker.canPickZip)
        assertFalse(picker.canPickFolder)
    }

    @Test
    fun aPickedFileBecomesAZipLocationAtItsPath() =
        runTest {
            presenter.answer = NSURL.fileURLWithPath("/On My iPhone/instagram-me-2026-10-20.zip")

            assertEquals(ExportLocation.Zip("/On My iPhone/instagram-me-2026-10-20.zip"), picker.pickZip())
        }

    @Test
    fun aCancelledPickIsNull() =
        runTest {
            presenter.answer = null

            assertNull(picker.pickZip())
        }

    @Test
    fun aPickThatIsNotAFileIsNull() =
        runTest {
            presenter.answer = NSURL(string = "https://example.com/instagram-me-2026-10-20.zip")

            assertNull(picker.pickZip())
        }

    @Test
    fun aFolderCanNeverBePicked() =
        runTest {
            assertNull(picker.pickFolder())
            assertEquals(0, presenter.presented)
        }

    private class FakeDocumentPickerPresenter : DocumentPickerPresenter {
        var answer: NSURL? = null
        var presented = 0

        override fun pickZip(onPicked: (NSURL?) -> Unit) {
            presented++
            onPicked(answer)
        }
    }
}
