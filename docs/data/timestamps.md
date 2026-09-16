# Dates and times in DistriGo

How the database stores time, the rules new code follows, and where today's code does not follow them yet.

Written from the code and from a real device's data (schema version 50). Existing data is not rewritten to match: the deviations at the end are recorded so they can be fixed deliberately, each with its own change.

## The convention

DistriGo stores two different kinds of time, and never mixes them up.

### 1. An instant: *when something happened*

A point on the global timeline — a sale was recorded, a client was visited, a row was edited. Always **UTC**.

| Encoding | Used for | Example | Produced by |
|---|---|---|---|
| ISO-8601 text, `Z` suffix | Business columns: `created_at`, `date_debut`, `date_fin`, `visited_at`, `started_at`, `completed_at`, `charges.date_time`, `base_captured_at` | `2026-09-16T21:46:23.643262Z` | `Instant.now().toString()` |
| `INTEGER`, milliseconds since the epoch | Change-tracking metadata: `updated_at` and `deleted_at` on business tables, `tombstones.deleted_at` | `1789574502472` | `System.currentTimeMillis()`, or `julianday('now')` in the triggers |

ISO text sorts in time order as a string, which is what the range queries and keyset cursors rely on. The fractional digits vary (Android's `Instant.toString()` drops trailing zeros), so compare instants as instants or as whole strings; never slice them by position.

An unknown instant is `1970-01-01T00:00:00Z`: rows that existed before a column recorded it (MIGRATION_37_38, MIGRATION_43_44).

### 2. A calendar date: *which day it belongs to*

A day in the business's life with no time attached — the date written on a bon, a return, a chargement session, an expiry date. Always **local**, as `yyyy-MM-dd`.

| Column | Example |
|---|---|
| `purchase_orders.date`, `price_history.date` | `2026-09-16` |
| `retour_client.date`, `retour_fournisseur.date` | `2026-08-19` |
| `chargement_sessions.session_date` | `2026-09-11` |
| `products.expiry_date`, `purchase_order_items.expiry_date` | `2026-09-25` |

The device's time zone is the business's (Algeria: `Africa/Algiers`, UTC+1, no daylight saving).

### Rules for new code

1. **Record an instant in UTC**: `Instant.now().toString()` for a business column, epoch milliseconds for sync metadata. Never store a local time without its offset.
2. **Record a calendar date as local `yyyy-MM-dd`**: `LocalDate.now().toString()`, or the date the user picked.
3. **To show or group an instant by day, convert it through the local zone first**: `Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate()`. Never take the first ten characters of an instant: that is its UTC date.
4. **To select instants within a local day or month, convert the local bounds to instants**: `day.atStartOfDay(zone).toInstant()` to the next day's. Comparing an instant with `"2026-09-01"` compares against UTC midnight.
5. **Material date pickers work in UTC milliseconds.** Converting a picked value with `ZoneOffset.UTC` to get the `LocalDate` the user tapped is correct, and is not a deviation from rule 3.
6. **Receipts print local time**: see `RECEIPT_ZONE` in `ReceiptData.kt`.

## Known deviations

In Algeria (UTC+1), every one of these affects only records made **between 00:00 and 01:00 local time**, which land on the previous day, or on the previous month on the 1st. None is fixed yet.

### Instants grouped or filtered by their UTC date (rule 3)

`created_at.take(10)` is used as the day of:

- the Dépôt Vente list and its date filter: `ui/common/ListFilters.kt` (`filterVentes`, `groupVentesByDay`)
- the Achats list and its date filter: `ui/common/ListFilters.kt` (`filterOrders` and its day grouping)
- the client ledger's day headers: `ui/clients/ClientDetailScreen.kt`
- the stock movements' day headers: `ui/mouvements/MouvementsScreen.kt`
- the charges list's day headers (`date_time.take(10)`): `ui/charges/ChargeListScreen.kt`
- the inventory history's day headers: `ui/inventory/InventoryHistoryScreen.kt`

`formatOrderDate` (`ui/purchases/PurchasesScreen.kt`) compares the UTC date of an instant with the local today, so a sale at 00:30 reads "Hier".

### Month totals against UTC instants (rule 4)

The charges and pertes month statistics compare `date_time` with `monthRange("2026-09")` = `["2026-09-01", "2026-10-01")` (`data/repository/MonthRange.kt`). A charge at 00:30 on 1 October is counted in September.

### `pertes.date_time` holds two meanings

- Recorded from the Pertes form: the chosen **day**, stored as UTC midnight (`ui/navigation/PertesFormNavGraph.kt`, `atStartOfDay(ZoneOffset.UTC)`).
- Recorded by a return: the **instant** it happened (`RetourClientRepository` and `RetourFournisseurRepository`, `dateTime = now`).

The form's values read back as the right day through `take(10)`, and the returns' do not always. Fixing rule 3 for pertes means deciding which meaning the column has first.

### Not yet wired: commission periods

`IncentiveRepository.calculateDistributorReward(periodStartIso, periodEndIso)` compares its bounds with `ventes.created_at` and `client_payments.created_at` as strings. Nothing calls it yet. The caller must pass the UTC instants of the local period's bounds (rule 4), not local dates.

### Draft tables

`purchase_drafts`, `vente_drafts`, `tournee_vente_drafts` and `chargement_drafts` keep `updated_at` as ISO text rather than milliseconds. They never leave the device and are not change-tracked, so this is consistent with their purpose rather than a defect.
