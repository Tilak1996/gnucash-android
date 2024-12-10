package org.gnucash.android.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.databinding.ActivitySettingsBinding
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.passcode.PasscodeLockActivity

@AndroidEntryPoint
class PreferenceActivity: PasscodeLockActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG,"onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val actionBar = checkNotNull(supportActionBar)

        val action = intent.action
        if (action != null && action == ACTION_MANAGE_BOOKS) {
            loadFragment(BookManagerFragment())
        } else {
            loadFragment(GeneralPreferenceFragment())
        }

        actionBar.apply {
            title = getString(R.string.title_settings)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat?,
        pref: Preference): Boolean {
        Log.i(TAG,"onPreferenceStartFragment")
        val fragment: Fragment?
        try {
            val clazz = Class.forName(pref.fragment)
            fragment = clazz.newInstance() as Fragment
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            //if we do not have a matching class, do nothing
            return false
        } catch (e: InstantiationException) {
            e.printStackTrace()
            return false
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            return false
        }
        loadFragment(fragment)
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(TAG,"onOptionsItemSelected: ${item.itemId}")
        when (item.itemId) {
            android.R.id.home -> {
                val fm = fragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    finish()
                }
                return true
            }

            else -> return false
        }
    }

    /**
     * Load the provided fragment into the right pane, replacing the previous one
     * @param fragment BaseReportFragment instance
     */
    private fun loadFragment(fragment: Fragment) {
        Log.i(TAG,"loadFragment")
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, fragment)
            commit()
        }
    }

    companion object {

        const val TAG = "PreferenceActivity"

        const val ACTION_MANAGE_BOOKS: String = "org.gnucash.android.intent.action.MANAGE_BOOKS"

        // TODO Get both getActiveBookSharedPreferences and getBookSharedPreferences and move them in
        //  repository.
        /**
         * Return the [SharedPreferences] for a specific book
         * @param bookUID GUID of the book
         * @return Shared preferences
         */
        @JvmStatic
        fun getBookSharedPreferences(bookUID: String?): SharedPreferences =
            GnuCashApplication.getAppContext().getSharedPreferences(bookUID, MODE_PRIVATE)
        /**
         * Returns the shared preferences file for the currently active book.
         * Should be used instead of [PreferenceManager.getDefaultSharedPreferences]
         * @return Shared preferences file
         */
        @JvmStatic
        fun getActiveBookSharedPreferences() =
            getBookSharedPreferences(BooksDbAdapter.getInstance().activeBookUID)
    }
}