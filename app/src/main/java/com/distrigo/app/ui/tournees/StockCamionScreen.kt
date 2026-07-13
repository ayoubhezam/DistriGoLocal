package com.distrigo.app.ui.tournees

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.basicMarquee
@Composable
fun StockCamionScreen(
    onBack           : () -> Unit,
    productViewModel : com.distrigo.app.ui.products.ProductViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(Unit) { productViewModel.loadProducts() }

    val products  by productViewModel.products.collectAsState()
    val isLoading by productViewModel.isLoading.collectAsState()

    var search by remember { mutableStateOf("") }

    val camionProducts = products.filter { it.camion_stock > 0 }
    val filtered = camionProducts.filter { it.name.contains(search, ignoreCase = true) }

    val totalQuantity = camionProducts.sumOf { it.camion_stock }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text(
                "Stock Camion",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        }
        Spacer(Modifier.height(DsSpacing.md))

        // ── Search ──
        OutlinedTextField(
            value         = search,
            onValueChange = { search = it },
            placeholder   = { Text("Rechercher un produit…", fontSize = DsTextSize.body) },
            leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape         = DsShapes.large,
            singleLine    = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary
            )
        )

        Spacer(Modifier.height(DsSpacing.sm))

        when {
            isLoading && products.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint     = DsColors.TextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text("Aucun produit dans le camion", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(filtered, key = { it.id }) { product ->
                        StockCamionProductRow(product = product)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockCamionProductRow(product: Product) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(44.dp).clip(DsShapes.medium).background(DsColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            if (product.image_uri != null) {
                val imageBytes = Base64.decode(product.image_uri.substringAfter("base64,"), Base64.NO_WRAP)
                val bitmap     = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmap?.let {
                    Image(
                        bitmap             = it.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize().clip(DsShapes.medium),
                        contentScale       = ContentScale.Crop
                    )
                }
            } else {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.width(DsSpacing.md))

        Text(
            product.name,
            fontSize   = DsTextSize.body,
            fontWeight = FontWeight.SemiBold,
            color      = DsColors.TextPrimary,
            maxLines   = 1,
            modifier   = Modifier
                .weight(1f)
                .basicMarquee()
        )

        Spacer(Modifier.width(DsSpacing.sm))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${"%.2f".format(product.camion_stock * product.selling_price)} DA",
                fontSize   = DsTextSize.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = DsColors.Primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${product.camion_stock} ${product.unit_type}",
                fontSize = DsTextSize.caption,
                color    = Color.Black
            )
        }

        Spacer(Modifier.width(DsSpacing.lg))

    }
}