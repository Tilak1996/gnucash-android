package org.gnucash.android.ui.settings;

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentGeneralHeadersBinding
import org.gnucash.android.ui.settings.adapter.PreferenceHeadersAdapter
import org.gnucash.android.ui.settings.data.GeneralItem


/**
 * Fragment for displaying preference headers
 * @author Tilak Patel
 */
class PreferenceHeadersFragment: Fragment() {

    private lateinit var generalItems: List<GeneralItem>
    private lateinit var binding: FragmentGeneralHeadersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val items = mutableListOf<GeneralItem>()
        items.add(
            GeneralItem(
            context?.getString(R.string.header_general_settings) ?: "",
                R.id.action_preferenceHeadersFragment_to_generalPreferenceFragment
            )
        )
        items.add(
            GeneralItem(
                context?.getString(R.string.title_manage_books) ?: "",
                R.id.action_preferenceHeadersFragment_to_bookManagerFragment
            )
        )
        items.add(
            GeneralItem(
                context?.getString(R.string.header_account_settings) ?: "",
                R.id.action_preferenceHeadersFragment_to_accountPreferencesFragment
            )
        )
        items.add(
            GeneralItem(
                context?.getString(R.string.header_transaction_settings) ?: "",
                R.id.action_preferenceHeadersFragment_to_transactionsPreferenceFragment
            )
        )
        items.add(
            GeneralItem(
                context?.getString(R.string.header_backup_and_export_settings) ?: "",
                R.id.action_preferenceHeadersFragment_to_backupPreferenceFragment
            )
        )
        items.add(
            GeneralItem(
                context?.getString(R.string.header_about_gnucash) ?: "",
                R.id.action_preferenceHeadersFragment_to_aboutPreferenceFragment
            )
        )
        items.add(
            GeneralItem(
                context?.getString(R.string.label_recommend_app) ?: "",
                0
            )
        )
        generalItems = items
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGeneralHeadersBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navController = binding.rv.findNavController()
        binding.rv.adapter = PreferenceHeadersAdapter(context, generalItems, navController)
        binding.rv.layoutManager = LinearLayoutManager(context)
    }

}
