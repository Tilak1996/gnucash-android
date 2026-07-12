/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report.barchart

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.LargeValueFormatter
import org.gnucash.android.R
import org.gnucash.android.model.data.Account
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.TransactionsDbAdapter
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.report.ReportsActivity
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlin.math.abs

/** Fragment used for drawing a stacked bar chart. */
class StackedBarChartFragment : BaseReportFragment() {
    private val accountsDbAdapter = AccountsDbAdapter.getInstance()
    private lateinit var chart: BarChart
    private var useAccountColor = true
    private var totalPercentageMode = true
    private var chartDataPresent = true

    override val title = R.string.title_cash_flow_report
    override val layoutResource = R.layout.fragment_bar_chart
    override val reportType = ReportType.BAR_CHART

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        chart = view.findViewById(R.id.bar_chart)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        useAccountColor = PreferenceManager.getDefaultSharedPreferences(requireActivity())
            .getBoolean(getString(R.string.key_use_account_color), false)
        chart.setOnChartValueSelectedListener(this)
        chart.setDescription("")
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.setStartAtZero(false)
        chart.axisLeft.enableGridDashedLine(4f, 4f, 0f)
        chart.axisLeft.valueFormatter = LargeValueFormatter(mCommodity.getSymbol())
        chart.legend.apply {
            form = Legend.LegendForm.CIRCLE
            position = Legend.LegendPosition.BELOW_CHART_CENTER
            isWordWrapEnabled = true
        }
    }

    private fun getData(): BarData {
        val values = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val colors = mutableListOf<Int>()
        val accountColors = LinkedHashMap<String, Int>()
        val xValues = mutableListOf<String>()
        var temporaryDate = LocalDateTime(getStartDate(mAccountType).toDate().time)
        val count = getDateDiff(
            LocalDateTime(getStartDate(mAccountType).toDate().time),
            LocalDateTime(getEndDate(mAccountType).toDate().time)
        )

        for (index in 0..count) {
            var start = 0L
            var end = 0L
            when (mGroupInterval) {
                ReportsActivity.GroupInterval.MONTH -> {
                    start = temporaryDate.dayOfMonth().withMinimumValue().millisOfDay()
                        .withMinimumValue().toDate().time
                    end = temporaryDate.dayOfMonth().withMaximumValue().millisOfDay()
                        .withMaximumValue().toDate().time
                    xValues.add(temporaryDate.toString(X_AXIS_MONTH_PATTERN))
                    temporaryDate = temporaryDate.plusMonths(1)
                }
                ReportsActivity.GroupInterval.QUARTER -> {
                    val quarter = getQuarter(temporaryDate)
                    start = temporaryDate.withMonthOfYear(quarter * 3 - 2).dayOfMonth()
                        .withMinimumValue().millisOfDay().withMinimumValue().toDate().time
                    end = temporaryDate.withMonthOfYear(quarter * 3).dayOfMonth()
                        .withMaximumValue().millisOfDay().withMaximumValue().toDate().time
                    xValues.add(String.format(X_AXIS_QUARTER_PATTERN, quarter, temporaryDate.toString(" YY")))
                    temporaryDate = temporaryDate.plusMonths(3)
                }
                ReportsActivity.GroupInterval.YEAR -> {
                    start = temporaryDate.dayOfYear().withMinimumValue().millisOfDay()
                        .withMinimumValue().toDate().time
                    end = temporaryDate.dayOfYear().withMaximumValue().millisOfDay()
                        .withMaximumValue().toDate().time
                    xValues.add(temporaryDate.toString(X_AXIS_YEAR_PATTERN))
                    temporaryDate = temporaryDate.plusYears(1)
                }
                else -> Unit
            }

            val stack = mutableListOf<Float>()
            for (account in accountsDbAdapter.getSimpleAccountList()) {
                if (account.getAccountType() == mAccountType &&
                    !account.isPlaceholderAccount() && account.getCommodity() == mCommodity
                ) {
                    val balance = accountsDbAdapter.getAccountsBalance(
                        Collections.singletonList(account.getUID()), start, end
                    ).asDouble()
                    if (balance != 0.0) {
                        stack.add(balance.toFloat())
                        var accountName = account.getName()
                        while (labels.contains(accountName)) {
                            if (!accountColors.containsKey(account.getUID())) {
                                labels.forEach { if (it == accountName) accountName += " " }
                            } else break
                        }
                        labels.add(accountName)
                        if (!accountColors.containsKey(account.getUID())) {
                            val fallback = ReportsActivity.COLORS[
                                accountColors.size % ReportsActivity.COLORS.size
                            ]
                            val color = if (useAccountColor && account.getColor() != Account.DEFAULT_COLOR)
                                account.getColor() else fallback
                            accountColors[account.getUID()] = color
                        }
                        colors.add(accountColors.getValue(account.getUID()))
                        Log.d(
                            TAG,
                            "$mAccountType${temporaryDate.toString(" MMMM yyyy ")}${account.getName()} = ${stack.last()}"
                        )
                    }
                }
            }
            val stackLabels = labels.subList(labels.size - stack.size, labels.size).toString()
            values.add(BarEntry(stack.toFloatArray(), index, stackLabels))
        }

        val dataSet = BarDataSet(values, "")
        dataSet.setDrawValues(false)
        dataSet.setStackLabels(labels.toTypedArray())
        dataSet.colors = colors
        if (dataSet.yValueSum == 0f) {
            chartDataPresent = false
            return emptyData
        }
        chartDataPresent = true
        return BarData(xValues, dataSet)
    }

    private val emptyData: BarData
        get() {
            val xValues = MutableList(NO_DATA_BAR_COUNTS) { "" }
            val yValues = MutableList(NO_DATA_BAR_COUNTS) { BarEntry((it + 1).toFloat(), it) }
            val dataSet = BarDataSet(yValues, resources.getString(R.string.label_chart_no_data))
            dataSet.setDrawValues(false)
            dataSet.color = NO_DATA_COLOR
            return BarData(xValues, dataSet)
        }

    private fun getStartDate(accountType: AccountType): LocalDate {
        val adapter = TransactionsDbAdapter.getInstance()
        val date = if (mReportPeriodStart == -1L) {
            LocalDate(adapter.getTimestampOfEarliestTransaction(accountType, mCommodity.getCurrencyCode()))
        } else LocalDate(mReportPeriodStart)
        return date.withDayOfMonth(1).also {
            Log.d(TAG, "$accountType X-axis star date: ${it.toString("dd MM yyyy")}")
        }
    }

    private fun getEndDate(accountType: AccountType): LocalDate {
        val adapter = TransactionsDbAdapter.getInstance()
        val date = if (mReportPeriodEnd == -1L) {
            LocalDate(adapter.getTimestampOfLatestTransaction(accountType, mCommodity.getCurrencyCode()))
        } else LocalDate(mReportPeriodEnd)
        return date.withDayOfMonth(1).also {
            Log.d(TAG, "$accountType X-axis end date: ${it.toString("dd MM yyyy")}")
        }
    }

    override fun generateReport() {
        chart.data = getData()
        setCustomLegend()
        chart.axisLeft.setDrawLabels(chartDataPresent)
        chart.xAxis.setDrawLabels(chartDataPresent)
        chart.setTouchEnabled(chartDataPresent)
    }

    override fun displayReport() {
        chart.notifyDataSetChanged()
        chart.highlightValues(null)
        if (chartDataPresent) chart.animateY(ANIMATION_DURATION)
        else {
            chart.clearAnimation()
            mSelectedValueTextView.setText(R.string.label_chart_no_data)
        }
        chart.invalidate()
    }

    private fun setCustomLegend() {
        val dataSet = chart.data.getDataSetByIndex(0) as BarDataSet
        val labels = LinkedHashSet(dataSet.stackLabels.asList())
        val colors = LinkedHashSet(dataSet.colors)
        if (ReportsActivity.COLORS.size >= labels.size) {
            chart.legend.setCustom(ArrayList(colors), ArrayList(labels))
        } else chart.legend.isEnabled = false
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_percentage_mode).isVisible = chartDataPresent
        menu.findItem(R.id.menu_order_by_size).isVisible = false
        menu.findItem(R.id.menu_toggle_labels).isVisible = false
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = false
        menu.findItem(R.id.menu_group_other_slice).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        return when (item.itemId) {
            R.id.menu_toggle_legend -> {
                val legend = chart.legend
                if (!legend.isLegendCustom) {
                    Toast.makeText(activity, R.string.toast_legend_too_long, Toast.LENGTH_LONG).show()
                    item.isChecked = false
                } else {
                    item.isChecked = !legend.isEnabled
                    legend.isEnabled = !legend.isEnabled
                    chart.invalidate()
                }
                true
            }
            R.id.menu_percentage_mode -> {
                totalPercentageMode = !totalPercentageMode
                val message = if (totalPercentageMode) R.string.toast_chart_percentage_mode_total
                else R.string.toast_chart_percentage_mode_current_bar
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(entry: Entry?, dataSetIndex: Int, highlight: Highlight?) {
        val barEntry = entry as? BarEntry ?: return
        if (barEntry.vals.isEmpty()) return
        val stackIndex = if (highlight?.stackIndex == -1) 0 else highlight?.stackIndex ?: 0
        val stackLabels = barEntry.data.toString()
        val label = chart.data.xVals[barEntry.xIndex] + ", " +
            stackLabels.substring(1, stackLabels.length - 1).split(",")[stackIndex]
        val value = abs(barEntry.vals[stackIndex].toDouble())
        val sum = if (totalPercentageMode) {
            chart.data.getDataSetByIndex(dataSetIndex).yVals.sumOf {
                val valueEntry = it as BarEntry
                (valueEntry.negativeSum + valueEntry.positiveSum).toDouble()
            }
        } else (barEntry.negativeSum + barEntry.positiveSum).toDouble()
        mSelectedValueTextView.text = String.format(
            SELECTED_VALUE_PATTERN,
            label.trim(),
            value,
            value / sum * 100
        )
    }

    companion object {
        private const val X_AXIS_MONTH_PATTERN = "MMM YY"
        private const val X_AXIS_QUARTER_PATTERN = "Q%d %s"
        private const val X_AXIS_YEAR_PATTERN = "YYYY"
        private const val ANIMATION_DURATION = 2000
        private const val NO_DATA_BAR_COUNTS = 3
    }
}
