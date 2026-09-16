package com.distrigo.app.data.local.entity

import java.util.UUID

/**
 * The stable identity of a new row: a random (version 4) UUID, lowercase with hyphens.
 *
 * Every business table carries one in a `uuid` column beside its Int `id`. The Int stays the key
 * inside this database — navigation, paging cursors, joins and draft JSON all use it — but it is
 * only unique on one device. The UUID is unique everywhere, which is what a backup merge or a sync
 * needs to recognise the same row on two devices. Nothing reads it yet.
 *
 * Entities take it as a constructor default, so a row gets one when it is first built and keeps it
 * through every `copy()`. That makes one rule matter: an update must copy the row it read, never
 * build a fresh entity with the same `id`, or the row would silently get a new identity. Every
 * update path follows that rule today.
 *
 * The draft tables have no `uuid`: a draft never leaves the device it was typed on.
 */
fun newRowUuid(): String = UUID.randomUUID().toString()
