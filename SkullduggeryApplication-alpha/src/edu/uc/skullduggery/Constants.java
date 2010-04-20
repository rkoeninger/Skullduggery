package edu.uc.skullduggery;

public class Constants {
	public static final String USERDBNAME = "SkullStorage";
	public static final String USERINFOTABLE = "UserInfo";
	public static final String USERINFOTABLESCHEMA =
		"CREATE TABLE IF NOT EXISTS " + USERINFOTABLE + "(" +
		"UserID INTEGER PRIMARY KEY ASC AUTOINCREMENT," +
		"Number CHAR(10), " +
		"Salt BLOB," +
		"PubKeyHash BLOB);";

	public static final String KEYDBNAME = "SkeletonKeys";
    public static final String KEYTABLE = "CryptoKeys";
    public static final String KEYTABLESCHEMA = 
            "CREATE TABLE IF NOT EXISTS " + KEYTABLE + "(" +
            "KeyID INTEGER PRIMARY KEY ASC AUTOINCREMENT," +
            "Modulus BLOB NOT NULL," +
            "PublicExponent BLOB NOT NULL," +
            "PrivateExponent BLOB NOT NULL);";
    
	public static final String KEYALIAS = "SkullKeys";	
	
	public static final String HASHALGORITHM = "HmacSHA1";
	public static final int HASHKEYSIZE = 128;
	public static final String SYMMALGORITHM = "AES";
	public static final int SYMKEYSIZE = 128;
	public static final String ASYMALGORITHM = "RSA";
	public static final int ASYMKEYSIZE = 4096;
	
}
