package com.distrigo.app.ui.account

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import com.distrigo.app.data.billing.BillingPeriod
import com.distrigo.app.data.billing.PlanOffer
import com.distrigo.app.data.license.licText
import com.distrigo.app.ui.common.ltrIsolate
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardBody
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing

/**
 * The paywall at the top of « Abonnement » (docs/play_billing.md §3): Google Play's plans with their own prices, or
 * the step before (an e-mail for an account made by a code), or why Google Play can't sell here.
 */
@Composable
internal fun PaywallCard(
    step: PaywallStep,
    onSelect: (BillingPeriod) -> Unit,
    onBuy: () -> Unit,
    onAddEmail: () -> Unit,
    onManage: (productId: String) -> Unit,
) {
    BrandCard {
        BrandCardHeader(Icons.Outlined.WorkspacePremium, licText(R.string.bill_title))
        BrandCardBody {
            when (step) {
                PaywallStep.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsBrand.Blue)
                }
                PaywallStep.SignInFirst -> PaywallText(licText(R.string.acc_sign_in_first))
                PaywallStep.NeedsEmail -> {
                    PaywallText(licText(R.string.bill_needs_email))
                    BrandWideButton(licText(R.string.acc_add_email), Icons.Outlined.Email, filled = true, enabled = true, onClick = onAddEmail)
                }
                is PaywallStep.Unavailable -> PaywallText(Paywall.unavailableText(step.reason))
                is PaywallStep.Choose -> {
                    Column(Modifier.selectableGroup()) {
                        step.offers.forEach { offer ->
                            PlanRow(offer, selected = offer.period == step.selected, enabled = !step.busy && step.owned == null) { onSelect(offer.period) }
                            Spacer(Modifier.padding(top = DsSpacing.sm))
                        }
                    }
                    if (step.owned != null) {
                        PaywallText(licText(R.string.bill_owned))
                        BrandWideButton(
                            licText(R.string.bill_manage), Icons.AutoMirrored.Outlined.OpenInNew, filled = false, enabled = true,
                            onClick = { onManage(step.owned.productId) },
                        )
                    } else {
                        BrandWideButton(
                            licText(R.string.bill_subscribe), Icons.Outlined.WorkspacePremium, filled = true,
                            enabled = !step.busy, busy = step.busy, onClick = onBuy,
                        )
                        Text(
                            licText(R.string.bill_renewal_note),
                            fontFamily = DsBrand.Font,
                            fontSize   = BrandSize.Small,
                            lineHeight = BrandSize.SmallLine,
                            color      = DsBrand.Slate,
                        )
                    }
                }
            }
        }
    }
}

/** One plan: « Mensuel » or « Annuel », and Google Play's price for it, written in the buyer's currency. */
@Composable
private fun PlanRow(offer: PlanOffer, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, if (selected) DsBrand.Blue else DsBrand.CardBorder, DsShapes.medium)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = DsBrand.Blue))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            licText(if (offer.period == BillingPeriod.MONTHLY) R.string.bill_monthly else R.string.bill_yearly),
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Bold,
            fontSize   = BrandSize.Body,
            color      = DsBrand.Ink,
            modifier   = Modifier.weight(1f),
        )
        Text(
            licText(
                if (offer.period == BillingPeriod.MONTHLY) R.string.bill_price_per_month else R.string.bill_price_per_year,
                ltrIsolate(offer.formattedPrice),
            ),
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.SemiBold,
            fontSize   = BrandSize.Body,
            color      = DsBrand.Ink,
        )
    }
}

@Composable
private fun PaywallText(text: String) {
    Text(text, fontFamily = DsBrand.Font, fontSize = BrandSize.Body, lineHeight = BrandSize.BodyLine, color = DsBrand.Slate)
}

/** « J'ai un code d'activation »: the code, folded under the paywall while Google Play sells (not promoted). */
@Composable
internal fun CodeFoldLink(onOpen: () -> Unit) {
    Text(
        licText(R.string.bill_have_code),
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.SemiBold,
        fontSize   = BrandSize.Small,
        color      = DsBrand.Slate,
        modifier   = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = DsSpacing.sm),
    )
}
