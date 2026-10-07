# DistriGo

Offline-first Android app for a distributor: dépôt stock, tournées (camion), ventes, achats, clients,
suppliers, inventaire, pertes, retours, rapports. Kotlin, Jetpack Compose, Hilt, Room (KSP, schemas
exported to `app/schemas`). All data is local; sync is out of scope until DistriGo Solo ships.
UI text is French.

Code lives in `app/src/main/java/com/distrigo/app/`: `data/` (Room entities, DAOs, repositories,
backup, import, print) and `ui/` (one package per section, plus `common/`, `designsystem/`,
`navigation/`).

## Two kinds of document forms

**Inventaire, Pertes and Retours: list → centred dialog → cart → dated summary.**
The user picks a product from a list (or scans it), enters its quantity in a centred `Dialog`, and the
line goes to the selection; a summary with the date (`InventoryDateField`) saves the document.
- Inventaire: `ui/inventory/` (`InventoryCountScreen`, `InventoryCartScreen`).
- Pertes: `ui/pertes/NewPerteScreens.kt`, `PerteDialog.kt` (a new perte type is created in the dialog).
- Retours: `ui/retours/NewRetourScreens.kt` (`RetourDialog`, a motif per line), driven by
  `RetourClientFormNavGraph` / `RetourFournisseurFormNavGraph`. Client and supplier returns share the
  abstract `NewRetourViewModel`.

**Ventes, Achats, Tournée ventes and Chargements keep their own form graphs**
(`VenteFormNavGraph`, `TourneeVenteFormNavGraph`, `PurchaseFormNavGraph`, `ChargementNavHost`).
They are stable: do not migrate them to the dialog flow unless asked.

Both kinds share the "Ma sélection" components in `ui/common/CartSelectionComponents.kt`
(`SelectionCartCard`, `CartBlockingBanner`, `QuantityStepper`); the visual reference is
`docs/design/distrigo_ma_selection_unifiee.html`.

## "Autoriser le stock négatif"

A setting (`business_settings.allow_negative_stock`), not a fixed rule. Dépôt stock is
`stock - camion_stock`.
- **On:** stock may go below zero.
- **Off (strict):** enforced in two layers:
  - `data/model/StockPolicy.kt` only shapes the controls: stepper `max`, a disabled "add". Read by the
    form ViewModels (ventes, chargements, pertes, retours).
  - `data/repository/DepotStockGuard.kt` is the actual rule. It wraps a write in a transaction,
    compares each product's dépôt stock before and after, and rolls back when a product ends below
    zero *and lower than it started* (so stock already negative can still be restocked or edited).
    Every new write path that moves stock must go through the ledger (`data/local/database/StockLedger.kt`)
    and the guard.
- A restore whose backup has a different setting warns first (`data/backup/RestoreStockRule.kt`).

## Drill-down navigation

`ui/navigation/DrillDown.kt`. A screen says *what* to open, never which route:
`LocalDrillDown.current(DrillTarget.Client(id))`. `LocalDrillDown` is provided once at the root, so
any section can open any record. Targets: Client, Supplier, Bon, Vente, Perte, Inventaire,
RetoursClient, RetoursFournisseur, Produit.
- `drillDown()` pops back to the record if it is already open on the stack (Back never loops) and
  ignores a double tap.
- To add a target: a `DrillTarget` case, its `Screen.Drill` route, and its case in `screen()`.
- Inside a section opened on a record, go back with `popOr(exit)`, never a bare `popBackStack()`.
- Reports reload when their tables are written (see `VentesReportViewModel`), so a drill-down and
  Back shows fresh numbers.

## Database

- `DATABASE_VERSION` is in `data/local/database/AppDatabase.kt`; migrations in `Migrations.kt`.
- Bump the version *before* compiling an entity change (the schema export would overwrite the
  previous version's JSON). New `ALTER TABLE` columns go last in the entity. Drop triggers
  (`ChangeTracking.kt`) before rebuilding a table.

## Conventions

- Quantities are `Double`: carton/kg take up to 3 decimals, pièce whole units (`data/model/Quantity`).
- Money is formatted through the user's setting (spaces, commas or dots); don't hard-code a format.
- Keep the horizontal swipe between tabs.
