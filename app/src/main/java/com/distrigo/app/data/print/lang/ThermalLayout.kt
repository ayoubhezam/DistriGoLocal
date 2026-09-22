package com.distrigo.app.data.print.lang

import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.ui.components.ReceiptData
import com.distrigo.app.ui.components.ReceiptLineItem
import com.distrigo.app.ui.components.formatQty
import java.util.Locale

/**
 * Lays a [ReceiptData] out for thermal paper, once, for everybody.
 *
 * The ESC/POS renderer, the label renderers and the on-screen preview all consume the list of
 * [ReceiptRow] this produces. Nothing downstream re-measures or re-wraps, so the preview cannot drift
 * from the paper the way `ReceiptPreviewSheet` and `ReceiptPdfGenerator` already have — those two each
 * carry their own copy of the same rules and agree only because somebody keeps checking.
 *
 * Everything is measured in the printer's own characters — Font A, so **32 across on 58 mm and 48 on
 * 80 mm**. Those two numbers are the whole layout spec (see docs/print_architecture.md §2).
 *
 * The receipt is still built to be short, just not at the cost of legibility: tight line spacing, no
 * blank line between items, no QR. A receipt is a running cost, and a shop buying rolls by the box
 * notices the difference between twenty lines and thirty — but Font B, tried for the same reason,
 * was too small to read on the counter.
 *
 * The item table differs by width on purpose, because it has to:
 * - **48 columns** fit a real table, one line per item, with a header.
 * - **32 columns** do not — see [MIN_COLUMNS_FOR_TABLE] — so an item takes two lines: the name, then
 *   the quantity and the line total. It is why switching the paper in the preview visibly rewrites
 *   the receipt instead of merely narrowing it.
 *
 * (The dependency on `ui.components.ReceiptData` points the wrong way for a `data` package. It is
 * where the receipt model already lives, and `BusinessSettingsRepository` already reaches into
 * `ui.common.ImageCapture` the same way; moving `ReceiptData` down into `data/model` is a rename
 * across a dozen files and belongs in its own commit, not in this one.)
 */
object ThermalLayout {

    /**
     * The cut-off above which an item table fits on one line.
     *
     * 48-column paper clears it, 32 does not: 32 minus the three numeric columns leaves nothing for a
     * product name at all. The threshold tracks the font — it was 50 while the receipt was set in
     * Font B, where 42 columns *could* have ruled a table but a twelve-character name column would
     * have wrapped every product over three lines and printed longer, not shorter.
     */
    private const val MIN_COLUMNS_FOR_TABLE = 40

    /**
     * @param logo already scaled and dithered to [paper]'s dot width, or null when the business has no
     *   logo or its file is missing on this device. Rasterising is the caller's job because it needs a
     *   `Bitmap`, and this object stays free of Android types so it can be tested on the desk.
     */
    fun layout(
        receipt: ReceiptData,
        paper  : PaperProfile,
        logo   : MonoRaster? = null,
    ): List<ReceiptRow> = buildList {
        val w = paper.charsPerLine

        // ── Header ──
        //
        // No blank lines anywhere in here. Every one costs 20 dots of paper, and a rule already
        // separates the header from what follows it.
        logo?.let { add(ReceiptRow.Raster(it)) }
        add(ReceiptRow.Line(receipt.businessName.uppercase(Locale.FRENCH), RowAlign.Center, RowWeight.Bold))
        receipt.businessPhone?.takeIf { it.isNotBlank() }?.let {
            add(ReceiptRow.Line("Tel: $it", RowAlign.Center))
        }
        add(ReceiptRow.Line(badgeLabel(receipt.documentTitle), RowAlign.Center, RowWeight.Bold))
        add(ReceiptRow.Rule())

        // ── What this document is, and who for ──
        //
        // A label column with the values wrapped and hanging-indented under it. The A4 receipt sets
        // these in two side-by-side columns; a thermal roll has room for one.
        val labelWidth = listOf("N°", "Date", "Heure", receipt.partyLabel, "Type", "Secteur", "Par")
            .maxOf { it.length }
        infix fun String.field(value: String?) {
            val text = value?.takeIf { it.isNotBlank() } ?: return
            addAll(field(this, text, labelWidth, w))
        }
        "N°"                field receipt.referenceNumber
        "Date"              field receipt.dateLabel
        "Heure"             field receipt.timeLabel
        receipt.partyLabel  field receipt.partyName
        "Type"              field receipt.clientType
        "Secteur"           field receipt.clientSecteur
        "Par"               field receipt.performedBy
        add(ReceiptRow.Rule())

        // ── Items ──
        if (w >= MIN_COLUMNS_FOR_TABLE) addAll(itemTable(receipt.items, w))
        else                            addAll(itemPairs(receipt.items, w))
        add(ReceiptRow.Rule('='))

        // ── Money ──
        add(ReceiptRow.Columns("TOTAL", money(receipt.total, w - 6) + " DA", RowWeight.Bold))
        if (receipt.paid > 0 || receipt.balance != receipt.total) {
            add(ReceiptRow.Columns("Payé", money(receipt.paid, w - 6) + " DA"))
            if (receipt.balance > 0) add(ReceiptRow.Columns("Reste", money(receipt.balance, w - 6) + " DA", RowWeight.Bold))
            else                     add(ReceiptRow.Columns("Statut", "Réglé"))
        }
        add(ReceiptRow.Rule())

        // ── Amount in words ──
        add(ReceiptRow.Line("Arrêté à la somme de :"))
        wrap(receipt.amountInWords, w).forEach { add(ReceiptRow.Line(it)) }

        receipt.note?.takeIf { it.isNotBlank() }?.let { note ->
            addAll(field("Note", note, 4, w))
        }

        // ── Signature, only where one is worth having ──
        //
        // On a fully paid receipt nobody signs anything; on one with a balance the signature is the
        // acknowledgement of the debt, which is the whole reason to print it.
        if (receipt.balance > 0) {
            add(ReceiptRow.Line("Signature : " + "_".repeat((w - 12).coerceAtLeast(6))))
        }

        // ── Footer ──
        //
        // No QR. It cost about a centimetre of roll on every receipt and encoded only what the
        // receipt already prints in words — the title, the date and the total — so nothing could be
        // learned by scanning it that reading it did not already give. (The A4 PDF keeps its one:
        // there the space is free.)
        add(ReceiptRow.Rule())
        add(ReceiptRow.Line("Merci pour votre confiance !", RowAlign.Center, RowWeight.Bold))
        // The two blank lines are not padding: they feed the last printed line clear of the tear bar,
        // which sits about 15 mm above the head on every mechanism we have seen.
        add(ReceiptRow.Blank(2))
        add(ReceiptRow.Cut)
    }

    // ───────────────────────────── items ─────────────────────────────

    /**
     * Wide paper: `Article Qté P.U. Total`, single spaces between, one line per item.
     *
     * The widths are derived from [width] rather than constants so an unusual profile still produces a
     * table that adds up, and the name column absorbs whatever is left over.
     */
    private fun itemTable(items: List<ReceiptLineItem>, width: Int): List<ReceiptRow> {
        val qtyW   = 10
        val puW    = 9
        val totalW = 8
        val nameW  = width - qtyW - puW - totalW - 3

        fun line(name: String, qty: String, pu: String, total: String) =
            pad(name, nameW, RowAlign.Left) + " " +
            pad(qty, qtyW, RowAlign.Left) + " " +
            pad(pu, puW, RowAlign.Right) + " " +
            pad(total, totalW, RowAlign.Right)

        return buildList {
            add(ReceiptRow.Line(line("Article", "Qté", "P.U.", "Total"), weight = RowWeight.Bold))
            add(ReceiptRow.Rule())
            items.forEach { item ->
                val nameLines = wrap(item.name, nameW).ifEmpty { listOf("") }
                add(ReceiptRow.Line(line(
                    nameLines.first(),
                    "${formatQty(item.quantity)} ${item.unitLabel}",
                    money(item.unitPrice, puW),
                    money(item.totalPrice, totalW),
                )))
                // A name too long for its cell continues on its own line rather than being cut. The
                // numeric columns stay on the first line, where the eye looks for them.
                nameLines.drop(1).forEach { add(ReceiptRow.Line("  " + fit(it, nameW))) }
            }
        }
    }

    /**
     * Narrow paper: the name on its own line, then `qté × P.U.` indented with the line total pinned right.
     *
     * Two lines per item, with nothing between one item and the next — the indent on the figures line
     * is what groups them, and it costs no paper.
     */
    private fun itemPairs(items: List<ReceiptLineItem>, width: Int): List<ReceiptRow> = buildList {
        items.forEach { item ->
            // No blank line between items. The indent on the figures line already groups each pair
            // visually, and a separator per item is a line of paper per item.
            wrap(item.name, width).ifEmpty { listOf("") }.forEach { add(ReceiptRow.Line(it)) }
            val left  = "  ${formatQty(item.quantity)} ${item.unitLabel} x ${money(item.unitPrice, 9)}"
            val total = money(item.totalPrice, 9)
            // Squeezed, the quantity line loses its unit before it loses the total: the total is the
            // number the client checks.
            val fitted = if (left.length + total.length + 1 <= width) left
                         else "  ${formatQty(item.quantity)} x ${money(item.unitPrice, 9)}"
            add(ReceiptRow.Columns(fit(fitted, width - total.length - 1), total))
        }
    }

    // ───────────────────────────── text helpers ─────────────────────────────

    /** A labelled value: `Label   : value`, wrapped and hanging-indented under the colon. */
    private fun field(label: String, value: String, labelWidth: Int, width: Int): List<ReceiptRow> {
        val prefix = pad(label, labelWidth, RowAlign.Left) + " : "
        val body   = wrap(value, (width - prefix.length).coerceAtLeast(8)).ifEmpty { listOf("") }
        return body.mapIndexed { i, line ->
            ReceiptRow.Line(if (i == 0) prefix + line else " ".repeat(prefix.length) + line)
        }
    }

    /** Word wrap, splitting a word that is longer than the line rather than letting it overflow. */
    internal fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) return emptyList()
        val out = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            var rest = word
            while (rest.length > width) {
                if (out.isNotEmpty() && out.last().length < width) {
                    // Fill the current line before breaking, so a long word does not leave a short one.
                    val room = width - out.last().length - 1
                    if (room > 0) { out[out.lastIndex] = out.last() + " " + rest.take(room); rest = rest.drop(room) }
                    else out.add("")
                } else {
                    out.add(rest.take(width)); rest = rest.drop(width)
                }
            }
            val last = out.lastOrNull()
            if (last == null || last.length + 1 + rest.length > width) out.add(rest)
            else if (last.isEmpty()) out[out.lastIndex] = rest
            else out[out.lastIndex] = "$last $rest"
        }
        return out.filter { it.isNotEmpty() }
    }

    /** Hard truncation, for a cell that has nowhere to wrap to. */
    internal fun fit(text: String, width: Int): String =
        if (text.length <= width) text else text.take(width)

    internal fun pad(text: String, width: Int, align: RowAlign): String {
        val t = fit(text, width)
        val room = width - t.length
        return when (align) {
            RowAlign.Left   -> t + " ".repeat(room)
            RowAlign.Right  -> " ".repeat(room) + t
            RowAlign.Center -> " ".repeat(room / 2) + t + " ".repeat(room - room / 2)
        }
    }

    /**
     * An amount that fits, dropping precision only when it must.
     *
     * Two decimals is the format everywhere else in the app. A total that will not fit loses its
     * centimes rather than its thousands — a receipt that reads `15250` is right to the dinar, one that
     * reads `5250.00` because the leading digit was cut is a dispute.
     */
    internal fun money(value: Double, width: Int): String {
        val full = String.format(Locale.ROOT, "%.2f", value)
        if (full.length <= width) return full
        val rounded = String.format(Locale.ROOT, "%.0f", value)
        return if (rounded.length <= width) rounded else rounded.takeLast(width)
    }

    private fun badgeLabel(documentTitle: String): String =
        if (documentTitle.startsWith("Vente")) "REÇU DE VENTE" else "BON D'ACHAT"
}
