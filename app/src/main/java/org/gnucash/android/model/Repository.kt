package org.gnucash.android.model

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.util.Pair
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.gnucash.android.R
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import org.gnucash.android.model.importer.ImportAsyncUtil
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.util.TaskDelegate
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.BookUtils
import javax.inject.Inject

class Repository @Inject constructor(
    @ApplicationContext private val context: Context,
    val booksDbAdapter: BooksDbAdapter,
    val backupManager: BackupManager
) {

    fun backupAllBooks() = backupManager.backupAllBooks()

    fun backupActiveBook() = backupManager.backupActiveBook()

    fun backupBook(bookUID: String) = backupManager.backupBook(bookUID)

    fun getBookBackupFileUri(bookUID: String) = backupManager.getBookBackupFileUri(bookUID)

    fun getBackupList(bookUID: String) = backupManager.getBackupList(bookUID)

    fun schedulePeriodicBackups(context: Context) = backupManager.schedulePeriodicBackups(context)

    /**
     * Reads and XML file from an intent and imports it into the database
     *
     * This method is usually called in response to [AccountsActivity.startXmlFileChooser]
     * @param activity Activity context
     * @param data Intent data containing the XML uri
     * @param onFinishTask Task to be executed when import is complete
     */
    fun importXmlFileFromIntent(activity: Activity?, data: Intent, onFinishTask: TaskDelegate?) {
        backupActiveBook()
        val progressDialog = ProgressDialog(activity)
        ImportAsyncUtil.importDataSingle(activity, data.data)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Pair<Boolean, String?>> {
                override fun onSubscribe(d: Disposable) {
//                    mCompositeDisposable.add(d)

                    progressDialog.apply {
                        setTitle(R.string.title_progress_importing_accounts)
                        isIndeterminate = true
                        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                        show()

                        //these methods must be called after progressDialog.show()
                        setProgressNumberFormat(null)
                        setProgressPercentFormat(null)
                    }
                }

                override fun onSuccess(result: Pair<Boolean, String?>) {
                    if (progressDialog.isShowing)
                        progressDialog.dismiss()

                    val message =
                        if (result.first) R.string.toast_success_importing_accounts else R.string.toast_error_importing_accounts
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

                    result.second?.let {
                        BookUtils.loadBook(it)
                    }

                    onFinishTask?.onTaskComplete()
                }

                override fun onError(e: Throwable) {
                }
            })
    }

}