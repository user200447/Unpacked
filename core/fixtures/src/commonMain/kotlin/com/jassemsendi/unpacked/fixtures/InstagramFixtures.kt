package com.jassemsendi.unpacked.fixtures

/**
 * Hand-written Instagram records in the `label_values` dialect, shaped exactly like the real
 * Export but with invented content. Shared by every ticket's tests.
 */
public object InstagramFixtures {
    public const val ADS_VIEWED_PATH: String = "ads_information/ads_and_topics/ads_viewed.json"
    public const val ADS_VIEWED_FILE: String = "ads_viewed.json"
    public const val POSTS_VIEWED_PATH: String = "ads_information/ads_and_topics/posts_viewed.json"

    /** A file no mapper knows: a real name some Exports carry, kept out of the registry so the Explorer path stays tested. */
    public const val UNMAPPED_PATH: String = "ads_information/instagram_ads_and_businesses/subscription_for_no_ads.json"

    public const val FIRST_AD_TIME_SECONDS: Long = 1_791_445_719L
    private const val ONE_MINUTE = 60L

    /** Three ad impressions: a plain one, one with hashtags, and one with no timestamp. */
    public val ADS_VIEWED_JSON: String =
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
            "timestamp": ${FIRST_AD_TIME_SECONDS + ONE_MINUTE},
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
            "fbid": "1:3:${FIRST_AD_TIME_SECONDS + ONE_MINUTE}::DYIImpressionsData"
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

    /** Two viewed posts: one by a named author with two hashtags, one whose author is unknown. */
    public val POSTS_VIEWED_JSON: String =
        """
        [
          {
            "timestamp": $FIRST_AD_TIME_SECONDS,
            "media": [],
            "label_values": [
              {"label": "URL", "value": "https://www.instagram.com/p/CCC/", "href": "https://www.instagram.com/p/CCC/"},
              {"label": "Caption", "value": "Match day #Football #explore"},
              {"label": "Title", "value": ""},
              {"dict": [
                {"dict": [{"label": "Name", "value": "Football"}], "title": ""},
                {"dict": [{"label": "Name", "value": "explore"}], "title": ""}
              ], "title": "Hashtags"},
              {"dict": [{"dict": [
                {"label": "URL", "value": ""},
                {"label": "Name", "value": "Example Player"},
                {"label": "Username", "value": "Example.Player"}
              ], "title": ""}], "title": "Owner"},
              {"dict": [], "title": "Brand partner"}
            ],
            "fbid": "1:5:$FIRST_AD_TIME_SECONDS"
          },
          {
            "timestamp": ${FIRST_AD_TIME_SECONDS + ONE_MINUTE},
            "media": [],
            "label_values": [
              {"label": "URL", "value": "https://www.instagram.com/p/DDD/", "href": "https://www.instagram.com/p/DDD/"},
              {"dict": [], "title": "Hashtags"},
              {"dict": [], "title": "Owner"}
            ],
            "fbid": "1:6:${FIRST_AD_TIME_SECONDS + ONE_MINUTE}"
          }
        ]
        """.trimIndent()

    public val UNMAPPED_JSON: String =
        """
        {
          "timestamp": $FIRST_AD_TIME_SECONDS,
          "media": [],
          "label_values": [{"label": "Subscription status", "value": "Not subscribed"}]
        }
        """.trimIndent()

    public const val MALFORMED_JSON: String = """[{"timestamp": 1791445719, "label_values": ["""
}
