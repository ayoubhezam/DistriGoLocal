package com.distrigo.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A record read by id: [loaded] once the first read has landed, [value] null then if it is gone. */
data class LiveRecord<T>(val loaded: Boolean, val value: T?)

/**
 * The record [source] observes, live (PERF-01): a client's or a supplier's page reads that one row, where it used to
 * find it in the whole table, read again after every sale.
 */
@Composable
fun <T : Any> liveRecord(key: Any, source: () -> Flow<T?>): LiveRecord<T> {
    val record by remember(key) { source().map { LiveRecord<T>(loaded = true, value = it) } }
        .collectAsStateWithLifecycle(LiveRecord<T>(loaded = false, value = null))
    return record
}
