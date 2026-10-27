package com.jassemsendi.unpacked.fixtures

/**
 * Hand-written Instagram records from the untimed files, shaped exactly like the real Export but
 * with invented content: the files that become Claims rather than Events.
 */
public object InstagramClaimFixtures {
    public const val ADVERTISERS_PATH: String =
        "ads_information/instagram_ads_and_businesses/advertisers_using_your_activity_or_information.json"
    public const val CONSENTS_PATH: String = "preferences/settings/consents.json"
    public const val SYNCED_CONTACTS_PATH: String = "connections/contacts/synced_contacts.json"
    public const val PROFILE_BASED_IN_PATH: String = "personal_information/information_about_you/profile_based_in.json"

    public const val CONSENT_TIME_SECONDS: Long = 1_560_978_845L
    public const val CONSENT_TIME_ISO: String = "2019-06-19T21:14:05Z"

    /** The sentence Instagram writes above its list of advertisers, and so the attribute each Claim carries. */
    public const val ADVERTISERS_LABEL: String =
        "Advertisers that have uploaded an audience list containing entries that were matched to your Instagram profile"
    public const val CONSENT_LABEL: String = "Consent for the processing of user's data by ad partners on Instagram"

    /** "Émilie Noël" as the real Export writes it: UTF-8 bytes read back as Latin-1, then JSON-escaped. */
    public const val MOJIBAKE_NAME: String = """Ã\u0089milie NoÃ«l"""
    public const val REPAIRED_NAME: String = "Émilie Noël"

    /** Already valid: characters beyond Latin-1 cannot be mojibake. */
    public const val ARABIC_NAME: String = "جاسم"

    /** Already valid, and the hard case: Latin-1 letters that do not form a UTF-8 sequence. */
    public const val LATIN1_NAME: String = "Zoë"

    /** One record listing three advertisers, one of them with a comma in its name. */
    public val ADVERTISERS_JSON: String =
        """
        {
          "media": [],
          "label_values": [
            {"label": "$ADVERTISERS_LABEL", "vec": [
              {"value": "Example Hotels"}, {"value": "Smith, Jones & Co"}, {"value": "Example Fashion"}
            ]}
          ]
        }
        """.trimIndent()

    /** One consent whose label is the whole statement, dated by the record and by an update time. */
    public val CONSENTS_JSON: String =
        """
        {
          "timestamp": $CONSENT_TIME_SECONDS,
          "media": [],
          "label_values": [
            {"label": "$CONSENT_LABEL"},
            {"label": "Update time", "timestamp_value": $CONSENT_TIME_SECONDS}
          ],
          "fbid": "17842155808504822"
        }
        """.trimIndent()

    /** Three synced contacts: a mis-decoded name, an Arabic name and a Latin-1 name. */
    public val SYNCED_CONTACTS_JSON: String =
        """
        {
          "contacts_contact_info": [
            {"string_map_data": {"First Name": {"value": "$MOJIBAKE_NAME"}, "Contact Information": {"value": "20022578"}}},
            {"string_map_data": {"First Name": {"value": "$ARABIC_NAME"}, "Contact Information": {"value": "44340762"}}},
            {"string_map_data": {"First Name": {"value": "$LATIN1_NAME"}, "Contact Information": {"value": "56930764"}}}
          ]
        }
        """.trimIndent()

    public val PROFILE_BASED_IN_JSON: String =
        """
        {
          "media": [],
          "label_values": [
            {"label": "Location", "dict": [
              {"label": "Country", "value": "Tunisia"},
              {"label": "Region", "value": "Sousse Governorate"},
              {"label": "City", "value": "Sousse"}
            ]}
          ],
          "fbid": "15171016821"
        }
        """.trimIndent()
}
