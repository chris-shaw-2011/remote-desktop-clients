/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
 * <p>
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

package com.iiordanov.bVNC;

import static com.iiordanov.bVNC.Constants.ENABLE_GLYPH_CACHE_DEFAULT;
import static com.iiordanov.bVNC.Constants.INVISIBLE_DEFAULT;
import static java.lang.String.format;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import com.iiordanov.bVNC.input.AbstractMetaKeyBean;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import java.io.File;

/**
 * @author Michael A. MacDonald
 */
public class Database extends SQLiteOpenHelper {

    /**
     * Operation that uses a {@link SQLiteDatabase} and returns a result.
     * Paired with {@link #withReadable(Context, DbOp)} / {@link #withWritable(Context, DbOp)}.
     */
    @FunctionalInterface
    public interface DbOp<T> {
        T apply(SQLiteDatabase db);
    }

    /**
     * Void variant of {@link DbOp}; see {@link #runReadable} / {@link #runWritable}.
     */
    @FunctionalInterface
    public interface DbVoidOp {
        void apply(SQLiteDatabase db);
    }

    /**
     * Runs {@code op} against a freshly opened readable database and guarantees
     * the underlying {@link SQLiteOpenHelper} is closed afterwards even on
     * exception. Use this for any single-shot DB access; leaking the helper
     * pushes its native sqlcipher handle teardown onto the FinalizerDaemon
     * and risks tripping the 10-second FinalizerWatchdog.
     */
    public static <T> T withReadable(Context context, DbOp<T> op) {
        Database database = new Database(context);
        try {
            return op.apply(database.getReadableDatabase());
        } finally {
            database.close();
        }
    }

    /**
     * Writable equivalent of {@link #withReadable(Context, DbOp)}.
     */
    public static <T> T withWritable(Context context, DbOp<T> op) {
        Database database = new Database(context);
        try {
            return op.apply(database.getWritableDatabase());
        } finally {
            database.close();
        }
    }

    /**
     * Void readable variant; see {@link #withReadable(Context, DbOp)}.
     */
    public static void runReadable(Context context, DbVoidOp op) {
        withReadable(context, db -> {
            op.apply(db);
            return null;
        });
    }

    /**
     * Void writable variant; see {@link #withWritable(Context, DbOp)}.
     */
    public static void runWritable(Context context, DbVoidOp op) {
        withWritable(context, db -> {
            op.apply(db);
            return null;
        });
    }

    public final static String TAG = Database.class.toString();
    static final int DBV_0_5_0 = 12;
    static final int DBV_1_2_0 = 20;
    static final int DBV_1_5_0 = 22;
    static final int DBV_1_6_0 = 291;
    static final int DBV_1_7_0 = 292;
    static final int DBV_1_8_0 = 293;
    static final int DBV_1_9_0 = 308;
    static final int DBV_2_0_0 = 309;
    static final int DBV_2_1_0 = 329;
    static final int DBV_2_1_1 = 335;
    static final int DBV_2_1_2 = 336;
    static final int DBV_2_1_3 = 360;
    static final int DBV_2_1_4 = 367;
    static final int DBV_2_1_5 = 374;
    static final int DBV_2_1_6 = 431;
    static final int DBV_2_1_7 = 501;
    static final int DBV_2_1_8 = 507;
    static final int DBV_2_1_9 = 521;
    static final int DBV_2_2_0 = 525;
    static final int DBV_2_2_1 = 561;
    static final int DBV_2_2_2 = 602;
    static final int DBV_2_2_3 = 626;
    static final int DBV_2_2_4 = 640;
    static final int DBV_2_2_5 = 641;
    static final int DBV_2_2_6 = 642;
    static final int CURRVERS = DBV_2_2_6;
    private static final String dbName = "VncDatabase";
    private static String password = "";

    private final Context context;
    private final String helperPassword;
    private final boolean followPassword;
    private Database replacement;

    public Database(Context context) {
        this(context, Database.password, true);
    }

    private Database(Context context, String password, boolean followPassword) {
        super(context, dbName, password, null, CURRVERS, 0, null, null, false);
        this.context = context;
        this.helperPassword = password;
        this.followPassword = followPassword;
        System.loadLibrary("sqlcipher");
    }

    public static String getPassword() {
        return password;
    }

    public static void setPassword(String newPassword) {
        Database.password = newPassword;
    }

    private String boolToString(boolean value) {
        return value ? "TRUE" : "FALSE";
    }

    /* (non-Javadoc)
     * @see net.zetetic.database.sqlcipher.SQLiteOpenHelper#onCreate(net.zetetic.database.sqlcipher.SQLiteDatabase)
     */
    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(AbstractConnectionBean.GEN_CREATE);
        db.execSQL(MostRecentBean.GEN_CREATE);
        db.execSQL(MetaList.GEN_CREATE);
        db.execSQL(AbstractMetaKeyBean.GEN_CREATE);
        db.execSQL(SentTextBean.GEN_CREATE);

        db.execSQL("INSERT INTO " + MetaList.GEN_TABLE_NAME + " VALUES ( 1, 'DEFAULT')");
    }

    // SQLCipher binds the password at helper construction. A long-lived helper
    // must use a new helper after the master password changes.
    private Database currentHelper() {
        if (!followPassword || helperPassword.equals(Database.password)) {
            if (replacement != null) {
                replacement.close();
                replacement = null;
            }
            return this;
        }
        if (replacement == null || !replacement.helperPassword.equals(Database.password)) {
            if (replacement != null) replacement.close();
            super.close();
            replacement = new Database(context, Database.password, false);
        }
        return replacement;
    }

    @Override
    public synchronized SQLiteDatabase getReadableDatabase() {
        Database helper = currentHelper();
        return helper == this ? super.getReadableDatabase() : helper.getReadableDatabase();
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        Database helper = currentHelper();
        return helper == this ? super.getWritableDatabase() : helper.getWritableDatabase();
    }

    @Override
    public synchronized void close() {
        if (replacement != null) {
            replacement.close();
            replacement = null;
        }
        super.close();
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    public boolean changeDatabasePassword(String newPassword) {
        SQLiteDatabase db = getReadableDatabase();
        String pathToDb = db.getPath();
        String tempPath = pathToDb + "-temp";
        String backupPath = pathToDb + "-BAK";
        boolean oldMoved = false;
        try {
            if (Database.password.equals(newPassword)) return true;
            if (!Database.password.isEmpty() && !newPassword.isEmpty()) {
                db.execSQL("PRAGMA rekey = " + sqlLiteral(newPassword));
                Database.setPassword(newPassword);
                return true;
            }

            // Never overwrite a backup left by a failed restoration.
            if (new File(backupPath).exists()) return false;
            deleteTempDatabase(pathToDb, "-temp");
            if (new File(tempPath).exists()) return false;
            int version = db.getVersion();
            db.execSQL("ATTACH DATABASE " + sqlLiteral(tempPath) + " AS converted KEY " + sqlLiteral(newPassword));
            try {
                try (Cursor cursor = db.rawQuery("SELECT sqlcipher_export('converted')", null)) {
                    if (!cursor.moveToFirst()) throw new IllegalStateException("Database export returned no result");
                }
            } finally {
                db.execSQL("DETACH DATABASE converted");
            }
            close();

            SQLiteDatabase tempDb = SQLiteDatabase.openDatabase(tempPath, newPassword, null, SQLiteDatabase.OPEN_READWRITE, null);
            try {
                tempDb.setVersion(version);
            } finally {
                tempDb.close();
            }
            if (!moveFile(pathToDb, backupPath)) return false;
            oldMoved = true;
            if (!moveFile(tempPath, pathToDb)) throw new IllegalStateException("Cannot install converted database");
            SQLiteDatabase newDb = SQLiteDatabase.openDatabase(pathToDb, newPassword, null, SQLiteDatabase.OPEN_READWRITE, null);
            try {
                newDb.getVersion();
            } finally {
                newDb.close();
            }
            Database.setPassword(newPassword);
            deleteTempDatabase(pathToDb, "-BAK");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "changeDatabasePassword failed", e);
            if (oldMoved && !moveFile(backupPath, pathToDb)) {
                Log.e(TAG, "Database restore failed; backup retained at " + backupPath);
            }
            return false;
        } finally {
            close();
            deleteTempDatabase(pathToDb, "-temp");
        }
    }

    private boolean moveFile(String from, String to) {
        boolean result = new File(from).renameTo(new File(to));
        if (!result) {
            Log.e(TAG, format("moveFile - Failed to move database file %s to %s", from, to));
        }
        return result;
    }

    private void deleteTempDatabase(String pathToDb, String suffix) {
        String tempDbPath = pathToDb + suffix;
        File temp = new File(tempDbPath);
        if (temp.exists()) {
            if (!temp.delete()) {
                Log.e(TAG, format("deleteTempDatabase - Failed to delete database at path %s", tempDbPath));
            }
        }
    }

    /* (non-Javadoc)
     * @see net.zetetic.database.sqlcipher.SQLiteOpenHelper#onUpgrade(net.zetetic.database.sqlcipher.SQLiteDatabase, int, int)
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == DBV_0_5_0) {
            Log.i(TAG, "Doing upgrade from 12 to 20");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CONNECTIONTYPE + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHSERVER + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHPORT + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHUSER + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHPASSWORD + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_KEEPSSHPASSWORD + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHPUBKEY + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHPRIVKEY + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHPASSPHRASE + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USESSHPUBKEY + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHHOSTKEY + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHREMOTECOMMANDOS + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHREMOTECOMMAND + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHREMOTECOMMANDTIMEOUT + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USESSHREMOTECOMMAND + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_1_2_0;
        }

        if (oldVersion == DBV_1_2_0) {
            Log.i(TAG, "Doing upgrade from 20 to 22");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USEDPADASARROWS + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_ROTATEDPAD + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USEPORTRAIT + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_1_5_0;
        }

        if (oldVersion == DBV_1_5_0) {
            Log.i(TAG, "Doing upgrade from 22 to 291");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SSHREMOTECOMMANDTYPE + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXENABLED + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXTYPE + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXCOMMAND + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXRESTYPE + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXWIDTH + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXHEIGHT + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXSESSIONTYPE + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXSESSIONPROG + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXRANDFILENM + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXUNIXPW + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_1_6_0;
        }

        if (oldVersion == DBV_1_6_0) {
            Log.i(TAG, "Doing upgrade from 291 to 292");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_AUTOXUNIXAUTH + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_1_7_0;
        }

        if (oldVersion == DBV_1_7_0) {
            Log.i(TAG, "Doing upgrade from 292 to 293");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_EXTRAKEYSTOGGLETYPE + " INTEGER");
            oldVersion = DBV_1_8_0;
        }

        if (oldVersion == DBV_1_8_0) {
            Log.i(TAG, "Doing upgrade from 293 to 308");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_PREFENCODING + " INTEGER");
            oldVersion = DBV_1_9_0;
        }

        if (oldVersion == DBV_1_9_0) {
            Log.i(TAG, "Doing upgrade from 308 to 309");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_RDPDOMAIN + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_RDPRESTYPE + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_RDPWIDTH + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_RDPHEIGHT + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_RDPCOLOR + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_REMOTEFX + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_DESKTOPBACKGROUND + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_FONTSMOOTHING + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_DESKTOPCOMPOSITION + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_WINDOWCONTENTS + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_MENUANIMATION + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_VISUALSTYLES + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_2_0_0;
        }

        if (oldVersion == DBV_2_0_0) {
            Log.i(TAG, "Doing upgrade from 309 to 329");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CACERT + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CACERTPATH + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_TLSPORT + " INTEGER");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CERTSUBJECT + " TEXT");
            oldVersion = DBV_2_1_0;
        }

        if (oldVersion == DBV_2_1_0) {
            Log.i(TAG, "Doing upgrade from 329 to 335");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_ENABLESOUND + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_2_1_1;
        }

        if (oldVersion == DBV_2_1_1) {
            Log.i(TAG, "Doing upgrade from 335 to 336");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_VIEWONLY + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_2_1_2;
        }

        if (oldVersion == DBV_2_1_2) {
            Log.i(TAG, "Doing upgrade from 336 to 360");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CONSOLEMODE + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_REDIRECTSDCARD + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_2_1_3;
        }

        if (oldVersion == DBV_2_1_3) {
            Log.i(TAG, "Doing upgrade from 360 to 367");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_LAYOUTMAP + " TEXT DEFAULT 'English (US)'");
            oldVersion = DBV_2_1_4;
        }

        if (oldVersion == DBV_2_1_4) {
            Log.i(TAG, "Doing upgrade from 367 to 374");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_ENABLERECORDING + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_REMOTESOUNDTYPE + " INTEGER DEFAULT " + Constants.REMOTE_SOUND_DISABLED);
            oldVersion = DBV_2_1_5;
        }

        if (oldVersion == DBV_2_1_5) {
            Log.i(TAG, "Doing upgrade from 374 to 430");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_FILENAME + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_X509KEYSIGNATURE + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SCREENSHOTFILENAME + " TEXT");
            oldVersion = DBV_2_1_6;
        }

        if (oldVersion == DBV_2_1_6) {
            Log.i(TAG, "Doing upgrade from 430 to 501");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_ENABLEGFX + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_ENABLEGFXH264 + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_2_1_7;
        }

        if (oldVersion == DBV_2_1_7) {
            Log.i(TAG, "Doing upgrade from 501 to 507");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_PREFERSENDINGUNICODE + " BOOLEAN DEFAULT FALSE");
            oldVersion = DBV_2_1_8;
        }

        if (oldVersion == DBV_2_1_8) {
            Log.i(TAG, "Doing upgrade from 507 to 521");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_EXTERNALID + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_REQUIRESVPN + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_VPNURISCHEME + " TEXT");
            oldVersion = DBV_2_1_9;
        }

        if (oldVersion == DBV_2_1_9) {
            Log.i(TAG, "Doing upgrade from 521 to 525");
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPGATEWAYENABLED
                            + " BOOLEAN DEFAULT FALSE"
            );
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPGATEWAYHOSTNAME + " TEXT"
            );
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPGATEWAYPORT + " INTEGER DEFAULT "
                            + Constants.DEFAULT_RDP_GATEWAY_PORT
            );
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPGATEWAYUSERNAME + " TEXT"
            );
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPGATEWAYDOMAIN + " TEXT"
            );
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPGATEWAYPASSWORD + " TEXT"
            );
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_KEEPRDPGATEWAYPASSWORD
                            + " BOOLEAN DEFAULT FALSE"
            );
            oldVersion = DBV_2_2_0;
        }
        if (oldVersion == DBV_2_2_0) {
            Log.i(TAG, "Doing upgrade from 515 to 561");
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_DESKTOPSCALEPERCENTAGE + " INTEGER DEFAULT "
                            + Constants.DEFAULT_DESKTOP_SCALE_PERCENTAGE
            );
            oldVersion = DBV_2_2_1;
        }
        if (oldVersion == DBV_2_2_1) {
            Log.i(TAG, "Doing upgrade from 561 to 602");
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_RDPSECURITY + " INTEGER DEFAULT "
                            + Constants.DEFAULT_RDP_SECURITY_AUTO_NEGOTIATE
            );
            oldVersion = DBV_2_2_2;
        }
        if (oldVersion == DBV_2_2_2) {
            Log.i(TAG, "Doing upgrade from 602 to 626");
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_ENABLEGLYPHCACHE
                            + " BOOLEAN DEFAULT " + boolToString(ENABLE_GLYPH_CACHE_DEFAULT)
            );
            oldVersion = DBV_2_2_3;
        }
        if (oldVersion == DBV_2_2_3) {
            Log.i(TAG, "Doing upgrade from 626 to 640");
            db.execSQL(
                    "ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                            + AbstractConnectionBean.GEN_FIELD_INVISIBLE
                            + " BOOLEAN DEFAULT " + boolToString(INVISIBLE_DEFAULT)
            );
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBAR + " BOOLEAN DEFAULT TRUE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBARX + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBARY + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBARMOVED + " BOOLEAN DEFAULT FALSE");
            migrateToolbarPrefsToDb(db);
            oldVersion = DBV_2_2_4;
        }
        if (oldVersion == DBV_2_2_4) {
            Log.i(TAG, "Doing upgrade from 640 to 641");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CLIENTAUTHPUBKEY + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CLIENTAUTHPRIVKEY + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CLIENTAUTHPASSPHRASE + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_CLIENTAUTHENABLED + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SVNCENABLED + " BOOLEAN DEFAULT FALSE");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_SVNCPASSPHRASE + " TEXT");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_KEEPSVNCPASSPHRASE + " BOOLEAN DEFAULT TRUE");
            oldVersion = DBV_2_2_5;
        }
        if (oldVersion == DBV_2_2_5) {
            Log.i(TAG, "Doing upgrade from 641 to 642");
            db.execSQL("ALTER TABLE " + AbstractConnectionBean.GEN_TABLE_NAME + " ADD COLUMN "
                    + AbstractConnectionBean.GEN_FIELD_RDPMONITORCOUNT + " INTEGER DEFAULT 1");
        }
    }

    /**
     * One-time migration: the toolbar last-position settings used to live in a
     * per-connection SharedPreferences file keyed by the connection's _id. Copy
     * each connection's saved values into the new DB columns, then clear the
     * obsolete prefs file so nothing is left orphaned.
     */
    private void migrateToolbarPrefsToDb(SQLiteDatabase db) {
        Cursor c = db.query(AbstractConnectionBean.GEN_TABLE_NAME,
                new String[]{AbstractConnectionBean.GEN_FIELD__ID},
                null, null, null, null, null);
        while (c.moveToNext()) {
            long id = c.getLong(0);
            SharedPreferences sp = context.getSharedPreferences(
                    Long.toString(id), Context.MODE_PRIVATE);
            if (!sp.contains(Constants.PREF_USE_LAST_POSITION_TOOLBAR)
                    && !sp.contains(Constants.PREF_USE_LAST_POSITION_TOOLBAR_X)
                    && !sp.contains(Constants.PREF_USE_LAST_POSITION_TOOLBAR_Y)
                    && !sp.contains(Constants.PREF_USE_LAST_POSITION_TOOLBAR_MOVED)) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put(AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBAR,
                    sp.getBoolean(Constants.PREF_USE_LAST_POSITION_TOOLBAR, true));
            v.put(AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBARX,
                    sp.getInt(Constants.PREF_USE_LAST_POSITION_TOOLBAR_X, 0));
            v.put(AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBARY,
                    sp.getInt(Constants.PREF_USE_LAST_POSITION_TOOLBAR_Y, 0));
            v.put(AbstractConnectionBean.GEN_FIELD_USELASTPOSITIONTOOLBARMOVED,
                    sp.getBoolean(Constants.PREF_USE_LAST_POSITION_TOOLBAR_MOVED, false));
            db.update(AbstractConnectionBean.GEN_TABLE_NAME, v,
                    AbstractConnectionBean.GEN_FIELD__ID + " = ?",
                    new String[]{Long.toString(id)});
            sp.edit().clear().apply();
        }
        c.close();
    }

    public boolean checkMasterPassword(String password, Context context) {
        Log.i(TAG, "Checking master password.");
        boolean result;

        Database testPassword = new Database(context, password, false);
        try {
            testPassword.getReadableDatabase();
            result = true;
        } catch (Exception e) {
            result = false;
        } finally {
            testPassword.close();
        }
        return result;
    }
}
