/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.model.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;


import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.data.Commodity;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Helper class for managing the SQLite database.
 * Creates the database and handles upgrades
 * @author Ngewi Fet <ngewif@gmail.com>
 *
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    /**
	 * Tag for logging
	 */
	public static final String LOG_TAG = DatabaseHelper.class.getName();

    /**
	 * SQL statement to create the accounts table in the database
	 */
	private static final String ACCOUNTS_TABLE_CREATE = "create table " + DatabaseSchema.AccountEntry.TABLE_NAME + " ("
			+ DatabaseSchema.AccountEntry._ID                      + " integer primary key autoincrement, "
			+ DatabaseSchema.AccountEntry.COLUMN_UID 	            + " varchar(255) not null UNIQUE, "
			+ DatabaseSchema.AccountEntry.COLUMN_NAME 	            + " varchar(255) not null, "
			+ DatabaseSchema.AccountEntry.COLUMN_TYPE              + " varchar(255) not null, "
			+ DatabaseSchema.AccountEntry.COLUMN_CURRENCY          + " varchar(255) not null, "
            + DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID     + " varchar(255) not null, "
			+ DatabaseSchema.AccountEntry.COLUMN_DESCRIPTION       + " varchar(255), "
            + DatabaseSchema.AccountEntry.COLUMN_COLOR_CODE        + " varchar(255), "
            + DatabaseSchema.AccountEntry.COLUMN_FAVORITE 		    + " tinyint default 0, "
            + DatabaseSchema.AccountEntry.COLUMN_HIDDEN 		    + " tinyint default 0, "
            + DatabaseSchema.AccountEntry.COLUMN_FULL_NAME 	    + " varchar(255), "
            + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER           + " tinyint default 0, "
            + DatabaseSchema.AccountEntry.COLUMN_PARENT_ACCOUNT_UID    + " varchar(255), "
            + DatabaseSchema.AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID   + " varchar(255), "
            + DatabaseSchema.AccountEntry.COLUMN_CREATED_AT       + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.AccountEntry.COLUMN_MODIFIED_AT      + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
//            + "FOREIGN KEY (" 	+ AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE SET NULL, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + DatabaseSchema.CommodityEntry.TABLE_NAME + " (" + DatabaseSchema.CommodityEntry.COLUMN_UID + ") "
			+ ");" + createUpdatedAtTrigger(DatabaseSchema.AccountEntry.TABLE_NAME);
	
	/**
	 * SQL statement to create the transactions table in the database
	 */
	private static final String TRANSACTIONS_TABLE_CREATE = "create table " + DatabaseSchema.TransactionEntry.TABLE_NAME + " ("
			+ DatabaseSchema.TransactionEntry._ID 		            + " integer primary key autoincrement, "
			+ DatabaseSchema.TransactionEntry.COLUMN_UID 		    + " varchar(255) not null UNIQUE, "
			+ DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION   + " varchar(255), "
			+ DatabaseSchema.TransactionEntry.COLUMN_NOTES         + " text, "
			+ DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP     + " integer not null, "
			+ DatabaseSchema.TransactionEntry.COLUMN_EXPORTED      + " tinyint default 0, "
			+ DatabaseSchema.TransactionEntry.COLUMN_TEMPLATE      + " tinyint default 0, "
            + DatabaseSchema.TransactionEntry.COLUMN_CURRENCY      + " varchar(255) not null, "
            + DatabaseSchema.TransactionEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
            + DatabaseSchema.TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " varchar(255), "
            + DatabaseSchema.TransactionEntry.COLUMN_CREATED_AT    + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.TransactionEntry.COLUMN_MODIFIED_AT   + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.TransactionEntry.COLUMN_SCHEDX_ACTION_UID + ") REFERENCES " + DatabaseSchema.ScheduledActionEntry.TABLE_NAME + " (" + DatabaseSchema.ScheduledActionEntry.COLUMN_UID + ") ON DELETE SET NULL, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.TransactionEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + DatabaseSchema.CommodityEntry.TABLE_NAME + " (" + DatabaseSchema.CommodityEntry.COLUMN_UID + ") "
			+ ");" + createUpdatedAtTrigger(DatabaseSchema.TransactionEntry.TABLE_NAME);

    /**
     * SQL statement to create the transaction splits table
     */
    private static final String SPLITS_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.SplitEntry.TABLE_NAME + " ("
            + DatabaseSchema.SplitEntry._ID                    + " integer primary key autoincrement, "
            + DatabaseSchema.SplitEntry.COLUMN_UID             + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.SplitEntry.COLUMN_MEMO 	        + " text, "
            + DatabaseSchema.SplitEntry.COLUMN_TYPE            + " varchar(255) not null, "
            + DatabaseSchema.SplitEntry.COLUMN_VALUE_NUM       + " integer not null, "
            + DatabaseSchema.SplitEntry.COLUMN_VALUE_DENOM     + " integer not null, "
            + DatabaseSchema.SplitEntry.COLUMN_QUANTITY_NUM    + " integer not null, "
            + DatabaseSchema.SplitEntry.COLUMN_QUANTITY_DENOM  + " integer not null, "
            + DatabaseSchema.SplitEntry.COLUMN_ACCOUNT_UID 	+ " varchar(255) not null, "
            + DatabaseSchema.SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
            + DatabaseSchema.SplitEntry.COLUMN_RECONCILE_STATE + " varchar(1) not null default 'n', "
            + DatabaseSchema.SplitEntry.COLUMN_RECONCILE_DATE  + " timestamp not null default current_timestamp, "
            + DatabaseSchema.SplitEntry.COLUMN_CREATED_AT      + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.SplitEntry.COLUMN_MODIFIED_AT     + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + DatabaseSchema.AccountEntry.TABLE_NAME + " (" + DatabaseSchema.AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + DatabaseSchema.TransactionEntry.TABLE_NAME + " (" + DatabaseSchema.TransactionEntry.COLUMN_UID + ") ON DELETE CASCADE "
            + ");" + createUpdatedAtTrigger(DatabaseSchema.SplitEntry.TABLE_NAME);


    public static final String SCHEDULED_ACTIONS_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.ScheduledActionEntry.TABLE_NAME + " ("
            + DatabaseSchema.ScheduledActionEntry._ID                      + " integer primary key autoincrement, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_UID               + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_ACTION_UID        + " varchar(255) not null, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_TYPE              + " varchar(255) not null, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_RECURRENCE_UID    + " varchar(255) not null, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID + " varchar(255) not null, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_LAST_RUN          + " integer default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_START_TIME        + " integer not null, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_END_TIME          + " integer default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_TAG               + " text, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_ENABLED           + " tinyint default 1, " //enabled by default
            + DatabaseSchema.ScheduledActionEntry.COLUMN_AUTO_CREATE       + " tinyint default 1, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_AUTO_NOTIFY       + " tinyint default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_ADVANCE_CREATION  + " integer default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY    + " integer default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY   + " integer default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_EXECUTION_COUNT   + " integer default 0, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_CREATED_AT        + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.ScheduledActionEntry.COLUMN_MODIFIED_AT       + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.ScheduledActionEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + DatabaseSchema.RecurrenceEntry.TABLE_NAME + " (" + DatabaseSchema.RecurrenceEntry.COLUMN_UID + ") "
            + ");" + createUpdatedAtTrigger(DatabaseSchema.ScheduledActionEntry.TABLE_NAME);

    public static final String COMMODITIES_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.CommodityEntry.TABLE_NAME + " ("
            + DatabaseSchema.CommodityEntry._ID                + " integer primary key autoincrement, "
            + DatabaseSchema.CommodityEntry.COLUMN_UID         + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.CommodityEntry.COLUMN_NAMESPACE   + " varchar(255) not null default " + Commodity.Namespace.ISO4217.name() + ", "
            + DatabaseSchema.CommodityEntry.COLUMN_FULLNAME    + " varchar(255) not null, "
            + DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC    + " varchar(255) not null, "
            + DatabaseSchema.CommodityEntry.COLUMN_LOCAL_SYMBOL+ " varchar(255) not null default '', "
            + DatabaseSchema.CommodityEntry.COLUMN_CUSIP       + " varchar(255), "
            + DatabaseSchema.CommodityEntry.COLUMN_SMALLEST_FRACTION + " integer not null, "
            + DatabaseSchema.CommodityEntry.COLUMN_QUOTE_FLAG  + " integer not null, "
            + DatabaseSchema.CommodityEntry.COLUMN_CREATED_AT  + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.CommodityEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
            + ");" + createUpdatedAtTrigger(DatabaseSchema.CommodityEntry.TABLE_NAME);

    /**
     * SQL statement to create the commodity prices table
     */
    private static final String PRICES_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.PriceEntry.TABLE_NAME + " ("
            + DatabaseSchema.PriceEntry._ID                    + " integer primary key autoincrement, "
            + DatabaseSchema.PriceEntry.COLUMN_UID             + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.PriceEntry.COLUMN_COMMODITY_UID 	+ " varchar(255) not null, "
            + DatabaseSchema.PriceEntry.COLUMN_CURRENCY_UID    + " varchar(255) not null, "
            + DatabaseSchema.PriceEntry.COLUMN_TYPE            + " varchar(255), "
            + DatabaseSchema.PriceEntry.COLUMN_DATE 	        + " TIMESTAMP not null, "
            + DatabaseSchema.PriceEntry.COLUMN_SOURCE          + " text, "
            + DatabaseSchema.PriceEntry.COLUMN_VALUE_NUM       + " integer not null, "
            + DatabaseSchema.PriceEntry.COLUMN_VALUE_DENOM     + " integer not null, "
            + DatabaseSchema.PriceEntry.COLUMN_CREATED_AT      + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.PriceEntry.COLUMN_MODIFIED_AT     + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "UNIQUE (" + DatabaseSchema.PriceEntry.COLUMN_COMMODITY_UID + ", " + DatabaseSchema.PriceEntry.COLUMN_CURRENCY_UID + ") ON CONFLICT REPLACE, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.PriceEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + DatabaseSchema.CommodityEntry.TABLE_NAME + " (" + DatabaseSchema.CommodityEntry.COLUMN_UID + ") ON DELETE CASCADE, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.PriceEntry.COLUMN_CURRENCY_UID + ") REFERENCES " + DatabaseSchema.CommodityEntry.TABLE_NAME + " (" + DatabaseSchema.CommodityEntry.COLUMN_UID + ") ON DELETE CASCADE "
            + ");" + createUpdatedAtTrigger(DatabaseSchema.PriceEntry.TABLE_NAME);


    private static final String BUDGETS_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.BudgetEntry.TABLE_NAME + " ("
            + DatabaseSchema.BudgetEntry._ID                   + " integer primary key autoincrement, "
            + DatabaseSchema.BudgetEntry.COLUMN_UID            + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.BudgetEntry.COLUMN_NAME           + " varchar(255) not null, "
            + DatabaseSchema.BudgetEntry.COLUMN_DESCRIPTION    + " varchar(255), "
            + DatabaseSchema.BudgetEntry.COLUMN_RECURRENCE_UID + " varchar(255) not null, "
            + DatabaseSchema.BudgetEntry.COLUMN_NUM_PERIODS    + " integer, "
            + DatabaseSchema.BudgetEntry.COLUMN_CREATED_AT     + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.BudgetEntry.COLUMN_MODIFIED_AT    + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.BudgetEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + DatabaseSchema.RecurrenceEntry.TABLE_NAME + " (" + DatabaseSchema.RecurrenceEntry.COLUMN_UID + ") "
            + ");" + createUpdatedAtTrigger(DatabaseSchema.BudgetEntry.TABLE_NAME);

    private static final String BUDGET_AMOUNTS_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.BudgetAmountEntry.TABLE_NAME + " ("
            + DatabaseSchema.BudgetAmountEntry._ID                   + " integer primary key autoincrement, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_UID            + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_BUDGET_UID     + " varchar(255) not null, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_ACCOUNT_UID    + " varchar(255) not null, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_AMOUNT_NUM     + " integer not null, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_AMOUNT_DENOM   + " integer not null, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_PERIOD_NUM     + " integer not null, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_CREATED_AT     + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.BudgetAmountEntry.COLUMN_MODIFIED_AT    + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.BudgetAmountEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + DatabaseSchema.AccountEntry.TABLE_NAME + " (" + DatabaseSchema.AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
            + "FOREIGN KEY (" 	+ DatabaseSchema.BudgetAmountEntry.COLUMN_BUDGET_UID + ") REFERENCES " + DatabaseSchema.BudgetEntry.TABLE_NAME + " (" + DatabaseSchema.BudgetEntry.COLUMN_UID + ") ON DELETE CASCADE "
            + ");" + createUpdatedAtTrigger(DatabaseSchema.BudgetAmountEntry.TABLE_NAME);


    private static final String RECURRENCE_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.RecurrenceEntry.TABLE_NAME + " ("
            + DatabaseSchema.RecurrenceEntry._ID                   + " integer primary key autoincrement, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_UID            + " varchar(255) not null UNIQUE, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_MULTIPLIER     + " integer not null default 1, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_PERIOD_TYPE    + " varchar(255) not null, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_BYDAY          + " varchar(255), "
            + DatabaseSchema.RecurrenceEntry.COLUMN_PERIOD_START   + " timestamp not null, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_PERIOD_END   + " timestamp, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_CREATED_AT     + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + DatabaseSchema.RecurrenceEntry.COLUMN_MODIFIED_AT    + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP); "
            + createUpdatedAtTrigger(DatabaseSchema.RecurrenceEntry.TABLE_NAME);


    /**
	 * Constructor
	 * @param context Application context
     * @param databaseName Name of the database
	 */
	public DatabaseHelper(Context context, String databaseName){
		super(context, databaseName, null, DatabaseSchema.DATABASE_VERSION);

	}

    /**
     * Creates an update trigger to update the updated_at column for all records in the database.
     * This has to be run per table, and is currently appended to the create table statement.
     * @param tableName Name of table on which to create trigger
     * @return SQL statement for creating trigger
     */
    static String createUpdatedAtTrigger(String tableName){
        return "CREATE TRIGGER update_time_trigger "
                + "  AFTER UPDATE ON " + tableName + " FOR EACH ROW"
                + "  BEGIN " + "UPDATE " + tableName
                + "  SET " + DatabaseSchema.CommonColumns.COLUMN_MODIFIED_AT + " = CURRENT_TIMESTAMP"
                + "  WHERE OLD." + DatabaseSchema.CommonColumns.COLUMN_UID + " = NEW." + DatabaseSchema.CommonColumns.COLUMN_UID + ";"
                + "  END;";
    }

	@Override
	public void onCreate(SQLiteDatabase db) {
		createDatabaseTables(db);

	}

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    @Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
		Log.i(LOG_TAG, "Upgrading database from version "
                + oldVersion + " to " + newVersion);

        Toast.makeText(GnuCashApplication.getAppContext(), "Upgrading GnuCash database", Toast.LENGTH_SHORT).show();
        /*
        * NOTE: In order to modify the database, create a new static method in the MigrationHelper class
        * called upgradeDbToVersion<#>, e.g. int upgradeDbToVersion10(SQLiteDatabase) in order to upgrade to version 10.
        * The upgrade method should return the new (upgraded) database version as the return value.
        * Then all you need to do is increment the DatabaseSchema.DATABASE_VERSION to the appropriate number to trigger an upgrade.
        */
		if (oldVersion > newVersion) {
            throw new IllegalArgumentException("Database downgrades are not supported at the moment");
        }

        while(oldVersion < newVersion){
            try {
                Method method = MigrationHelper.class.getDeclaredMethod("upgradeDbToVersion" + (oldVersion+1), SQLiteDatabase.class);
                Object result = method.invoke(null, db);
                oldVersion = Integer.parseInt(result.toString());

            } catch (NoSuchMethodException e) {
                String msg = String.format("Database upgrade method upgradeToVersion%d(SQLiteDatabase) definition not found ", newVersion);
                Log.e(LOG_TAG, msg, e);
//                Crashlytics.log(msg);
//                Crashlytics.logException(e);
                throw new RuntimeException(e);
            }  catch (IllegalAccessException e) {
                String msg = String.format("Database upgrade to version %d failed. The upgrade method is inaccessible ", newVersion);
                Log.e(LOG_TAG, msg, e);
//                Crashlytics.log(msg);
//                Crashlytics.logException(e);
                throw new RuntimeException(e);
            } catch (InvocationTargetException e){
//                Crashlytics.logException(e.getTargetException());
                throw new RuntimeException(e.getTargetException());
            }
        }
	}


    /**
     * Creates the tables in the database and import default commodities into the database
     * @param db Database instance
     */
    private void createDatabaseTables(SQLiteDatabase db) {
        Log.i(LOG_TAG, "Creating database tables");
        db.execSQL(ACCOUNTS_TABLE_CREATE);
        db.execSQL(TRANSACTIONS_TABLE_CREATE);
        db.execSQL(SPLITS_TABLE_CREATE);
        db.execSQL(SCHEDULED_ACTIONS_TABLE_CREATE);
        db.execSQL(COMMODITIES_TABLE_CREATE);
        db.execSQL(PRICES_TABLE_CREATE);
        db.execSQL(RECURRENCE_TABLE_CREATE);
        db.execSQL(BUDGETS_TABLE_CREATE);
        db.execSQL(BUDGET_AMOUNTS_TABLE_CREATE);


        String createAccountUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.AccountEntry.INDEX_UID + "' ON "
                + DatabaseSchema.AccountEntry.TABLE_NAME + "(" + DatabaseSchema.AccountEntry.COLUMN_UID + ")";

        String createTransactionUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.TransactionEntry.INDEX_UID + "' ON "
                + DatabaseSchema.TransactionEntry.TABLE_NAME + "(" + DatabaseSchema.TransactionEntry.COLUMN_UID + ")";

        String createSplitUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.SplitEntry.INDEX_UID + "' ON "
                + DatabaseSchema.SplitEntry.TABLE_NAME + "(" + DatabaseSchema.SplitEntry.COLUMN_UID + ")";

        String createScheduledEventUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.ScheduledActionEntry.INDEX_UID
                + "' ON " + DatabaseSchema.ScheduledActionEntry.TABLE_NAME + "(" + DatabaseSchema.ScheduledActionEntry.COLUMN_UID + ")";

        String createCommodityUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.CommodityEntry.INDEX_UID
                + "' ON " + DatabaseSchema.CommodityEntry.TABLE_NAME + "(" + DatabaseSchema.CommodityEntry.COLUMN_UID + ")";

        String createPriceUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.PriceEntry.INDEX_UID
                + "' ON " + DatabaseSchema.PriceEntry.TABLE_NAME + "(" + DatabaseSchema.PriceEntry.COLUMN_UID + ")";

        String createBudgetUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.BudgetEntry.INDEX_UID
                + "' ON " + DatabaseSchema.BudgetEntry.TABLE_NAME + "(" + DatabaseSchema.BudgetEntry.COLUMN_UID + ")";

        String createBudgetAmountUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.BudgetAmountEntry.INDEX_UID
                + "' ON " + DatabaseSchema.BudgetAmountEntry.TABLE_NAME + "(" + DatabaseSchema.BudgetAmountEntry.COLUMN_UID + ")";

        String createRecurrenceUidIndex = "CREATE UNIQUE INDEX '" + DatabaseSchema.RecurrenceEntry.INDEX_UID
                + "' ON " + DatabaseSchema.RecurrenceEntry.TABLE_NAME + "(" + DatabaseSchema.RecurrenceEntry.COLUMN_UID + ")";

        db.execSQL(createAccountUidIndex);
        db.execSQL(createTransactionUidIndex);
        db.execSQL(createSplitUidIndex);
        db.execSQL(createScheduledEventUidIndex);
        db.execSQL(createCommodityUidIndex);
        db.execSQL(createPriceUidIndex);
        db.execSQL(createBudgetUidIndex);
        db.execSQL(createRecurrenceUidIndex);
        db.execSQL(createBudgetAmountUidIndex);

        try {
            MigrationHelper.importCommodities(db);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            Log.e(LOG_TAG, "Error loading currencies into the database");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
