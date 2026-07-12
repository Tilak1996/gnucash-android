/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report.linechart

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.LargeValueFormatter
import org.gnucash.android.R
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.TransactionsDbAdapter
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.util.Collections

/** Fragment for cash-flow line chart reports. */
class CashFlowLineChartFragment : BaseReportFragment() {
    private val accountsDbAdapter = AccountsDbAdapter.getInstance()
    private val earliestTimestamps = mutableMapOf<AccountType, Long>()
    private val latestTimestamps = mutableMapOf<AccountType, Long>()
    private var earliestTransactionTimestamp = 0L
    private var latestTransactionTimestamp = 0L
    private var chartDataPresent = true
    private lateinit var chart: LineChart

    override val layoutResource = R.layout.fragment_line_chart
    override val title = R.string.title_cash_flow_report
    override val reportType = ReportType.LINE_CHART

    override fun requiresAccountTypeOptions() = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        chart = view.findViewById(R.id.line_chart)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        chart.setOnChartValueSelectedListener(this)
        chart.setDescription("")
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.enableGridDashedLine(4f, 4f, 0f)
        chart.axisLeft.valueFormatter = LargeValueFormatter(mCommodity.getSymbol())
        chart.legend.apply {
            position = Legend.LegendPosition.BELOW_CHART_CENTER
            textSize = 16f
            form = Legend.LegendForm.CIRCLE
        }
    }

    private fun getData(accountTypes: MutableList<AccountType>): LineData {
        Log.w(TAG, "getData")
        calculateEarliestAndLatestTimestamps(accountTypes)
        var startDate: LocalDate
        val endDate: LocalDate
        if (mReportPeriodStart == -1L && mReportPeriodEnd == -1L) {
            startDate = LocalDate(earliestTransactionTimestamp).withDayOfMonth(1)
            endDate = LocalDate(latestTransactionTimestamp).withDayOfMonth(1)
        } else {
            startDate = LocalDate(mReportPeriodStart).withDayOfMonth(1)
            endDate = LocalDate(mReportPeriodEnd).withDayOfMonth(1)
        }

        val count = getDateDiff(
            LocalDateTime(startDate.toDate().time),
            LocalDateTime(endDate.toDate().time)
        )
        Log.d(TAG, "X-axis count$count")
        val xValues = mutableListOf<String>()
        for (index in 0..count) {
            when (mGroupInterval) {
                GroupInterval.MONTH -> {
                    xValues.add(startDate.toString(X_AXIS_PATTERN))
                    Log.d(TAG, "X-axis ${startDate.toString("MM yy")}")
                    startDate = startDate.plusMonths(1)
                }
                GroupInterval.QUARTER -> {
                    val quarter = getQuarter(LocalDateTime(startDate.toDate().time))
                    xValues.add("Q$quarter${startDate.toString(" yy")}")
                    Log.d(TAG, "X-axis Q$quarter${startDate.toString(" MM yy")}")
                    startDate = startDate.plusMonths(3)
                }
                GroupInterval.YEAR -> {
                    xValues.add(startDate.toString("yyyy"))
                    Log.d(TAG, "X-axis ${startDate.toString("yyyy")}")
                    startDate = startDate.plusYears(1)
                }
                else -> Unit
            }
        }

        val dataSets = mutableListOf<LineDataSet>()
        for (accountType in accountTypes) {
            val dataSet = LineDataSet(getEntryList(accountType), accountType.toString())
            dataSet.setDrawFilled(true)
            dataSet.lineWidth = 2f
            dataSet.color = COLORS[dataSets.size]
            dataSet.fillColor = FILL_COLORS[dataSets.size]
            dataSets.add(dataSet)
        }
        val lineData = LineData(xValues, dataSets)
        if (lineData.yValueSum == 0f) {
            chartDataPresent = false
            return emptyData
        }
        return lineData
    }

    private val emptyData: LineData
        get() {
            val xValues = MutableList(NO_DATA_BAR_COUNTS) { "" }
            val yValues = MutableList(NO_DATA_BAR_COUNTS) {
                Entry(if (it % 2 == 0) 5f else 4.5f, it)
            }
            val dataSet = LineDataSet(yValues, resources.getString(R.string.label_chart_no_data))
            dataSet.setDrawFilled(true)
            dataSet.setDrawValues(false)
            dataSet.color = NO_DATA_COLOR
            dataSet.fillColor = NO_DATA_COLOR
            return LineData(xValues, Collections.singletonList(dataSet))
        }

    private fun getEntryList(accountType: AccountType): List<Entry> {
        val accountUIDs = mutableListOf<String>()
        for (account in accountsDbAdapter.getSimpleAccountList()) {
            if (account.getAccountType() == accountType &&
                !account.isPlaceholderAccount() && account.getCommodity() == mCommodity
            ) accountUIDs.add(account.getUID())
        }

        var earliest: LocalDateTime
        val latest: LocalDateTime
        if (mReportPeriodStart == -1L && mReportPeriodEnd == -1L) {
            earliest = LocalDateTime(earliestTimestamps.getValue(accountType))
            latest = LocalDateTime(latestTimestamps.getValue(accountType))
        } else {
            earliest = LocalDateTime(mReportPeriodStart)
            latest = LocalDateTime(mReportPeriodEnd)
        }
        Log.d(TAG, "Earliest $accountType date ${earliest.toString("dd MM yyyy")}")
        Log.d(TAG, "Latest $accountType date ${latest.toString("dd MM yyyy")}")

        val xAxisOffset = getDateDiff(LocalDateTime(earliestTransactionTimestamp), earliest)
        val count = getDateDiff(earliest, latest)
        val values = ArrayList<Entry>(count + 1)
        for (index in 0..count) {
            var start = 0L
            var end = 0L
            when (mGroupInterval) {
                GroupInterval.QUARTER -> {
                    val quarter = getQuarter(earliest)
                    start = earliest.withMonthOfYear(quarter * 3 - 2).dayOfMonth()
                        .withMinimumValue().millisOfDay().withMinimumValue().toDate().time
                    end = earliest.withMonthOfYear(quarter * 3).dayOfMonth()
                        .withMaximumValue().millisOfDay().withMaximumValue().toDate().time
                    earliest = earliest.plusMonths(3)
                }
                GroupInterval.MONTH -> {
                    start = earliest.dayOfMonth().withMinimumValue().millisOfDay()
                        .withMinimumValue().toDate().time
                    end = earliest.dayOfMonth().withMaximumValue().millisOfDay()
                        .withMaximumValue().toDate().time
                    earliest = earliest.plusMonths(1)
                }
                GroupInterval.YEAR -> {
                    start = earliest.dayOfYear().withMinimumValue().millisOfDay()
                        .withMinimumValue().toDate().time
                    end = earliest.dayOfYear().withMaximumValue().millisOfDay()
                        .withMaximumValue().toDate().time
                    earliest = earliest.plusYears(1)
                }
                else -> Unit
            }
            val balance = accountsDbAdapter.getAccountsBalance(accountUIDs, start, end)
                .asDouble().toFloat()
            values.add(Entry(balance, index + xAxisOffset))
            Log.d(TAG, "$accountType${earliest.toString(" MMM yyyy")}, balance = $balance")
        }
        return values
    }

    private fun calculateEarliestAndLatestTimestamps(accountTypes: MutableList<AccountType>) {
        if (mReportPeriodStart != -1L && mReportPeriodEnd != -1L) {
            earliestTransactionTimestamp = mReportPeriodStart
            latestTransactionTimestamp = mReportPeriodEnd
            return
        }
        val adapter = TransactionsDbAdapter.getInstance()
        val iterator = accountTypes.iterator()
        while (iterator.hasNext()) {
            val type = iterator.next()
            val earliest = adapter.getTimestampOfEarliestTransaction(type, mCommodity.getCurrencyCode())
            val latest = adapter.getTimestampOfLatestTransaction(type, mCommodity.getCurrencyCode())
            if (earliest > 0 && latest > 0) {
                earliestTimestamps[type] = earliest
                latestTimestamps[type] = latest
            } else iterator.remove()
        }
        if (earliestTimestamps.isEmpty() || latestTimestamps.isEmpty()) return
        val timestamps = (earliestTimestamps.values + latestTimestamps.values).sorted()
        earliestTransactionTimestamp = timestamps.first()
        latestTransactionTimestamp = timestamps.last()
    }

    override fun generateReport() {
        val lineData = getData(mutableListOf(AccountType.INCOME, AccountType.EXPENSE))
        chart.data = lineData
        chartDataPresent = true
    }

    override fun displayReport() {
        if (!chartDataPresent) {
            chart.axisLeft.axisMaxValue = 10f
            chart.axisLeft.setDrawLabels(false)
            chart.xAxis.setDrawLabels(false)
            chart.setTouchEnabled(false)
            mSelectedValueTextView.text = resources.getString(R.string.label_chart_no_data)
        } else chart.animateX(ANIMATION_DURATION)
        chart.invalidate()
    }

    override fun onTimeRangeUpdated(start: Long, end: Long) {
        if (mReportPeriodStart != start || mReportPeriodEnd != end) {
            mReportPeriodStart = start
            mReportPeriodEnd = end
            chart.data = getData(mutableListOf(AccountType.INCOME, AccountType.EXPENSE))
            chart.invalidate()
        }
    }

    override fun onGroupingUpdated(groupInterval: GroupInterval) {
        if (mGroupInterval != groupInterval) {
            mGroupInterval = groupInterval
            chart.data = getData(mutableListOf(AccountType.INCOME, AccountType.EXPENSE))
            chart.invalidate()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = chartDataPresent
        menu.findItem(R.id.menu_order_by_size).isVisible = false
        menu.findItem(R.id.menu_toggle_labels).isVisible = false
        menu.findItem(R.id.menu_percentage_mode).isVisible = false
        menu.findItem(R.id.menu_group_other_slice).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        return when (item.itemId) {
            R.id.menu_toggle_legend -> {
                chart.legend.isEnabled = !chart.legend.isEnabled
                chart.invalidate()
                true
            }
            R.id.menu_toggle_average_lines -> {
                if (chart.axisLeft.limitLines.isEmpty()) {
                    for (dataSet in chart.data.dataSets) {
                        val line = LimitLine(
                            dataSet.yValueSum / dataSet.entryCount,
                            dataSet.label
                        )
                        line.enableDashedLine(10f, 5f, 0f)
                        line.lineColor = dataSet.color
                        chart.axisLeft.addLimitLine(line)
                    }
                } else chart.axisLeft.removeAllLimitLines()
                chart.invalidate()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(entry: Entry?, dataSetIndex: Int, highlight: Highlight?) {
        entry ?: return
        val label = chart.data.xVals[entry.xIndex]
        val value = entry.`val`.toDouble()
        val sum = chart.data.getDataSetByIndex(dataSetIndex).yValueSum.toDouble()
        mSelectedValueTextView.text = String.format(
            SELECTED_VALUE_PATTERN,
            label,
            value,
            value / sum * 100
        )
    }

    companion object {
        private const val X_AXIS_PATTERN = "MMM YY"
        private const val ANIMATION_DURATION = 3000
        private const val NO_DATA_BAR_COUNTS = 5
        private val COLORS = intArrayOf(
            Color.parseColor("#68F1AF"), Color.parseColor("#cc1f09"),
            Color.parseColor("#EE8600"), Color.parseColor("#1469EB"),
            Color.parseColor("#B304AD")
        )
        private val FILL_COLORS = intArrayOf(
            Color.parseColor("#008000"), Color.parseColor("#FF0000"),
            Color.parseColor("#BE6B00"), Color.parseColor("#0065FF"),
            Color.parseColor("#8F038A")
        )
    }
}
