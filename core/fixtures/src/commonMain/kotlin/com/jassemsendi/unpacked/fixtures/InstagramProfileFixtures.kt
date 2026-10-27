package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.fixtures.InstagramJson.container
import com.jassemsendi.unpacked.fixtures.InstagramStringDataJson.entry
import com.jassemsendi.unpacked.fixtures.InstagramStringDataJson.stringMap

/**
 * The Export owner's own profile, as the personal-information file states it: the one file that
 * says whose Export this is. Shared by the Claim fixtures, which read it as ProfileField Claims,
 * and the message fixtures, which decide sent against received by the owner's name.
 */
public object InstagramProfileFixtures {
    public const val PERSONAL_INFORMATION_PATH: String =
        "personal_information/personal_information/personal_information.json"

    /** The owner's name as the Export writes it, mojibake and all: see [InstagramClaimFixtures.MOJIBAKE_NAME]. */
    public const val OWNER_NAME_AS_WRITTEN: String = InstagramClaimFixtures.MOJIBAKE_NAME

    /** The owner's name as it reads once repaired. */
    public const val OWNER_NAME: String = InstagramClaimFixtures.REPAIRED_NAME
    public const val OWNER_USERNAME: String = "example.owner"

    public val PERSONAL_INFORMATION_JSON: String =
        container(
            "profile_user",
            stringMap(
                "User Information",
                entry("Email", value = "owner@example.com"),
                entry("Username", value = OWNER_USERNAME),
                entry("Name", value = OWNER_NAME_AS_WRITTEN),
                entry("Bio"),
                entry("Gender", value = "female"),
                entry("Private Account", value = "True"),
            ),
        )
}
