package edu.uc.skullduggery;

public class Constants {
	public static final String DBNAME = "SkullStorage";
	public static final String USERINFOTABLE = "UserInfo";
	public static final String USERINFOTABLESCHEMA =
		"CREATE TABLE IF NOT EXISTS " + USERINFOTABLE + "(" +
		"UserID INTEGER PRIMARY KEY ASC AUTOINCREMENT," +
		"Number CHAR(10), " +
		"Salt BLOB," +
		"PubKeyHash BLOB);";
	public static final String KEYTABLE = "CryptoKeys";
	public static final String KEYTABLESCHEMA = 
		"CREATE TABLE IF NOT EXISTS " + KEYTABLE + "(" +
		"KeyID INTEGER PRIMARY KEY ASC AUTOINCREMENT," +
		"Public BLOB NOT NULL," +
		"Private BLOB NOT NULL);";
}
