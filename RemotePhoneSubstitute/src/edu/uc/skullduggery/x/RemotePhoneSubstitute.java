package edu.uc.skullduggery.x;

import java.io.*;
import java.math.*;
import java.net.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import java.security.*;
import java.security.spec.*;
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

	/* Input and output audio must be of this format */
	private static final int sampleRate = 8000;     // 8khz
	private static final int sampleSize = 16;       // 16-bit, 2-byte
	private static final int channels = 1;          // mono
	private static final boolean bigEndian = false; // little-endian
	private static final byte[] magic = "SKUL".getBytes();
	
	
	
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
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(1024);
		
		KeyPair localKeys = kpg.generateKeyPair();//changed code
		RSAPublicKey pub = (RSAPublicKey) localKeys.getPublic();
		BigInteger localPubMod = pub.getModulus();
		BigInteger localPubExp = pub.getPublicExponent();
		
		writeMessage(dos, MessageType.PUBMOD, localPubMod.toByteArray());
		writeMessage(dos, MessageType.PUBEXP, localPubExp.toByteArray());
		byte[] remotePubModBytes = readMessage(dis, MessageType.PUBMOD);
		byte[] remotePubExpBytes = readMessage(dis, MessageType.PUBEXP);
		
		BigInteger remotePubMod = new BigInteger(remotePubModBytes);
		BigInteger remotePubExp = new BigInteger(remotePubExpBytes);
		
		RSAPublicKeySpec remotePubKeySpec =
		new RSAPublicKeySpec(remotePubMod, remotePubExp);
		KeyFactory keygen = KeyFactory.getInstance("RSA");
		PublicKey remotePublicKey = keygen.generatePublic(remotePubKeySpec);
		
		/*Get session key*/
		Cipher decryptor = 
		Cipher.getInstance(localKeys.getPrivate().getAlgorithm());
		decryptor.init(Cipher.DECRYPT_MODE, localKeys.getPrivate());
				
		System.out.println("Reading session key");
		byte[] sessionKeyBytes = readMessage(dis, MessageType.SESKEY);
		System.out.println("Session key read");
		
		System.out.println("Session Key:" + sessionKeyBytes.length);
		
		byte[] sessionKeyBytesDec = decryptor.doFinal(sessionKeyBytes);
				
		SecretKey sessionKey = new SecretKeySpec(sessionKeyBytesDec, "AES");
		
		/*Start talking*/
		Cipher encryptor = Cipher.getInstance(sessionKey.getAlgorithm());
		encryptor.init(Cipher.ENCRYPT_MODE, sessionKey);
		
		decryptor = Cipher.getInstance(sessionKey.getAlgorithm());
		decryptor.init(Cipher.DECRYPT_MODE, sessionKey);

		dos = new DataOutputStream(new CipherOutputStream(
		callSocket.getOutputStream(), encryptor));
		dis = new DataInputStream(new CipherInputStream(
		callSocket.getInputStream(), decryptor));
		
		byte[] buf = new byte[1024 * 4];
		int bytesRead = 0;
		int sentPackets = 0;
		
		while (true){
			System.out.println("Reading from the audio stream");
			// Read from loop (similar to microphone record)
			bytesRead = lais.read(buf, 0, buf.length);
			System.out.println("Read " + bytesRead + " bytes");
			System.out.println("Writing data to output stream");
			
			// Write packet of voice data
			writeMessage(dos, MessageType.VOICE,buf, bytesRead);
			System.out.println("Data written to output stream");
			
//			if(sentPackets > 1){
//				System.out.println("Reading incoming message");
//				buf = readMessage(dis);
//			}
			
			// Write to audio log (similar to speaker play)
			System.out.println("Writing incoming message to audio log");
			alog.write(buf);
		}
	}
	
	private static void readMagic(DataInputStream dis) throws IOException{
		byte[] magic = new byte[4];
		dis.readFully(magic, 0, magic.length);
		if (! Arrays.equals(magic, "SKUL".getBytes()))
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
		dos.write(magic);
		dos.write((byte) type.ordinal());
		dos.writeInt(data.length);
		dos.write(data);		
	}
	
	private static void writeMessage(DataOutputStream dos,MessageType type, byte[] data, int len) throws IOException
	{
		dos.write(magic);
		dos.write((byte) type.ordinal());
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
			if (bytesRead < 0){
				raf.seek(0);
				bytesRead = raf.read(buf, off, len);
			}
			return bytesRead;
		}
	}
}