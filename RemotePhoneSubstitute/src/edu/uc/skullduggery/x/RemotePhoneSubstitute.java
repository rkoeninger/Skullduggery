package edu.uc.skullduggery.x;

import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

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

	/* Input and output audio must be of this format */
	private static final int sampleRate = 8000;     // 8khz
	private static final int sampleSize = 16;       // 16-bit, 2-byte
	private static final int channels = 1;          // mono
	private static final boolean bigEndian = false; // little-endian
	
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
		callSocket.connect(InetSocketAddress.createUnresolved(
		args[0], Integer.parseInt(args[1])), 5000);
		DataOutputStream dos =
		new DataOutputStream(callSocket.getOutputStream());
		DataInputStream dis =
		new DataInputStream(callSocket.getInputStream());
		
		/*Initial contact*/
		dos.write("SKUL".getBytes()); // MAGICBYTES
		dos.writeByte(0); // CALL
		dos.writeInt(myPhoneNum.length());
		dos.write(myPhoneNum.getBytes());
		readMagic(dis);
		byte type = dis.readByte();
		if (type == 7){ // BUSY
			throw new IOException("Phone is busy");
		}
		if (type != 5){ // ACCEPT
			throw new IOException("Rejected");
		}
		
		/*Key exchange handshake*/
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(4096);
		
		KeyPair localKeys = kpg.generateKeyPair();//changed code
		RSAPublicKey pub = (RSAPublicKey) localKeys.getPublic();
		BigInteger localPubMod = pub.getModulus();
		BigInteger localPubExp = pub.getPublicExponent();
		
		dos.write("SKUL".getBytes());
		dos.write(1); // PUBMOD
		dos.writeInt(localPubMod.toByteArray().length);
		dos.write(localPubMod.toByteArray());
		dos.write("SKUL".getBytes());
		dos.write(2); // PUBEXP
		dos.writeInt(localPubExp.toByteArray().length);
		dos.write(localPubExp.toByteArray());
		
		readMagic(dis);
		readType(dis, 1); // PUBMOD
		byte[] remotePubModBytes = new byte[dis.readInt()];
		dis.readFully(remotePubModBytes);
		readMagic(dis);
		readType(dis, 2); // PUBEXP
		byte[] remotePubExpBytes = new byte[dis.readInt()];
		dis.readFully(remotePubExpBytes);
		BigInteger remotePubMod = new BigInteger(remotePubModBytes);
		BigInteger remotePubExp = new BigInteger(remotePubExpBytes);
		
		RSAPublicKeySpec remotePubKeySpec =
		new RSAPublicKeySpec(remotePubMod, remotePubExp);
		KeyFactory keygen = KeyFactory.getInstance("RSA");
		PublicKey remotePublicKey = keygen.generatePublic(remotePubKeySpec);
		
		/*Get session key*/
		Cipher decryptor = Cipher.getInstance(
				localKeys.getPrivate().getAlgorithm());
				decryptor.init(Cipher.DECRYPT_MODE, localKeys.getPrivate());
		dis = new DataInputStream(new CipherInputStream(
		callSocket.getInputStream(), decryptor));
		
		readMagic(dis);
		readType(dis, 3); // SESKEY
		int sessionKeyLength = dis.readInt();
		byte[] sessionKeyBytes =
		new byte[sessionKeyLength > 0 ? sessionKeyLength : 128];
		SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, "AES");
		
		/*Start talking*/
		Cipher encryptor = Cipher.getInstance(
		remotePublicKey.getAlgorithm());
		encryptor.init(Cipher.ENCRYPT_MODE, sessionKey);
		decryptor = Cipher.getInstance(
		localKeys.getPrivate().getAlgorithm());
		decryptor.init(Cipher.DECRYPT_MODE, sessionKey);

		dos = new DataOutputStream(new CipherOutputStream(
		callSocket.getOutputStream(), encryptor));
		dis = new DataInputStream(new CipherInputStream(
		callSocket.getInputStream(), decryptor));
		
		byte[] buf = new byte[1024 * 4];
		int bytesRead = 0;
		
		while (true){
			// Read from loop (similar to microphone record)
			bytesRead = lais.read(buf, 0, buf.length);
			
			// Write packet of voice data
			dos.write("SKUL".getBytes());
			dos.writeByte(8); // VOICE
			dos.writeInt(bytesRead);
			dos.write(buf, 0, bytesRead);
			
			// Read packet of voice data
			readMagic(dis);
			int messageType = dis.readByte();
			if (messageType == 9){ // HANGUP
				throw new EOFException("Remote phone hung-up");
			} else if (messageType != 8){ // VOICE
				throw new IOException("Unexpected packet type");
			}
			bytesRead = dis.readInt();
			dis.readFully(buf, 0, bytesRead);
			
			// Write to audio log (similar to speaker play)
			alog.write(buf, 0, bytesRead);
		}
	}
	
	private static void readMagic(DataInputStream dis) throws IOException{
		byte[] magic = new byte[4];
		dis.readFully(magic, 0, magic.length);
		if (! Arrays.equals(magic, "SKUL".getBytes()))
			throw new IOException("Wrong magic bytes");
	}

	private static void readType(DataInputStream dis, int expectedType)
	throws IOException{
		if (dis.readByte() != expectedType){
			throw new IOException("Unexpected packet type");
		}
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