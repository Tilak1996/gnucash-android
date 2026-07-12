/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report

import androidx.annotation.ColorRes
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.report.barchart.StackedBarChartFragment
import org.gnucash.android.ui.report.linechart.CashFlowLineChartFragment
import org.gnucash.android.ui.report.piechart.PieChartFragment
import org.gnucash.android.ui.report.sheet.BalanceSheetFragment

/** Different types of reports and the report fragments available for each type. */
enum class ReportType(private val value: Int) {
    PIE_CHART(0), BAR_CHART(1), LINE_CHART(2), TEXT(3), NONE(4);

    private val reportTypeMap: Map<String, Class<out BaseReportFragment>> = buildMap {
        val context = GnuCashApplication.getAppContext()
        when (value) {
            0 -> put(context.getString(R.string.title_pie_chart), PieChartFragment::class.java)
            1 -> put(context.getString(R.string.title_bar_chart), StackedBarChartFragment::class.java)
            2 -> put(context.getString(R.string.title_cash_flow_report), CashFlowLineChartFragment::class.java)
            3 -> put(context.getString(R.string.title_balance_sheet_report), BalanceSheetFragment::class.java)
        }
    }

    @get:ColorRes
    val titleColor: Int
        get() = when (value) {
            0 -> R.color.account_green
            1 -> R.color.account_red
            2 -> R.color.account_blue
            3 -> R.color.account_purple
            else -> R.color.theme_primary
        }

    val reportNames: List<String>
        get() = reportTypeMap.keys.toList()

    fun getFragment(name: String): BaseReportFragment? = try {
        reportTypeMap[name]?.getDeclaredConstructor()?.newInstance()
    } catch (error: ReflectiveOperationException) {
        error.printStackTrace()
        null
    }
}
