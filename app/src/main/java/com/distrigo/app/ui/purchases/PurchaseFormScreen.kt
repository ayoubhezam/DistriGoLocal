package com.distrigo.app.ui.purchases

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.suppliers.formatDZD

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

data class CartItem(
    val product       : Product,
    val quantity      : Double,   // final quantity sent to API
    val unitCost      : Double,
    val nbColis       : Double = 1.0,
    val uniteParColis : Int = 1,  // only used for pièce
    val hasExpiry     : Boolean = false,
    val expiryDate    : String? = null   // "yyyy-MM-dd"
)

// ── Step 1 : Fournisseur ─────────────────────────────────────────────────────
@Composable
internal fun Step1Fournisseur(
    selectedSupplier : Supplier?,
    onChooseSupplier : () -> Unit,
    onNext           : () -> Unit
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
                Icon(Icons.Default.Business, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(DsSpacing.xl))

            Text(
                "Choisir un fournisseur",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Sélectionnez le fournisseur avant d'ajouter des produits",
                fontSize  = DsTextSize.bodySmall,
                color     = DsColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xxl))

            if (selectedSupplier == null) {
                Button(
                    onClick  = onChooseSupplier,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = DsShapes.large,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Choisir un fournisseur", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(DsSpacing.lg))

                // ── Info box ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.PrimaryLight)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                        Text(
                            "Pourquoi d'abord le fournisseur ?",
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.Primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Les prix d'achat et les produits disponibles dépendent du fournisseur choisi. " +
                            "Cela permet de proposer une liste de produits pertinente et d'éviter les erreurs de saisie.",
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextPrimary
                    )
                }

                Spacer(Modifier.height(DsSpacing.md))

                // ── Search preview (disabled) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceSunken)
                        .padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        "La recherche sera disponible après la sélection",
                        fontSize = DsTextSize.bodySmall,
                        color    = DsColors.TextSecondary
                    )
                }
            } else {
                // ── Selected supplier card ──
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Primary)
                ) {
                    Column(modifier = Modifier.padding(DsSpacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avatarColors = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
                            val color        = Color(avatarColors[selectedSupplier.name[0].code % avatarColors.size])
                            val initials     = selectedSupplier.name.split(" ").take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                            Box(
                                modifier         = Modifier.size(46.dp).clip(DsShapes.pill),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                                Text(initials, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = color)
                            }
                            Spacer(Modifier.width(DsSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedSupplier.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                                Text(
                                    "Solde : ${formatDZD(selectedSupplier.balance)} DA",
                                    fontSize = DsTextSize.bodySmall,
                                    color    = if (selectedSupplier.balance > 0) DsColors.Danger else DsColors.TextSecondary
                                )
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(DsSpacing.md))
                        OutlinedButton(
                            onClick  = onChooseSupplier,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = DsShapes.medium
                        ) {
                            Text("Changer", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── Suivant ──
        Button(
            onClick  = onNext,
            enabled  = selectedSupplier != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape  = DsShapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Step 3 : Validation ──────────────────────────────────────────────────────
@Composable
internal fun Step3Validation(
    selectedSupplier        : Supplier?,
    cartItems               : List<CartItem>,
    total                   : Double,
    montantPaye             : String,
    onMontantPayeChange     : (String) -> Unit,
    note                    : String,
    onNoteChange            : (String) -> Unit,
    isEdit                  : Boolean,
    isSaving                : Boolean,
    onBack                  : () -> Unit,
    onConfirm               : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Text("Validation", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Supplier summary card ──
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedSupplier?.name ?: "—", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text(
                                "Solde du fournisseur : ${formatDZD(selectedSupplier?.balance ?: 0.0)} DA",
                                fontSize = DsTextSize.bodySmall,
                                color    = if ((selectedSupplier?.balance ?: 0.0) > 0) DsColors.Danger else DsColors.TextSecondary
                            )
                        }
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
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }

// ── Articles list (read-only) ──
            items(cartItems, key = { it.product.id }) { item ->
                val quantityLine = if (item.product.unit_type == "pièce")
                    "${formatQty(item.nbColis)} colis × ${item.uniteParColis} = ${formatQty(item.quantity)} pièces"
                else
                    "${formatQty(item.nbColis)} cartons"

                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Row(modifier = Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                            Text(quantityLine, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            Text("${"%.2f".format(item.unitCost)} DA/u", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(
                            "${"%.2f".format(item.quantity * item.unitCost)} DA",
                            fontSize   = DsTextSize.body,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.Primary
                        )
                    }
                }
            }

            // ── Total ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
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
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceSunken)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Montant payé (DA)", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        TextButton(
                            onClick = { onMontantPayeChange("%.2f".format(total)) }
                        ) {
                            Text("Tout réglé", fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = montantPaye,
                        onValueChange = { raw -> onMontantPayeChange(raw.filter { it.isDigit() || it == '.' }) },
                        placeholder     = { Text("0.00", fontSize = DsTextSize.body) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = DsShapes.medium,
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = OutlinedTextFieldDefaults.colors(
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
                            Text("Reste", fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                            Text(
                                "${"%.2f".format(reste)} DA",
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.Bold,
                                color      = DsColors.Danger
                            )
                        }
                    } else if (paye >= total && total > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Statut", fontSize = DsTextSize.bodySmall, color = DsColors.Success)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text("Réglé ✓", fontSize = DsTextSize.bodySmall, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
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
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }
        }

        // ── Confirm button ──
        Button(
            onClick  = onConfirm,
            enabled  = !isSaving && selectedSupplier != null && (cartItems.isNotEmpty() || isEdit),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape    = DsShapes.large,
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isEdit) DsColors.Primary else DsColors.Success
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(
                    if (isEdit) "Enregistrer · ${"%.2f".format(total)} DA"
                    else        "Confirmer la commande · ${"%.2f".format(total)} DA",
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
