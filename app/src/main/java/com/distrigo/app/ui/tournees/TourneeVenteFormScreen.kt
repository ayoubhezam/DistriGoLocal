package com.distrigo.app.ui.tournees

import com.distrigo.app.core.format.westernNumber
import com.distrigo.app.data.model.Quantity
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
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.common.CartBlockingBanner
import com.distrigo.app.ui.common.CartStatusLine
import com.distrigo.app.ui.common.CartStatusTone
import com.distrigo.app.ui.common.PriceFieldWithHistory
import com.distrigo.app.ui.common.isBelowCost
import com.distrigo.app.ui.common.QuantityStepper
import com.distrigo.app.ui.common.SelectionCartCard
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
import com.distrigo.app.ui.designsystem.DsStepBadge
import androidx.compose.ui.res.stringResource
import com.distrigo.app.ui.common.asString
import com.distrigo.app.ui.common.quantityText
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource


data class TourneeVenteCartItem(
    val product   : Product,
    val quantity  : Double = 1.0,
    val unitPrice : Double
)

// ── Step 1 : Client ──────────────────────────────────────────────────────────
@Composable
internal fun Step1Client(
    /** The flow's title, as on its other steps: « Tournée #N ». */
    title          : String,
    selectedClient : Client?,
    onChooseClient : () -> Unit,
    onNext         : () -> Unit,
    onBack         : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    Column(modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        // The flow's bar, as on its other steps and on Achats' step 1 (2026-10-19)
        DsTopAppBar(
            title         = title,
            subtitle      = selectedClient?.name ?: stringResource(R.string.common_choose_client),
            subtitleColor = if (selectedClient != null) DsColors.Primary else DsColors.TextSecondary,
            leading       = DsTopBarLeading.Back(onBack)
        ) {
            DsStepBadge(stringResource(R.string.common_label_client), 1, 3)
            Spacer(Modifier.width(DsSpacing.xs))
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

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
                stringResource(R.string.common_choose_client),
                fontSize   = DsTextSize.headline,
                fontWeight = FontWeight.Bold,
                color      = DsColors.PrimaryDark,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(DsSpacing.xs))

            Text(
                stringResource(R.string.ventes_choose_client_hint),
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
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(stringResource(R.string.common_choose_client), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
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
                                stringResource(R.string.common_balance_value, money.da(selectedClient.balance)),
                                fontSize = DsTextSize.bodySmall,
                                color    = if (Amount.owing(selectedClient.balance)) DsColors.Danger else DsColors.TextSecondary
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
                        Text(stringResource(R.string.common_action_change), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium)
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
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
        ) {
            Text(stringResource(R.string.common_action_next), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Step 3 : Validation ──────────────────────────────────────────────────────
@Composable
internal fun Step3Validation(
    /** The flow's title, as on its other steps: « Tournée #N ». */
    title               : String,
    selectedClient      : Client?,
    cartItems           : List<TourneeVenteCartItem>,
    total               : Double,
    montantPaye         : String,
    onMontantPayeChange : (String) -> Unit,
    note                : String,
    onNoteChange        : (String) -> Unit,
    isSaving            : Boolean,
    /**
     * A resumed draft can name a product that has since been deleted, or ask for more of one than
     * the camion still holds. Either way the sale cannot be made: `createVente` would throw inside
     * its own transaction, roll back, and leave a button that looked like it did nothing. The cart
     * step marks the offending line; this is the block behind that marker.
     */
    blockedReason       : String? = null,
    onBack              : () -> Unit,
    /** Where "Corriger" goes. Defaults to [onBack], which on this graph is the cart. */
    onFixBlocked        : () -> Unit = onBack,
    onConfirm           : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    Column(modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        // One bar, the flow's, as on its other steps (2026-10-19)
        DsTopAppBar(
            title         = title,
            subtitle      = selectedClient?.name,
            subtitleColor = DsColors.Primary,
            leading       = DsTopBarLeading.Back(onBack)
        ) {
            DsStepBadge(stringResource(R.string.common_step_validation), 3, 3)
            Spacer(Modifier.width(DsSpacing.xs))
        }
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── The sale in one frame: the client, its articles and the total, close together (as Achats,
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
                    // Client
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
                            Icon(Icons.Default.Person, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedClient?.name ?: "—", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            Text(
                                stringResource(R.string.common_client_balance_value, money.da(selectedClient?.balance ?: 0.0)),
                                fontSize = DsTextSize.bodySmall,
                                color    = if (Amount.owing(selectedClient?.balance ?: 0.0)) DsColors.Danger else DsColors.TextSecondary
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
                                Text(stringResource(R.string.common_qty_times_price, formatQty(item.quantity), money.da(item.unitPrice)), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text(
                                money.da(item.quantity * item.unitPrice),
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
                        .clip(DsShapes.medium)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.medium)
                        .padding(DsSpacing.md)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.common_amount_paid_da), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        // Locale.ROOT — see PurchaseFormScreen: the field is parsed back with
                        // String.toDoubleOrNull(), which only accepts a '.' decimal separator.
                        TextButton(onClick = { onMontantPayeChange(String.format(Locale.ROOT, "%.2f", total)) }) {
                            Text(stringResource(R.string.common_settled_all), fontSize = DsTextSize.caption, color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(DsSpacing.xs))
                    OutlinedTextField(
                        value           = montantPaye,
                        onValueChange   = { raw -> onMontantPayeChange(westernNumber(raw).filter { it.isDigit() || it == '.' }) },
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
                            Text(stringResource(R.string.common_label_remaining), fontSize = DsTextSize.caption, color = DsColors.Danger)
                            Text(money.da(reste), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                        }
                    } else if (paye >= total && total > 0) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.common_label_status), fontSize = DsTextSize.caption, color = DsColors.Success)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text(stringResource(R.string.common_settled_check), fontSize = DsTextSize.caption, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
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
        // Docked against the button it explains, not trailing the scrolling form — a reason
        // that can be scrolled away from its control is a reason nobody reads.
        blockedReason?.let { reason ->
            CartBlockingBanner(
                text        = reason,
                actionLabel = stringResource(R.string.common_action_fix),
                onAction    = onFixBlocked
            )
        }

        Button(
            onClick  = onConfirm,
            enabled  = !isSaving && blockedReason == null && selectedClient != null && cartItems.isNotEmpty(),
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
                    stringResource(R.string.common_confirm_amount, money.da(total)),
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
    /** The catalogue no longer has this product; the line survives on the draft's own copy. */
    isMissingProduct : Boolean = false,
    isExpanded       : Boolean,
    onToggleExpand   : () -> Unit,
    onQuantityChange : (Double) -> Unit,
    onPriceChange    : (Double) -> Unit,
    onRemove         : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    val availableStock = item.product.camion_stock

    // Only reachable on a resumed draft. Live, the stepper's own ceiling makes it impossible to
    // ask for more than the van holds — so if the cart is over it, the stock moved after the draft
    // was written, and the sale is blocked until the quantity comes down.
    val overStock = item.quantity > availableStock
    // Selling below the purchase price is said on the folded line, where the "qty × price" line is
    // cut short; a blocking problem keeps the line.
    val belowCost = !isMissingProduct && !overStock && isBelowCost(item.unitPrice, item.product.purchase_price)

    SelectionCartCard(
        avatarIcon      = Icons.Default.ShoppingCart,
        title           = item.product.name,
        metaLine        = stringResource(R.string.common_qty_times_price, quantityText(item.quantity, item.product.unit_type).asString(), money.da(item.unitPrice)),
        totalPriceLabel = money.da(item.quantity * item.unitPrice),
        priceColor      = DsColors.PrimaryDark,
        imageRef        = item.product.image_uri,
        neutralAvatar   = true,
        isExpanded      = isExpanded,
        onToggleExpand  = onToggleExpand,
        isDanger   = isMissingProduct || overStock,
        statusLine = {
            CartStatusLine(
                icon = if (isMissingProduct || overStock || belowCost) Icons.Default.Warning
                       else Icons.Default.LocalShipping,
                text = when {
                    isMissingProduct -> stringResource(R.string.common_cart_product_deleted)
                            overStock        -> stringResource(R.string.tournees_cart_truck_short, quantityText(availableStock, item.product.unit_type).asString())
                    belowCost        -> stringResource(R.string.common_cart_below_cost_short, money.da(item.product.purchase_price))
                    else             -> stringResource(R.string.tournees_cart_available, quantityText(availableStock, item.product.unit_type).asString())
                },
                tone = when {
                    isMissingProduct || overStock -> CartStatusTone.DANGER
                    belowCost                     -> CartStatusTone.WARNING
                    else                          -> CartStatusTone.NEUTRAL
                },
                // Both blocking lines are instructions, and an instruction cut in half is worse
                // than none — the readings they replace fit on one line and still do.
                maxLines = if (isMissingProduct || overStock) 2 else 1
            )
        },
        expandedContent = {
            QuantityStepper(
                label         = stringResource(R.string.common_label_quantity),
                value         = item.quantity,
                onValueChange = onQuantityChange,
                // No ceiling while the line is over it: clamping to the live stock would silently
                // rewrite a quantity the user entered, and the whole point of the block is that
                // they get to decide what to do about it.
                max           = if (overStock) null else availableStock,
                formatValue   = ::formatQty,
                min            = Quantity.smallest(item.product.unit_type),
                allowFractions = Quantity.allowsFractions(item.product.unit_type),
            )

            Spacer(Modifier.height(DsSpacing.md))

            PriceFieldWithHistory(
                price         = item.unitPrice,
                onPriceChange = onPriceChange,
                floorPrice    = item.product.purchase_price
            )

            Spacer(Modifier.height(DsSpacing.xs))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_action_remove), color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                }
            }
        }
    )
}
