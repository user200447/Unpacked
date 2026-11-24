package com.jassemsendi.unpacked.model

/**
 * A platform whose Export Unpacked can read. Instagram is the first; Google Takeout is named so that a
 * Match (ADR 0002) has a second Source to pair across and is tested as such, but it has no adapter yet,
 * so nothing imports it and no Entity of it exists until that adapter comes (spec story 59).
 */
public enum class Source(
    public val id: String,
) {
    Instagram("instagram"),
    GoogleTakeout("google"),
}
