package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A secteur attached to a tournée — the join table behind "Souk Ahras (secteur 01, secteur 02)".
 *
 * Shaped after [TourneeClientEntity]: a plain row with its own id, no foreign key (nothing else in
 * this schema declares one), and an `order_index` so the secteurs read back in the order they were
 * picked rather than in insertion-rowid order.
 *
 * `secteur_name` is a snapshot, exactly as `clients.secteur_name` is. Display paths — the Tournée
 * card and the detail bar — read it directly and never join, which is what keeps `toTournee()` to
 * one extra query. `secteur_id` is kept alongside so a later feature can still join on the real
 * secteur (e.g. pulling in every client of a tournée's secteurs); renaming a secteur therefore does
 * not rewrite the tournées that already recorded it, which matches how clients behave today.
 */
@Entity(
    tableName = "tournee_secteurs",
    indices = [Index(value = ["tournee_id", "order_index"])]
)
data class TourneeSecteurEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tournee_id: Int,
    val secteur_id: Int,
    val secteur_name: String,
    val order_index: Int = 0
)
