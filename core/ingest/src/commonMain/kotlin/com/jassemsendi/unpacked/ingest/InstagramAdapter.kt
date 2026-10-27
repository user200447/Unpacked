package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.ingest.instagram.ExportOwner
import com.jassemsendi.unpacked.ingest.instagram.ExportOwners
import com.jassemsendi.unpacked.ingest.instagram.FlatRecord
import com.jassemsendi.unpacked.ingest.instagram.InstagramDecoder
import com.jassemsendi.unpacked.ingest.instagram.InstagramMapper
import com.jassemsendi.unpacked.ingest.instagram.InstagramMappers
import com.jassemsendi.unpacked.ingest.instagram.InstagramRecord
import com.jassemsendi.unpacked.ingest.instagram.MessageThreads
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Source

/**
 * Reads an Instagram "Download your information" Export in its JSON dialects: one mapper per
 * recognised record file, and message threads counted against the Export owner, who is read from
 * the Export's own profile before any file is parsed.
 */
public object InstagramAdapter : SourceAdapter {
    override val source: Source = Source.Instagram

    /** Why every message of a thread is dropped when the Export's profile names nobody. */
    public const val OWNER_UNKNOWN: String =
        "Export owner unknown: personal_information.json names no account, so sent cannot be told from received"

    override fun begin(export: Export): ExportReader = Reader(ExportOwners.of(export))

    private class Reader(
        private val owner: ExportOwner?,
    ) : ExportReader {
        override fun parse(
            file: ExportFile,
            text: String,
        ): FileOutcome =
            if (MessageThreads.isThreadFile(file.name)) MessageThreads.read(file, text, owner) else records(file, text)

        private fun records(
            file: ExportFile,
            text: String,
        ): FileOutcome {
            val mapper =
                InstagramMappers.forFile(file.name)
                    ?: return FileOutcome(
                        FileResult.Skipped(file.path, file.rawKind, "no mapper registered for ${file.name}"),
                    )
            return InstagramDecoder.decode(text).fold(
                onSuccess = { records -> map(file, records, mapper) },
                onFailure = { error ->
                    FileOutcome(FileResult.Failed(file.path, file.rawKind, error.message ?: "could not decode JSON"))
                },
            )
        }

        private fun map(
            file: ExportFile,
            records: List<InstagramRecord>,
            mapper: InstagramMapper,
        ): FileOutcome {
            var output = Output.NONE
            var dropped = 0
            records.forEachIndexed { index, record ->
                val flat = FlatRecord(record.fields, record.time, file.provenanceOf(index), file.rawKind, record.lists)
                val mapped = mapper.map(flat)
                if (mapped.isEmpty) dropped++ else output += mapped
            }
            val result =
                FileResult.Parsed(
                    path = file.path,
                    rawKind = file.rawKind,
                    recordCount = records.size,
                    eventCount = output.events.size,
                    claimCount = output.claims.size,
                    droppedCount = dropped,
                )
            return FileOutcome(result, output)
        }
    }
}
