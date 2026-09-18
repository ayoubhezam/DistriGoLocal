# The DistriGo backup file

What a `.distrigo` backup holds, what it leaves out, and the rules a restore follows. The code that relies on this is `data/backup/BackupFormat.kt` and `BackupManifest.kt`.

A backup is a **full copy** of one phone's data at one moment, and a restore **replaces** everything with it. Nothing is merged: combining two phones' data is the job of sync, not of a backup.

## The file

A ZIP named for the local time it was made, such as `DistriGo-2026-09-17-1430.distrigo`. It holds exactly these entries, and a restore refuses a file holding anything else:

| Entry | What it is |
|---|---|
| `manifest.json` | What the backup holds, with a size and SHA-256 for every other entry |
| `distrigo.db` | The whole database, as one SQLite file |
| `images/<sha256>.jpg` | Each photo the data refers to, under the same name as in `filesDir/images`. Absent from a data-only backup (`photos_included` false in the manifest), such as the copy taken before an import: restoring one keeps the phone's photos as they are |

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

1. The file opens (otherwise: it is gone, or no longer shared with the app).
2. It is a ZIP whose first entry is a readable `manifest.json` (otherwise: not a DistriGo backup), and `format_version` is not later than this app's (otherwise: update the app).
3. Its entries are exactly the manifest's, each once, within its size and matching its SHA-256, and the ZIP ends with its end record, so a file cut short is caught.
4. `schema_version` is not later than this app's database version, since Room cannot downgrade (otherwise: update the app), and not earlier than 32, the first version with a migration path, since opening it would recreate it empty.

Only a file that passes these is previewed: its date, the phone that made it, and its row counts next to the phone's current ones (`BackupInspector`). The messages the user sees are in `BackupMessages`. When the user confirms, the restore reads the file again, checking it the same way as it unpacks it, then:

5. The phone has room: every entry's size, plus the database's size again for opening it, plus 16 MB. This, the version checks and a file that changed since its preview are all refused before anything is unpacked.
6. Unpacked into `noBackupFilesDir/restore/staging` (not the cache, which Android may clear before the restore is installed), the database's `user_version` is the manifest's `schema_version`, it passes `PRAGMA integrity_check`, and its row counts match the manifest's. Counts are compared here, before migrating, because a migration may add rows.
7. The staged database opens as the app opens its own: every migration, never the destructive fallback, the app's triggers, and this phone's `device_id`.
8. Its shape then matches a database this app creates: every table's columns and indices, and every trigger (`SchemaFingerprint`). Room checks tables only after migrating, and at the app's own version trusts an identity hash stored in the file, so a hand-edited file with valid checksums could otherwise lack a column or carry a trigger of its own.
9. Folded back into one file, it passes `integrity_check` again and keeps the manifest's `database_id` (`RestorePreparer`).

The schema declares no foreign keys, so there is no `foreign_key_check` to run. A failure at any point deletes the staging folder; nothing on the phone has changed.

### Installing

Only then is a **safety backup** of the current data taken: a normal `.distrigo` file in `no_backup/restore/safety`, named `avant-restauration-<local time>.distrigo`, restorable like any other to undo the restore. The newest three are kept. They are in the app's private storage, so they do not survive uninstalling the app. No safety backup, no restore (`RestoreCoordinator`).

The prepared restore then moves to `no_backup/restore/pending` with a `READY` marker, and the app restarts. The swap happens at the very start of the next launch, in `Application.onCreate` and again before the database is built, so nothing can be holding the old data (`RestoreInstaller`):

1. `moving-old`: the database files and the photo folder move to `restore/previous`.
2. `moving-new`: the restored database and photos move into place, and the database passes `quick_check`, is at this app's version, and is the `database_id` that was prepared.

The install runs on its own thread as the app starts (`RestoreStartup`): the first database open waits for it, and the main screen shows « Restauration en cours… » until it ends.
3. `finishing`: `app_meta` records `restore.last_at` and `restore.backup_created_at`, and the restored backup becomes the data's last backup (`backup.last_*`: its date, and the name and size of the file picked), since the data is now exactly that backup and a backup never contains its own record. ImageBackfill's done flag is cleared, and `pending` and `previous` are deleted.

Each move is a rename within the app's storage, and the stage is written to `restore/install-state` before it starts. A launch killed in stage 1 or 2 puts every file back and starts again, at most three times; one killed in stage 3 finishes it. A restored database that fails its check is not retried: the old data goes back and the restore is discarded. The outcome is written to `restore/last-result` for the screen to report.

## Automatic backups

A normal `.distrigo` backup, saved without the user (`data/backup/auto`):

- **Where.** A folder the user picks once with the system folder picker; the app keeps access to it across restarts. When that folder cannot be used — deleted, renamed, or its access taken back — the backup is saved in the app's own storage (`no_backup/auto-backup/files`) and the screen says the folder is inaccessible. A copy there is not recorded as the data's last backup: it does not survive uninstalling the app.
- **When nothing changed, nothing is saved.** `DataFingerprint` digests every table's row ids and `updated_at`, `tombstones`, and `app_meta` without its `backup.*` and `restore.*` keys. A run whose fingerprint matches the last backup's, whose last file is still where it was saved, saves nothing. The fingerprint is taken before the copy, so a change during a backup causes one more backup, never one fewer.
- **Names and rotation.** `DistriGo-auto-yyyy-MM-dd-HHmmss.distrigo`, in local time and to the second, so two backups never share a name. After each save only the newest seven files with exactly that name shape are kept. Nothing else in the folder is ever deleted: manual backups, renamed copies, `… (1)` duplicates or the user's own files.
- **Schedule.** While automatic backups are on, a WorkManager job runs once every 24 hours, first at the next 03:00 local time, only while the battery and storage are not low. Android picks the exact moment, so a run can be hours late. The job always reports success to WorkManager: a failed backup is recorded and tried again the next night, not retried within minutes.
- **Problems and alerts.** A run leaves a problem when it fails, or when it could not use a chosen folder — including an unchanged night, since the only copies are then in the app. Problems in a row are counted and a good run resets the count. From the second in a row a notification says what is wrong; it is one notification, updated silently, and removed after the next good run. The notification permission is asked for when daily backups are turned on; without it the screen alone shows the problem. If the daily job has not run for 36 hours while on (manual backups do not count), the screen suggests setting DistriGo's battery use to « Non restreinte » and opens the app's settings.
- **State.** The on/off setting, the folder, the last run's fingerprint, time, file and location, and the problem count are kept in `no_backup/auto-backup/state.json`, not in the database: they belong to the phone, and a restore must neither bring another phone's folder nor make the next run believe restored data was already backed up.

### Identity after a restore

- **`database_id` is kept from the backup.** It is the same body of data, whichever phone it lands on.
- **`device_id` is the restoring phone's**, as on every open. Documents created after the restore are numbered with that phone's code, continuing the counters the backup carried, so they cannot collide with numbers the other phone issues.
- **The restore is recorded in `app_meta`** (`restore.last_at`, `restore.backup_created_at`), so sync can later tell that this database went back in time.
