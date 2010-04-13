package edu.uc.skullduggery;

import android.app.Activity;
import android.content.ContentValues;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

public class SqlTest extends Activity {
	private static final String sql_CreateTable = 
		"CREATE TABLE IF NOT EXISTS TestTable (" +
		"TestID INTEGER PRIMARY KEY ASC AUTOINCREMENT," +
		"Test1 CHAR(10), " +
		"Test2 CHAR(10));";
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        SQLiteDatabase sqldb = this.openOrCreateDatabase("SkullTest.db",ContextWrapper.MODE_PRIVATE, null);
        sqldb.execSQL(sql_CreateTable);
        ContentValues TestInsert = new ContentValues();
        ContentValues TestReplace = new ContentValues();
        //ContentValues TestUpdate = new ContentValues();
        
        TestInsert.put("Test1","Ins.1");
        TestInsert.put("Test2","Ins.2");
        TestReplace.put("Test1","Rep.1");
        TestReplace.put("Test2","Rep.2");
        
        sqldb.insert("TestTable", null, TestInsert);
        sqldb.insert("TestTable", null, TestInsert);
        sqldb.replace("TestTable", null, TestReplace);
        
    }
}