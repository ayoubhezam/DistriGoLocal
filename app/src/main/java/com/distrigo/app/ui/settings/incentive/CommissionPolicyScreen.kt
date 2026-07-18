package com.distrigo.app.ui.settings.incentive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.local.entity.incentive.PeriodType
import com.distrigo.app.data.local.entity.incentive.PolicyTierEntity
import com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import java.time.Instant
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.products.ProductViewModel

/** شريحة تحرير محلية للـ UI فقط — تُحوَّل إلى PolicyTierEntity عند الحفظ */
private data class TierDraft(
    val id: Long = System.nanoTime(),
    var minThreshold: String = "",
    var rewardRate: String = "",
    var fixedBonus: String = ""
)

@Composable
fun CommissionPolicyScreen(
    onBack: () -> Unit,
    viewModel: IncentiveViewModel = viewModel(),
    productViewModel: ProductViewModel = viewModel()   // ← جديد

) {
    BackHandler { onBack() }

    val activePolicy by viewModel.activePolicy.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var incentiveType by remember { mutableStateOf(IncentiveType.FIXED_RATE) }
    var periodType by remember { mutableStateOf(PeriodType.MONTHLY) }
    var calculationSource by remember { mutableStateOf(CalculationSource.INVOICED_SALES) }
    var targetValue by remember { mutableStateOf("") }
    var rewardRate by remember { mutableStateOf("") }
    var fixedBonusAmount by remember { mutableStateOf("") }
    var freeGoodQuantity by remember { mutableStateOf("") }
    var tiers by remember { mutableStateOf(listOf(TierDraft())) }
    var saveError by remember { mutableStateOf("") }
    val products by productViewModel.products.collectAsState()
    var freeGoodProductId by remember { mutableStateOf<Int?>(null) }
    var freeGoodProductName by remember { mutableStateOf("") }
    var productSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { productViewModel.loadProducts() }

    // ── تعبئة النموذج من السياسة الحالية عند فتح الشاشة (إن وُجدت) ──
    LaunchedEffect(activePolicy) {
        activePolicy?.let { pwt ->
            incentiveType = pwt.policy.incentive_type
            periodType = pwt.policy.period_type
            calculationSource = pwt.policy.calculation_source
            targetValue = pwt.policy.target_value.toString()
            rewardRate = pwt.policy.reward_rate?.toString() ?: ""
            fixedBonusAmount = pwt.policy.fixed_bonus_amount?.toString() ?: ""
            freeGoodQuantity = pwt.policy.free_good_quantity?.toString() ?: ""
            freeGoodProductId = pwt.policy.free_good_product_id
            freeGoodProductName = products.find { it.id == pwt.policy.free_good_product_id }?.name ?: ""
            if (pwt.tiers.isNotEmpty()) {
                tiers = pwt.tiers.sortedBy { it.tier_order }.map {
                    TierDraft(
                        minThreshold = it.min_threshold.toString(),
                        rewardRate = it.reward_rate?.toString() ?: "",
                        fixedBonus = it.fixed_bonus?.toString() ?: ""
                    )
                }
            }
        }
    }

    fun save() {
        val target = targetValue.toDoubleOrNull()
        if (target == null || target <= 0) {
            saveError = "Veuillez saisir un objectif valide."
            return
        }
        saveError = ""

        val policy = TargetPolicyEntity(
            nom = "Politique ${periodType.name}",
            incentive_type = incentiveType,
            period_type = periodType,
            calculation_source = calculationSource,
            target_value = target,
            reward_rate = rewardRate.toDoubleOrNull(),
            fixed_bonus_amount = fixedBonusAmount.toDoubleOrNull(),
            free_good_product_id = freeGoodProductId,
            free_good_quantity = freeGoodQuantity.toIntOrNull(),
            is_active = true,
            distributor_id = null,
            created_at = Instant.now().toString()
        )

        val tierEntities = if (incentiveType == IncentiveType.PROGRESSIVE_TIERS) {
            tiers.mapIndexedNotNull { index, draft ->
                val min = draft.minThreshold.toDoubleOrNull() ?: return@mapIndexedNotNull null
                PolicyTierEntity(
                    policy_id = 0, // يُستبدل في savePolicy بعد insertPolicy
                    min_threshold = min,
                    max_threshold = null,
                    reward_rate = draft.rewardRate.toDoubleOrNull(),
                    fixed_bonus = draft.fixedBonus.toDoubleOrNull(),
                    tier_order = index
                )
            }
        } else emptyList()

        viewModel.savePolicy(
            policy = policy,
            tiers = tierEntities,
            onSuccess = onBack,
            onError = { saveError = it }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)            }
            Text(
                "Politique de commission",
                fontSize = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color = DsColors.TextPrimary,
                modifier = Modifier.padding(start = DsSpacing.xs)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.lg)
        ) {
            SectionLabel("Type de calcul")
            SegmentedSelector(
                options = IncentiveType.entries,
                selected = incentiveType,
                onSelected = { incentiveType = it },
                labelFor = {
                    when (it) {
                        IncentiveType.FIXED_RATE -> "Taux fixe"
                        IncentiveType.PROGRESSIVE_TIERS -> "Paliers"
                        IncentiveType.FIXED_BONUS -> "Forfaitaire"
                        IncentiveType.FREE_GOODS -> "Gratuité"
                    }
                }
            )

            Spacer(Modifier.height(DsSpacing.lg))
            SectionLabel("Période")
            SegmentedSelector(
                options = PeriodType.entries,
                selected = periodType,
                onSelected = { periodType = it },
                labelFor = {
                    when (it) {
                        PeriodType.MONTHLY -> "Mensuelle"
                        PeriodType.QUARTERLY -> "Trimestrielle"
                        PeriodType.YEARLY -> "Annuelle"
                    }
                }
            )

            Spacer(Modifier.height(DsSpacing.lg))
            SectionLabel("Basé sur")
            SegmentedSelector(
                options = CalculationSource.entries,
                selected = calculationSource,
                onSelected = { calculationSource = it },
                labelFor = {
                    when (it) {
                        CalculationSource.INVOICED_SALES -> "Ventes facturées"
                        CalculationSource.COLLECTED_CASH -> "Recouvrement"
                    }
                }
            )

            Spacer(Modifier.height(DsSpacing.lg))
            DsFormField(
                label = "Objectif (DA) *",
                value = targetValue,
                onValueChange = { targetValue = it.filter { c -> c.isDigit() || c == '.' } },
                error = saveError,
                placeholder = "Ex: 500000",
                keyboardType = KeyboardType.Decimal
            )

            Spacer(Modifier.height(DsSpacing.lg))

            // ── حقول متغيّرة حسب النوع ──
            when (incentiveType) {
                IncentiveType.FIXED_RATE -> {
                    DsFormField(
                        label = "Taux de commission (%)",
                        value = rewardRate,
                        onValueChange = { rewardRate = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = "Ex: 2.5",
                        keyboardType = KeyboardType.Decimal
                    )
                }
                IncentiveType.FIXED_BONUS -> {
                    DsFormField(
                        label = "Prime forfaitaire (DA)",
                        value = fixedBonusAmount,
                        onValueChange = { fixedBonusAmount = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = "Ex: 15000",
                        keyboardType = KeyboardType.Decimal
                    )
                }
                IncentiveType.FREE_GOODS -> {
                    Text("Produit offert", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))

                    if (freeGoodProductId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DsColors.PrimaryLight, DsShapes.medium)
                                .padding(DsSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                            Text(
                                freeGoodProductName,
                                fontSize = DsTextSize.body,
                                fontWeight = FontWeight.SemiBold,
                                color = DsColors.TextPrimary,
                                modifier = Modifier.weight(1f).padding(start = DsSpacing.sm)
                            )
                            Text(
                                "Changer",
                                fontSize = DsTextSize.caption,
                                color = DsColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    freeGoodProductId = null
                                    freeGoodProductName = ""
                                }
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            placeholder = { Text("Rechercher un produit...", fontSize = DsTextSize.body) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextTertiary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = DsShapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = DsColors.Border,
                                focusedBorderColor = DsColors.Primary
                            )
                        )

                        if (productSearchQuery.isNotBlank()) {
                            val filtered = products.filter { it.name.contains(productSearchQuery, ignoreCase = true) }.take(5)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = DsSpacing.xs)
                                    .background(DsColors.Surface, DsShapes.medium)
                            ) {
                                filtered.forEach { product ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                freeGoodProductId = product.id
                                                freeGoodProductName = product.name
                                                productSearchQuery = ""
                                            }
                                            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
                                        Text(product.name, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, modifier = Modifier.padding(start = DsSpacing.sm))
                                    }
                                }
                                if (filtered.isEmpty()) {
                                    Text(
                                        "Aucun produit trouvé",
                                        fontSize = DsTextSize.caption,
                                        color = DsColors.TextTertiary,
                                        modifier = Modifier.padding(DsSpacing.md)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(DsSpacing.md))
                    DsFormField(
                        label = "Quantité offerte",
                        value = freeGoodQuantity,
                        onValueChange = { freeGoodQuantity = it.filter { c -> c.isDigit() } },
                        placeholder = "Ex: 10",
                        keyboardType = KeyboardType.Number
                    )
                }
                IncentiveType.PROGRESSIVE_TIERS -> {
                    TiersEditor(
                        tiers = tiers,
                        onTiersChange = { tiers = it }
                    )
                }
            }

            Spacer(Modifier.height(DsSpacing.xxxl))
        }

        Button(
            onClick = { save() },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(DsSpacing.lg)
                .height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Enregistrer la politique", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = DsTextSize.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DsColors.TextSecondary,
        modifier = Modifier.padding(bottom = DsSpacing.xs)
    )
}

@Composable
private fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    labelFor: (T) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DsColors.SurfaceMuted, DsShapes.pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(DsShapes.pill)
                    .background(if (active) DsColors.Primary else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    labelFor(option),
                    fontSize = DsTextSize.caption,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) Color.White else DsColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TiersEditor(
    tiers: List<TierDraft>,
    onTiersChange: (List<TierDraft>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
        SectionLabel("Paliers (à partir de plusieurs seuils)")

        tiers.forEachIndexed { index, tier ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DsColors.SurfaceMuted, DsShapes.medium)
                    .padding(DsSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Palier ${index + 1}",
                        fontSize = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DsColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (tiers.size > 1) {
                        IconButton(onClick = { onTiersChange(tiers.filterNot { it.id == tier.id }) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                DsFormField(
                    label = "Seuil minimum (DA)",
                    value = tier.minThreshold,
                    onValueChange = { newVal ->
                        onTiersChange(tiers.map { if (it.id == tier.id) it.copy(minThreshold = newVal.filter { c -> c.isDigit() || c == '.' }) else it })
                    },
                    placeholder = "Ex: 300000",
                    keyboardType = KeyboardType.Decimal
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DsFormField(
                            label = "Taux (%)",
                            value = tier.rewardRate,
                            onValueChange = { newVal ->
                                onTiersChange(tiers.map { if (it.id == tier.id) it.copy(rewardRate = newVal.filter { c -> c.isDigit() || c == '.' }) else it })
                            },
                            placeholder = "Ex: 3",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DsFormField(
                            label = "Ou prime fixe (DA)",
                            value = tier.fixedBonus,
                            onValueChange = { newVal ->
                                onTiersChange(tiers.map { if (it.id == tier.id) it.copy(fixedBonus = newVal.filter { c -> c.isDigit() || c == '.' }) else it })
                            },
                            placeholder = "Ex: 20000",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { onTiersChange(tiers + TierDraft()) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = DsShapes.medium
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ajouter un palier", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

@Composable
private fun DsFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String = "",
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = DsTextSize.body) },
            singleLine = true,
            isError = error.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = DsShapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor = DsColors.Primary,
                errorBorderColor = DsColors.Danger
            )
        )
        if (error.isNotEmpty()) {
            Text(error, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(start = DsSpacing.xs, top = 2.dp))
        }
    }
}