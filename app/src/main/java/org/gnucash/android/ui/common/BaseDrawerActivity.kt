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
package org.gnucash.android.ui.common

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.Repository
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.report.ReportsActivity
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.ScheduledActionsActivity
import org.gnucash.android.util.BookUtils
import javax.inject.Inject

/**
 * Base activity implementing the navigation drawer, to be extended by all activities requiring one.
 *
 * Each activity inheriting from this class has an indeterminate progress bar at the top,
 * (above the action bar) which can be used to display busy operations. See [getProgressBar].
 *
 * The activity layout of the subclass is expected to contain `DrawerLayout` and
 * a `NavigationView`.
 * Sub-class should also consider using the `toolbar.xml` or `toolbar_with_spinner.xml`
 * for the action bar in their XML layout. Otherwise provide another which contains widgets for the
 * toolbar and progress indicator with the IDs `R.id.toolbar` and `R.id.progress_indicator` respectively.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
@AndroidEntryPoint
abstract class BaseDrawerActivity : PasscodeLockActivity(), PopupMenu.OnMenuItemClickListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarProgress: ProgressBar

    protected lateinit var mBookNameTextView: TextView
    protected lateinit var mDrawerToggle: ActionBarDrawerToggle

    @Inject
    lateinit var repository: Repository

    private inner class DrawerItemClickListener : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
            onDrawerMenuItemClicked(menuItem.itemId)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getContentView())

        //if a parameter was passed to open an account within a specific book, then switch
        val bookUID = intent.getStringExtra(UxArgument.BOOK_UID)
        if (bookUID != null && bookUID != BooksDbAdapter.getInstance().activeBookUID) {
            BookUtils.activateBook(bookUID)
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        toolbarProgress = findViewById(R.id.toolbar_progress)
        setSupportActionBar(toolbar)
        supportActionBar?.let { actionBar ->
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(getTitleRes())
        }

        toolbarProgress.indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<View>(R.id.drawer_title).setOnClickListener { view ->
            onClickAppTitle(view)
        }

        mBookNameTextView = headerView.findViewById(R.id.book_name)
        mBookNameTextView.setOnClickListener { view ->
            onClickBook(view)
        }
        updateActiveBookName()
        setUpNavigationDrawer()
    }

    override fun onResume() {
        super.onResume()
        updateActiveBookName()
    }

    /**
     * Return the layout to inflate for this activity
     *
     * @return Layout resource identifier
     */
    @LayoutRes
    abstract fun getContentView(): Int

    /**
     * Return the title for this activity.
     * This will be displayed in the action bar
     *
     * @return String resource identifier
     */
    @StringRes
    abstract fun getTitleRes(): Int

    /**
     * Returns the progress bar for the activity.
     *
     * This progress bar is displayed above the toolbar and should be used to show busy status
     * for long operations.
     * The progress bar visibility is set to [View.GONE] by default. Make visible to use.
     *
     * @return Indeterminate progress bar.
     */
    fun getProgressBar(): ProgressBar {
        return toolbarProgress
    }

    /**
     * Sets up the navigation drawer for this activity.
     */
    private fun setUpNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(DrawerItemClickListener())

        mDrawerToggle = object : ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            /** Called when a drawer has settled in a completely closed state. */
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
            }

            /** Called when a drawer has settled in a completely open state. */
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
            }
        }

        drawerLayout.setDrawerListener(mDrawerToggle)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.openDrawer(navigationView)
            } else {
                drawerLayout.closeDrawer(navigationView)
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Update the display name of the currently active book
     */
    protected fun updateActiveBookName() {
        mBookNameTextView.text = BooksDbAdapter.getInstance().activeBookDisplayName
    }

    /**
     * Handler for the navigation drawer items
     */
    protected fun onDrawerMenuItemClicked(itemId: Int) {
        when (itemId) {
            R.id.nav_item_open -> {
                //use the storage access framework
                val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT)
                openDocument.addCategory(Intent.CATEGORY_OPENABLE)
                openDocument.type = "text/*|application/*"
                val mimeTypes = arrayOf("text/*", "application/*")
                openDocument.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                startActivityForResult(openDocument, REQUEST_OPEN_DOCUMENT)
            }

            R.id.nav_item_favorites -> {
                val intent = Intent(this, AccountsActivity::class.java)
                intent.putExtra(
                    AccountsActivity.EXTRA_TAB_INDEX,
                    AccountsActivity.INDEX_FAVORITE_ACCOUNTS_FRAGMENT
                )
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }

            R.id.nav_item_reports -> {
                val intent = Intent(this, ReportsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }

            /*
            //todo: Re-enable this when Budget UI is complete
            R.id.nav_item_budgets -> startActivity(Intent(this, BudgetsActivity::class.java))
            */
            R.id.nav_item_scheduled_actions -> {
                val intent = Intent(this, ScheduledActionsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }

            R.id.nav_item_export -> AccountsActivity.openExportFragment(this)

            R.id.nav_item_settings -> startActivity(Intent(this, PreferenceActivity::class.java))

            R.id.nav_item_help -> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit().putBoolean(UxArgument.SKIP_PASSCODE_SCREEN, true).apply()
            }
        }
        drawerLayout.closeDrawer(navigationView)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_CANCELED) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        when (requestCode) {
            AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE -> {
                val intentData = requireNotNull(data)
                repository.importXmlFileFromIntent(this, intentData, null)
            }

            REQUEST_OPEN_DOCUMENT -> {
                val intentData = requireNotNull(data)
                val takeFlags = intentData.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                repository.importXmlFileFromIntent(this, intentData, null)
                contentResolver.takePersistableUriPermission(intentData.data!!, takeFlags)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == ID_MANAGE_BOOKS) {
            val intent = Intent(this, PreferenceActivity::class.java)
            intent.action = PreferenceActivity.ACTION_MANAGE_BOOKS
            startActivity(intent)
            drawerLayout.closeDrawer(navigationView)
            return true
        }
        val booksDbAdapter = BooksDbAdapter.getInstance()
        val bookUID = booksDbAdapter.getUID(id.toLong())
        if (bookUID != booksDbAdapter.activeBookUID) {
            BookUtils.loadBook(bookUID)
            finish()
        }
        AccountsActivity.start(GnuCashApplication.getAppContext())
        return true
    }

    fun onClickAppTitle(view: View?) {
        drawerLayout.closeDrawer(navigationView)
        AccountsActivity.start(this)
    }

    fun onClickBook(view: View) {
        val popup = PopupMenu(this, view)
        popup.setOnMenuItemClickListener(this)

        val menu = popup.menu
        var maxRecent = 0
        val cursor: Cursor = BooksDbAdapter.getInstance().fetchAllRecords(
            null,
            null,
            DatabaseSchema.BookEntry.COLUMN_MODIFIED_AT + " DESC"
        )
        while (cursor.moveToNext() && maxRecent++ < 5) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME))
            menu.add(0, id.toInt(), maxRecent, name)
        }
        menu.add(0, ID_MANAGE_BOOKS, maxRecent, R.string.menu_manage_books)

        popup.show()
    }

    companion object {
        const val ID_MANAGE_BOOKS: Int = 0xB00C
        const val REQUEST_OPEN_DOCUMENT: Int = 0x20
    }
}
