package com.distrigo.app.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.license.LicensePolicy

/**
 * The license on the screens (docs/license_l5_enforcement.md §2): one per app, provided at the root as
 * [LocalLicense], so any screen reads it like StockPolicy.
 *
 * A control that starts a write stays visible and wraps what it does in [runOrExplain]: when recording is off, the
 * « Consultation seule » sheet opens instead (§8.1). The database's write gate is the rule; this only explains.
 */
@Stable
class LicenseUi(
    /** What the banner's and the sheet's buttons do: open Abonnement or Compte, check in, open the date settings. */
    val act: (LicensePolicy.Action) -> Unit = {},
    /** The day a warning banner was closed with its text (decision 5), and where to keep it. */
    dismissed: String? = null,
    private val onDismissed: (String) -> Unit = {},
    private val today: () -> String = { java.time.LocalDate.now().toString() },
) {
    var policy: LicensePolicy by mutableStateOf(LicensePolicy(canRecord = true))

    /** The sheet open now, if any. */
    var shown: LicensePolicy.Block? by mutableStateOf(null)
        private set

    /** `{day}|{text}` of the warning closed last; restored from the app's preferences at start. */
    var dismissedKey: String? by mutableStateOf(dismissed)

    /** Runs [action] when the license allows recording; otherwise opens the sheet that says why. */
    fun runOrExplain(action: () -> Unit) {
        val block = policy.block
        if (!policy.canRecord && block != null) shown = block else action()
    }

    /** For a save function: true, with the sheet open, when the license refuses; the caller then returns. */
    fun refuses(): Boolean {
        val block = policy.block
        if (policy.canRecord || block == null) return false
        shown = block
        return true
    }

    /** Recording is off: the truck's screens let its stock go down (an unload, D4), never up. */
    val unloadOnly: Boolean
        get() = !policy.canRecord && policy.block != null

    /**
     * The most the truck may hold of a product after this chargement: [stockCap] (strict stock), and, while only
     * unloading is allowed, no more than it holds now ([camionNow]).
     */
    fun camionCap(stockCap: Double?, camionNow: Double): Double? =
        if (unloadOnly) minOf(stockCap ?: camionNow, camionNow) else stockCap

    /** [action] wrapped in [runOrExplain], for an `onClick`. */
    fun guarded(action: () -> Unit): () -> Unit = { runOrExplain(action) }

    /** Opens the sheet for the current state, if it refuses: a write refused by the gate rather than by a button. */
    fun explain() {
        policy.block?.takeIf { !policy.canRecord }?.let { shown = it }
    }

    fun closeSheet() {
        shown = null
    }

    /** The banner to show: a warning closed today stays closed until tomorrow, or until its text changes. */
    val banner: LicensePolicy.Banner?
        get() = policy.banner?.takeUnless { it.dismissible && dismissedKey == key(it) }

    fun dismiss(banner: LicensePolicy.Banner) {
        val key = key(banner)
        dismissedKey = key
        onDismissed(key)
    }

    private fun key(banner: LicensePolicy.Banner) = "${today()}|${banner.text}"

    companion object {
        /** Previews and tests: everything allowed, nothing said. */
        val ALLOWING = LicenseUi()
    }
}

val LocalLicense = staticCompositionLocalOf { LicenseUi.ALLOWING }

/**
 * A write the database's gate refused, whatever screen tried it (userMessage): the root opens the « Consultation
 * seule » sheet for it, as a guarded button would have.
 */
object LicenseRefusals {
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: kotlinx.coroutines.flow.SharedFlow<Unit> = _events

    fun report() {
        _events.tryEmit(Unit)
    }
}

/**
 * The license's line under a top bar (§2): amber for a warning, red when recording stopped or is about to, with its
 * button; a warning can be closed for the day. Nothing when the license has nothing to say.
 */
@Composable
fun DsLicenseBanner(license: LicenseUi = LocalLicense.current) {
    val banner = license.banner ?: return
    if (banner.card) return LicenseCard(banner, license)
    val readOnly = banner.tone == LicensePolicy.Tone.READ_ONLY
    val tint = if (readOnly) DsColors.Danger else DsColors.Warning
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(if (readOnly) DsColors.DangerLight else DsColors.WarningLight)
            .padding(start = DsSpacing.lg, end = DsSpacing.xs, top = DsSpacing.xs, bottom = DsSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (readOnly) Icons.Outlined.Lock else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint     = tint,
            modifier = Modifier.size(BrandSize.RowIcon)
        )
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            banner.text,
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.SemiBold,
            fontSize   = BrandSize.Small,
            lineHeight = BrandSize.SmallLine,
            color      = if (readOnly) DsColors.Danger else DsColors.TextPrimary,
            modifier   = Modifier.weight(1f).padding(vertical = DsSpacing.xs)
        )
        banner.action?.let { action ->
            TextButton(onClick = { license.act(action) }) {
                Text(action.label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Small, color = DsBrand.Ink)
            }
        }
        if (banner.dismissible) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable { license.dismiss(banner) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer pour aujourd'hui", tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * D4 in the truck's and the open tournée's screens: while the license does not record, the tournée can still be closed
 * and the truck unloaded toward the dépôt (docs/license_l5_enforcement.md §7, decision 3), and this line says so.
 * Nothing otherwise.
 */
@Composable
fun DsUnloadOnlyNote(
    text: String = "Consultation seule : vous pouvez encore décharger le camion vers le dépôt, pas le charger.",
    license: LicenseUi = LocalLicense.current,
) {
    if (!license.unloadOnly) return
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(DsColors.WarningLight)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(BrandSize.RowIcon))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            text,
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.SemiBold,
            fontSize   = BrandSize.Small,
            lineHeight = BrandSize.SmallLine,
            color      = DsColors.TextPrimary
        )
    }
}

/**
 * The phone's date set back more than a day (§4): a full-width card in the banner's place, its title, what to do, and
 * « Régler la date ». Coming back from the settings re-reads the license at once (LicenseInFront).
 */
@Composable
private fun LicenseCard(banner: LicensePolicy.Banner, license: LicenseUi) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)
            .clip(DsShapes.medium)
            .background(DsColors.DangerLight)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(banner.text, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.CardTitle, color = DsColors.Danger)
        }
        banner.detail?.let {
            Text(it, fontFamily = DsBrand.Font, fontSize = BrandSize.Body, lineHeight = BrandSize.BodyLine, color = DsColors.TextPrimary)
        }
        banner.action?.let { action ->
            BrandWideButton(label = action.label, icon = action.icon, filled = true, enabled = true) { license.act(action) }
        }
    }
}

/**
 * « Consultation seule » (§8.1): why the write is refused, what still works, and the one thing to do about it.
 * Hosted once, at the root, for [LicenseUi.shown].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseBlockSheet(license: LicenseUi = LocalLicense.current) {
    val block = license.shown ?: return
    ModalBottomSheet(
        onDismissRequest = license::closeSheet,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(start = DsSpacing.xl, end = DsSpacing.xl, bottom = DsSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(DsColors.DangerLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(24.dp))
            }
            Text(
                "Consultation seule",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = BrandSize.DialogTitle,
                color      = DsBrand.Ink
            )
            Text(
                block.reason,
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.SemiBold,
                fontSize   = BrandSize.Body,
                lineHeight = BrandSize.BodyLine,
                color      = DsColors.TextPrimary,
                textAlign  = TextAlign.Center
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = DsBrand.Slate, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(DsSpacing.xs))
                Text(
                    LicensePolicy.STILL_WORKS,
                    fontFamily = DsBrand.Font,
                    fontSize   = BrandSize.Small,
                    lineHeight = BrandSize.SmallLine,
                    color      = DsBrand.Slate,
                    textAlign  = TextAlign.Center
                )
            }
            Spacer(Modifier.height(DsSpacing.sm))
            BrandWideButton(label = block.actionLabel, icon = block.action.icon, filled = true, enabled = true) {
                license.closeSheet()
                license.act(block.action)
            }
            TextButton(onClick = license::closeSheet, modifier = Modifier.fillMaxWidth()) {
                Text("Fermer", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Slate)
            }
        }
    }
}

private val LicensePolicy.Action.icon: ImageVector
    get() = when (this) {
        LicensePolicy.Action.SUBSCRIPTION -> Icons.Outlined.Autorenew
        LicensePolicy.Action.ACCOUNT -> Icons.Outlined.Person
        LicensePolicy.Action.RETRY -> Icons.Outlined.Refresh
        LicensePolicy.Action.DATE_SETTINGS -> Icons.Outlined.Schedule
    }
