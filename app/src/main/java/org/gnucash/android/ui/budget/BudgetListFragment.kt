/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.budget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.model.db.DatabaseCursorLoader
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.BudgetsDbAdapter
import org.gnucash.android.ui.budget.BudgetListFragment.BudgetRecyclerAdapter.BudgetViewHolder
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.CursorRecyclerAdapter
import org.gnucash.android.ui.util.widget.EmptyRecyclerView
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Budget list fragment
 */
class BudgetListFragment : Fragment(), Refreshable, LoaderManager.LoaderCallbacks<Cursor> {
    private var mBudgetRecyclerAdapter: BudgetRecyclerAdapter? = null

    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null

    private var mRecyclerView: EmptyRecyclerView? = null
    private var mProposeBudgets: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_budget_list, container, false)
        mRecyclerView = view.findViewById<EmptyRecyclerView?>(R.id.budget_recycler_view)
        mProposeBudgets = view.findViewById<Button?>(R.id.empty_view)

        mRecyclerView!!.setHasFixedSize(true)
        mRecyclerView!!.setEmptyView(mProposeBudgets)

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val gridLayoutManager = GridLayoutManager(getActivity(), 2)
            mRecyclerView!!.setLayoutManager(gridLayoutManager)
        } else {
            val mLayoutManager = LinearLayoutManager(getActivity())
            mRecyclerView!!.setLayoutManager(mLayoutManager)
        }
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance()
        mBudgetRecyclerAdapter = BudgetRecyclerAdapter(null)

        mRecyclerView!!.setAdapter(mBudgetRecyclerAdapter)

        getLoaderManager().initLoader<Cursor?>(0, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        Log.d(LOG_TAG, "Creating the accounts loader")
        return BudgetsCursorLoader(getActivity())
    }

    override fun onLoadFinished(loaderCursor: Loader<Cursor>, cursor: Cursor) {
        Log.d(LOG_TAG, "Budget loader finished. Swapping in cursor")
        mBudgetRecyclerAdapter!!.swapCursor(cursor)
        mBudgetRecyclerAdapter!!.notifyDataSetChanged()
    }

    override fun onLoaderReset(loadCursor: Loader<Cursor>) {
        Log.d(LOG_TAG, "Resetting the accounts loader")
        mBudgetRecyclerAdapter!!.swapCursor(null)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        requireActivity().findViewById<View?>(R.id.fab_create_budget).setVisibility(View.VISIBLE)
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle("Budgets")
    }

    override fun refresh() {
        getLoaderManager().restartLoader<Cursor?>(0, null, this)
    }

    /**
     * This method does nothing with the GUID.
     * Is equivalent to calling [.refresh]
     * @param uid GUID of relevant item to be refreshed
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    /**
     * Opens the budget detail fragment
     * @param budgetUID GUID of budget
     */
    fun onClickBudget(budgetUID: String?) {
        val fragmentManager = requireActivity().getSupportFragmentManager()
        val fragmentTransaction = fragmentManager
            .beginTransaction()

        fragmentTransaction.replace(
            R.id.fragment_container,
            BudgetDetailFragment.newInstance(budgetUID)
        )
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
    }

    /**
     * Launches the FormActivity for editing the budget
     * @param budgetId Db record Id of the budget
     */
    private fun editBudget(budgetId: Long) {
        val addAccountIntent = Intent(getActivity(), FormActivity::class.java)
        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT)
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
        addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetsDbAdapter!!.getUID(budgetId))
        startActivityForResult(addAccountIntent, REQUEST_EDIT_BUDGET)
    }

    /**
     * Delete the budget from the database
     * @param budgetId Database record ID
     */
    private fun deleteBudget(budgetId: Long) {
        BudgetsDbAdapter.getInstance().deleteRecord(budgetId)
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
        }
    }

    internal inner class BudgetRecyclerAdapter(cursor: Cursor?) :
        CursorRecyclerAdapter<BudgetViewHolder?>(cursor) {
        override fun onBindViewHolderCursor(holder: BudgetViewHolder?, cursor: Cursor) {
            val budget = mBudgetsDbAdapter!!.buildModelInstance(cursor)
            holder?.budgetId = mBudgetsDbAdapter!!.getID(budget.getUID())

            holder?.budgetName?.setText(budget.getName())

            val accountsDbAdapter = AccountsDbAdapter.getInstance()
            val accountString: String?
            val numberOfAccounts = budget.getNumberOfAccounts()
            if (numberOfAccounts == 1) {
                accountString = accountsDbAdapter.getAccountFullName(
                    budget.getBudgetAmounts().get(0).getAccountUID()
                )
            } else {
                accountString = numberOfAccounts.toString() + " budgeted accounts"
            }
            holder?.accountName?.setText(accountString)

            holder?.budgetRecurrence?.setText(
                (budget.getRecurrence().getRepeatString() + " - "
                        + budget.getRecurrence().getDaysLeftInCurrentPeriod() + " days left")
            )

            var spentAmountValue = BigDecimal.ZERO
            for (budgetAmount in budget.getCompactedBudgetAmounts()) {
                val balance = accountsDbAdapter.getAccountBalance(
                    budgetAmount.getAccountUID(),
                    budget.getStartofCurrentPeriod(), budget.getEndOfCurrentPeriod()
                )
                spentAmountValue = spentAmountValue.add(balance.asBigDecimal())
            }

            val budgetTotal = budget.getAmountSum()
            val commodity = budgetTotal.getCommodity()
            val usedAmount = (commodity.getSymbol() + spentAmountValue + " of "
                    + budgetTotal.formattedString())
            holder?.budgetAmount?.setText(usedAmount)

            val budgetProgress = spentAmountValue.divide(
                budgetTotal.asBigDecimal(),
                commodity.getSmallestFractionDigits(), RoundingMode.HALF_EVEN
            )
                .toDouble()
            holder?.budgetIndicator?.setProgress((budgetProgress * 100).toInt())

            holder?.budgetAmount?.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress))

            holder?.itemView?.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    onClickBudget(budget.getUID())
                }
            })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
            val v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cardview_budget, parent, false)

            return BudgetViewHolder(v)
        }

        internal inner class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
            PopupMenu.OnMenuItemClickListener {
            val budgetName: TextView
            val accountName: TextView
            val budgetAmount: TextView
            val optionsMenu: ImageView
            val budgetIndicator: ProgressBar
            val budgetRecurrence: TextView
            var budgetId: Long = 0

            init {
                budgetName = itemView.findViewById<TextView>(R.id.primary_text)
                accountName = itemView.findViewById<TextView>(R.id.secondary_text)
                budgetAmount = itemView.findViewById<TextView>(R.id.budget_amount)
                optionsMenu = itemView.findViewById<ImageView>(R.id.options_menu)
                budgetIndicator = itemView.findViewById<ProgressBar>(R.id.budget_indicator)
                budgetRecurrence = itemView.findViewById<TextView>(R.id.budget_recurrence)

                optionsMenu.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View) {
                        val popup = PopupMenu(requireActivity(), v)
                        popup.setOnMenuItemClickListener(this@BudgetViewHolder)
                        val inflater = popup.getMenuInflater()
                        inflater.inflate(R.menu.budget_context_menu, popup.getMenu())
                        popup.show()
                    }
                })
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                when (item.getItemId()) {
                    R.id.context_menu_edit_budget -> {
                        editBudget(budgetId)
                        return true
                    }

                    R.id.context_menu_delete -> {
                        deleteBudget(budgetId)
                        return true
                    }

                    else -> return false
                }
            }
        }
    }

    /**
     * Loads Budgets asynchronously from the database
     */
    private class BudgetsCursorLoader
    /**
     * Constructor
     * Initializes the content observer
     *
     * @param context Application context
     */
        (context: Context?) : DatabaseCursorLoader(context) {
        override fun loadInBackground(): Cursor? {
            mDatabaseAdapter = BudgetsDbAdapter.getInstance()
            return mDatabaseAdapter.fetchAllRecords(
                null,
                null,
                DatabaseSchema.BudgetEntry.COLUMN_NAME + " ASC"
            )
        }
    }

    companion object {
        private const val LOG_TAG = "BudgetListFragment"
        private const val REQUEST_EDIT_BUDGET = 0xB
        private const val REQUEST_OPEN_ACCOUNT = 0xC
    }
}