# Import from Excel

Données et sauvegarde › Importer depuis Excel adds or updates **products and clients** from a `.xlsx` workbook. The code is `data/importer/`: `XlsxReader` reads the workbook, `ImportPlanner` decides what each row would do, `ImportRepository` applies it; the screen is `ui/settings/data/importer/`.

## The file

The export is the template: a workbook with a sheet named **Produits** and/or **Clients**, whose first row holds the headers the export writes (see [csv-export.md](csv-export.md)). Sheet names and headers are matched without case, accents or the unit in brackets, so `prix de vente` finds *Prix de vente (DA)*; a few short forms are accepted (`PV`, `PA`, `Code barre`, `Tél`). Columns the import does not know are ignored and listed. Only the columns present are read, so a file holding a name and a new price is enough.

The reader handles what Excel actually saves — shared and rich text, formulas' last result, dates stored as numbers with a date format, the 1904 calendar — and refuses `.xls`, password-protected files, damaged files, DOCTYPEs and files past its size limits, each with its own message.

## What a row does

- **Match.** A product is found by its barcode, else by its name; a client by its name. Names match without case or accents, and a name that only differs that way keeps the app's spelling. No match: the row **creates**; a match: the row **updates**; nothing to change: **unchanged**.
- **Blank cell = leave it.** For a new row, a blank takes the default the form would (unit *carton*, stock minimum 10, no expiry). Clearing a value is done in the app, not by the file.
- **Barcode.** Required in the form, so a new product without one gets `id` padded to 13 digits, as the form's button makes it.
- **Lookups.** Categories, sub-categories (under the row's category, or the product's), brands, suppliers and secteurs (in the row's commune, or the client's) that the app lacks are **created** and listed in the preview. A wilaya or a commune must be one the app knows, as the client form only offers those.
- **Refused rows** (missing name, unknown unit or type, negative price, a cell that is not a number or a date, a name or barcode another product has, the same product twice in the file, a sub-category without a category…) are skipped with a reason; the rest of the file still imports.
- **Never from the file:** an existing product's stock (a new product takes its *Stock dépôt* as an opening adjustment), a client's balance, and deletions — a row absent from the file is left alone.

## Safety

The preview shows every row and, for an update, each change (`Prix de vente : 120,00 → 130,00`). Applying takes a safety backup first (`avant-import-…`, kept in the same place as the `avant-restauration-…` copies, three of each) and writes everything in **one transaction**, planning again inside it: if any row changed in the meantime, nothing is written and the user is asked to preview again. Products go through `ProductRepository`, so triggers, versions and the stock ledger see the import like any other edit.
