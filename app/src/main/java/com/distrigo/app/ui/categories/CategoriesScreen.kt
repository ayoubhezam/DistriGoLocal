package com.distrigo.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.distrigo.app.data.model.Category
import com.distrigo.app.ui.products.*

@Composable
fun CategoriesScreen(viewModel: CategoryViewModel = viewModel()) {

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
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = BorderGray,
                            errorBorderColor     = DestructiveRed
                        )
                    )
                    if (nameError.isNotEmpty()) {
                        Text(nameError, fontSize = 11.sp, color = DestructiveRed,
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
                    Text("Ajouter", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
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
                        shape         = RoundedCornerShape(12.dp),
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = BorderGray,
                            errorBorderColor     = DestructiveRed
                        )
                    )
                    if (editError.isNotEmpty()) {
                        Text(editError, fontSize = 11.sp, color = DestructiveRed,
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
                    Text("Enregistrer", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
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
                        fontSize = 13.sp, color = TextMuted)
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text     = deleteError,
                            fontSize = 13.sp,
                            color    = DestructiveRed
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
                    Text("Supprimer", color = DestructiveRed, fontWeight = FontWeight.SemiBold)
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
            .background(Color.White)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Catégories", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            FloatingActionButton(
                onClick        = { showAddDialog = true },
                containerColor = PrimaryBlue,
                contentColor   = Color.White,
                modifier       = Modifier.size(40.dp),
                shape          = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }

        // ── Loading ──
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return
        }

        // ── Error ──
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = DestructiveRed)
            }
            return
        }

        // ── Empty state ──
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Category, contentDescription = null,
                        tint = PrimaryBlue.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Aucune catégorie", color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Appuyez sur + pour ajouter", fontSize = 12.sp, color = TextMuted)
                }
            }
            return
        }

        // ── Count ──
        Text(
            text     = "${categories.size} catégorie(s)",
            fontSize = 12.sp,
            color    = TextMuted,
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
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null,
                                    tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            }
                            Text(category.name, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp, color = TextPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick  = { editCategory = category },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BlueLight)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier",
                                    tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick  = { deleteCategory = category },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RedLight)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                                    tint = DestructiveRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}