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
3. **To show or group an instant by day, convert it through the local zone first**: `BusinessDates.localDay(value)` (`data/time/BusinessDates.kt`). Never take the first ten characters of an instant: that is its UTC date.
4. **To select instants within a local day or month, compare with instant bounds**: `BusinessDates.monthBounds(month)` or `BusinessDates.dayRangeBounds(from, to)`, both half-open `[start, end)`. Their bounds are written `yyyy-MM-ddTHH:mm:ss` without the `Z`, which sorts just below every stored instant of that second, so a text comparison keeps its fractional digits right. Comparing an instant with `"2026-09-01"` compares against UTC midnight.
5. **Material date pickers work in UTC milliseconds.** Converting a picked value with `ZoneOffset.UTC` to get the `LocalDate` the user tapped is correct, and is not a deviation from rule 3.
6. **Receipts print local time**: see `RECEIPT_ZONE` in `ReceiptData.kt`.

## Fixed

Until September 2026, day lists and month totals read instants in UTC, so in Algeria everything recorded **between 00:00 and 01:00 local time** landed on the previous day, or on the previous month on the 1st. All of these now follow rules 3 and 4:

- the day headers and date filters of the Dépôt Vente and Achats lists, the client and supplier ledgers, the stock movements, the charges and pertes lists and the inventory history, and the vente detail's date;
- `formatOrderDate`, which read a sale at 00:30 as "Hier";
- the charges and pertes month statistics and lists (`monthRange`);
- the stock movements filter, which also left out the whole last day of its range.

`pertes.date_time` used to mean two things: the day chosen in the Pertes form, stored as **UTC** midnight, and the instant a return recorded its loss. The form now stores the chosen day's **local** midnight, so the column holds instants throughout. Rows written before read as the right day in any zone at or ahead of UTC, which includes Algeria; none was rewritten.

## Known deviations

### Not yet wired: commission periods

`IncentiveRepository.calculateDistributorReward(periodStartIso, periodEndIso)` compares its bounds with `ventes.created_at` and `client_payments.created_at` as strings. Nothing calls it yet. The caller must pass the UTC instants of the local period's bounds (rule 4), not local dates.

### Draft tables

`purchase_drafts`, `vente_drafts`, `tournee_vente_drafts` and `chargement_drafts` keep `updated_at` as ISO text rather than milliseconds. They never leave the device and are not change-tracked, so this is consistent with their purpose rather than a defect.
