package com.distrigo.app.ui.charges

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.distrigo.app.ui.format.LocalMoneyFormatter

/**
 * The expense form: one screen, the amount first.
 *
 * It replaced a two-step wizard — details, then a summary to confirm — that took two screens and
 * three taps after the amount for the most routine entry in the app. Here the amount field is focused
 * with the keyboard up as the screen opens; the date and time are "now" on one chip, changed only
 * when they are not; a subtype's recent amounts and suppliers are one tap away; and the button,
 * which the keyboard never covers, says what it will save. The summary's safety net is an "Annuler"
 * on the list after saving (see ChargeSavedSnackbar).
 *
 * An existing expense reaches it only through its read-only details and a confirmed "Modifier"
 * (ChargeDetailScreen), which is also where it is deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeEntryScreen(
    onBack    : () -> Unit,
    onSaved   : (SavedCharge) -> Unit,
    viewModel : ChargeEntryViewModel = hiltViewModel()
) {
    val money = LocalMoneyFormatter.current
    val vm = viewModel
    var confirmLeave by remember { mutableStateOf(false) }
    var showWhen by remember { mutableStateOf(false) }

    fun attemptBack() { if (vm.isDirty && !vm.saving) confirmLeave = true else onBack() }
    BackHandler { attemptBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title    = if (vm.isEdit) "Modifier la dépense" else "Nouvelle dépense",
            subtitle = vm.subtitle.ifEmpty { null },
            leading  = DsTopBarLeading.Back { attemptBack() }
        )

        if (!vm.loaded) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            return@Column
        }

        Column(
            modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            if (vm.choosesSubtype) {
                SubtypePicker(subTypes = vm.subTypes, selected = vm.subtypeId, onSelect = vm::selectSubtype)
            }

            AmountHero(
                amount    = vm.amount,
                onChange  = vm::onAmountChange,
                autoFocus = !vm.isEdit,
                recent    = vm.recentAmounts,
                onRecent  = vm::useAmount
            )

            Column(
                modifier            = Modifier.padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                WhenChip(date = vm.date, time = vm.time, onClick = { showWhen = true })

                if (vm.showsSupplier) {
                    SupplierField(
                        value     = vm.fournisseur,
                        onChange  = { vm.fournisseur = it },
                        suggested = vm.recentSuppliers
                    )
                }

                if (vm.noteOpen) {
                    OutlinedTextField(
                        value         = vm.note,
                        onValueChange = { vm.note = it },
                        label         = { Text("Note") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = DsShapes.medium,
                        minLines      = 2,
                        maxLines      = 4,
                        colors        = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary)
                    )
                } else {
                    TextButton(onClick = { vm.noteOpen = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(DsSpacing.xs))
                        Text("Ajouter une note", fontSize = DsTextSize.body)
                    }
                }
            }
            Spacer(Modifier.height(DsSpacing.sm))
        }

        vm.error?.let {
            Text(
                it,
                color    = DsColors.Danger,
                fontSize = DsTextSize.bodySmall,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }

        // The window shrinks above the keyboard (the root's imePadding), so this stays reachable
        // while typing: the amount and the save are one gesture apart.
        Button(
            onClick  = { vm.save(onSaved) },
            enabled  = vm.canSave,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (vm.saving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                val value = vm.amountValue
                Text(
                    when {
                        vm.isEdit     -> "Enregistrer les modifications"
                        value != null -> "Enregistrer · ${money.da(value)}"
                        else          -> "Enregistrer"
                    },
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showWhen) {
        WhenSheet(
            date      = vm.date,
            time      = vm.time,
            onDate    = { vm.date = it },
            onTime    = { vm.time = it },
            onDismiss = { showWhen = false }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title            = { Text(if (vm.isEdit) "Abandonner les modifications ?" else "Abandonner cette dépense ?") },
            text             = { Text("Ce qui a été saisi sera perdu.") },
            confirmButton    = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Abandonner", color = DsColors.Danger) } },
            dismissButton    = { TextButton(onClick = { confirmLeave = false }) { Text("Continuer la saisie") } },
            containerColor   = DsColors.Surface
        )
    }

}

// ── Pieces ───────────────────────────────────────────────────────────────────

/** The quick entry's categories, one row of chips, scrolled to the one selected. */
@Composable
private fun SubtypePicker(subTypes: List<ChargeSubType>, selected: Int?, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(subTypes, selected) {
        val index = subTypes.indexOfFirst { it.id == selected }
        if (index > 0) listState.scrollToItem(index)
    }
    LazyRow(
        state                 = listState,
        contentPadding        = PaddingValues(horizontal = DsSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        itemsIndexed(subTypes, key = { _, sub -> sub.id }) { _, sub ->
            val active = sub.id == selected
            Row(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(if (active) DsColors.Primary else DsColors.Surface)
                    .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.pill)
                    .clickable { onSelect(sub.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    ChargeIconMapper.iconFor(sub.icon),
                    contentDescription = null,
                    tint     = if (active) Color.White else DsColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    sub.name,
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color      = if (active) Color.White else DsColors.TextPrimary
                )
            }
        }
    }
}

/** The amount, large and first, focused on opening; the subtype's recent amounts under it. */
@Composable
private fun AmountHero(
    amount    : String,
    onChange  : (String) -> Unit,
    autoFocus : Boolean,
    recent    : List<Double>,
    onRecent  : (Double) -> Unit
) {
    val money = LocalMoneyFormatter.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) { focus.requestFocus(); keyboard?.show() }
    }

    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Montant", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            BasicTextField(
                value                = amount,
                onValueChange        = onChange,
                singleLine           = true,
                visualTransformation = AmountVisualTransformation,
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                cursorBrush          = SolidColor(DsColors.Primary),
                textStyle            = TextStyle(
                    fontSize   = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.TextPrimary,
                    textAlign  = TextAlign.Center
                ),
                modifier             = Modifier.focusRequester(focus).width(IntrinsicSize.Min).widthIn(min = 48.dp),
                decorationBox        = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (amount.isEmpty()) {
                            Text("0", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = DsColors.TextTertiary)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(DsSpacing.sm))
            Text("DA", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        HorizontalDivider(color = DsColors.Border, modifier = Modifier.padding(top = DsSpacing.xs).fillMaxWidth(0.6f))

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(DsSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                recent.forEach { value ->
                    Text(
                        money.da(value),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.Primary,
                        modifier   = Modifier
                            .clip(DsShapes.pill)
                            .background(DsColors.PrimaryLight)
                            .clickable { onRecent(value) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** "Aujourd'hui · 14:32": the date and time on one chip, "now" until changed. */
@Composable
private fun WhenChip(date: LocalDate, time: LocalTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Event, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.md))
        Text(
            "${dayLabel(date)} · ${time.format(TIME)}",
            fontSize   = DsTextSize.body,
            fontWeight = FontWeight.Medium,
            color      = DsColors.TextPrimary,
            modifier   = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, contentDescription = "Changer la date", tint = DsColors.TextTertiary)
    }
}

/** Today and yesterday one tap away; any other day and the time through their pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenSheet(
    date      : LocalDate,
    time      : LocalTime,
    onDate    : (LocalDate) -> Unit,
    onTime    : (LocalTime) -> Unit,
    onDismiss : () -> Unit
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DsColors.Surface) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg).padding(bottom = DsSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            Text("Date et heure", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                DayOption("Aujourd'hui", selected = date == today, modifier = Modifier.weight(1f)) { onDate(today) }
                DayOption("Hier", selected = date == today.minusDays(1), modifier = Modifier.weight(1f)) { onDate(today.minusDays(1)) }
                DayOption(
                    if (date != today && date != today.minusDays(1)) dayLabel(date) else "Autre date",
                    selected = date != today && date != today.minusDays(1),
                    modifier = Modifier.weight(1f)
                ) { pickDate = true }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.medium)
                    .background(DsColors.SurfaceSunken)
                    .clickable { pickTime = true }
                    .padding(horizontal = DsSpacing.md, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.md))
                Text("Heure", fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(time.format(TIME), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            }
            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) { Text("OK", fontWeight = FontWeight.SemiBold) }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton    = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton    = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title            = { Text("Heure") },
            text             = { TimePicker(state = state) },
            confirmButton    = { TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); pickTime = false }) { Text("OK") } },
            dismissButton    = { TextButton(onClick = { pickTime = false }) { Text("Annuler") } },
            containerColor   = DsColors.Surface
        )
    }
}

@Composable
private fun DayOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(DsShapes.medium)
            .background(if (selected) DsColors.Primary else DsColors.Surface)
            .border(1.dp, if (selected) DsColors.Primary else DsColors.Border, DsShapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize   = DsTextSize.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else DsColors.TextPrimary,
            maxLines   = 1
        )
    }
}

/** The supplier or station, with the ones this subtype used before one tap away. */
@Composable
private fun SupplierField(value: String, onChange: (String) -> Unit, suggested: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            label         = { Text("Fournisseur / Station") },
            placeholder   = { Text("Ex: Station Naftal - Hydra") },
            leadingIcon   = { Icon(Icons.Default.Storefront, contentDescription = null, tint = DsColors.TextSecondary) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = DsShapes.medium,
            colors        = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary)
        )
        val offered = suggested.filterNot { it.equals(value.trim(), ignoreCase = true) }
        if (offered.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                itemsIndexed(offered) { _, name ->
                    Text(
                        name,
                        fontSize = DsTextSize.bodySmall,
                        color    = DsColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .border(1.dp, DsColors.Border, DsShapes.pill)
                            .clickable { onChange(name) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/** "Aujourd'hui", "Hier", "12 sept.", or with its year when it is not this one. */
private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today                -> "Aujourd'hui"
        date == today.minusDays(1)   -> "Hier"
        date.year == today.year      -> date.format(DAY)
        else                         -> date.format(DAY_YEAR)
    }
}
