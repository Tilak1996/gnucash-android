/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.Legend.LegendPosition
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import org.gnucash.android.R
import org.gnucash.android.model.data.Account
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.data.Money
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.report.barchart.StackedBarChartFragment
import org.gnucash.android.ui.report.linechart.CashFlowLineChartFragment
import org.gnucash.android.ui.report.piechart.PieChartFragment
import org.gnucash.android.ui.report.sheet.BalanceSheetFragment
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.joda.time.LocalDate
import java.util.Collections

/** Shows a summary of the available reports. */
class ReportsOverviewFragment : BaseReportFragment() {
    private lateinit var pieChartButton: Button
    private lateinit var barChartButton: Button
    private lateinit var lineChartButton: Button
    private lateinit var balanceSheetButton: Button
    private lateinit var chart: PieChart
    private lateinit var totalAssets: TextView
    private lateinit var totalLiabilities: TextView
    private lateinit var netWorth: TextView
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var assetsBalance: Money
    private lateinit var liabilitiesBalance: Money
    private var chartHasData = false

    override val layoutResource = R.layout.fragment_report_summary
    override val title = R.string.title_reports
    override val reportType = ReportType.NONE

    override fun requiresAccountTypeOptions() = false

    override fun requiresTimeRangeOptions() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountsDbAdapter = AccountsDbAdapter.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        pieChartButton = view.findViewById(R.id.btn_pie_chart)
        barChartButton = view.findViewById(R.id.btn_bar_chart)
        lineChartButton = view.findViewById(R.id.btn_line_chart)
        balanceSheetButton = view.findViewById(R.id.btn_balance_sheet)
        chart = view.findViewById(R.id.pie_chart)
        totalAssets = view.findViewById(R.id.total_assets)
        totalLiabilities = view.findViewById(R.id.total_liabilities)
        netWorth = view.findViewById(R.id.net_worth)
        listOf(pieChartButton, barChartButton, lineChartButton, balanceSheetButton).forEach {
            it.setOnClickListener(::onClickChartTypeButton)
        }
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(false)
        chart.setCenterTextSize(PieChartFragment.CENTER_TEXT_SIZE)
        chart.setDescription("")
        chart.setDrawSliceText(false)
        chart.legend.apply {
            isEnabled = true
            isWordWrapEnabled = true
            form = LegendForm.CIRCLE
            position = LegendPosition.RIGHT_OF_CHART_CENTER
            textSize = LEGEND_TEXT_SIZE.toFloat()
        }
        setButtonTint(pieChartButton, tint(R.color.account_green))
        setButtonTint(barChartButton, tint(R.color.account_red))
        setButtonTint(lineChartButton, tint(R.color.account_blue))
        setButtonTint(balanceSheetButton, tint(R.color.account_purple))
    }

    private fun tint(color: Int) = ColorStateList(
        arrayOf(intArrayOf()),
        intArrayOf(ContextCompat.getColor(requireContext(), color))
    )

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    override fun generateReport() {
        val pieData = PieChartFragment.groupSmallerSlices(getData(), requireContext())
        if (pieData != null && pieData.yValCount != 0) {
            chart.data = pieData
            val sum = chart.data.yValueSum
            val total = resources.getString(R.string.label_chart_total)
            chart.centerText = String.format(
                PieChartFragment.TOTAL_VALUE_LABEL_PATTERN,
                total,
                sum,
                mCommodity.getSymbol()
            )
            chartHasData = true
        } else {
            chart.data = emptyData
            chart.centerText = resources.getString(R.string.label_chart_no_data)
            chart.legend.isEnabled = false
            chartHasData = false
        }

        val accountTypes = mutableListOf(AccountType.ASSET, AccountType.CASH, AccountType.BANK)
        assetsBalance = accountsDbAdapter.getAccountBalance(
            accountTypes,
            -1,
            System.currentTimeMillis()
        )
        accountTypes.clear()
        accountTypes.add(AccountType.LIABILITY)
        accountTypes.add(AccountType.CREDIT)
        liabilitiesBalance = accountsDbAdapter.getAccountBalance(
            accountTypes,
            -1,
            System.currentTimeMillis()
        )
    }

    private fun getData(): PieData {
        val dataSet = PieDataSet(null, "")
        val labels = mutableListOf<String>()
        val colors = mutableListOf<Int>()
        for (account in accountsDbAdapter.getSimpleAccountList()) {
            if (account.getAccountType() == AccountType.EXPENSE &&
                !account.isPlaceholderAccount() && account.getCommodity() == mCommodity
            ) {
                val start = LocalDate().minusMonths(2).dayOfMonth().withMinimumValue().toDate().time
                val end = LocalDate().plusDays(1).toDate().time
                val balance = accountsDbAdapter.getAccountsBalance(
                    Collections.singletonList(account.getUID()),
                    start,
                    end
                ).asDouble()
                if (balance > 0) {
                    dataSet.addEntry(Entry(balance.toFloat(), dataSet.entryCount))
                    colors.add(
                        if (account.getColor() != Account.DEFAULT_COLOR) account.getColor()
                        else ReportsActivity.COLORS[(dataSet.entryCount - 1) % ReportsActivity.COLORS.size]
                    )
                    labels.add(account.getName())
                }
            }
        }
        dataSet.colors = colors
        dataSet.sliceSpace = PieChartFragment.SPACE_BETWEEN_SLICES
        return PieData(labels, dataSet)
    }

    override fun displayReport() {
        if (chartHasData) {
            chart.animateXY(1800, 1800)
            chart.setTouchEnabled(true)
        } else {
            chart.setTouchEnabled(false)
        }
        chart.highlightValues(null)
        chart.invalidate()
        TransactionsActivity.displayBalance(totalAssets, assetsBalance)
        TransactionsActivity.displayBalance(totalLiabilities, liabilitiesBalance)
        TransactionsActivity.displayBalance(netWorth, assetsBalance.subtract(liabilitiesBalance))
    }

    private val emptyData: PieData
        get() {
            val dataSet = PieDataSet(null, resources.getString(R.string.label_chart_no_data))
            dataSet.addEntry(Entry(1f, 0))
            dataSet.color = NO_DATA_COLOR
            dataSet.setDrawValues(false)
            return PieData(Collections.singletonList(""), dataSet)
        }

    fun onClickChartTypeButton(view: View) {
        val fragment = when (view.id) {
            R.id.btn_pie_chart -> PieChartFragment()
            R.id.btn_bar_chart -> StackedBarChartFragment()
            R.id.btn_line_chart -> CashFlowLineChartFragment()
            R.id.btn_balance_sheet -> BalanceSheetFragment()
            else -> this
        }
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun setButtonTint(button: Button, tint: ColorStateList) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP && button is AppCompatButton) {
            button.supportBackgroundTintList = tint
        } else {
            ViewCompat.setBackgroundTintList(button, tint)
        }
        button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
    }

    companion object {
        const val LEGEND_TEXT_SIZE = 14
    }
}
