package com.distrigo.app.ui.onboarding

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
        title        = "Travaillez sans Internet,\nmême en tournée",
        subtitle     = "Accédez à toutes vos données et continuez à travailler, même sans connexion.",
        features     = listOf(
            Feature(Icons.Filled.WifiOff, "Mode hors ligne", "Vos données toujours disponibles"),
            Feature(Icons.Filled.PhonelinkLock, "Vos données sur votre téléphone", "Sécurité et confidentialité"),
            // The mockup said « Optimisez vos déplacements »: the app plans no routes
            Feature(Icons.Filled.Map, "Suivi des tournées", "Même sans réseau"),
        ),
    ),
    FeaturePageContent(
        illustration = R.drawable.onboarding_stock,
        title        = "Gérez votre stock\net chargez vos camions",
        subtitle     = "Gardez le contrôle de votre inventaire et préparez vos tournées en toute simplicité.",
        features     = listOf(
            Feature(Icons.Filled.Inventory2, "Gestion des produits", "Catégories, marques, fournisseurs"),
            Feature(Icons.Filled.LocalShipping, "Chargement / déchargement", "Suivi du stock camion"),
            Feature(Icons.Filled.QrCodeScanner, "Code-barres et inventaire", "Gagnez du temps, évitez les erreurs"),
        ),
    ),
    FeaturePageContent(
        illustration = R.drawable.onboarding_clients,
        title        = "Suivez vos ventes\net vos clients",
        subtitle     = "Pilotez votre activité commerciale et entretenez vos relations clients.",
        features     = listOf(
            Feature(Icons.Filled.Person, "Vos clients", "Fiches, géolocalisation, historique"),
            Feature(Icons.Filled.CreditCard, "Ventes et règlements", "Tout en un seul endroit"),
            Feature(Icons.Filled.Alarm, "Crédits et encours", "Maîtrisez vos risques"),
        ),
    ),
    FeaturePageContent(
        illustration = R.drawable.onboarding_pilotez,
        title        = "Pilotez votre activité",
        subtitle     = "Des tableaux de bord clairs et des rapports détaillés pour prendre les bonnes décisions.",
        features     = listOf(
            Feature(Icons.Filled.BarChart, "Tableaux de bord", "Vue d'ensemble en temps réel"),
            Feature(Icons.Filled.Description, "Rapports et analyses", "Des indicateurs qui comptent"),
            // The mockup said « Reçus et bons de livraison »: the app prints sales receipts and purchase vouchers
            Feature(Icons.Filled.Print, "Impression Bluetooth", "Reçus de vente et bons d'achat"),
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
            Text("Passer", fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DsBrand.Ink)
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
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Page suivante")
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
        Text("Créer mon compte", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.width(DsSpacing.sm))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
