package edu.uc.skullduggery;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SkullMessageFactory {
	private final String HMAC = Constants.HASHALGORITHM;
	private SecretKey _hashKey;
	private Mac _mac;
	
	public class TrickeryException extends Exception
	{
		private static final long serialVersionUID = -5951444176541432600L;
	}
	
	public SkullMessageFactory(SecretKey hashKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException
	{
		_hashKey = new SecretKeySpec(hashKey.getEncoded(), HMAC);
		_mac = Mac.getInstance(HMAC);
		_mac.init(_hashKey);
	}
	
	public SkullMessage createMessage(byte[] data, SkullMessage.MessageType type) throws IllegalBlockSizeException, BadPaddingException
	{
		return new SkullMessage(_mac.doFinal(data), type, data);
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
	
	public boolean checkHash(SkullMessage m)
	{
		byte[] data = m.getData();
		_mac.update(data);
		byte[] messageHash = m.getHash();
		byte[] dataHash = _mac.doFinal();
		
		return java.util.Arrays.equals(messageHash, dataHash);
	}
	
	public SkullMessage readMessage(InputStream s) throws IllegalBlockSizeException, BadPaddingException
	{
		SkullMessage rMessage = null;
		//TODO: Read MAC from the stream.
		//TODO: Read Type from the stream.
		//TODO: Read data length from the stream.
		//TODO: Read data from the stream.
		//TODO: Construct a SkullMessage
		//TODO: Return our SkullMessage
		return rMessage;
	}
}
