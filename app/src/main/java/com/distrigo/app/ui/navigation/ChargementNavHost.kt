package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.distrigo.app.ui.chargements.ChargementCartItem
import com.distrigo.app.ui.chargements.ChargementCartRow
import com.distrigo.app.ui.chargements.ChargementFormSessionViewModel
import com.distrigo.app.ui.chargements.ChargementViewModel
import com.distrigo.app.ui.chargements.formatQty
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.DsCompactSearchField

// Self-contained NavHost, invoked as a plain composable call from StockCamionScreen's existing
// legacy `showNewChargement`/`editingProduct` toggles — mirroring how VentesNavHost/TourneesNavHost
// are invoked from TourneesHubScreen. Not a nested graph: ChargementFormScreen had exactly one
// caller (StockCamionScreen), so there's no cross-NavHost sharing need like Vente/Tournée/
// Purchase/Retour had, and preSelectedProductId/draftId are passed as ordinary composable
// parameters (closed over directly) rather than threaded through nav route args, since this
// NavHost is entered via a fresh function call, not via navController.navigate(...).
@Composable
fun ChargementNavHost(
    onBack                 : () -> Unit,
    onSaved                : () -> Unit,
    preSelectedProductId   : Int? = null,
    /** A Brouillon to resume, or null to start clean. */
    draftId                : Int? = null
) {
    val navController = rememberNavController()

    NavHost(
        navController      = navController,
        startDestination   = Screen.ChargementFormProducts.route,
        route              = Screen.ChargementFormGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.ChargementFormProducts.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargementFormGraph.route) }
            // No ChargementViewModel here any more: the only thing this step ever asked it for was
            // selectedChargement, which existed to pre-fill a correction that nothing ever started.
            val session: ChargementFormSessionViewModel = hiltViewModel(parentEntry)
            LaunchedEffect(Unit) { session.beginOrResumeSession(draftId) }
            val productViewModel: ProductViewModel = hiltViewModel()
            val products by productViewModel.products.collectAsState()
            val cartItems by session.formCartItems.collectAsState()
            var search by remember { mutableStateOf("") }

            // preSelectedProduct: jump straight to the cart/review screen with that one product
            // pre-added — matches the original's `showCart = preSelectedProduct != null` initial
            // state exactly.
            LaunchedEffect(preSelectedProductId, products) {
                if (preSelectedProductId != null && cartItems.isEmpty() && products.isNotEmpty()) {
                    val product = products.find { it.id == preSelectedProductId }
                    if (product != null) {
                        session.setFormCartItems(listOf(ChargementCartItem(product = product, targetCamion = product.camion_stock)))
                        navController.navigate(Screen.ChargementFormCart.route) {
                            popUpTo(Screen.ChargementFormProducts.route) { inclusive = false }
                        }
                    }
                }
            }

            BackHandler { onBack() }

            val filteredProducts = products.filter { product ->
                search.isBlank() ||
                        product.name.contains(search, ignoreCase = true) ||
                        (product.barcode?.contains(search, ignoreCase = true) == true)
            }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title         = "Chargement / Déchargement",
                    subtitle      = if (cartItems.isNotEmpty()) "${cartItems.size} article(s)" else "Sélectionnez des produits",
                    // Blue once something is in the cart, grey while the step is still empty.
                    subtitleColor = if (cartItems.isNotEmpty()) DsColors.Primary else DsColors.TextSecondary,
                    leading       = DsTopBarLeading.Back(onBack)
                )

                // Outside the list, not its first item: a search box that scrolls away is gone
                // exactly when a long list makes it useful. Fixed here, as Stock Camion and the
                // tournée detail already have theirs, so only the products move.
                DsCompactSearchField(
                    value         = search,
                    onValueChange = { search = it },
                    placeholder   = "Rechercher un produit",
                    modifier      = Modifier.padding(horizontal = DsSpacing.lg)
                )

                Spacer(Modifier.height(DsSpacing.sm))

                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
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
                                if (isInCart) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                                } else {
                                    EntityImage(
                                        ref                = product.image_uri,
                                        contentDescription = null,
                                        modifier           = Modifier.fillMaxSize().clip(DsShapes.medium)
                                    ) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(20.dp))
                                    }
                                }
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
                                    Text("Dépôt: ${formatQty(product.stock - product.camion_stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                    Text("Camion: ${formatQty(product.camion_stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                            }

                            Spacer(Modifier.width(DsSpacing.sm))

                            if (!isInCart) {
                                Button(
                                    onClick = {
                                        session.setFormCartItems(
                                            cartItems + ChargementCartItem(product = product, targetCamion = product.camion_stock)
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
                                        .clickable { session.setFormCartItems(cartItems.filter { it.product.id != product.id }) }
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
                            .clickable(enabled = cartItems.isNotEmpty()) { navController.navigate(Screen.ChargementFormCart.route) }
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

        composable(Screen.ChargementFormCart.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ChargementFormGraph.route) }
            val viewModel: ChargementViewModel = hiltViewModel(parentEntry)
            val session: ChargementFormSessionViewModel = hiltViewModel(parentEntry)
            LaunchedEffect(Unit) { session.beginOrResumeSession(draftId) }
            val cartItems by session.formCartItems.collectAsState()
            val note by session.formNote.collectAsState()
            val userName by session.formUserName.collectAsState()
            var isSaving by remember { mutableStateOf(false) }

            // Exactly the condition save() uses to decide whether to write anything: a line counts
            // only when its target differs from what the truck currently holds. Derived from
            // cartItems rather than tracked separately, so the button cannot drift out of step with
            // what the save would actually do.
            val hasChanges = cartItems.any { it.targetCamion != it.product.camion_stock }

            fun save() {
                isSaving = true
                val items = cartItems.mapNotNull { ci ->
                    val delta = ci.targetCamion - ci.product.camion_stock
                    if (delta == 0.0) return@mapNotNull null
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
                    // Deleted as the final statement inside the movement's own transaction, so the
                    // draft outlives any failure.
                    draftId   = session.draftId,
                    note      = note.trim().ifEmpty { null },
                    userName  = userName.trim().ifEmpty { null },
                    items     = items,
                    onSuccess = { session.onCommitted(); onSaved() },
                    onError   = { isSaving = false }
                )
            }

            BackHandler { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title    = "Ma sélection",
                    subtitle = "${cartItems.size} article(s)",
                    leading  = DsTopBarLeading.Back({ navController.popBackStack() })
                ) {
                    if (cartItems.isNotEmpty()) {
                        TextButton(onClick = { session.setFormCartItems(emptyList()) }) {
                            Text("Vider", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                        }
                    }
                    // TextButton brings its own inset; this makes up the standard end margin.
                    Spacer(Modifier.width(DsSpacing.xs))
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
                                onClick = { navController.popBackStack() },
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
                                item              = item,
                                initiallyExpanded = preSelectedProductId == item.product.id,
                                onQuantityChange  = { newTarget ->
                                    session.setFormCartItems(cartItems.map {
                                        if (it.product.id == item.product.id) it.copy(targetCamion = newTarget.coerceAtLeast(0.0)) else it
                                    })
                                },
                                onRemove = {
                                    session.setFormCartItems(cartItems.filter { it.product.id != item.product.id })
                                }
                            )
                        }

                        item {
                            OutlinedTextField(
                                value         = userName,
                                onValueChange = { session.setFormUserName(it) },
                                placeholder   = { Text("Effectué par (optionnel)", fontSize = DsTextSize.body) },
                                leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = DsShapes.medium,
                                singleLine    = true,
                                colors = dsTextFieldColors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                        }

                        item {
                            OutlinedTextField(
                                value         = note,
                                onValueChange = { session.setFormNote(it) },
                                placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = DsShapes.medium,
                                minLines      = 2,
                                colors = dsTextFieldColors(
                                    unfocusedBorderColor = DsColors.Border,
                                    focusedBorderColor   = DsColors.Primary
                                )
                            )
                        }
                    }

                    // A cart full of untouched lines has a zero delta on every one of them,
                    // and save() already returns without writing in that case — so the button was
                    // live and did nothing, which looks like a failure rather than a no-op. The
                    // same rule the single-product card uses: the control is dead when there is
                    // nothing for it to do, and says so.
                    Button(
                        onClick  = { save() },
                        enabled  = hasChanges && !isSaving,
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
        }
    }
}
