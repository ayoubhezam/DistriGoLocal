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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors

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
    itemContent: @Composable (T) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().background(DsColors.Surface)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Column(modifier = Modifier.padding(start = 2.dp)) {
                Text(title, fontSize = DsTextSize.title, color = DsColors.TextPrimary)
                Text(countLabel, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(searchPlaceholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = DsShapes.medium,
            colors = dsTextFieldColors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor = DsColors.Primary
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = 6.dp)
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
                    contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { item -> itemKey(item) }
                    ) { index ->
                        val item = pagingItems[index]
                        if (item != null) itemContent(item)
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
}