/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.gnucash.android.ui.homescreen

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.RemoteViews
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.preference.PreferenceManager
import org.gnucash.android.R
import org.gnucash.android.model.data.Account
import org.gnucash.android.model.db.BookDbHelper
import org.gnucash.android.model.db.DatabaseHelper
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.receivers.TransactionAppWidgetProvider
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.util.Locale

/** Activity for choosing which account to display on a home-screen widget. */
class WidgetConfigurationActivity : Activity() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var accountsSpinner: Spinner
    private lateinit var booksSpinner: Spinner
    private lateinit var hideAccountBalance: CheckBox
    private lateinit var okButton: Button
    private lateinit var cancelButton: Button
    private lateinit var accountsCursorAdapter: SimpleCursorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_configuration)
        setResult(RESULT_CANCELED)

        accountsSpinner = findViewById(R.id.input_accounts_spinner)
        booksSpinner = findViewById(R.id.input_books_spinner)
        hideAccountBalance = findViewById(R.id.input_hide_account_balance)
        okButton = findViewById(R.id.btn_save)
        cancelButton = findViewById(R.id.btn_cancel)

        val booksDbAdapter = BooksDbAdapter.getInstance()
        val booksCursor = booksDbAdapter.fetchAllRecords()
        val currentBookUID = booksDbAdapter.getActiveBookUID()
        var position = 0
        while (booksCursor.moveToNext()) {
            val bookUID = booksCursor.getString(
                booksCursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry.COLUMN_UID)
            )
            if (bookUID == currentBookUID) break
            position++
        }

        val booksCursorAdapter = SimpleCursorAdapter(
            this,
            android.R.layout.simple_spinner_item,
            booksCursor,
            arrayOf(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME),
            intArrayOf(android.R.id.text1),
            0
        )
        booksCursorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        booksSpinner.adapter = booksCursorAdapter
        booksSpinner.setSelection(position)

        accountsDbAdapter = AccountsDbAdapter.getInstance()
        val accountsCursor = accountsDbAdapter.fetchAllRecordsOrderedByFullName()
        if (accountsCursor.count <= 0) {
            Toast.makeText(this, R.string.error_no_accounts, Toast.LENGTH_LONG).show()
            finish()
        }

        accountsCursorAdapter = QualifiedAccountNameCursorAdapter(this, accountsCursor)
        accountsCursorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accountsSpinner.adapter = accountsCursorAdapter

        val passcodeEnabled = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(UxArgument.ENABLED_PASSCODE, false)
        hideAccountBalance.isChecked = passcodeEnabled
        bindListeners()
    }

    private fun bindListeners() {
        booksSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val book = BooksDbAdapter.getInstance().getRecord(id)
                val database: SQLiteDatabase =
                    DatabaseHelper(this@WidgetConfigurationActivity, book.getUID()).writableDatabase
                accountsDbAdapter = AccountsDbAdapter(database)
                accountsCursorAdapter.swapCursor(accountsDbAdapter.fetchAllRecordsOrderedByFullName())
                accountsCursorAdapter.notifyDataSetChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        okButton.setOnClickListener {
            appWidgetId = intent.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
                return@setOnClickListener
            }

            val bookUID = BooksDbAdapter.getInstance().getUID(booksSpinner.selectedItemId)
            val accountUID = accountsDbAdapter.getUID(accountsSpinner.selectedItemId)
            configureWidget(this, appWidgetId, bookUID, accountUID, hideAccountBalance.isChecked)
            updateWidget(this, appWidgetId)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
        cancelButton.setOnClickListener { finish() }
    }

    companion object {
        private const val TAG = "WidgetConfiguration"

        @JvmStatic
        fun configureWidget(
            context: Context,
            appWidgetId: Int,
            bookUID: String,
            accountUID: String,
            hideAccountBalance: Boolean
        ) {
            context.getSharedPreferences("widget:$appWidgetId", Context.MODE_PRIVATE).edit()
                .putString(UxArgument.BOOK_UID, bookUID)
                .putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                .putBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, hideAccountBalance)
                .apply()
        }

        @JvmStatic
        fun removeWidgetConfiguration(context: Context, appWidgetId: Int) {
            context.getSharedPreferences("widget:$appWidgetId", Context.MODE_PRIVATE).edit()
                .clear()
                .apply()
        }

        private fun loadOldPreferences(context: Context, appWidgetId: Int) {
            val preferences = PreferenceActivity.getActiveBookSharedPreferences()
            val accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId, null)
                ?: return
            configureWidget(
                context,
                appWidgetId,
                BooksDbAdapter.getInstance().getActiveBookUID(),
                accountUID,
                preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET + appWidgetId, false)
            )
            preferences.edit()
                .remove(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId)
                .remove(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET + appWidgetId)
                .apply()
        }

        @JvmStatic
        fun updateWidget(context: Context, appWidgetId: Int) {
            Log.i(TAG, "Updating widget: $appWidgetId")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            loadOldPreferences(context, appWidgetId)

            val preferences = context.getSharedPreferences("widget:$appWidgetId", Context.MODE_PRIVATE)
            val bookUID = preferences.getString(UxArgument.BOOK_UID, null) ?: return
            val accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID, null) ?: return
            val hideAccountBalance =
                preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, false)
            val accountsDbAdapter = AccountsDbAdapter(BookDbHelper.getDatabase(bookUID))

            val account: Account = try {
                accountsDbAdapter.getRecord(accountUID)
            } catch (_: IllegalArgumentException) {
                showDeletedAccount(context, appWidgetManager, appWidgetId)
                return
            }

            val views = RemoteViews(context.packageName, R.layout.widget_4x1)
            views.setTextViewText(R.id.account_name, account.getName())
            val balance = accountsDbAdapter.getAccountBalance(accountUID, -1, System.currentTimeMillis())
            if (hideAccountBalance) {
                views.setViewVisibility(R.id.transactions_summary, View.GONE)
            } else {
                views.setTextViewText(
                    R.id.transactions_summary,
                    balance.formattedString(Locale.getDefault())
                )
                val color = if (balance.isNegative) R.color.debit_red else R.color.credit_green
                views.setTextColor(
                    R.id.transactions_summary,
                    ContextCompat.getColor(context, color)
                )
            }

            val accountIntent = Intent(context, TransactionsActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                putExtra(UxArgument.BOOK_UID, bookUID)
            }
            val accountPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                accountIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_layout, accountPendingIntent)

            if (accountsDbAdapter.isPlaceholderAccount(accountUID)) {
                views.setOnClickPendingIntent(R.id.btn_view_account, accountPendingIntent)
                views.setViewVisibility(R.id.btn_new_transaction, View.GONE)
            } else {
                val transactionIntent = Intent(context, FormActivity::class.java).apply {
                    action = Intent.ACTION_INSERT_OR_EDIT
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                    putExtra(UxArgument.BOOK_UID, bookUID)
                    putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                }
                val transactionPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    transactionIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_new_transaction, transactionPendingIntent)
                views.setViewVisibility(R.id.btn_view_account, View.GONE)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun showDeletedAccount(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.i(TAG, "Account not found, resetting widget $appWidgetId")
            val views = RemoteViews(context.packageName, R.layout.widget_4x1)
            views.setTextViewText(
                R.id.account_name,
                context.getString(R.string.toast_account_deleted)
            )
            views.setTextViewText(R.id.transactions_summary, "")
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, AccountsActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
            views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            PreferenceActivity.getActiveBookSharedPreferences().edit()
                .remove(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId)
                .apply()
        }

        @JvmStatic
        fun updateAllWidgets(context: Context) {
            Log.i(TAG, "Updating all widgets")
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TransactionAppWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            Thread {
                widgetIds.forEach { updateWidget(context, it) }
            }.start()
        }
    }
}
