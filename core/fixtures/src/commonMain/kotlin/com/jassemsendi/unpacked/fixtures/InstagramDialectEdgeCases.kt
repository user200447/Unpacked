package com.jassemsendi.unpacked.fixtures

/**
 * One record of each Instagram dialect built from every shape the real Export mixes: nested `vec`
 * and `dict` groups, leaves that carry a timestamp or media instead of a value, and map entries
 * that are a value, a link, a timestamp or nothing at all.
 */
public object InstagramDialectEdgeCases {
    public const val TIME_SECONDS: Long = 1_791_445_719L
    public const val UPDATE_TIME_SECONDS: Long = 1_791_445_800L
    public const val TIME_ISO: String = "2026-10-08T07:48:39Z"
    public const val UPDATE_TIME_ISO: String = "2026-10-08T07:50:00Z"

    public val LABEL_VALUES_JSON: String =
        """
        [{
          "timestamp": $TIME_SECONDS,
          "media": [],
          "label_values": [
            {"label": "Survey responses", "vec": [
              {"dict": [{"label": "Question", "value": "Why?"}, {"label": "Answer", "value": "Because"}]},
              {"dict": [{"label": "Question", "value": "When?"}, {"label": "Answer", "value": "Now"}]}
            ], "description": "Your survey questions and responses"},
            {"label": "Update time", "timestamp_value": $UPDATE_TIME_SECONDS},
            {"label": "Expiration time", "timestamp_value": 0},
            {"label": "Media", "media": [{"uri": "media/posts/x.jpg", "creation_timestamp": $TIME_SECONDS}]},
            {"label": "Count", "value": "3"},
            {"title": "Owner", "dict": [{"title": "", "dict": [
              {"label": "URL", "value": "https://www.instagram.com/stories/outer/1", "href": "https://www.instagram.com/stories/outer/1"},
              {"title": "Owner", "dict": [{"title": "", "dict": [{"label": "Username", "value": "Nested.Owner"}]}]}
            ]}]}
          ],
          "fbid": "edge"
        }]
        """.trimIndent()

    public val STRING_DATA_JSON: String =
        """
        {"edge_cases": [{
          "title": "Edge",
          "media_list_data": [{"uri": "media/x.jpg"}],
          "string_list_data": [
            {"href": "https://a.example", "value": "a"},
            {"value": "b", "timestamp": ${TIME_SECONDS + 60}}
          ],
          "string_map_data": {
            "Count": {"href": "", "value": "42", "timestamp": 0},
            "Link": {"href": "https://l.example", "value": "", "timestamp": 0},
            "Blank": {"href": "", "value": "", "timestamp": 0},
            "When": {"timestamp": ${TIME_SECONDS * 1_000}}
          }
        }]}
        """.trimIndent()
}
