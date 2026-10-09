package com.distrigo.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.distrigo.app.ui.designsystem.DsColors
import kotlinx.coroutines.launch

/**
 * A list's filter sheet that can show a [SearchableSelectList] in its own place — Client, Fournisseur —
 * while [listShown], rather than opening a second sheet over itself (a second window: see
 * [SearchableSelectList]).
 *
 * Back is the sheet's own here: from the list, back to the filters ([onCloseList]); from the filters,
 * the sheet slides away and [onDismiss] runs. Material's sheet would take Back first on Android 13 and
 * later — its window callback outranks any handler in its content — and close the whole sheet from the
 * list, so its own Back is turned off ([ModalBottomSheetProperties.shouldDismissOnBackPress]). That also
 * turns off the sheet's predictive-back preview; a tap outside or a swipe down still closes it as before.
 *
 * Fully expanded, with no half-way stop: the content changes height as the list replaces the filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    onDismiss: () -> Unit,
    listShown: Boolean,
    onCloseList: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = DsColors.Surface,
        properties       = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler {
            if (listShown) onCloseList()
            else scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
        }
        content()
    }
}
