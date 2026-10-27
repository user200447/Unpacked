package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.fixtures.InstagramJson.container
import com.jassemsendi.unpacked.fixtures.InstagramJson.dict
import com.jassemsendi.unpacked.fixtures.InstagramJson.labelOnly
import com.jassemsendi.unpacked.fixtures.InstagramJson.labelValues
import com.jassemsendi.unpacked.fixtures.InstagramJson.leaf
import com.jassemsendi.unpacked.fixtures.InstagramJson.list
import com.jassemsendi.unpacked.fixtures.InstagramJson.timed
import com.jassemsendi.unpacked.fixtures.InstagramJson.vec
import com.jassemsendi.unpacked.fixtures.InstagramRecords.contact
import com.jassemsendi.unpacked.fixtures.InstagramRecords.feedback
import com.jassemsendi.unpacked.fixtures.InstagramRecords.profileRecord
import com.jassemsendi.unpacked.fixtures.InstagramStringDataJson.entry
import com.jassemsendi.unpacked.fixtures.InstagramStringDataJson.stringMap

/**
 * The untimed files of the synthetic Export, one per file the Instagram adapter turns into Claims,
 * each with a handful of invented records shaped like the real Export. Counts are fixed so a test
 * can compute the expected Claims per Kind by hand: see the comment above each group.
 */
internal object SyntheticClaimFiles {
    private val player = Account("Example Player", "example.player")
    private val coach = Account("Example Coach", "Example.Coach")

    /** Every file by path, with its JSON text, given the instant the first dated record happens at. */
    fun all(firstTime: Long): Map<String, String> {
        val t = Ticker(firstTime)
        return advertisers() + interests(t) + locations() + reach() + consents(t) + profile(t) + contacts() + other(t)
    }

    // AdvertiserHoldsContact: 4 Claims from one record listing four advertisers
    private fun advertisers(): Map<String, String> =
        mapOf(
            "$ADS_BUSINESSES/advertisers_using_your_activity_or_information.json" to
                labelValues(
                    null,
                    vec(
                        "Advertisers that have uploaded an audience list containing entries that were matched " +
                            "to your Instagram profile",
                        listOf("Example Hotels", "Example Fashion", "Example Courier", "Smith, Jones & Co"),
                    ),
                ),
        )

    // InferredInterest: 3 + 1 + 2 + 1 + 1 = 8 Claims
    private fun interests(t: Ticker): Map<String, String> =
        mapOf(
            "preferences/your_topics/your_topics.json" to
                container(
                    "topics_your_topics",
                    stringMap(null, entry("Name", value = "Cooking")),
                    stringMap(null, entry("Name", value = "Football")),
                    stringMap(null, entry("Name", value = "Travel")),
                ),
            "preferences/your_topics/your_ads_see_more/see_less_topics.json" to
                list(labelValues(t.next(), timed("Update time", t.next()), """{"dict": [], "title": "Topics"}""")),
            "$ADS/posts_you're_interested_or_not_interested_in.json" to
                list(
                    feedback(t.next(), "See more", "Post", player),
                    feedback(t.next(), "See less", "Post", coach),
                ),
            "$ADS/posts_you're_not_interested_in.json" to list(feedback(t.next(), "", "Menu", coach)),
            "$ADS/profiles_you're_not_interested_in.json" to
                profileRecord(t.next(), Account("Rahma Example", "rahma_example")),
        )

    // InferredLocation: 3 + 1 + 1 = 5 Claims
    private fun locations(): Map<String, String> =
        mapOf(
            "$ABOUT_YOU/locations_of_interest.json" to
                labelValues(
                    null,
                    vec(
                        "Locations of interest",
                        listOf("Sousse, Sousse Governorate", "Tunis, Tunis Governorate", "Kasserine Governorate"),
                    ),
                    leaf("Usage explanation", "Locations of interest are used to personalise your experience."),
                ),
            "$ABOUT_YOU/profile_based_in.json" to
                labelValues(
                    null,
                    dict(
                        "Location",
                        leaf("Country", "Tunisia"),
                        leaf("Region", "Sousse Governorate"),
                        leaf("City", "Sousse"),
                    ),
                ),
            "$LOGIN/last_known_location.json" to
                container(
                    "account_history_imprecise_last_known_location",
                    stringMap(
                        "",
                        entry("Imprecise Latitude", value = "0"),
                        entry("Imprecise Longitude", value = "0"),
                        entry("GPS Time Uploaded"),
                    ),
                ),
        )

    // ReachCategory: 3 Claims from one record listing three categories
    private fun reach(): Map<String, String> =
        mapOf(
            "$ADS_BUSINESSES/other_categories_used_to_reach_you.json" to
                labelValues(null, vec("Name", listOf("Engaged Shoppers", "Frequent Travelers", "Birthday in October"))),
        )

    // ConsentGiven: 1 Claim
    private fun consents(t: Ticker): Map<String, String> {
        val time = t.next()
        return mapOf(
            "preferences/settings/consents.json" to
                labelValues(time, labelOnly(InstagramClaimFixtures.CONSENT_LABEL), timed("Update time", time)),
        )
    }

    // ProfileField: 1 + 1 + 1 + 1 = 4 Claims
    private fun profile(t: Ticker): Map<String, String> =
        mapOf(
            "$PERSONAL/personal_information.json" to
                container(
                    "profile_user",
                    stringMap(
                        "User Information",
                        entry("Email", value = "owner@example.com"),
                        entry("Username", value = "example.owner"),
                        entry("Name", value = InstagramClaimFixtures.MOJIBAKE_NAME),
                        entry("Bio"),
                        entry("Gender", value = "female"),
                        entry("Private Account", value = "True"),
                    ),
                ),
            "$LOGIN/signup_details.json" to
                container(
                    "account_history_registration_info",
                    stringMap(
                        "",
                        entry("Username", value = "Example Owner"),
                        entry("IP Address", value = "203.0.113.7"),
                        entry("Time", timestamp = t.next()),
                        entry("Email"),
                        entry("Phone Number", value = "+21600000000"),
                        entry("Device", value = "L-EMENT505"),
                    ),
                ),
            "$PERSONAL/instagram_profile_information.json" to
                labelValues(
                    null,
                    leaf("Contact Syncing", "True"),
                    leaf("First Country Code", "TN"),
                    timed("Last Login", t.next()),
                    labelOnly("Email modification method"),
                ),
            "$ABOUT_YOU/possible_phone_numbers.json" to
                labelValues(
                    t.next(),
                    leaf("Phone number", "+21600000001"),
                    labelOnly("Source"),
                    timed("Update time", t.next()),
                ),
        )

    // ContactSynced: 3 Claims
    private fun contacts(): Map<String, String> =
        mapOf(
            "connections/contacts/synced_contacts.json" to
                container(
                    "contacts_contact_info",
                    contact(InstagramClaimFixtures.MOJIBAKE_NAME, "20022578"),
                    contact(InstagramClaimFixtures.ARABIC_NAME, "44340762"),
                    contact(InstagramClaimFixtures.LATIN1_NAME, "56930764"),
                ),
        )

    // Other: 1 + 1 + 1 + 1 + 1 + 1 = 6 Claims
    private fun other(t: Ticker): Map<String, String> =
        mapOf(
            "$ADS_BUSINESSES/ad_preferences.json" to
                labelValues(
                    t.next(),
                    """{"label": "Removed categories", "vec": []}""",
                    leaf("Is opted out of third party data at the user level", "False"),
                    labelOnly("Received Global Privacy Control (GPC) enable signal from browser settings"),
                ),
            "apps_and_websites_off_of_instagram/apps_and_websites/your_activity_off_meta_technologies_settings.json" to
                labelValues(
                    t.next(),
                    leaf("Profile association state", "Account association enabled"),
                    timed("Latest last activity time", 0),
                    leaf("Number of times you used clear history settings", "0"),
                ),
            "preferences/settings/notification_preferences.json" to
                labelValues(
                    t.next(),
                    dict(
                        "Preferences",
                        leaf("Channel", "Push notifications"),
                        leaf("Notification type", "Recently uploaded reels"),
                        leaf("Value", "From everyone"),
                    ),
                    timed("Update time", t.next()),
                ),
            "logged_information/past_instagram_insights/audience_insights.json" to
                container(
                    "organic_insights_audience",
                    stringMap("", entry("Date Range", value = "Jul 16 - Oct 13"), entry("Followers", value = "208")),
                ),
            "your_instagram_activity/monetization/eligibility.json" to
                container(
                    "monetization_eligibility",
                    stringMap(
                        "",
                        entry("Product Name", value = "BRANDED CONTENT"),
                        entry("Decision", value = "Eligible"),
                        entry("Reason"),
                    ),
                ),
            "your_instagram_activity/shopping/recently_viewed_items.json" to
                labelValues(null, vec("Product names", listOf("Example sneaker"))),
        )

    private const val ADS = "ads_information/ads_and_topics"
    private const val ADS_BUSINESSES = "ads_information/instagram_ads_and_businesses"
    private const val ABOUT_YOU = "personal_information/information_about_you"
    private const val PERSONAL = "personal_information/personal_information"
    private const val LOGIN = "security_and_login_information/login_and_profile_creation"
}
