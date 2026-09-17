# CSV export

What DistriGo exports for a spreadsheet or an accountant, and how the files are written. The code is `data/export/`: `CsvWriter` writes the format, `ExportDataset` defines each file, `CsvExporter` streams them from the database.

## The format

Written for French Excel opened by double-click:

- **UTF-8 with a byte-order mark**, so accents and Arabic names display correctly.
- **`;` between cells, CRLF between rows**, under a first line reading `sep=;`. A double-clicked CSV is split by the list separator of *that* computer's Windows region — `,` on an English one, which would put every row in one column — and `sep=;` is Excel's own way to be told otherwise. Tools other than Excel may show that line as a first row; importing the file rather than opening it ignores it.
- **Numbers with a decimal comma**, no thousands separator, never in scientific notation: `3450,00`. Amounts have 2 decimals (DA); quantities up to 3, without trailing zeros: `12,5`.
- **Dates in local time**: `17/09/2026 00:30` for moments, `17/09/2026` for calendar dates.
- **Quoted** only when a cell holds `;`, `"`, a line break, or leading or trailing spaces.
- **Formulas cannot run.** A text cell starting with `=`, `+`, `-`, `@`, a tab or a carriage return is written with a leading `'`, which Excel shows. Amounts and dates are formatted by the writer, not taken from user text, so a negative amount stays a number.

Excel still reads a code made only of digits — a barcode, a `0555…` phone number — as a number, dropping leading zeros. To keep them, import the file (Données › À partir d'un fichier texte/CSV) and set those columns to Texte.

One dataset is a `.csv`; several are a `.zip` of `<dataset>.csv` files, read in one transaction so they describe one moment.

## The datasets

Documents, payments, movements, charges and pertes are chosen by a period of **local days**, both included, over the moment they happened (see [timestamps.md](timestamps.md)): a sale at 00:30 belongs to its own day. Clients and products are exported as they are now. Rows in the bin (`deleted_at`) are left out; payments still name a client or supplier who was since moved to the bin. Document numbers read as the app shows them: `V-6DED-000124`, or `#26` for documents created before numbering.

| File | Rows | Columns |
|---|---|---|
| `ventes.csv` | Sales, by creation time | N°, Date, Client, Origine (Dépôt/Camion), Statut (En attente/Livré), Total, Payé, Reste, Note, Vendeur |
| `lignes-de-vente.csv` | Sale lines, by their sale's time | N° vente, Date, Client, Produit, Unité, Quantité, Prix unitaire, Total |
| `achats.csv` | Purchase orders, by creation time — or by their calendar date for older bons without one | N°, Date, Fournisseur, Statut (En attente/Reçu), Total, Payé, Reste, Note |
| `paiements-clients.csv` | Client payments | Date, Client, Montant, Note |
| `paiements-fournisseurs.csv` | Supplier payments | Date, Fournisseur, Montant, Note |
| `clients.csv` | Clients, by name | Nom, Téléphone, Type (Détail/Gros/Société), Secteur, Wilaya, Commune, Adresse, Solde, Note |
| `produits.csv` | Products, by name | Nom, Code-barres, Catégorie, Sous-catégorie, Marque, Fournisseur, Unité, Unités par colis, Prix d'achat, Prix de vente, Stock total, Stock dépôt, Stock camion, Stock minimum, Date de péremption |
| `mouvements-de-stock.csv` | Stock movements | Date, Produit, Type (Achat/Vente/Perte/Ajustement/Retour client/Retour fournisseur), Sens (Entrée/Sortie), Emplacement, Quantité, Prix unitaire, Valeur, Origine, Note, Utilisateur |
| `charges.csv` | Charges, by their date | Date, Type, Sous-type, Montant, Fournisseur, Note |
| `pertes.csv` | Pertes, by their date | Date, Type, Produit, Quantité, Unité, Emplacement, Valeur, Motif |

**Stock.** `products.stock` is the total, dépôt and camion together; `camion_stock` is the camion's share. Stock dépôt is their difference, as the product screen shows it.
