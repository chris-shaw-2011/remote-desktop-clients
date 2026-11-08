package com.iiordanov.bVNC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatabasePasswordTest {
    private Database helper;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        helper = mock(Database.class, CALLS_REAL_METHODS);
        db = mock(SQLiteDatabase.class);
        doReturn(db).when(helper).getReadableDatabase();
        doNothing().when(helper).close();
        doReturn("/nonexistent/database").when(db).getPath();
    }

    @After
    public void tearDown() {
        Database.setPassword("");
    }

    @Test
    public void successfulRekeyEscapesQuoteAndPublishesPassword() {
        Database.setPassword("old");
        assertTrue(helper.changeDatabasePassword("new'key"));
        verify(db).execSQL("PRAGMA rekey = 'new''key'");
        assertEquals("new'key", Database.getPassword());
        verify(helper).close();
    }

    @Test
    public void failedRekeyKeepsOldPasswordAndClosesHelper() {
        Database.setPassword("old");
        doThrow(new IllegalStateException()).when(db).execSQL(anyString());
        assertFalse(helper.changeDatabasePassword("new"));
        assertEquals("old", Database.getPassword());
        verify(helper).close();
    }

    @Test
    public void failedExportKeepsOldPasswordAndClosesResources() {
        Database.setPassword("");
        Cursor cursor = mock(Cursor.class);
        doReturn(cursor).when(db).rawQuery("SELECT sqlcipher_export('converted')", null);
        assertFalse(helper.changeDatabasePassword("new'key"));
        verify(db).execSQL("ATTACH DATABASE '/nonexistent/database-temp' AS converted KEY 'new''key'");
        verify(db).execSQL("DETACH DATABASE converted");
        verify(cursor).close();
        verify(helper).close();
        assertEquals("", Database.getPassword());
    }
}
