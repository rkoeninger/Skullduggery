package edu.uc.skullduggery;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SkullMessageFactory {
	private final String HMAC = "HmacSHA1";
	private final String AES = "AES";
	private SecretKey _cryptoKey, _hashKey;
	private SecretKeySpec _AESKey;
	private Cipher _crypto;
	private Mac _mac;
	
	public class TrickeryException extends Exception
	{
		private static final long serialVersionUID = -5951444176541432600L;
	}
	public SkullMessageFactory(SecretKey cryptoKey, SecretKey hashKey) throws NoSuchAlgorithmException, NoSuchPaddingException
	{
		_cryptoKey = cryptoKey;
		_hashKey = hashKey;
		_AESKey = new SecretKeySpec(_cryptoKey.getEncoded(), AES);
		_crypto = Cipher.getInstance(AES);
		_mac = Mac.getInstance(HMAC);
	}
	
	public SkullMessage createMessage(byte[] m) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException
	{
		_crypto.init(Cipher.ENCRYPT_MODE, _AESKey);
		byte[] cipherText = _crypto.doFinal(m);
		_mac.init(_hashKey);
		byte[] mac = _mac.doFinal(m);
		
		return new SkullMessage(cipherText, mac);
	}
	
	public int getHashSize()
	{
		return _mac.getMacLength();
	}
	public int getBlockSize()
	{
		return _crypto.getBlockSize();
	}
	public SkullMessage readMessage(byte[] m) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, TrickeryException
	{
		int macLength = _mac.getMacLength();
		_crypto.init(Cipher.DECRYPT_MODE, _AESKey);
		byte[] message = _crypto.doFinal(m,macLength,m.length - macLength);
		_mac.init(_hashKey);
		byte[] mac_check = _mac.doFinal(message);
		
		/*
		for(int i = 0; i<mac_check.length; i++)
			if(mac_check[i] != m[i]){
				throw new TrickeryException();
			}
		*/
		return new SkullMessage(message, mac_check);
	}
}
