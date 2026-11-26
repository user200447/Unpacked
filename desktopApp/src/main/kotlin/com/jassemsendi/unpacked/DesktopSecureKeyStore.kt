package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.agent.SecureKeyStore
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

/**
 * The desktop binding of [SecureKeyStore] (spec story 54, ADR 0008): the macOS Keychain where there is one, else a
 * file only the user's own account can read, in the app's data directory.
 */
fun desktopKeyStore(dataDirectory: File): SecureKeyStore =
    if (HostOs.isMac) MacKeychainKeyStore() else FileSecureKeyStore(dataDirectory.resolve("secrets"))

/**
 * Secrets in the macOS login Keychain, through the `security` tool every Mac ships, as generic passwords under the
 * app's service name. The value crosses to the tool as an argument, so it is briefly visible to other processes of
 * the same user while the tool runs; the Keychain itself is what protects it at rest.
 */
class MacKeychainKeyStore(
    private val service: String = SERVICE,
    private val run: (List<String>) -> CommandOutcome = ::runCommand,
) : SecureKeyStore {
    override val description: String = "the macOS Keychain"

    override suspend fun read(name: String): Result<String?, KeyStoreError> =
        withContext(Dispatchers.IO) {
            when (val outcome = run(listOf("security", "find-generic-password", "-a", name, "-s", service, "-w"))) {
                is CommandOutcome.Ran -> {
                    when (outcome.exitCode) {
                        0 -> Result.Success(outcome.output.trimEnd('\n'))
                        NOT_FOUND -> Result.Success(null)
                        else -> Result.Error(KeyStoreError.NOT_AVAILABLE)
                    }
                }

                CommandOutcome.NotRun -> {
                    Result.Error(KeyStoreError.NOT_AVAILABLE)
                }
            }
        }

    override suspend fun write(
        name: String,
        value: String,
    ): EmptyResult<KeyStoreError> =
        withContext(Dispatchers.IO) {
            run(listOf("security", "add-generic-password", "-U", "-a", name, "-s", service, "-w", value)).asEmpty()
        }

    override suspend fun remove(name: String): EmptyResult<KeyStoreError> =
        withContext(Dispatchers.IO) {
            run(listOf("security", "delete-generic-password", "-a", name, "-s", service)).asEmpty(NOT_FOUND)
        }

    private fun CommandOutcome.asEmpty(alsoFine: Int? = null): EmptyResult<KeyStoreError> =
        if (this is CommandOutcome.Ran && (exitCode == 0 || exitCode == alsoFine)) {
            Result.Success(Unit)
        } else {
            Result.Error(KeyStoreError.NOT_AVAILABLE)
        }

    companion object {
        const val SERVICE: String = "com.jassemsendi.unpacked"

        /** What `security` answers when no such item exists (errSecItemNotFound). */
        private const val NOT_FOUND = 44
    }
}

/** How a run of a command-line tool ended: with its exit code and output, or without starting. */
sealed interface CommandOutcome {
    data class Ran(
        val exitCode: Int,
        val output: String,
    ) : CommandOutcome

    data object NotRun : CommandOutcome
}

private fun runCommand(command: List<String>): CommandOutcome =
    try {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            CommandOutcome.NotRun
        } else {
            CommandOutcome.Ran(process.exitValue(), output)
        }
    } catch (_: IOException) {
        CommandOutcome.NotRun
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        CommandOutcome.NotRun
    }

private const val COMMAND_TIMEOUT_SECONDS = 30L

/**
 * Secrets as files only the user's account can read, for a desktop with no keychain the app can reach: one file per
 * secret in [directory], the directory and each file restricted to the owner where the file system can say so.
 */
class FileSecureKeyStore(
    private val directory: File,
) : SecureKeyStore {
    override val description: String = "a file only your user account can read, in ${directory.absolutePath}"

    override suspend fun read(name: String): Result<String?, KeyStoreError> =
        withContext(Dispatchers.IO) {
            val file = directory.resolve(name)
            try {
                Result.Success(if (file.isFile) file.readText() else null)
            } catch (_: IOException) {
                Result.Error(KeyStoreError.NOT_AVAILABLE)
            }
        }

    override suspend fun write(
        name: String,
        value: String,
    ): EmptyResult<KeyStoreError> =
        withContext(Dispatchers.IO) {
            try {
                directory.mkdirs()
                directory.ownerOnly(executable = true)
                val file = directory.resolve(name)
                file.writeText(value)
                file.ownerOnly(executable = false)
                Result.Success(Unit)
            } catch (_: IOException) {
                Result.Error(KeyStoreError.NOT_AVAILABLE)
            }
        }

    override suspend fun remove(name: String): EmptyResult<KeyStoreError> =
        withContext(Dispatchers.IO) {
            val file = directory.resolve(name)
            if (!file.exists() || file.delete()) Result.Success(Unit) else Result.Error(KeyStoreError.NOT_AVAILABLE)
        }

    private fun File.ownerOnly(executable: Boolean) {
        try {
            val permissions = mutableSetOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            if (executable) permissions += PosixFilePermission.OWNER_EXECUTE
            Files.setPosixFilePermissions(toPath(), permissions)
        } catch (_: UnsupportedOperationException) {
            // Windows: the java.io flags restrict to the owner as far as that API can.
            setReadable(false, false)
            setReadable(true, true)
            setWritable(false, false)
            setWritable(true, true)
        }
    }
}
