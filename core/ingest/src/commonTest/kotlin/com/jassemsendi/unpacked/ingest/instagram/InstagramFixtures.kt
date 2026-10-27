package com.jassemsendi.unpacked.ingest.instagram

/**
 * Hand-written Instagram records in the `label_values` dialect, shaped exactly like the real
 * Export but with invented content. Shared by every ticket's tests.
 */
internal object InstagramFixtures {
    const val ADS_VIEWED_PATH = "ads_information/ads_and_topics/ads_viewed.json"
    const val ADS_VIEWED_FILE = "ads_viewed.json"

    /** A single-object `label_values` file with a `vec` list; no mapper registers it in ticket #3. */
    const val UNMAPPED_PATH =
        "ads_information/instagram_ads_and_businesses/advertisers_using_your_activity_or_information.json"

    const val FIRST_AD_TIME_SECONDS = 1_791_445_719L

    /** Three ad impressions: a plain one, one with hashtags, and one with no timestamp. */
    val ADS_VIEWED_JSON =
        """
        [
          {
            "timestamp": $FIRST_AD_TIME_SECONDS,
            "media": [],
            "label_values": [
              {"label": "Ad library public URL", "value": "https://www.facebook.com/ads/library/?id=1", "href": "https://www.facebook.com/ads/library/?id=1"},
              {"label": "Brand partner", "vec": []},
              {"label": "Brand partner's profile", "vec": []},
              {"label": "URL", "value": "https://www.instagram.com/p/AAA/", "href": "https://www.instagram.com/p/AAA/"},
              {"label": "Caption", "value": "Sign up and come to the match"},
              {"label": "Title", "value": ""},
              {"dict": [], "title": "Hashtags"},
              {"dict": [{"dict": [
                {"label": "URL", "value": "http://example.com/club"},
                {"label": "Name", "value": "Example Club"},
                {"label": "Username", "value": "exampleclub"}
              ], "title": ""}], "title": "Owner"},
              {"dict": [], "title": "Brand partner"}
            ],
            "fbid": "1:2:$FIRST_AD_TIME_SECONDS::DYIImpressionsData"
          },
          {
            "timestamp": ${FIRST_AD_TIME_SECONDS + 60},
            "media": [],
            "label_values": [
              {"label": "URL", "value": "https://www.instagram.com/p/BBB/", "href": "https://www.instagram.com/p/BBB/"},
              {"label": "Caption", "value": "Ship anything #logistics #growth"},
              {"dict": [
                {"dict": [{"label": "Name", "value": "logistics"}], "title": ""},
                {"dict": [{"label": "Name", "value": "growth"}], "title": ""}
              ], "title": "Hashtags"},
              {"dict": [{"dict": [
                {"label": "URL", "value": "http://example.com/courier"},
                {"label": "Name", "value": "Example Courier"},
                {"label": "Username", "value": "ExampleCourier"}
              ], "title": ""}], "title": "Owner"}
            ],
            "fbid": "1:3:${FIRST_AD_TIME_SECONDS + 60}::DYIImpressionsData"
          },
          {
            "timestamp": 0,
            "media": [],
            "label_values": [
              {"label": "Caption", "value": "An undated impression"},
              {"dict": [{"dict": [{"label": "Name", "value": "Nobody Knows"}], "title": ""}], "title": "Owner"}
            ],
            "fbid": "1:4:0::DYIImpressionsData"
          }
        ]
        """.trimIndent()

    val ADVERTISERS_JSON =
        """
        {
          "media": [],
          "label_values": [
            {"label": "Advertisers", "vec": [{"value": "Example Hotels"}, {"value": "Example Fashion"}]}
          ]
        }
        """.trimIndent()

    const val MALFORMED_JSON = """[{"timestamp": 1791445719, "label_values": ["""
}
