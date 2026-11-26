package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.data.ImportUseCase
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.exportId
import com.jassemsendi.unpacked.model.ExportLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the platform does around a hosted Import: on Android, the foreground service and its progress notification. */
internal interface ImportHost {
    fun started(exportName: String)

    fun progress(
        exportName: String,
        filesDone: Int,
        filesTotal: Int,
    )

    fun finished()
}

/**
 * Runs the Import in the app's own [scope] rather than the collector's, so the run goes on when the screen that
 * started it is gone, and tells the [host] when it starts, how far it is and when it ends. Every step still reaches
 * the collector in order; a run that blows up fails the collector with the same error. Nothing starts until the
 * flow is collected, as with the use case it wraps. Runs are hosted one at a time, so a screen that comes back
 * and starts another Import while one is still running queues it rather than sharing the host; and the host is
 * best effort: a platform that refuses to host or to show progress never stops the run.
 */
internal class HostedImportUseCase(
    private val delegate: ImportUseCase,
    private val host: ImportHost,
    private val scope: CoroutineScope,
) : ImportUseCase {
    private val oneRunAtATime = Mutex()

    override fun run(location: ExportLocation): Flow<ImportStep> =
        flow {
            val steps = Channel<ImportStep>(Channel.UNLIMITED)
            scope.launch { oneRunAtATime.withLock { hostRun(location, steps) } }
            emitAll(steps.receiveAsFlow())
        }

    private suspend fun hostRun(
        location: ExportLocation,
        steps: SendChannel<ImportStep>,
    ) {
        val name = location.exportId.value
        bestEffort { host.started(name) }
        val outcome =
            runCatching {
                delegate.run(location).collect { step ->
                    val filesDone =
                        when (step) {
                            is ImportStep.FileStarted -> step.progress.index to step.progress.total
                            is ImportStep.FileFinished -> step.progress.index + 1 to step.progress.total
                            else -> null
                        }
                    filesDone?.let { (done, total) -> bestEffort { host.progress(name, done, total) } }
                    steps.send(step)
                }
            }
        bestEffort { host.finished() }
        steps.close(outcome.exceptionOrNull())
    }

    // Hosting is a courtesy to the user; what the platform refuses must not reach the run or the process.
    private inline fun bestEffort(call: () -> Unit) {
        runCatching(call)
    }
}
