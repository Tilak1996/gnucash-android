/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report

import org.gnucash.android.model.data.AccountType

/** Listener for passing reporting options from the activity to report fragments. */
interface ReportOptionsListener {
    fun onTimeRangeUpdated(start: Long, end: Long)
    fun onGroupingUpdated(groupInterval: ReportsActivity.GroupInterval)
    fun onAccountTypeUpdated(accountType: AccountType)
}
