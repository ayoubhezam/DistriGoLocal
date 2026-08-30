package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors

/**
 * Shared "pick a client via search" screen body: header + search field + filtered client list,
 * used by the client-picker route of VenteFormNavGraph, TourneeVenteFormNavGraph and
 * RetourClientFormNavGraph. Owns its own search text state and filtering logic.
 */
@Composable
fun ClientSearchPicker(
    clients          : List<Client>,
    selectedClientId : Int? = null,
    onClientSelected : (Client) -> Unit,
    onBack           : () -> Unit,
    onAddNewClient   : (() -> Unit)? = null,
    showBalance      : Boolean = true
) {
    var clientSearch by remember { mutableStateOf("") }

    val filteredClients = clients.filter { client ->
        val tokens = clientSearch.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            client.name.contains(token, ignoreCase = true) ||
                    (client.phone?.contains(token, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title   = stringResource(R.string.client_picker_title),
            leading = DsTopBarLeading.Back(onBack)
        )

        OutlinedTextField(
            value         = clientSearch,
            onValueChange = { clientSearch = it },
            placeholder   = {
                Text(
                    stringResource(R.string.client_picker_search_placeholder),
                    fontSize = DsTextSize.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape         = DsShapes.large,
            singleLine    = true,
            textStyle     = LocalTextStyle.current.copy(fontSize = DsTextSize.bodySmall),
            colors = dsTextFieldColors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary
            )
        )

        Spacer(Modifier.height(DsSpacing.sm))

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            items(filteredClients, key = { it.id }) { client ->
                val typeColors = when (client.customer_type) {
                    "wholesale" -> DsColors.TagWholesale
                    "business"  -> DsColors.TagBusiness
                    else        -> DsColors.TagRetail
                }
                val isSelected = selectedClientId == client.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, if (isSelected) DsColors.Primary else DsColors.Border, DsShapes.large)
                        .clickable { onClientSelected(client) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val initials = client.name.split(" ").take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                    Box(
                        modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(typeColors.second),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = typeColors.first)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(client.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary, maxLines = 1)
                        Text(client.phone ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    if (showBalance) {
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            if (client.balance > 0) {
                                Text("${"%.2f".format(client.balance)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                            } else {
                                Text("✓ Soldé", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Success)
                            }
                        }
                    }
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (onAddNewClient != null) {
            Button(
                onClick  = onAddNewClient,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                    .height(52.dp),
                shape  = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(stringResource(R.string.client_picker_add_new), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
