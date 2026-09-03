# Dépôt Vente — Brouillons (Phases 1–4)

**Date:** 2026-09-03
**Scope:** the Draft/Brouillon feature for Dépôt Vente, plus the shared machinery extracted out of Achats to support it.
**Status:** Phases 1–3 committed and merged to `main`. Phase 4 built and verified, **not yet committed**.

| Phase | Commit | State |
|---|---|---|
| 1 — shared draft machinery | `43cc4f4` | merged |
| 2 — Vente draft storage | `a65a712` | merged |
| 3 — Vente form session | `f8e8709` | merged |
| 4 — Brouillons surfaced in the UI | *uncommitted* | verified |

---

## 0. Summary

Dépôt Vente now keeps a Brouillon of an unfinished sale, exactly as Achats does for a bon: autosaved while you work, restored silently after a process kill, resumable from a FAB sheet or its own screen, deleted inside the same transaction that commits the sale, and guarded by a conflict check when the underlying vente moved.

The three genuinely repeated pieces — the fingerprint hashing core, the autosave loop, and the Brouillon card/sheet/dialog UI — were extracted first, so Chargement and Tournée-Vente can reuse them rather than becoming a third and fourth copy.

---

## 1. Decisions taken

| # | Decision | Consequence |
|---|---|---|
| 1 | **Delivered ventes stay editable.** Only `DELETED` blocks. | `resolveBaseState` has no `RECEIVED` branch; the list query asks *which ventes still exist* rather than reading a status. Justified because `ProductRepository.updateVente` reverses every old line's stock before applying the new ones, so a delivered vente is still safely editable. |
| 2 | **`ClientsNavHost` "Nouvelle facture" is in scope** — drafts can be created and resumed there, but Brouillons are listed only under Ventes. | No code needed in `ClientsNavHost`: the client page offers no edit entry, so there is no second edit path to gate. Drafts started there are ordinary new-vente drafts and appear in the Ventes Brouillons list. |
| 3 | **Do the generic refactor now** rather than duplicating and consolidating later. | Phase 1. Achats was re-verified end to end afterwards to prove no behaviour changed. |
| 4 | **Separate `vente_drafts` table**, not a shared one with `purchase_drafts`. | A bon carries a supplier and a colis/expiry breakdown per line; a vente carries a client and the name of whoever made the sale. One wide table would be half-null in both directions, and would have meant migrating a table that had shipped hours earlier. |

---

## 2. Phase 1 — the shared machinery (`43cc4f4`)

Net **−322 lines** in Achats-specific code. No behaviour change.

* **`DraftFingerprint`** reduced to the hashing core: `of(canonical: String)` (SHA-256) and `num(v: Double)` (`Locale.ROOT`), plus the rule every domain's canonical builder must follow. The Achats canonical string moved to **`PurchaseFingerprint`** *character for character*, so fingerprints already recorded against a bon stay valid.
* **`DraftAutosave` / `DraftAutosaveHost`** own the `combine → drop(1) → filter { armed } → debounce(500) → persist` loop, the `ON_STOP` flush, and the dirty rule — fingerprint against base for an edit, emptiness for a new record, and delete the row again when an edit is undone back to its original values.
* **`DraftComponents.kt`** holds `DraftCardUi`, `DraftRow`, `DraftsSheet`, `DraftConflictDialog` and a generic `draftResumeAction<D>`, with `DraftSheetCopy` / `DraftConflictCopy` carrying the words.
* **Stale KDoc fixed** on `DraftFingerprint`: it still described the pre-F14 prefill discarding `nb_colis`/`unite_par_colis`/`has_expiry`.

### Achats regression after the refactor — all PASS

| # | Scenario | Result |
|---|---|---|
| R1 | Untouched edit of bon #11 → **no draft** | PASS |
| R2 | Dirty the note → draft with `source_order_id`, base fingerprint recorded | PASS |
| R2b | Undo back to the original → **draft deleted again** | PASS |
| R3 | Back out → `am force-stop` → relaunch → resume | PASS — silent, note and `2 colis × 12 = 24 pièces` restored |
| R4 | Brouillons screen: new-purchase card + delete dialog | PASS |
| R5 | FAB drafts sheet | PASS — renders identically |
| R6 | Commit an edit → draft deleted | PASS |
| R7 | Bon mutated underneath its draft → **"Le bon #11 a changé"** | PASS |
| R7b | "Repartir du bon actuel" → draft deleted, form re-prefilled from the current bon | PASS |

---

## 3. Phase 2 — the storage layer (`a65a712`)

`VenteDraftEntity`, `VenteDraftDao`, `VenteDraft` models, `VenteFingerprint`, `VenteDraftRepository`, migration 33→34 with exported schema. `DraftBlock`/`DraftBaseState` moved out of `PurchaseDraft.kt` into `DraftState.kt` and reworded for any flow.

### Three fingerprint details that are not obvious

1. **`user_name` is hashed, and the committed side is always empty.** "Effectué par" is written onto the **stock movements**, not onto the `ventes` row, so a committed vente has nothing to prefill it back from. Both sides agreeing on `""` is what matters: an untouched edit stays clean, typing a name is a real change, and a resume gives it back.
2. **The cart line's product snapshot is excluded.** `VenteCartItem` carries a `Product` whose stock has this sale's own deduction re-added for display, and that number moves whenever anything else in the app touches stock. Hashing it would make every parked draft read as conflicted the moment an unrelated sale went through. Only `product_id`, `quantity`, `unit_price` are hashed.
3. **`originalReservedQty` is not persisted.** It is derived from the committed vente, which may have moved while the draft sat unsaved, so a resume re-derives it rather than trusting a stale copy.

### Migration verification

* Migration SQL **diffed programmatically** against Room's generated `34.json` — table and index match byte for byte. Identity hash `4001e9baa761802df6de9e9945fc3356` recorded in the KDoc.
* Live database backed up before installing.
* Run against the real database: `user_version` 33 → 34, both objects created, **no Room validation error and no crash** — a clean open is itself Room confirming the result matches what it expects.
* **Data intact:** orders 13, ventes 21, products 34, clients 18 — identical to the pre-migration inventory.
* **Unique index checked, not assumed:** two new-vente drafts with a null `source_vente_id` coexist; a second edit draft for the same vente fails with `UNIQUE constraint failed`.

---

## 4. Phase 3 — the form session (`f8e8709`)

`VenteFormSessionViewModel`, scoped to the form graph's back stack entry, owns the five form fields and the session lifecycle. Three pieces moved out of the graph:

1. **The duplicated entry logic.** `resetVenteForm()` behind a `rememberSaveable("initialized")` flag plus two client-resolution effects existed **twice** — once on the client step, once again on the products step because `skipClientStep` means the client step is never entered. That flag survived process death while the `VenteViewModel` it described did not, so after a kill it claimed setup was done against a form that had been reset.
2. **The edit prefill**, which ran off whichever destination composed first — not guaranteed by a process-death restore — and which must stay in step with the fingerprint's mirror of it.
3. **Re-deriving `originalReservedQty`** from the committed vente. The session publishes `editSource` so the products and cart steps re-apply it the same way when they re-sync.

`createVente`/`updateVente` take an optional `draftId` and delete it as the **final statement inside their transaction**, as the Achats commit path already does — so a failure anywhere in the insert, the stock deltas or the balance recalculation rolls back and leaves the unsaved work intact.

### Results — all PASS

| # | Scenario | Result |
|---|---|---|
| D1 | Empty new form → no draft; pick a client → draft created (`source_vente_id` NULL) | PASS |
| D2 | Add a product → 1 item, 80.00, line JSON in `VenteDraftLine` shape | PASS |
| D3 | Type a note → carried into the draft | PASS |
| D4 | **Real process death** (`am kill`, pid gone) → relaunch | PASS — same step (Produits · 2/3), client in the header, product selected, "Ma sélection 1 — 80.00 DA", silent |
| D6 | Confirm the sale → vente created, stock 8→7, **draft deleted in the same transaction** | PASS |
| D7 | Reopen it as an untouched edit → **no draft** | PASS — fingerprint symmetry holds, including the empty `user_name` |
| D8 | Dirty the note → draft with `source_vente_id` and a base fingerprint | PASS |
| D8b | Undo back to the original → draft deleted again | PASS |
| D9 | Back out of the graph → draft persists; the vente on disk unchanged | PASS |

---

## 5. Phase 4 — Brouillons in the UI (uncommitted)

`VenteBrouillonComponents.kt` (card title/meta, blocked label, sheet and conflict copy, `venteDraftResumeAction`) and `VenteBrouillonsScreen.kt`, both thin over the Phase-1 generics. `VenteViewModel` gained the Room-observed `drafts`/`draftCount`, `draftForVente`, `resolveDraftBase`, `deleteDraft`. `VentesScreen` gained the chip and the FAB sheet. `VentesNavHost` gained `openVenteFormAction`, the `editVenteAction` gate and the `VentesBrouillons` destination.

### Results — all PASS

| # | Scenario | Result |
|---|---|---|
| P1 | Pick a client → draft created; empty form creates none | PASS |
| P2 | "Brouillons (1)" chip appears beside "7 ventes" | PASS |
| P3 | Chip → Brouillons screen, new-vente card (client as title, "0 produit · à l'instant") | PASS |
| P4 | Tap the card → form opens with the client restored, **no second draft** | PASS |
| P5 | FAB → sheet with Vente wording ("…commencez une nouvelle vente") | PASS |
| P6a | Edit vente #17, bump a quantity → edit draft (`source_vente_id = 17`, 900.00) | PASS |
| P6b | "Modifier la vente" again → **resumes** that draft, no second row | PASS — the gate works |
| P7 | Vente #17 mutated underneath → **"La vente #17 a changé"**, both options | PASS |
| P8a | Blocked draft in the list → danger style, **OBSOLÈTE** badge, "Vente supprimée" | PASS |
| P8b | Resume it → **"Vente introuvable"** dialog | PASS |
| P9 | Both drafts deleted through the UI | PASS |

---

## 6. Disclosures

* **P8 used a simulated divergence.** For the `DELETED` case a draft was repointed at a nonexistent vente (`source_vente_id = 9999`) rather than deleting a real sale. It exercises the same `resolveBaseState` branch and the same badge query, but it is not an end-to-end delete.
* **R7 also used a simulated divergence** (bon mutated directly in SQLite), because the app offers no UI route to change a bon that already has a pending edit draft — which is the situation the dialog exists for.
* **A correction to an earlier claim.** `last_step` recording the picker route was reported as a Phase 4 bug. It is not: `last_step` is written but **never read for navigation** in either flow — a resume always opens at the graph's start destination. The comments that promised otherwise (and that called it "the deepest step reached", when backing out overwrites it with a shallower one) were corrected instead. Resume-to-step navigation was **not** built.

---

## 7. Open items

1. **Bidi rendering in the card meta line.** With an Arabic client or supplier name the meta renders as `4 · جلاال produits · il y a…` instead of `جلاال · 4 produits · il y a…` — joining RTL and LTR segments with `·` lets the bidi algorithm reorder them. Cosmetic, pre-existing in the same shape on the Achats side, and fixable by wrapping the name in bidi isolates (`U+2068` / `U+2069`) in both `cardMeta` functions. **Not fixed.**
2. **Phase 4 is not committed.**
3. **Phase 5** — the full device verification pass — not started.
4. **Not started, deferred:** the Chargement and Tournée-Vente Draft flows, Notifications, WorkManager.

---

## 8. Device state after testing

Everything restored to the pre-test baseline:

| | |
|---|---|
| `vente_drafts` | 0 |
| `purchase_drafts` | 0 |
| ventes | 21 |
| purchase orders | 13 |
| product 31 stock | 8.0 |
| vente #17 | total 750, `montant_paye` 0.0, note empty, status `pending` — fully restored |

Test records created during the run (vente #22, and the Achats test bon's mutations) were removed or reverted through the app's own paths where possible. No crash lines in logcat; screen timeout restored.
