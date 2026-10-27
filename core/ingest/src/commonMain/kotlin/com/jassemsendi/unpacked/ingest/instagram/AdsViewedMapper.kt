package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Source

/** `ads_viewed.json`: one AdShown Event per impression, with the ad's owner as an Advertiser Entity. */
internal object AdsViewedMapper : InstagramMapper {
    override fun map(record: FlatRecord): Mapped {
        val time = record.time ?: return Mapped.NONE
        val event =
            Event(
                kind = EventKind.AdShown,
                rawKind = record.rawKind,
                time = time,
                provenance = record.provenance,
                entities = listOfNotNull(advertiser(record.fields)),
                attributes = record.fields,
            )
        return Mapped(events = listOf(event))
    }

    private fun advertiser(fields: Map<String, String>): Entity? {
        val username = fields["Owner / Username"]?.takeIf { it.isNotBlank() }
        val name = fields["Owner / Name"]?.takeIf { it.isNotBlank() }
        val displayName = name ?: username ?: return null
        return Entity(
            source = Source.Instagram,
            kind = EntityKind.Advertiser,
            key = username?.lowercase() ?: Entity.normalizeKey(displayName),
            displayName = displayName,
            attributes =
                buildMap {
                    username?.let { put("username", it) }
                    fields["Owner / URL"]?.let { put("url", it) }
                },
        )
    }
}
