package com.distrigo.app.ui.mouvements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.StockMovement
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

data class MovementFilters(
    val dateFrom    : String? = null,   // "yyyy-MM-dd"
    val dateTo      : String? = null,
    val direction   : String? = null,   // null = "Tous" | "entree" | "sortie"
    val sourceLabel : String? = null
)

@Composable
fun MouvementsScreen(
    product   : Product,
    onBack    : () -> Unit,
    viewModel : StockMovementViewModel = viewModel()
) {
    var filters          by remember { mutableStateOf(MovementFilters()) }
    var showFilters       by remember { mutableStateOf(false) }
    var selectedMovement  by remember { mutableStateOf<StockMovement?>(null) }

    val movements by viewModel.movements.collectAsState()
    val sources    by viewModel.availableSources.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()

    LaunchedEffect(product.id) {
        viewModel.loadSourcesForProduct(product.id)
    }
    LaunchedEffect(product.id, filters) {
        viewModel.loadFilteredMovements(
            productId   = product.id,
            dateFrom    = filters.dateFrom,
            dateTo      = filters.dateTo,
            direction   = filters.direction,
            sourceLabel = filters.sourceLabel
        )
    }

    // ── Detail Sub-screen ──
    selectedMovement?.let { movement ->
        BackHandler { selectedMovement = null }
        MovementDetailView(
            movement = movement,
            onBack   = { selectedMovement = null }
        )
        return
    }

    // ── Filters Sub-screen ──
    if (showFilters) {
        BackHandler { showFilters = false }
        MovementFiltersView(
            initialFilters   = filters,
            availableSources = sources,
            onBack           = { showFilters = false },
            onApply          = { newFilters -> filters = newFilters; showFilters = false },
            onReset          = { filters = MovementFilters(); showFilters = false }
        )
        return
    }

    // ── Main List ──
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text(
                "Mouvements",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary,
                modifier   = Modifier.weight(1f)
            )
            IconButton(onClick = { showFilters = true }) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filtres",
                    tint = if (filters != MovementFilters()) DsColors.Primary else DsColors.TextSecondary
                )
            }
        }

        // ── Product row ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column {
                Text(product.name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                Text("Stock actuel : ${product.stock}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Stats ──
        val entrees = movements.filter { it.direction == "entree" }.sumOf { it.quantity }
        val sorties = movements.filter { it.direction == "sortie" }.sumOf { it.quantity }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            StatCard(modifier = Modifier.weight(1f), label = "Entrées",    value = "$entrees",        color = DsColors.Success)
            StatCard(modifier = Modifier.weight(1f), label = "Sorties",    value = "$sorties",         color = DsColors.Danger)
            StatCard(modifier = Modifier.weight(1f), label = "Mouvements", value = "${movements.size}", color = DsColors.Primary)
        }

        Spacer(Modifier.height(DsSpacing.md))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            movements.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(DsSpacing.md))
                        Text("Aucun mouvement", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> {
                val grouped = movements.groupBy { it.created_at.take(10) }
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    grouped.forEach { (date, dayMovements) ->
                        item(key = "header_$date") {
                            Text(
                                formatMovementDateLabel(date),
                                fontSize   = DsTextSize.caption,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(top = DsSpacing.sm, bottom = DsSpacing.xs)
                            )
                        }
                        items(dayMovements, key = { it.id }) { movement ->
                            MovementRow(movement = movement, onClick = { selectedMovement = movement })
                        }
                    }
                }
            }
        }
    }
}

// ── Stat card ──
@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceMuted)
            .padding(DsSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

// ── Movement row ──
@Composable
private fun MovementRow(movement: StockMovement, onClick: () -> Unit) {
    val isEntree = movement.direction == "entree"
    val (icon, typeLabel) = movementTypeDisplay(movement.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .clickable { onClick() }
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(DsShapes.medium)
                .background(if (isEntree) DsColors.SuccessLight else DsColors.DangerLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (isEntree) DsColors.Success else DsColors.Danger, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(movement.source_label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
        }
        Text(
            "${if (isEntree) "+" else "-"}${movement.quantity}",
            fontSize   = DsTextSize.bodyLarge,
            fontWeight = FontWeight.Bold,
            color      = if (isEntree) DsColors.Success else DsColors.Danger
        )
    }
}

// ── Helpers ──
fun movementTypeDisplay(type: String): Pair<ImageVector, String> = when (type) {
    "achat"      -> Icons.Default.Inventory to "Achat"
    "vente"      -> Icons.Default.PointOfSale to "Vente"
    "chargement" -> Icons.Default.LocalShipping to "Chargement"
    "perte"      -> Icons.Default.Warning to "Perte"
    "ajustement" -> Icons.Default.Tune to "Ajustement"
    else         -> Icons.Default.SwapVert to type
}

fun formatMovementDateLabel(date: String): String {
    val today     = java.time.LocalDate.now().toString()
    val yesterday = java.time.LocalDate.now().minusDays(1).toString()
    return when (date) {
        today     -> "Aujourd'hui"
        yesterday -> "Hier"
        else      -> date
    }
}

// ── Sub-screen 3 : Filtres ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementFiltersView(
    initialFilters   : MovementFilters,
    availableSources : List<String>,
    onBack           : () -> Unit,
    onApply          : (MovementFilters) -> Unit,
    onReset          : () -> Unit
) {
    var dateFrom    by remember { mutableStateOf(initialFilters.dateFrom ?: "") }
    var dateTo      by remember { mutableStateOf(initialFilters.dateTo ?: "") }
    var direction   by remember { mutableStateOf(initialFilters.direction) }
    var sourceLabel by remember { mutableStateOf(initialFilters.sourceLabel) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker   by remember { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }

    val dateFromState = rememberDatePickerState(
        initialSelectedDateMillis = dateFrom.takeIf { it.isNotEmpty() }?.let {
            runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
    )
    val dateToState = rememberDatePickerState(
        initialSelectedDateMillis = dateTo.takeIf { it.isNotEmpty() }?.let {
            runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
    )

    if (showDateFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateFromState.selectedDateMillis?.let { millis ->
                        dateFrom = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDateFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDateFromPicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = dateFromState) }
    }
    if (showDateToPicker) {
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateToState.selectedDateMillis?.let { millis ->
                        dateTo = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDateToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDateToPicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = dateToState) }
    }

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Mouvements", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        Column(
            modifier            = Modifier.weight(1f).fillMaxWidth().padding(horizontal = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            Text("Période", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)

            OutlinedTextField(
                value         = dateFrom,
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Du") },
                trailingIcon  = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                modifier      = Modifier.fillMaxWidth().clickable { showDateFromPicker = true },
                shape         = DsShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary,
                    disabledBorderColor  = DsColors.Border
                ),
                enabled = false
            )
            OutlinedTextField(
                value         = dateTo,
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Au") },
                trailingIcon  = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                modifier      = Modifier.fillMaxWidth().clickable { showDateToPicker = true },
                shape         = DsShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary,
                    disabledBorderColor  = DsColors.Border
                ),
                enabled = false
            )

            Text("Type de mouvement", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                FilterChipOption(label = "Tous",     selected = direction == null,       onClick = { direction = null })
                FilterChipOption(label = "Entrées",  selected = direction == "entree",   onClick = { direction = "entree" })
                FilterChipOption(label = "Sorties",  selected = direction == "sortie",   onClick = { direction = "sortie" })
            }

            Text("Source", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value         = sourceLabel ?: "Toutes les sources",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth().clickable { sourceMenuExpanded = true },
                    shape         = DsShapes.medium,
                    enabled       = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = DsColors.Border,
                        disabledTextColor   = DsColors.TextPrimary,
                        disabledTrailingIconColor = DsColors.TextSecondary
                    )
                )
                DropdownMenu(
                    expanded         = sourceMenuExpanded,
                    onDismissRequest = { sourceMenuExpanded = false },
                    modifier         = Modifier.fillMaxWidth(0.85f)
                ) {
                    DropdownMenuItem(text = { Text("Toutes les sources") }, onClick = { sourceLabel = null; sourceMenuExpanded = false })
                    availableSources.forEach { src ->
                        DropdownMenuItem(text = { Text(src) }, onClick = { sourceLabel = src; sourceMenuExpanded = false })
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg)) {
            Button(
                onClick  = {
                    onApply(
                        MovementFilters(
                            dateFrom    = dateFrom.ifEmpty { null },
                            dateTo      = dateTo.ifEmpty { null },
                            direction   = direction,
                            sourceLabel = sourceLabel
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                Text("Appliquer les filtres", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.height(DsSpacing.sm))
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Réinitialiser", color = DsColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun FilterChipOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(if (selected) DsColors.Primary else DsColors.SurfaceMuted)
            .clickable { onClick() }
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
    ) {
        Text(
            label,
            color      = if (selected) Color.White else DsColors.TextSecondary,
            fontSize   = DsTextSize.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Sub-screen 5 : Détail du mouvement ──
@Composable
private fun MovementDetailView(
    movement : StockMovement,
    onBack   : () -> Unit
) {
    val isEntree = movement.direction == "entree"
    val (_, typeLabel) = movementTypeDisplay(movement.type)

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Détail du mouvement", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── Type + badge ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(typeLabel, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Box(
                    modifier = Modifier
                        .clip(DsShapes.pill)
                        .background(if (isEntree) DsColors.SuccessLight else DsColors.DangerLight)
                        .padding(horizontal = DsSpacing.md, vertical = 4.dp)
                ) {
                    Text(
                        if (isEntree) "Entrée" else "Sortie",
                        color      = if (isEntree) DsColors.Success else DsColors.Danger,
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(formatMovementDateTime(movement.created_at), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)

            // ── Quantité ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceMuted)
                    .padding(DsSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Quantité", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                Text(
                    "${if (isEntree) "+" else "-"}${movement.quantity}",
                    fontSize   = DsTextSize.display,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isEntree) DsColors.Success else DsColors.Danger
                )
                Text("Pièces", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }

            // ── Infos ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .border(1.dp, DsColors.Border, DsShapes.large)
                    .padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                DetailInfoRow("Produit", movement.product_name)
                DetailInfoRow("Source", movement.source_label)
                DetailInfoRow("N° de source", "#${movement.source_id}")
                movement.unit_price?.let {
                    DetailInfoRow("Prix unitaire", "${"%.2f".format(it)} DA")
                }
                DetailInfoRow("Valeur totale", "${"%.2f".format(movement.total_value)} DA")
                DetailInfoRow("Utilisateur", movement.user_name ?: "—")
                DetailInfoRow("Notes", movement.note ?: "—")
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}

fun formatMovementDateTime(iso: String): String = runCatching {
    val instant = java.time.Instant.parse(iso)
    val local   = instant.atZone(java.time.ZoneId.systemDefault())
    "${local.toLocalDate()} à ${"%02d".format(local.hour)}:${"%02d".format(local.minute)}"
}.getOrDefault(iso)