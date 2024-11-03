package org.gnucash.android.model.importer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.gnucash.android.R;
import org.gnucash.android.model.db.DatabaseSchema;
import org.gnucash.android.model.db.adapter.BooksDbAdapter;

import java.io.InputStream;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;

public class ImportAsyncUtil {

    private static final String TAG = ImportAsyncUtil.class.getName();

    public static Single<Pair<Boolean,String>> importDataSingle(Activity mContext, Uri... uris) {
        return Single.create(new SingleOnSubscribe<Pair<Boolean,String>>() {
            @Override
            public void subscribe(@NonNull SingleEmitter<Pair<Boolean,String>> emitter) throws Throwable {
                String mImportedBookUID = "";
                try {
                    InputStream accountInputStream = mContext.getContentResolver().openInputStream(uris[0]);
                    mImportedBookUID = GncXmlImporter.parse(accountInputStream);

                } catch (Exception exception){
                    Log.e(TAG, "", exception);
//            Crashlytics.log("Could not open: " + uris[0].toString());
//            Crashlytics.logException(exception);

                    final String err_msg = exception.getLocalizedMessage();
//            Crashlytics.log(err_msg);
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext,
                                    mContext.getString(R.string.toast_error_importing_accounts) + "\n" + err_msg,
                                    Toast.LENGTH_LONG).show();
                        }
                    });

                    emitter.onSuccess(new Pair(false,mImportedBookUID));
                }

                Cursor cursor = mContext.getContentResolver().query(uris[0], null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    String displayName = cursor.getString(nameIndex);
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName);
                    contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uris[0].toString());
                    BooksDbAdapter.getInstance().updateRecord(mImportedBookUID, contentValues);

                    cursor.close();
                }

                //set the preferences to their default values
                mContext.getSharedPreferences(mImportedBookUID, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(mContext.getString(R.string.key_use_double_entry), true)
                        .apply();

                emitter.onSuccess(new Pair<>(true,mImportedBookUID));
            }
        });
    }
}
