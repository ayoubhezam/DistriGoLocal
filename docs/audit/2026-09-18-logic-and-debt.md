# Logic Issues & Technical Debt Report — 2026-09-18

> **Status (same day):** 1.1, 1.2, 1.4, 3.1, 3.2, 3.5 and 5.1 are fixed (commits c7fade2–c54b65c). 1.5 is fixed for quantities, prices and payments; a paid amount above the total is **by design** an advance, now shown as such in the Solde cell (d3bf135). The rest stands as written.

Scope: the whole app as of commit `d145a40` (338 Kotlin files, ~60k lines). Method: a pattern sweep (writes outside transactions, swallowed exceptions, blocking calls, `!!`, clock use) followed by a close read of everything that moves money or stock (`ProductRepository`, the ledger and balance formulas, the triggers, drafts) and of the backup / restore / import / Corbeille code. Nothing was changed. Each item says where it is, what can go wrong, and how sure I am (**verified** = read the code path end to end; **likely** = strong evidence, not traced through the UI).

Severity: **H** data can become wrong or lost · **M** wrong in an edge case, or a crash · **L** debt, hygiene, scaling.

## 1. Money and stock logic

| # | Sev | Where | Issue |
|---|---|---|---|
| 1.1 | **H** | `ProductRepository.receivePurchaseOrder` (~l. 790) | `productDao.getProductById(item.product_id) ?: continue` — a product that is in the **Corbeille** (or gone) when the bon is received is **silently skipped**: no stock movement, yet the bon is marked `received` and its total stays in the supplier balance. Before the Corbeille this needed a hard delete; now one tap on "Supprimer" makes it reachable. *Verified.* |
| 1.2 | **H** | `createVente` / `updateVente` camion check (~l. 963, 1055) | The "stock insuffisant" check is **per line, not per product**: two lines of the same product (8 + 8 with 10 in the camion) both pass, and the camion goes negative. Sales from the dépôt intentionally allow negative stock, so this only matters for tournées. *Verified.* |
| 1.3 | **M** | `deliverVente`, balance formulas (`ClientDao.recomputeBalance`, `SupplierDao.recomputeBalance`) | `pending` vs `delivered`/`received` is a **label for sales**: stock leaves at creation and the client owes the total immediately. For purchases the opposite: stock enters only at receipt, but the **debt is counted from creation** (`SUM(total) − SUM(montant_paye)` with no status filter). Consistent with the migration notes, but an unreceived bon already appears as money owed; a cancelled one must be deleted, not left pending. Worth a deliberate decision and a sentence in the docs. *Verified.* |
| 1.4 | **M** | `updateVente` (~l. 1042) | Editing any sale whose product has since gone to the Corbeille throws `Produit introuvable` for every field — the note or the amount paid of an old sale cannot be changed once one of its products is binned. The draft path handles this (`missingProductIds`); the direct edit path does not. *Verified.* |
| 1.5 | **M** | `createVente`, `updateVente`, `createPurchaseOrder`, `updatePurchaseOrder`, `addClientPayment` | **No validation that `montant_paye ≤ total`** or that amounts/quantities are ≥ 0 at the repository level; `grep` finds no such check in the forms either. A typo in the paid amount produces a negative balance that the ledger will faithfully carry. *Likely.* |
| 1.6 | **M** | `deleteChargement`, `createChargement` | Transfers dépôt → camion have **no availability check** (dépôt may go negative by design), and `camion_stock` is a single global figure: the app assumes **one truck**. `getOpenTournee()` is `LIMIT 1` — two open tournées would share the same camion stock silently. *Verified.* |
| 1.7 | **M** | `deleteClient` → `ClientsScreen` "Dettes en cours" (`debtClients.sumOf { it.balance }`) | Binning a client with a debt removes it from the debts total and from every client list, while its sales, payments and balance remain. Money owed disappears from view until the client is restored. (Same for suppliers.) *Verified.* |
| 1.8 | **L** | Whole domain | Amounts and quantities are `Double`; totals are `sumOf(q × p)`. Comparisons like `quantity > product.camion_stock` compare a typed value with a `SUM()` of decimals, so `0.1 × 3` vs `0.3` can refuse or allow a sale by a rounding hair. Display rounds to 2 decimals, so stored totals and printed totals can differ by 0.01 on long invoices. *Likely.* |
| 1.9 | **L** | Ledger triggers (`StockLedger.kt`) | Every movement insert recomputes `stock` and `camion_stock` with two full `SUM` subqueries per product; `insertAll` of *N* lines = *N* recomputes; the `products` guard trigger runs the sums again on any stock write. Fine at hundreds of products; a `chargement_items` sum is not covered by an index on `product_id` (only `stock_movements` has one). *Verified.* |

## 2. Soft delete (Corbeille) interactions

| # | Sev | Where | Issue |
|---|---|---|---|
| 2.1 | **M** | Forms and import vs. the bin | Uniqueness of a product name/barcode or a category name is enforced **only against live rows** (form: an in-memory list; import: `getAllProducts()`). A new row can take the name of a binned one; restoring the binned one is then refused by `restoreConflict`, with a message that names the clash but leaves the user to rename by hand. Acceptable, undocumented. *Verified.* |
| 2.2 | **M** | `ProductFormScreen` duplicate check | The check reads the `products` list held by the view model. On a cold screen (list still loading, or filtered) the check passes and a **duplicate name/barcode is saved**; there is no unique index (only on `uuid`). *Likely.* |
| 2.3 | **L** | `TrashRepository.deletePermanently` (product) | Deletes the `product_images` **rows** but not the image **files** they point to; the files stay in app storage forever (and in every backup). *Verified.* |
| 2.4 | **L** | `receivePurchaseOrder`, exports, ledgers | Several joins go through `*_name` columns denormalised at creation; a rename or a restore does not touch them, so a bon shows the supplier's old name while the supplier screen shows the new one. By design (paper keeps its name), but not stated anywhere. |

## 3. Concurrency and state management

| # | Sev | Where | Issue |
|---|---|---|---|
| 3.1 | **M** | `ProductRepository.updateTournee`, `deleteTournee`; `ChargeRepository.deleteChargeType`; `BusinessSettingsRepository.saveLogo`; the four draft `upsert`s | **Multi-statement writes outside a transaction.** `updateTournee` writes the row, then deletes and reinserts the secteurs — a failure in between leaves a tournée with no secteurs. `deleteTournee` checks for linked sales then deletes (check-then-act). Draft `upsert` is read-then-insert/update; two overlapping autosaves with `draftId == null` can create **two drafts**. The debounce makes that rare, not impossible (flush on `ON_STOP` runs beside the pending debounced write; the comment says it is idempotent — it is only once a `draftId` exists). *Verified for the tournée and drafts.* |
| 3.2 | **M** | `ChargeRepository.seedDefaultChargeTypesIfNeeded` | Check-then-insert with no transaction and no unique constraint on the name; two callers on first launch (two screens, or a restore followed by a screen open) can seed twice. Deterministic `uuid`s mean the second insert fails on the `uuid` index rather than duplicating — but it **throws** instead of being a no-op. *Likely.* |
| 3.3 | **M** | `AppDatabase.getDatabase` | `RestoreInstaller.installPending()` runs inside the `synchronized` block **on whatever thread first asks for the database** — Hilt injection on the main thread at first screen. Normally a no-op, but with a restore waiting it moves/renames a 300 KB–multi-MB database and every photo on the main thread. `DistriGoApplication.onCreate` does the same on purpose (blocking on purpose is fine; doing it twice from two places is the debt). *Verified.* |
| 3.4 | **M** | `AutoBackupWorker` / `AutoBackupRunner` | A backup can run while the app is writing. `DatabaseSnapshot` copies through SQLite, so the file is consistent, but `DataFingerprint` is read **before** the copy: a change landing between the two is recorded as "backed up" and skipped next night if nothing else changes. One-day-late backup at worst. *Likely.* |
| 3.5 | **L** | `DistriGoApplication.onCreate` | `ensureScheduled()` runs on a raw `Thread` with no error handling; an exception there kills the process at startup (uncaught on a non-main thread = crash). *Verified.* |
| 3.6 | **L** | 135 `catch (e: Exception)` sites, mostly view models | Most map to a user message via `extractErrorMessage`; some swallow with a `Log.w` only (`deleteQuietly`, `displayName`, photo helpers). `CancellationException` is caught along with the rest in view-model `launch` blocks, so a cancelled coroutine can surface as an error toast. *Likely.* |
| 3.7 | **L** | Screens with `!!` on `entry.arguments` (nav hosts, 25+ sites) and `longPressType!!` in dialogs | Crash if a route is ever entered without its argument (deep link, process death with a changed graph). Not reachable today. |

## 4. Backup, restore, import, export

| # | Sev | Where | Issue |
|---|---|---|---|
| 4.1 | **M** | `RestoreCoordinator.rotateSafetyBackups`, `ImportRepository.apply` | Rotation keeps 3 restore copies and 3 import copies; the sizes are ~300 KB now but grow with photos? — no: safety copies are `BackupCreator` archives **with photos**. Six of them on a phone with a big gallery is six times the gallery in `no_backup/`. There is no size cap and no age cap. *Verified.* |
| 4.2 | **M** | `ImportRepository.apply` staleness check | The re-plan compares the **entire plan**, so an unrelated edit to any product named in the file (a stock movement does not count, a price does) aborts the whole import with "les données ont changé". Correct, conservative, and could surprise a user editing while previewing. |
| 4.3 | **M** | `ImportPlanner` reservations | A refused row that names an existing product reserves nothing, but a row that *passes* reserves the product; a later row for the same product is refused as "déjà sur une ligne plus haut" even when it only adds a different column. Documented behaviour, but a merged-columns file (two sheets exported separately and pasted) will trip on it. |
| 4.4 | **L** | `XlsxReader` | Parts are held in memory until the ZIP is fully read (80 MB cap, 40 MB per part). A legitimate 60 MB workbook (many sheets of sales history someone pasted in) is refused as "trop volumineux" although only the Produits sheet was needed. |
| 4.5 | **L** | `ExportDatasets` / `DataExporter` | Exports read through `db.openHelper.readableDatabase` in one transaction — correct — but a sale created during a long export is not in it; fine. `ExportPeriod` uses local days from `BusinessDates`; a phone whose timezone changed between two exports (travel) shifts which day a 00:30 sale belongs to. |
| 4.6 | **L** | `DocumentNumberTriggers` | Counters live in `app_meta` and **travel with the database**; a restored backup carries the counters and the `database_id`, while `withDeviceIdentity` rewrites `device_id` on restore. So after restoring phone A's backup on phone B, new numbers use B's prefix with A's counter values — no duplicates, but a visible jump. Fine; noting for the sync design. |

## 5. Navigation and UI state

| # | Sev | Where | Issue |
|---|---|---|---|
| 5.1 | **M** | `ProduitsNavHost` l. 105/132/175/200, `AchatsNavHost` l. 149, `InventoryNavHost` l. 186/211 | The same **"entity vanished → auto-pop"** pattern that crashed clients/suppliers exists here. Products and bons only auto-pop (no explicit pop after delete), so they don't double-pop today; any future "delete then `popBackStack()`" in these hosts reproduces the crash. Consider the `leave()` guard everywhere the pattern is used. *Verified.* |
| 5.2 | **M** | `DataBackupScreen`, `ParametresScreen`, `TrashScreen` | Sub-screens are shown by **`if (showX) { Screen(); return }` inside the parent composable**, not by navigation: Back is handled by `BackHandler`s, state survives rotation only through `remember`, and a process death lands on the parent. The Export → Import → Données refresh bug fixed today is a symptom. |
| 5.3 | **L** | `TourneesScreen` (1666 lines), `PurchaseFormNavGraph` (1288), `ProductFormScreen` (1256), `VentesScreen` (1224), `ProductsScreen` (1094) | Composables that own dozens of `remember` states, dialogs and side effects. Hard to test, easy to leave a state flag set (`showDeleteConfirm`, `deleteError`) across recompositions. |
| 5.4 | **L** | `MainActivity` (632 lines) | Four copies of the same top-bar wiring with `/* TODO: notifications */` and `/* TODO: profile */`; the dashboard is still "Tableau de bord en construction". |

## 6. Technical debt

| # | Sev | Where | Issue |
|---|---|---|---|
| 6.1 | **M** | `ProductRepository` (1724 lines) | One class holds products, categories, suppliers, purchases, sales, payments, chargements, tournées, secteurs and images. Public API is `Map<String, Any?>` in and `Map<String, Any>` out with `as Number` / `as String` casts: a missing or mistyped key is a `ClassCastException`/`NullPointerException` at runtime, and `mapOf("error" to …)` vs thrown exceptions are two error channels callers must know about. |
| 6.2 | **M** | Room DAOs `@Insert(onConflict = REPLACE)` on products/clients | `REPLACE` on a conflict **deletes and re-inserts**, which fires the tombstone trigger and resets `origin_device_id`/`created_at`. Today conflicts cannot happen (only `uuid` is unique and it is generated), but any future unique index turns an update into a delete+insert with a tombstone. |
| 6.3 | **L** | `Migrations.kt` (946 lines, v52) | Migrations carry repaired-formula history (v41) that must never be edited; that is documented. What is missing is a **schema-export diff test** for the last few versions; the existing tests cover the migration policy, not each step. |
| 6.4 | **L** | Time handling | 66 direct uses of `Instant.now()` / `LocalDate.now()` / `System.currentTimeMillis()` in production code, with three formats in the database: ISO instants (`created_at` text), epoch ms (`updated_at`, `deleted_at`), calendar dates (`date`). `BusinessDates` centralises the day logic, but nothing injects a clock, so time-dependent behaviour (03:00 backups, "today's" chargement session, expiry) is untestable without sleeping. |
| 6.5 | **L** | Comments and naming | Mixed Arabic/French/English comments and stale ones ("← جديد", "moved off VenteViewModel"); `data/ProductRepository.kt` lives outside `data/repository/` though its package says otherwise. |
| 6.6 | **L** | Libraries | Kotlin 2.0.21, AGP 8.12.3, Room 2.7.2, Hilt 2.56.2, WorkManager 2.10.1 — current. Compose BOM version not checked here. |

## What I would look at first

1. **1.1** (silent skip at receipt) — the only item that can lose stock quietly through a normal tap sequence.
2. **1.2** (per-line camion check) and **1.5** (no `paid ≤ total`) — small validations that close real money paths.
3. **3.1** (tournée and draft writes without a transaction) — wrap in `withTransaction`; one line each.
4. **1.7 / 2.1** (Corbeille and debts / uniqueness) — decide the rule, then write it in `docs/`.
5. **5.1** — apply the `leave()` guard in the other nav hosts before anyone adds a delete there.
