package com.jassemsendi.unpacked.fixtures

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

/**
 * Builds a small Instagram Export in memory with the real folder layout: a deterministic
 * ads-viewed file, an unmapped file, media that must never be opened, and optionally the timed
 * files of every other recognised name ([TIMED_FILE_PATHS]), the untimed files that become Claims
 * ([CLAIM_FILE_PATHS]), a malformed ads-viewed file, and non-JSON non-media files such as the
 * HTML notes a real Export carries.
 * Every later ticket adds files to it rather than inventing new fixtures.
 */
public class SyntheticInstagramExport(
    public val adsViewed: Int = 5,
    public val malformedAdsViewed: Boolean = false,
    public val includeUnmappedFile: Boolean = true,
    public val includeTimedFiles: Boolean = false,
    public val includeClaimFiles: Boolean = false,
    public val mediaFiles: Int = 3,
    public val otherFiles: Int = 0,
    seed: Int = 42,
) {
    public data class Ad(
        val name: String,
        val username: String,
        val timestampSeconds: Long,
        val caption: String,
    )

    public val ads: List<Ad>

    init {
        val random = Random(seed)
        ads =
            List(adsViewed) { index ->
                val advertiser = ADVERTISERS[random.nextInt(ADVERTISERS.size)]
                Ad(
                    name = advertiser.first,
                    username = advertiser.second,
                    timestampSeconds = FIRST_TIME + index * HOUR + random.nextInt(HOUR.toInt()),
                    caption = "Synthetic ad $index from ${advertiser.first}",
                )
            }
    }

    public fun writeTo(
        fileSystem: FileSystem,
        root: Path,
    ) {
        val adsJson = if (malformedAdsViewed) InstagramFixtures.MALFORMED_JSON else adsViewedJson()
        fileSystem.writeText(root / InstagramFixtures.ADS_VIEWED_PATH.toPath(), adsJson)
        if (includeUnmappedFile) {
            fileSystem.writeText(root / InstagramFixtures.UNMAPPED_PATH.toPath(), InstagramFixtures.UNMAPPED_JSON)
        }
        if (includeTimedFiles) {
            SyntheticTimedFiles.all(TIMED_FILES_FIRST_TIME).forEach { (path, json) ->
                fileSystem.writeText(root / path.toPath(), json)
            }
        }
        if (includeClaimFiles) {
            SyntheticClaimFiles.all(CLAIM_FILES_FIRST_TIME).forEach { (path, json) ->
                fileSystem.writeText(root / path.toPath(), json)
            }
        }
        repeat(mediaFiles) { index ->
            val extension = MEDIA_EXTENSIONS[index % MEDIA_EXTENSIONS.size]
            fileSystem.writeText(root / "media/posts/202610/synthetic_$index.$extension".toPath(), "not really media")
        }
        repeat(otherFiles) { index ->
            fileSystem.writeText(root / "personal_information/notes_$index.txt".toPath(), "plain text, not records")
        }
    }

    public fun adsViewedJson(): String =
        ads.joinToString(prefix = "[", postfix = "]", separator = ",") { ad -> ad.toJson() }

    private fun Ad.toJson(): String =
        """
        {
          "timestamp": $timestampSeconds,
          "media": [],
          "label_values": [
            {"label": "URL", "value": "https://www.instagram.com/p/$username/", "href": "https://www.instagram.com/p/$username/"},
            {"label": "Caption", "value": "$caption"},
            {"label": "Title", "value": ""},
            {"dict": [], "title": "Hashtags"},
            {"dict": [{"dict": [
              {"label": "Name", "value": "$name"},
              {"label": "Username", "value": "$username"}
            ], "title": ""}], "title": "Owner"}
          ],
          "fbid": "1:$timestampSeconds::DYIImpressionsData"
        }
        """.trimIndent()

    public companion object {
        public const val FIRST_TIME: Long = 1_790_000_000L

        /** The timed files start a day after the first ad, one minute apart. */
        public const val TIMED_FILES_FIRST_TIME: Long = FIRST_TIME + 24 * 3_600L

        /** The dated Claims are "as of" two days after the first ad, one minute apart. */
        public const val CLAIM_FILES_FIRST_TIME: Long = FIRST_TIME + 2 * 24 * 3_600L

        /** The paths of the timed files written when `includeTimedFiles` is set. */
        public val TIMED_FILE_PATHS: List<String> = SyntheticTimedFiles.all(TIMED_FILES_FIRST_TIME).keys.toList()

        /** The paths of the untimed files written when `includeClaimFiles` is set. */
        public val CLAIM_FILE_PATHS: List<String> = SyntheticClaimFiles.all(CLAIM_FILES_FIRST_TIME).keys.toList()
        private const val HOUR = 3_600L
        private val ADVERTISERS =
            listOf(
                "Example Club" to "exampleclub",
                "Example Courier" to "examplecourier",
                "Example Hotels" to "examplehotels",
                "Example Fashion" to "examplefashion",
            )
        private val MEDIA_EXTENSIONS = listOf("jpg", "mp4", "png", "aac")
    }
}
