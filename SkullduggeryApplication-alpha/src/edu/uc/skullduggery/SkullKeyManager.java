package edu.uc.skullduggery;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SkullKeyManager {
	private final static String SkullStorage = Constants.KEYDBNAME;
	private final static String SkullKeys = Constants.KEYTABLE;
	private final static String sql_createKeyTable = Constants.KEYTABLESCHEMA;
	private final static String RSA = Constants.ASYMALGORITHM;
	private final static int RSAKeySize = Constants.ASYMKEYSIZE;
	
	//private final static String Password = Constants.KEYFILEPASS;
	private SQLiteDatabase _sqldb;
	
	public SkullKeyManager(ContextWrapper context)
	{
		_sqldb = context.openOrCreateDatabase(SkullStorage, Context.MODE_PRIVATE, null);
		_sqldb.execSQL(sql_createKeyTable);
	}
	
	public KeyPair getKeys() throws InvalidKeySpecException
	{
		_sqldb.beginTransaction();
		String[] columns = {"KeyID", "Modulus", "PublicExponent", "PrivateExponent"};
		String selection = null;
		String[] selectionArgs = null;
		String groupBy = null;
		String having = null;
		String orderBy = "KeyID DESC";
		String limit = "1"; 
		PublicKey pubKey;
		PrivateKey privKey;
		KeyPair kp;
		 
		Cursor keyCursor = _sqldb.query(SkullKeys, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
		if(keyCursor.moveToNext())
		{
			KeyFactory kf;
			try{
				kf = KeyFactory.getInstance(RSA);
			}catch (NoSuchAlgorithmException nsae){
				throw new Error(nsae);
			}
			RSAPublicKeySpec pubKeySpec;
			RSAPrivateKeySpec privKeySpec;
			
			byte[] modBytes, pubExponentBytes, privExponentBytes;
			BigInteger mod, pubExponent, privExponent;
			
			//read public, private keys	
			modBytes = keyCursor.getBlob(keyCursor.getColumnIndex("Modulus"));
			pubExponentBytes = keyCursor.getBlob(keyCursor.getColumnIndex("PublicExponent"));
			privExponentBytes = keyCursor.getBlob(keyCursor.getColumnIndex("PrivateExponent"));
			keyCursor.close();
			_sqldb.endTransaction();
			
			mod = new BigInteger(modBytes);
			pubExponent = new BigInteger(pubExponentBytes);
			privExponent = new BigInteger(privExponentBytes);
			
			pubKeySpec = new RSAPublicKeySpec(mod, pubExponent);
			privKeySpec = new RSAPrivateKeySpec(mod, privExponent);
			
			pubKey = kf.generatePublic(pubKeySpec);
			privKey = kf.generatePrivate(privKeySpec);
			
			kp = new KeyPair(pubKey, privKey);

			return kp;
		}
		else
		{
			//close cursor
			keyCursor.close();
			KeyPairGenerator kpg;
			ContentValues insertArgs;
			
			//Generate public, private keys
			try{
				kpg = KeyPairGenerator.getInstance(RSA);
			}catch (NoSuchAlgorithmException nsae){
				throw new Error(nsae);
			}
			kpg.initialize(RSAKeySize);
			kp = kpg.generateKeyPair();
			//Write keys to db
			insertArgs = new ContentValues();
			pubKey =  kp.getPublic();
			privKey = kp.getPrivate();
			
			insertArgs.put("Modulus",((RSAPublicKey) pubKey).getModulus().toByteArray());
			insertArgs.put("PublicExponent",((RSAPublicKey) pubKey).getPublicExponent().toByteArray());
			insertArgs.put("PrivateExponent",((RSAPrivateKey) privKey).getPrivateExponent().toByteArray());
			
			_sqldb.insert(SkullKeys, null, insertArgs);
			_sqldb.endTransaction();
			
			//TODO: Return public, private keys
			return kp;	
		}
	}
}
