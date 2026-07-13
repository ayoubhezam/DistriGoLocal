package com.distrigo.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider

private val InkColor = Color(0xFF14213D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareOptionsSheet(
    receipt   : ReceiptData,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Text(
                "Partager le reçu",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = InkColor,
                modifier   = Modifier.padding(bottom = 12.dp)
            )

            ShareOptionRow(
                iconBg   = Color(0xFFE8F5E9),
                iconTint = Color(0xFF2E7D32),
                title    = "WhatsApp",
                subtitle = "Envoyer directement au client",
                icon     = { WhatsAppIcon(tint = Color(0xFF2E7D32)) },
                onClick  = {
                    shareViaWhatsApp(context, receipt)
                    onDismiss()
                }
            )

            ShareOptionRow(
                iconBg   = Color(0xFFFFEBEE),
                iconTint = Color(0xFFC62828),
                title    = "Exporter en PDF",
                subtitle = "Enregistrer ou envoyer le fichier",
                icon     = { Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp)) },
                onClick  = {
                    sharePdfFile(context, receipt)
                    onDismiss()
                }
            )

            ShareOptionRow(
                iconBg   = Color(0xFFF1F5F9),
                iconTint = Color(0xFF64748B),
                title    = "Copier en texte",
                subtitle = "Coller où vous voulez",
                icon     = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(19.dp)) },
                onClick  = {
                    copyReceiptAsText(context, receipt)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ShareOptionRow(
    iconBg   : Color,
    iconTint : Color,
    title    : String,
    subtitle : String,
    icon     : @Composable () -> Unit,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(44.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkColor)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun WhatsAppIcon(tint: Color) {
    // استخدام أيقونة نصية بسيطة بدل الاعتماد على شعار WhatsApp نفسه (حقوق الملكية الفكرية)
    Text("W", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
}

// ── منطق المشاركة الفعلي ──

private fun receiptAsPlainText(receipt: ReceiptData): String {
    val sb = StringBuilder()
    sb.appendLine("*DISTRIGO*")
    sb.appendLine(receipt.documentTitle)
    sb.appendLine("${receipt.partyLabel}: ${receipt.partyName}")
    sb.appendLine("Date: ${receipt.dateLabel}")
    sb.appendLine("—".repeat(20))
    receipt.items.forEach { item ->
        sb.appendLine("${item.name}")
        sb.appendLine("  ${item.quantity} ${item.unitLabel} × ${"%.2f".format(item.unitPrice)} DA = ${"%.2f".format(item.totalPrice)} DA")
    }
    sb.appendLine("—".repeat(20))
    sb.appendLine("TOTAL: ${"%.2f".format(receipt.total)} DA")
    sb.appendLine("Payé: ${"%.2f".format(receipt.paid)} DA")
    if (receipt.balance > 0) sb.appendLine("Reste: ${"%.2f".format(receipt.balance)} DA")
    else sb.appendLine("Statut: Réglé")
    receipt.note?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Note: $it") }
    return sb.toString()
}

private fun shareViaWhatsApp(context: Context, receipt: ReceiptData) {
    val text = receiptAsPlainText(receipt)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // WhatsApp غير مثبَّت — نرجع لقائمة مشاركة عامة
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallback, "Partager via"))
        Toast.makeText(context, "WhatsApp non installé", Toast.LENGTH_SHORT).show()
    }
}

private fun sharePdfFile(context: Context, receipt: ReceiptData) {
    val file = ReceiptPdfGenerator.generate(context, receipt)
    val uri  = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Partager le PDF"))
}

private fun copyReceiptAsText(context: Context, receipt: ReceiptData) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Reçu", receiptAsPlainText(receipt)))
    Toast.makeText(context, "Reçu copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
}