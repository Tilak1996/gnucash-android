package org.gnucash.android.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.account.AccountsActivity
import javax.inject.Inject

class Repository @Inject constructor(
    @ApplicationContext private val context: Context,
    val booksDbAdapter: BooksDbAdapter
) {

    /**
     * Activates the book with unique identifer `bookUID`, and refreshes the database adapters
     * @param bookUID GUID of the book to be activated
     */
    fun activateBook(bookUID: String) {
        booksDbAdapter.setActive(bookUID)
        GnuCashApplication.initializeDatabaseAdapters()
    }

    /**
     * Loads the book with GUID `bookUID` and opens the AccountsActivity
     * @param bookUID GUID of the book to be loaded
     */
    fun loadBook(bookUID: String) {
        activateBook(bookUID)
        AccountsActivity.start(context)
    }

}