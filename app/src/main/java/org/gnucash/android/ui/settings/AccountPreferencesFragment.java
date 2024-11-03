/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.util.Log;
import android.widget.Toast;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.data.Transaction;
import org.gnucash.android.model.db.DatabaseSchema;
import org.gnucash.android.model.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.db.adapter.BooksDbAdapter;
import org.gnucash.android.model.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.model.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.db.adapter.SplitsDbAdapter;
import org.gnucash.android.model.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.export.ExportAsyncUtil;
import org.gnucash.android.model.export.ExportFormat;
import org.gnucash.android.model.export.ExportParams;
import org.gnucash.android.model.export.Exporter;
import org.gnucash.android.model.data.Money;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.account.AccountsListFragment;
import org.gnucash.android.ui.settings.dialog.DeleteAllAccountsConfirmationDialog;
import org.gnucash.android.util.BackupManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Account settings fragment inside the Settings activity
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountPreferencesFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener{

    private static final String TAG = AccountPreferencesFragment.class.getName();

    private static final int REQUEST_EXPORT_FILE = 0xC5;

    List<CharSequence> mCurrencyEntries = new ArrayList<>();
    List<CharSequence> mCurrencyEntryValues = new ArrayList<>();
    private CompositeDisposable mCompositeDisposable;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.fragment_account_preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(R.string.title_account_preferences);
        mCompositeDisposable = new CompositeDisposable();

        Cursor cursor = CommoditiesDbAdapter.getInstance().fetchAllRecords(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC + " ASC");
        while(cursor.moveToNext()){
            String code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_FULLNAME));
            mCurrencyEntries.add(code + " - " + name);
            mCurrencyEntryValues.add(code);
        }
        cursor.close();
    }

    @Override
    public void onResume() {
        super.onResume();

        String defaultCurrency = GnuCashApplication.getDefaultCurrencyCode();
        Preference pref = findPreference(getString(R.string.key_default_currency));
        String currencyName = CommoditiesDbAdapter.getInstance().getCommodity(defaultCurrency).getFullname();
        pref.setSummary(currencyName);
        pref.setOnPreferenceChangeListener(this);

        CharSequence[] entries = new CharSequence[mCurrencyEntries.size()];
        CharSequence[] entryValues = new CharSequence[mCurrencyEntryValues.size()];
        ((ListPreference) pref).setEntries(mCurrencyEntries.toArray(entries));
        ((ListPreference) pref).setEntryValues(mCurrencyEntryValues.toArray(entryValues));

        Preference preference = findPreference(getString(R.string.key_import_accounts));
        preference.setOnPreferenceClickListener(this);

        preference = findPreference(getString(R.string.key_export_accounts_csv));
        preference.setOnPreferenceClickListener(this);

        preference = findPreference(getString(R.string.key_delete_all_accounts));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDeleteAccountsDialog();
                return true;
            }
        });

        preference = findPreference(getString(R.string.key_create_default_accounts));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.title_create_default_accounts)
                        .setMessage(R.string.msg_confirm_create_default_accounts_setting)
                        .setIcon(R.drawable.ic_warning_black_24dp)
                        .setPositiveButton(R.string.btn_create_accounts, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                AccountsActivity.createDefaultAccounts(Money.DEFAULT_CURRENCY_CODE, getActivity());
                            }
                        })
                        .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create()
                        .show();

                return true;
            }
        });
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (key.equals(getString(R.string.key_import_accounts))){
            AccountsActivity.startXmlFileChooser(this);
            return true;
        }

        if (key.equals(getString(R.string.key_export_accounts_csv))){
            selectExportFile();
            return true;
        }

        return false;
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private void selectExportFile() {
        Intent createIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        createIntent.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();

        String filename = Exporter.buildExportFilename(ExportFormat.CSVA, bookName);
        createIntent.setType("application/text");

        createIntent.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(createIntent, REQUEST_EXPORT_FILE);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.key_default_currency))){
            GnuCashApplication.setDefaultCurrencyCode(newValue.toString());
            String fullname = CommoditiesDbAdapter.getInstance().getCommodity(newValue.toString()).getFullname();
            preference.setSummary(fullname);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.clear();
    }

    /**
     * Show the dialog for deleting accounts
     */
    public void showDeleteAccountsDialog(){
        DeleteAllAccountsConfirmationDialog deleteConfirmationDialog = DeleteAllAccountsConfirmationDialog.newInstance();
        deleteConfirmationDialog.show(getActivity().getSupportFragmentManager(), "account_settings");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    AccountsActivity.importXmlFileFromIntent(getActivity(), data, null);
                }
                break;

            case REQUEST_EXPORT_FILE:
                if (resultCode == Activity.RESULT_OK && data != null){
                    ExportParams exportParams = new ExportParams(ExportFormat.CSVA);
                    exportParams.setExportTarget(ExportParams.ExportTarget.URI);
                    exportParams.setExportLocation(data.getData().toString());
                    ExportAsyncUtil exportTask = new ExportAsyncUtil(getActivity(), GnuCashApplication.getActiveDb());
                    ProgressDialog progressDialog = new ProgressDialog(getActivity());
                    exportTask.exportData(exportParams)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new SingleObserver<Boolean>() {
                                @Override
                                public void onSubscribe(@NonNull Disposable d) {
                                    progressDialog.setTitle(R.string.title_progress_exporting_transactions);
                                    progressDialog.setIndeterminate(true);
                                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                    progressDialog.setProgressNumberFormat(null);
                                    progressDialog.setProgressPercentFormat(null);

                                    progressDialog.show();
                                    mCompositeDisposable.add(d);
                                }

                                @Override
                                public void onSuccess(@NonNull Boolean exportSuccessful) {
                                    if (progressDialog.isShowing())
                                        progressDialog.dismiss();
                                    getActivity().finish();

                                    if (exportSuccessful) {
                                        ExportAsyncUtil.reportSuccess(exportParams, getActivity());

                                        if (exportParams.shouldDeleteTransactionsAfterExport()) {
                                            // Refresh activity
                                            AccountsListFragment fragment =
                                                    ((AccountsActivity) getActivity()).getCurrentAccountListFragment();
                                            if (fragment != null)
                                                fragment.refresh();
                                        }
                                    }
                                }

                                @Override
                                public void onError(@NonNull Throwable e) {
                                    Log.e(TAG, "Error exporting: " + e.getMessage());
                                    if(e instanceof IOException) {
                                        Toast.makeText(getActivity(),
                                                R.string.toast_no_transactions_to_export,
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(getActivity(),
                                                getString(R.string.toast_export_error,
                                                        exportParams.getExportFormat().name())
                                                        + "\n" + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
        }
    }
}
