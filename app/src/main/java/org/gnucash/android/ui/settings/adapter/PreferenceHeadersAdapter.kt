package org.gnucash.android.ui.settings.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.databinding.GeneralHeaderItemBinding
import org.gnucash.android.ui.settings.data.GeneralItem

class PreferenceHeadersAdapter(
    private val context: Context?,
    private val itemsList: List<GeneralItem>,
    private val navController: NavController
): RecyclerView.Adapter<PreferenceHeadersAdapter.PreferenceHeaderViewHolder>() {

    class PreferenceHeaderViewHolder(
        val binding: GeneralHeaderItemBinding
    ): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PreferenceHeaderViewHolder(
            GeneralHeaderItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun getItemCount() = itemsList.size

    override fun onBindViewHolder(holder: PreferenceHeaderViewHolder, position: Int) {
        val item = itemsList[position]
        holder.binding.title.text = item.title
        if(item.navigationPath != 0) {
            holder.binding.root.setOnClickListener {
                navController.navigate(item.navigationPath)
            }
        } else {
            context?.let {
                holder.binding.root.setOnClickListener {
                    val uri = Uri.parse("market://details?id=org.gnucash.android")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                }
            }

        }
    }

}