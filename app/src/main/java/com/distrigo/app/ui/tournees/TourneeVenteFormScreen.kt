package com.distrigo.app.ui.tournees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.common.CartStatusLine
import com.distrigo.app.ui.common.CartStatusTone
import com.distrigo.app.ui.common.PriceFieldWithHistory
import com.distrigo.app.ui.common.QuantityStepper
import com.distrigo.app.ui.common.SelectionCartCard
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors


internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

data class TourneeVenteCartItem(
    val product   : Product,
    val quantity  : Double = 1.0,
    val unitPrice : Double
)

// ── Step 1 : Client ──────────────────────────────────────────────────────────
@Composable
internal fun Step1Client(
    selectedClient : Client?,
    onChooseClient : () -> Unit,
    onNext         : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(DsSpacing.xxl))

            Box(
                modifier         = Modifier.size(88.dp).clip(DsShapes.large).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(DsSpacing.xl))

            Text(
                "Choisir un client",
                fontSize   = DsTextSize.headline,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xs))

            Text(
                "Sélectionnez le client avant d'ajouter des produits",
                fontSize  = DsTextSize.bodySmall,
                color     = DsColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xxl))

            if (selectedClient == null) {
                Button(
                    onClick  = onChooseClient,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Choisir un client", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(DsSpacing.sm))


                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.PrimaryLight)
                        .padding(DsSpacing.md)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                        Text(
                            "Pourquoi d'abord le client ?",
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.Primary
                        )
                    }
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text(
                        "Le solde et l'historique du client permettent de vérifier sa situation avant de préparer la vente. " +
                            "Cela évite les erreurs de facturation et de règlement.",
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextPrimary
                    )
                }

                Spacer(Modifier.height(DsSpacing.md))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceMuted)
                        .padding(DsSpacing.md),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        "La recherche de produits sera disponible après la sélection",
                        fontSize = DsTextSize.bodySmall,
                        color    = DsColors.TextSecondary
                    )
                }
            } else {
                val typeColors = when (selectedClient.customer_type) {
                    "wholesale" -> DsColors.TagWholesale
                    "business"  -> DsColors.TagBusiness
                    else        -> DsColors.TagRetail
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Primary, DsShapes.large)
                        .padding(DsSpacing.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val initials = selectedClient.name.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(
                            modifier         = Modifier.size(46.dp).clip(DsShapes.medium).background(typeColors.second),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = typeColors.first)
                        }
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedClient.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text(
                                "Solde : ${"%.2f".format(selectedClient.balance)} DA",
                                fontSize = DsTextSize.bodySmall,
                                color    = if (selectedClient.balance > 0) DsColors.Danger else DsColors.TextSecondary
                            )
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(DsSpacing.md))
                    OutlinedButton(
                        onClick  = onChooseClient,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = DsShapes.medium
                    ) {
                        Text("Changer", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Button(
            onClick  = onNext,
            enabled  = selectedClient != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape  = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Step 3 : Validation ──────────────────────────────────────────────────────
@Composable
internal fun Step3Validation(
    selectedClient      : Client?,
    cartItems           : List<TourneeVenteCartItem>,
    total               : Double,
    montantPaye         : String,
    onMontantPayeChange : (String) -> Unit,
    note                : String,
    onNoteChange        : (String) -> Unit,
    isSaving            : Boolean,
    onBack              : () -> Unit,
    onConfirm           : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Text("Validation", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── Client summary card ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(DsSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedClient?.name ?: "—", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                        Text(
                            "Solde du client : ${"%.2f".format(selectedClient?.balance ?: 0.0)} DA",
                            fontSize = DsTextSize.bodySmall,
                            color    = if ((selectedClient?.balance ?: 0.0) > 0) DsColors.Danger else DsColors.TextSecondary
                        )
                    }
                }
            }

            // ── Articles header ──
            item {
                Text(
                    "Articles (${cartItems.size})",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextSecondary,
                    modifier   = Modifier.padding(top = DsSpacing.xs)
                )
            }

            // ── Articles list (read-only) ──
            items(cartItems, key = { it.product.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = remember(item.product.image_uri) {
                            item.product.image_uri?.let { uri ->
                                val imageBytes = android.util.Base64.decode(uri.substringAfter("base64,"), android.util.Base64.NO_WRAP)
                                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            }
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(DsSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                        Text("${formatQty(item.quantity)} × ${"%.2f".format(item.unitPrice)} DA", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(
                        "${"%.2f".format(item.quantity * item.unitPrice)} DA",
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.Primary
                    )
                }
            }

            // ── Total ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.PrimaryLight)
                        .padding(DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Total", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                    Text("${"%.2f".format(total)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
                }
            }

            // ── Montant payé ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.SurfaceMuted)
                        .padding(DsSpacing.md)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Montant payé (DA)", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        TextButton(onClick = { onMontantPayeChange("%.2f".format(total)) }) {
                            Text("Tout réglé", fontSize = DsTextSize.caption, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(DsSpacing.xs))
                    OutlinedTextField(
                        value           = montantPaye,
                        onValueChange   = { raw -> onMontantPayeChange(raw.filter { it.isDigit() || it == '.' }) },
                        placeholder     = { Text("0.00", fontSize = DsTextSize.body) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = DsShapes.small,
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )

                    val paye  = montantPaye.toDoubleOrNull() ?: 0.0
                    val reste = total - paye
                    if (paye > 0 && reste > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Reste", fontSize = DsTextSize.caption, color = DsColors.Danger)
                            Text("${"%.2f".format(reste)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                        }
                    } else if (paye >= total && total > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Statut", fontSize = DsTextSize.caption, color = DsColors.Success)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text("Réglé ✓", fontSize = DsTextSize.caption, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Note ──
            item {
                OutlinedTextField(
                    value         = note,
                    onValueChange = onNoteChange,
                    placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = DsShapes.medium,
                    minLines      = 2,
                    maxLines      = 3,
                    colors = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }
        }

        // ── Confirm button ──
        Button(
            onClick  = onConfirm,
            enabled  = !isSaving && selectedClient != null && cartItems.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape  = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(
                    "Confirmer la vente · ${"%.2f".format(total)} DA",
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Cart row (expandable, shared SelectionCartCard) ──────────────────────────
// Not private: reused by VenteFormScreen.kt for camion-sourced items in edit mode,
// so the camion availability formula has exactly one implementation in the app.
@Composable
fun TourneeVenteCartRow(
    item             : TourneeVenteCartItem,
    isExpanded       : Boolean,
    onToggleExpand   : () -> Unit,
    onQuantityChange : (Double) -> Unit,
    onPriceChange    : (Double) -> Unit,
    onRemove         : () -> Unit
) {
    val availableStock = item.product.camion_stock
    val isLow          = item.quantity > availableStock

    SelectionCartCard(
        avatarIcon      = Icons.Default.ShoppingCart,
        title           = item.product.name,
        metaLine        = "${formatQty(item.quantity)} ${item.product.unit_type} × ${"%.2f".format(item.unitPrice)} DA",
        totalPriceLabel = "${"%.2f".format(item.quantity * item.unitPrice)} DA",
        isExpanded      = isExpanded,
        onToggleExpand  = onToggleExpand,
        statusLine = {
            CartStatusLine(
                icon = if (isLow) Icons.Default.Warning else Icons.Default.LocalShipping,
                text = "Disponible : ${formatQty(availableStock)} ${item.product.unit_type}",
                tone = if (isLow) CartStatusTone.WARNING else CartStatusTone.NEUTRAL
            )
        },
        expandedContent = {
            QuantityStepper(
                label         = "Quantité",
                value         = item.quantity,
                onValueChange = onQuantityChange,
                max           = availableStock,
                formatValue   = ::formatQty,
                min = 0.01,
            )

            Spacer(Modifier.height(DsSpacing.md))

            PriceFieldWithHistory(
                price         = item.unitPrice,
                onPriceChange = onPriceChange
            )

            Spacer(Modifier.height(DsSpacing.xs))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                }
            }
        }
    )
}
