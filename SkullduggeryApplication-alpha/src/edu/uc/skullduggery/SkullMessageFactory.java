package edu.uc.skullduggery;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SkullMessageFactory {
	private static final String HMAC = Constants.HASHALGORITHM;
	
	private SecretKey _hashKey;
	private Mac _mac;
	
	public class TrickeryException extends Exception
	{
		private static final long serialVersionUID = -5951444176541432600L;
	}
	
	/* Creates a new instance of SkullMessageFactory with a new hash key */
	/* Note: Not a singleton object. We don't want that; multiple calls should have different hash keys */
	/* A single call should only have one hash key though. */
	/* Returns null upon failure */
	public static SkullMessageFactory getInstance()
	{
		try{
			KeyGenerator keygen;
			SecretKey hashKey;
		
			keygen = KeyGenerator.getInstance(HMAC);
			keygen.init(Constants.HASHKEYSIZE);
			hashKey = keygen.generateKey();
			return new SkullMessageFactory(hashKey);
		}
		catch (NoSuchAlgorithmException e)
		{	
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public SkullMessageFactory(SecretKey hashKey) throws NoSuchAlgorithmException, InvalidKeyException
	{
		_hashKey = new SecretKeySpec(hashKey.getEncoded(), HMAC);
		_mac = Mac.getInstance(HMAC);
		_mac.init(_hashKey);
	}
	
	public SkullMessage createMessage(byte[] data, SkullMessage.MessageType type)
	{
		return new SkullMessage(_mac.doFinal(data), type, data);
	}
	
	public int getHashSize()
	{
		return _mac.getMacLength();
	}
	
	public boolean checkHash(SkullMessage m)
	{
		byte[] messageData, dataHash, messageHash;
		
		messageData = m.getData();
		messageHash = m.getHash();
		
		dataHash = _mac.doFinal(messageData);
		return java.util.Arrays.equals(messageHash, dataHash);
	}
	
	public static void writeMessage(DataOutputStream dos, SkullMessage mes) throws IOException
	{
		byte[] hash = mes.getHash();
		byte[] data = mes.getData();
		SkullMessage.MessageType mesType = mes.getType();
		
		dos.write(Constants.MAGICBYTES);
		dos.write(hash);
		dos.writeByte((byte) mesType.ordinal());
		dos.writeInt(data.length);
		dos.write(data);
	}
	public static SkullMessage readMessage(InputStream s)
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
