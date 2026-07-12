/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.gnucash.android.ui.report.sheet

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TextView
import org.gnucash.android.R
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.data.Money
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.transaction.TransactionsActivity

/** Balance-sheet report fragment. */
class BalanceSheetFragment : BaseReportFragment() {
    private lateinit var assetsTableLayout: TableLayout
    private lateinit var liabilitiesTableLayout: TableLayout
    private lateinit var equityTableLayout: TableLayout
    private lateinit var netWorth: TextView
    private val accountsDbAdapter = AccountsDbAdapter.getInstance()
    private lateinit var assetsBalance: Money
    private lateinit var liabilitiesBalance: Money
    private lateinit var assetAccountTypes: List<AccountType>
    private lateinit var liabilityAccountTypes: List<AccountType>
    private lateinit var equityAccountTypes: List<AccountType>

    override val layoutResource = R.layout.fragment_text_report
    override val title = R.string.title_balance_sheet_report
    override val reportType = ReportType.TEXT

    override fun requiresAccountTypeOptions() = false

    override fun requiresTimeRangeOptions() = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        assetsTableLayout = view.findViewById(R.id.table_assets)
        liabilitiesTableLayout = view.findViewById(R.id.table_liabilities)
        equityTableLayout = view.findViewById(R.id.table_equity)
        netWorth = view.findViewById(R.id.total_liability_and_equity)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        assetAccountTypes = listOf(AccountType.ASSET, AccountType.CASH, AccountType.BANK)
        liabilityAccountTypes = listOf(AccountType.LIABILITY, AccountType.CREDIT)
        equityAccountTypes = listOf(AccountType.EQUITY)
    }

    override fun generateReport() {
        assetsBalance = accountsDbAdapter.getAccountBalance(
            assetAccountTypes,
            -1,
            System.currentTimeMillis()
        )
        liabilitiesBalance = accountsDbAdapter.getAccountBalance(
            liabilityAccountTypes,
            -1,
            System.currentTimeMillis()
        )
    }

    override fun displayReport() {
        loadAccountViews(assetAccountTypes, assetsTableLayout)
        loadAccountViews(liabilityAccountTypes, liabilitiesTableLayout)
        loadAccountViews(equityAccountTypes, equityTableLayout)
        TransactionsActivity.displayBalance(
            netWorth,
            assetsBalance.subtract(liabilitiesBalance)
        )
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    /** Loads the individual account rows and a total row into [tableLayout]. */
    private fun loadAccountViews(accountTypes: List<AccountType>, tableLayout: TableLayout) {
        val inflater = LayoutInflater.from(requireActivity())
        val cursor = accountsDbAdapter.fetchAccounts(
            DatabaseSchema.AccountEntry.COLUMN_TYPE + " IN ( '" +
                TextUtils.join("' , '", accountTypes) + "' ) AND " +
                DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0",
            null,
            DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC"
        )

        while (cursor.moveToNext()) {
            val accountUID = cursor.getString(
                cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID)
            )
            val name = cursor.getString(
                cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME)
            )
            val row = inflater.inflate(R.layout.row_balance_sheet, tableLayout, false)
            row.findViewById<TextView>(R.id.account_name).text = name
            val balanceView = row.findViewById<TextView>(R.id.account_balance)
            TransactionsActivity.displayBalance(
                balanceView,
                accountsDbAdapter.getAccountBalance(accountUID)
            )
            tableLayout.addView(row)
        }

        val totalView = inflater.inflate(R.layout.row_balance_sheet, tableLayout, false)
        val layoutParams = totalView.layoutParams as TableLayout.LayoutParams
        layoutParams.setMargins(
            layoutParams.leftMargin,
            20,
            layoutParams.rightMargin,
            layoutParams.bottomMargin
        )
        totalView.layoutParams = layoutParams

        totalView.findViewById<TextView>(R.id.account_name).apply {
            textSize = 16f
            setText(R.string.label_balance_sheet_total)
        }
        val accountBalance = totalView.findViewById<TextView>(R.id.account_balance).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        TransactionsActivity.displayBalance(
            accountBalance,
            accountsDbAdapter.getAccountBalance(accountTypes, -1, System.currentTimeMillis())
        )
        tableLayout.addView(totalView)
    }
}
