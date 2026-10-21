package com.distrigo.app.data.importer

import com.distrigo.app.data.export.ExportCell
import com.distrigo.app.data.export.ExportColumn
import com.distrigo.app.data.export.XlsxWriter
import java.io.OutputStream

/**
 * « Télécharger un modèle vide » (owner, 2026-10-21): a workbook with one sheet named as the import expects and,
 * as its first row, only the columns the import reads — no « Solde » nor « Stock camion » to fill in for nothing.
 * The headers are the export's, so a template and an export read the same.
 */
object ImportTemplate {

    /** The columns of [sheet]'s template, in the export's order and words. */
    fun headers(sheet: ImportSheet): List<String> = when (sheet) {
        ImportSheet.PRODUITS -> listOf(
            "Nom", "Code-barres", "Catégorie", "Sous-catégorie", "Marque", "Fournisseur", "Unité", "Unités par colis",
            "Prix d'achat (DA)", "Prix de vente (DA)",
            // A new product's opening stock; an existing product's is never read
            "Stock initial",
            "Stock minimum", "Date de péremption",
        )
        ImportSheet.CLIENTS -> listOf("Nom", "Téléphone", "Type", "Secteur", "Wilaya", "Commune", "Adresse", "Note")
    }

    /** The file's name, as the save dialog proposes it. */
    fun fileName(sheet: ImportSheet): String = "modele-import-${sheet.plural}.xlsx"

    /** Writes [sheet]'s empty template to [out], which it does not close. */
    fun write(sheet: ImportSheet, out: OutputStream) {
        val writer = XlsxWriter(KeepOpen(out))
        writer.sheet(sheet.title, headers(sheet).map { ExportColumn<Unit>(it) { ExportCell.Text(null) } }, emptySequence())
        writer.close()
    }

    /** Lets the writer be closed, to finish the file, without closing the stream underneath. */
    private class KeepOpen(out: OutputStream) : java.io.FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }
}
