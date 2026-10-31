package com.distrigo.app.ui.purchases

import com.distrigo.app.core.format.westernNumber
import com.distrigo.app.data.model.ProductUnit
import com.distrigo.app.ui.common.formatQty
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
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.common.CartBlockingBanner
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import java.util.Locale
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.data.model.Amount
import com.distrigo.app.ui.common.avatarColor
import androidx.compose.ui.res.stringResource
import com.distrigo.app.ui.common.asString
import com.distrigo.app.ui.common.quantityText
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource

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
    /**
     * False in edit mode. A committed bon's supplier cannot be changed — `updatePurchaseOrder`
     * reads `existing.supplier_id` and never writes one — so offering "Changer" there promises an
     * edit the save silently discards. The "Choisir un fournisseur" button in the empty branch is
     * deliberately left alone: it is the only way out if the bon's supplier has been deleted,
     * since "Suivant" stays disabled while the selection is null.
     */
    canChangeSupplier : Boolean = true,
    onChooseSupplier : () -> Unit,
    onNext           : () -> Unit
) {
    val money = LocalMoneyFormatter.current
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
                stringResource(R.string.purchases_choose_supplier),
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.PrimaryDark,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                stringResource(R.string.purchases_choose_supplier_hint),
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
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(stringResource(R.string.purchases_choose_supplier), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // ── Selected supplier card ──
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Primary)
                ) {
                    Column(modifier = Modifier.padding(DsSpacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color        = avatarColor(selectedSupplier.name)
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
                                    stringResource(R.string.common_balance_value, money.da(selectedSupplier.balance)),
                                    fontSize = DsTextSize.bodySmall,
                                    color    = if (Amount.owing(selectedSupplier.balance)) DsColors.Danger else DsColors.TextSecondary
                                )
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                        }
                        if (canChangeSupplier) {
                            Spacer(Modifier.height(DsSpacing.md))
                            OutlinedButton(
                                onClick  = onChooseSupplier,
                                modifier = Modifier.fillMaxWidth(),
                                shape    = DsShapes.medium
                            ) {
                                Text(stringResource(R.string.common_action_change), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
                            }
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
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
        ) {
            Text(stringResource(R.string.common_action_next), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
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
    /**
     * A restored draft may reference a product that has since been deleted. Saving would throw
     * "Produit introuvable" deep inside the commit transaction and roll back with nothing the user
     * can act on, so the button goes dead until the offending line is removed — the cart step marks
     * which one it is.
     */
    hasMissingProducts      : Boolean = false,
    /** The name of a pièce line whose colis make part of a piece: the bon waits until the cart fixes it. */
    fractionalPieces        : String? = null,
    onBack                  : () -> Unit,
    /**
     * Where "Corriger" on the blocking banner goes: the cart, the only place the offending
     * line can be removed. Separate from [onBack] because the cart is popped off the stack on
     * the way to this step, so going back lands on the product list instead.
     */
    onFixMissing            : () -> Unit = onBack,
    onConfirm               : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    // No bar of its own: the workflow's header above already says « Validation · 3/3 » and goes back.
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── The bon in one frame: the supplier, its articles and the total, close together (the owner,
            // 2026-10-19). « Montant payé » and the note keep frames of their own below.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Supplier
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceMuted)
                            .padding(DsSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                stringResource(R.string.purchases_supplier_balance, money.da(selectedSupplier?.balance ?: 0.0)),
                                fontSize = DsTextSize.bodySmall,
                                color    = if (Amount.owing(selectedSupplier?.balance ?: 0.0)) DsColors.Danger else DsColors.TextSecondary
                            )
                        }
                    }

                    Text(
                        pluralStringResource(R.plurals.common_items_count, cartItems.size, cartItems.size),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextSecondary,
                        modifier   = Modifier.padding(start = DsSpacing.xs, top = 2.dp)
                    )

                    // Articles (read-only)
                    cartItems.forEach { item ->
                        val quantityLine = if (item.product.unit_type == "pièce")
                            stringResource(R.string.purchases_cart_colis_meta, formatQty(item.nbColis), item.uniteParColis, quantityText(item.quantity, item.product.unit_type).asString())
                        else
                            quantityText(item.quantity, item.product.unit_type).asString()

                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .background(DsColors.SurfaceMuted)
                                .padding(DsSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.SurfaceSunken),
                                contentAlignment = Alignment.Center
                            ) {
                                EntityImage(
                                    ref                = item.product.image_uri,
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize()
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                                Text(quantityLine, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                Text(stringResource(R.string.purchases_unit_price, money.da(item.unitCost)), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text(
                                money.da(item.quantity * item.unitCost),
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.Bold,
                                color      = DsColors.PrimaryDark
                            )
                        }
                    }

                    // Total
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.PrimaryLight)
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.common_label_total), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
                        Text(money.da(total), fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark)
                    }
                }
            }

            // ── Montant payé ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.common_amount_paid_da), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        TextButton(
                            // Locale.ROOT: this string goes straight into the field that is read
                            // back with String.toDoubleOrNull(), which accepts only a '.' decimal
                            // separator. On a French-locale device the default format yields
                            // "2880,00", which parses as 0 — "Tout réglé" would record nothing paid.
                            onClick = { onMontantPayeChange(String.format(Locale.ROOT, "%.2f", total)) }
                        ) {
                            Text(stringResource(R.string.common_settled_all), fontSize = DsTextSize.bodySmall, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = montantPaye,
                        onValueChange = { raw -> onMontantPayeChange(westernNumber(raw).filter { it.isDigit() || it == '.' }) },
                        placeholder     = { Text("0.00", fontSize = DsTextSize.body) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = DsShapes.medium,
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = dsTextFieldColors(
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
                            Text(stringResource(R.string.common_label_remaining), fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                            Text(
                                money.da(reste),
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
                            Text(stringResource(R.string.common_label_status), fontSize = DsTextSize.bodySmall, color = DsColors.Success)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text(stringResource(R.string.common_settled_check), fontSize = DsTextSize.bodySmall, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
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
                    placeholder   = { Text(stringResource(R.string.common_label_note_optional), fontSize = DsTextSize.body) },
                    modifier      = Modifier.fillMaxWidth().background(DsColors.Surface, DsShapes.medium),
                    shape         = DsShapes.medium,
                    minLines      = 2,
                    maxLines      = 3,
                    colors        = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }
        }

        // The reason for a dead button, docked against the button itself. Before this the
        // only marker was back on the cart step, so this screen offered a greyed-out control
        // and no account of why — the one thing the user needed in order to act.
        if (hasMissingProducts) {
            CartBlockingBanner(
                text        = stringResource(R.string.purchases_banner_missing_product),
                actionLabel = stringResource(R.string.common_action_fix),
                onAction    = onFixMissing
            )
        } else if (fractionalPieces != null) {
            CartBlockingBanner(
                text        = stringResource(R.string.purchases_banner_fractional_pieces, fractionalPieces),
                actionLabel = stringResource(R.string.common_action_fix),
                onAction    = onFixMissing
            )
        }

        // ── Confirm button ──
        Button(
            onClick  = onConfirm,
            enabled  = !isSaving && !hasMissingProducts && fractionalPieces == null && selectedSupplier != null &&
                       (cartItems.isNotEmpty() || isEdit),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape    = DsShapes.large,
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isEdit) DsColors.PrimaryDark else DsColors.Success
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(
                    if (isEdit) stringResource(R.string.purchases_save_amount, money.da(total))
                    else        stringResource(R.string.common_confirm_amount, money.da(total)),
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
