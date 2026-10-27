# DistriGo: plan for French + Arabic and right-to-left layout

**Date:** 2026-10-27 · **Status:** plan only, awaiting the owner's decisions (D1–D10). Nothing has been implemented.
**Based on:** a read-only inspection of commit `1c59385` (562 Kotlin files, ~100k lines).

> **For agents picking this up:** D1–D10 are decided (2026-10-27). Phase 1 is done; Phase 2 (RTL structure) is next,
> after the owner's go. Follow `CLAUDE.md` throughout. In particular, the Ventes, Achats, Tournée ventes and
> Chargements form graphs are stable: only their text and icons change, never their flow. Paths below start at
> `app/src/main/java/com/distrigo/app/` unless they begin with `app/`.

## Summary

**Where things stand (checked in the code)**
- **Almost no text is in Android string resources yet.** `app/src/main/res/values/strings.xml` holds 9 strings, and
  the app has 8 `stringResource` calls (the bottom bar and the client picker).
  - Estimated text to translate: about **2,900 unique French strings (~13,700 words)**. About 545 of them have
    values inserted into them (names, amounts, counts).
  - By layer: screens ~2,150, data/core ~590, export/import ~200, receipts/printing ~120.
  - These counts come from a scanner, so treat them as ±15%.
- **The layouts are already in good shape for Arabic's right-to-left (RTL) direction.**
  - In all 562 files there is no fixed left/right alignment, arrangement, padding or text alignment. Layouts use
    start/end, which flip automatically.
  - The manifest already has `supportsRtl="true"`.
  - The back arrows and the onboarding arrows are already the kind that flip automatically.
- **RTL bugs are concentrated** in about 8 places that compute their own positions, plus about 45 arrow-type icons
  that won't flip.
- **Most of the work is in how text moves through the code, not in layout:**
  - Repositories throw French error messages that screens display through `e.message`.
  - 21 enums carry French labels.
  - About 100 plurals are written by hand ("produit"/"produits").
  - 39 date/number formatters are hard-wired to `Locale.FRENCH`.
  - Some French text is stored in the database or used as an identifier in code.

**The most serious findings**
1. **Plus drawer** (`MainActivity.kt:648`, `:650`, `:951`, `:752`): it is placed with an alignment that flips in RTL,
   but moved with a `translationX` that doesn't.
   - In Arabic, about 20% of the closed drawer would stay visible at the left edge.
   - The edge swipe and the system-gesture exclusion zone would stay on the left.
   - **Seen on the owner's phone on 2026-10-27:** with DistriGo's language set to Arabic, the screens are already right
     to left today (with French text, since `supportsRtl="true"` and the app follows the phone's language), and the
     closed drawer covers the left fifth of every tab screen. A user whose phone is in Arabic likely sees this now;
     D1 (pin French) or this fix would end it.
   - **Fixed 2026-10-27** (`MainActivity.kt`). The sheet slides in from the start edge, the right one in Arabic.
     Touches and drags are measured from that edge, and the gesture-exclusion strip follows it. Checked on the phone
     in both directions: closed, nothing covers the screen; the menu, the edge swipe, the dim-area tap, Back and the
     menu's items all work; the opposite edge's swipe does nothing.
2. **Screen transitions** (`ui/navigation/NavTransitions.kt:14-23`) use fixed `SlideDirection.Left/Right`. Moving
   forward in Arabic would slide the wrong way, in every section.
3. **Tab underline** (`ui/common/ElasticUnderlineTabRow.kt:63`, `:96`) and the product form's tab pill
   (`ui/products/ProductFormScreen.kt:664`, `:683`): the position is measured from the left (`positionInParent().x`)
   but applied from the start (`offset {}` from `BottomStart`, both of which flip). In Arabic the underline would sit
   under the opposite tab (client and supplier detail pages, product form).
4. **« Nouvelle vente » speed dial** (`ui/dashboard/NewSaleSpeedDial.kt:141`, `:160`): it copies the tile's screen
   position (`boundsInWindow`, `ui/dashboard/DashboardScreen.kt:657`) into a position that flips, and it fans its two
   actions out to the tile's *right*. In Arabic that tile sits at the right edge, so the actions would go off screen.
5. **Ventes report chart** (`ui/rapports/VentesReportScreen.kt:308` vs `:348`): the bars are always drawn left to
   right on a Canvas, but the date labels underneath are in a `Row` that flips. The first date would end up under
   the last bar.
6. **An expiry date is saved through the phone's locale** (`ui/products/ProductFormScreen.kt:373`, read back at
   `ui/products/ProductListFilters.kt:75`, which uses `SimpleDateFormat(..., Locale.getDefault())`). Under some
   Arabic locales this writes Arabic-Indic digits (٢٠٢٦) into the database. This may already happen on phones whose
   system language is Arabic; it has not been reproduced. **Fixed 2026-10-27** (see "Digits are always 0–9").
7. **Arabic-Indic digits in amount and quantity fields** (`data/model/Amount.kt:40`, `data/model/Quantity.kt:63`):
   `isDigit()` lets "٥٠٠" into the field, but `toDoubleOrNull()` reads only 0–9. The field looks filled but parses
   to nothing. Confirmed by a unit test before the fix: `١٢٥٠٫٥` (1250.5) was kept as `١٢٥٠٥`, the decimal mark
   silently dropped. **Fixed 2026-10-27.**
8. **French text used as an identifier**, which translation must not touch:
   - Retour motifs are saved as the French sentence itself (`data/model/RetourMotif.kt:6`).
   - Code branches on `unitLabel == "pièce"` (`ui/components/ReceiptData.kt:96`) and `statut == "Partiel"`
     (`ui/clients/FactureRow.kt:85`, and in about 8 files).
   - The QR code embeds the French day name (`ui/components/ReceiptData.kt:74`).
   - Export headers double as the import template (`data/importer/ImportPlan.kt:14`).

---

## Decisions (D1 and D3–D10 approved by the owner as recommended, 2026-10-27)

Phase 0 done: the glossary (`docs/i18n_glossary.md`) was approved on 2026-10-27.

**Phase 1 done 2026-10-27 (not committed):**
- `core/locale/AppLanguage.kt`, French pinned at first launch (D1). Android 13+ uses the system's per-app language;
  older versions use a preference applied in `MainActivity.attachBaseContext`.
- `res/xml/locales_config.xml`; `values/strings*.xml` (`tools:locale="fr"`) with their `values-ar` twins.
- `localeFilters` (fr, ar), debug pseudo-locales, and `MissingTranslation` as a warning.
- `core/text/UiText.kt` with `UserFacingException`, `userText()` and `UiText.asString()`.
- `core/format/AppDates.kt` and `ltrIsolate()`.
- The ratchet `HardcodedTextGuardTest` (baseline 1371).
- A debug-only « Langue » row in Paramètres.
- Moved to later phases: the screens' date formatters and the notifications' `AppLanguage.localized()` move with
  their text (Phases 3–4); the welcome screen's language choice waits for the Arabic onboarding text (Phase 5).
- Lint reports 19 errors that predate this work (navigation back-stack calls, notification permissions…); none
  is from i18n.

| # | Question | Recommendation |
|---|---|---|
| D1 | On a phone whose system language is Arabic, should the app switch to Arabic by itself? | **No: keep French until the user picks Arabic.** Otherwise any user with an Arabic phone gets a different language on update. |
| D2 | Which Arabic locale? | **Digits: decided by the owner on 2026-10-27 and implemented: always 0–9, whatever the app's language or the phone's** (see "Digits are always 0–9" below), so the locale tag no longer decides the digits. Still recommended: `ar-DZ` for its Algerian month names (جانفي, فيفري…). |
| D3 | Do receipts, PDFs, WhatsApp text, the QR code and exports follow the screen language? | **No: they stay French in this project.** Arabic documents become a later, separate project (Arabic amount-in-words, a mirrored receipt layout). |
| D4 | Ventes report bar chart in Arabic | **Flip the bars and their date labels together**, so time reads right to left. Material's direction guidance flips things that show forward progression (timelines, progress) but not clocks or round progress, so the ring charts stay clockwise. The alternative is to lock the chart and labels left-to-right. |
| D5 | Currency label in Arabic | **Keep "DA".** It belongs to the « Format des montants » setting and appears on receipts. Changing it to د.ج also changes how the text runs in mixed lines. |
| D6 | Excel/CSV column headers and sheet names | **Keep them French.** They are a fixed format that the import reads back. Translating them would mean the import must accept both languages. |
| D7 | French text already stored in the database | **Show built-in perte and charge types, and retour motifs, in Arabic by matching their stable key** (only when the user hasn't renamed them). **Leave free text in the stock history as it is** ("Inventaire session #12", "Non inventorié, mis à zéro", "Retour client V-…"). |
| D8 | Wilaya and commune names | **Show the Arabic name in Arabic.** `app/src/main/assets/wilayas.json` already has `nameAr` for all 69 wilayas and their communes, and it's display-only. |
| D9 | Who translates? | Claude drafts the Arabic from a **domain glossary the owner approves first** (Tournée, Dépôt, Bon, Charges, Pertes, Chargement…). The owner reviews it in the app. |
| D10 | Administration (owner-only screens) | It can **stay French**, which saves about 180 strings. |

---

## Digits are always 0–9 (decided and implemented 2026-10-27)

The owner's rule: the Arabic screens are right-to-left, but every number, amount, date, time and percentage is
written with Western digits (0–9), whatever the app's language or the phone's locale. Arabic-Indic digits typed on a
keyboard are converted to 0–9 and shown as 0–9. Calculations and stored data don't change. What is in place, and
what every new piece of code must keep:

- **One converter:** `core/format/WesternDigits.kt`.
  - `westernDigits(text)` turns any script's digits into 0–9.
  - `westernNumber(text)` does the same, and also reads `٫` as the decimal mark and drops the `٬` thousands mark.
  - `WesternDigits.latin(locale)` adds `-u-nu-latn` to a locale. That changes only its digits: not its language,
    region, text direction or resources.
- **Formatting under any phone locale:**
  - `DistriGoApplication` (at launch and after a configuration change) and `MainActivity.attachBaseContext` call
    `WesternDigits.applyToDefaultLocale()`.
  - `MainActivity` also applies an override configuration whose locales carry `nu-latn`. Material 3's date picker
    formats its days with the default locale and its header with the configuration's, so both are needed.
  - `MoneyFormatter`, `Quantity` and java.time already write 0–9.
  - **Rule:** never format a number or a date with the phone's locale. `MoneyFormattingGuardTest.noNumberOrDateIsWrittenInThePhonesLocale`
    fails on `String.format("…")`, `"%d".format(x)`, `SimpleDateFormat` or `NumberFormat` without a given locale.
- **Input:**
  - `Amount` and `Quantity` (`sanitizeInput`, `parse`) and the Excel import's `ImportNumber` read Arabic digits.
  - The field wrappers convert by keyboard type through `ui/common/NumberFieldInput.kt` (`numberFieldInput`):
    `FormField`, both `DsFormField`s and `BrandTextField`.
  - About 20 other numeric, phone, barcode and code fields convert in their own `onValueChange`.
  - **Rule:** a new number, phone, code or barcode field must pass its text through `westernNumber`,
    `westernDigits` or `numberFieldInput`.
  - Free text (names, notes, addresses) keeps the digits it was written with.
- **Direction:** the theme's text styles (`ui/theme/Type.kt`) use `TextDirection.ContentOrLtr`. Text with no letters
  of its own (`75 %`, `05 55 12 34 56`, `-3`) reads left to right on a right-to-left screen. A line's first letter
  still decides the rest. On a left-to-right screen nothing changes. Hand-built `TextStyle`s for numbers do the same
  (`BrandTextField`, the activation code, the goal's amount field, the ring chart's percentages).
- **Stored data:** unchanged. The only write path that could store Arabic-Indic digits (a product's expiry date) now
  writes with `Locale.ROOT`. The expiry reader also accepts a date written with Arabic-Indic digits in the past.
  Phone numbers are shown in 0–9 even if they were stored otherwise.
- **Tests:**
  - Unit: `core/format/WesternDigitsTest`, plus Arabic cases in `AmountTest`, `QuantityTest`, `ImportNumberTest`,
    and the guard rule.
  - On a phone: `androidTest/…/core/format/WesternDigitsDeviceTest`.
  - Amounts keep « Format des montants » (`12 500,00 DA` by default).
- **Percentages (owner, 2026-10-27):** `75%` in Arabic, `75 %` in French. Every percentage goes through
  `core/format/Percent.kt` (`percentText`), which reads the app's language from the default locale.

---

## 1. Moving the text into resources

### What has to be moved (by category)

| Category | Where it is | How much (approx.) |
|---|---|---|
| Composable text | `Text("…")` ×846, `title =` ×204, `label =` ×135, `contentDescription =` ×132, placeholders ×28 | ~2,150 unique strings in `ui/` |
| Shared words | "Annuler" ×103, "Supprimer" ×53, "Modifier" ×33, "Filtres" ×29, "Fermer" ×20, "OK" ×19, "Erreur inconnue" ×18, "Enregistrer" ×16, "Voir tout" ×16, "Suivant →" ×9… | about 70 strings repeated 6 times or more |
| Error messages | 50 `throw …("French")` in repositories, 39 `Refused("…")`, `userMessage(e)` (`ui/common/ErrorMessage.kt:23`), `caughtAsError` returning `"error"` → String | data layer |
| Data-layer messages | `BackupMessages`, `LicensePolicy` (36), `AuthApi.AuthError` (13), `LicenseClient`, `DepotStockGuard`, `ImportPlanner` (127), `TrashRepository` (65), `ListsRepository` (47), `ChargeRepository`, `PerteRepository` | ~590 |
| Enum labels | 21 enums carry a `label: String`: `ReportPeriod`, `ReportSource`, `DebtSide`, `AgeBand` ("0 – 30 jours"), `PartyRanking`, `ProductRanking`, `ProductGrouping`, `ActivityFilter`, `AchatFilter`, `FactureFilter`, `PriceMovementKind`, `TrashKind`, `ListKind`, `ImportSheet`, `ExportDataset`, `ExportFormat`, `PaperSize`, `ConnectionMethod`, `PrintLanguage`, `MovementType/Party`, `SortOption`, `DraftType/StateFilter`, `BalanceFilter`, `DebtorSort`, `PeriodPreset`… | keep the storage keys, swap the label for a resource id |
| Generated text | `ui/goal/GoalWording.kt` (67: "4e trimestre", "ce mois-ci", "de vos ventes facturées"), `ui/common/RelativeTimeFr.kt`, `core/format/Durations.kt`, `ProductUnit.label/plural`, ~100 hand-made plurals, "Page x sur y" | needs full-sentence strings plus Arabic plural forms (Arabic has six) |
| Dates | 39 formatters with `Locale.FRENCH`: month and day names (`ui/dashboard/DashboardScreen.kt:131`, `SalesBuckets`, `PriceHistoryScreen`, `GoalSteps`…) | central formatter per language |
| Notifications and Toasts | the "Sauvegardes" channel (`data/backup/auto/AutoBackupAlerts.kt:81`), `CheckInAlerts`, `BackupJobNotifications`, `BackupNowWorker` progress text, 33 Toasts, 10 snackbars | resolved from a context that isn't an activity (see §4) |
| Documents (D3) | `ThermalLayout`, `ReceiptPdfGenerator` (22), `NumberToWordsFr`, `ReceiptData`, `ShareOptionsSheet` text, `CodeExports`, `CodeCardsPdf`, `ExportDatasets` (111), `XlsxWriter` | only if D3 changes |
| Excluded | `data/debug/StressDataGenerator` (debug data), `diagnostics/*` crash reports (for the developer), route strings in `ui/navigation/Screen.kt` (identifiers, not text) | — |

### Naming the string keys

All keys are lowercase snake_case. The prefix is the **`ui` package name**, so a key tells you which folder uses it.
The 9 existing keys (`nav_*`, `client_picker_*`) keep their names.

| Kind | Pattern | Example |
|---|---|---|
| Shared words | `common_<kind>_<name>` | `common_action_cancel`, `common_status_paid`, `common_cd_back` |
| Screen text | `<package>_<screen>_<element>` | `ventes_list_title`, `products_form_expiry_label` |
| Dialog | `<package>_<name>_dialog_{title,body,confirm}` | `ventes_delete_dialog_body` |
| Field | `…_label`, `…_hint`, `…_error_<rule>` | `clients_form_phone_error_invalid` |
| Empty state | `…_empty_{title,body}` | `pertes_history_empty_title` |
| Plurals | `<plurals>` with the same naming | `common_products_count` |
| Data-layer messages | `error_<area>_<reason>` | `error_stock_insufficient` |
| Enum value | `<enum>_<value>` | `report_period_month`, `unit_carton` |
| Notification | `notif_<channel>_<event>_{title,text}` | `notif_backup_failed_title` |

Rules for writing them:
- Use whole sentences with numbered placeholders (`%1$s`). Never assemble a sentence from pieces.
- Use `<plurals>` for every count.
- Escape `\'` and `%%`, and keep French non-breaking spaces.
- Don't put arrows inside text (`→` becomes an icon).
- Mark brand words (DistriGo, DISTRIGO) as `translatable="false"`.

### How the files are organized, and how to add more languages later
- Use one file per package: `res/values/strings_<package>.xml` (French, the default, marked `tools:locale="fr"`), and
  `res/values-ar/strings_<package>.xml` with the same file names.
- To add another language: create `values-xx/` with the same files, add one entry to the `AppLanguage` enum, and add
  one line to `locales_config.xml` (or let AGP generate that file). Lint's `MissingTranslation` and `MissingQuantity`
  checks then list every missing key, so no code search is needed.
- `androidResources.localeFilters += listOf("fr", "ar")` keeps library translations to the languages the app supports.

### How text moves through the code (shared tools to build first)
- **`UiText`**: a small type that is either raw text, a string resource with arguments, or a plural resource.
  ViewModels hold it in their state and screens turn it into text (`asString()`).
  - Because ViewModels survive a language switch, any text they had already built as French would otherwise stay
    French.
- **`UserFacingException(UiText)`**: the 50 repository `throw`s use it. `userMessage(e)` resolves it, so every caller
  keeps working unchanged.
  - The data layer still gets no `Context`; today it has no `@ApplicationContext` anywhere.
- **`LocalAppLocale`**: provides the screen language plus date formatters cached per language. Machine-readable
  formats (QR, stored dates, file names, exports) stay on `Locale.ROOT` or French on purpose.
- **Lists sorted with explicit `Locale.FRENCH` collators** (`ListsRepository`, `SalesPlaces`, `DebtorListQuery`…) stay
  as they are, so list order doesn't change with the language.

---

## 2. Changing the layout for right-to-left

### How each element behaves in Arabic

| Element | Behaviour in Arabic | What to do |
|---|---|---|
| Top bar (`DsTopAppBar`: 103 uses in 73 files; `BrandTopBar`) | Title on the right, back arrow on the right pointing right, actions on the left | Nothing for layout (it already uses start/end and auto-flipping arrows). Move its own strings: "Retour", "Menu", "Fermer", "Notifications", "Profil", "Plus d'options" (`ui/designsystem/DsTopAppBar.kt:156`, `:290`, `:341`, `:392`). |
| Notifications and profile (`DsTopBarRootActions`) | Avatar furthest left, bell beside it, menu button furthest right | Already correct. The drawer must then open from the right. |
| Bottom bar | Dashboard on the right, Achats on the left, Tournées stays in the centre | Nothing (it's a `Row`). Check that the Arabic labels fit a fifth of the width (`FitText` shrinks to 80% at most). |
| Plus drawer | Must slide in from the right edge | **Rewrite its geometry** (finding 1). |
| FAB (`DsFab`, `ScrollAwareFab`) | Bottom-left (`BottomEnd` flips) | Nothing. The **speed dial needs fixing** (finding 4). |
| Cards and lists | Rows flip; amounts at the row's end go to the left | Flip the chevrons. Fix the 9 `TextAlign.End` uses. Change the text style so French or number values line up with the Arabic (see below). |
| Forms (68 `OutlinedTextField`s, the `FormField`/`DsFormField`/`BrandTextField` wrappers) | Labels and placeholders flip automatically | Make the 44 number, phone, email and code fields always left-to-right (25 Decimal, 10 Number, 5 Phone, 3 Email, 1 Password). Accept Arabic-Indic digits (finding 7). |
| Dialogs, sheets, menus (89 `AlertDialog`, 30 `BrandDialog`, 28 sheets, 26 menus) | Material 3 flips buttons and menu anchors itself | Only move the text. The date picker (10 uses) translates its own labels; its digits depend on D2. |
| Swipe to confirm (`SwipeToConfirm`) | Swiping toward the end means right to left | Nothing (it uses `StartToEnd`). Check it on a phone. |
| Pagers (7), page dots, carousels | Flip together | Nothing. The swipe between tabs keeps working. |
| Bar chart | Decision D4 | Flip the drawing and the tap detection, or lock the chart left-to-right. |
| Ring charts (`ReportComponents`, `SalesPlacesSection`, `MonObjectifScreen`) | Stay clockwise | Don't flip. The legends flip on their own. |
| Maps (Google Maps + osmdroid: `TourneeMap`, `MapPickerScreen`, `ClientLocationPickerScreen`) | The map never flips; overlay buttons switch sides | Nothing beyond keeping coordinates left-to-right. Map label language follows the app's locale (not verified). |
| Illustrations (`onboarding_*.xml`, welcome photo) | Don't flip | Nothing: no drawable is set to auto-mirror. Check visually for arrows inside the drawings. |
| `BrandWave` (`ui/designsystem/BrandComponents.kt:153`; Compte, Abonnement, Admin, SignIn) | Drawn from the left, but placed at the start, so in Arabic its high end would land mid-screen | **Flip it** in RTL. |
| Onboarding step transitions (`ui/onboarding/CodePages.kt:167`, `ui/onboarding/SignUpPages.kt:147`) | Forward always slides in from the right | Flip by direction. |
| Loading shimmer (`DsSkeleton`), decorative circles (`ui/goal/MonObjectifScreen.kt:257`) | Cosmetic | Optional. |

### Icons: which flip and which don't

**Must flip.** Each one is replaced by its auto-flipping variant (these were checked to exist in the icons library
1.7.5), or by a small `mirrorInRtl()` modifier:

| Icon | Count | Where | Replacement |
|---|---|---|---|
| `ChevronRight` | 19 | `MoreScreen`, `SettingsComponents`, `SecteurComponents`, `ShareOptionsSheet`, `DataBackupScreen`, `ListsScreens` ×2, `ReceiptAndPrintSettingsScreen`, `ProductDetailScreen` ×3, `ClientDetailScreen` ×3, `SupplierDetailScreen` ×2, `TourneesScreen` ×2, `ClientsScreen` | No auto-flipping version exists: use `NavigateNext` or `KeyboardArrowRight` |
| `ArrowForwardIos` | 5 | `ui/navigation/PurchaseFormNavGraph.kt:443` (« Suivant »), `InventoryHistoryScreen`, `ProductsScreen`, `RapportsHomeScreen`, `SuppliersScreen` | auto-flipping variant |
| `KeyboardArrowRight` | 1 | `ui/common/DraftComponents.kt:170` | auto-flipping variant |
| `AssignmentReturn` | 9 | | auto-flipping variant |
| `Notes` | 7 | | auto-flipping variant |
| `Sort`, `ViewList`, `Label`, `Chat` | 1 each | | auto-flipping variants |
| `SubdirectoryArrowRight` | 1 | `ui/settings/lists/ListsScreens.kt:345` | `mirrorInRtl()` |
| `TrendingUp/Down`, `NorthEast/SouthEast` | 3 + 1 | `InventoryCartScreen`, `StockReportScreen`, `ui/products/ProductDetailScreen.kt:611` | Match the 16 trend icons that already flip, and D4 |

- Also the arrow characters inside text: "Suivant →" (×9), "Paramètres → …", "Compte → Abonnement".

**Don't flip:**
- `Navigation` (GPS arrow), `RotateRight` (the photo always turns clockwise), `LocalShipping` (truck), `ShoppingCart`.
- Up/down arrows (`ArrowUpward/Downward`, `KeyboardArrowDown/Up`, `ArrowDropDown`, `SwapVert`).
- `History`, `Schedule`, `Refresh`/`Autorenew`, `Menu`, `MoreVert`, `Search`, `Add`, `Close`, `Check`, `Delete`,
  `Edit`, `Print`, `Share`, `QrCodeScanner`, `OpenInFull`, `Percent`.

### Mixed Arabic and French text (fix centrally)
- **Alignment of French or number values.** Compose lines text up by the text's *own* direction. So a French product
  name or an amount inside a full-width `Text` (a `weight(1f)` column) would hug the wrong edge in an Arabic layout.
  - **Central fix in `ui/theme/Type.kt` / `DistriGoTheme`:** when the layout is RTL, the default text style aligns
    right, uses 0 letter spacing (letter spacing can damage connected Arabic letters), and gets a little more line
    height. That covers the Material components and `FitText` too.
  - The 9 `TextAlign.End` sites get a small "end of the layout" helper.
- **Text helpers** (extend `ui/common/BidiText.kt`; `bidiIsolate` already exists and is used 10 times): a new `ltr()`
  wrapper for:
  - **Phone numbers.** `formatPhone` (`ui/suppliers/SupplierFormScreen.kt:47`) groups digits in pairs, and in RTL the
    pairs reverse ("05 55 12" shows as "12 55 05"). A leading `+213` also moves the `+` to the far end. There are
    about 11 phone displays.
  - **Negative amounts inside Arabic sentences**, where the minus sign jumps to the wrong side.
  - **Percentages, barcodes, activation codes, document numbers and coordinates.**
- Amounts shown alone are safe: the no-break space the money formatter uses as a thousands separator keeps
  `1 236 790,50 DA` together as one left-to-right run.

---

## 3. Inventory of screens and components

Counts are unique strings per file from the scan. "Auto" means start/end layout that flips with no change. File
names below are under `ui/` unless stated.

### Fix centrally first: shared design system and components
| Component (file) | Strings | RTL changes | Risks | Used by |
|---|---|---|---|---|
| `designsystem/DsTopAppBar.kt` | 7 | auto | "label · 2/3" step badge | 73 files |
| `designsystem/BrandComponents.kt` | 3 ("Retour", "Ajouter", "Modifier %s") | `BrandWave` flip | — | Compte, Abonnement, Admin ×4, SignIn |
| `designsystem/DsFab.kt`, `components/ScrollAwareChrome.kt` | 1 | auto | — | 13 FABs |
| `designsystem/DsLicense.kt`, `DsInfoSheet.kt` + `data/license/LicensePolicy.kt` | 8 + 36 | auto | Text comes from the data layer, so it needs `UiText` | every screen root |
| `theme/Type.kt`, `Theme.kt`, `designsystem/ArabicWeightFont.kt` | — | Arabic typography variant | Tall Arabic letters can be cut off in fixed heights; the heavier Arabic weight only works from Android 10 (API 29) | everything |
| `common/ElasticUnderlineTabRow.kt` | 0 | **Rewrite (finding 3)** | wrong tab underlined | client detail, supplier detail |
| `common/CartSelectionComponents.kt` (`SelectionCartCard`, `CartBlockingBanner`, `QuantityStepper`) | 16 | auto (the stepper becomes + n −) | price field must stay left-to-right | Inventaire, Pertes, Retours, Ventes, Achats, Tournée, Chargements |
| `common/DraftComponents`, `DraftFiltering`, `DraftSelection` | 4 / 14 / 11 | `KeyboardArrowRight` | plurals ("%d sélectionné(s)") | all draft lists |
| `common/PartyFilterSheet`, `ProductFilterControls`, `FilterSheet`, `ListSort`, `ListFilters` | 15 / 14 / – / 2 / 3 | `Sort` icon | enum labels | lists |
| `common/PaymentDialog`, `PhotoPicker`, `SecteurPickerSheet`, `QuartierPickerSheet`, `SearchableSelectSheet`, `ClientSearchPicker`, `DsCompactSearchField`, `DsSelectorField` | 8 / 8 / 9 / 6 / 4 / 1 / 1 / 1 | auto | amount input stays left-to-right | many forms |
| `common/ErrorMessage.kt`, `RefusalMessage.kt` | 2 | — | **every error message passes through here** | everything |
| `common/RelativeTimeFr.kt`, `core/format/Durations.kt`, `data/model/ProductUnit.kt` | 4 / ~8 / 4 | — | French plurals | draft cards, subscription, quantities |
| `common/SwipeToConfirm.kt`, `TicketShape.kt` ("PAYÉ" stamp) | 2 | auto (verify) | — | Ventes, Tournées |
| `components/ReceiptPreviewSheet`, `ShareOptionsSheet`, `ReceiptPdfGenerator`, `ReceiptData`, `NumberToWordsFr` | 11 / 16 / 22 / 8 / 10 | `ChevronRight` | **D3: stays French**; QR and "pièce" used as identifiers | every document |
| `components/paging/PagedHistoryScreen` | 5 | auto | — | 4 history lists |

### App shell and navigation
| Screen | Files | Strings | RTL changes | Risks |
|---|---|---|---|---|
| Root, exit dialog, license dialog, Toast | `MainActivity.kt` | 9 | **Drawer** (side, translation, edge zone, gesture exclusion, drag and fling direction) | Critical. Affects the edge swipe and the back gesture. |
| Plus menu | `more/MoreScreen.kt` | 17 | `ChevronRight` | — |
| Transitions | `navigation/NavTransitions.kt` | 0 | Start/End | used by every NavHost |
| Form graphs | `PurchaseFormNavGraph` 42, `VenteFormNavGraph` 23, `TourneeVenteFormNavGraph` 23, `ChargementNavHost` 15 | 103 | `ArrowForwardIos`, "Suivant →" | These are "stable" (CLAUDE.md): only text and icon edits, no flow changes |
| Section hosts | `ClientsNavHost` 17, `SuppliersNavHost` 17, others ≤4 | ~40 | auto | — |

### Sections
| Section | Screens (strings) | Specific RTL changes | Risks / logic tied to text |
|---|---|---|---|
| Dashboard | `DashboardScreen` 44, `DashboardCards` 20, `NewSaleSpeedDial` 5 | **Speed dial** | French date "EEEE d MMMM"; `source == "camion"` label |
| Mon objectif | `GoalWording` 67, `MonObjectifScreen` 53 | rings not flipped | **French grammar built in code**: biggest rewrite into plurals |
| Dépôt vente | `VentesScreen` 41, `VenteFormScreen` 31, `VenteEditConfirmDialog` 20, `VenteBrouillonComponents` 19, `VenteBrouillonsScreen` 14, `VenteDeleteDialog` 14 | `Notes` | Status "Payé/Partiel" decides the colour |
| Tournées | `TourneesScreen` 97, `TourneeMap` 28, `TourneeVenteFormScreen` 24, `TourneeFormScreen` 17, `StockCamionScreen` 16, `TourneeVenteBrouillonsScreen` 14, `AddClientsToTourneeScreen` 8, `TourneeVenteBrouillonComponents` 7 | `ChevronRight` ×2, map overlays | scrolling title direction (verify); French + Arabic collators (keep) |
| Chargements | `ChargementFormScreen` 11, `ChargementBrouillonsScreen` 10, `ChargementBrouillonComponents` 7, `ChargementProduitScreen` 7 | auto | plurals |
| Produits | `ProductFormScreen` 67, `ProductDetailScreen` 56, `ProductsScreen` 56, `PriceHistoryScreen` 28, `ProductBarcodesField` 12, `ProductGallery` 10, `ProductImageViewer` 3, `BarcodeScannerScreen` 4 | **form tab pill**, `ChevronRight` ×3, `ArrowForwardIos`, `ViewList`, `NorthEast/SouthEast` | **Expiry date saved through the locale**; `unit_type == "pièce"`; French month names; barcodes left-to-right |
| Achats | `PurchasesScreen` 37, `PurchaseProductFilterSheet` 25, `PurchaseOrderDetailScreen` 24, `PurchaseFormScreen` 23, `BrouillonComponents` 20, `BrouillonsScreen` 14 | `Notes` | payment status label used in logic |
| Clients | `ClientDetailScreen` 53, `ClientFormScreen` 33, `ClientsScreen` 32, `ClientLocationPickerScreen` 11, `FactureRow` 5 | **tab underline**, `ChevronRight` ×3, `AssignmentReturn` ×2, `Notes` | `statut == "Partiel"`; phone left-to-right; wilaya `nameAr` (D8) |
| Fournisseurs | `SupplierDetailScreen` 45, `SupplierFormScreen` 22, `MapPickerScreen` 10, `SuppliersScreen` 9, `AchatRow` 6 | same as clients, `ArrowForwardIos` | `formatPhone` reverses its pairs |
| Inventaire | `InventoryScreen` 19, `InventoryCartScreen` 15, `InventoryCountScreen` 15, `InventoryHistoryScreen` 10 | `TrendingUp/Down`, `ArrowForwardIos` | "Inventaire session #N" stored in the stock history (D7) |
| Pertes | `NewPerteScreens` 22, `PerteDetailScreen` 20, `PerteHistoryScreen` 16, `PerteDialog` 12 | `Notes` | built-in types shown by key (`data/model/DefaultTypes.kt:22`) |
| Retours | `NewRetourScreens` 26, `RetourDetailContent` 17, `NewRetourViewModel` 11, lists 9+9, details 8+7 | `AssignmentReturn` ×5, `Label`, `Notes` | **motif is saved as the French sentence**: translate only on display |
| Charges | `ChargeHistoryScreen` 20, `ChargeDialog` 15, `ChargeDetailScreen` 13 | `Notes` | built-in charge types (key + seed name) |
| Mouvements | `MouvementsScreen` 32, `MovementFiltersSheet` 26, `MovementFilters` 8 | `AssignmentReturn` | `"%02d".format` uses the locale (`ui/mouvements/MouvementsScreen.kt:422`); history labels are data |
| Rapports | `DebtReportScreen` 50, `PartyReportScreen` 44, `StockReportScreen` 35, `ProduitsReportScreen` 32, `ProfitReportScreen` 24, `VentesReportScreen` 21, `SalesPlacesSection` 19, `RapportsHomeScreen` 15, `ReportFilterBar` 6 | **bar chart (D4)**, `TrendingDown`, `ArrowForwardIos` | `percent()` forces French; day/month names; 8 enums |
| Secteurs & quartiers | `SecteurFormScreen` 29, `QuartierFormScreen` 19, `QuartierDetailScreen` 16, `SecteursScreen` 9, `SecteurDetailScreen` 8, `SecteurComponents` 4 | `ChevronRight` | — |
| Paramètres | `DataBackupScreen` 79, `PrinterSelectionScreen` 49, `CommissionPolicyScreen` 46, `ListsScreens` 46, `ImportScreen` 45, `ParametresScreen` 38, `ExportScreen` 25, `ReceiptAndPrintSettingsScreen` 25, `DiagnosticsScreen` 19, `DataBackupFormatting` 15, `TrashScreen` 12, `SampleReceipt` 11 | `ChevronRight` ×4, `SubdirectoryArrowRight` | plus `ImportPlanner` 127 / `TrashRepository` 65 in data; Ko/Mo/s units; **add the « Langue » row** (`ui/settings/ParametresScreen.kt:94`) |
| Compte / Abonnement | `CompteScreen` 78, `CompteDialogs` 52, `AbonnementScreen` 27, `AccountViewModel` 21 | `BrandWave`, `Chat` | `AuthError` codes are mapped on the phone, so they can be translated |
| Administration (owner) | `AdminScreens` 95, `admin/ClientsScreen` 42, `AdminViewModel` 18, `CodeExports` 12, `CodeCardsPdf` 10 | auto | D10: can stay French |
| Onboarding | `SignUpPages` 39, `FeaturePages` 35, `CodePages` 25, `SuccessPage` 15, `SignInPage` 13, `OnboardingNavHost` 8, `OnboardingViewModel` 7, `WelcomeScreen` 6 | **step transitions**, `BrandWave` | first launch, so this is where to offer the language choice |
| Background (not a screen) | `AutoBackupAlerts`, `CheckInAlerts`, `BackupJobNotifications`, `BackupNowWorker`, `DistriGoApplication` Toast | — | no activity context before Android 13 (see §4) |

---

## 4. Switching languages

**Recommendation: a small in-house `AppLanguage` layer, without adding AppCompat.**

1. **Model.** `AppLanguage { FRENCH("fr"), ARABIC("ar-DZ") }`, each shown in its own language ("Français", "العربية").
   - The choice belongs to the **phone**: it is not stored in `business_settings` and not included in the `.distrigo`
     backup.
   - So there is **no database migration**, and a restore never changes the language.
2. **Android 13 and later (API 33+):** use the system's per-app language (`LocaleManager.setApplicationLocales`) plus
   `android:localeConfig` in the manifest.
   - Android saves the choice itself, it also appears in the phone's Settings › Apps › DistriGo › Language, and the
     screens restart automatically.
3. **Android 8 to 12 (API 26–32; the minimum is 26):**
   - Save the language code in a tiny preferences file, read synchronously at startup. This is what AppCompat does;
     in debug, StrictMode only logs it.
   - Apply it in `MainActivity.attachBaseContext` and set `Locale.setDefault`. Call `recreate()` after a change.
   - Notifications, workers and the Application Toast resolve their text through `AppLanguage.context(appContext)`.
     Re-register the notification channel so its name follows the language.
4. **Why not AppCompat:**
   - It would need `MainActivity : AppCompatActivity` and an AppCompat theme parent;
     `app/src/main/res/values/themes.xml:4` currently uses `android:Theme.Material.Light.NoActionBar`.
   - Below Android 13 it still doesn't translate the Application context, so the helper above would be needed anyway.
5. **What survives the switch.** `MainActivity` declares no `configChanges` and its rotation isn't locked, so a
   language change rebuilds it exactly like a rotation does today.
   - `rememberSaveable` state, ViewModels, the navigation back stack, drafts and the onboarding overlay all survive.
   - The only catch is text a ViewModel already built in French, which `UiText` solves.
6. **Where the user chooses:** Paramètres › Préférences « Langue » (next to « Format des montants »), plus a small
   Français/العربية switch on the onboarding welcome screen.
7. **First launch after the update (D1):** if no choice has been saved, pin French.

---

## 5. Technical risks and quality checks

| Risk | Detail | What to do |
|---|---|---|
| Missed hardcoded strings | Lint can't detect them in Compose | A JVM test that scans `ui/` for `Text("…")`, `contentDescription = "…"` and similar, with an allowlist. Turn on pseudo-locales in debug: under `ar-XB`, any string left in code shows up un-mirrored. |
| Unit tests | **45 of 107 unit-test files** check French text; there is no Robolectric | Compare `UiText` / resource ids instead (the `R` ids work in plain JVM tests). Keep the receipt tests in French (D3). |
| Digits | `String.format` / `SimpleDateFormat` with the phone's locale (`ProductFormScreen:373`, `ProductListFilters:75`, `MouvementsScreen:422`); Arabic-Indic keyboard digits | **Done 2026-10-27**: see "Digits are always 0–9". |
| Text used as an identifier | Retour motifs, `"pièce"`, `"Partiel"`, built-in type names, QR date, import headers | Never translate stored values. Turn the payment status into a UI-only enum. Show built-ins by key. |
| Bidi (mixed direction) | Phones, negative amounts, percentages, "→" in text | `ltr()` helpers; icons instead of arrow characters. |
| Fixed geometry | The 8 hand-positioned places in findings 1–5 | Phase 2 below. |
| Cut-off and overflowing text | 155 single-line `Text`s, 30 `softWrap=false`, 53 custom line heights, fixed-height tiles; Arabic letters are taller | Walk every screen at 360dp width, font scale 1.3, and a low-resolution emulator (720×1280). `FitText` (24 uses) already shrinks to fit. |
| Performance | Arabic letter shaping costs more; the heavier Arabic font builds 6 typefaces on first use | Measure FPS on the benchmark build in both languages with the existing tools. Re-record the baseline profile. Cache formatters per language, not per redraw. Choose the RTL typography once per direction. |
| Memory and app size | `values-ar` adds roughly 0.3 MB at most | `localeFilters` removes the other ~80 library languages, so the APK gets smaller. |
| Other screens | Map label language, date picker digits, scrolling title direction | Check on a phone. |

**Checks:**
- Use the developer option "Force RTL layout direction" to check layout fixes **before** any Arabic text exists.
- Test data that mixes Arabic, French and digits in product names, plus `+213` phone numbers, negative balances and
  percentages.
- Final walk of every screen in both languages on the M34 and one small emulator.

---

## 6. Implementation phases

| Phase | Scope | Files | Depends on | Risks | Done when |
|---|---|---|---|---|---|
| **0. Decisions and glossary** (S) | D1–D10; Arabic glossary of ~150 domain terms | none | — | wrong terms copied everywhere | owner approves |
| **1. Infrastructure** (M), no visible change in French | `strings_*` files + `tools:locale`, lint, `localeFilters`, pseudo-locales; `UiText`, `UserFacingException`, `AppLanguage` (+ `locales_config`, `attachBaseContext`, `LocaleManager`), `LocalAppLocale` + formatters, bidi helpers, digit normalization, locale-safe date writes, guard test (report-only at first) | `MainActivity`, `DistriGoApplication`, manifest, `app/build.gradle.kts`, `ui/common/*`, `ui/theme/*`, `core/format/*`, `Amount`, `Quantity`, `ProductFormScreen:373` | Phase 0 | the startup read; the change of language | French screens identical, all tests pass, a debug-only language switch persists and recreates, notifications follow |
| **2. RTL structure** (M), checkable with Force RTL | drawer, transitions, tab underline + pill, speed dial, chart (D4), `BrandWave`, onboarding transitions, ~45 icons, RTL typography, `TextAlign.End` helpers, left-to-right fields and phones | `MainActivity`, `NavTransitions`, `ElasticUnderlineTabRow`, `ProductFormScreen`, `NewSaleSpeedDial`, `VentesReportScreen`, `BrandComponents`, `Type.kt`, ~25 icon files | 1 | drawer gestures; the back gesture | every screen correct in Force RTL; French unchanged |
| **3. Shared text** (L) | design system, common words, dialogs, sheets, filters, cart, drafts, payment; error plumbing; 21 enums; `PaymentStatus`; plurals helpers; units, durations, relative time; built-in types and motifs shown by key; wilaya `nameAr` | `ui/designsystem`, `ui/common`, `ui/components` (minus documents), `data/model`, `data/repository` messages | 1 | a translation slipping into stored values | all shared pieces come from resources, tests use resource ids |
| **4. Screens by section** (XL, ~2,150 strings) | order: shell + Plus → Dashboard → Ventes → Tournées + Chargements + map → Produits → Achats → Clients/Fournisseurs → Inventaire/Pertes/Retours → Charges/Mouvements → Rapports → Mon objectif → Paramètres → Compte/Abonnement (/Admin, D10) → Onboarding → background notifications | one commit per section | 3 | the "stable" form graphs: only text and icons change | guard test passes per section; French identical |
| **5. Arabic translation** (L, ~13,700 words) | fill `values-ar/*`, plurals, owner's review in the app | `app/src/main/res/values-ar` | 4 + glossary | missing plural forms; wrong terms | lint finds no missing translation or plural; owner signs off |
| **6. QA and release** (M) | full walks, pseudo-locale pass, font scale, FPS, baseline profile, update CLAUDE.md ("UI text is French" becomes the resource rules) | — | 5 | — | owner's acceptance |
| *7. Arabic documents (optional, later)* | receipts, PDF, WhatsApp text, Arabic amount-in-words, mirrored receipt layout, a « Langue des documents » setting | `data/print`, `ui/components` | D3 | the printer still hasn't been tested on a real device | separate approval |

---

## Checked in the code vs assumed

**Checked in the code:** every finding numbered above, every count, the list of auto-flipping icons that exist (in
the library sources on disk), the drawer and offset logic, the stored identifiers, the data-layer messages, the
absence of AppCompat, WebView and `configChanges`, the tests that check French text, and `wilayas.json` having
`nameAr`.

**Assumed, to confirm on a phone:**
- Compose aligns text by the text's own direction (from the Compose documentation).
- `ar-DZ` gives 0–9 digits and Algerian month names on Android.
- ~~`toDoubleOrNull` rejects Arabic-Indic digits~~: confirmed by a unit test on 2026-10-27, and fixed.
- Which keyboards actually send Arabic-Indic digits.
- Swipe-to-confirm and the scrolling title behave correctly in RTL.
- Google Maps label language.
- The interpretation of Material's guidance on charts.
- The string counts (±15%).

## What was inspected and what's left
- **Read closely:** `MainActivity`, the Application class, manifest, build files, theme, design system, top bars,
  `BrandComponents`, transitions, tab switching, the tab underline, speed dial, swipe-to-confirm, ticket shape,
  bidi/font/time/money/amount/quantity helpers, units, default types, motifs, charge seeds, `MoreScreen`, onboarding
  carousel, error helpers, backup messages, the receipt row/renderer, import plan and receipt data.
- **Scanned with patterns in all 562 files:** strings, icons, alignment, gestures, canvas drawing, formatters,
  locales, notifications, enums, text compared in logic, and tests.
- **Not read line by line:** the inside of the ~140 screen files, for example `TourneesScreen` (1,796 lines),
  `ProductFormScreen` and `ProductsScreen`. Their RTL status comes from the absence of fixed left/right code and from
  the shared components they use. Clipping or tight widths specific to one screen will only show up in the Phase 2
  Force-RTL walk.
- **Not reviewed at all:** `androidTest`, `baselineprofile`, the Supabase functions (only checked that the server's
  refusal codes are mapped to French on the phone, in `AuthApi`, `FunctionCalls` and `LicenseClient`), and
  `CodesAdminClient`'s message paths.

## Next step
The owner answers D1–D10 (or approves the recommendations). Then Phase 0 (the glossary), then Phase 1.
