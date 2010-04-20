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
	private final String HMAC = Constants.HASHALGORITHM;
	private final String AES = Constants.SYMMALGORITHM;
	private SecretKey _aesEncryptKey, _aesDecryptKey, _hashKey;
	private Cipher _encryptor, _decryptor;
	private Mac _mac;
	
	public class TrickeryException extends Exception
	{
		private static final long serialVersionUID = -5951444176541432600L;
	}
	public SkullMessageFactory(SecretKey cryptoKey, SecretKey hashKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException
	{
		_aesEncryptKey = new SecretKeySpec(cryptoKey.getEncoded(), AES);
		_aesDecryptKey = new SecretKeySpec(cryptoKey.getEncoded(), AES);
		_hashKey = new SecretKeySpec(hashKey.getEncoded(), HMAC);
		_encryptor = Cipher.getInstance(AES);
		_encryptor.init(Cipher.ENCRYPT_MODE, _aesEncryptKey);
		
		_decryptor = Cipher.getInstance(AES);
		_decryptor.init(Cipher.DECRYPT_MODE, _aesDecryptKey);
		
		_mac = Mac.getInstance(HMAC);
		_mac.init(_hashKey);
	}
	
	public SkullMessage createMessage(byte[] m) throws IllegalBlockSizeException, BadPaddingException
	{
		byte[] cipherText = _encryptor.update(m);
		_mac.update(m);
		
		return new SkullMessage(cipherText);
	}
	
	public int getHashSize()
	{
		return _mac.getMacLength();
	}
	
	public byte[] getHash() throws InvalidKeyException
	{
		byte[] b = _mac.doFinal();
		_mac.init(_hashKey);
		return b;
	}
	
	public int getBlockSize()
	{
		return _encryptor.getBlockSize();
	}
	
	public SkullMessage readMessage(byte[] cipherText) throws IllegalBlockSizeException, BadPaddingException
	{
		byte[] plainText = _decryptor.update(cipherText);
		_mac.update(plainText);
		
		return new SkullMessage(plainText);
	}
}
