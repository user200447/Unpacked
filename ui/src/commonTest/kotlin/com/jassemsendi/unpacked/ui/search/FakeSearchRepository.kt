package com.jassemsendi.unpacked.ui.search

import com.jassemsendi.unpacked.data.SearchHit
import com.jassemsendi.unpacked.data.SearchRepository
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import kotlinx.coroutines.CompletableDeferred

private val wordBreak = Regex("[^\\p{L}\\p{N}]+")

/**
 * Searches the Events and Claims of the Home fakes the way the Room repository does: every typed word
 * must start some word of an attribute value or Entity name, newest first, undated Claims last. A test
 * that wants to see the state while a search is in flight holds [gate]; one that wants every search
 * refused sets [failure]. [searches] records every text the repository was asked for.
 */
class FakeSearchRepository(
    private val events: FakeEventRepository,
    private val claims: FakeClaimRepository,
) : SearchRepository {
    var gate: CompletableDeferred<Unit>? = null
    var failure: DataError.Local? = null
    val searches = mutableListOf<String>()

    override suspend fun search(
        text: String,
        limit: Int,
        offset: Int,
    ): Result<List<SearchHit>, DataError.Local> {
        searches += text
        gate?.await()
        val words = text.split(wordBreak).filter { it.isNotEmpty() }
        val hits =
            events.events.filter { it.mentions(words) }.map { SearchHit.OfEvent(it) } +
                claims.claims.filter { it.mentions(words) }.map { SearchHit.OfClaim(it) }
        val page =
            if (words.isEmpty()) {
                emptyList()
            } else {
                hits
                    .sortedWith(compareBy<SearchHit> { it.time == null }.thenByDescending { it.time })
                    .drop(offset)
                    .take(limit)
            }
        return failure?.let { Result.Error(it) } ?: Result.Success(page)
    }

    private fun Event.mentions(words: List<String>) = (attributes.values + entities.names()).mention(words)

    private fun Claim.mentions(words: List<String>) = (attributes.values + entities.names()).mention(words)

    private fun List<Entity>.names() = map { it.displayName }

    private fun List<String>.mention(words: List<String>): Boolean {
        val own = flatMap { it.split(wordBreak) }.filter { it.isNotEmpty() }
        return words.all { word -> own.any { it.startsWith(word, ignoreCase = true) } }
    }
}
