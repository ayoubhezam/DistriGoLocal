# The DistriGo backup file

What a `.distrigo` backup holds, what it leaves out, and the rules a restore follows. The code that relies on this is `data/backup/BackupFormat.kt` and `BackupManifest.kt`.

A backup is a **full copy** of one phone's data at one moment, and a restore **replaces** everything with it. Nothing is merged: combining two phones' data is the job of sync, not of a backup.

## The file

A ZIP named for the local time it was made, such as `DistriGo-2026-09-17-1430.distrigo`. It holds exactly these entries, and a restore refuses a file holding anything else:

| Entry | What it is |
|---|---|
| `manifest.json` | What the backup holds, with a size and SHA-256 for every other entry |
| `distrigo.db` | The whole database, as one SQLite file |
| `images/<sha256>.jpg` | Each photo the data refers to, under the same name as in `filesDir/images` |

No directories, no other names, no `..`: a name that does not match one of these three shapes makes the file damaged, so nothing can unpack outside its folder.

## `manifest.json`

```json
{
  "format_version": 1,
  "schema_version": 52,
  "app_version": "1.0 (1)",
  "created_at": "2026-09-17T13:30:05.123Z",
  "database_id": "38b1db1e-…",
  "device_id": "6ded79d5-210a-4e54-a58b-5caf1b0e7ee9",
  "device_model": "samsung SM-M346B",
  "row_counts": { "clients": 18, "products": 153, "ventes": 27 },
  "entries": [
    { "name": "distrigo.db", "size": 966656, "sha256": "…" },
    { "name": "images/0f3c….jpg", "size": 48211, "sha256": "…" }
  ]
}
```

| Field | Meaning |
|---|---|
| `format_version` | The layout of the file, currently `1`. Raised only when an older app would misread a new file; a new field older apps can ignore does not raise it, and unknown fields are ignored |
| `schema_version` | `PRAGMA user_version` of the copied database |
| `app_version` | `versionName (versionCode)` of the app that wrote it. Shown, never compared. Optional |
| `created_at` | When the copy was taken: a UTC instant (see [timestamps.md](timestamps.md)) |
| `database_id` | `app_meta.database_id`: which body of data this is |
| `device_id` | `app_meta.device_id` of the phone that wrote it |
| `device_model` | A name the user recognises. Optional |
| `row_counts` | Rows per table in the copy, for the restore preview and to check the restored database |
| `entries` | Every entry but the manifest itself: name, size in bytes, lower-case hex SHA-256 |

A manifest is damaged when a required field is missing or malformed, a count or size is negative or not a whole number, a size is past its limit (1 MB for the manifest, 2 GB for the database, 20 MB for a photo), an entry is listed twice, or there is no `distrigo.db`.

## What is in the backup

- **Every table of the database**, including the draft tables, `tombstones`, and `app_meta` with its `database_id` and document numbering counters. The copy is taken while writes are held back, with the write-ahead journal folded into it, so it is one consistent file.
- **The photos the data refers to**: every `img:` reference in the database whose file exists, which includes the receipt logo (`business_settings.logo_ref`). Photos no row refers to any more are left out. A referenced photo missing from the phone is left out as well; the app already shows a missing photo as no photo.

## What is not

| Left out | Why |
|---|---|
| The phone's device ID (`noBackupFilesDir/device_id`) | It names the phone, not the data. A restored database takes the ID of the phone it is restored on |
| The `image_backfill` flag | It records that this phone's database holds no base64 photos. A restore clears it, so a restored database is checked again |
| The `business_settings` preferences | Emptied when the identity moved into the database (version 52) |
| `settings_preference` and the other `com.google.*` preferences | The Google Maps SDK's own state, rebuilt by the SDK |
| `cacheDir/receipts` | Receipt PDFs are made again when shared |

## Restoring

A restore checks everything before it changes anything, in this order, and stops at the first failure with a message the user can act on:

1. The file is a ZIP with a readable `manifest.json` (otherwise: not a DistriGo backup).
2. `format_version` is not later than this app's (otherwise: update the app).
3. `schema_version` is not later than this app's database version, since Room cannot downgrade (otherwise: update the app), and not earlier than 32, the first version with a migration path, since opening it would recreate it empty.
4. The ZIP's entries are exactly the manifest's, each within its size and matching its SHA-256.
5. Unpacked into a staging folder, the database's `user_version` is the manifest's `schema_version`, it passes `PRAGMA integrity_check`, and its row counts match the manifest's. Counts are compared here, before migrating, because a migration may add rows.
6. The staged database then opens through the normal migrations, never the destructive fallback.

The schema declares no foreign keys, so there is no `foreign_key_check` to run.

Only then is an automatic safety backup of the current data taken, and the database and photos are swapped in when the app next starts, before the database opens.

### Identity after a restore

- **`database_id` is kept from the backup.** It is the same body of data, whichever phone it lands on.
- **`device_id` is the restoring phone's**, as on every open. Documents created after the restore are numbered with that phone's code, continuing the counters the backup carried, so they cannot collide with numbers the other phone issues.
- **The restore is recorded in `app_meta`**, so sync can later tell that this database went back in time.
