/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.transaction.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.Repository;
import org.gnucash.android.model.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.data.Transaction;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Displays a delete confirmation dialog for transactions
 * If the transaction ID parameter is 0, then all transactions will be deleted
 * @author Ngewi Fet <ngewif@gmail.com>
 *
 */
@AndroidEntryPoint
public class TransactionsDeleteConfirmationDialogFragment extends DialogFragment {

    @Inject
    Repository mRepository;

    public static TransactionsDeleteConfirmationDialogFragment newInstance(int title, long id) {
        TransactionsDeleteConfirmationDialogFragment frag = new TransactionsDeleteConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putLong(UxArgument.SELECTED_TRANSACTION_IDS, id);
        frag.setArguments(args);
        return frag;
    }

    @Override    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int title = getArguments().getInt("title");
        final long rowId = getArguments().getLong(UxArgument.SELECTED_TRANSACTION_IDS);
        int message = rowId == 0 ? R.string.msg_delete_all_transactions_confirmation : R.string.msg_delete_transaction_confirmation;
        return new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_delete)
                .setTitle(title).setMessage(message)
                .setPositiveButton(R.string.alert_dialog_ok_delete,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();
                                if (rowId == 0) {
                                    mRepository.backupActiveBook(); //create backup before deleting everything
                                    List<Transaction> openingBalances = new ArrayList<Transaction>();
                                    boolean preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false);
                                    if (preserveOpeningBalances) {
                                        openingBalances = AccountsDbAdapter.getInstance().getAllOpeningBalanceTransactions();
                                    }

                                    transactionsDbAdapter.deleteAllRecords();

                                    if (preserveOpeningBalances) {
                                        transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert);
                                    }
                                } else {
                                    transactionsDbAdapter.deleteRecord(rowId);
                                }
                                if (getTargetFragment() instanceof Refreshable) {
                                    ((Refreshable) getTargetFragment()).refresh();
                                }
                                WidgetConfigurationActivity.updateAllWidgets(getActivity());
                            }
                        }
                )
                .setNegativeButton(R.string.alert_dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dismiss();
                            }
                        }
                )
                .create();
    }
}
