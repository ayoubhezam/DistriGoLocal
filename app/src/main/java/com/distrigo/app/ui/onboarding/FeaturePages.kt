package com.distrigo.app.ui.onboarding

import com.distrigo.app.data.license.licText
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing

internal class Feature(val icon: ImageVector, val title: String, val detail: String)

internal class FeaturePageContent(
    @DrawableRes val illustration: Int,
    val title: String,
    val subtitle: String,
    val features: List<Feature>,
)

/** The pages after the welcome screen, in the mockup's order and words. */
internal val FEATURE_PAGES = listOf(
    FeaturePageContent(
        illustration = R.drawable.onboarding_offline,
        title        = licText(R.string.onb_f1_title),
        subtitle     = licText(R.string.onb_f1_sub),
        features     = listOf(
            Feature(Icons.Filled.WifiOff, licText(R.string.onb_f1_a), licText(R.string.onb_f1_a_sub)),
            Feature(Icons.Filled.PhonelinkLock, licText(R.string.onb_f1_b), licText(R.string.onb_f1_b_sub)),
            // The mockup said « Optimisez vos déplacements »: the app plans no routes
            Feature(Icons.Filled.Map, licText(R.string.onb_f1_c), licText(R.string.onb_f1_c_sub)),
        ),
    ),
    FeaturePageContent(
        illustration = R.drawable.onboarding_stock,
        title        = licText(R.string.onb_f2_title),
        subtitle     = licText(R.string.onb_f2_sub),
        features     = listOf(
            Feature(Icons.Filled.Inventory2, licText(R.string.onb_f2_a), licText(R.string.onb_f2_a_sub)),
            Feature(Icons.Filled.LocalShipping, licText(R.string.onb_f2_b), licText(R.string.onb_f2_b_sub)),
            Feature(Icons.Filled.QrCodeScanner, licText(R.string.onb_f2_c), licText(R.string.onb_f2_c_sub)),
        ),
    ),
    FeaturePageContent(
        illustration = R.drawable.onboarding_clients,
        title        = licText(R.string.onb_f3_title),
        subtitle     = licText(R.string.onb_f3_sub),
        features     = listOf(
            Feature(Icons.Filled.Person, licText(R.string.onb_f3_a), licText(R.string.onb_f3_a_sub)),
            Feature(Icons.Filled.CreditCard, licText(R.string.onb_f3_b), licText(R.string.onb_f3_b_sub)),
            Feature(Icons.Filled.Alarm, licText(R.string.onb_f3_c), licText(R.string.onb_f3_c_sub)),
        ),
    ),
    FeaturePageContent(
        illustration = R.drawable.onboarding_pilotez,
        title        = licText(R.string.onb_f4_title),
        subtitle     = licText(R.string.onb_f4_sub),
        features     = listOf(
            Feature(Icons.Filled.BarChart, licText(R.string.onb_f4_a), licText(R.string.onb_f4_a_sub)),
            Feature(Icons.Filled.Description, licText(R.string.onb_f4_b), licText(R.string.onb_f4_b_sub)),
            // The mockup said « Reçus et bons de livraison »: the app prints sales receipts and purchase vouchers
            Feature(Icons.Filled.Print, licText(R.string.onb_f4_c), licText(R.string.onb_f4_c_sub)),
        ),
    ),
)

// 20dp, not 24: « Vos données sur votre téléphone » then fits one line on a 360dp-wide phone
private val PageGutter = DsSpacing.xl
private val ControlHeight = 52.dp
private val FeatureIconSize = 40.dp

/**
 * One feature page: the illustration takes whatever height the text leaves, so the words never move
 * whatever the screen; the last page ends on « Créer mon compte » instead of the page controls.
 */
@Composable
internal fun FeaturePage(
    content: FeaturePageContent,
    page: Int,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DsBrand.Mist)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Image(
            painter            = painterResource(content.illustration),
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.md)
        )
        Column(Modifier.padding(horizontal = PageGutter)) {
            Text(
                content.title,
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 24.sp,
                lineHeight = 30.sp,
                color      = DsBrand.Ink
            )
            Spacer(Modifier.height(DsSpacing.sm))
            Text(
                content.subtitle,
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                fontSize   = 14.sp,
                lineHeight = 20.sp,
                color      = DsBrand.Blue
            )
            Spacer(Modifier.height(DsSpacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm + 2.dp)) {
                content.features.forEach { FeatureRow(it) }
            }
        }
        Spacer(Modifier.height(DsSpacing.lg))
        if (page == ONBOARDING_PAGE_COUNT - 1) {
            CreateAccountButton(onCreateAccount)
        } else {
            PageControls(page, onSkip, onNext)
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(Color.White)
            .border(1.dp, DsBrand.CardBorder, DsShapes.medium)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(FeatureIconSize).clip(CircleShape).background(DsBrand.Blue),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column {
            Text(
                feature.title,
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                lineHeight = 18.sp,
                color      = DsBrand.Ink
            )
            Text(
                feature.detail,
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                fontSize   = 12.sp,
                lineHeight = 16.sp,
                color      = DsBrand.Slate
            )
        }
    }
}

/** « Passer » on the left, the dots in the middle, the next-page arrow on the right. */
@Composable
private fun PageControls(page: Int, onSkip: () -> Unit, onNext: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = DsSpacing.sm, end = PageGutter, bottom = DsSpacing.md)
    ) {
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(licText(R.string.onb_skip), fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DsBrand.Ink)
        }
        PageDots(
            current  = page,
            active   = DsBrand.Ink,
            inactive = DsBrand.Ink.copy(alpha = 0.18f),
            modifier = Modifier.align(Alignment.Center)
        )
        FilledIconButton(
            onClick  = onNext,
            modifier = Modifier.align(Alignment.CenterEnd).size(ControlHeight),
            colors   = IconButtonDefaults.filledIconButtonColors(containerColor = DsBrand.Ink, contentColor = Color.White)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = licText(R.string.onb_next_page))
        }
    }
}

@Composable
private fun CreateAccountButton(onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PageGutter, end = PageGutter, bottom = DsSpacing.md)
            .height(ControlHeight),
        shape    = DsShapes.pill,
        colors   = ButtonDefaults.buttonColors(containerColor = DsBrand.Ink, contentColor = Color.White)
    ) {
        Text(licText(R.string.onb_create_my_account), fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.width(DsSpacing.sm))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
