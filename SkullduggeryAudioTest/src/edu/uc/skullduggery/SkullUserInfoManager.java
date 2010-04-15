package edu.uc.skullduggery;

import java.security.NoSuchAlgorithmException;

import android.content.ContentValues;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

public class SkullUserInfoManager {
	Bundle state;
	SQLiteDatabase sqldb;
	
	private static final String SkullStorage = Constants.DBNAME;
	private static final String tableName = Constants.USERINFOTABLE;
	private static final String sql_CreateUserInfoTable = Constants.USERINFOTABLESCHEMA;
	
	public SkullUserInfoManager(ContextWrapper context)
	{
		sqldb = context.openOrCreateDatabase(SkullStorage,ContextWrapper.MODE_PRIVATE, null);
		sqldb.execSQL(sql_CreateUserInfoTable);
	}
	
	public synchronized void saveInstance(SkullUserInfo SUI)
	{
		ContentValues saveArgs = new ContentValues();
		saveArgs.put("Number", SUI.getNumber());
		saveArgs.put("Salt", SUI.getSalt());
		saveArgs.put("PubKeyHash", SUI.getHash());
		
		if(SUI.getUserId() > 0)
		{
			String[] whereArgs = {Integer.toString(SUI.getUserId())};
			
			sqldb.beginTransaction();
			sqldb.update("UserInfo", saveArgs, "UserID = ?",whereArgs);
			sqldb.endTransaction();
		}
		else
		{
			sqldb.beginTransaction();
			long UserId = sqldb.insertOrThrow(tableName, null, saveArgs);
			sqldb.endTransaction();
			SUI.setUserId((int) UserId);
		}
	}
	
	public synchronized SkullUserInfo getInstance(String number) throws NoSuchAlgorithmException
	{
		SkullUserInfo retUserInfo;
		//sqldb.query(table, columns, selection, selectionArgs, groupBy, having, orderBy,limit)
		String[] columns = {"UserID", "Number","Salt","PubKeyHash"};
		String[] selectionArgs = {number};
		
		Cursor skullCurs = sqldb.query(SkullStorage ,columns,"Number = ?", selectionArgs,null,null,null);
		if(skullCurs.moveToNext())
		{

			int	dataUserId = skullCurs.getInt(skullCurs.getColumnIndex("UserID"));
			String dataNumber = skullCurs.getString(skullCurs.getColumnIndex("Number"));
			byte[] dataSalt = skullCurs.getBlob(skullCurs.getColumnIndex("Salt"));
			byte[] dataPubKeyHash = skullCurs.getBlob(skullCurs.getColumnIndex("PubKeyHash"));
			skullCurs.close();
			retUserInfo = new SkullUserInfo(dataNumber,dataUserId,dataSalt,dataPubKeyHash);
		}
		else
		{
			retUserInfo = new SkullUserInfo(number);
		}
		
		return retUserInfo;
	}
	
	public void close()
	{
		sqldb.close();
	}
}
