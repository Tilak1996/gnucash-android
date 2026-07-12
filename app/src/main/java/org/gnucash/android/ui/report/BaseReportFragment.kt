/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report

import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.data.Commodity
import org.gnucash.android.model.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.ui.common.Refreshable
import org.joda.time.LocalDateTime
import org.joda.time.Months
import org.joda.time.Years

/** Base class for report fragments. */
abstract class BaseReportFragment : Fragment(), OnChartValueSelectedListener,
    ReportOptionsListener, Refreshable {

    protected var mReportPeriodStart = -1L
    protected var mReportPeriodEnd = -1L
    protected lateinit var mAccountType: AccountType
    protected lateinit var mCommodity: Commodity
    protected var mGroupInterval = ReportsActivity.GroupInterval.MONTH
    protected lateinit var mReportsActivity: ReportsActivity
    protected lateinit var mSelectedValueTextView: TextView
    private var mReportGenerator: AsyncTask<Void, Void, Void>? = null

    @get:StringRes
    abstract val title: Int

    @get:LayoutRes
    abstract val layoutResource: Int

    abstract val reportType: ReportType

    open fun requiresAccountTypeOptions() = true

    open fun requiresTimeRangeOptions() = true

    protected abstract fun generateReport()

    protected abstract fun displayReport()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TAG = javaClass.simpleName
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(layoutResource, container, false)
        mSelectedValueTextView = view.findViewById(R.id.selected_chart_slice)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        checkNotNull((requireActivity() as AppCompatActivity).supportActionBar).setTitle(title)
        setHasOptionsMenu(true)
        mCommodity = CommoditiesDbAdapter.getInstance()
            .getCommodity(GnuCashApplication.getDefaultCurrencyCode())
        mReportsActivity = requireActivity() as ReportsActivity
        mReportPeriodStart = mReportsActivity.getReportPeriodStart()
        mReportPeriodEnd = mReportsActivity.getReportPeriodEnd()
        mAccountType = mReportsActivity.getAccountType()
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        mReportsActivity.setAppBarColor(reportType.titleColor)
        mReportsActivity.toggleToolbarTitleVisibility()
        toggleBaseReportingOptionsVisibility()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mReportsActivity = activity as? ReportsActivity
            ?: throw RuntimeException("Report fragments can only be used with the ReportsActivity")
    }

    override fun onDetach() {
        mReportGenerator?.cancel(true)
        super.onDetach()
    }

    private fun toggleBaseReportingOptionsVisibility() {
        val visibility = if (requiresTimeRangeOptions()) View.VISIBLE else View.GONE
        mReportsActivity.findViewById<View?>(R.id.time_range_layout)?.visibility = visibility
        mReportsActivity.findViewById<View?>(R.id.date_range_divider)?.visibility = visibility
        mReportsActivity.findViewById<View>(R.id.report_account_type_spinner).visibility =
            if (requiresAccountTypeOptions()) View.VISIBLE else View.GONE
    }

    protected fun getDateDiff(start: LocalDateTime, end: LocalDateTime): Int =
        when (mGroupInterval) {
            ReportsActivity.GroupInterval.QUARTER -> {
                val years = Years.yearsBetween(
                    start.withDayOfYear(1).withMillisOfDay(0),
                    end.withDayOfYear(1).withMillisOfDay(0)
                ).years
                getQuarter(end) - getQuarter(start) + years * 4
            }
            ReportsActivity.GroupInterval.MONTH -> Months.monthsBetween(
                start.withDayOfMonth(1).withMillisOfDay(0),
                end.withDayOfMonth(1).withMillisOfDay(0)
            ).months
            ReportsActivity.GroupInterval.YEAR -> Years.yearsBetween(
                start.withDayOfYear(1).withMillisOfDay(0),
                end.withDayOfYear(1).withMillisOfDay(0)
            ).years
            else -> 0
        }

    protected fun getQuarter(date: LocalDateTime): Int = (date.monthOfYear - 1) / 3 + 1

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chart_actions, menu)
    }

    override fun refresh() {
        mReportGenerator?.cancel(true)
        mReportGenerator = object : AsyncTask<Void, Void, Void>() {
            override fun onPreExecute() {
                mReportsActivity.getProgressBar().visibility = View.VISIBLE
            }

            override fun doInBackground(vararg params: Void?): Void? {
                generateReport()
                return null
            }

            override fun onPostExecute(result: Void?) {
                displayReport()
                mReportsActivity.getProgressBar().visibility = View.GONE
            }
        }.also { it.execute() }
    }

    override fun refresh(uid: String?) = refresh()

    override fun onGroupingUpdated(groupInterval: ReportsActivity.GroupInterval) {
        if (mGroupInterval != groupInterval) {
            mGroupInterval = groupInterval
            refresh()
        }
    }

    override fun onTimeRangeUpdated(start: Long, end: Long) {
        if (mReportPeriodStart != start || mReportPeriodEnd != end) {
            mReportPeriodStart = start
            mReportPeriodEnd = end
            refresh()
        }
    }

    override fun onAccountTypeUpdated(accountType: AccountType) {
        if (mAccountType != accountType) {
            mAccountType = accountType
            refresh()
        }
    }

    override fun onValueSelected(entry: Entry?, dataSetIndex: Int, highlight: Highlight?) = Unit

    override fun onNothingSelected() {
        mSelectedValueTextView.setText(R.string.select_chart_to_view_details)
    }

    companion object {
        const val NO_DATA_COLOR: Int = Color.LTGRAY
        const val SELECTED_VALUE_PATTERN: String = "%s - %.2f (%.2f %%)"
        @JvmStatic protected var TAG: String = "BaseReportFragment"
    }
}
