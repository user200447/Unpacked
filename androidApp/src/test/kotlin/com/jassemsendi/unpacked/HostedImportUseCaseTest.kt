package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.data.ImportUseCase
import com.jassemsendi.unpacked.ingest.FileOutcome
import com.jassemsendi.unpacked.ingest.ImportProgress
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The Import runs in the app's own scope, told to the host as it goes, and outlives the screen that started it. */
@OptIn(ExperimentalCoroutinesApi::class)
class HostedImportUseCaseTest {
    private val location = ExportLocation.Zip("/content/authority/document/1/instagram-me-2026-10-20.zip")
    private val parsed = FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 4, 4, 0)
    private val steps =
        listOf(
            ImportStep.Started(ExportId("instagram-me-2026-10-20"), Source.Instagram, Inventory(2, 0, 0)),
            ImportStep.FileStarted(parsed.path, ImportProgress(0, 2)),
            ImportStep.FileFinished(FileOutcome(parsed), ImportProgress(0, 2)),
            ImportStep.FileStarted("other.json", ImportProgress(1, 2)),
            ImportStep.FileFinished(FileOutcome(parsed), ImportProgress(1, 2)),
        )
    private val host = FakeImportHost()

    @Test
    fun everyStepIsForwardedAndTheHostFollowsTheRunFromStartToFinish() =
        runTest {
            val delegate = ScriptedImportUseCase(steps)
            val useCase = HostedImportUseCase(delegate, host, backgroundScope)

            val collected = useCase.run(location).toList()

            assertEquals(steps, collected)
            assertEquals<List<ExportLocation>>(listOf(location), delegate.runs)
            val expectedCalls =
                listOf(
                    "started instagram-me-2026-10-20",
                    "progress 0/2",
                    "progress 1/2",
                    "progress 1/2",
                    "progress 2/2",
                    "finished",
                )
            assertEquals(expectedCalls, host.calls)
        }

    @Test
    fun aRunKeepsGoingAfterTheScreenStopsListening() =
        runTest {
            val delegate = ScriptedImportUseCase(steps)
            val useCase = HostedImportUseCase(delegate, host, backgroundScope)

            useCase.run(location).first()
            // advanceUntilIdle stops once only background work is left; the run is exactly that work.
            runCurrent()

            assertTrue(delegate.completed, "the delegate's run ended on its own")
            assertEquals("finished", host.calls.last())
        }

    @Test
    fun aRunThatBlowsUpFailsTheCollectorAndStillLeavesTheHost() =
        runTest {
            val delegate = ScriptedImportUseCase(steps.take(1), thenThrow = IllegalStateException("boom"))
            val useCase = HostedImportUseCase(delegate, host, backgroundScope)

            val error = assertFailsWith<IllegalStateException> { useCase.run(location).toList() }

            assertEquals("boom", error.message)
            assertEquals("finished", host.calls.last())
        }

    @Test
    fun twoRunsCollectedAtOnceAreHostedOneAfterTheOther() =
        runTest {
            val delegate = ScriptedImportUseCase(steps)
            val useCase = HostedImportUseCase(delegate, host, backgroundScope)
            val first = mutableListOf<ImportStep>()
            val second = mutableListOf<ImportStep>()

            val one = backgroundScope.launch { useCase.run(location).toList(first) }
            val other = backgroundScope.launch { useCase.run(location).toList(second) }
            runCurrent()

            assertTrue(one.isCompleted && other.isCompleted)
            assertEquals(steps, first)
            assertEquals(steps, second)
            val starts = host.calls.indices.filter { host.calls[it].startsWith("started") }
            val firstFinish = host.calls.indexOf("finished")
            assertEquals(2, starts.size)
            assertTrue(firstFinish < starts[1], "the second run is hosted only after the first has finished")
        }

    @Test
    fun aHostThatCannotStartOrFinishNeverStopsTheRun() =
        runTest {
            host.failing = true
            val delegate = ScriptedImportUseCase(steps)
            val useCase = HostedImportUseCase(delegate, host, backgroundScope)

            val collected = useCase.run(location).toList()

            assertEquals(steps, collected)
            assertTrue(delegate.completed)
        }

    @Test
    fun nothingHappensUntilTheRunIsCollected() =
        runTest {
            val delegate = ScriptedImportUseCase(steps)
            val useCase = HostedImportUseCase(delegate, host, backgroundScope)

            useCase.run(location)
            runCurrent()

            assertEquals<List<ExportLocation>>(emptyList(), delegate.runs)
            assertEquals<List<String>>(emptyList(), host.calls)
        }
}

private class FakeImportHost : ImportHost {
    val calls = mutableListOf<String>()

    /** The platform refuses every call, as Android does when a foreground service may not start. */
    var failing = false

    override fun started(exportName: String) {
        calls += "started $exportName"
        if (failing) error("foreground service not allowed")
    }

    override fun progress(
        exportName: String,
        filesDone: Int,
        filesTotal: Int,
    ) {
        calls += "progress $filesDone/$filesTotal"
        if (failing) error("notifications not allowed")
    }

    override fun finished() {
        calls += "finished"
        if (failing) error("service already gone")
    }
}

private class ScriptedImportUseCase(
    private val steps: List<ImportStep>,
    private val thenThrow: Throwable? = null,
) : ImportUseCase {
    val runs = mutableListOf<ExportLocation>()
    var completed = false

    override fun run(location: ExportLocation): Flow<ImportStep> =
        flow {
            runs += location
            steps.forEach {
                emit(it)
                yield()
            }
            thenThrow?.let { throw it }
            completed = true
        }
}
