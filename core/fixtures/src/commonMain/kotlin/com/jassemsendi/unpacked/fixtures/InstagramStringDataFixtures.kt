package com.jassemsendi.unpacked.fixtures

/**
 * Hand-written Instagram records in the `string_list_data` / `string_map_data` dialect, shaped
 * exactly like the real Export but with invented content.
 */
public object InstagramStringDataFixtures {
    public const val FOLLOWERS_PATH: String = "connections/followers_and_following/followers_1.json"
    public const val FOLLOWING_PATH: String = "connections/followers_and_following/following.json"

    public const val LOGIN_ACTIVITY_PATH: String =
        "security_and_login_information/login_and_profile_creation/login_activity.json"

    public const val LOGOUT_ACTIVITY_PATH: String =
        "security_and_login_information/login_and_profile_creation/logout_activity.json"

    public const val FIRST_FOLLOW_TIME_SECONDS: Long = 1_790_061_723L
    public const val FIRST_LOGIN_TIME_SECONDS: Long = 1_791_463_420L
    public const val PHONE_USER_AGENT: String =
        "Instagram 450.0.0.50.77 Android (34/14; 420dpi; 1080x2199; samsung; SM-A135F; a13; mt6768; en_US; 123)"
    public const val LAPTOP_USER_AGENT: String =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0"

    /** A top-level list: three followers, the second with a display name as title, the third undated. */
    public val FOLLOWERS_JSON: String =
        """
        [
          {
            "title": "",
            "media_list_data": [],
            "string_list_data": [
              {"href": "https://www.instagram.com/Amira_Ayari", "value": "Amira_Ayari", "timestamp": $FIRST_FOLLOW_TIME_SECONDS}
            ]
          },
          {
            "title": "Basma Example",
            "media_list_data": [],
            "string_list_data": [
              {"href": "https://www.threads.com/basma.example", "value": "basma.example", "timestamp": ${FIRST_FOLLOW_TIME_SECONDS + 60}}
            ]
          },
          {
            "title": "",
            "media_list_data": [],
            "string_list_data": [
              {"href": "https://www.instagram.com/nobody", "value": "nobody", "timestamp": 0}
            ]
          }
        ]
        """.trimIndent()

    /** A container object with one key: two accounts the owner follows, named by title only. */
    public val FOLLOWING_JSON: String =
        """
        {
          "relationships_following": [
            {"title": "Myriem.Example", "string_list_data": [{"href": "https://www.instagram.com/_u/myriem.example", "timestamp": ${FIRST_FOLLOW_TIME_SECONDS + 120}}]},
            {"title": "aziz_example", "string_list_data": [{"href": "https://www.instagram.com/_u/aziz_example", "timestamp": ${FIRST_FOLLOW_TIME_SECONDS + 180}}]}
          ]
        }
        """.trimIndent()

    /** Three logins from two devices; only the `Time` entry is dated, the others carry a zero timestamp. */
    public val LOGIN_ACTIVITY_JSON: String =
        """
        {
          "account_history_login_history": [
            ${accountHistory(FIRST_LOGIN_TIME_SECONDS, PHONE_USER_AGENT)},
            ${accountHistory(FIRST_LOGIN_TIME_SECONDS + 3_600, LAPTOP_USER_AGENT)},
            ${accountHistory(FIRST_LOGIN_TIME_SECONDS + 7_200, PHONE_USER_AGENT)}
          ]
        }
        """.trimIndent()

    /** One logout at the first login's instant, but written in epoch milliseconds as some files are. */
    public val LOGOUT_ACTIVITY_JSON: String =
        """
        {
          "account_history_logout_history": [
            ${accountHistory(FIRST_LOGIN_TIME_SECONDS * 1_000, LAPTOP_USER_AGENT)}
          ]
        }
        """.trimIndent()

    private fun accountHistory(
        timestamp: Long,
        userAgent: String,
    ): String =
        """
        {
          "title": "",
          "media_map_data": {},
          "string_map_data": {
            "Cookie Name": {"href": "", "value": "*************************ese", "timestamp": 0},
            "IP Address": {"href": "", "value": "203.0.113.7", "timestamp": 0},
            "Port": {"href": "", "value": "33068", "timestamp": 0},
            "Language Code": {"href": "", "value": "en", "timestamp": 0},
            "Time": {"href": "", "value": "", "timestamp": $timestamp},
            "User Agent": {"href": "", "value": "$userAgent", "timestamp": 0}
          }
        }
        """.trimIndent()
}
