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
import android.content.Intent
import android.database.Cursor
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.model.data.BudgetAmount
import org.gnucash.android.model.data.Commodity
import org.gnucash.android.model.data.Money
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter

/**
 * Fragment for editing budgeting amounts
 */
class BudgetAmountEditorFragment : Fragment() {
    private var mAccountCursor: Cursor? = null
    private var mAccountCursorAdapter: QualifiedAccountNameCursorAdapter? = null
    private val mBudgetAmountViews: MutableList<View> = ArrayList<View>()
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    var mBudgetAmountTableLayout: LinearLayout? = null
    var mKeyboardView: KeyboardView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_budget_amount_editor, container, false)
        mBudgetAmountTableLayout = view.findViewById<LinearLayout?>(R.id.budget_amount_layout)
        mKeyboardView = view.findViewById<KeyboardView?>(R.id.calculator_keyboard)
        setupAccountSpinnerAdapter()
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAccountsDbAdapter = AccountsDbAdapter.getInstance()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val actionBar: ActionBar? =
            checkNotNull((getActivity() as AppCompatActivity).getSupportActionBar())
        actionBar!!.setTitle("Edit Budget Amounts")
        setHasOptionsMenu(true)

        val budgetAmounts =
            requireArguments().getParcelableArrayList<BudgetAmount?>(UxArgument.BUDGET_AMOUNT_LIST)
        if (budgetAmounts != null) {
            if (budgetAmounts.isEmpty()) {
                val viewHolder = addBudgetAmountView(null).getTag() as BudgetAmountViewHolder
                viewHolder.removeItemBtn.setVisibility(View.GONE) //there should always be at least one
            } else {
                loadBudgetAmountViews(budgetAmounts)
            }
        } else {
            val viewHolder = addBudgetAmountView(null).getTag() as BudgetAmountViewHolder
            viewHolder.removeItemBtn.setVisibility(View.GONE) //there should always be at least one
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.budget_amount_editor_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menu_add_budget_amount -> {
                addBudgetAmountView(null)
                return true
            }

            R.id.menu_save -> {
                saveBudgetAmounts()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    /**
     * Checks if the budget amounts can be saved
     * @return `true` if all amounts a properly entered, `false` otherwise
     */
    private fun canSave(): Boolean {
        for (budgetAmountView in mBudgetAmountViews) {
            val viewHolder = budgetAmountView.getTag() as BudgetAmountViewHolder
            viewHolder.amountEditText.evaluate()
            if (viewHolder.amountEditText.getError() != null) {
                return false
            }
            //at least one account should be loaded (don't create budget with empty account tree
            if (viewHolder.budgetAccountSpinner.getCount() == 0) {
                Toast.makeText(
                    getActivity(), "You need an account hierarchy to create a budget!",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
        }
        return true
    }

    private fun saveBudgetAmounts() {
        if (canSave()) {
            val budgetAmounts = extractBudgetAmounts() as ArrayList<BudgetAmount?>
            val data = Intent()
            data.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, budgetAmounts)
            requireActivity().setResult(Activity.RESULT_OK, data)
            requireActivity().finish()
        }
    }

    /**
     * Load views for the budget amounts
     * @param budgetAmounts List of [BudgetAmount]s
     */
    private fun loadBudgetAmountViews(budgetAmounts: MutableList<BudgetAmount?>) {
        for (budgetAmount in budgetAmounts) {
            addBudgetAmountView(budgetAmount)
        }
    }

    /**
     * Inflates a new BudgetAmount item view and adds it to the UI.
     *
     * If the `budgetAmount` is not null, then it is used to initialize the view
     * @param budgetAmount Budget amount
     */
    private fun addBudgetAmountView(budgetAmount: BudgetAmount?): View {
        val layoutInflater = requireActivity().getLayoutInflater()
        val budgetAmountView = layoutInflater.inflate(
            R.layout.item_budget_amount,
            mBudgetAmountTableLayout, false
        )
        val viewHolder: BudgetAmountViewHolder = BudgetAmountViewHolder(budgetAmountView)
        if (budgetAmount != null) {
            viewHolder.bindViews(budgetAmount)
        }
        mBudgetAmountTableLayout!!.addView(budgetAmountView, 0)
        mBudgetAmountViews.add(budgetAmountView)
        //        mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
        return budgetAmountView
    }

    /**
     * Loads the accounts in the spinner
     */
    private fun setupAccountSpinnerAdapter() {
        val conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 )"

        if (mAccountCursor != null) {
            mAccountCursor!!.close()
        }
        mAccountCursor =
            mAccountsDbAdapter!!.fetchAccountsOrderedByFavoriteAndFullName(conditions, null)

        mAccountCursorAdapter = QualifiedAccountNameCursorAdapter(getActivity(), mAccountCursor)
    }

    /**
     * Extract [BudgetAmount]s from the views
     * @return List of budget amounts
     */
    private fun extractBudgetAmounts(): MutableList<BudgetAmount?> {
        val budgetAmounts: MutableList<BudgetAmount?> = ArrayList<BudgetAmount?>()
        for (view in mBudgetAmountViews) {
            val viewHolder = view.getTag() as BudgetAmountViewHolder
            val amountValue = viewHolder.amountEditText.getValue()
            if (amountValue == null) continue
            val amount = Money(amountValue, Commodity.DEFAULT_COMMODITY)
            val accountUID =
                mAccountsDbAdapter!!.getUID(viewHolder.budgetAccountSpinner.getSelectedItemId())
            val budgetAmount = BudgetAmount(amount, accountUID)
            budgetAmounts.add(budgetAmount)
        }
        return budgetAmounts
    }

    /**
     * View holder for budget amounts
     */
    internal inner class BudgetAmountViewHolder(view: View) {
        var currencySymbolTextView: TextView
        var amountEditText: CalculatorEditText
        var removeItemBtn: ImageView
        var budgetAccountSpinner: Spinner
        var itemView: View?

        init {
            itemView = view
            currencySymbolTextView = view.findViewById<TextView?>(R.id.currency_symbol)
            amountEditText = view.findViewById<CalculatorEditText?>(R.id.input_budget_amount)
            removeItemBtn = view.findViewById<ImageView?>(R.id.btn_remove_item)
            budgetAccountSpinner = view.findViewById<Spinner?>(R.id.input_budget_account_spinner)
            itemView!!.setTag(this)

            amountEditText.bindListeners(mKeyboardView)
            budgetAccountSpinner.setAdapter(mAccountCursorAdapter)

            budgetAccountSpinner.setOnItemSelectedListener(object :
                AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val currencyCode =
                        mAccountsDbAdapter!!.getCurrencyCode(mAccountsDbAdapter!!.getUID(id))
                    val commodity = Commodity.getInstance(currencyCode)
                    currencySymbolTextView.setText(commodity.getSymbol())
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    //nothing to see here, move along
                }
            })

            removeItemBtn.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    mBudgetAmountTableLayout!!.removeView(itemView)
                    mBudgetAmountViews.remove(itemView)
                }
            })
        }

        fun bindViews(budgetAmount: BudgetAmount) {
            amountEditText.setValue(budgetAmount.getAmount().asBigDecimal())
            budgetAccountSpinner.setSelection(mAccountCursorAdapter!!.getPosition(budgetAmount.getAccountUID()))
        }
    }

    companion object {
        fun newInstance(args: Bundle?): BudgetAmountEditorFragment {
            val fragment = BudgetAmountEditorFragment()
            fragment.setArguments(args)
            return fragment
        }
    }
}