package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.ingest.Output
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Source

/** `ads_viewed.json`: one AdShown Event per impression, with the ad's owner as an Advertiser Entity. */
internal object AdsViewedMapper : InstagramMapper {
    private const val OWNER_NAME = "Owner / Name"
    private const val OWNER_USERNAME = "Owner / Username"
    private const val OWNER_URL = "Owner / URL"

    override fun map(record: FlatRecord): Output {
        val time = record.time ?: return Output.NONE
        val event =
            Event(
                kind = EventKind.AdShown,
                rawKind = record.rawKind,
                time = time,
                provenance = record.provenance,
                entities = listOfNotNull(advertiser(record.fields)),
                attributes = record.fields,
            )
        return Output(events = listOf(event))
    }

    /** Instagram usernames are case-insensitive, so the key is the lowercased username (ADR 0002). */
    private fun advertiser(fields: Map<String, String>): Entity? {
        val username = fields[OWNER_USERNAME]?.takeIf { it.isNotBlank() }
        val name = fields[OWNER_NAME]?.takeIf { it.isNotBlank() }
        val displayName = name ?: username ?: return null
        return Entity(
            source = Source.Instagram,
            kind = EntityKind.Advertiser,
            key = username?.lowercase() ?: Entity.normalizeKey(displayName),
            displayName = displayName,
            attributes =
                buildMap {
                    username?.let { put("username", it) }
                    fields[OWNER_URL]?.let { put("url", it) }
                },
        )
    }
}
