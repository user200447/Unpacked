package com.jassemsendi.unpacked.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp

/**
 * A list with the opened Record beside it on a wide window and over it on a narrow one. The list stays
 * composed underneath, so closing the Record returns to the same place in it; back closes the Record first.
 */
@Composable
internal fun RecordDetailLayout(
    detail: RecordDetailUi?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    list: @Composable (Modifier) -> Unit,
) {
    DetailLayout(
        detail = detail,
        onClose = onClose,
        modifier = modifier,
        pane = { opened, paneModifier -> RecordDetailPane(opened, onClose = onClose, modifier = paneModifier) },
        list = list,
    )
}

/**
 * A list with one opened thing beside it on a wide window and over it on a narrow one: a Record, or a file of the
 * Explorer. The list stays composed underneath, so closing returns to the same place in it; back closes first.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <T : Any> DetailLayout(
    detail: T?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pane: @Composable (T, Modifier) -> Unit,
    list: @Composable (Modifier) -> Unit,
) {
    BackHandler(enabled = detail != null, onBack = onClose)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= TWO_PANE_MIN_WIDTH
        Row(modifier = Modifier.fillMaxSize()) {
            list(Modifier.weight(weight = 1f))
            if (detail != null && sideBySide) {
                pane(detail, Modifier.width(DETAIL_PANE_WIDTH).fillMaxSize())
            }
        }
        if (detail != null && !sideBySide) {
            Surface(modifier = Modifier.fillMaxSize()) {
                pane(detail, Modifier.fillMaxSize())
            }
        }
    }
}

private val TWO_PANE_MIN_WIDTH = 840.dp
private val DETAIL_PANE_WIDTH = 360.dp
