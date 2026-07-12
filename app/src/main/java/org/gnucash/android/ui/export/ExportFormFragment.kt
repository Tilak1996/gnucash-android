package org.gnucash.android.ui.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment
import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.*
import com.dropbox.core.android.Auth
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.data.BaseModel
import org.gnucash.android.model.data.ScheduledAction
import org.gnucash.android.model.db.adapter.*
import org.gnucash.android.model.export.*
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.BackupPreferenceFragment
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import java.io.IOException
import java.sql.Timestamp
import java.text.ParseException
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ExportFormFragment : Fragment(),
    RecurrencePickerDialogFragment.OnRecurrenceSetListener,
    CalendarDatePickerDialogFragment.OnDateSetListener,
    RadialTimePickerDialogFragment.OnTimeSetListener {

    private lateinit var destination: Spinner
    private lateinit var deleteAll: CheckBox
    private lateinit var warning: TextView
    private lateinit var targetUri: TextView
    private lateinit var recurrence: TextView
    private lateinit var startDate: TextView
    private lateinit var startTime: TextView
    private lateinit var exportAll: SwitchCompat
    private lateinit var dateLayout: LinearLayout
    private lateinit var ofx: RadioButton
    private lateinit var qif: RadioButton
    private lateinit var xml: RadioButton
    private lateinit var csv: RadioButton
    private lateinit var comma: RadioButton
    private lateinit var colon: RadioButton
    private lateinit var semicolon: RadioButton
    private lateinit var csvOptions: LinearLayout
    private lateinit var recurrenceOptions: View
    @Inject lateinit var dropboxHelper: DropboxHelper

    private val disposables = CompositeDisposable()
    private val eventRecurrence = EventRecurrence()
    private var recurrenceRule: String? = null
    private val exportCalendar = Calendar.getInstance()
    private var format = ExportFormat.QIF
    private var exportTarget = ExportParams.ExportTarget.SD_CARD
    private var exportUri: Uri? = null
    private var separator = ','
    private var exportStarted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_export_form, container, false)
        destination = v.findViewById(R.id.spinner_export_destination)
        deleteAll = v.findViewById(R.id.checkbox_post_export_delete)
        warning = v.findViewById(R.id.export_warning)
        targetUri = v.findViewById(R.id.target_uri)
        recurrence = v.findViewById(R.id.input_recurrence)
        startDate = v.findViewById(R.id.export_start_date)
        startTime = v.findViewById(R.id.export_start_time)
        exportAll = v.findViewById(R.id.switch_export_all)
        dateLayout = v.findViewById(R.id.export_date_layout)
        ofx = v.findViewById(R.id.radio_ofx_format)
        qif = v.findViewById(R.id.radio_qif_format)
        xml = v.findViewById(R.id.radio_xml_format)
        csv = v.findViewById(R.id.radio_csv_transactions_format)
        comma = v.findViewById(R.id.radio_separator_comma_format)
        colon = v.findViewById(R.id.radio_separator_colon_format)
        semicolon = v.findViewById(R.id.radio_separator_semicolon_format)
        csvOptions = v.findViewById(R.id.layout_csv_options)
        recurrenceOptions = v.findViewById(R.id.recurrence_options)
        bindListeners()
        return v
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.default_save_actions, menu)
        menu.findItem(R.id.menu_save).setTitle(R.string.btn_export)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_save -> true.also { startExport() }
        android.R.id.home -> true.also { requireActivity().finish() }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onActivityCreated(state: Bundle?) {
        super.onActivityCreated(state)
        checkNotNull((requireActivity() as AppCompatActivity).supportActionBar).setTitle(R.string.title_export_dialog)
        setHasOptionsMenu(true)
    }

    override fun onResume() { super.onResume(); dropboxHelper.retrieveAndSaveToken() }
    override fun onDestroyView() { disposables.clear(); super.onDestroyView() }
    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
            .putBoolean(UxArgument.SKIP_PASSCODE_SCREEN, true).apply()
    }

    private fun radioClicked(view: View) {
        when (view.id) {
            R.id.radio_ofx_format -> selectFormat(ExportFormat.OFX, R.string.export_warning_ofx, true, false)
            R.id.radio_qif_format -> selectFormat(ExportFormat.QIF, R.string.export_warning_qif, true, false)
            R.id.radio_xml_format -> selectFormat(ExportFormat.XML, R.string.export_warning_xml, false, false)
            R.id.radio_csv_transactions_format -> selectFormat(ExportFormat.CSVT, R.string.export_notice_csv, true, true)
            R.id.radio_separator_comma_format -> separator = ','
            R.id.radio_separator_colon_format -> separator = ':'
            R.id.radio_separator_semicolon_format -> separator = ';'
        }
    }

    private fun selectFormat(value: ExportFormat, message: Int, showDate: Boolean, showCsv: Boolean) {
        format = value
        warning.setText(message)
        if ((value == ExportFormat.OFX || value == ExportFormat.QIF) && !GnuCashApplication.isDoubleEntryEnabled())
            warning.visibility = View.GONE else warning.visibility = View.VISIBLE
        if (showDate) Anim.expand(dateLayout) else Anim.collapse(dateLayout)
        if (showCsv) Anim.expand(csvOptions) else Anim.collapse(csvOptions)
    }

    private fun startExport() {
        if (exportTarget == ExportParams.ExportTarget.URI && exportUri == null) {
            exportStarted = true; selectExportFile(); return
        }
        val params = ExportParams(format)
        params.setExportStartTime(if (exportAll.isChecked) TimestampHelper.getTimestampFromEpochZero() else Timestamp(exportCalendar.timeInMillis))
        params.setExportTarget(exportTarget)
        params.setExportLocation(exportUri?.toString())
        params.setDeleteTransactionsAfterExport(deleteAll.isChecked)
        params.setCsvSeparator(separator)
        Log.i(TAG, "Commencing async export of transactions")
        val dialog = ProgressDialog(requireActivity())
        ExportAsyncUtil(requireActivity(), GnuCashApplication.getActiveDb()).exportData(params)
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Boolean> {
                override fun onSubscribe(d: Disposable) {
                    dialog.setTitle(R.string.title_progress_exporting_transactions)
                    dialog.isIndeterminate = true
                    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                    dialog.setProgressNumberFormat(null); dialog.setProgressPercentFormat(null)
                    dialog.show(); disposables.add(d)
                }
                override fun onSuccess(success: Boolean) {
                    if (dialog.isShowing) dialog.dismiss()
                    val a = activity ?: return
                    a.finish()
                    if (success) ExportAsyncUtil.reportSuccess(params, a)
                }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error exporting: ${e.message}")
                    val c = context ?: return
                    val msg = if (e is IOException) getString(R.string.toast_no_transactions_to_export)
                    else getString(R.string.toast_export_error, params.getExportFormat().name) + "\n" + e.message
                    Toast.makeText(c, msg, if (e is IOException) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
            })
        if (recurrenceRule != null) {
            val action = ScheduledAction(ScheduledAction.ActionType.BACKUP)
            action.setRecurrence(RecurrenceParser.parse(eventRecurrence)); action.setTag(params.toCsv())
            action.setActionUID(BaseModel.generateUID())
            ScheduledActionDbAdapter.getInstance().addRecord(action, DatabaseAdapter.UpdateMethod.insert)
        }
        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
            .putInt(getString(R.string.key_last_export_destination), destination.selectedItemPosition).apply()
    }

    private fun bindListeners() {
        destination.adapter = ArrayAdapter.createFromResource(requireActivity(), R.array.export_destinations,
            android.R.layout.simple_spinner_item).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        destination.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (view == null) return
                when (position) {
                    0 -> { exportTarget = ExportParams.ExportTarget.URI; recurrenceOptions.visibility = View.VISIBLE; exportUri?.let { setUriText(it.toString()) } }
                    1 -> {
                        setUriText(getString(R.string.label_dropbox_export_destination)); recurrenceOptions.visibility = View.VISIBLE
                        exportTarget = ExportParams.ExportTarget.DROPBOX
                        if (!dropboxHelper.hasToken()) Auth.startOAuth2Authentication(requireActivity(), getString(R.string.dropbox_app_key, BackupPreferenceFragment.DROPBOX_APP_KEY))
                    }
                    2 -> {
                        setUriText(null); recurrenceOptions.visibility = View.VISIBLE; exportTarget = ExportParams.ExportTarget.OWNCLOUD
                        if (!PreferenceManager.getDefaultSharedPreferences(requireActivity()).getBoolean(getString(R.string.key_owncloud_sync), false))
                            OwnCloudDialogFragment.newInstance(null).show(parentFragmentManager, "ownCloud dialog")
                    }
                    3 -> { setUriText(getString(R.string.label_select_destination_after_export)); exportTarget = ExportParams.ExportTarget.SHARING; recurrenceOptions.visibility = View.GONE }
                    else -> exportTarget = ExportParams.ExportTarget.SD_CARD
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        destination.setSelection(prefs.getInt(getString(R.string.key_last_export_destination), 0))
        val timestamp = PreferencesHelper.getLastExportTime()
        exportCalendar.timeInMillis = timestamp.time
        startDate.text = TransactionFormFragment.DATE_FORMATTER.format(Date(timestamp.time))
        startTime.text = TransactionFormFragment.TIME_FORMATTER.format(Date(timestamp.time))
        startDate.setOnClickListener { showDatePicker() }; startTime.setOnClickListener { showTimePicker() }
        exportAll.setOnCheckedChangeListener { _, checked ->
            startDate.isEnabled = !checked; startTime.isEnabled = !checked
            val color = if (checked) android.R.color.darker_gray else android.R.color.black
            startDate.setTextColor(ContextCompat.getColor(requireContext(), color)); startTime.setTextColor(ContextCompat.getColor(requireContext(), color))
        }
        exportAll.isChecked = prefs.getBoolean(getString(R.string.key_export_all_transactions), false)
        deleteAll.isChecked = prefs.getBoolean(getString(R.string.key_delete_transactions_after_export), false)
        recurrence.setOnClickListener(RecurrenceViewClickListener(requireActivity() as AppCompatActivity, recurrenceRule, this))
        val defaultName = prefs.getString(getString(R.string.key_default_export_format), ExportFormat.CSVT.name)!!
        format = ExportFormat.valueOf(defaultName)
        val listener = View.OnClickListener(::radioClicked)
        listOf(ofx, qif, xml, csv, comma, colon, semicolon).forEach { it.setOnClickListener(listener) }
        when (ExportFormat.valueOf(defaultName.uppercase())) {
            ExportFormat.QIF -> qif.performClick(); ExportFormat.OFX -> ofx.performClick()
            ExportFormat.XML -> xml.performClick(); ExportFormat.CSVT -> csv.performClick()
            else -> {}
        }
        if (GnuCashApplication.isDoubleEntryEnabled()) ofx.visibility = View.GONE else xml.visibility = View.GONE
    }

    private fun showDatePicker() {
        val cal = parsedCalendar(startDate, true)
        CalendarDatePickerDialogFragment().apply {
            setOnDateSetListener(this@ExportFormFragment)
            setPreselectedDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            show(parentFragmentManager, "date_picker_fragment")
        }
    }
    private fun showTimePicker() {
        val cal = parsedCalendar(startTime, false)
        RadialTimePickerDialogFragment().apply {
            setOnTimeSetListener(this@ExportFormFragment); setStartTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            show(parentFragmentManager, "time_picker_dialog_fragment")
        }
    }
    private fun parsedCalendar(text: TextView, date: Boolean): Calendar {
        var millis = 0L
        try { millis = (if (date) TransactionFormFragment.DATE_FORMATTER else TransactionFormFragment.TIME_FORMATTER).parse(text.text.toString())!!.time }
        catch (_: ParseException) { Log.e(tag, "Error converting input time to Date object") }
        return Calendar.getInstance().apply { timeInMillis = millis }
    }
    private fun setUriText(path: String?) { targetUri.text = path.orEmpty(); targetUri.visibility = if (path == null) View.GONE else View.VISIBLE }
    private fun selectExportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_TITLE, Exporter.buildExportFilename(format, BooksDbAdapter.getInstance().getActiveBookDisplayName()))
        startActivityForResult(intent, REQUEST_EXPORT_FILE)
    }

    override fun onRecurrenceSet(rrule: String?) {
        recurrenceRule = rrule
        recurrence.text = if (rrule == null) getString(R.string.label_tap_to_create_schedule) else {
            eventRecurrence.parse(rrule); EventRecurrenceFormatter.getRepeatString(requireActivity(), resources, eventRecurrence, true)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_EXPORT_FILE && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return; exportUri = uri
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            requireActivity().contentResolver.takePersistableUriPermission(uri, flags)
            targetUri.text = uri.toString(); if (exportStarted) startExport()
        }
    }
    override fun onDateSet(dialog: CalendarDatePickerDialogFragment?, year: Int, month: Int, day: Int) {
        startDate.text = TransactionFormFragment.DATE_FORMATTER.format(GregorianCalendar(year, month, day).time)
        exportCalendar.set(Calendar.YEAR, year); exportCalendar.set(Calendar.MONTH, month); exportCalendar.set(Calendar.DAY_OF_MONTH, day)
    }
    override fun onTimeSet(dialog: RadialTimePickerDialogFragment?, hour: Int, minute: Int) {
        startTime.text = TransactionFormFragment.TIME_FORMATTER.format(GregorianCalendar(0, 0, 0, hour, minute).time)
        exportCalendar.set(Calendar.HOUR_OF_DAY, hour); exportCalendar.set(Calendar.MINUTE, minute)
    }
    companion object { private const val REQUEST_EXPORT_FILE = 0x14; private const val TAG = "ExportFormFragment" }
}

private object Anim {
    fun expand(v: View) {
        v.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); val height = v.measuredHeight
        v.layoutParams.height = 0; v.visibility = View.VISIBLE
        v.startAnimation(animation(v, height, true))
    }
    fun collapse(v: View) { v.startAnimation(animation(v, v.measuredHeight, false)) }
    private fun animation(v: View, height: Int, expanding: Boolean) = object : Animation() {
        override fun applyTransformation(t: Float, tr: Transformation?) {
            if (!expanding && t == 1f) v.visibility = View.GONE else {
                v.layoutParams.height = if (expanding && t == 1f) ViewGroup.LayoutParams.WRAP_CONTENT
                else if (expanding) (height * t).toInt() else height - (height * t).toInt(); v.requestLayout()
            }
        }
        override fun willChangeBounds() = true
    }.apply { duration = (3 * height / v.resources.displayMetrics.density).toLong() }
}
