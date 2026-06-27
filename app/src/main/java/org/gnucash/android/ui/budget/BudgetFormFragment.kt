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
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import com.google.android.material.textfield.TextInputLayout
import org.gnucash.android.R
import org.gnucash.android.model.data.Budget
import org.gnucash.android.model.data.BudgetAmount
import org.gnucash.android.model.data.Commodity
import org.gnucash.android.model.data.Money
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.BudgetsDbAdapter
import org.gnucash.android.model.db.adapter.DatabaseAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.sql.Timestamp
import java.text.ParseException
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Fragment for creating or editing Budgets
 */
class BudgetFormFragment : Fragment(), OnRecurrenceSetListener,
    CalendarDatePickerDialogFragment.OnDateSetListener {
    private var mBudgetNameInput: EditText? = null
    private var mDescriptionInput: EditText? = null
    private var mRecurrenceInput: TextView? = null
    private var mNameTextInputLayout: TextInputLayout? = null
    private var mKeyboardView: KeyboardView? = null
    private var mBudgetAmountInput: CalculatorEditText? = null
    private var mBudgetAccountSpinner: Spinner? = null
    private var mAddBudgetAmount: Button? = null
    private var mStartDateInput: TextView? = null
    private var mBudgetAmountLayout: View? = null

    var mEventRecurrence: EventRecurrence = EventRecurrence()
    var mRecurrenceRule: String? = null

    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null

    private var mBudget: Budget? = null
    private var mStartDate: Calendar? = null
    private var mBudgetAmounts: ArrayList<BudgetAmount>? = null
    private var mAccountsDbAdapter: AccountsDbAdapter? = null
    private var mAccountsCursorAdapter: QualifiedAccountNameCursorAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_budget_form, container, false)
        mBudgetNameInput = view.findViewById<EditText?>(R.id.input_budget_name)
        mDescriptionInput = view.findViewById<EditText?>(R.id.input_description)
        mRecurrenceInput = view.findViewById<TextView?>(R.id.input_recurrence)
        mNameTextInputLayout = view.findViewById<TextInputLayout?>(R.id.name_text_input_layout)
        mKeyboardView = view.findViewById<KeyboardView?>(R.id.calculator_keyboard)
        mBudgetAmountInput = view.findViewById<CalculatorEditText?>(R.id.input_budget_amount)
        mBudgetAccountSpinner = view.findViewById<Spinner?>(R.id.input_budget_account_spinner)
        mAddBudgetAmount = view.findViewById<Button?>(R.id.btn_add_budget_amount)
        mStartDateInput = view.findViewById<TextView?>(R.id.input_start_date)
        mBudgetAmountLayout = view.findViewById<View?>(R.id.budget_amount_layout)

        mStartDateInput!!.setOnClickListener(View.OnClickListener { v: View? ->
            this.onClickBudgetStartDate(
                v!!
            )
        })
        mAddBudgetAmount!!.setOnClickListener(View.OnClickListener { v: View? ->
            this.onOpenBudgetAmountEditor(
                v
            )
        })

        view.findViewById<View?>(R.id.btn_remove_item).setVisibility(View.GONE)
        mBudgetAmountInput!!.bindListeners(mKeyboardView)
        mStartDateInput!!.setText(TransactionFormFragment.DATE_FORMATTER.format(mStartDate!!.getTime()))
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance()
        mStartDate = Calendar.getInstance()
        mBudgetAmounts = ArrayList<BudgetAmount>()
        val conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 )"
        mAccountsDbAdapter = AccountsDbAdapter.getInstance()
        val accountCursor =
            mAccountsDbAdapter!!.fetchAccountsOrderedByFavoriteAndFullName(conditions, null)
        mAccountsCursorAdapter = QualifiedAccountNameCursorAdapter(getActivity(), accountCursor)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        mBudgetAccountSpinner!!.setAdapter(mAccountsCursorAdapter)
        val budgetUID = requireArguments().getString(UxArgument.BUDGET_UID)
        if (budgetUID != null) { //if we are editing the budget
            initViews(mBudgetsDbAdapter!!.getRecord(budgetUID).also { mBudget = it })
        }
        val actionbar: ActionBar? =
            checkNotNull((getActivity() as AppCompatActivity).getSupportActionBar())
        if (mBudget == null) actionbar!!.setTitle("Create Budget")
        else actionbar!!.setTitle("Edit Budget")

        mRecurrenceInput!!.setOnClickListener(
            RecurrenceViewClickListener(getActivity() as AppCompatActivity?, mRecurrenceRule, this)
        )
    }

    /**
     * Initialize views when editing an existing budget
     * @param budget Budget to use to initialize the views
     */
    private fun initViews(budget: Budget) {
        mBudgetNameInput!!.setText(budget.getName())
        mDescriptionInput!!.setText(budget.getDescription())

        val recurrenceRuleString = budget.getRecurrence().getRuleString()
        mRecurrenceRule = recurrenceRuleString
        mEventRecurrence.parse(recurrenceRuleString)
        mRecurrenceInput!!.setText(budget.getRecurrence().getRepeatString())

        mBudgetAmounts = budget.getCompactedBudgetAmounts() as ArrayList<BudgetAmount>
        toggleAmountInputVisibility()
    }

    /**
     * Extracts the budget amounts from the form
     *
     * If the budget amount was input using the simple form, then read the values.<br></br>
     * Else return the values gotten from the BudgetAmountEditor
     * @return List of budget amounts
     */
    private fun extractBudgetAmounts(): ArrayList<BudgetAmount> {
        val value = mBudgetAmountInput!!.getValue()
        if (value == null) return mBudgetAmounts!!

        if (mBudgetAmounts!!.isEmpty()) { //has not been set in budget amounts editor
            val budgetAmounts = ArrayList<BudgetAmount>()
            val amount = Money(value, Commodity.DEFAULT_COMMODITY)
            val accountUID =
                mAccountsDbAdapter!!.getUID(mBudgetAccountSpinner!!.getSelectedItemId())
            val budgetAmount = BudgetAmount(amount, accountUID)
            budgetAmounts.add(budgetAmount)
            return budgetAmounts
        } else {
            return mBudgetAmounts!!
        }
    }

    /**
     * Checks that this budget can be saved
     * Also sets the appropriate error messages on the relevant views
     *
     * For a budget to be saved, it needs to have a name, an amount and a schedule
     * @return `true` if the budget can be saved, `false` otherwise
     */
    private fun canSave(): Boolean {
        if (mEventRecurrence.until != null && mEventRecurrence.until.length > 0
            || mEventRecurrence.count <= 0
        ) {
            Toast.makeText(
                getActivity(),
                "Set a number periods in the recurrence dialog to save the budget",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        mBudgetAmounts = extractBudgetAmounts()
        val budgetName = mBudgetNameInput!!.getText().toString()
        val canSave =
            mRecurrenceRule != null && !budgetName.isEmpty() && !mBudgetAmounts!!.isEmpty()

        if (!canSave) {
            if (budgetName.isEmpty()) {
                mNameTextInputLayout!!.setError("A name is required")
                mNameTextInputLayout!!.setErrorEnabled(true)
            } else {
                mNameTextInputLayout!!.setErrorEnabled(false)
            }

            if (mBudgetAmounts!!.isEmpty()) {
                mBudgetAmountInput!!.setError("Enter an amount for the budget")
                Toast.makeText(
                    getActivity(), "Add budget amounts in order to save the budget",
                    Toast.LENGTH_SHORT
                ).show()
            }

            if (mRecurrenceRule == null) {
                Toast.makeText(
                    getActivity(), "Set a repeat pattern to create a budget!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return canSave
    }

    /**
     * Extracts the information from the form and saves the budget
     */
    private fun saveBudget() {
        if (!canSave()) return
        val name = mBudgetNameInput!!.getText().toString().trim { it <= ' ' }


        if (mBudget == null) {
            mBudget = Budget(name)
        } else {
            mBudget!!.setName(name)
        }

        // TODO: 22.10.2015 set the period num of the budget amount
        extractBudgetAmounts()
        mBudget!!.setBudgetAmounts(mBudgetAmounts)

        mBudget!!.setDescription(mDescriptionInput!!.getText().toString().trim { it <= ' ' })

        val recurrence = RecurrenceParser.parse(mEventRecurrence)
        recurrence.setPeriodStart(Timestamp(mStartDate!!.getTimeInMillis()))
        mBudget!!.setRecurrence(recurrence)

        mBudgetsDbAdapter!!.addRecord(mBudget!!, DatabaseAdapter.UpdateMethod.insert)
        requireActivity().finish()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menu_save -> {
                saveBudget()
                return true
            }
        }
        return false
    }

    fun onClickBudgetStartDate(v: View) {
        var dateMillis: Long = 0
        try {
            val date =
                TransactionFormFragment.DATE_FORMATTER.parse((v as TextView).getText().toString())
            dateMillis = date.getTime()
        } catch (e: ParseException) {
            Log.e(getTag(), "Error converting input time to Date object")
        }
        val calendar = Calendar.getInstance()
        calendar.setTimeInMillis(dateMillis)

        val year = calendar.get(Calendar.YEAR)
        val monthOfYear = calendar.get(Calendar.MONTH)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val datePickerDialog = CalendarDatePickerDialogFragment()
        datePickerDialog.setOnDateSetListener(this@BudgetFormFragment)
        datePickerDialog.setPreselectedDate(year, monthOfYear, dayOfMonth)
        datePickerDialog.show(requireFragmentManager(), "date_picker_fragment")
    }

    fun onOpenBudgetAmountEditor(v: View?) {
        val intent = Intent(getActivity(), FormActivity::class.java)
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET_AMOUNT_EDITOR.name)
        mBudgetAmounts = extractBudgetAmounts()
        intent.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, mBudgetAmounts)
        startActivityForResult(intent, REQUEST_EDIT_BUDGET_AMOUNTS)
    }

    override fun onRecurrenceSet(rrule: String?) {
        mRecurrenceRule = rrule
        var repeatString: String? = getString(R.string.label_tap_to_create_schedule)
        if (mRecurrenceRule != null) {
            mEventRecurrence.parse(mRecurrenceRule)
            repeatString = EventRecurrenceFormatter.getRepeatString(
                getActivity(),
                getResources(),
                mEventRecurrence,
                true
            )
        }

        mRecurrenceInput!!.setText(repeatString)
    }

    override fun onDateSet(
        dialog: CalendarDatePickerDialogFragment?,
        year: Int,
        monthOfYear: Int,
        dayOfMonth: Int
    ) {
        val cal: Calendar = GregorianCalendar(year, monthOfYear, dayOfMonth)
        mStartDateInput!!.setText(TransactionFormFragment.DATE_FORMATTER.format(cal.getTime()))
        mStartDate!!.set(Calendar.YEAR, year)
        mStartDate!!.set(Calendar.MONTH, monthOfYear)
        mStartDate!!.set(Calendar.DAY_OF_MONTH, dayOfMonth)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_EDIT_BUDGET_AMOUNTS) {
            if (resultCode == Activity.RESULT_OK) {
                val budgetAmounts =
                    data?.getParcelableArrayListExtra<BudgetAmount?>(UxArgument.BUDGET_AMOUNT_LIST)
                if (budgetAmounts != null) {
                    mBudgetAmounts = budgetAmounts
                    toggleAmountInputVisibility()
                }
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Toggles the visibility of the amount input based on [.mBudgetAmounts]
     */
    private fun toggleAmountInputVisibility() {
        if (mBudgetAmounts!!.size > 1) {
            mBudgetAmountLayout!!.setVisibility(View.GONE)
            mAddBudgetAmount!!.setText("Edit Budget Amounts")
        } else {
            mAddBudgetAmount!!.setText("Add Budget Amounts")
            mBudgetAmountLayout!!.setVisibility(View.VISIBLE)
            if (!mBudgetAmounts!!.isEmpty()) {
                val budgetAmount = mBudgetAmounts!!.get(0)
                mBudgetAmountInput!!.setValue(budgetAmount.getAmount().asBigDecimal())
                mBudgetAccountSpinner!!.setSelection(
                    mAccountsCursorAdapter!!.getPosition(
                        budgetAmount.getAccountUID()
                    )
                )
            }
        }
    }

    companion object {
        const val REQUEST_EDIT_BUDGET_AMOUNTS: Int = 0xBA
    }
}