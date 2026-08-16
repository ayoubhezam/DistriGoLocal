package com.distrigo.app.ui.categories

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Category
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun CategoriesScreen(viewModel: CategoryViewModel = hiltViewModel()) {

    val categories by viewModel.categories.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()
    val error      by viewModel.error.collectAsState()

    var showAddDialog    by remember { mutableStateOf(false) }
    var editCategory     by remember { mutableStateOf<Category?>(null) }
    var deleteCategory   by remember { mutableStateOf<Category?>(null) }
    var newName          by remember { mutableStateOf("") }
    var nameError        by remember { mutableStateOf("") }

    // ── Add Dialog ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newName = ""; nameError = "" },
            title = { Text("Nouvelle catégorie") },
            text  = {
                Column {
                    OutlinedTextField(
                        value         = newName,
                        onValueChange = { newName = it; nameError = "" },
                        placeholder   = { Text("Ex: Boissons") },
                        isError       = nameError.isNotEmpty(),
                        singleLine    = true,
                        shape         = DsShapes.medium,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = DsColors.Primary,
                            unfocusedBorderColor = DsColors.Border,
                            errorBorderColor     = DsColors.Danger
                        )
                    )
                    if (nameError.isNotEmpty()) {
                        Text(nameError, fontSize = DsTextSize.caption, color = DsColors.Danger,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isBlank()) {
                        nameError = "Le nom est obligatoire."
                        return@TextButton
                    }
                    val duplicate = categories.find {
                        it.name.trim().lowercase() == newName.trim().lowercase()
                    }
                    if (duplicate != null) {
                        nameError = "Cette catégorie existe déjà."
                        return@TextButton
                    }
                    viewModel.addCategory(
                        name      = newName.trim(),
                        onSuccess = { showAddDialog = false; newName = "" },
                        onError   = { nameError = it }
                    )
                }) {
                    Text("Ajouter", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newName = ""; nameError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ── Edit Dialog ──
    editCategory?.let { cat ->
        var editName by remember { mutableStateOf(cat.name) }
        var editError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editCategory = null },
            title = { Text("Modifier la catégorie") },
            text  = {
                Column {
                    OutlinedTextField(
                        value         = editName,
                        onValueChange = { editName = it; editError = "" },
                        isError       = editError.isNotEmpty(),
                        singleLine    = true,
                        shape         = DsShapes.medium,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = DsColors.Primary,
                            unfocusedBorderColor = DsColors.Border,
                            errorBorderColor     = DsColors.Danger
                        )
                    )
                    if (editError.isNotEmpty()) {
                        Text(editError, fontSize = DsTextSize.caption, color = DsColors.Danger,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isBlank()) { editError = "Le nom est obligatoire."; return@TextButton }
                    viewModel.updateCategory(
                        id        = cat.id,
                        name      = editName.trim(),
                        onSuccess = { editCategory = null },
                        onError   = { editError = it }
                    )
                }) {
                    Text("Enregistrer", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editCategory = null }) { Text("Annuler") }
            }
        )
    }

    // ── Delete Dialog ──
    deleteCategory?.let { cat ->
        var deleteError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteCategory = null; deleteError = "" },
            title = { Text("Supprimer la catégorie") },
            text  = {
                Column {
                    Text("Voulez-vous supprimer \"${cat.name}\" ?")
                    Text("Attention : impossible si des produits y sont associés.",
                        fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text     = deleteError,
                            fontSize = DsTextSize.bodySmall,
                            color    = DsColors.Danger
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(
                        id        = cat.id,
                        onSuccess = { deleteCategory = null; deleteError = "" },
                        onError   = { deleteError = it }
                    )
                }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCategory = null; deleteError = "" }) { Text("Annuler") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Catégories", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
            FloatingActionButton(
                onClick        = { showAddDialog = true },
                containerColor = DsColors.Primary,
                contentColor   = Color.White,
                modifier       = Modifier.size(40.dp),
                shape          = DsShapes.pill
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }

        // ── Loading ──
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            return
        }

        // ── Error ──
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = DsColors.Danger)
            }
            return
        }

        // ── Empty state ──
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Category, contentDescription = null,
                        tint = DsColors.Primary.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Aucune catégorie", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Appuyez sur + pour ajouter", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }
            return
        }

        // ── Count ──
        Text(
            text     = "${categories.size} catégorie(s)",
            fontSize = DsTextSize.caption,
            color    = DsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── List ──
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(40.dp)
                                    .clip(DsShapes.medium)
                                    .background(DsColors.PrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null,
                                    tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                            }
                            Text(category.name, fontWeight = FontWeight.SemiBold,
                                fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick  = { editCategory = category },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(DsShapes.small)
                                    .background(DsColors.PrimaryLight)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier",
                                    tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick  = { deleteCategory = category },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(DsShapes.small)
                                    .background(DsColors.DangerLight)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                                    tint = DsColors.Danger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}