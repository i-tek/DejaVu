package org.fitchfamily.android.dejavu;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.HashSet;

/**
 * Created by tfitch on 9/1/17.
 */

/**
 * Interface to our on flash SQL database. Note that these methods are not
 * thread safe. However all access to the database is through the Cache object
 * which is thread safe.
 */
public class Database extends SQLiteOpenHelper {
    private static final String TAG = "DejaVu DB";

    private static final int VERSION = 1;
    private static final String NAME = "rf.db";

    public static final String TABLE_SAMPLES = "emitters";

    public static final String COL_TYPE = "rfType";
    public static final String COL_RFID = "rfID";
    public static final String COL_TRUST = "trust";
    public static final String COL_LAT = "latitude";
    public static final String COL_LON = "longitude";
    public static final String COL_RAD = "radius";
    public static final String COL_NOTE = "note";

    private SQLiteDatabase database;
    private boolean withinTransaction;
    private boolean updatesMade;

    private SQLiteStatement sqlSampleInsert;
    private SQLiteStatement sqlSampleUpdate;
    private SQLiteStatement sqlAPdrop;

    public class EmitterInfo {
        public double latitude;
        public double longitude;
        public float radius;
        public long trust;
        public String note;
    }

    public Database(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        database = db;
        withinTransaction = false;
        // Always create version 1 of database, then update the schema
        // in the same order it might occur "in the wild". Avoids having
        // to check to see if the table exists (may be old version)
        // or not (can be new version).
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAMPLES + "(" +
                COL_RFID + " STRING PRIMARY KEY, " +
                COL_TYPE + " STRING, " +
                COL_TRUST + " INTEGER, " +
                COL_LAT + " REAL, " +
                COL_LON + " REAL, " +
                COL_RAD + " REAL, " +
                COL_NOTE + " STRING);");

        onUpgrade(db, 1, VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // no old versions (yet)
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
    }

    /**
     * Start an update operation.
     *
     * We make sure we are not already in a transaction, make sure
     * our database is writeable, compile the insert, update and drop
     * statements that are likely to be used, etc. Then we actually
     * start the transaction on the underlying SQL database.
     */
    public void beginTransaction() {
        //Log.d(TAG,"beginTransaction()");
        if (withinTransaction) {
            Log.d(TAG,"beginTransaction() - Already in a transaction?");
            return;
        }
        withinTransaction = true;
        updatesMade = false;
        database = getWritableDatabase();

        sqlSampleInsert = database.compileStatement("INSERT INTO " +
                TABLE_SAMPLES + "("+
                COL_RFID + ", " +
                COL_TYPE + ", " +
                COL_TRUST + ", " +
                COL_LAT + ", " +
                COL_LON + ", " +
                COL_RAD + ", " +
                COL_NOTE + ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);");

        sqlSampleUpdate = database.compileStatement("UPDATE " +
                TABLE_SAMPLES + " SET "+
                COL_TRUST + "=?, " +
                COL_LAT + "=?, " +
                COL_LON + "=?, " +
                COL_RAD + "=?, " +
                COL_NOTE + "=? " +
                "WHERE " + COL_RFID + "=? AND " + COL_TYPE + "=?;");

        sqlAPdrop = database.compileStatement("DELETE FROM " +
                TABLE_SAMPLES +
                " WHERE " + COL_RFID + "=? AND " + COL_TYPE  + "=?;");

        database.beginTransaction();
    }

    /**
     * End a transaction. If we actually made any changes then we mark
     * the transaction as successful. Once marked as successful we
     * end the transaction with the underlying SQL database.
     */
    public void endTransaction() {
        //Log.d(TAG,"endTransaction()");
        if (!withinTransaction) {
            Log.d(TAG,"Asked to end transaction but we are not in one???");
        }

        if (updatesMade) {
            //Log.d(TAG,"endTransaction() - Setting transaction successful.");
            database.setTransactionSuccessful();
        }
        updatesMade = false;
        database.endTransaction();
        withinTransaction = false;
    }

    /**
     * Drop an RF emitter from the database.
     *
     * @param emitter The emitter to be dropped.
     */
    public void drop(RfEmitter emitter) {
        //Log.d(TAG, "Dropping " + emitter.logString() + " from db");

        sqlAPdrop.bindString(1, emitter.getId());
        sqlAPdrop.bindString(2, emitter.getTypeString());
        sqlAPdrop.executeInsert();
        sqlAPdrop.clearBindings();
        updatesMade = true;
    }

    /**
     * Insert a new RF emitter into the database.
     *
     * @param emitter The emitter to be added.
     */
    public void insert(RfEmitter emitter) {
        //Log.d(TAG, "Inserting " + emitter.logString() + " into db");
        sqlSampleInsert.bindString(1, emitter.getId());
        sqlSampleInsert.bindString(2, String.valueOf(emitter.getType()));
        sqlSampleInsert.bindString(3, String.valueOf(emitter.getTrust()));
        sqlSampleInsert.bindString(4, String.valueOf(emitter.getLat()));
        sqlSampleInsert.bindString(5, String.valueOf(emitter.getLon()));
        sqlSampleInsert.bindString(6, String.valueOf(emitter.getRadius()));
        sqlSampleInsert.bindString(7, emitter.getNote());

        sqlSampleInsert.executeInsert();
        sqlSampleInsert.clearBindings();
        updatesMade = true;
    }

    /**
     * Update information about an emitter already existing in the database
     *
     * @param emitter The emitter to be updated
     */
    public void update(RfEmitter emitter) {
        //Log.d(TAG, "Updating " + emitter.logString() + " in db");
        // the data fields
        sqlSampleUpdate.bindString(1, String.valueOf(emitter.getTrust()));
        sqlSampleUpdate.bindString(2, String.valueOf(emitter.getLat()));
        sqlSampleUpdate.bindString(3, String.valueOf(emitter.getLon()));
        sqlSampleUpdate.bindString(4, String.valueOf(emitter.getRadius()));
        sqlSampleUpdate.bindString(5, emitter.getNote());

        // the Where fields
        sqlSampleUpdate.bindString(6, emitter.getId());
        sqlSampleUpdate.bindString(7, String.valueOf(emitter.getType()));
        sqlSampleUpdate.executeInsert();
        sqlSampleUpdate.clearBindings();
        updatesMade = true;
    }

    /**
     * Return a list of all emitters of a specified type within a bounding box.
     *
     * @param rfType The type of emitter the caller is interested in
     * @param bb The lat,lon bounding box.
     * @return A collection of RF emitter identifications
     */
    public HashSet<RfIdentification> getEmitters(RfEmitter.EmitterType rfType, BoundingBox bb) {
        HashSet<RfIdentification> rslt = new HashSet<RfIdentification>();
        String query = "SELECT " +
                COL_RFID + " " +
                " FROM " + TABLE_SAMPLES +
                " WHERE " + COL_TYPE + "='" + rfType +
                "' AND " + COL_LAT + ">='" + bb.getSouth() +
                "' AND " + COL_LAT + "<='" + bb.getNorth() +
                "' AND " + COL_LON + ">='" + bb.getWest() +
                "' AND " + COL_LON + "<='" + bb.getEast() + "';";

        //Log.d(TAG, "getEmitters(): query='"+query+"'");
        Cursor cursor = getReadableDatabase().rawQuery(query, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    RfIdentification e = new RfIdentification(cursor.getString(0), rfType);
                    rslt.add(e);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rslt;
    }

    /**
     * Get all the information we have on an RF emitter
     *
     * @param ident The identification of the emitter caller wants
     * @return A emitter object with all the information we have. Or null if we have nothing.
     */
    public RfEmitter getEmitter(RfIdentification ident) {
        RfEmitter rslt = null;
        String query = "SELECT " +
                COL_TYPE + ", " +
                COL_TRUST + ", " +
                COL_LAT + ", " +
                COL_LON + ", " +
                COL_RAD + ", " +
                COL_NOTE + " " +
                " FROM " + TABLE_SAMPLES +
                " WHERE " + COL_TYPE + "='" + ident.getRfType() +
                "' AND " + COL_RFID + "='" + ident.getRfId() + "';";

        // Log.d(TAG, "getEmitter(): query='"+query+"'");
        Cursor cursor = getReadableDatabase().rawQuery(query, null);
        try {
            if (cursor.moveToFirst()) {
                rslt = new RfEmitter(ident, 0);
                EmitterInfo ei = new EmitterInfo();
                ei.trust = (int) cursor.getLong(1);
                ei.latitude = (double) cursor.getDouble(2);
                ei.longitude = (double) cursor.getDouble(3);
                ei.radius = (float) cursor.getDouble(4);
                ei.note = cursor.getString(5);
                if (ei.note == null)
                    ei.note = "";
                rslt.updateInfo(ei);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rslt;
    }
}
