package edu.uc.skullduggery.x;

import java.io.*;
import java.math.*;
import java.net.*;
import javax.crypto.*;

import java.security.*;
import java.security.interfaces.*;
import java.util.*;

/**
 * This class is designed to mirror the process of the Skullduggery
 * comm system.
 * 
 * !!! It will become out of date every time something is changed in
 * SkullTalkService.java or Constants.java. Look over this file following
 * every mod to the main app.
 * 
 * @author bort
 *
 */
public class RemotePhoneSubstitute {


	public static final String SYMMALGORITHM = "AES";
	public static final String SYMMALGORITHMMODE = "AES";
	public static final int SYMKEYSIZE = 128;
	
	public static final String ASYMALGORITHM = "RSA";
	public static final String ASYMALGORITHMMODE = "RSA/ECB/PKCS1Padding";
	public static final int ASYMKEYSIZE = 1024;
	
	public enum MessageType
	{
		CALL,
		PUBMOD,
		PUBEXP,
		SESKEY,
		MESKEY,
		ACCEPT,
		REJECT,
		BUSY,
		VOICE,
		HANGUP;
	}
	
	/**
	 * args[0] = phone to call's ip
	 * args[1] = phone to call's port
	 * args[2] = outgoing audio file to loop
	 * args[3] = incoming audio log file
	 * 
	 * @param args
	 * @throws Exception
	 */
	
	public static void main(String[] args) throws Exception{
		
		/*Prepare audio and logging*/
		LoopedAudioInputStream lais =
		new LoopedAudioInputStream(new File(args[2]));
		FileOutputStream alog = new FileOutputStream(args[3]);
		
		/*Open socket - call other phone*/
		final String myPhoneNum = "1237890";
		Socket callSocket = new Socket();
		callSocket.connect(new InetSocketAddress(
				InetAddress.getByName(args[0]),
				Integer.parseInt(args[1])),
				5000);

		DataOutputStream dos =
		new DataOutputStream(callSocket.getOutputStream());
		DataInputStream dis =
		new DataInputStream(callSocket.getInputStream());
		
		/*Initial contact*/
		writeMessage(dos, MessageType.CALL, myPhoneNum.getBytes());
		readMessage(dis);
		
		/*Key exchange handshake*/
		KeyPairGenerator kpg = KeyPairGenerator.getInstance(ASYMALGORITHM);
		kpg.initialize(1024);
		
		KeyPair localKeys = kpg.generateKeyPair();//changed code
		RSAPublicKey pub = (RSAPublicKey) localKeys.getPublic();
		BigInteger localPubMod = pub.getModulus();
		BigInteger localPubExp = pub.getPublicExponent();
		System.out.println("Local Mod: " + localPubMod);
		System.out.println("Local Exp: " + localPubExp);
		
		writeMessage(dos, MessageType.PUBMOD, localPubMod.toByteArray());
		writeMessage(dos, MessageType.PUBEXP, localPubExp.toByteArray());
		byte[] remotePubModBytes = readMessage(dis, MessageType.PUBMOD);
		byte[] remotePubExpBytes = readMessage(dis, MessageType.PUBEXP);
		
		BigInteger remotePubMod = new BigInteger(remotePubModBytes);
		BigInteger remotePubExp = new BigInteger(remotePubExpBytes);
		
		System.out.println("Remote Mod: " + remotePubMod);
		System.out.println("Remote Exp: " + remotePubExp);
		
		/*Get session key*/
		Cipher decryptor = 
		Cipher.getInstance(ASYMALGORITHMMODE);
		decryptor.init(Cipher.UNWRAP_MODE, localKeys.getPrivate());
				
		System.out.println("Reading session key");
		byte[] sessionKeyWrapped = readMessage(dis, MessageType.SESKEY);
		System.out.println("Session key read");
		
		BigInteger sesKeyEnc = new BigInteger(sessionKeyWrapped);
		System.out.println("Session Key:" + sesKeyEnc);
		
		SecretKey sessionKey = (SecretKey) decryptor.unwrap(sessionKeyWrapped, SYMMALGORITHM,Cipher.SECRET_KEY);
		
		/*Start talking*/
		Cipher encryptor = Cipher.getInstance(SYMMALGORITHMMODE);
		encryptor.init(Cipher.ENCRYPT_MODE, sessionKey);
		
		decryptor = Cipher.getInstance(SYMMALGORITHMMODE);
		decryptor.init(Cipher.DECRYPT_MODE, sessionKey);

		dos = new DataOutputStream(new CipherOutputStream(
		callSocket.getOutputStream(), encryptor));
		dis = new DataInputStream(new CipherInputStream(
		callSocket.getInputStream(), decryptor));
		
		byte[] buf = new byte[4096];
		byte[] inBuf = null;
		int bytesRead = 0;
		int sentPackets = 0;
		
		while (true){
			System.out.println("Reading from the audio stream");
			// Read from loop (similar to microphone record)
			bytesRead = lais.read(buf, 0, buf.length);
			System.out.println("Read " + bytesRead + " bytes");

			// Write packet of voice data
			System.out.println("Writing data to output stream");
			writeMessage(dos, MessageType.VOICE, buf, bytesRead);
			System.out.println("Data written to output stream");
			sentPackets++;
			dos.flush();
			
			if(sentPackets > 1){
				System.out.println("Reading incoming message");
				inBuf = readMessage(dis);

				// Write to audio log (similar to speaker play)
				System.out.println("Writing incoming message to audio log");
				alog.write(inBuf);
			}
		}
	}

	private static final byte[] magicBytes = "SKUL".getBytes();
	
	private static void readMagic(DataInputStream dis) throws IOException{
		byte[] magic = new byte[4];
		dis.readFully(magic, 0, magic.length);
		if (! Arrays.equals(magic, magicBytes))
			throw new IOException("Wrong magic bytes");
	}

	private static void readType(DataInputStream dis, MessageType expectedType)
	throws IOException{
		if (dis.readByte() != (byte) expectedType.ordinal()){
			throw new IOException("Unexpected packet type");
		}
	}
	
	private static byte[] readMessage(DataInputStream dis, MessageType type) throws IOException
	{
		readMagic(dis);
		readType(dis, type);
		int len = dis.readInt();
		if(len <= 0) return null;
		byte[] data = new byte[len];
		dis.readFully(data);
		return data;		
	}
	
	private static byte[] readMessage(DataInputStream dis) throws IOException
	{
		readMagic(dis);
		dis.readByte();
		int len = dis.readInt();
		if(len <= 0) return null;
		byte[] data = new byte[len];
		dis.readFully(data);
		return data;		
	}
	
	private static void writeMessage(DataOutputStream dos,MessageType type, byte[] data) throws IOException
	{
		dos.write(magicBytes);
		dos.writeByte(type.ordinal());
		if(data == null)
		{
			dos.writeInt(0);
			return;
		}
		dos.writeInt(data.length);
		dos.write(data);		
	}
	
	private static void writeMessage(DataOutputStream dos,MessageType type, byte[] data, int len) throws IOException
	{
		dos.write(magicBytes);
		dos.writeByte(type.ordinal());
		if(data == null)
		{
			dos.writeInt(0);
			return;
		}
		dos.writeInt(len);
		dos.write(data,0,len);		
	}

	private static class LoopedAudioInputStream{
		RandomAccessFile raf;
		public LoopedAudioInputStream(File f) throws Exception{
			raf = new RandomAccessFile(f, "r");
		}
		public int read(byte[] buf, int off, int len) throws Exception{
			int bytesRead = raf.read(buf, off, len);
			while (bytesRead < 0){
				raf.seek(0);
				bytesRead = raf.read(buf, off, len);
			}
			return bytesRead;
		}
	}
}