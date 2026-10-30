package com.jassemsendi.unpacked.ui.claims

import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordDetailUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRowUi

data class ClaimsState(
    /** True from the start until the Kind counts have been read, and again during a refresh. */
    val isLoading: Boolean = true,
    /** Claims in the whole library. */
    val totalCount: Int = 0,
    /** Every Kind with at least one Claim, in Kind order, each with its count: the groups the screen shows. */
    val kinds: List<KindCountUi<ClaimKind>> = emptyList(),
    /** The group opened to list its Claims, or null while every group is closed. */
    val expanded: ClaimKind? = null,
    /** The Claims of [expanded] read so far: dated ones newest first, then undated ones in file order. */
    val claims: List<RecordRowUi> = emptyList(),
    /** True while the repository holds Claims of [expanded] beyond [claims]. */
    val hasMore: Boolean = false,
    val isReadingNextPage: Boolean = false,
    /** The Record opened from the list, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last read failed, until the next one succeeds; what is listed is the last successful read. */
    val error: String? = null,
)

sealed interface ClaimsAction {
    /** Re-read the counts and the expanded group; the screen sends it whenever it appears, since Imports may have changed. */
    data object Refresh : ClaimsAction

    /** Open one group and list its Claims, or close them all with null. */
    data class Expand(
        val kind: ClaimKind?,
    ) : ClaimsAction

    /** Read the page after the last one listed. */
    data object NextPage : ClaimsAction

    data class Open(
        val ref: RecordRef,
    ) : ClaimsAction

    data object CloseDetail : ClaimsAction
}
