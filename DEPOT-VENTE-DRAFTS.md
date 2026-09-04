# Dépôt Vente — Brouillons (Phases 1–4)

**Date:** 2026-09-03
**Scope:** the Draft/Brouillon feature for Dépôt Vente, plus the shared machinery extracted out of Achats to support it.
**Status:** Phases 1–4 committed and merged to `main`. Phase 5 (verification) found two defects; both are now fixed and verified, uncommitted at the time of writing.

| Phase | Commit | State |
|---|---|---|
| 1 — shared draft machinery | `43cc4f4` | merged |
| 2 — Vente draft storage | `a65a712` | merged |
| 3 — Vente form session | `f8e8709` | merged |
| 4 — Brouillons surfaced in the UI | `96788f6` | merged |
| 5 — full device verification | — | run; two defects found |
| 6 — both defects fixed | *uncommitted* | verified |

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

## 5. Phase 4 — Brouillons in the UI (`96788f6`)

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

## 5b. Phase 5 — full device verification

Run against `main` after a clean rebuild. Covers what Phases 3–4 had not exercised end to end.

| # | Scenario | Result |
|---|---|---|
| A1 | Achats list after the Phase 3–4 changes (shared `SessionPhase`, `ProductRepository` signatures) | PASS — 13 bons, chip, all data intact |
| A2 | Achats untouched edit → no new draft | PASS — session still correct |
| B1 | Client page → "Nouvelle facture" → opens at Produits with the client preselected | PASS |
| B1b | Add a product on that entry → draft written (`last_step = clients_vente_form_products`) | PASS |
| B2 | Type, then Home immediately → **ON_STOP flush** writes the draft | PASS |
| B4 | Resume a draft → confirm the sale → vente created, **draft deleted in the same transaction** | PASS |
| B6 | CHANGED → **"Repartir de la vente actuelle"** → draft deleted, form re-prefilled from the *current* vente (50.00, empty note) | PASS |
| B5 | **End-to-end DELETED**: edit → back out → delete the vente → open Brouillons | **FAIL — see Defect 1** |
| B5b | Resume that draft anyway → "Vente introuvable" dialog | PASS — the gate is not stale |

### Defect 1 — the block badge did not refresh when the source record changed

Deleting a vente left its edit draft rendering as a **normal** edit card: no OBSOLÈTE badge, no "Vente supprimée". Leaving and re-entering the screen did not fix it (the ViewModel is graph-scoped and `stateIn(WhileSubscribed(5_000))` keeps the upstream alive).

**Cause.** `observeDrafts()` observed `dao.observeAll()`, a Room query on `vente_drafts` only. `existingVenteIds(...)` was called inside `.map { }`, so it was not part of the observed query — Room's invalidation tracker never saw a change to `ventes` and the flow never re-emitted. `PurchaseDraftRepository.observeDrafts()` had the identical structure, so Achats carried the same latent defect.

**Severity: cosmetic, not a data risk.** `resolveBaseState` is a fresh suspend query at resume time, so the gate still blocked correctly — verified: the stale-badged draft still raised "Vente introuvable" on resume (B5b). Nothing could be overwritten; the list just told the truth late.

**Note on how it was missed:** Phase 4's P8a "passed" because it simulated the deletion by patching the database and **restarting the app** — a fresh collection recomputes the badge. Only the end-to-end path, with the app running, exposes it. This is exactly the gap a simulated divergence leaves.

**Fix.** Both DAOs gained an *observed*, unfiltered query — `observeVenteIds()` / `observeOrderStatuses()` — and each repository now `combine`s it with `observeAll()` instead of doing a suspend lookup inside the mapping. Unfiltered rather than `WHERE id IN (:ids)` is the point: Room re-runs a Flow only when a table that query itself reads is written.

**Verified live**, with the app running and no restart: with a draft on bon #11, marking the bon received flipped its card to OBSOLÈTE / "Bon déjà réceptionné"; reopening the bon flipped it straight back to a normal edit card.

### Defect 2 — a Brouillon was created before anything about the bon had been entered

**The reported scenario:** ACHATS → Nouveau bon → select a supplier → press Back. No product, no quantity, no note, no amount. A Brouillon was created anyway.

**Reproduced:** `purchase_drafts` went 0 → 1 (`item_count 0`, `total 0.0`, empty note, empty `montant_paye`) **the instant the supplier was selected** — before Back was involved at all.

**Cause.** `DraftSnapshot.isEmpty` was `supplierId == null && lines.isEmpty()`, so a chosen supplier alone made the snapshot non-empty. Selecting one writes `_formSupplier`, the autosave `combine` emits, the session is already armed, and `persist` reads `dirty = !isEmpty` → INSERT.

**Fix.** The supplier is no longer counted. Choosing one is the *precondition* for entering anything about the bon — the form refuses to go further without it — not content in itself:

```kotlin
val isEmpty get() = lines.isEmpty() && note.isBlank() && montantPaye.isBlank()
```

The same rule was then applied to `VenteDraftSnapshot`, with one deliberate difference: **`userName` does count there.** "Effectué par" is written onto the stock movements rather than onto the `ventes` row, so a draft is the only place it can survive — unlike the client, which the form re-derives on every entry.

**A second, independent contributor**, found while investigating: the `ON_STOP` flush would write a form nobody had touched since arming — that is how arriving from a *supplier page* and backing straight out left an empty draft. `DraftAutosave` now tracks `touched`, set on the first armed emission and checked by `flush()`. Being armed is not the same as having something to save: a session can be armed over a form populated *before* arming — an edit prefill, a hydrated draft, or a record chosen on the page the form was opened from — and none of that is the user entering anything.

**Verified on both flows** — supplier/client selected for real (screenshots confirm the green check and an enabled "Suivant"):

| Check | Achats | Dépôt Vente |
|---|---|---|
| Select supplier / client only | 0 drafts | 0 drafts |
| Press Back | 0 drafts | 0 drafts |
| Add a product | 1 draft | 1 draft |
| Remove it again | 0 drafts | 0 drafts |

### Correction to this section's earlier text

An earlier version of this document reported Defect 2 as "the preselected client/supplier is inconsistent between the two flows", and attributed it to `preselectClient` running before `arm()` in Vente and the graph applying `supplierIdArg` after arming in Achats.

**That diagnosis was wrong, and the evidence behind it was unsound.** The comparison sampled two different moments — Vente was checked *while still in the form*, Achats *after backing out* — so it was never a like-for-like test. Moving the Achats preselect before `arm()` did not fix the reported behaviour, which is what exposed the error. The real causes are the two above: an emptiness rule that counted the supplier, and a flush that wrote untouched forms.

The entry path that actually mattered — ACHATS → Nouveau bon → select a supplier — was never exercised in Phase 5 at all. The path that was tested, "Nouvel achat" from a supplier page, reaches the form a different way and hid the emptiness rule behind the flush.

---

## 6. Disclosures

* **P8 used a simulated divergence.** For the `DELETED` case a draft was repointed at a nonexistent vente (`source_vente_id = 9999`) rather than deleting a real sale. It exercises the same `resolveBaseState` branch and the same badge query, but it is not an end-to-end delete.
* **R7 also used a simulated divergence** (bon mutated directly in SQLite), because the app offers no UI route to change a bon that already has a pending edit draft — which is the situation the dialog exists for.
* **A correction to an earlier claim.** `last_step` recording the picker route was reported as a Phase 4 bug. It is not: `last_step` is written but **never read for navigation** in either flow — a resume always opens at the graph's start destination. The comments that promised otherwise (and that called it "the deepest step reached", when backing out overwrites it with a shallower one) were corrected instead. Resume-to-step navigation was **not** built.

---

## 7. Open items

1. **Bidi rendering in the card meta line.** With an Arabic client or supplier name the meta renders as `4 · جلاال produits · il y a…` instead of `جلاال · 4 produits · il y a…` — joining RTL and LTR segments with `·` lets the bidi algorithm reorder them. Cosmetic, pre-existing in the same shape on the Achats side, and fixable by wrapping the name in bidi isolates (`U+2068` / `U+2069`) in both `cardMeta` functions. **Not fixed.**
2. **Title truncation when a blocked draft also has a price.** With the OBSOLÈTE badge, the total and the delete icon all competing for width, the title ellipsised to "Modification ·…" — hiding the bon number that the two-line title fix exists to protect. Only occurs for a blocked draft with a non-zero total. **Not fixed.**
3. **Sheet with more than 4 drafts** — the "Voir tout (N)" overflow link was never exercised; at most 2 drafts existed at once.
4. **`missingProductIds` blocking** — a draft referencing a since-deleted product was never tested.
5. **Not started, deferred:** the Chargement and Tournée-Vente Draft flows, Notifications, WorkManager.

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
