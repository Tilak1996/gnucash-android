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
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.Repository
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.model.importer.ImportAsyncUtil
import org.gnucash.android.ui.account.AccountsActivity.Companion.REQUEST_PICK_ACCOUNTS_FILE
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.ui.wizard.FirstRunWizardActivity
import org.gnucash.android.util.BookUtils
import javax.inject.Inject

/**
 * Manages actions related to accounts, displaying, exporting and creating new accounts
 * The various actions are implemented as Fragments which are then added to this activity
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 */
@AndroidEntryPoint
class AccountsActivity : BaseDrawerActivity(), OnAccountClickedListener {
    /**
     * Map containing fragments for the different tabs
     */
    private val mFragmentPageReferenceMap = SparseArray<Refreshable?>()

    /**
     * ViewPager which manages the different tabs
     */
    private var mViewPager: ViewPager? = null
    private var mFloatingActionButton: FloatingActionButton? = null
    private var mCoordinatorLayout: CoordinatorLayout? = null

    @Inject
    var mRepository: Repository? = null

    /**
     * Configuration for rating the app
     */
    //    public static RateThisApp.Config rateAppConfig = new RateThisApp.Config(14, 100);
    private var mPagerAdapter: AccountViewPagerAdapter? = null

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    inner class AccountViewPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        override fun getItem(i: Int): Fragment {
            var currentFragment = mFragmentPageReferenceMap.get(i) as AccountsListFragment?
            if (currentFragment == null) {
                when (i) {
                    INDEX_RECENT_ACCOUNTS_FRAGMENT -> currentFragment =
                        AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.RECENT)

                    INDEX_FAVORITE_ACCOUNTS_FRAGMENT -> currentFragment =
                        AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.FAVORITES)

                    INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT -> currentFragment =
                        AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL)

                    else -> currentFragment =
                        AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL)
                }
                mFragmentPageReferenceMap.put(i, currentFragment)
            }
            return currentFragment
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            super.destroyItem(container, position, `object`)
            mFragmentPageReferenceMap.remove(position)
        }

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                INDEX_RECENT_ACCOUNTS_FRAGMENT -> return getString(R.string.title_recent_accounts)

                INDEX_FAVORITE_ACCOUNTS_FRAGMENT -> return getString(R.string.title_favorite_accounts)

                INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT -> return getString(R.string.title_all_accounts)
                else -> return getString(R.string.title_all_accounts)
            }
        }

        override fun getCount(): Int {
            return DEFAULT_NUM_PAGES
        }
    }

    val currentAccountListFragment: AccountsListFragment
        get() {
            val index = mViewPager!!.getCurrentItem()
            var fragment = mFragmentPageReferenceMap.get(index) as Fragment?
            if (fragment == null) fragment = mPagerAdapter!!.getItem(index)
            return fragment as AccountsListFragment
        }

    override fun getContentView(): Int {
        return R.layout.activity_accounts
    }

    override fun getTitleRes(): Int {
        return R.string.title_accounts
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mViewPager = findViewById<ViewPager?>(R.id.pager)
        mFloatingActionButton = findViewById<FloatingActionButton?>(R.id.fab_create_account)
        mCoordinatorLayout = findViewById<CoordinatorLayout?>(R.id.coordinatorLayout)


        val intent = getIntent()
        handleOpenFileIntent(intent)

        init()

        val tabLayout = findViewById<View?>(R.id.tab_layout) as TabLayout
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_recent_accounts))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_all_accounts))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_favorite_accounts))
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL)

        //show the simple accounts list
        mPagerAdapter = AccountViewPagerAdapter(getSupportFragmentManager())
        mViewPager!!.setAdapter(mPagerAdapter)

        mViewPager!!.addOnPageChangeListener(TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mViewPager!!.setCurrentItem(tab.getPosition())
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                //nothing to see here, move along
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                //nothing to see here, move along
            }
        })

        setCurrentTab()

        mFloatingActionButton!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val addAccountIntent = Intent(this@AccountsActivity, FormActivity::class.java)
                addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT)
                addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
                startActivityForResult(addAccountIntent, REQUEST_EDIT_ACCOUNT)
            }
        })
    }

    override fun onStart() {
        super.onStart()

        if (BuildConfig.CAN_REQUEST_RATING) {
//            RateThisApp.init(rateAppConfig);
//            RateThisApp.onStart(this);
//            RateThisApp.showRateDialogIfNeeded(this);
        }
    }

    /**
     * Handles the case where another application has selected to open a (.gnucash or .gnca) file with this app
     * @param intent Intent containing the data to be imported
     */
    private fun handleOpenFileIntent(intent: Intent) {
        //when someone launches the app to view a (.gnucash or .gnca) file
        val data = intent.getData()
        if (data != null) {
            mRepository!!.backupActiveBook()
            intent.setData(null)
            val progressDialog = ProgressDialog(this)
            ImportAsyncUtil.importDataSingle(this, data)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<Pair<Boolean, String>> {
                    override fun onSubscribe(d: Disposable) {
                        mCompositeDisposable.add(d)

                        progressDialog.setTitle(R.string.title_progress_importing_accounts)
                        progressDialog.setIndeterminate(true)
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                        progressDialog.show()

                        //these methods must be called after progressDialog.show()
                        progressDialog.setProgressNumberFormat(null)
                        progressDialog.setProgressPercentFormat(null)
                    }

                    override fun onSuccess(result: Pair<Boolean, String>) {
                        if (progressDialog.isShowing()) progressDialog.dismiss()

                        val message =
                            if (result.first) R.string.toast_success_importing_accounts else R.string.toast_error_importing_accounts
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()

                        if (result.second != null) BookUtils.loadBook(result.second!!)
                        removeFirstRunFlag()
                    }

                    override fun onError(e: Throwable) {
                    }
                })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setCurrentTab()

        val index = mViewPager!!.getCurrentItem()
        val fragment = mFragmentPageReferenceMap.get(index) as Fragment?
        if (fragment != null) (fragment as Refreshable).refresh()

        handleOpenFileIntent(intent)
    }

    /**
     * Sets the current tab in the ViewPager
     */
    fun setCurrentTab() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastTabIndex =
            preferences.getInt(LAST_OPEN_TAB_INDEX, INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT)
        val index = getIntent().getIntExtra(EXTRA_TAB_INDEX, lastTabIndex)
        mViewPager!!.setCurrentItem(index)
    }

    /**
     * Loads default setting for currency and performs app first-run initialization.
     *
     * Also handles displaying the What's New dialog
     */
    private fun init() {
        PreferenceManager.setDefaultValues(
            this, BooksDbAdapter.getInstance().getActiveBookUID(),
            MODE_PRIVATE, R.xml.fragment_transaction_preferences, true
        )

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val firstRun = prefs.getBoolean(getString(R.string.key_first_run), true)

        if (firstRun) {
            startActivity(
                Intent(
                    GnuCashApplication.getAppContext(),
                    FirstRunWizardActivity::class.java
                )
            )

            //default to using double entry and save the preference explicitly
            prefs.edit().putBoolean(getString(R.string.key_use_double_entry), true).apply()
            finish()
            return
        }

        if (hasNewFeatures()) {
            showWhatsNewDialog(this)
        }
        GnuCashApplication.startScheduledActionExecutionService(this)
        mRepository!!.schedulePeriodicBackups(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit().putInt(LAST_OPEN_TAB_INDEX, mViewPager!!.getCurrentItem()).apply()
        mCompositeDisposable.clear()
    }

    /**
     * Checks if the minor version has been increased and displays the What's New dialog box.
     * This is the minor version as per semantic versioning.
     * @return `true` if the minor version has been increased, `false` otherwise.
     */
    private fun hasNewFeatures(): Boolean {
        val minorVersion = getResources().getString(R.string.app_minor_version)
        val currentMinor = minorVersion.toInt()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val previousMinor = prefs.getInt(getString(R.string.key_previous_minor_version), 0)
        if (currentMinor > previousMinor) {
            val editor = prefs.edit()
            editor.putInt(getString(R.string.key_previous_minor_version), currentMinor)
            editor.apply()
            return true
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = getMenuInflater()
        inflater.inflate(R.menu.global_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> return super.onOptionsItemSelected(item)

            else -> return false
        }
    }

    override fun accountSelected(accountUID: String?) {
        val intent = Intent(this, TransactionsActivity::class.java)
        intent.setAction(Intent.ACTION_VIEW)
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)

        startActivity(intent)
    }

    companion object {
        /**
         * Request code for GnuCash account structure file to import
         */
        const val REQUEST_PICK_ACCOUNTS_FILE: Int = 0x1

        /**
         * Request code for opening the account to edit
         */
        const val REQUEST_EDIT_ACCOUNT: Int = 0x10

        /**
         * Logging tag
         */
        protected const val LOG_TAG: String = "AccountsActivity"

        /**
         * Number of pages to show
         */
        private const val DEFAULT_NUM_PAGES = 3

        /**
         * Index for the recent accounts tab
         */
        const val INDEX_RECENT_ACCOUNTS_FRAGMENT: Int = 0

        /**
         * Index of the top level (all) accounts tab
         */
        const val INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT: Int = 1

        /**
         * Index of the favorite accounts tab
         */
        const val INDEX_FAVORITE_ACCOUNTS_FRAGMENT: Int = 2

        /**
         * Used to save the index of the last open tab and restore the pager to that index
         */
        const val LAST_OPEN_TAB_INDEX: String = "last_open_tab"

        /**
         * Key for putting argument for tab into bundle arguments
         */
        const val EXTRA_TAB_INDEX: String = "org.gnucash.android.extra.TAB_INDEX"

        private val mCompositeDisposable = CompositeDisposable()

        /**
         * Show dialog with new features for this version
         */
        fun showWhatsNewDialog(context: Context): AlertDialog? {
            val resources = context.getResources()
            val releaseTitle = StringBuilder(resources.getString(R.string.title_whats_new))
            val packageInfo: PackageInfo
            try {
                packageInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                releaseTitle.append(" - v").append(packageInfo.versionName)
            } catch (e: PackageManager.NameNotFoundException) {
//            Crashlytics.logException(e);
                Log.e(LOG_TAG, "Error displaying 'Whats new' dialog")
            }

            return AlertDialog.Builder(context)
                .setTitle(releaseTitle.toString())
                .setMessage(R.string.whats_new)
                .setPositiveButton(
                    R.string.label_dismiss,
                    object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            dialog.dismiss()
                        }
                    }).show()
        }

        /**
         * Displays the dialog for exporting transactions
         */
        fun openExportFragment(activity: AppCompatActivity) {
            val intent = Intent(activity, FormActivity::class.java)
            intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name)
            activity.startActivity(intent)
        }

        /**
         * Creates default accounts with the specified currency code.
         * If the currency parameter is null, then locale currency will be used if available
         *
         * @param currencyCode Currency code to assign to the imported accounts
         * @param activity Activity for providing context and displaying dialogs
         */
        fun createDefaultAccounts(currencyCode: String?, activity: Activity?) {
            val uri =
                Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.default_accounts)
            val progressDialog = ProgressDialog(activity)
            ImportAsyncUtil.importDataSingle(activity, uri)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<Pair<Boolean, String>> {
                    override fun onSubscribe(d: Disposable) {
                        mCompositeDisposable.add(d)

                        progressDialog.setTitle(R.string.title_progress_importing_accounts)
                        progressDialog.setIndeterminate(true)
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                        progressDialog.show()

                        //these methods must be called after progressDialog.show()
                        progressDialog.setProgressNumberFormat(null)
                        progressDialog.setProgressPercentFormat(null)
                    }

                    override fun onSuccess(result: Pair<Boolean, String>) {
                        if (progressDialog.isShowing()) progressDialog.dismiss()

                        val message =
                            if (result.first) R.string.toast_success_importing_accounts else R.string.toast_error_importing_accounts
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

                        if (result.second != null) BookUtils.loadBook(result.second!!)

                        if (currencyCode != null) {
                            AccountsDbAdapter.getInstance().updateAllAccounts(
                                DatabaseSchema.AccountEntry.COLUMN_CURRENCY,
                                currencyCode
                            )
                            GnuCashApplication.setDefaultCurrencyCode(currencyCode)
                        }
                    }

                    override fun onError(e: Throwable) {
                    }
                })
            //        new ImportAsyncTask(activity, delegate).execute(uri);
        }

        /**
         * Starts Intent chooser for selecting a GnuCash accounts file to import.
         *
         * The `activity` is responsible for the actual import of the file and can do so by calling [.importXmlFileFromIntent]<br></br>
         * The calling class should respond to the request code [REQUEST_PICK_ACCOUNTS_FILE] in its [.onActivityResult] method
         * @param activity Activity starting the request and will also handle the response
         * @see .importXmlFileFromIntent
         */
        fun startXmlFileChooser(activity: Activity) {
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE)
            pickIntent.setType("*/*")
            val chooser = Intent.createChooser(
                pickIntent,
                "Select GnuCash account file"
            ) //todo internationalize string

            try {
                activity.startActivityForResult(chooser, REQUEST_PICK_ACCOUNTS_FILE)
            } catch (ex: ActivityNotFoundException) {
//            Crashlytics.log("No file manager for selecting files available");
//            Crashlytics.logException(ex);
                Toast.makeText(activity, R.string.toast_install_file_manager, Toast.LENGTH_LONG)
                    .show()
            }
        }

        /**
         * Overloaded method.
         * Starts chooser for selecting a GnuCash account file to import
         * @param fragment Fragment creating the chooser and which will also handle the result
         * @see .startXmlFileChooser
         */
        fun startXmlFileChooser(fragment: Fragment) {
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE)
            pickIntent.setType("*/*")
            val chooser = Intent.createChooser(
                pickIntent,
                "Select GnuCash account file"
            ) //todo internationalize string

            try {
                fragment.startActivityForResult(chooser, REQUEST_PICK_ACCOUNTS_FILE)
            } catch (ex: ActivityNotFoundException) {
//            Crashlytics.log("No file manager for selecting files available");
//            Crashlytics.logException(ex);
                Toast.makeText(
                    fragment.getActivity(),
                    R.string.toast_install_file_manager,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        /**
         * Starts the AccountsActivity and clears the activity stack
         * @param context Application context
         */
        fun start(context: Context) {
            val accountsActivityIntent = Intent(context, AccountsActivity::class.java)
            accountsActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            accountsActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(accountsActivityIntent)
        }

        /**
         * Removes the flag indicating that the app is being run for the first time.
         * This is called every time the app is started because the next time won't be the first time
         */
        fun removeFirstRunFlag() {
            val context = GnuCashApplication.getAppContext()
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.putBoolean(context.getString(R.string.key_first_run), false)
            editor.commit()
        }
    }
}