# L5 — Enforcement: what an expired license blocks, and how

**Approved by the owner on 2026-10-19**: the two-layer design, all six decisions of §7 as
recommended, and the blocked-action flow with the « Abonnement » screen of §8. Builds on
`docs/license_architecture.md` (§2.5 states, §2.6 van-sales rules, §2.7 the two layers, §8 the L5
walk) and on L4 (`docs/license_l4_checkin.md`). Next: L5a (§6), started in a new conversation.

## 0. In one page

Today the license is read, checked in and shown in Compte, but **nothing is blocked**: an expired,
revoked or suspended phone still sells. L5 makes `LicenseState.canRecord` count, in the same two
layers as « Autoriser le stock négatif » (off):

| Layer | Strict stock (today) | License (L5) |
|---|---|---|
| Shapes the screens | `StockPolicy`: stepper max, disabled « add » | `LicensePolicy`: the banner, the « + » and quick actions that explain instead of opening a form, « Confirmer » refused with the reason |
| The rule | `DepotStockGuard` rolls the transaction back | `LicenseWriteGate`: SQLite triggers refuse the write with `LICENSE_READ_ONLY` |

The screens mean the merchant almost never meets the gate; the gate means a forgotten screen, a
stale draft or a future feature can't write past an expired license.

**Read-only never takes the merchant's data hostage:** everything stays readable, the reports work,
a receipt can be reprinted, Excel/CSV export, `.distrigo` backup and restore, Paramètres and Compte
all work. An open tournée can still be closed and the truck unloaded (D4).

## 1. Which states block

From `LicenseState` (already built in L1–L4); only `canRecord` decides.

| State | Records? | Banner (French) | Banner action |
|---|---|---|---|
| `Active` | yes | none | — |
| `RenewSoon` (≤ 7 days left) | yes | « Votre abonnement expire le 31/12/2026. » | « Compte » |
| `CheckInSoon` (offline window ending) | yes | « Connectez-vous à Internet avant le 24/10 pour continuer à enregistrer. » | — |
| `Grace` (3 days, D3) | yes | « Abonnement expiré le 31/12. Période de grâce jusqu'au 03/01. » (red) | « Compte » |
| `Expired` | **no** | « Abonnement expiré — consultation seule. Sauvegarde et export restent disponibles. » | « Compte » |
| `CheckInRequired` | **no** | « Vérification de l'abonnement nécessaire : connectez-vous à Internet. » | « Réessayer » |
| `ClockBehind` | **no** | own screen, §4 | « Régler la date » |
| `Revoked` | **no** | « Cet appareil n'est plus associé au compte. » | « Compte » |
| `Suspended` | **no** | « L'abonnement est suspendu. Contactez DistriGo. » | « Compte » |
| `OtherBusiness` | **no** | « Ces données appartiennent à une autre entreprise. » | « Compte » |
| `NoLicense` / `NotYetValid` | **no** | the sign-in / activation screen, as today | — |

`Unchecked` (the instant before the first evaluation at start) records nothing; the gate is written
before the database's first write, as `RestoreStartup` already orders it.

## 2. Layer 1 — the screens (`LicensePolicy`)

- **`data/license/LicensePolicy.kt`**: derived from `LicenseManager.state`, a `StateFlow` with
  `canRecord`, the banner (text, color, action) and `canUnloadTruck` / `canCloseTournee` (D4). Read
  like `StockPolicy`; a `LocalLicensePolicy` provided once at the root, so any screen reads it.
- **`DsLicenseBanner`** (ui/designsystem), under the top bar of the four tab roots (Dashboard,
  Ventes, Produits, Achats) and of the « Plus » sections that record (Retours, Pertes, Inventaire,
  Charges, Stock Camion, Tournées). Amber for the warnings, red for read-only.
- **The ways in.** Each control that starts a write stays visible (the merchant must see what is
  missing), and when recording is off a tap opens the short « Consultation seule » sheet of §8
  instead of the form:
  - « + » / FAB: Dépôt Vente, Nouveau bon, new tournée, « Ajouter des produits » (Stock Camion),
    new retour client / fournisseur, new perte, new inventaire, new charge;
  - the Dashboard's quick actions;
  - in a tournée: « Créer une vente », « Visité sans vente », « Ajouter un client »;
  - client and supplier detail: « Encaisser » / « Payer », edit and delete of a document;
  - Produits / Clients / Fournisseurs: new, edit, delete, Excel import (see decision 1);
  - brouillons: opening one shows it, its « Confirmer » is refused (decision 2).
- **A form already open when the state flips** (the 15-minute check, a time change): its
  « Confirmer » shows the same sheet; the draft stays, nothing is lost.
- **Errors from the gate** are turned into the same French text by the repositories, as they already
  do for `DepotStockException`, so even a path missed by layer 1 says why.

## 3. Layer 2 — the rule (`LicenseWriteGate`)

- **`data/local/database/LicenseWriteGate.kt`**, built like `DocumentNumbers` / `ChangeTracking`:
  from a list of tables, `BEFORE INSERT`, `BEFORE UPDATE` and `BEFORE DELETE` triggers created at
  every database open:

  ```sql
  CREATE TRIGGER IF NOT EXISTS `trg_ventes_license_insert` BEFORE INSERT ON `ventes`
  WHEN (SELECT `value` FROM `app_meta` WHERE `key` = 'license.write_gate') = 'closed'
  BEGIN SELECT RAISE(ABORT, 'LICENSE_READ_ONLY'); END
  ```

- **The tables** (every document, its lines, money and stock):
  `ventes`, `vente_items`, `purchase_orders`, `purchase_order_items`, `retour_client`,
  `retour_client_items`, `retour_fournisseur`, `retour_fournisseur_items`, `pertes`,
  `inventory_sessions`, `inventory_items`, `chargement_sessions`, `chargement_items`, `chargements`,
  `charges`, `client_payments`, `supplier_payments`, `tournees`, `tournee_clients`,
  `stock_movements`, `commission_periods`; plus the master data if decision 1 says so.
  **Not gated:** the drafts (`*_drafts`), `app_meta`, `business_settings`, `tombstones`,
  `price_history` (written by the gated paths anyway), so drafts, settings and restore keep working.
- **`license.write_gate`** in `app_meta`: `LicenseManager` writes `open` / `closed` at every open
  (before any write), at every state change, and right after a restore, so the value a backup carries
  never counts.
- **D4 through the gate.** Closing a tournée and an unloading chargement (`vers_depot`) run inside
  `LicenseWriteGate.allowing(reason) { … }`: in the same transaction it sets the gate to `open`, does
  the write, and sets it back to `closed` before commit. Nothing else can slip in, since the
  transaction is the only writer. Loading the truck is not allowed.
- **Restore and import of a backup** replace the data under the gate: they run inside `allowing`
  too (a restore is how an expired merchant gets their records back), then the gate is rewritten
  from the license, never from the backup. Checked on the phone before L5a is done.
- **No schema change, no `DATABASE_VERSION` bump**: triggers and an `app_meta` row only.

## 4. `ClockBehind`: its own screen

When the phone's date is more than 24 hours behind the trusted floor, every new document would carry
a wrong date, whatever the license says. A full-width card replaces the banner:
« La date du téléphone est en retard » · « Activez « Date et heure automatiques », puis revenez. » ·
button « Régler la date » (`Settings.ACTION_DATE_SETTINGS`). Coming back re-evaluates at once (the
`ACTION_TIME_CHANGED` receiver of `LicenseInFront` is already there). 10 minutes to 24 hours behind:
an amber banner only, recording continues (§2.5).

## 5. Testing

- **JVM:** `LicensePolicy` per state (records / banner / D4 flags), the gate's SQL text, the French
  message mapping. The existing unit tests stay green.
- **Phone (androidTest, run by hand like `BusinessSettingsTest`):**
  - **Coverage test:** fails when a table with a `numero`, a ledger write or a money column is not in
    the gate list, so a future document table can't be forgotten.
  - The gate closed: an insert, an update and a delete refused on each table; drafts and
    `business_settings` still written; `allowing` lets a tournée close and an unload through, and
    nothing else.
- **The walk (§8 of the architecture doc), on a test account, never the owner's:**

  | Step | Expected |
  |---|---|
  | Test plan with `ou` = now + 10 min, airplane mode, wait | `CheckInRequired`, « + » explains, writes refused |
  | Back online | recording again by itself |
  | Date set back 2 days | the `ClockBehind` card, « Régler la date » opens the settings |
  | Date set back, then reboot | still blocked |
  | Expired with an open tournée | the tournée closes, the truck unloads, a sale is refused |
  | Second phone signs in | transfer; the first phone read-only at its next check-in |
  | Backup, export, reprint while expired | all work |
  | Restore a backup made while active | stays read-only (the gate is rewritten) |

- **The development phone is never locked:** the walk runs on a test account (a plan with a short
  offline window set in Supabase Studio). The owner's account is not touched; at the end the phone
  is signed back into it and checked « Actif ».

## 6. Order of work

1. **L5a — the gate:** `LicenseWriteGate`, the table list, writing `license.write_gate`, the French
   message, `allowing` for D4; the androidTest coverage and refusal tests.
2. **L5b — the screens:** `LicensePolicy`, `DsLicenseBanner`, the « Consultation seule » sheet, every
   way in from §2; « Confirmer » in the open forms.
3. **L5c — the clock card** and the D4 actions in the tournée and Stock Camion.
4. **L5d — the walk** on the test account; benchmark build left on the phone at the end.

Each part compiled, tested and shown on the phone before the next; commits as asked.

## 7. Decisions (approved by the owner, 2026-10-19: all six as recommended)

| | Question | Decision |
|---|---|---|
| **1** | Read-only: may the merchant still add or edit **clients, products, prices, categories** (and import Excel)? | **No, block them too.** Simpler to explain (« consultation seule » means nothing changes), and a catalogue kept up to date is part of what the subscription pays for. Settings, backup, restore and export stay open |
| **2** | **Drafts** while read-only: keep editing them? | **Yes**: a draft is not a document, it changes no stock and no money. Only « Confirmer » is refused, so nothing typed is lost and it can be confirmed after renewing |
| **3** | D4: what exactly stays allowed? | **Closing the open tournée and unloading the truck (toward the dépôt), always.** No sale, no « visité sans vente », no loading. The truck's stock can always come home |
| **4** | Payments on old debts (« Encaisser ») while read-only? | **No**: a payment is a document with money in it, and it would make read-only a usable app. Shown in the sheet: « Renouvelez pour encaisser » |
| **5** | Can the banner be closed? | **Warnings (`RenewSoon`, `CheckInSoon`) close for the day; read-only banners stay** |
| **6** | Grace (3 days): just the red banner, or also a reminder notification? | **The red banner plus one notification on the first day of grace**, on the backups' channel like L4's reminder |

## 8. Blocked actions: a short sheet, then the « Abonnement » screen (approved 2026-10-19)

The owner's design concept (an « Abonnement » page with an expiry illustration, a code field and
support contacts) is the **full screen**; a blocked action opens a **short sheet** first. Not the
full page on every tap: an expired merchant keeps consulting, printing and backing up, and may tap a
blocked « + » many times a day; the page is too tall for a sheet with the keyboard up; and a code is
not the fix for every state.

### 8.1 The short sheet (« Consultation seule »)

Opened by any blocked « + », quick action, tournée action, « Encaisser », edit or delete, and by
« Confirmer » in a form already open when the state flips. Small icon, title « Consultation seule »,
one line of reason (from the state), one line of what still works (« Vos données restent
consultables ; sauvegarde et export disponibles. »), one navy main button, « Fermer ».

| State | Main button |
|---|---|
| `Expired`, `Grace`, `Suspended` | « Réactiver mon abonnement » → the « Abonnement » screen |
| `CheckInRequired` | « Réessayer » (a check-in now; no screen) |
| `ClockBehind` | « Régler la date » (`Settings.ACTION_DATE_SETTINGS`) |
| `Revoked`, `OtherBusiness` | « Compte » |

No screen is forced at startup: the banner already says it each time the app opens.

### 8.2 The « Abonnement » screen

Reached from the sheet, the banner and Compte. Top bar « Abonnement », subtitle « Accédez à nouveau
à toutes les fonctionnalités ». Built in the app's look (Mist page, white bordered cards, navy
buttons, Plus Jakarta Sans, `BrandComponents`), not the mockup's bright blue.

- **Illustration:** the owner's asset `subscription_expired.webp` (689 × 279, WebP with alpha). The
  original is at `C:\Users\pc\Desktop\photos\subscription_expired.webp`; a copy is kept in
  `docs/design/subscription_expired.webp`. L5b puts it in `res/drawable-nodpi/`.
- **State card** (icon, title, one paragraph), its text true to the case:
  - `Expired`: « Votre abonnement a expiré ». « La période d'essai est terminée » only when the
    license is a trial; a paid plan gets its own sentence.
  - `Grace`: « Expiré le 31/12 — période de grâce jusqu'au 03/01 ».
  - `RenewSoon`: « Votre abonnement expire le 31/12 ».
  - `Suspended`: « L'abonnement est suspendu » and the support card; **no code card**.
- **Code card** (« Code d'activation »): a text field only, **no QR button** (codes are not scanned),
  the real code format as placeholder, and the navy button « Activer mon abonnement ». It uses the
  existing « Saisir un code » activation (`AccountViewModel`, `CompteDialogs`), not a new path.
- **Support card** (« Besoin d'aide ? »), each row tappable:

  | Row | Value | Opens |
  |---|---|---|
  | Téléphone | 0697875799 | the dialer |
  | WhatsApp | 0697875799 | WhatsApp (`https://wa.me/213697875799`) |
  | E-mail | dzaitech.store@gmail.com | the mail app |
  | Horaires de support | Tous les jours, 8h - 22h | — |

- **Footer note:** « Votre accès sera rétabli dès la validation du code (connexion Internet
  nécessaire). »

