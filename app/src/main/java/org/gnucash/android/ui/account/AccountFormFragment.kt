/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import org.gnucash.android.R
import org.gnucash.android.model.data.Account
import org.gnucash.android.model.data.AccountType
import org.gnucash.android.model.data.Money
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.db.adapter.DatabaseAdapter
import org.gnucash.android.ui.colorpicker.ColorPickerDialog
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener
import org.gnucash.android.ui.colorpicker.ColorSquare
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.PreferenceActivity.Companion.getActiveBookSharedPreferences
import org.gnucash.android.util.CommoditiesCursorAdapter
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.util.Arrays

/**
 * Fragment used for creating and editing accounts
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 */
class AccountFormFragment
/**
 * Default constructor
 * Required, else the app crashes on screen rotation
 */
    : Fragment() {
    /**
     * EditText for the name of the account to be created/edited
     */
    private var mNameEditText: EditText? = null

    private var mTextInputLayout: TextInputLayout? = null

    /**
     * Spinner for selecting the currency of the account
     * Currencies listed are those specified by ISO 4217
     */
    private var mCurrencySpinner: Spinner? = null

    /**
     * Accounts database adapter
     */
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    /**
     * GUID of the parent account
     * This value is set to the parent account of the transaction being edited or
     * the account in which a new sub-account is being created
     */
    private var mParentAccountUID: String? = null

    /**
     * Account ID of the root account
     */
    private var mRootAccountId: Long = -1

    /**
     * Account UID of the root account
     */
    private var mRootAccountUID: String? = null

    /**
     * Reference to account object which will be created at end of dialog
     */
    private var mAccount: Account? = null

    /**
     * Unique ID string of account being edited
     */
    private var mAccountUID: String? = null

    /**
     * Cursor which will hold set of eligible parent accounts
     */
    private var mParentAccountCursor: Cursor? = null

    /**
     * List of all descendant Account UIDs, if we are modifying an account
     * null if creating a new account
     */
    private var mDescendantAccountUIDs: MutableList<String>? = null

    /**
     * SimpleCursorAdapter for the parent account spinner
     * @see QualifiedAccountNameCursorAdapter
     */
    private var mParentAccountCursorAdapter: SimpleCursorAdapter? = null

    /**
     * Spinner for parent account list
     */
    private var mParentAccountSpinner: Spinner? = null

    /**
     * Checkbox which activates the parent account spinner when selected
     * Leaving this unchecked means it is a top-level root account
     */
    private var mParentCheckBox: CheckBox? = null

    /**
     * Spinner for the account type
     * @see AccountType
     */
    private var mAccountTypeSpinner: Spinner? = null

    /**
     * Checkbox for activating the default transfer account spinner
     */
    private var mDefaultTransferAccountCheckBox: CheckBox? = null

    /**
     * Spinner for selecting the default transfer account
     */
    private var mDefaultTransferAccountSpinner: Spinner? = null

    /**
     * Account description input text view
     */
    private var mDescriptionEditText: EditText? = null

    /**
     * Checkbox indicating if account is a placeholder account
     */
    private var mPlaceholderCheckBox: CheckBox? = null

    /**
     * Cursor adapter which binds to the spinner for default transfer account
     */
    private var mDefaultTransferAccountCursorAdapter: SimpleCursorAdapter? = null

    /**
     * Flag indicating if double entry transactions are enabled
     */
    private var mUseDoubleEntry = false

    private var mSelectedColor = Account.DEFAULT_COLOR

    /**
     * Trigger for color picker dialog
     */
    private var mColorSquare: ColorSquare? = null

    private val mColorSelectedListener: OnColorSelectedListener = object : OnColorSelectedListener {
        override fun onColorSelected(color: Int) {
            mColorSquare!!.setBackgroundColor(color)
            mSelectedColor = color
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mAccountsDbAdapter = AccountsDbAdapter.getInstance()

        val sharedPrefs = getActiveBookSharedPreferences()
        mUseDoubleEntry = sharedPrefs.getBoolean(getString(R.string.key_use_double_entry), true)
    }

    /**
     * Inflates the dialog view and retrieves references to the dialog elements
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_account_form, container, false)
        mNameEditText = view.findViewById<EditText?>(R.id.input_account_name)
        mTextInputLayout = view.findViewById<TextInputLayout?>(R.id.name_text_input_layout)
        mCurrencySpinner = view.findViewById<Spinner?>(R.id.input_currency_spinner)
        mParentAccountSpinner = view.findViewById<Spinner?>(R.id.input_parent_account)
        mParentCheckBox = view.findViewById<CheckBox?>(R.id.checkbox_parent_account)
        mAccountTypeSpinner = view.findViewById<Spinner?>(R.id.input_account_type_spinner)
        mDefaultTransferAccountCheckBox =
            view.findViewById<CheckBox?>(R.id.checkbox_default_transfer_account)
        mDefaultTransferAccountSpinner =
            view.findViewById<Spinner?>(R.id.input_default_transfer_account)
        mDescriptionEditText = view.findViewById<EditText?>(R.id.input_account_description)
        mPlaceholderCheckBox = view.findViewById<CheckBox?>(R.id.checkbox_placeholder_account)
        mColorSquare = view.findViewById<ColorSquare?>(R.id.input_color_picker)

        mNameEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                //nothing to see here, move along
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                //nothing to see here, move along
            }

            override fun afterTextChanged(s: Editable) {
                if (s.toString().length > 0) {
                    mTextInputLayout!!.setErrorEnabled(false)
                }
            }
        })

        mAccountTypeSpinner!!.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                loadParentAccountList(selectedAccountType)
                if (mParentAccountUID != null) setParentAccountSelection(
                    mAccountsDbAdapter!!.getID(
                        mParentAccountUID!!
                    )
                )
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                //nothing to see here, move along
            }
        })


        mParentAccountSpinner!!.setEnabled(false)

        mParentCheckBox!!.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                mParentAccountSpinner!!.setEnabled(isChecked)
            }
        })

        mDefaultTransferAccountSpinner!!.setEnabled(false)
        mDefaultTransferAccountCheckBox!!.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(compoundButton: CompoundButton?, isChecked: Boolean) {
                mDefaultTransferAccountSpinner!!.setEnabled(isChecked)
            }
        })

        mColorSquare!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
                showColorPickerDialog()
            }
        })

        return view
    }


    /**
     * Initializes the values of the views in the dialog
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val commoditiesAdapter = CommoditiesCursorAdapter(
            getActivity(), android.R.layout.simple_spinner_item
        )
        commoditiesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        mCurrencySpinner!!.setAdapter(commoditiesAdapter)


        mAccountUID = requireArguments().getString(UxArgument.SELECTED_ACCOUNT_UID)

        val supportActionBar = (getActivity() as AppCompatActivity).getSupportActionBar()
        if (mAccountUID != null) {
            mAccount = mAccountsDbAdapter!!.getRecord(mAccountUID!!)
            supportActionBar!!.setTitle(R.string.title_edit_account)
        } else {
            supportActionBar!!.setTitle(R.string.title_create_account)
        }

        mRootAccountUID = mAccountsDbAdapter!!.getOrCreateGnuCashRootAccountUID()
        if (mRootAccountUID != null) mRootAccountId = mAccountsDbAdapter!!.getID(mRootAccountUID!!)

        //need to load the cursor adapters for the spinners before initializing the views
        loadAccountTypesList()
        loadDefaultTransferAccountList()
        setDefaultTransferAccountInputsVisible(mUseDoubleEntry)

        if (mAccount != null) {
            initializeViewsWithAccount(mAccount!!)
            //do not immediately open the keyboard when editing an account
            requireActivity().getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } else {
            initializeViews()
        }
    }

    /**
     * Initialize view with the properties of `account`.
     * This is applicable when editing an account
     * @param account Account whose fields are used to populate the form
     */
    private fun initializeViewsWithAccount(account: Account) {
        requireNotNull(account) { "Account cannot be null" }

        loadParentAccountList(account.getAccountType())
        mParentAccountUID = account.getParentUID()
        if (mParentAccountUID == null) {
            // null parent, set Parent as root
            mParentAccountUID = mRootAccountUID
        }

        if (mParentAccountUID != null) {
            setParentAccountSelection(mAccountsDbAdapter!!.getID(mParentAccountUID!!))
        }

        val currencyCode = account.getCommodity().getCurrencyCode()
        setSelectedCurrency(currencyCode)

        if (mAccountsDbAdapter!!.getTransactionMaxSplitNum(mAccount!!.getUID()) > 1) {
            //TODO: Allow changing the currency and effecting the change for all transactions without any currency exchange (purely cosmetic change)
            mCurrencySpinner!!.setEnabled(false)
        }

        mNameEditText!!.setText(account.getName())
        mNameEditText!!.setSelection(mNameEditText!!.getText().length)
        mDescriptionEditText!!.setText(account.getDescription())

        if (mUseDoubleEntry) {
            if (account.getDefaultTransferAccountUID() != null) {
                val doubleDefaultAccountId =
                    mAccountsDbAdapter!!.getID(account.getDefaultTransferAccountUID())
                setDefaultTransferAccountSelection(doubleDefaultAccountId, true)
            } else {
                var currentAccountUID = account.getParentUID()
                val rootAccountUID = mAccountsDbAdapter!!.getOrCreateGnuCashRootAccountUID()
                while (currentAccountUID != rootAccountUID) {
                    val defaultTransferAccountID = mAccountsDbAdapter!!.getDefaultTransferAccountID(
                        mAccountsDbAdapter!!.getID(currentAccountUID)
                    )
                    if (defaultTransferAccountID > 0) {
                        setDefaultTransferAccountSelection(defaultTransferAccountID, false)
                        break //we found a parent with default transfer setting
                    }
                    currentAccountUID = mAccountsDbAdapter!!.getParentAccountUID(currentAccountUID)
                }
            }
        }

        mPlaceholderCheckBox!!.setChecked(account.isPlaceholderAccount())
        mSelectedColor = account.getColor()
        mColorSquare!!.setBackgroundColor(account.getColor())

        setAccountTypeSelection(account.getAccountType())
    }

    /**
     * Initialize views with defaults for new account
     */
    private fun initializeViews() {
        setSelectedCurrency(Money.DEFAULT_CURRENCY_CODE)
        mColorSquare!!.setBackgroundColor(Color.LTGRAY)
        mParentAccountUID = requireArguments().getString(UxArgument.PARENT_ACCOUNT_UID)


        if (mParentAccountUID != null) {
            val parentAccountType = mAccountsDbAdapter!!.getAccountType(mParentAccountUID!!)
            setAccountTypeSelection(parentAccountType)
            loadParentAccountList(parentAccountType)
            setParentAccountSelection(mAccountsDbAdapter!!.getID(mParentAccountUID!!))
        }
    }

    /**
     * Selects the corresponding account type in the spinner
     * @param accountType AccountType to be set
     */
    private fun setAccountTypeSelection(accountType: AccountType) {
        val accountTypeEntries = getResources().getStringArray(R.array.key_account_type_entries)
        val accountTypeIndex = Arrays.asList<String?>(*accountTypeEntries).indexOf(accountType.name)
        mAccountTypeSpinner!!.setSelection(accountTypeIndex)
    }

    /**
     * Toggles the visibility of the default transfer account input fields.
     * This field is irrelevant for users who do not use double accounting
     */
    private fun setDefaultTransferAccountInputsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        val view: View? = requireView()
        view!!.findViewById<View?>(R.id.layout_default_transfer_account).setVisibility(visibility)
        view.findViewById<View?>(R.id.label_default_transfer_account).setVisibility(visibility)
    }

    /**
     * Selects the currency with code `currencyCode` in the spinner
     * @param currencyCode ISO 4217 currency code to be selected
     */
    private fun setSelectedCurrency(currencyCode: String?) {
        val commodityDbAdapter = CommoditiesDbAdapter.getInstance()
        val commodityId = commodityDbAdapter.getID(commodityDbAdapter.getCommodityUID(currencyCode))
        var position = 0
        for (i in 0..<mCurrencySpinner!!.getCount()) {
            if (commodityId == mCurrencySpinner!!.getItemIdAtPosition(i)) {
                position = i
            }
        }
        mCurrencySpinner!!.setSelection(position)
    }

    /**
     * Selects the account with ID `parentAccountId` in the parent accounts spinner
     * @param parentAccountId Record ID of parent account to be selected
     */
    private fun setParentAccountSelection(parentAccountId: Long) {
        if (parentAccountId <= 0 || parentAccountId == mRootAccountId) {
            return
        }

        for (pos in 0..<mParentAccountCursorAdapter!!.getCount()) {
            if (mParentAccountCursorAdapter!!.getItemId(pos) == parentAccountId) {
                mParentCheckBox!!.setChecked(true)
                mParentAccountSpinner!!.setEnabled(true)
                mParentAccountSpinner!!.setSelection(pos, true)
                break
            }
        }
    }

    /**
     * Selects the account with ID `parentAccountId` in the default transfer account spinner
     * @param defaultTransferAccountId Record ID of parent account to be selected
     */
    private fun setDefaultTransferAccountSelection(
        defaultTransferAccountId: Long,
        enableTransferAccount: Boolean
    ) {
        if (defaultTransferAccountId > 0) {
            mDefaultTransferAccountCheckBox!!.setChecked(enableTransferAccount)
            mDefaultTransferAccountSpinner!!.setEnabled(enableTransferAccount)
        } else return

        for (pos in 0..<mDefaultTransferAccountCursorAdapter!!.getCount()) {
            if (mDefaultTransferAccountCursorAdapter!!.getItemId(pos) == defaultTransferAccountId) {
                mDefaultTransferAccountSpinner!!.setSelection(pos)
                break
            }
        }
    }

    private val accountColorOptions: IntArray
        /**
         * Returns an array of colors used for accounts.
         * The array returned has the actual color values and not the resource ID.
         * @return Integer array of colors used for accounts
         */
        get() {
            val res = getResources()
            val colorTypedArray = res.obtainTypedArray(R.array.account_colors)
            val colorOptions = IntArray(colorTypedArray.length())
            for (i in 0..<colorTypedArray.length()) {
                val color = colorTypedArray.getColor(
                    i, ContextCompat.getColor(
                        requireContext(),
                        R.color.title_green
                    )
                )
                colorOptions[i] = color
            }
            colorTypedArray.recycle()
            return colorOptions
        }

    /**
     * Shows the color picker dialog
     */
    private fun showColorPickerDialog() {
        val fragmentManager = requireActivity().getSupportFragmentManager()
        var currentColor = Color.LTGRAY
        if (mAccount != null) {
            currentColor = mAccount!!.getColor()
        }

        val colorPickerDialogFragment = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            this.accountColorOptions,
            currentColor, 4, 12
        )
        colorPickerDialogFragment.setOnColorSelectedListener(mColorSelectedListener)
        colorPickerDialogFragment.show(fragmentManager, COLOR_PICKER_DIALOG_TAG)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menu_save -> {
                saveAccount()
                return true
            }

            android.R.id.home -> {
                finishFragment()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Initializes the default transfer account spinner with eligible accounts
     */
    private fun loadDefaultTransferAccountList() {
        val condition =
            (DatabaseSchema.AccountEntry.COLUMN_UID + " != '" + mAccountUID + "' " //when creating a new account mAccountUID is null, so don't use whereArgs
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + "=0"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "=0"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?")

        val defaultTransferAccountCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFullName(
            condition,
            arrayOf<String>(AccountType.ROOT.name)
        )

        if (mDefaultTransferAccountSpinner!!.getCount() <= 0) {
            setDefaultTransferAccountInputsVisible(false)
        }

        mDefaultTransferAccountCursorAdapter = QualifiedAccountNameCursorAdapter(
            getActivity(),
            defaultTransferAccountCursor
        )
        mDefaultTransferAccountSpinner!!.setAdapter(mDefaultTransferAccountCursorAdapter)
    }

    /**
     * Loads the list of possible accounts which can be set as a parent account and initializes the spinner.
     * The allowed parent accounts depends on the account type
     * @param accountType AccountType of account whose allowed parent list is to be loaded
     */
    private fun loadParentAccountList(accountType: AccountType) {
        var condition = (DatabaseSchema.SplitEntry.COLUMN_TYPE + " IN ("
                + getAllowedParentAccountTypes(accountType) + ") AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "!=1 ")

        if (mAccount != null) {  //if editing an account
            mDescendantAccountUIDs =
                mAccountsDbAdapter!!.getDescendantAccountUIDs(mAccount!!.getUID(), null, null)
            val rootAccountUID = mAccountsDbAdapter!!.getOrCreateGnuCashRootAccountUID()
            val descendantAccountUIDs: MutableList<String?> =
                ArrayList<String?>(mDescendantAccountUIDs)
            if (rootAccountUID != null) descendantAccountUIDs.add(rootAccountUID)
            // limit cyclic account hierarchies.
            condition += (" AND (" + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ( '"
                    + TextUtils.join("','", descendantAccountUIDs) + "','" + mAccountUID + "' ) )")
        }

        //if we are reloading the list, close the previous cursor first
        if (mParentAccountCursor != null) mParentAccountCursor!!.close()

        mParentAccountCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFullName(condition, null)
        val view: View? = requireView()
        if (mParentAccountCursor!!.getCount() <= 0) {
            mParentCheckBox!!.setChecked(false) //disable before hiding, else we can still read it when saving
            view!!.findViewById<View?>(R.id.layout_parent_account).setVisibility(View.GONE)
            view.findViewById<View?>(R.id.label_parent_account).setVisibility(View.GONE)
        } else {
            view!!.findViewById<View?>(R.id.layout_parent_account).setVisibility(View.VISIBLE)
            view.findViewById<View?>(R.id.label_parent_account).setVisibility(View.VISIBLE)
        }

        mParentAccountCursorAdapter = QualifiedAccountNameCursorAdapter(
            getActivity(), mParentAccountCursor
        )
        mParentAccountSpinner!!.setAdapter(mParentAccountCursorAdapter)
    }

    /**
     * Returns a comma separated list of account types which can be parent accounts for the specified `type`.
     * The strings in the list are the [AccountType.name]s of the different types.
     * @param type [AccountType]
     * @return String comma separated list of account types
     */
    private fun getAllowedParentAccountTypes(type: AccountType): String {
        when (type) {
            AccountType.EQUITY -> return "'" + AccountType.EQUITY.name + "'"

            AccountType.INCOME, AccountType.EXPENSE -> return "'" + AccountType.EXPENSE.name + "', '" + AccountType.INCOME.name + "'"

            AccountType.CASH, AccountType.BANK, AccountType.CREDIT, AccountType.ASSET, AccountType.LIABILITY, AccountType.PAYABLE, AccountType.RECEIVABLE, AccountType.CURRENCY, AccountType.STOCK, AccountType.MUTUAL -> {
                val accountTypeStrings =
                    this.accountTypeStringList
                accountTypeStrings.remove(AccountType.EQUITY.name)
                accountTypeStrings.remove(AccountType.EXPENSE.name)
                accountTypeStrings.remove(AccountType.INCOME.name)
                accountTypeStrings.remove(AccountType.ROOT.name)
                return "'" + TextUtils.join("','", accountTypeStrings) + "'"
            }

            AccountType.TRADING -> return "'" + AccountType.TRADING.name + "'"

            AccountType.ROOT -> return AccountType.entries.toTypedArray().contentToString()
                .replace("\\[|]".toRegex(), "")

            else -> return AccountType.entries.toTypedArray().contentToString()
                .replace("\\[|]".toRegex(), "")
        }
    }

    private val accountTypeStringList: MutableList<String?>
        /**
         * Returns a list of all the available [AccountType]s as strings
         * @return String list of all account types
         */
        get() {
            val accountTypes =
                AccountType.entries.toTypedArray().contentToString().replace("\\[|]".toRegex(), "")
                    .split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val accountTypesList: MutableList<String?> = ArrayList<String?>()
            for (accountType in accountTypes) {
                accountTypesList.add(accountType.trim { it <= ' ' })
            }

            return accountTypesList
        }

    /**
     * Loads the list of account types into the account type selector spinner
     */
    private fun loadAccountTypesList() {
        val accountTypes = getResources().getStringArray(R.array.account_type_entry_values)
        val accountTypesAdapter = ArrayAdapter<String?>(
            requireActivity(), android.R.layout.simple_list_item_1, accountTypes
        )

        accountTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mAccountTypeSpinner!!.setAdapter(accountTypesAdapter)
    }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private fun finishFragment() {
        val imm = requireActivity().getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        imm.hideSoftInputFromWindow(mNameEditText!!.getWindowToken(), 0)

        val action = requireActivity().getIntent().getAction()
        if (action != null && action == Intent.ACTION_INSERT_OR_EDIT) {
            requireActivity().setResult(Activity.RESULT_OK)
            requireActivity().finish()
        } else {
            requireActivity().getSupportFragmentManager().popBackStack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mParentAccountCursor != null) {
            mParentAccountCursor!!.close()
        }
        if (mDefaultTransferAccountCursorAdapter != null) {
            mDefaultTransferAccountCursorAdapter!!.getCursor().close()
        }
    }

    /**
     * Reads the fields from the account form and saves as a new account
     */
    private fun saveAccount() {
        Log.i("AccountFormFragment", "Saving account")
        if (mAccountsDbAdapter == null) mAccountsDbAdapter = AccountsDbAdapter.getInstance()
        // accounts to update, in case we're updating full names of a sub account tree
        val accountsToUpdate = ArrayList<Account>()
        var nameChanged = false
        if (mAccount == null) {
            val name = this.enteredName
            if (name.length == 0) {
                mTextInputLayout!!.setErrorEnabled(true)
                mTextInputLayout!!.setError(getString(R.string.toast_no_account_name_entered))
                return
            }
            mAccount = Account(this.enteredName)
            mAccountsDbAdapter!!.addRecord(
                mAccount!!,
                DatabaseAdapter.UpdateMethod.insert
            ) //new account, insert it
        } else {
            nameChanged = mAccount!!.getName() != this.enteredName
            mAccount!!.setName(this.enteredName)
        }

        val commodityId = mCurrencySpinner!!.getSelectedItemId()
        val commodity = CommoditiesDbAdapter.getInstance().getRecord(commodityId)
        mAccount!!.setCommodity(commodity)

        mAccount!!.setAccountType(selectedAccountType)

        mAccount!!.setDescription(mDescriptionEditText!!.getText().toString())
        mAccount!!.setPlaceHolderFlag(mPlaceholderCheckBox!!.isChecked())
        mAccount!!.setColor(mSelectedColor)

        val newParentAccountId: Long
        val newParentAccountUID: String?
        if (mParentCheckBox!!.isChecked()) {
            newParentAccountId = mParentAccountSpinner!!.getSelectedItemId()
            newParentAccountUID = mAccountsDbAdapter!!.getUID(newParentAccountId)
            mAccount!!.setParentUID(newParentAccountUID)
        } else {
            //need to do this explicitly in case user removes parent account
            newParentAccountUID = mRootAccountUID
            newParentAccountId = mRootAccountId
        }
        mAccount!!.setParentUID(newParentAccountUID)

        if (mDefaultTransferAccountCheckBox!!.isChecked()
            && mDefaultTransferAccountSpinner!!.getSelectedItemId() != Spinner.INVALID_ROW_ID
        ) {
            val id = mDefaultTransferAccountSpinner!!.getSelectedItemId()
            mAccount!!.setDefaultTransferAccountUID(mAccountsDbAdapter!!.getUID(id))
        } else {
            //explicitly set in case of removal of default account
            mAccount!!.setDefaultTransferAccountUID(null)
        }

        val parentAccountId =
            if (mParentAccountUID == null) -1 else mAccountsDbAdapter!!.getID(mParentAccountUID!!)
        // update full names
        if (nameChanged || mDescendantAccountUIDs == null || newParentAccountId != parentAccountId) {
            // current account name changed or new Account or parent account changed
            val newAccountFullName: String?
            if (newParentAccountId == mRootAccountId) {
                newAccountFullName = mAccount!!.getName()
            } else {
                newAccountFullName = mAccountsDbAdapter!!.getAccountFullName(newParentAccountUID) +
                        AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + mAccount!!.getName()
            }
            mAccount!!.setFullName(newAccountFullName)
            if (mDescendantAccountUIDs != null) {
                // modifying existing account, e.t. name changed and/or parent changed
                if ((nameChanged || parentAccountId != newParentAccountId) && mDescendantAccountUIDs!!.size > 0) {
                    // parent change, update all full names of descent accounts
                    accountsToUpdate.addAll(
                        mAccountsDbAdapter!!.getSimpleAccountList(
                            DatabaseSchema.AccountEntry.COLUMN_UID + " IN ('" +
                                    TextUtils.join("','", mDescendantAccountUIDs!!) + "')",
                            null,
                            null
                        )
                    )
                }
                val mapAccount = HashMap<String?, Account>()
                for (acct in accountsToUpdate) mapAccount.put(acct.getUID(), acct)
                mDescendantAccountUIDs?.let { accountUIDs ->
                    accountUIDs.forEach { uid ->
                        // mAccountsDbAdapter.getDescendantAccountUIDs() will ensure a parent-child order
                        val acct: Account = mapAccount.get(uid)!!
                        // mAccount cannot be root, so acct here cannot be top level account.
                        if (mAccount!!.getUID() == acct.getParentUID()) {
                            acct.setFullName(mAccount!!.getFullName() + AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + acct.getName())
                        } else {
                            acct.setFullName(
                                mapAccount.get(acct.getParentUID())!!.getFullName() +
                                        AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR +
                                        acct.getName()
                            )
                        }
                    }
                }
            }
        }
        accountsToUpdate.add(mAccount!!)

        // bulk update, will not update transactions
        mAccountsDbAdapter!!.bulkAddRecords(accountsToUpdate, DatabaseAdapter.UpdateMethod.update)

        finishFragment()
    }

    private val selectedAccountType: AccountType
        /**
         * Returns the currently selected account type in the spinner
         * @return [AccountType] currently selected
         */
        get() {
            val selectedAccountTypeIndex = mAccountTypeSpinner!!.getSelectedItemPosition()
            val accountTypeEntries = getResources().getStringArray(R.array.key_account_type_entries)
            return AccountType.valueOf(accountTypeEntries[selectedAccountTypeIndex]!!)
        }

    private val enteredName: String
        /**
         * Retrieves the name of the account which has been entered in the EditText
         * @return Name of the account which has been entered in the EditText
         */
        get() = mNameEditText!!.getText().toString().trim { it <= ' ' }

    companion object {
        /**
         * Tag for the color picker dialog fragment
         */
        private const val COLOR_PICKER_DIALOG_TAG = "color_picker_dialog"

        /**
         * Construct a new instance of the dialog
         * @return New instance of the dialog fragment
         */
        fun newInstance(): AccountFormFragment {
            val f = AccountFormFragment()
            f.mAccountsDbAdapter = AccountsDbAdapter.getInstance()
            return f
        }
    }
}