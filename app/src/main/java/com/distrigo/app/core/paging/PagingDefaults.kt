package com.distrigo.app.core.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

object PagingDefaults {
    const val PAGE_SIZE = 20
    const val PREFETCH_DISTANCE = 6

    val config = PagingConfig(
        pageSize = PAGE_SIZE,
        prefetchDistance = PREFETCH_DISTANCE,
        enablePlaceholders = false
    )
}

fun <T : Any> pagedFlow(
    pagingSourceFactory: () -> PagingSource<Int, T>
): Flow<PagingData<T>> =
    Pager(
        config = PagingDefaults.config,
        pagingSourceFactory = pagingSourceFactory
    ).flow