package com.distrigo.app.ui.retours

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime

@Composable
fun RetourClientListScreen(
    client      : Client,
    viewModel   : RetourClientViewModel = hiltViewModel(),
    onBack      : () -> Unit,
    onAddRetour : () -> Unit = {}
) {
    val allRetours by viewModel.retours.collectAsState()
    val retours = allRetours.filter { it.client_id == client.id }

    var search   by remember { mutableStateOf("") }
    var selectedRetour by remember { mutableStateOf<RetourClient?>(null) }

    LaunchedEffect(Unit) { viewModel.loadRetours() }

    val filteredRetours = retours.filter { retour ->
        search.isBlank() ||
            retour.client_name.contains(search, ignoreCase = true) ||
            (retour.motif?.contains(search, ignoreCase = true) == true)
    }

    val totalProduits = retours.sumOf { it.items_count ?: 0 }

    BackHandler(enabled = selectedRetour != null) { selectedRetour = null }
    BackHandler(enabled = selectedRetour == null) { onBack() }

    AnimatedContent(
        targetState = selectedRetour,
        transitionSpec = {
            if (targetState != null && initialState == null) {
                (slideInVertically(animationSpec = tween(450, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight })
                    .togetherWith(ExitTransition.None)
            } else {
                EnterTransition.None
                    .togetherWith(slideOutVertically(animationSpec = tween(450, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight })
            }
        },
        label = "retour_client_detail_transition"
    ) { retour ->
        if (retour != null) {
            RetourClientDetailScreen(
                retourSummary = retour,
                viewModel     = viewModel,
                onBack        = { selectedRetour = null },
                onDeleted     = { selectedRetour = null; viewModel.loadRetours() }
            )
        } else {
            Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                        }
                        Spacer(Modifier.width(DsSpacing.xs))
                        Column {
                            Text("Retours", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text("Historique des retours effectués", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                    Box(
                        modifier         = Modifier.size(40.dp).clip(DsShapes.pill).background(DsColors.Primary).clickable { onAddRetour() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau retour", tint = Color.White)
                    }
                }

                // ── Stats banner ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg).clip(DsShapes.large)
                        .background(DsColors.Primary).padding(DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RetourClientStatColumn(value = "${retours.size}", label = "Retours")
                    RetourClientStatColumn(value = "$totalProduits", label = "Produits")
                    RetourClientStatColumn(value = "${"%,.0f".format(retours.sumOf { it.total })} DA", label = "Valeur totale")
                }

                Spacer(Modifier.height(DsSpacing.md))

                OutlinedTextField(
                    value         = search,
                    onValueChange = { search = it },
                    placeholder   = { Text("Rechercher…", fontSize = DsTextSize.body) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    shape         = DsShapes.large,
                    singleLine    = true,
                    colors        = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor = DsColors.Primary
                    )
                )

                Spacer(Modifier.height(DsSpacing.sm))

                if (filteredRetours.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(DsSpacing.sm))
                            Text("Aucun retour", color = DsColors.TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier            = Modifier.fillMaxWidth().weight(1f),
                        contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        items(filteredRetours, key = { it.id }) { retour ->
                            RetourRow(retour = retour, onClick = { selectedRetour = retour })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetourClientStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.8f))
    }
}

/** Shared row for one retour — used here and in ClientDetailScreen's inline preview. */
@Composable
fun RetourRow(retour: RetourClient, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape    = DsShapes.medium,
        color    = DsColors.SurfaceMuted
    ) {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Retour #${retour.id} · ${retour.client_name}", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1)
                Text(
                    "${formatOrderDate(retour.created_at)} · ${formatOrderTime(retour.created_at)}",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                )
                Text(
                    "${retour.items_count ?: 0} produit(s)",
                    fontSize = DsTextSize.caption, color = DsColors.TextTertiary
                )
            }
            Text("${"%,.2f".format(retour.total)} DA", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Danger)
        }
    }
}