/*
 * Copyright (c) 2014-2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report.piechart

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.Legend.LegendPosition
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.highlight.Highlight
import org.gnucash.android.R
import org.gnucash.android.model.data.Account
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.report.ReportsActivity
import java.util.Collections

/** Fragment used for drawing a pie chart. */
class PieChartFragment : BaseReportFragment() {
    private lateinit var chart: PieChart
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private var chartDataPresent = true
    private var useAccountColor = true
    private var groupSmallerSlices = true

    override val title = R.string.title_pie_chart
    override val reportType = ReportType.PIE_CHART
    override val layoutResource = R.layout.fragment_pie_chart

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        chart = view.findViewById(R.id.pie_chart)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        useAccountColor = PreferenceManager.getDefaultSharedPreferences(requireActivity())
            .getBoolean(getString(R.string.key_use_account_color), false)
        accountsDbAdapter = AccountsDbAdapter.getInstance()
        chart.setCenterTextSize(CENTER_TEXT_SIZE)
        chart.setDescription("")
        chart.setOnChartValueSelectedListener(this)
        chart.legend.apply {
            form = LegendForm.CIRCLE
            isWordWrapEnabled = true
            position = LegendPosition.BELOW_CHART_CENTER
        }
    }

    override fun generateReport() {
        val pieData = data
        if (pieData.yValCount != 0) {
            chartDataPresent = true
            chart.data = if (groupSmallerSlices) groupSmallerSlices(pieData, requireContext()) else pieData
            chart.centerText = String.format(
                TOTAL_VALUE_LABEL_PATTERN,
                resources.getString(R.string.label_chart_total),
                chart.data.yValueSum,
                mCommodity.getSymbol()
            )
        } else {
            chartDataPresent = false
            chart.centerText = resources.getString(R.string.label_chart_no_data)
            chart.data = emptyData
        }
    }

    override fun displayReport() {
        if (chartDataPresent) chart.animateXY(ANIMATION_DURATION, ANIMATION_DURATION)
        mSelectedValueTextView.setText(R.string.label_select_pie_slice_to_see_details)
        chart.setTouchEnabled(chartDataPresent)
        chart.highlightValues(null)
        chart.invalidate()
    }

    private val data: PieData
        get() {
            val dataSet = PieDataSet(null, "")
            val labels = mutableListOf<String>()
            val colors = mutableListOf<Int>()
            for (account in accountsDbAdapter.getSimpleAccountList()) {
                if (account.getAccountType() == mAccountType &&
                    !account.isPlaceholderAccount() && account.getCommodity() == mCommodity
                ) {
                    val balance = accountsDbAdapter.getAccountsBalance(
                        Collections.singletonList(account.getUID()),
                        mReportPeriodStart,
                        mReportPeriodEnd
                    ).asDouble()
                    if (balance > 0) {
                        dataSet.addEntry(Entry(balance.toFloat(), dataSet.entryCount))
                        val fallback = ReportsActivity.COLORS[
                            (dataSet.entryCount - 1) % ReportsActivity.COLORS.size
                        ]
                        colors.add(
                            if (useAccountColor && account.getColor() != Account.DEFAULT_COLOR)
                                account.getColor() else fallback
                        )
                        labels.add(account.getName())
                    }
                }
            }
            dataSet.colors = colors
            dataSet.sliceSpace = SPACE_BETWEEN_SLICES
            return PieData(labels, dataSet)
        }

    private val emptyData: PieData
        get() {
            val dataSet = PieDataSet(null, resources.getString(R.string.label_chart_no_data))
            dataSet.addEntry(Entry(1f, 0))
            dataSet.color = NO_DATA_COLOR
            dataSet.setDrawValues(false)
            return PieData(Collections.singletonList(""), dataSet)
        }

    private fun bubbleSort() {
        val labels = chart.data.xVals
        val values = chart.data.dataSet.yVals
        val colors = chart.data.dataSet.colors
        for (i in 0 until values.size - 1) {
            for (j in 1 until values.size - i) {
                if (values[j - 1].`val` > values[j].`val`) {
                    val value = values[j - 1].`val`
                    values[j - 1].`val` = values[j].`val`
                    values[j].`val` = value

                    val label = labels[j - 1]
                    labels[j - 1] = labels[j]
                    labels[j] = label

                    val color = colors[j - 1]
                    colors[j - 1] = colors[j]
                    colors[j] = color
                }
            }
        }
        chart.notifyDataSetChanged()
        chart.highlightValues(null)
        chart.invalidate()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_order_by_size).isVisible = chartDataPresent
        menu.findItem(R.id.menu_toggle_labels).isVisible = chartDataPresent
        menu.findItem(R.id.menu_group_other_slice).isVisible = chartDataPresent
        menu.findItem(R.id.menu_percentage_mode).isVisible = false
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = false
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        return when (item.itemId) {
            R.id.menu_order_by_size -> {
                bubbleSort()
                true
            }
            R.id.menu_toggle_legend -> {
                chart.legend.isEnabled = !chart.legend.isEnabled
                chart.notifyDataSetChanged()
                chart.invalidate()
                true
            }
            R.id.menu_toggle_labels -> {
                chart.data.setDrawValues(!chart.isDrawSliceTextEnabled)
                chart.setDrawSliceText(!chart.isDrawSliceTextEnabled)
                chart.invalidate()
                true
            }
            R.id.menu_group_other_slice -> {
                groupSmallerSlices = !groupSmallerSlices
                refresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(entry: Entry?, dataSetIndex: Int, highlight: Highlight?) {
        entry ?: return
        val label = chart.data.xVals[entry.xIndex]
        val value = entry.`val`
        val percent = value / chart.data.yValueSum * 100
        mSelectedValueTextView.text = String.format(
            SELECTED_VALUE_PATTERN,
            label,
            value,
            percent
        )
    }

    companion object {
        const val TOTAL_VALUE_LABEL_PATTERN = "%s\n%.2f %s"
        const val CENTER_TEXT_SIZE = 18f
        const val SPACE_BETWEEN_SLICES = 2f
        private const val ANIMATION_DURATION = 1800
        private const val GROUPING_SMALLER_SLICES_THRESHOLD = 5.0

        /** Combines slices below the percentage threshold into a single “Other” slice. */
        @JvmStatic
        fun groupSmallerSlices(data: PieData, context: Context): PieData {
            var otherSlice = 0f
            val newEntries = mutableListOf<Entry>()
            val newLabels = mutableListOf<String>()
            val newColors = mutableListOf<Int>()
            val entries = data.dataSet.yVals
            for (index in entries.indices) {
                val value = entries[index].`val`
                if (value / data.yValueSum * 100 > GROUPING_SMALLER_SLICES_THRESHOLD) {
                    newEntries.add(Entry(value, newEntries.size))
                    newLabels.add(data.xVals[index])
                    newColors.add(data.dataSet.colors[index])
                } else otherSlice += value
            }
            if (otherSlice > 0) {
                newEntries.add(Entry(otherSlice, newEntries.size))
                newLabels.add(context.resources.getString(R.string.label_other_slice))
                newColors.add(Color.LTGRAY)
            }
            val dataSet = PieDataSet(newEntries, "")
            dataSet.sliceSpace = SPACE_BETWEEN_SLICES
            dataSet.colors = newColors
            return PieData(newLabels, dataSet)
        }
    }
}
