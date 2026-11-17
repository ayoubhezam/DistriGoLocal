# Technical and Logic Audit — 2026-11-09

Scope: `main` at `b12e317` plus the uncommitted cloud-backup work (606 Kotlin files, 107,615 lines), the Supabase
Edge Functions and the SQL migrations. Audit only: no code was changed. The same report is published as a page with
filters: https://claude.ai/artifact/WPrvpeBkszzKCa2DuWDpxx

Paths are relative to `app/src/main/java/com/distrigo/app/` unless they start with `app/`, `docs/` or `supabase/`.

## Verdict

The infrastructure is strong. The stock ledger, the backup and restore path, the cloud encryption and the license token
checks are carefully built, and I found no way to corrupt data through them. The serious problems sit one level up, in
business rules that this infrastructure records faithfully: how a typed amount is read, what a return is worth and where
it goes, what an inventory count means, and what a product costs. None of them crashes the app. Each one quietly writes
a wrong number.

The first item is a small input fix with the largest money impact. Items 2 to 7 change how stock and money are valued,
so each needs a decision from the owner before code.

**26 findings:** 1 Critical, 6 High, 9 Medium, 10 Low.

## How severity is graded

| Severity | Meaning |
|---|---|
| **Critical** | Silently writes wrong money or stock on a routine path, in the default settings. |
| **High** | Wrong money, stock or reports in common situations, or user data lost through a plausible sequence. |
| **Medium** | Wrong in specific situations, recoverable, or a security hardening gap. |
| **Low** | Edge cases, latent traps, growth and hygiene. |

**Verified** means the path was read end to end in code. **Likely** means it is traced in code but was not reproduced
on a device. **Known** means it was already recorded elsewhere.

## Priority order

| # | ID | Severity | Area | Finding | Confidence |
|---|---|---|---|---|---|
| 1 | FIN-01 | Critical | Money | Paid amounts typed with a decimal comma are saved 10 to 100 times too large | Verified |
| 2 | STK-01 | High | Stock | Inventaire counts against total stock but books at the dépôt | Verified |
| 3 | FIN-02 | High | Money | Client returns credited at today's catalogue price | Verified |
| 4 | DATA-01 | High | Data integrity | A draft can come back after its sale is saved | Likely |
| 5 | FIN-03 | High | Money | « Refusé (perte) » supplier returns still lower the supplier debt | Verified |
| 6 | STK-02 | High | Stock | Every client return goes into the truck | Verified |
| 7 | FIN-04 | High | Reports | A product's cost never follows what you pay for it | Verified |
| 8 | FIN-05 | Medium | Money | Unrounded totals: settled documents show « Reste 0,00 DA » | Verified |
| 9 | BAK-01 | Medium | Backup | Restoring an older backup re-issues printed document numbers | Verified |
| 10 | DATA-02 | Medium | Data integrity | Deleting a tournée with sales still deletes its drafts | Verified |
| 11 | STK-03 | Medium | Stock | Losses can only be recorded at the dépôt | Verified |
| 12 | STK-04 | Medium | Stock | Nothing guards truck stock against going negative | Verified |
| 13 | REP-01 | Medium | Reports | The Résultat leaves out inventory shortages | Verified |
| 14 | LIC-03 | Medium | Licensing | The live server still trusts the debug signing key | Verified |
| 15 | LIC-01 | Medium | Licensing | The one-phone rule can be sidestepped as a « reinstall » | Verified |
| 16 | LIC-02 | Medium | Licensing | Repackaged clients activate; release builds are not obfuscated | Verified |
| 17 | REP-02 | Low | Reports | Invoice status ignores later payments | Verified |
| 18 | DB-01 | Low | Database | No foreign keys, and orphan rows already exist | Verified |
| 19 | FIN-06 | Low | Money | Repository traps the screens currently avoid | Verified |
| 20 | UX-01 | Low | Returns | Only sales marked « Livré » can be returned | Verified |
| 21 | DB-02 | Low | Database | Tables and files that only grow | Verified |
| 22 | PERF-01 | Low | Performance | Whole client and supplier tables re-read after every sale | Verified |
| 23 | DB-03 | Low | Database | Indexes missing for current queries | Verified |
| 24 | PERF-02 | Low | Performance | Screens keep collecting while the app is in the background | Verified |
| 25 | LIC-04 | Low | Licensing | Trials trust an unverified ANDROID_ID | Known |
| 26 | LIC-05 | Low | Licensing | The clock anchor can be rolled back on a rooted phone | Verified |

## Findings

### 1. FIN-01 — Paid amounts typed with a decimal comma are saved 10 to 100 times too large, and amounts with two dots are saved as 0

**Critical · Money · Verified**

**What happens.** The « Montant payé » field in Dépôt vente, Tournée vente and Achats keeps only digits and dots. The app
shows money as `1 500,50 DA` by default, with a decimal comma, and the code itself notes that the French keyboard's
decimal key types a comma. Typing `1500,50` drops the comma, the field becomes `150050`, and the form shows « Réglé ✓ »
because 150 050 is at least the total. The sale is saved with 150 050 DA paid, and the client ends up with an advance of
148 549,50 DA. With the « points » money format, typing `1.500` saves 1,5 DA. Typing `1.500.00` has two dots, the save
falls back to `?: 0.0`, and 0 is recorded with no message. The cart's unit-price field uses the same filter: once its
« .00 » is erased, typing `120,5` gives 1205.

**Evidence.**
- `ui/ventes/VenteFormScreen.kt:374`, `ui/tournees/TourneeVenteFormScreen.kt:362`, `ui/purchases/PurchaseFormScreen.kt:358`:
  `filter { it.isDigit() || it == '.' }`
- `ui/navigation/VenteFormNavGraph.kt:704, 717, 748`, `ui/navigation/TourneeVenteFormNavGraph.kt:633`,
  `ui/navigation/PurchaseFormNavGraph.kt:1115, 1126`: `montantPaye.toDoubleOrNull() ?: 0.0`
- `ui/common/CartSelectionComponents.kt:526–533`: unit price, same filter, no limit on decimals
- `data/model/Amount.kt:43`: `Amount.sanitizeInput` already turns ',' into '.', keeps one dot and two decimals. These
  fields don't use it.
- `core/format/MoneyFormat.kt:14`: the default format, `1 236 790,50 DA`

**Impact.** Client or supplier balances wrong by up to 100 times the payment, on the most-used screen, and displayed as
settled. It can be repaired by editing the document, but only once someone notices.

**Fix.** Use `Amount.sanitizeInput` and `Amount.parse` in these four fields. Refuse an unreadable amount instead of
saving 0. Ask for confirmation when the amount paid is more than the total (an advance).

### 2. STK-01 — Inventaire compares a count with the total stock but books the difference at the dépôt, and zeroes uncounted products at the dépôt only

**High · Stock · Verified**

**What happens.** For a counted product the dialog shows « Qté système » as `products.stock`, which includes what the
truck holds. `recordScan` computes `ecart = counted − stock` and books it as a dépôt adjustment. With 30 in the warehouse
and 20 in the truck (stock 50), a warehouse count of 30 gives an écart of −20 and the dépôt drops to 10. For uncounted
products, closing the count does the opposite: « Leur stock au dépôt passe à 0. Le stock des camions n'est pas touché. »
The two halves assume different things about what was counted, so one of them is wrong whenever the truck holds the
product. The truck's stock carries over from one tournée to the next, so this is the normal case. With « Autoriser le
stock négatif » on (the default), the wrong adjustment is saved silently. In strict mode it is refused as a stock
shortage.

**Evidence.**
- `data/repository/InventoryRepository.kt:138–139`: `qteSysteme = product.stock`, `ecart = counted − qteSysteme`; `:162`
  booked at `"depot"`
- `data/repository/InventoryRepository.kt:74–77`: uncounted products counted at `camion_stock`, so the dépôt goes to 0
- `ui/inventory/InventoryCountScreen.kt:248–250`: « Qté système » shows `product.stock`
- `app/src/main/res/values/strings_inventory.xml:44`: `inventory_zero_detail`

**Impact.** The operation meant to correct stock corrupts the split between dépôt and truck, and misstates the écart
value.

**Fix.** Decide what a count covers. The likely answer is the dépôt only: show « Qté système (dépôt) » as
`stock − camion_stock`, compute the écart against it, and keep the zeroing as it is. Alternatively, ask for dépôt and
truck quantities separately.

### 3. FIN-02 — Client returns are credited at today's catalogue price, not at the price the client paid

**High · Money · Verified**

**What happens.** A return values each line at `product.selling_price` on the day of the return, and the return's total
is taken off the client's balance. The price on the sale is ignored. A unit sold at 80 DA (a price changed in the cart)
and listed at 100 DA today is credited 100 DA. After a price cut, the client is under-credited. The same figure feeds the
Résultat's « retours », whose comment says « at the price they were sold at », and the « Ventes facturées » measure
behind Mon objectif.

**Evidence.**
- `data/repository/RetourClientRepository.kt:133, 144`: `quantity * product.selling_price`
- `ui/retours/NewRetourViewModel.kt:171`: `priceOf = product.selling_price`
- `data/local/dao/ClientDao.kt:83–84`: the balance subtracts `SUM(retour_client.total)`
- `data/repository/ProfitReport.kt:13` and `data/local/dao/ReportDao.kt:120–121`: documented as the sale price, computed
  from the return line

**Impact.** Client balances are wrong on every return where the price moved or was negotiated. Raising a price before
recording a return over-credits, which is also an abuse path for a rep. Margins and goals inherit the error.

**Fix.** Value a returned line at the price of the sale it came from: the client's own sale lines for that product (last
or average delivered price), or let the user pick the sale. Keep that price on the return line, and add a cost snapshot
so the Résultat stops using today's purchase price.

### 4. DATA-01 — A draft can be re-created right after its sale is saved, and resuming it saves the sale twice

**High · Data integrity · Likely**

**What happens.** Drafts autosave 500 ms after the last change. « Confirmer » starts the save, the sale's transaction
deletes its draft, and only after the save returns does `onCommitted()` stop the autosave. Type the amount paid and tap
Confirmer within half a second, and an autosave is still pending. It runs during the save, waits for the write lock, and
lands after the commit: `upsert` finds no draft row and inserts a new one. `onCommitted()` then only forgets the id. The
result is a Brouillon identical to the sale just saved. Resuming and saving it creates a second sale: the stock goes out
twice and the client owes twice. The `flush()` on ON_STOP has the same window. Dépôt vente, Tournée vente and Achats
share this code.

**Evidence.**
- `ui/common/DraftAutosave.kt:94` (debounce), `:134–150` (`persist`, which upserts while armed), `:113` (`disarm()`, only
  called after the commit), `:129` (`flush()`, not cancelled)
- `data/repository/VenteDraftRepository.kt:67–72`: inserts when the given draft row is gone
- `data/ProductRepository.kt:1335`: the draft is deleted inside the sale's transaction
- `ui/ventes/VenteFormSessionViewModel.kt:529–532` and `ui/navigation/VenteFormNavGraph.kt:701–724`: `onCommitted()`
  runs in `onSuccess`

**Impact.** A duplicate sale or bon from a « ghost » draft; at the least, confusing Brouillons. Traced in code, not
reproduced on a phone.

**Fix.** Disarm the autosave, and wait for any write in flight, before calling the repository; re-arm if the save fails.
Or make `upsert` refuse to insert when it is given a draft id that no longer exists.

### 5. FIN-03 — A supplier return marked « refusé (perte) » still lowers what you owe the supplier, and also books the goods as a loss

**High · Money · Verified**

**What happens.** The motifs « Produit défectueux — refusé (perte) » and « Produit périmé — refusé (perte) » take the
goods out of the dépôt and record a perte (Casse or Péremption). The return's total still includes those lines at the
purchase price, and the supplier balance subtracts every return total. If the supplier refused the goods, no credit was
given, yet the debt drops by their value, and the same value is counted as a loss in the Résultat. Either the credit or
the loss is wrong, so the amount is counted twice.

**Evidence.**
- `data/model/RetourMotif.kt:26, 28`: refused motifs are DECREASE with a perte type
- `data/repository/RetourFournisseurRepository.kt:132`: total over all lines; `:169–176` perte with
  `affectsStock = false`; `:185` balance recomputed
- `data/local/dao/SupplierDao.kt:63–64`: `− SUM(retour_fournisseur.total)`

**Impact.** Supplier debt is understated by the value of refused goods, and the net result counts them twice.

**Fix.** Leave refused lines out of the return total, or record them as a perte only, without a supplier return. Add a
test of balance and Résultat for each motif.

### 6. STK-02 — Every client return goes into the truck, even when the goods come back to the warehouse

**High · Stock · Verified**

**What happens.** A client return books its stock movement with `emplacement = "camion"`, whatever the source of the
sale. The return screen offers no choice and passes no tournée, and the returnable quantities include dépôt sales. For a
business selling from the dépôt, every returned item raises `camion_stock`. The dépôt never gets it back, Stock Camion
shows goods in a truck that may not exist, and in strict mode the item cannot be sold from the dépôt until someone
« unloads » it.

**Evidence.**
- `data/repository/RetourClientRepository.kt:157`: `emplacement = "camion"`
- `ui/retours/NewRetourViewModel.kt:177`: every delivered sale is returnable; `:187` no tournée passed

**Impact.** The dépôt/truck split drifts with every return.

**Fix.** Let the return choose dépôt or camion, defaulting to the sale's source, or to the truck while a tournée is open.

### 7. FIN-04 — A product's cost never follows what you pay for it

**High · Reports · Verified**

**What happens.** `products.purchase_price` changes only when someone edits the product, or through an Excel import.
Creating or receiving a bon writes the unit cost to the bon line and to `price_history`, never to the product. That one
manually kept field is the cost of every sale line (`purchase_price_snapshot`), the value of every perte and inventory
écart, the stock value, and the cost of returns in the Résultat.

**Evidence.**
- `data/ProductRepository.kt:922–982` (bon created: price history only), `:984–1034` (bon received: movements only),
  `:1329` (sale cost = `product.purchase_price`)
- `data/repository/PerteRepository.kt:202`, `data/repository/InventoryRepository.kt:140`, `data/local/dao/ReportDao.kt:121`
- No code outside `ProductFormScreen` and the importer sends a purchase price to `updateProduct`

**Impact.** Margin, Résultat, stock value and loss values drift away from reality as purchase prices move, unless every
product is re-edited after every bon.

**Fix.** Update the cost at reception (last cost, or a weighted average), with a setting to keep it manual. Sale
snapshots keep working as they are.

### 8. FIN-05 — Totals are stored unrounded and compared exactly, so settled documents show « Reste 0,00 DA »

**Medium · Money · Verified**

**What happens.** Totals are sums of quantity × price in `Double`, never rounded to the centime, and the paid, partial
and unpaid tests compare them exactly. 3 × 150,15 is 450.45000000000005; « Tout réglé » fills 450.45, so the sale shows
« Reste 0,00 DA » in red instead of the PAYÉ stamp and falls under « Partielle ». The cart's price field accepts any
number of decimals, so 2,675 × 3 leaves a real 0,005 DA. The October half-centime fix covers balances, not these
document-level checks.

**Evidence.**
- `data/ProductRepository.kt:1276`: `total = items.sumOf { quantity * unit_price }`
- `data/local/dao/VenteDao.kt:165–167`: `montant_paye >= total`
- `ui/ventes/VentesScreen.kt:957–971`, `ui/purchases/PurchaseOrderDetailScreen.kt:365`: `reste > 0`

**Fix.** Round prices, line totals and totals with `Amount.normalize` when writing, and compare through `Amount.owing`
and `Amount.settled`. Planned as its own item in `docs/fin05_payment_state_plan.md` (FIN-01 covers only the three
forms' « Montant payé » block).

### 9. BAK-01 — Restoring an older backup on the same phone re-issues document numbers already printed

**Medium · Backup · Verified**

**What happens.** A number is the phone's prefix, rewritten at every open, plus a counter kept in `app_meta`, which
travels inside the backup. Restoring yesterday's backup on the same phone brings back yesterday's counters, so the next
sales get `V-6DED-000106` and onwards again: numbers already on receipts handed to clients. Nothing makes `numero`
unique.

**Evidence.**
- `data/local/database/DocumentNumbers.kt:18, 60`: the counter lives in `app_meta` and travels with the database
- `data/local/database/DeviceTracking.kt:67`: the device prefix is rewritten at open
- `data/backup/RestoreInstaller.kt`, `finish()`: no counter reconciliation; schema v64 has no unique index on `numero`

**Fix.** Keep a per-device high-water mark outside the database (in `no_backup`), and on open set each counter to the
larger of the two.

### 10. DATA-02 — Deleting a tournée that has sales deletes its unsaved drafts anyway

**Medium · Data integrity · Verified**

**What happens.** The view model deletes the tournée's sale drafts first, then calls `deleteTournee`, which refuses a
tournée with sales by returning an error, not by throwing. The user sees the refusal; the rep's drafts are already gone.
When a deletion succeeds, the tournée's client plan (`tournee_clients`) stays behind (see DB-01).

**Evidence.**
- `ui/tournees/TourneeViewModel.kt:387–388`: drafts deleted, then the tournée
- `data/ProductRepository.kt:2060–2071`: refusal on linked sales; secteurs deleted, clients not

**Fix.** Delete drafts and `tournee_clients` inside `deleteTournee`'s transaction, after the sales check.

### 11. STK-03 — Losses can only be recorded at the dépôt

**Medium · Stock · Verified**

**What happens.** New pertes are always written with source `"depot"`. A loss on the truck, including the built-in type
« Perdus pendant le transport », lowers the dépôt instead and leaves the truck with stock it no longer has. In strict
mode, when the dépôt holds none of that product, the loss is refused outright.

**Evidence.**
- `data/repository/PerteRepository.kt:146–152`: `source = "depot"`
- `ui/pertes/NewPerteViewModel.kt:123`: the only creation path

**Fix.** Add a dépôt or camion choice to the perte dialog; the repository already accepts `source = "camion"`.

### 12. STK-04 — Nothing guards truck stock, so `camion_stock` can go negative

**Medium · Stock · Verified**

**What happens.** The strict-stock guard protects the dépôt only. Deleting a client return removes its truck entry with
no check: a return of 5, then a truck unload, then deleting the return leaves the truck at −5 and the dépôt 5 above
reality. Lowering the quantity of an old camion sale after the truck was unloaded puts stock back in the truck. A
negative truck also makes the camion check in `addPerte` fail, so a later defective return can lose its linked perte
(see FIN-06).

**Evidence.**
- `data/repository/RetourClientRepository.kt:190–206`: delete without any guard
- `data/repository/DepotStockGuard.kt:31–44`: dépôt only
- `data/ProductRepository.kt:1362–1364`: a camion sale edit returns stock to the truck

**Fix.** Add the same before-and-after check for the truck (may not end below zero and lower than it started) on the
paths that remove truck movements.

### 13. REP-01 — The Résultat leaves out inventory shortages

**Medium · Reports · Verified**

**What happens.** Net result is gross margin, less the returns' margin, charges and pertes. Shortages found at an
inventory (negative écarts) are real losses but are not counted, so net profit is overstated by every shortage found.

**Evidence.** `data/repository/ProfitReport.kt:37–38`, `data/repository/ReportRepository.kt:195–215`

**Fix.** Add the period's inventory écart value (`valeur_ecart`) as its own line in the Résultat.

### 14. LIC-03 — The live server still trusts the debug signing certificate

**Medium · Licensing · Verified**

**What happens.** `LICENSE_APP_SIGNING_DIGESTS` includes the debug certificate so benchmark builds can activate. Anyone
holding that debug keystore can sign a modified APK that passes the signer check with a genuine attestation, so it gets
the normal tier and the 14-day offline window.

**Evidence.** `docs/license_architecture.md:786–787`; `app/build.gradle.kts`: benchmark signed with the debug key

**Fix.** Remove the debug digest before public release, as the docs already say; give beta builds their own key.

### 15. LIC-01 — The one-phone rule can be sidestepped through the « same phone, reinstalled » path

**Medium · Licensing · Verified**

**What happens.** When the seat is taken, `activate_device` frees it without counting a transfer if `android_id_hint`
and `model` match the active phone. Both are free text sent by the client, and the « seat_taken » reply lists each
active phone's model. A modified client, or a rooted phone that intercepts its own traffic, can send the holder's values.
The freed phone keeps working offline until its window ends, so repeating this lets two phones share one seat without
reaching the 3-transfers-per-30-days limit.

**Evidence.**
- `supabase/migrations/20261010120000_accounts_and_licenses.sql:407–415` (free release), `:419–420` (models disclosed)
- `supabase/functions/_shared/service.ts:99–100`: optional, unverified text

**Fix.** Release for free only when the old device has not checked in for some days, or when the new attestation proves
the same hardware; count it as a transfer otherwise. Stop returning models to a phone that is not yet activated.

### 16. LIC-02 — Unattested or repackaged clients are activated, and release builds are not obfuscated

**Medium · Licensing · Verified**

**What happens.** The server accepts any certificate chain that does not end at a Google root, on the reduced tier
(one-day window). A repackaged APK with a software key is therefore accepted; the « repackaged » refusal only fires on a
genuine Google chain. Release builds use `-dontobfuscate`, so `LicenseWriteGate` and `LicenseManager` keep their names,
and the closed gate is a single value in `app_meta`. The license docs already say an offline patch can't be stopped; the
point here is that nothing raises its cost.

**Evidence.** `supabase/functions/_shared/policy.ts:21–23`; `app/proguard-rules.pro:11`;
`data/local/database/LicenseWriteGate.kt:122–125`

**Fix.** Obfuscate at least `data/license` and the gate. Flag reduced-tier devices that reactivate often. Add Play
Integrity at the Play launch (decision D5). Consider refusing paid activation codes on reduced-tier devices.

### 17. REP-02 — Invoice status ignores later payments

**Low · Reports · Verified**

**What happens.** Payée, Partielle and Impayée compare each sale's amount paid at the sale with its total. Versements are
not allocated to sales, so a client who settles the whole balance with a separate payment still has every sale listed as
« Impayée ». The debt ageing report allocates correctly.

**Evidence.** `data/local/dao/VenteDao.kt:165–167`

**Fix.** Rename the filter « payé à la vente », or derive the status from the ageing allocation.

### 18. DB-01 — No foreign keys, and orphan rows already exist

**Low · Database · Verified**

- A deleted tournée leaves its `tournee_clients` rows. The Corbeille then refuses to delete those clients for good,
  citing « 1 tournée » that no longer exists (`data/trash/TrashRepository.kt:256`).
- A deleted bon leaves its `price_history` rows, and the purchase form's price suggestions keep showing them
  (`data/ProductRepository.kt:1130–1142`, `ui/navigation/PurchaseFormNavGraph.kt:834`).

**Fix.** Delete children explicitly in the parent's transaction. Foreign keys with cascades are possible later, through a
migration that drops and rebuilds the triggers as CLAUDE.md describes.

### 19. FIN-06 — Repository traps that are safe today only because the screens avoid them

**Low · Money · Verified**

- `updateVente` takes a client id but never writes it, then recomputes that client's balance. A different client passed
  in would leave the real owner's balance stale (`data/local/dao/VenteDao.kt:119–124`, `data/ProductRepository.kt:1413–1414`).
  The edit screen skips the client step today.
- `updatePurchaseOrder` has no status check; on a received bon it would rewrite the lines and leave the reception's stock
  movements as they were (`data/ProductRepository.kt:1043–1097`). The detail screen forces « Rouvrir » first.
- A client return ignores `addPerte`'s error result, so a refused linked perte leaves the return saved without it
  (`data/repository/RetourClientRepository.kt:168–176`).
- `guardOrError` catches the refusal after its inner transaction has ended. Inside an outer transaction, Android then
  rolls everything back silently at commit while the caller reports success (`data/repository/DepotStockGuard.kt:50–55`).
  No current path reaches this.

**Fix.** Enforce these rules in the repositories, and inside transactions let refusals propagate as exceptions instead of
returning maps.

### 20. UX-01 — Only sales marked « Livré » can be returned

**Low · Returns · Verified**

**What happens.** Sales start as pending and their stock leaves at creation, but returnable quantities count delivered
sales only. A business that never marks deliveries cannot record any client return.

**Evidence.** `ui/retours/NewRetourViewModel.kt:177`: `getSoldQuantitiesForClient(partyId, "delivered")`

**Fix.** Count every sale, or say on the screen why a product cannot be returned.

### 21. DB-02 — Tables and files that only grow

**Low · Database · Verified**

- `tombstones`: editing a sale deletes and re-inserts its lines and movements, which leaves two tombstones per line per
  edit. Nothing prunes them (`data/local/database/ChangeTracking.kt:113–116`).
- `price_history`: every edit of a pending bon appends all its lines again (`data/ProductRepository.kt:1081–1090`).
- Photos: a file is never deleted once its last reference goes, and a write killed midway leaves its temp file
  (`data/image/ImageStore.kt:33–37, 103`).

**Fix.** Prune tombstones beyond a fixed horizon until sync exists; stop writing price history on bon edits; add a photo
sweep that keeps every file a row or backup still names.

### 22. PERF-01 — Whole client and supplier tables are re-read after every sale

**Low · Performance · Verified**

**What happens.** 29 screens collect `observeClients()` or `observeSuppliers()`. Every sale and payment rewrites
`clients.balance`, so each open subscriber re-reads and re-maps the whole table. Fine at hundreds of clients, noticeable
at thousands.

**Evidence.** `data/ProductRepository.kt:1577–1578`, `ui/clients/ClientViewModel.kt:63`

**Fix.** Search and page the pickers in SQL, as the product pickers already do.

### 23. DB-03 — Indexes missing for current queries

**Low · Database · Verified**

**What happens.** No index on `pertes(product_id)`, `retour_client_items(product_id)`, `retour_fournisseur_items(product_id)`
or `products(deleted_at)`, all used by returnable quantities, Corbeille counts and duplicate checks. The covering indexes
behind the stock triggers are in place.

**Evidence.** `app/schemas/com.distrigo.app.data.local.database.AppDatabase/64.json`

**Fix.** Add them in the next schema migration.

### 24. PERF-02 — Screens keep collecting while the app is in the background

**Low · Performance · Verified**

**What happens.** 287 `collectAsState()` calls and no `collectAsStateWithLifecycle()`. Room flows keep re-querying while
the app is stopped, and background workers do write.

**Fix.** Switch to `collectAsStateWithLifecycle()`, starting with the screens that watch whole tables.

### 25. LIC-04 — Trials trust an unverified ANDROID_ID

**Low · Licensing · Known**

Already in `docs/backlog.md`: a missing or invented `android_id` gets a trial, and a factory reset gives a new one
(`supabase/functions/_shared/service.ts:99`).

### 26. LIC-05 — The clock anchor can be rolled back on a rooted phone

**Low · Licensing · Verified**

**What happens.** The HMAC seal detects an edited `anchor.state`, but not an older, genuine copy put back. The comment says
the state « cannot be rolled back ». This needs root, which defeats the gate anyway.

**Evidence.** `data/license/LicenseStore.kt:78`, `data/license/TrustedClock.kt:52`

**Fix.** Correct the comment. If it matters later, keep a counter that only goes up beside the Keystore key.

## Still open from earlier audits

- 2026-09-18, item 1.3: supplier debt counts bons not yet received. The decision is still not written down.
- Item 3.7: 25 `arguments!!` in the navigation hosts.
- Item 6.1: `ProductRepository` is now 2,150 lines with `Map<String, Any?>` inputs and casts such as
  `map["product_id"] as Int` (`data/ProductRepository.kt:1693`).
- Item 6.2: `REPLACE` on client and supplier inserts.
- Item 4.1: three pre-restore safety copies with photos in private storage, with no size cap.

## What is solid

**Stock and money**
- Stock is a ledger: triggers recompute stock and truck stock from movements and transfers, rounded to the thousandth,
  and repair any direct write. Covering indexes keep the sums fast.
- The strict-stock guard compares before and after inside one transaction, so edits that dip below zero midway still pass.
- Each party's balance is one SQL formula, returns included, recomputed inside the same transaction as the write.

**Licensing**
- ES256 tokens with pinned keys; algorithm and type checked; the signature verified before any claim is read.
- Sequence numbers block replays; tokens are bound to the device key and the installation; any error fails closed.
- Read-only is enforced by database triggers, and migrations lift them so updates always succeed.

**Backup and import**
- Manifest-first archive with a SHA-256 per entry and whitelisted names, so no path traversal.
- Integrity check, row counts and schema fingerprint; migrations without a destructive fallback; a staged swap that
  survives a kill at any step; a safety copy before every restore.
- Cloud copies: AES-256-GCM in authenticated segments, a random data key wrapped by PBKDF2 at 600,000 iterations and by
  a 120-bit recovery code.
- Import: external XML entities disabled, size and row limits, the plan re-checked inside the transaction.

**Stability**
- Photos are decoded at a reduced size, turned upright from EXIF, recycled, and written atomically under their hash.
- No `runBlocking`, no `GlobalScope`, no main-thread queries.
- Crash, ANR and exit-reason reports are collected on the phone.

## Method and limits

- Read: `main` at `b12e317` plus the uncommitted cloud-backup work, the Supabase Edge Functions and the SQL migrations.
  In depth: the stock ledger and its triggers, every repository that moves money or stock, the DAOs behind balances and
  reports, backup, restore and import, the license client and server. Screens only where they feed those writes.
- Not done: no build, no test run, no reproduction on a phone, no queries against the live server. « Likely » marks a
  path traced in code but not reproduced.
- Not covered in depth: printing, translations, maps, onboarding screens, the uncommitted `CloudJobHealth` scheduling,
  the Administration screens.
