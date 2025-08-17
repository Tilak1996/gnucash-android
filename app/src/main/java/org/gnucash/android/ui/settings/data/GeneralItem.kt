package org.gnucash.android.ui.settings.data

import androidx.annotation.IdRes

data class GeneralItem(
    val title: String,
    @IdRes val navigationPath: Int
)