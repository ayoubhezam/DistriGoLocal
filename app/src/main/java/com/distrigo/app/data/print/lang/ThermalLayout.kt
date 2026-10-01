package com.distrigo.app.data.print.lang

import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.ui.components.ReceiptData
import com.distrigo.app.ui.components.ReceiptLineItem
import com.distrigo.app.data.model.Quantity
import java.util.Locale
import com.distrigo.app.core.format.MoneyFormatter

/**
 * Lays a [ReceiptData] out for thermal paper, once, for everybody.
 *
 * It decides **what goes on the receipt and in what order**, and nothing else. It does not pad, wrap,
 * measure or truncate: all of those need to know what the glyphs look like, and since the receipt is
 * drawn on an Android Canvas so that Arabic can be shaped and reordered, only the renderer knows that.
 *
 * That division is what keeps the same list serving the printer and the preview — the preview shows
 * the renderer's own bitmaps, so the two cannot disagree about what fits.
 *
 * Proportions rather than columns. The item table's cells are fractions of the printable width, so
 * one layout serves 58 mm and 80 mm without a second set of numbers; the narrow roll differs only in
 * that a name and its figures cannot sit side by side and stay readable, so it takes two rows per item.
 *
 * (The dependency on `ui.components.ReceiptData` points the wrong way for a `data` package. It is
 * where the receipt model already lives, and `BusinessSettingsRepository` already reaches into
 * `ui.common.ImageCapture` the same way; moving `ReceiptData` down into `data/model` is a rename
 * across a dozen files and belongs in its own commit, not in this one.)
 */
object ThermalLayout {

    /**
     * Below this printable width, a name and its three figures cannot share a row and stay readable.
     *
     * In millimetres rather than characters, now that there is no character grid: 48 mm of 58 mm paper
     * leaves about 21 mm for a product name once the figures have taken theirs, which is four or five
     * characters at a legible size. So the narrow roll gets two rows per item and the wide roll gets
     * a table.
     */
    private const val MIN_MM_FOR_TABLE = 60

    /** The share of the width a header field's label takes — enough for "Secteur :" at any size. */
    private const val LABEL_FRACTION = 0.28f

    /** A small vertical gap, in dots. Deliberately small: paper is a running cost. */
    private const val SECTION_GAP = 6

    /** Feed past the tear bar, which sits ~15 mm above the head: 8 dots per mm. */
    private const val TEAR_OFF_DOTS = 120

    /**
     * @param logo already scaled, dithered and centred on [paper]'s dot width, no taller than
     *   `ReceiptRasterizer.LOGO_MAX_HEIGHT_MM`, or null when the business has no
     *   logo or its file is missing on this device. Rasterising needs a `Bitmap`, so it is the
     *   caller's job and this object stays free of Android types.
     */
    fun layout(
        receipt: ReceiptData,
        paper  : PaperProfile,
        logo   : MonoRaster? = null,
    ): List<ReceiptRow> = buildList {
        val money = receipt.money

        // ── Header ──
        logo?.let { add(ReceiptRow.Raster(it)) }
        add(ReceiptRow.Line(receipt.businessName.uppercase(Locale.FRENCH), RowAlign.Center, RowWeight.Bold))
        receipt.businessPhone?.takeIf { it.isNotBlank() }?.let {
            add(ReceiptRow.Line("Tel: $it", RowAlign.Center))
        }
        add(ReceiptRow.Line(badgeLabel(receipt.documentTitle), RowAlign.Center, RowWeight.Bold))
        add(ReceiptRow.Rule())

        // ── What this document is, and who for ──
        //
        // A label cell and a value cell. A long value wraps inside its own cell rather than pushing
        // the label out of line, which is what a two-cell row buys over a padded string.
        fun field(label: String, value: String?) {
            val text = value?.takeIf { it.isNotBlank() } ?: return
            add(ReceiptRow.Cells(listOf(
                Cell("$label :", LABEL_FRACTION),
                Cell(text, 1f - LABEL_FRACTION),
            )))
        }
        field("N°", receipt.referenceNumber)
        field("Date", receipt.dateLabel)
        field("Heure", receipt.timeLabel)
        field(receipt.partyLabel, receipt.partyName)
        field("Type", receipt.clientType)
        field("Secteur", receipt.clientSecteur)
        add(ReceiptRow.Rule())

        // ── Items ──
        if (paper.printableMm >= MIN_MM_FOR_TABLE) addAll(itemTable(receipt.items, money))
        else                                       addAll(itemPairs(receipt.items, money))
        add(ReceiptRow.Rule(RuleThickness.Thick))

        // ── Money ──
        add(ReceiptRow.Columns("TOTAL", money.da(receipt.total), RowWeight.Bold, RowScale.Large))
        if (receipt.paid > 0 || receipt.balance != receipt.total) {
            add(ReceiptRow.Columns("Payé", money.da(receipt.paid)))
            if (receipt.balance > 0) add(ReceiptRow.Columns("Reste", money.da(receipt.balance), RowWeight.Bold))
            else                     add(ReceiptRow.Columns("Statut", "Réglé"))
        }
        add(ReceiptRow.Rule())

        // ── Amount in words ──
        add(ReceiptRow.Line("Arrêté à la somme de :"))
        add(ReceiptRow.Line(receipt.amountInWords))

        receipt.note?.takeIf { it.isNotBlank() }?.let { note ->
            add(ReceiptRow.Cells(listOf(
                Cell("Note :", LABEL_FRACTION),
                Cell(note, 1f - LABEL_FRACTION),
            )))
        }

        // ── Signature, only where one is worth having ──
        //
        // On a fully paid receipt nobody signs anything; on one with a balance the signature is the
        // acknowledgement of the debt, which is the whole reason to print it.
        if (receipt.balance > 0) {
            add(ReceiptRow.Blank(SECTION_GAP))
            add(ReceiptRow.Line("Signature :"))
            add(ReceiptRow.Blank(SECTION_GAP * 4))
            add(ReceiptRow.Rule())
        }

        // ── Footer ──
        add(ReceiptRow.Line("Merci pour votre confiance !", RowAlign.Center, RowWeight.Bold))
        add(ReceiptRow.Blank(TEAR_OFF_DOTS))
        add(ReceiptRow.Cut)
    }

    // ───────────────────────────── items ─────────────────────────────

    /**
     * Wide paper: name, quantity, unit price and line total across one row.
     *
     * The shares are set by what must never wrap rather than by what looks balanced. A four-figure
     * unit price and a five-figure total have to fit on one line — money broken across two lines is
     * money misread — while a long product name wrapping is ordinary and costs a row nobody minds.
     * So the numeric columns are sized for their worst realistic value and the name absorbs the rest.
     * Grouped in the business's format ("99 999,99") an amount is a character or two longer, and its
     * no-break spaces stop StaticLayout from wrapping it at a group: ReceiptAmountsFitTest draws these
     * worst cases in every format on both rolls and checks each stays on one line.
     */
    private fun itemTable(items: List<ReceiptLineItem>, money: MoneyFormatter): List<ReceiptRow> = buildList {
        add(ReceiptRow.Cells(
            listOf(
                Cell("Article", NAME_SHARE, RowAlign.Left),
                Cell("Qté", QTY_SHARE),
                Cell("P.U.", PRICE_SHARE, RowAlign.End),
                Cell("Total", TOTAL_SHARE, RowAlign.End),
            ),
            weight = RowWeight.Bold,
        ))
        add(ReceiptRow.Rule())
        items.forEach { item ->
            add(ReceiptRow.Cells(listOf(
                // Left rather than Start: a column of names is read down its left edge, and Arabic
                // entries starting at the right would leave no edge to read down. See RowAlign.Left.
                Cell(item.name, NAME_SHARE, RowAlign.Left),
                Cell("${Quantity.format(item.quantity)} ${item.unitLabel}", QTY_SHARE),
                Cell(money.amount(item.unitPrice), PRICE_SHARE, RowAlign.End),
                Cell(money.amount(item.totalPrice), TOTAL_SHARE, RowAlign.End),
            )))
        }
    }

    // The four shares of the item table, summing to 1. See itemTable.
    private const val NAME_SHARE  = 0.30f
    private const val QTY_SHARE   = 0.21f
    private const val PRICE_SHARE = 0.22f
    private const val TOTAL_SHARE = 0.27f

    /**
     * Narrow paper: the name on its own row, then the figures beneath it with the line total pinned
     * to the far edge.
     *
     * Nothing separates one item from the next. The un-indented name already reads as the start of an
     * item, and a blank row per item is a row of paper per item.
     */
    private fun itemPairs(items: List<ReceiptLineItem>, money: MoneyFormatter): List<ReceiptRow> = buildList {
        items.forEach { item ->
            add(ReceiptRow.Line(item.name, RowAlign.Left))
            // One space of indent, not three: grouped, a 9 999,99 unit price beside a 99 999,99 line
            // total overflowed the 48 mm line by a few dots and pushed the price onto a row of its own.
            // ReceiptAmountsFitTest holds that worst case to one line in every format.
            add(ReceiptRow.Columns(
                left  = " ${Quantity.format(item.quantity)} ${item.unitLabel} × ${money.amount(item.unitPrice)}",
                right = money.amount(item.totalPrice),
            ))
        }
    }

    private fun badgeLabel(documentTitle: String): String =
        if (documentTitle.startsWith("Vente")) "REÇU DE VENTE" else "BON D'ACHAT"
}
