package com.distrigo.app.data.model

import com.distrigo.app.R

enum class AchatFilter(@androidx.annotation.StringRes val label: Int) {
    TOUTES(R.string.common_filter_all_f),
    PAYEE(R.string.common_filter_paid_f),
    PARTIELLE(R.string.common_filter_partial_f),
    IMPAYEE(R.string.common_filter_unpaid_f),
    VERSEMENT(R.string.common_filter_payments)
}
