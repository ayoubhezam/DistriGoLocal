package com.distrigo.app.ui.suppliers

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.activity.compose.BackHandler

@Composable
fun SuppliersScreen(
    viewModel : SupplierViewModel = viewModel(),
    modifier  : Modifier = Modifier,
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()

    var search           by remember { mutableStateOf("") }
    var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
    var showAddScreen    by remember { mutableStateOf(false) }
    var showEditScreen   by remember { mutableStateOf<Supplier?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Supplier?>(null) }
    var snackbarMessage  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSuppliers() }

    val filtered  = suppliers.filter { supplier ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            supplier.name.contains(token, ignoreCase = true) || (supplier.phone?.contains(token, ignoreCase = true) == true)
        }
    }

    val totalDebt = suppliers.filter { it.balance > 0 }.sumOf { it.balance }

    // ── Delete Dialog ──
    showDeleteDialog?.let { supplier ->
        var deleteError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null; deleteError = "" },
            title = { Text("Supprimer le fournisseur") },
            text  = {
                Column {
                    Text("Voulez-vous supprimer \"${supplier.name}\" ?")
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSupplier(
                        id        = supplier.id,
                        onSuccess = {
                            showDeleteDialog = null
                            selectedSupplier = null
                            snackbarMessage  = "Fournisseur supprimé avec succès"
                        },
                        onError = {
                            deleteError = "Impossible de supprimer ce fournisseur car il est associé à des produits."
                        }
                    )
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null; deleteError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ── Add Screen ──
    if (showAddScreen) {
        onFullScreenChange(true)
        BackHandler { showAddScreen = false; onFullScreenChange(false) }
        SupplierFormScreen(
            onBack  = { showAddScreen = false; onFullScreenChange(false) },
            onSaved = { showAddScreen = false; onFullScreenChange(false); viewModel.loadSuppliers() }
        )
        return
    }

    // ── Edit Screen ──
    showEditScreen?.let { supplier ->
        onFullScreenChange(true)
        BackHandler { showEditScreen = null; onFullScreenChange(false) }
        SupplierFormScreen(
            supplier = supplier,
            onBack   = { showEditScreen = null; onFullScreenChange(false) },
            onSaved  = {
                showEditScreen = null
                onFullScreenChange(false)
                viewModel.loadSuppliersAndUpdate(supplier.id) { updated ->
                    selectedSupplier = updated
                    viewModel.loadSuppliers()

                }
            }
        )
        return
    }

        // ── Detail Screen ──
        selectedSupplier?.let { supplier ->
            BackHandler { selectedSupplier = null }
            SupplierDetailScreen(
                supplier = supplier,
                onBack = { selectedSupplier = null },
                onEdit = { showEditScreen = supplier },
                onDelete = { showDeleteDialog = supplier },
                viewModel = viewModel
            )
            return
        }



    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
            snackbarMessage?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2500)
                    snackbarMessage = null
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)
                        .clip(DsShapes.medium)
                        .background(Color(0xFF323232))
                        .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                ) {
                    Text(message, color = Color.White, fontSize = DsTextSize.bodySmall)
                }
            }
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fournisseurs",
                    fontSize = DsTextSize.headline,
                    fontWeight = FontWeight.Bold,
                    color = DsColors.TextPrimary
                )
                FloatingActionButton(
                    onClick = { showAddScreen = true },
                    containerColor = DsColors.Primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(40.dp),
                    shape = DsShapes.pill
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter")
                }
            }

            // ── Total Debt Banner ──
            if (totalDebt > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg)
                        .padding(bottom = 10.dp)
                        .clip(DsShapes.large)
                        .background(DsColors.DangerLight)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = DsColors.Danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Total des dettes",
                            fontSize = DsTextSize.bodySmall,
                            color = DsColors.Danger,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "${formatDZD(totalDebt)} DA",
                        fontSize = DsTextSize.bodyLarge,
                        color = DsColors.Danger,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Search ──
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Rechercher par nom ou téléphone…", fontSize = DsTextSize.body) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg)
                .clip(DsShapes.large),
            shape = DsShapes.large,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor = DsColors.Primary
            )
        )

            Spacer(Modifier.height(DsSpacing.sm))

            Text(
                "${filtered.size} fournisseur(s)",
                fontSize = DsTextSize.caption,
                color = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )

            Spacer(Modifier.height(DsSpacing.sm))

            // ── Loading ──
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
                return
            }

            // ── Error ──
            error?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(it, color = DsColors.Danger)
                }
                return
            }

            // ── Empty State ──
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Business, contentDescription = null,
                            tint = DsColors.Primary.copy(alpha = 0.3f), modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                        Text(
                            "Aucun fournisseur trouvé",
                            color = DsColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                return
            }

            // ── List ──
            LazyColumn(
                contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                items(filtered) { supplier ->
                    SupplierCard(
                        supplier = supplier,
                        onClick = { selectedSupplier = supplier }
                    )
                }
            }
        }
    }


@Composable
fun SupplierCard(supplier: Supplier, onClick: () -> Unit) {
        val isDebt = supplier.balance > 0
        val colors = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
        val color = Color(colors[supplier.name[0].code % colors.size])
        val initials = supplier.name.split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

        Card(
            modifier  = Modifier.fillMaxWidth().clickable { onClick() },
            shape     = DsShapes.large,
            colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
            elevation = CardDefaults.cardElevation(1.dp),
            border    = androidx.compose.foundation.BorderStroke(
                1.dp, if (isDebt) DsColors.Danger else DsColors.Border
            )
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(42.dp).clip(DsShapes.pill),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                    Text(initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = color)
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(11.dp))
                        Text(supplier.phone?:"", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    }
                }
                Spacer(Modifier.width(DsSpacing.sm))
                if (isDebt) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Doit", fontSize = DsTextSize.caption, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                        Text("${formatDZD(supplier.balance)} DA", fontSize = DsTextSize.bodySmall, color = DsColors.Danger, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(DsColors.SuccessLight)
                            .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(11.dp))
                            Text("Réglé", fontSize = DsTextSize.caption, color = DsColors.Success, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
