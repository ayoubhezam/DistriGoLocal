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
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
@Composable
fun SuppliersScreen(
    viewModel       : SupplierViewModel = hiltViewModel(),
    modifier        : Modifier = Modifier,
    onAddSupplier   : () -> Unit = {},
    onSupplierClick : (Int) -> Unit = {}
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()

    var search by remember { mutableStateOf("") }

    val filtered  = suppliers.filter { supplier ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            supplier.name.contains(token, ignoreCase = true) || (supplier.phone?.contains(token, ignoreCase = true) == true)
        }
    }

    val totalDebt = suppliers.filter { it.balance > 0 }.sumOf { it.balance }



    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        item {
            // ── Header ──
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Fournisseurs", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
                FloatingActionButton(
                    onClick        = { onAddSupplier()},
                    containerColor = DsColors.Primary,
                    contentColor   = Color.White,
                    modifier       = Modifier.size(40.dp),
                    shape          = DsShapes.pill
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter")
                }
            }
        }

        if (totalDebt > 0) {
            item {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.lg)
                            .clip(DsShapes.large)
                            .background(DsColors.DangerLight)
                            .padding(DsSpacing.lg)
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(16.dp))
                            Text("Total des dettes", fontSize = DsTextSize.bodySmall, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            "${formatDZD(totalDebt)} DA",
                            fontSize   = DsTextSize.bodyLarge,
                            color      = DsColors.Danger,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(DsSpacing.md))
                }
            }
        }

        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DsColors.Surface)
            ) {
                // ── Search ──
                OutlinedTextField(
                    value         = search,
                    onValueChange = { search = it },
                    placeholder   = {
                        Text(
                            "Rechercher par nom ou téléphone…",
                            fontSize = DsTextSize.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon  = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    shape         = DsShapes.large,
                    singleLine    = true,
                    textStyle     = LocalTextStyle.current.copy(fontSize = DsTextSize.bodySmall),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )

                Spacer(Modifier.height(DsSpacing.sm))

                Text(
                    "${filtered.size} fournisseur(s)",
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = DsSpacing.lg)
                )

                Spacer(Modifier.height(DsSpacing.xs))
            }
        }

        items(filtered, key = { it.id }) { supplier ->
            Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)) {
                SupplierCard(
                    supplier = supplier,
                    onClick  = { onSupplierClick(supplier.id) }
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
