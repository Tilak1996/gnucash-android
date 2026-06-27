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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import org.gnucash.android.R
import org.gnucash.android.model.data.Budget
import org.gnucash.android.model.data.BudgetAmount
import org.gnucash.android.model.db.DatabaseSchema
import org.gnucash.android.model.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.db.adapter.BudgetsDbAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.ui.util.widget.EmptyRecyclerView
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Fragment for displaying budget details
 */
class BudgetDetailFragment : Fragment(), Refreshable {
    var mBudgetNameTextView: TextView? = null
    var mBudgetDescriptionTextView: TextView? = null
    var mBudgetRecurrence: TextView? = null
    var mRecyclerView: EmptyRecyclerView? = null

    private var mBudgetUID: String? = null
    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_budget_detail, container, false)
        mBudgetNameTextView = view.findViewById<TextView?>(R.id.primary_text)
        mBudgetDescriptionTextView = view.findViewById<TextView?>(R.id.secondary_text)
        mBudgetRecurrence = view.findViewById<TextView?>(R.id.budget_recurrence)
        mRecyclerView = view.findViewById<EmptyRecyclerView?>(R.id.budget_amount_recycler)
        mBudgetDescriptionTextView!!.setMaxLines(3)

        mRecyclerView!!.setHasFixedSize(true)

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val gridLayoutManager = GridLayoutManager(getActivity(), 2)
            mRecyclerView!!.setLayoutManager(gridLayoutManager)
        } else {
            val mLayoutManager = LinearLayoutManager(getActivity())
            mRecyclerView!!.setLayoutManager(mLayoutManager)
        }
        return view
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance()
        mBudgetUID = requireArguments().getString(UxArgument.BUDGET_UID)
        bindViews()

        setHasOptionsMenu(true)
    }

    private fun bindViews() {
        val budget = mBudgetsDbAdapter!!.getRecord(mBudgetUID!!)
        mBudgetNameTextView!!.setText(budget.getName())

        val description = budget.getDescription()
        if (description != null && !description.isEmpty()) mBudgetDescriptionTextView!!.setText(
            description
        )
        else {
            mBudgetDescriptionTextView!!.setVisibility(View.GONE)
        }
        mBudgetRecurrence!!.setText(budget.getRecurrence().getRepeatString())

        mRecyclerView!!.setAdapter(BudgetAmountAdapter())
    }

    override fun onResume() {
        super.onResume()
        refresh()

        val view = requireActivity().findViewById<View?>(R.id.fab_create_budget)
        if (view != null) {
            view.setVisibility(View.GONE)
        }
    }

    override fun refresh() {
        bindViews()
        val budgetName =
            mBudgetsDbAdapter!!.getAttribute(mBudgetUID!!, DatabaseSchema.BudgetEntry.COLUMN_NAME)
        val actionBar: ActionBar? =
            checkNotNull((getActivity() as AppCompatActivity).getSupportActionBar())
        actionBar!!.setTitle("Budget: " + budgetName)
    }

    override fun refresh(budgetUID: String?) {
        mBudgetUID = budgetUID
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.budget_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menu_edit_budget -> {
                val addAccountIntent = Intent(getActivity(), FormActivity::class.java)
                addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT)
                addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
                addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetUID)
                startActivityForResult(addAccountIntent, 0x11)
                return true
            }

            else -> return false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
        }
    }


    inner class BudgetAmountAdapter :
        RecyclerView.Adapter<BudgetAmountAdapter.BudgetAmountViewHolder>() {
        private val mBudgetAmounts: MutableList<BudgetAmount>
        private val mBudget: Budget

        init {
            mBudget = mBudgetsDbAdapter!!.getRecord(mBudgetUID!!)
            mBudgetAmounts = mBudget.getCompactedBudgetAmounts()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetAmountViewHolder {
            val view = LayoutInflater.from(getActivity())
                .inflate(R.layout.cardview_budget_amount, parent, false)
            return BudgetAmountViewHolder(view)
        }

        override fun onBindViewHolder(holder: BudgetAmountViewHolder, @SuppressLint("RecyclerView") position: Int) {
            val budgetAmount = mBudgetAmounts.get(position)
            val projectedAmount = budgetAmount.getAmount()
            val accountsDbAdapter = AccountsDbAdapter.getInstance()

            holder.budgetAccount.setText(accountsDbAdapter.getAccountFullName(budgetAmount.getAccountUID()))
            holder.budgetAmount.setText(projectedAmount.formattedString())

            val spentAmount = accountsDbAdapter.getAccountBalance(
                budgetAmount.getAccountUID(),
                mBudget.getStartofCurrentPeriod(), mBudget.getEndOfCurrentPeriod()
            )

            holder.budgetSpent.setText(spentAmount.abs().formattedString())
            holder.budgetLeft.setText(projectedAmount.subtract(spentAmount.abs()).formattedString())

            var budgetProgress = 0.0
            if (projectedAmount.asDouble() != 0.0) {
                budgetProgress = spentAmount.asBigDecimal().divide(
                    projectedAmount.asBigDecimal(),
                    spentAmount.getCommodity().getSmallestFractionDigits(),
                    RoundingMode.HALF_EVEN
                ).toDouble()
            }

            holder.budgetIndicator.setProgress((budgetProgress * 100).toInt())
            holder.budgetSpent.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress))
            holder.budgetLeft.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress))

            generateChartData(holder.budgetChart, budgetAmount)

            holder.itemView.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    val intent = Intent(getActivity(), TransactionsActivity::class.java)
                    intent.putExtra(
                        UxArgument.SELECTED_ACCOUNT_UID,
                        mBudgetAmounts.get(position).getAccountUID()
                    )
                    startActivityForResult(intent, 0x10)
                }
            })
        }

        /**
         * Generate the chart data for the chart
         * @param barChart View where to display the chart
         * @param budgetAmount BudgetAmount to visualize
         */
        fun generateChartData(barChart: BarChart, budgetAmount: BudgetAmount) {
            // FIXME: 25.10.15 chart is broken

            val accountsDbAdapter = AccountsDbAdapter.getInstance()

            val barEntries: MutableList<BarEntry?> = ArrayList<BarEntry?>()
            val xVals: MutableList<String?> = ArrayList<String?>()

            //todo: refactor getNumberOfPeriods into budget
            var budgetPeriods = mBudget.getNumberOfPeriods().toInt()
            budgetPeriods = if (budgetPeriods == 0) 12 else budgetPeriods
            val periods = mBudget.getRecurrence().getNumberOfPeriods(budgetPeriods)

            /* FIXME: 15.08.2016 why do we need number of periods */
            for (periodNum in 1..periods) {
                val amount = accountsDbAdapter.getAccountBalance(
                    budgetAmount.getAccountUID(),
                    mBudget.getStartOfPeriod(periodNum), mBudget.getEndOfPeriod(periodNum)
                )
                    .asBigDecimal()

                if (amount == BigDecimal.ZERO) continue

                barEntries.add(BarEntry(amount.toFloat(), periodNum))
                xVals.add(mBudget.getRecurrence().getTextOfCurrentPeriod(periodNum))
            }

            val label = accountsDbAdapter.getAccountName(budgetAmount.getAccountUID())
            val barDataSet = BarDataSet(barEntries, label)

            val barData = BarData(xVals, barDataSet)
            val limitLine = LimitLine(budgetAmount.getAmount().asBigDecimal().toFloat())
            limitLine.setLineWidth(2f)
            limitLine.setLineColor(Color.RED)


            barChart.setData(barData)
            barChart.getAxisLeft().addLimitLine(limitLine)
            val maxValue =
                budgetAmount.getAmount().add(budgetAmount.getAmount().multiply(BigDecimal("0.2")))
                    .asBigDecimal()
            barChart.getAxisLeft().setAxisMaxValue(maxValue.toFloat())
            barChart.animateX(1000)
            barChart.setAutoScaleMinMaxEnabled(true)
            barChart.setDrawValueAboveBar(true)
            barChart.invalidate()
        }

        override fun getItemCount(): Int {
            return mBudgetAmounts.size
        }

        inner class BudgetAmountViewHolder(itemView: View) :
            RecyclerView.ViewHolder(itemView) {
            var budgetAccount: TextView
            var budgetAmount: TextView
            var budgetSpent: TextView
            var budgetLeft: TextView
            var budgetIndicator: ProgressBar
            var budgetChart: BarChart

            init {
                budgetAccount = itemView.findViewById<TextView?>(R.id.budget_account)
                budgetAmount = itemView.findViewById<TextView?>(R.id.budget_amount)
                budgetSpent = itemView.findViewById<TextView?>(R.id.budget_spent)
                budgetLeft = itemView.findViewById<TextView?>(R.id.budget_left)
                budgetIndicator = itemView.findViewById<ProgressBar?>(R.id.budget_indicator)
                budgetChart = itemView.findViewById<BarChart?>(R.id.budget_chart)
            }
        }
    }

    companion object {
        fun newInstance(budgetUID: String?): BudgetDetailFragment {
            val fragment = BudgetDetailFragment()
            val args = Bundle()
            args.putString(UxArgument.BUDGET_UID, budgetUID)
            fragment.setArguments(args)
            return fragment
        }
    }
}