/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.db.adapter.TransactionsDbAdapter
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment
import org.joda.time.LocalDate
import java.util.Calendar
import java.util.Date

/** Activity for displaying report fragments. */
class ReportsActivity : BaseDrawerActivity(), AdapterView.OnItemSelectedListener,
    DatePickerDialog.OnDateSetListener,
    DateRangePickerDialogFragment.OnDateRangeSetListener,
    Refreshable {

    private lateinit var timeRangeSpinner: Spinner
    private lateinit var accountTypeSpinner: Spinner
    private lateinit var reportTypeSpinner: Spinner
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private var accountType = AccountType.EXPENSE
    private var reportType = ReportType.NONE
    private lateinit var reportsOverviewFragment: ReportsOverviewFragment

    enum class GroupInterval { WEEK, MONTH, QUARTER, YEAR, ALL }

    private var reportPeriodStart = LocalDate().minusMonths(2).dayOfMonth()
        .withMinimumValue().toDate().time
    private var reportPeriodEnd = LocalDate().plusDays(1).toDate().time
    private var reportGroupInterval = GroupInterval.MONTH
    private var skipNextReportTypeSelectedRun = false

    private val reportTypeSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (skipNextReportTypeSelectedRun) {
                skipNextReportTypeSelectedRun = false
            } else {
                val reportName = parent?.getItemAtPosition(position).toString()
                loadFragment(reportType.getFragment(reportName))
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    override fun getContentView() = R.layout.activity_reports

    override fun getTitleRes() = R.string.title_reports

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            @Suppress("DEPRECATION")
            reportType = savedInstanceState.getSerializable(STATE_REPORT_TYPE) as ReportType
        }
        super.onCreate(savedInstanceState)
        timeRangeSpinner = findViewById(R.id.time_range_spinner)
        accountTypeSpinner = findViewById(R.id.report_account_type_spinner)
        reportTypeSpinner = findViewById(R.id.toolbar_spinner)
        transactionsDbAdapter = TransactionsDbAdapter.getInstance()

        timeRangeSpinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.report_time_range, android.R.layout.simple_spinner_item
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        timeRangeSpinner.onItemSelectedListener = this
        timeRangeSpinner.setSelection(1)

        accountTypeSpinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.report_account_types, android.R.layout.simple_spinner_item
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        accountTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                accountType = if (position == 1) AccountType.INCOME else AccountType.EXPENSE
                updateAccountTypeOnFragments()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        reportsOverviewFragment = ReportsOverviewFragment()
        if (savedInstanceState == null) loadFragment(reportsOverviewFragment)
    }

    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is BaseReportFragment) {
            updateReportTypeSpinner(fragment.reportType, getString(fragment.title))
        }
    }

    private fun loadFragment(fragment: BaseReportFragment?) {
        if (fragment == null) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun updateReportTypeSpinner(type: ReportType, reportName: String) {
        if (type == reportType) return
        reportType = type
        val actionBar = checkNotNull(supportActionBar)
        val adapter = ArrayAdapter(
            actionBar.themedContext,
            android.R.layout.simple_list_item_1,
            reportType.reportNames
        )
        skipNextReportTypeSelectedRun = true
        reportTypeSpinner.adapter = adapter
        reportTypeSpinner.setSelection(adapter.getPosition(reportName))
        reportTypeSpinner.onItemSelectedListener = reportTypeSelectedListener
        toggleToolbarTitleVisibility()
    }

    fun toggleToolbarTitleVisibility() {
        reportTypeSpinner.visibility = if (reportType == ReportType.NONE) View.GONE else View.VISIBLE
        checkNotNull(supportActionBar).setDisplayShowTitleEnabled(reportType == ReportType.NONE)
    }

    fun setAppBarColor(color: Int) {
        val resolvedColor = ContextCompat.getColor(this, color)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(resolvedColor))
        if (Build.VERSION.SDK_INT > 20) window.statusBarColor = GnuCashApplication.darken(resolvedColor)
    }

    private inline fun <reified T> notifyReportFragments(action: (T) -> Unit) {
        supportFragmentManager.fragments.filterIsInstance<T>().forEach(action)
    }

    private fun updateDateRangeOnFragment() =
        notifyReportFragments<ReportOptionsListener> {
            it.onTimeRangeUpdated(reportPeriodStart, reportPeriodEnd)
        }

    private fun updateAccountTypeOnFragments() =
        notifyReportFragments<ReportOptionsListener> { it.onAccountTypeUpdated(accountType) }

    private fun updateGroupingOnFragments() =
        notifyReportFragments<ReportOptionsListener> { it.onGroupingUpdated(reportGroupInterval) }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.report_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_group_reports_by -> true
        R.id.group_by_month -> selectGrouping(item, GroupInterval.MONTH)
        R.id.group_by_quarter -> selectGrouping(item, GroupInterval.QUARTER)
        R.id.group_by_year -> selectGrouping(item, GroupInterval.YEAR)
        android.R.id.home -> super.onOptionsItemSelected(item)
        else -> false
    }

    private fun selectGrouping(item: MenuItem, interval: GroupInterval): Boolean {
        item.isChecked = true
        reportGroupInterval = interval
        updateGroupingOnFragments()
        return true
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        reportPeriodEnd = LocalDate().plusDays(1).toDate().time
        reportPeriodStart = when (position) {
            0 -> LocalDate().dayOfMonth().withMinimumValue().toDate().time
            1 -> LocalDate().minusMonths(2).dayOfMonth().withMinimumValue().toDate().time
            2 -> LocalDate().minusMonths(5).dayOfMonth().withMinimumValue().toDate().time
            3 -> LocalDate().minusMonths(11).dayOfMonth().withMinimumValue().toDate().time
            4 -> {
                reportPeriodEnd = -1
                -1
            }
            5 -> {
                val earliest = transactionsDbAdapter.getTimestampOfEarliestTransaction(
                    accountType, GnuCashApplication.getDefaultCurrencyCode()
                )
                DateRangePickerDialogFragment.newInstance(
                    earliest, LocalDate().plusDays(1).toDate().time, this
                ).show(supportFragmentManager, "range_dialog")
                reportPeriodStart
            }
            else -> reportPeriodStart
        }
        if (position != 5) updateDateRangeOnFragment()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    override fun onDateSet(view: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        reportPeriodStart = Calendar.getInstance().apply {
            set(year, monthOfYear, dayOfMonth)
        }.timeInMillis
        updateDateRangeOnFragment()
    }

    override fun onDateRangeSet(startDate: Date?, endDate: Date?) {
        reportPeriodStart = requireNotNull(startDate).time
        reportPeriodEnd = requireNotNull(endDate).time
        updateDateRangeOnFragment()
    }

    fun getAccountType(): AccountType = accountType

    fun getReportPeriodEnd(): Long = reportPeriodEnd

    fun getReportPeriodStart(): Long = reportPeriodStart

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && reportType != ReportType.NONE) {
            loadFragment(reportsOverviewFragment)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun refresh() = notifyReportFragments<Refreshable> { it.refresh() }

    override fun refresh(uid: String?) = refresh()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_REPORT_TYPE, reportType)
    }

    companion object {
        @JvmField
        val COLORS: IntArray = intArrayOf(
            Color.parseColor("#17ee4e"), Color.parseColor("#cc1f09"),
            Color.parseColor("#3940f7"), Color.parseColor("#f9cd04"),
            Color.parseColor("#5f33a8"), Color.parseColor("#e005b6"),
            Color.parseColor("#17d6ed"), Color.parseColor("#e4a9a2"),
            Color.parseColor("#8fe6cd"), Color.parseColor("#8b48fb"),
            Color.parseColor("#343a36"), Color.parseColor("#6decb1"),
            Color.parseColor("#f0f8ff"), Color.parseColor("#5c3378"),
            Color.parseColor("#a6dcfd"), Color.parseColor("#ba037c"),
            Color.parseColor("#708809"), Color.parseColor("#32072c"),
            Color.parseColor("#fddef8"), Color.parseColor("#fa0e6e"),
            Color.parseColor("#d9e7b5")
        )
        private const val STATE_REPORT_TYPE = "STATE_REPORT_TYPE"
    }
}
