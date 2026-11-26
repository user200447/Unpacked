package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop stores: a secret file nobody else can read, the Keychain tool driven through a fake, and the JDK preferences. */
class DesktopKeyStoresTest {
    private val directory: File = Files.createTempDirectory("unpacked-secrets").toFile()
    private val node = "com/jassemsendi/unpacked/test-${System.nanoTime()}"

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
        Preferences.userRoot().node(node).removeNode()
    }

    @Test
    fun aSecretFileIsWrittenReadBackAndRemoved() =
        runTest {
            val store = FileSecureKeyStore(directory)

            assertEquals(Result.Success(null), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.write("gemini", "AIza-example"))
            assertEquals(Result.Success("AIza-example"), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.remove("gemini"))
            assertEquals(Result.Success(null), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.remove("gemini"), "removing what is not there is fine")
        }

    @Test
    fun aSecretFileIsReadableByItsOwnerOnly() =
        runTest {
            FileSecureKeyStore(directory).write("gemini", "AIza-example")

            val permissions = Files.getPosixFilePermissions(directory.resolve("gemini").toPath())
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
            assertTrue(FileSecureKeyStore(directory).description.contains(directory.absolutePath))
        }

    @Test
    fun aDirectoryThatCannotBeCreatedIsNotAvailable() =
        runTest {
            val blocked = directory.resolve("file").apply { writeText("in the way") }

            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), FileSecureKeyStore(blocked).write("gemini", "x"))
        }

    @Test
    fun theKeychainToolIsDrivenWithTheAppServiceAndItsAnswersAreTyped() =
        runTest {
            val commands = mutableListOf<List<String>>()
            var exit = 0
            var output = ""
            val store =
                MacKeychainKeyStore { command ->
                    commands += command
                    CommandOutcome.Ran(exit, output)
                }

            output = "AIza-example\n"
            assertEquals(Result.Success("AIza-example"), store.read("gemini"))
            assertEquals(
                listOf("security", "find-generic-password", "-a", "gemini", "-s", MacKeychainKeyStore.SERVICE, "-w"),
                commands.last(),
            )

            exit = 44
            assertEquals(Result.Success(null), store.read("gemini"), "no such item is simply no key")
            assertEquals(Result.Success(Unit), store.remove("gemini"), "removing what is not there is fine")

            exit = 0
            assertEquals(Result.Success(Unit), store.write("gemini", "AIza-example"))
            assertTrue(commands.last().containsAll(listOf("add-generic-password", "-U", "AIza-example")))

            exit = 1
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.read("gemini"))
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.write("gemini", "x"))
        }

    @Test
    fun aKeychainToolThatCannotRunIsNotAvailable() =
        runTest {
            val store = MacKeychainKeyStore { CommandOutcome.NotRun }

            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.read("gemini"))
        }

    @Test
    fun thePlatformChoosesTheKeychainOnAMacAndAFileElsewhere() {
        val store = desktopKeyStore(directory)

        if (HostOs.isMac) {
            assertTrue(store is MacKeychainKeyStore)
        } else {
            assertTrue(store is FileSecureKeyStore)
        }
    }

    @Test
    fun aPreferenceIsRememberedAndRemoved() =
        runTest {
            val store = DesktopPreferenceStore(node)

            assertEquals(null, store.read("agent.provider"))
            store.write("agent.provider", "Cloud")
            assertEquals("Cloud", DesktopPreferenceStore(node).read("agent.provider"))
            store.write("agent.provider", null)
            assertFalse(
                Preferences
                    .userRoot()
                    .node(node)
                    .keys()
                    .contains("agent.provider"),
            )
        }
}
