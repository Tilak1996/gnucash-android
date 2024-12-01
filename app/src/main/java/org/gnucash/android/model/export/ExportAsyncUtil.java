/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.model.export;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.CreateRemoteFolderOperation;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.UploadRemoteFileOperation;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.di.GnuCashEntryPoint;
import org.gnucash.android.model.Repository;
import org.gnucash.android.model.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.db.adapter.SplitsDbAdapter;
import org.gnucash.android.model.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.export.csv.CsvAccountExporter;
import org.gnucash.android.model.export.csv.CsvTransactionsExporter;
import org.gnucash.android.model.export.ofx.OfxExporter;
import org.gnucash.android.model.export.qif.QifExporter;
import org.gnucash.android.model.export.xml.GncXmlExporter;
import org.gnucash.android.model.data.Transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dagger.hilt.android.EntryPointAccessors;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportAsyncUtil {

    /**
     * App context
     */
    private final Context mContext;

    private SQLiteDatabase mDb;

    /**
     * Log tag
     */
    public static final String TAG = "ExportAsyncTask";

    /**
     * Export parameters
     */
    private ExportParams mExportParams;

    // File paths generated by the exporter
    private List<String> mExportedFiles = Collections.emptyList();

    private Exporter mExporter;
    private DropboxHelper mDropboxHelper;

    private Repository mRepository;

    public ExportAsyncUtil(Context context, SQLiteDatabase db){
        this.mContext = context;
        this.mDb = db;
        GnuCashEntryPoint entryPoint = EntryPointAccessors.fromApplication(mContext, GnuCashEntryPoint.class);
        mDropboxHelper = entryPoint.dropBoxHelper();
        mRepository = entryPoint.repository();;
    }

    /**
     * Generates the appropriate exported transactions file for the given parameters
     * @param params Export parameters
     * @return <code>true</code> if export was successful, <code>false</code> otherwise
     */

    public Single<Boolean> exportData(ExportParams params) {
        return Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(@io.reactivex.rxjava3.annotations.NonNull SingleEmitter<Boolean> emitter) throws Throwable {
                mExportParams = params;
                mExporter = getExporter();

                try {
                    mExportedFiles = mExporter.generateExport();
                } catch (final Exception e) {
                    emitter.onError(e);
                    return;
                }

                if (mExportedFiles.isEmpty()) {
                    emitter.onError(new IOException(mContext.getString(R.string.toast_no_transactions_to_export)));
                    return;
                }

                try {
                    moveToTarget();
                } catch (Exporter.ExporterException e) {
                    emitter.onError(e);
                    return;
                }

                if(params.shouldDeleteTransactionsAfterExport()) {
                    backupAndDeleteTransactions();
                }

                emitter.onSuccess(true);
            }
        });
    }

    /**
     * Returns an exporter corresponding to the user settings.
     * @return Object of one of {@link QifExporter}, {@link OfxExporter} or {@link GncXmlExporter}, {@Link CsvAccountExporter} or {@Link CsvTransactionsExporter}
     */
    private Exporter getExporter() {
        switch (mExportParams.getExportFormat()) {
            case QIF:
                return new QifExporter(mExportParams, mDb);
            case OFX:
                return new OfxExporter(mExportParams, mDb);
            case CSVA:
                return new CsvAccountExporter(mExportParams, mDb);
            case CSVT:
                return new CsvTransactionsExporter(mExportParams, mDb);
            case XML:
            default:
                return new GncXmlExporter(mExportParams, mDb);
        }
    }

    /**
     * Moves the generated export files to the target specified by the user
     * @throws Exporter.ExporterException if the move fails
     */
    private void moveToTarget() throws Exporter.ExporterException {
        switch (mExportParams.getExportTarget()) {
            case SHARING:
                shareFiles(mExportedFiles);
                break;

            case DROPBOX:
                moveExportToDropbox();
                break;

            case GOOGLE_DRIVE:
//                moveExportToGoogleDrive();
                break;

            case OWNCLOUD:
                moveExportToOwnCloud();
                break;

            case SD_CARD:
                moveExportToSDCard();
                break;

            case URI:
                moveExportToUri();
                break;

            default:
                throw new Exporter.ExporterException(mExportParams, "Invalid target");
        }
    }

    /**
     * Move the exported files to a specified URI.
     * This URI could be a Storage Access Framework file
     * @throws Exporter.ExporterException if something failed while moving the exported file
     */
    private void moveExportToUri() throws Exporter.ExporterException {
        Uri exportUri = Uri.parse(mExportParams.getExportLocation());
        if (exportUri == null){
            Log.w(TAG, "No URI found for export destination");
            return;
        }

        if (mExportedFiles.size() > 0){
            try {
                OutputStream outputStream = mContext.getContentResolver().openOutputStream(exportUri);
                // Now we always get just one file exported (multi-currency QIFs are zipped)
                org.gnucash.android.util.FileUtils.moveFile(mExportedFiles.get(0), outputStream);
            } catch (IOException ex) {
                throw new Exporter.ExporterException(mExportParams, "Error when moving file to URI");
            }
        }
    }

    /**
     * Move the exported files to a GnuCash folder on Google Drive
     * @throws Exporter.ExporterException if something failed while moving the exported file
     * @deprecated Explicit Google Drive integration is deprecated, use Storage Access Framework. See {@link #moveExportToUri()}
     */
//    @Deprecated
//    private void moveExportToGoogleDrive() throws Exporter.ExporterException {
//        Log.i(TAG, "Moving exported file to Google Drive");
//        final GoogleApiClient googleApiClient = BackupPreferenceFragment.getGoogleApiClient(GnuCashApplication.getAppContext());
//        googleApiClient.blockingConnect();
//
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
//        String folderId = sharedPreferences.getString(mContext.getString(R.string.key_google_drive_app_folder_id), "");
//        DriveFolder folder = DriveId.decodeFromString(folderId).asDriveFolder();
//        try {
//            for (String exportedFilePath : mExportedFiles) {
//                DriveApi.DriveContentsResult driveContentsResult =
//                        Drive.DriveApi.newDriveContents(googleApiClient).await(1, TimeUnit.MINUTES);
//                if (!driveContentsResult.getStatus().isSuccess()) {
//                    throw new Exporter.ExporterException(mExportParams,
//                                                "Error while trying to create new file contents");
//                }
//                final DriveContents driveContents = driveContentsResult.getDriveContents();
//                OutputStream outputStream = driveContents.getOutputStream();
//                File exportedFile = new File(exportedFilePath);
//                FileInputStream fileInputStream = new FileInputStream(exportedFile);
//                byte[] buffer = new byte[1024];
//                int count;
//
//                while ((count = fileInputStream.read(buffer)) >= 0) {
//                    outputStream.write(buffer, 0, count);
//                }
//                fileInputStream.close();
//                outputStream.flush();
//                exportedFile.delete();
//
//                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
//                        .setTitle(exportedFile.getName())
//                        .setMimeType(mExporter.getExportMimeType())
//                        .build();
//                // create a file on root folder
//                DriveFolder.DriveFileResult driveFileResult =
//                        folder.createFile(googleApiClient, changeSet, driveContents)
//                                                .await(1, TimeUnit.MINUTES);
//                if (!driveFileResult.getStatus().isSuccess())
//                    throw new Exporter.ExporterException(mExportParams, "Error creating file in Google Drive");
//
//                Log.i(TAG, "Created file with id: " + driveFileResult.getDriveFile().getDriveId());
//            }
//        } catch (IOException e) {
//            throw new Exporter.ExporterException(mExportParams, e);
//        }
//    }

    /**
     * Move the exported files (in the cache directory) to Dropbox
     */
    private void moveExportToDropbox() {
        Log.i(TAG, "Uploading exported files to DropBox");

        DbxClientV2 dbxClient = mDropboxHelper.getClient();

        for (String exportedFilePath : mExportedFiles) {
            File exportedFile = new File(exportedFilePath);
            try {
                FileInputStream inputStream = new FileInputStream(exportedFile);
                FileMetadata metadata = dbxClient.files()
                        .uploadBuilder("/" + exportedFile.getName())
                        .uploadAndFinish(inputStream);
                Log.i(TAG, "Successfully uploaded file " + metadata.getName() + " to DropBox");
                inputStream.close();
                exportedFile.delete(); //delete file to prevent cache accumulation
            } catch (IOException e) {
//                Crashlytics.logException(e);
                Log.e(TAG, e.getMessage());
            } catch (com.dropbox.core.DbxException e) {
                e.printStackTrace();
            }
        }
    }

    private void moveExportToOwnCloud() throws Exporter.ExporterException {
        Log.i(TAG, "Copying exported file to ownCloud");

        SharedPreferences mPrefs = mContext.getSharedPreferences(mContext.getString(R.string.owncloud_pref), Context.MODE_PRIVATE);

        Boolean mOC_sync = mPrefs.getBoolean(mContext.getString(R.string.owncloud_sync), false);

        if (!mOC_sync) {
            throw new Exporter.ExporterException(mExportParams, "ownCloud not enabled.");
        }

        String mOC_server = mPrefs.getString(mContext.getString(R.string.key_owncloud_server), null);
        String mOC_username = mPrefs.getString(mContext.getString(R.string.key_owncloud_username), null);
        String mOC_password = mPrefs.getString(mContext.getString(R.string.key_owncloud_password), null);
        String mOC_dir = mPrefs.getString(mContext.getString(R.string.key_owncloud_dir), null);

        Uri serverUri = Uri.parse(mOC_server);
        OwnCloudClient mClient = OwnCloudClientFactory.createOwnCloudClient(serverUri, this.mContext, true);
        mClient.setCredentials(
                OwnCloudCredentialsFactory.newBasicCredentials(mOC_username, mOC_password)
        );

        if (mOC_dir.length() != 0) {
            RemoteOperationResult dirResult = new CreateRemoteFolderOperation(
                    mOC_dir, true).execute(mClient);
            if (!dirResult.isSuccess()) {
                Log.w(TAG, "Error creating folder (it may happen if it already exists): "
                           + dirResult.getLogMessage());
            }
        }
        for (String exportedFilePath : mExportedFiles) {
            String remotePath = mOC_dir + FileUtils.PATH_SEPARATOR + stripPathPart(exportedFilePath);
            String mimeType = mExporter.getExportMimeType();

            RemoteOperationResult result = new UploadRemoteFileOperation(
                    exportedFilePath, remotePath, mimeType,
                    getFileLastModifiedTimestamp(exportedFilePath))
                    .execute(mClient);
            if (!result.isSuccess())
                throw new Exporter.ExporterException(mExportParams, result.getLogMessage());

            new File(exportedFilePath).delete();
        }
    }

    private static String getFileLastModifiedTimestamp(String path) {
        Long timeStampLong = new File(path).lastModified() / 1000;
        return timeStampLong.toString();
    }

    /**
     * Moves the exported files from the internal storage where they are generated to
     * external storage, which is accessible to the user.
     * @return The list of files moved to the SD card.
     * @deprecated Use the Storage Access Framework to save to SD card. See {@link #moveExportToUri()}
     */
    @Deprecated
    private List<String> moveExportToSDCard() throws Exporter.ExporterException {
        Log.i(TAG, "Moving exported file to external storage");
        new File(Exporter.getExportFolderPath(mExporter.mBookUID));
        List<String> dstFiles = new ArrayList<>();

        for (String src: mExportedFiles) {
            String dst = Exporter.getExportFolderPath(mExporter.mBookUID) + stripPathPart(src);
            try {
                org.gnucash.android.util.FileUtils.moveFile(src, dst);
                dstFiles.add(dst);
            } catch (IOException e) {
                throw new Exporter.ExporterException(mExportParams, e);
            }
        }

        return dstFiles;
    }

    // "/some/path/filename.ext" -> "filename.ext"
    private String stripPathPart(String fullPathName) {
        return (new File(fullPathName)).getName();
    }

    /**
     * Backups of the database, saves opening balances (if necessary)
     * and deletes all non-template transactions in the database.
     */
    private void backupAndDeleteTransactions() {
        Log.i(TAG, "Backup and deleting transactions after export");
        mRepository.backupActiveBook(); //create backup before deleting everything
        List<Transaction> openingBalances = new ArrayList<>();
        boolean preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false);

        TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(mDb, new SplitsDbAdapter(mDb));
        if (preserveOpeningBalances) {
            openingBalances = new AccountsDbAdapter(mDb, transactionsDbAdapter).getAllOpeningBalanceTransactions();
        }
        transactionsDbAdapter.deleteAllNonTemplateTransactions();

        if (preserveOpeningBalances) {
            transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert);
        }
    }

    /**
     * Starts an intent chooser to allow the user to select an activity to receive
     * the exported files.
     * @param paths list of full paths of the files to send to the activity.
     */
    private void shareFiles(List<String> paths) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("text/xml");

        ArrayList<Uri> exportFiles = convertFilePathsToUris(paths);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, exportFiles);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        shareIntent.putExtra(Intent.EXTRA_SUBJECT, mContext.getString(R.string.title_export_email,
                mExportParams.getExportFormat().name()));

        String defaultEmail = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(mContext.getString(R.string.key_default_export_email), null);
        if (defaultEmail != null && defaultEmail.trim().length() > 0)
            shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{defaultEmail});

        SimpleDateFormat formatter = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance();
        String extraText = mContext.getString(R.string.description_export_email)
                           + " " + formatter.format(new Date(System.currentTimeMillis()));
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText);

        if (mContext instanceof Activity) {
            List<ResolveInfo> activities = mContext.getPackageManager().queryIntentActivities(shareIntent, 0);
            if (activities != null && !activities.isEmpty()) {
                mContext.startActivity(Intent.createChooser(shareIntent,
                        mContext.getString(R.string.title_select_export_destination)));
            } else {
                Toast.makeText(mContext, R.string.toast_no_compatible_apps_to_receive_export,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Convert file paths to URIs by adding the file// prefix
     * <p>e.g. /some/path/file.ext --> file:///some/path/file.ext</p>
     * @param paths List of file paths to convert
     * @return List of file URIs
     */
    @NonNull
    private ArrayList<Uri> convertFilePathsToUris(List<String> paths) {
        ArrayList<Uri> exportFiles = new ArrayList<>();

        for (String path : paths) {
            File file = new File(path);
            Uri contentUri = FileProvider.getUriForFile(GnuCashApplication.getAppContext(), GnuCashApplication.FILE_PROVIDER_AUTHORITY, file);
            exportFiles.add(contentUri);
        }
        return exportFiles;
    }

    public static void reportSuccess(ExportParams exportParams, Context context) {
        String targetLocation;
        switch (exportParams.getExportTarget()){
            case SD_CARD:
                targetLocation = "SD card";
                break;
            case DROPBOX:
                targetLocation = "DropBox -> Apps -> GnuCash";
                break;
            case GOOGLE_DRIVE:
                targetLocation = "Google Drive -> " + context.getString(R.string.app_name);
                break;
            case OWNCLOUD:
                targetLocation = context.getSharedPreferences(
                        context.getString(R.string.owncloud_pref),
                        Context.MODE_PRIVATE).getBoolean(
                        context.getString(R.string.owncloud_sync), false) ?

                        "ownCloud -> " +
                                context.getSharedPreferences(
                                        context.getString(R.string.owncloud_pref),
                                        Context.MODE_PRIVATE).getString(
                                        context.getString(R.string.key_owncloud_dir), null) :
                        "ownCloud sync not enabled";
                break;
            default:
                targetLocation = context.getString(R.string.label_export_target_external_service);
        }
        Toast.makeText(context,
                String.format(context.getString(R.string.toast_exported_to), targetLocation),
                Toast.LENGTH_LONG).show();
    }
}
