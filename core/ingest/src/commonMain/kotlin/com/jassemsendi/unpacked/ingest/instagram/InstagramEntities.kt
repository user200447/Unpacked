package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Source

/** Which Entities one flattened record names. Rules compose with [plus]. */
internal typealias EntityRule = (fields: Map<String, String>) -> List<Entity>

internal operator fun EntityRule.plus(other: EntityRule): EntityRule = { fields -> this(fields) + other(fields) }

/** The `Owner` group Instagram nests under content: the author as an account, or the advertiser behind an ad. */
internal fun owner(
    kind: EntityKind,
    prefix: String = "Owner",
): EntityRule =
    { fields ->
        listOfNotNull(
            profile(
                kind,
                name = fields[fieldPath(prefix, "Name")],
                username = fields[fieldPath(prefix, "Username")],
                url = fields[fieldPath(prefix, "URL")],
            ),
        )
    }

/** A record that is itself about one account, with `Name`, `Username` and `URL` at the top level. */
internal fun profile(): EntityRule =
    { fields -> listOfNotNull(profile(EntityKind.Account, fields["Name"], fields["Username"], fields["URL"])) }

/**
 * An account named by its username in [usernameField], optionally with a display name in
 * [nameField] and its profile link in [urlField].
 */
internal fun account(
    usernameField: String,
    nameField: String? = null,
    urlField: String? = null,
): EntityRule =
    { fields ->
        listOfNotNull(
            profile(EntityKind.Account, nameField?.let(fields::get), fields[usernameField], urlField?.let(fields::get)),
        )
    }

/**
 * The hashtags on a piece of content, each a Topic. Flattening joined the `Hashtags` group's names
 * with ", ", which a hashtag can never contain.
 */
internal fun hashtags(): EntityRule =
    { fields ->
        fields[fieldPath("Hashtags", "Name")]?.split(", ").orEmpty().map { name ->
            Entity(Source.Instagram, EntityKind.Topic, Entity.normalizeKey(name), "#$name")
        }
    }

/** The device a login or logout came from, as Instagram names it: by the user agent string, kept as given. */
internal fun device(): EntityRule =
    { fields ->
        listOfNotNull(
            fields["User Agent"].orBlank()?.let { userAgent ->
                Entity(Source.Instagram, EntityKind.Device, Entity.normalizeKey(userAgent), userAgent)
            },
        )
    }

/**
 * Instagram usernames are case-insensitive, so the key is the lowercased username; a name that is
 * the only identifier is normalised like a display name (ADR 0002). The display name is kept as given.
 */
private fun profile(
    kind: EntityKind,
    name: String?,
    username: String?,
    url: String?,
): Entity? {
    val username = username.orBlank()
    val name = name.orBlank()
    val displayName = name ?: username ?: return null
    return Entity(
        source = Source.Instagram,
        kind = kind,
        key = username?.lowercase() ?: Entity.normalizeKey(displayName),
        displayName = displayName,
        attributes =
            buildMap {
                username?.let { put("username", it) }
                url.orBlank()?.let { put("url", it) }
            },
    )
}
