package com.distrigo.app.ui.chargements

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel

data class ChargementCartItem(
    val product      : Product,
    val targetCamion : Int  // desired final quantity in camion (not a delta)
)

@Composable
fun ChargementFormScreen(
    onBack            : () -> Unit,
    onSaved           : () -> Unit,
    viewModel         : ChargementViewModel = viewModel(),
    productViewModel  : ProductViewModel    = viewModel(),
    correctionSource  : com.distrigo.app.data.model.Chargement? = null
) {
    val products by productViewModel.products.collectAsState()

    var search     by remember { mutableStateOf("") }
    var cartItems  by remember { mutableStateOf<List<ChargementCartItem>>(emptyList()) }
    var note       by remember { mutableStateOf("") }
    var userName   by remember { mutableStateOf("") }
    var isSaving   by remember { mutableStateOf(false) }
    var showCart   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { productViewModel.loadProducts() }

    LaunchedEffect(correctionSource, products) {
        if (correctionSource != null && products.isNotEmpty() && cartItems.isEmpty()) {
            cartItems = correctionSource.items?.mapNotNull { item ->
                val product = products.find { it.id == item.product_id }
                product?.let {
                    ChargementCartItem(
                        product      = it,
                        targetCamion = it.camion_stock
                    )
                }
            } ?: emptyList()
            note = "Correction du mouvement #${correctionSource.id}"
        }
    }

    val filteredProducts = products.filter { product ->
        search.isBlank() ||
                product.name.contains(search, ignoreCase = true) ||
                (product.barcode?.contains(search, ignoreCase = true) == true)
    }

    fun save() {
        isSaving = true
        val items = cartItems.mapNotNull { ci ->
            val delta = ci.targetCamion - ci.product.camion_stock
            if (delta == 0) return@mapNotNull null
            val direction = if (delta > 0) "vers_camion" else "vers_depot"
            mapOf(
                "product_id" to ci.product.id,
                "quantity"   to kotlin.math.abs(delta),
                "direction"  to direction
            )
        }
        if (items.isEmpty()) {
            isSaving = false
            return
        }
        viewModel.createChargement(
            note      = note.trim().ifEmpty { null },
            userName  = userName.trim().ifEmpty { null },
            items     = items,
            onSuccess = {
                productViewModel.loadProducts()
                onSaved()
            },
            onError   = { isSaving = false }
        )
    }

    // ── Cart Sub-screen ─────────────────────────────────────────────────────
    if (showCart) {
        BackHandler { showCart = false }
        Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCart = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                    Column {
                        Text("Ma sélection", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        Text(
                            "${cartItems.size} article(s)",
                            fontSize = DsTextSize.caption,
                            color    = DsColors.TextSecondary
                        )
                    }
                }
                if (cartItems.isNotEmpty()) {
                    TextButton(onClick = { cartItems = emptyList() }) {
                        Text("Vider", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            }

            if (cartItems.isEmpty()) {
                Box(
                    modifier         = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint     = DsColors.TextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                        Text("Sélection vide", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(DsSpacing.sm))
                        Button(
                            onClick = { showCart = false },
                            shape   = DsShapes.medium,
                            colors  = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) { Text("Parcourir les produits") }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                    modifier            = Modifier.weight(1f)
                ) {
                    items(cartItems, key = { "cart_${it.product.id}" }) { item ->
                        ChargementCartRow(
                            item             = item,
                            onQuantityChange = { newTarget ->
                                cartItems = cartItems.map {
                                    if (it.product.id == item.product.id) it.copy(targetCamion = newTarget.coerceAtLeast(0)) else it
                                }
                            },
                            onRemove = {
                                cartItems = cartItems.filter { it.product.id != item.product.id }
                            }
                        )
                    }

                    item {
                        OutlinedTextField(
                            value         = userName,
                            onValueChange = { userName = it },
                            placeholder   = { Text("Effectué par (optionnel)", fontSize = DsTextSize.body) },
                            leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = DsShapes.medium,
                            singleLine    = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = DsColors.Border,
                                focusedBorderColor   = DsColors.Primary
                            )
                        )
                    }

                    item {
                        OutlinedTextField(
                            value         = note,
                            onValueChange = { note = it },
                            placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = DsShapes.medium,
                            minLines      = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = DsColors.Border,
                                focusedBorderColor   = DsColors.Primary
                            )
                        )
                    }
                }

                // ── Save button (always visible, no scrolling needed) ──
                Button(
                    onClick  = { save() },
                    enabled  = cartItems.isNotEmpty() && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                        .height(52.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            "Enregistrer le mouvement",
                            fontSize   = DsTextSize.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White
                        )
                    }
                }
            }
        }
        return
    }

    // ── Main Screen ─────────────────────────────────────────────────────────
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Column {
                Text(
                    if (correctionSource != null) "Correction du mouvement #${correctionSource.id}" else "Chargement / Déchargement",
                    fontSize   = DsTextSize.title,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.TextPrimary
                )
                Text(
                    if (cartItems.isNotEmpty()) "${cartItems.size} article(s)" else "Sélectionnez des produits",
                    fontSize = DsTextSize.caption,
                    color    = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextSecondary
                )
            }
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── Search ──
            item {
                OutlinedTextField(
                    value         = search,
                    onValueChange = { search = it },
                    placeholder   = { Text("Rechercher un produit…", fontSize = DsTextSize.body) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = DsShapes.large,
                    singleLine    = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }

            // ── Product list ──
            items(filteredProducts, key = { "product_${it.id}" }) { product ->
                val isInCart = cartItems.any { it.product.id == product.id }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, if (isInCart) DsColors.Primary else DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(DsShapes.medium)
                            .background(if (isInCart) DsColors.PrimaryLight else DsColors.SurfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isInCart) Icons.Default.Check else Icons.Default.Inventory2,
                            contentDescription = null,
                            tint     = if (isInCart) DsColors.Primary else DsColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(DsSpacing.sm))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            product.name,
                            fontSize   = DsTextSize.body,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.TextPrimary,
                            maxLines   = 1
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            Text("Dépôt: ${product.stock - product.camion_stock}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            Text("Camion: ${product.camion_stock}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }

                    Spacer(Modifier.width(DsSpacing.sm))

                    if (!isInCart) {
                        Button(
                            onClick = {
                                cartItems = cartItems + ChargementCartItem(
                                    product      = product,
                                    targetCamion = product.camion_stock
                                )
                            },
                            shape          = DsShapes.small,
                            colors         = ButtonDefaults.buttonColors(
                                containerColor = DsColors.PrimaryLight,
                                contentColor   = DsColors.Primary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ajouter", fontSize = DsTextSize.caption)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(DsShapes.pill)
                                .background(DsColors.SuccessLight)
                                .clickable { cartItems = cartItems.filter { it.product.id != product.id } }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(13.dp))
                                Text("Ajouté", fontSize = DsTextSize.caption, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom bar: cart summary pill ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(DsShapes.medium)
                    .background(if (cartItems.isNotEmpty()) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                    .clickable(enabled = cartItems.isNotEmpty()) { showCart = true }
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary
                )
                Text(
                    if (cartItems.isEmpty()) "Sélection vide" else "Ma sélection",
                    color      = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                if (cartItems.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Text("${cartItems.size}", color = DsColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChargementCartRow(
    item             : ChargementCartItem,
    onQuantityChange : (Int) -> Unit,
    onRemove         : () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val delta         = item.targetCamion - item.product.camion_stock
    val depotPreview  = item.product.stock - item.targetCamion   // stock = total désormais
    val subtitle = when {
        delta > 0 -> "+$delta vers le camion"
        delta < 0 -> "${-delta} vers le dépôt"
        else      -> "Aucun changement"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
    ) {
        // ── Collapsed header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.product.name,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1
                )
                Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DsColors.TextSecondary
            )
        }

        // ── Expanded panel ──
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .padding(horizontal = DsSpacing.md)
                    .padding(bottom = DsSpacing.md)
            ) {
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                Spacer(Modifier.height(DsSpacing.md))

                // ── Stepper ──
                Text(
                    "Quantité actuellement dans le camion",
                    fontSize  = DsTextSize.caption,
                    color     = DsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = { onQuantityChange(item.targetCamion - 1) },
                        enabled  = item.targetCamion > 0,
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = null,
                            tint = if (item.targetCamion > 0) DsColors.TextPrimary else DsColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        item.targetCamion.toString(),
                        fontSize   = DsTextSize.display,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.Primary,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.widthIn(min = 48.dp).padding(horizontal = DsSpacing.md)
                    )
                    IconButton(
                        onClick  = { onQuantityChange(item.targetCamion + 1) },
                        modifier = Modifier.size(36.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = DsColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Preview boxes ──
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DsShapes.medium)
                            .background(if (depotPreview < 0) DsColors.DangerLight else DsColors.SurfaceMuted)
                            .padding(DsSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Dépôt",
                            fontSize = DsTextSize.caption,
                            color    = if (depotPreview < 0) DsColors.Danger else DsColors.TextSecondary
                        )
                        Text(
                            "$depotPreview",
                            fontSize   = DsTextSize.headline,
                            fontWeight = FontWeight.Medium,
                            color      = if (depotPreview < 0) DsColors.Danger else DsColors.TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DsShapes.medium)
                            .background(DsColors.PrimaryLight)
                            .padding(DsSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Camion", fontSize = DsTextSize.caption, color = DsColors.Primary)
                        Text("${item.targetCamion}", fontSize = DsTextSize.headline, fontWeight = FontWeight.Medium, color = DsColors.Primary)
                    }
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Delta banner ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(
                            when {
                                delta > 0 -> DsColors.PrimaryLight
                                delta < 0 -> DsColors.WarningLight
                                else      -> DsColors.SurfaceMuted
                            }
                        )
                        .padding(DsSpacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    when {
                        delta > 0 -> {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(DsSpacing.xs))
                            Text(
                                "$delta unité(s) envoyée(s) vers le camion",
                                fontSize = DsTextSize.bodySmall,
                                color    = DsColors.Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        delta < 0 -> {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(DsSpacing.xs))
                            Text(
                                "${-delta} unité(s) ramenée(s) au dépôt",
                                fontSize = DsTextSize.bodySmall,
                                color    = DsColors.Warning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            Text("Aucun changement", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(DsSpacing.sm))

                // ── Retirer ──
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            }
        }
    }
}
