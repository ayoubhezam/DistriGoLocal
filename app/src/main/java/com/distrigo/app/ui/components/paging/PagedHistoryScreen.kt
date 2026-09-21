package com.distrigo.app.ui.components.paging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import androidx.compose.ui.text.font.FontWeight
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.common.DsCompactSearchField

/** Room left under a list for the floating action drawn over it: the button, plus a gap. */
private val FloatingActionClearance = 88.dp

@Composable
fun <Filter, T : Any> PagedHistoryScreen(
    title: String,
    countLabel: String,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    filters: List<Filter>,
    selectedFilter: Filter,
    onFilterSelected: (Filter) -> Unit,
    filterLabel: (Filter) -> String,
    pagingItems: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    onBack: () -> Unit,
    accentColor: Color = DsColors.Primary,
    modifier: Modifier = Modifier,
    /**
     * The heading a row belongs under — a day, typically. Rows carrying the same one in a row are
     * gathered beneath it; null leaves the list flat, as the supplier history is.
     */
    groupLabel: ((T) -> String)? = null,
    /** Shown over the bottom-right of the list: the action this history is the place to take. */
    floatingAction: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {

        // One bar serves both paged history routes, so the count line rides in the subtitle slot
        // the hand-rolled header spent a second Text on.
        DsTopAppBar(
            title    = title,
            subtitle = countLabel,
            leading  = DsTopBarLeading.Back(onBack)
        )

        // The old header carried 8dp of its own bottom padding; the fixed-height bar does not, so
        // the gap above the search field is restored here.
        Spacer(modifier = Modifier.height(DsSpacing.sm))

        DsCompactSearchField(
            value         = query,
            onValueChange = onQueryChange,
            placeholder   = searchPlaceholder,
            modifier      = Modifier.padding(horizontal = DsSpacing.lg, vertical = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = 4.dp)
        ) {
            items(filters) { currentFilter ->
                FilterChip(
                    selected = currentFilter == selectedFilter,
                    onClick = { onFilterSelected(currentFilter) },
                    label = { Text(filterLabel(currentFilter)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val refreshState = pagingItems.loadState.refresh
        val appendState = pagingItems.loadState.append

        when {
            refreshState is LoadState.Loading && pagingItems.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            refreshState is LoadState.Error && pagingItems.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Erreur de chargement", fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                        Spacer(Modifier.height(DsSpacing.sm))
                        Button(
                            onClick = { pagingItems.retry() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) { Text("Réessayer", color = Color.White) }
                    }
                }
            }
            refreshState is LoadState.NotLoading && pagingItems.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextTertiary)
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text("Aucun résultat", fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                        Text(
                            "Essayez un autre mot-clé ou un autre filtre",
                            fontSize = DsTextSize.bodySmall,
                            color = DsColors.TextSecondary
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // A floating action sits over the list, so the list ends above it: without this
                    // the last entry is covered by the very button that is drawn on top of it.
                    contentPadding = PaddingValues(
                        start  = DsSpacing.lg,
                        end    = DsSpacing.lg,
                        top    = 4.dp,
                        bottom = if (floatingAction != null) FloatingActionClearance else 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { item -> itemKey(item) }
                    ) { index ->
                        val item = pagingItems[index]
                        if (item != null) {
                            // A heading whenever the group changes. `peek` reads the row above
                            // without asking paging to load around it, so scrolling up a page
                            // cannot be triggered by drawing a heading.
                            if (groupLabel != null) {
                                val label = groupLabel(item)
                                val previous = if (index == 0) null else pagingItems.peek(index - 1)?.let(groupLabel)
                                if (label != previous) {
                                    Text(
                                        text       = label,
                                        fontSize   = DsTextSize.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = DsColors.TextSecondary,
                                        modifier   = Modifier.padding(top = DsSpacing.sm, bottom = DsSpacing.xs)
                                    )
                                }
                            }
                            itemContent(item)
                        }
                    }
                    if (appendState is LoadState.Loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(DsSpacing.lg), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DsColors.Primary)
                            }
                        }
                    }
                    if (appendState is LoadState.Error) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { pagingItems.retry() }) {
                                    Text("Réessayer le chargement", color = DsColors.Primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (floatingAction != null) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(DsSpacing.lg)
        ) { floatingAction() }
    }
  }
}