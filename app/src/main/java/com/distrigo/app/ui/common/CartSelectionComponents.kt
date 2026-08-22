package com.distrigo.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
/**
 * Shared "Ma sélection" cart-item card, used identically by Achat, Vente (Dépôt)
 * and Vente (Tournée). See docs/design/distrigo_ma_selection_unifiee.html for the
 * approved visual/interaction reference this file implements.
 */

// ── Danger avatar background, matches the existing hardcoded value already used
// in VenteFormScreen's VenteCartRow (distinct from DsColors.DangerLight, which is
// used for the card body background instead) ──
private val DangerAvatarBg = Color(0xFFFEE2E2)

@Composable
fun SelectionCartCard(
    avatarIcon: ImageVector,
    title: String,
    metaLine: String,
    totalPriceLabel: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isDanger: Boolean = false,
    statusLine: @Composable () -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isDanger   -> DsColors.Danger
        isExpanded -> DsColors.Primary
        else       -> DsColors.Border
    }
    val containerColor = if (isDanger) DsColors.DangerLight else DsColors.Surface
    val avatarBg        = if (isDanger) DangerAvatarBg else DsColors.PrimaryLight
    val avatarTint       = if (isDanger) DsColors.Danger else DsColors.Primary
    val priceColor       = if (isDanger) DsColors.Danger else DsColors.Primary
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevronRotation")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(containerColor)
            .border(1.5.dp, borderColor, DsShapes.large)
    ) {
        // ── Header (always visible, whole row tappable) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(avatarIcon, contentDescription = null, tint = avatarTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    metaLine,
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text(totalPriceLabel, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = priceColor)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint     = DsColors.TextSecondary,
                modifier = Modifier.size(20.dp).rotate(chevronRotation)
            )
        }

        // ── Status line — always visible, even collapsed ──
        statusLine()

        // ── Expanded content ──
        AnimatedVisibility(
            visible = isExpanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Column {
                HorizontalDivider(color = DsColors.Border)
                Column(modifier = Modifier.padding(DsSpacing.md)) {
                    expandedContent()
                }
            }
        }
    }
}

enum class CartStatusTone { OK, WARNING, DANGER, NEUTRAL }

@Composable
fun CartStatusLine(
    icon: ImageVector,
    text: String,
    tone: CartStatusTone,
    progressFraction: Float? = null,
    modifier: Modifier = Modifier
) {
    val (bg, content) = when (tone) {
        CartStatusTone.OK      -> DsColors.SuccessLight to DsColors.Success
        CartStatusTone.WARNING -> DsColors.WarningLight to DsColors.Warning
        CartStatusTone.DANGER  -> DsColors.DangerLight to DsColors.Danger
        CartStatusTone.NEUTRAL -> DsColors.SurfaceMuted to DsColors.TextSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.md)
            .padding(bottom = DsSpacing.md)
            .clip(DsShapes.small)
            .background(bg)
            .padding(horizontal = DsSpacing.md, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        Text(
            text,
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.SemiBold,
            color      = content,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f)
        )
        if (progressFraction != null) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(DsShapes.pill)
                    .background(DsColors.Border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .clip(DsShapes.pill)
                        .background(content)
                )
            }
        }
    }
}

@Composable
fun QuantityStepper(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double = 1.0,
    max: Double? = null,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val canDecrement = value > min
    val canIncrement = max == null || value < max
    val focusManager = LocalFocusManager.current

    var text by remember(value) { mutableStateOf(formatValue(value)) }

    fun commit() {
        val parsed = text.replace(',', '.').toDoubleOrNull()
        val next = when {
            parsed == null || parsed <= 0.0 -> value
            max != null                      -> parsed.coerceIn(min.coerceAtMost(parsed), max)
            else                              -> parsed.coerceAtLeast(min.coerceAtMost(parsed))
        }.let { if (parsed != null && parsed > 0.0) parsed.coerceIn(min.coerceAtMost(parsed), max ?: Double.MAX_VALUE) else value }
        text = formatValue(next)
        if (next != value) onValueChange(next)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(
                onClick  = { onValueChange(maxOf(min, value - 1.0)) },
                enabled  = canDecrement,
                modifier = Modifier.size(44.dp).alpha(if (canDecrement) 1f else 0.4f).clip(DsShapes.medium).background(DsColors.SurfaceSunken)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Diminuer", tint = DsColors.TextPrimary, modifier = Modifier.size(18.dp))
            }

            OutlinedTextField(
                value           = text,
                onValueChange   = { raw ->
                    val filtered = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        text = filtered
                        // نُحدّث البطاقة فوراً بمجرد أن يصبح النص رقماً كاملاً وصالحاً ضمن الحدود —
                        // الحالات غير المكتملة ("0"، "0.") تُترك محلياً بلا تحديث، وهذا وحده يكفي لتفادي الخلل القديم
                        val parsed = filtered.toDoubleOrNull()
                        if (parsed != null && parsed >= min && (max == null || parsed <= max)) {
                            onValueChange(parsed)
                        }
                    }
                },
                modifier        = Modifier
                    .widthIn(min = 72.dp)
                    .padding(horizontal = DsSpacing.sm)
                    .onFocusChanged { if (!it.isFocused) commit() },
                singleLine      = true,
                shape           = DsShapes.medium,
                textStyle       = LocalTextStyle.current.copy(
                    fontSize   = DsTextSize.headline,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.Primary,
                    textAlign  = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(); focusManager.clearFocus() }),
                colors          = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            IconButton(
                onClick  = { if (max != null) onValueChange(minOf(max, value + 1.0)) else onValueChange(value + 1.0) },
                enabled  = canIncrement,
                modifier = Modifier.size(44.dp).alpha(if (canIncrement) 1f else 0.4f).clip(DsShapes.medium).background(DsColors.SurfaceSunken)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Augmenter", tint = DsColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PriceFieldWithHistory(
    price: Double,
    onPriceChange: (Double) -> Unit,
    priceHistory: List<Double> = emptyList(),
    modifier: Modifier = Modifier
) {
    var priceStr    by remember(price) { mutableStateOf("%.2f".format(price)) }
    var showHistory by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Prix unitaire (DA)", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            OutlinedTextField(
                value         = priceStr,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() || it == '.' }.let { s ->
                        val dot = s.indexOf('.')
                        if (dot < 0) s
                        else s.substring(0, dot + 1) + s.substring(dot + 1).filter { it.isDigit() }
                    }
                    priceStr = filtered
                    val parsed = filtered.toDoubleOrNull()
                    if (parsed != null && parsed >= 0) onPriceChange(parsed)
                },
                modifier        = Modifier.weight(1f),
                singleLine      = true,
                shape           = DsShapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors          = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            if (priceHistory.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick  = { showHistory = true },
                        modifier = Modifier.size(44.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Historique des prix",
                            tint     = DsColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded         = showHistory,
                        onDismissRequest = { showHistory = false },
                        modifier         = Modifier.background(DsColors.Surface)
                    ) {
                        Text(
                            "DERNIERS PRIX",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.TextSecondary,
                            modifier   = Modifier.padding(horizontal = DsSpacing.md, vertical = 6.dp)
                        )
                        priceHistory.take(4).forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${"%.2f".format(p)} DA",
                                        fontSize   = DsTextSize.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = DsColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    priceStr = "%.2f".format(p)
                                    onPriceChange(p)
                                    showHistory = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryToggleField(
    hasExpiry: Boolean,
    expiryDate: String?,
    onHasExpiryChange: (Boolean) -> Unit,
    onExpiryDateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showExpiryPicker by remember { mutableStateOf(false) }
    val expiryDateState = rememberDatePickerState(
        initialSelectedDateMillis = expiryDate?.let {
            runCatching {
                LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
    )

    if (showExpiryPicker) {
        DatePickerDialog(
            onDismissRequest = { showExpiryPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryDateState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        onExpiryDateChange(date)
                    }
                    showExpiryPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showExpiryPicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = expiryDateState) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceSunken)
            .padding(DsSpacing.md)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                Column {
                    Text("Date d'expiration", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                    Text("Le produit a une date d'expiration", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }
            Switch(
                checked         = hasExpiry,
                onCheckedChange = onHasExpiryChange,
                colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DsColors.Primary)
            )
        }

        AnimatedVisibility(visible = hasExpiry) {
            Column(modifier = Modifier.padding(top = DsSpacing.sm)) {
                OutlinedTextField(
                    value         = expiryDate ?: "",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Date d'expiration", fontSize = DsTextSize.caption) },
                    trailingIcon  = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth().clickable { showExpiryPicker = true },
                    shape         = DsShapes.medium,
                    enabled       = false,
                    colors        = dsTextFieldColors(
                        disabledBorderColor      = DsColors.Border,
                        disabledTextColor        = DsColors.TextPrimary,
                        disabledLabelColor       = DsColors.TextSecondary,
                        disabledTrailingIconColor = DsColors.Primary
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PREVIEW — one Achat-style card + one Vente Dépôt-style card (danger state)
// ═══════════════════════════════════════════════════════════

private fun formatPreviewQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

@Preview(showBackground = true, widthDp = 380, name = "Ma sélection — cartes unifiées")
@Composable
private fun SelectionCartCardsPreview() {
    var achatExpanded by remember { mutableStateOf(true) }
    var venteExpanded  by remember { mutableStateOf(true) }
    var achatQty   by remember { mutableStateOf(2.0) }
    var achatPrice by remember { mutableStateOf(120.0) }
    var venteQty   by remember { mutableStateOf(6.0) }
    var ventePrice by remember { mutableStateOf(30.0) }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .background(DsColors.SurfaceSunken)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        // ── Achat — carton stepper card, OK stock status ──
        SelectionCartCard(
            avatarIcon      = Icons.Default.Inventory2,
            title           = "COCCA 2L",
            metaLine        = "${formatPreviewQty(achatQty)} carton × ${"%.2f".format(achatPrice)} DA",
            totalPriceLabel = "${"%.2f".format(achatQty * achatPrice)} DA",
            isExpanded      = achatExpanded,
            onToggleExpand  = { achatExpanded = !achatExpanded },
            statusLine = {
                CartStatusLine(
                    icon  = Icons.Default.ArrowUpward,
                    text  = "Stock 10 → ${formatPreviewQty(10.0 + achatQty)} carton",
                    tone  = CartStatusTone.OK
                )
            },
            expandedContent = {
                QuantityStepper(
                    label         = "Nombre de cartons",
                    value         = achatQty,
                    onValueChange = { achatQty = it },
                    formatValue   = ::formatPreviewQty
                )
                Spacer(Modifier.height(DsSpacing.md))
                PriceFieldWithHistory(
                    price         = achatPrice,
                    onPriceChange = { achatPrice = it },
                    priceHistory  = listOf(120.0, 115.0, 120.0, 110.0)
                )
            }
        )

        // ── Vente Dépôt — danger state (rupture de stock) ──
        SelectionCartCard(
            avatarIcon      = Icons.Default.ShoppingCart,
            title           = "biscuit soummam",
            metaLine        = "${formatPreviewQty(venteQty)} pièce × ${"%.2f".format(ventePrice)} DA",
            totalPriceLabel = "${"%.2f".format(venteQty * ventePrice)} DA",
            isExpanded      = venteExpanded,
            onToggleExpand  = { venteExpanded = !venteExpanded },
            isDanger        = true,
            statusLine = {
                CartStatusLine(
                    icon             = Icons.Default.Warning,
                    text             = "Rupture — dépassement de 2 pièce",
                    tone             = CartStatusTone.DANGER,
                    progressFraction = 0f
                )
            },
            expandedContent = {
                QuantityStepper(
                    label         = "Quantité",
                    value         = venteQty,
                    onValueChange = { venteQty = it },
                    formatValue   = ::formatPreviewQty
                )
                Spacer(Modifier.height(DsSpacing.md))
                PriceFieldWithHistory(
                    price         = ventePrice,
                    onPriceChange = { ventePrice = it }
                )
            }
        )
    }
}