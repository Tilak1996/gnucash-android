package org.gnucash.android.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.databinding.ActivitySettingsBinding
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.passcode.PasscodeLockActivity

@AndroidEntryPoint
class PreferenceActivity: PasscodeLockActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG,"onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val actionBar = checkNotNull(supportActionBar)
        actionBar.apply {
            title = getString(R.string.title_settings)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.navHostFragment?.let {
            val navController = it.findNavController()
            val appBarConfiguration = AppBarConfiguration(setOf(R.id.preferenceHeadersFragment))
            setupActionBarWithNavController(navController, appBarConfiguration)

            val action = intent.action
            if (action != null && action == ACTION_MANAGE_BOOKS) {
                navController.navigate(R.id.action_preferenceHeadersFragment_to_bookManagerFragment)
            }
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