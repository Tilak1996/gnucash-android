package org.gnucash.android.model

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.gnucash.android.R
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.passcode.PasscodeLockActivity.MODE_PRIVATE
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Return the [SharedPreferences] for a specific book
     * @param bookUID GUID of the book
     * @return Shared preferences
     */
    fun getBookSharedPreferences(bookUID: String?): SharedPreferences =
        context.getSharedPreferences(bookUID, MODE_PRIVATE)

    /**
     * Returns the shared preferences file for the currently active book.
     * Should be used instead of [PreferenceManager.getDefaultSharedPreferences]
     * @return Shared preferences file
     */
    fun getActiveBookSharedPreferences() =
        getBookSharedPreferences(BooksDbAdapter.getInstance().activeBookUID)

    /**
     * Returns `true` if double entry is enabled in the app settings, `false` otherwise.
     * If the value is not set, the default value can be specified in the parameters.
     * @return `true` if double entry is enabled, `false` otherwise
     */
    fun isDoubleEntryEnabled() =
        getActiveBookSharedPreferences()
            .getBoolean(context.getString(R.string.key_use_double_entry), true)
}