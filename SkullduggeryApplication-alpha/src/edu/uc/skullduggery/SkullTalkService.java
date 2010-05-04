package edu.uc.skullduggery;

import edu.uc.skullduggery.SkullMessage.MessageType;
import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import android.content.*;
import android.media.*;
import android.os.*;
import android.telephony.TelephonyManager;
import android.util.*;

// TODO: Several here:      *Don't remove this T0D0 marker*

//       Rewrite documentation and COMMENTS as sections are changed.
//       All comments will need constant editing.

//       Call state infrastructure and interface methods
//       i.e. (start/stop/hangup/startComm/endComm)
//       Need to be re-written or removed

//       Might need to improve use of synchronization. Have certain
//       sections of code synchronized, not entire methods. Uses of callState
//       might need to be replaced with calls to
//       the synchronized getCallState()

//       TalkThread (and maybe also Call+Accept Threads) need to better
//       respond to network communication errors.

//       Communication is currently being done line by line
//        - that means, SkullMessages are not being used.
//       Neither are the writeMessage and readMessage methods.
//       Rewrite may be necessary.

//       SkullMessage or SkullMessage factory need to have encryption built
//       into them. If we had a block of data to transmit, we could call
//       SkullMessageFactory.setCipherInfo(...);
//       SkullMessageFactory.getMessage(data);

//       The handshake method could still use alot of work
//        - I didn't understand key exchange very well
//       So...
//       To keep this simple,
//       Each phone generates/loads it's keys
//       and sends PUB-key, MAC, whatever else in one round.
//       Each phone sends one packet.
//       Following this exchange, we are ready to talk.

//       ^^^ I ignored the idea of (checking cached key for security reasons)
//       for development speed reasons.

public class SkullTalkService{
	
	
	/* Logging tag for Android logcat */
	private static final String TAG = "SkullTalk";
	
	private static final int debug(String msg){ return Log.d(TAG, msg); }
	private static final int info(String msg){ return Log.i(TAG, msg); }
	
	/* Audio encoding parameters */
	private static final int sampleRate = 8000;
	private static final int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
	private static final int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
	private static final int encoding = AudioFormat.ENCODING_PCM_16BIT;
	
	public static enum CallState
	{LISTENING, CALLING, TALKING, STOPPED};
	
	private CallState callState;
	private Thread callThread;
	private Thread acceptThread;
	private Thread talkThread;
	private Handler uiHandler;
	private ContextWrapper appContext;
	private SkullKeyManager keyManager;
	
	private final String phoneNumber;
	
	private Socket callSocket;
	private KeyPair localKeys;
	private PublicKey remotePublicKey;
	private SecretKey sessionKey;
	
	/**
	 * Information about the remote phone current talking with.
	 * Specifically, the phone number.
	 * Should be null when callState does not equal TALKING.
	 */
	private String remotePhoneNumber;
	
	/**
	 * Contact info and interface for the proxy (switch station) server.
	 */
	private SwitchStationClient serverComm;
	private final String serverIP =
		/* 
		 * Comment out the first / of the first line to toggle hardcoded IP.
		 * Note: It is difficult to talk between two emulated instances with the emulator's virtual subnet crap
		 */
		//*
		 "192.168.200.11";/*/
		"10.0.2.2";
		//*/
	
	private final static int serverPort = 9002;
	private final static int listenPort = 9003;
	
	public SkullTalkService(Handler uiHandler, ContextWrapper context){
		this.uiHandler = uiHandler;
		this.appContext = context;
		phoneNumber = ((TelephonyManager) context.getSystemService(
		Context.TELEPHONY_SERVICE)).getLine1Number();
		
		//Setup for droid - Use server IP if it's my phone.
		//TODO: Convert this into a host name or user-configured value.
//		serverIP = /*(phoneNumber == "6147380764")?*/ "192.168.200.11";// : "10.0.2.2";
		
		serverComm = new SwitchStationClient();
	}

	/* *********************************************************************
	 * Functions are synchronized so there won't be concurrent changes
	 * to the state of the service from the UI/TransmitThread/ReceiveThread
	 ***********************************************************************/
	
	public synchronized CallState getCallState(){
		return callState;
	}
	
	public synchronized void setCallState(CallState state){
		callState = state;
	}
	
	public String getPhoneNumber(){
		return remotePhoneNumber;
	}
	
	public void start(){   
		//TODO: Read public key from file
		keyManager = new SkullKeyManager(appContext);
		serverComm.connect(serverIP, serverPort);
		
		// TODO: How to find our own IP address, etc
		try {
			serverComm.register(phoneNumber, listenPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		callState = CallState.LISTENING;
		acceptThread = new AcceptThread(listenPort);
		acceptThread.start();
	}
	
	public synchronized void stop(){
		serverComm.disconnect();
		if (callState != CallState.STOPPED){
			callState = CallState.STOPPED;
			uiHandler.sendEmptyMessage(2);
			talkThread = null;
			acceptThread = null;
			callThread = null;
		}
	}
	
	private void closeCall(){
		if(callSocket == null || !callSocket.isConnected()) return;
		try{
			callSocket.close();
		}
		catch(Exception e){
			
		}
	}
	
	public synchronized void hangup(){
		if ((callState == CallState.TALKING) ||
		(callState == CallState.CALLING)){
			callState = CallState.LISTENING;
			uiHandler.sendEmptyMessage(2);
			talkThread = null;
			callThread = null;
			remotePhoneNumber = null;
		}
	}
	
	public synchronized void dial(String number){
		if (callState == CallState.LISTENING){
			callState = CallState.CALLING;
			uiHandler.sendEmptyMessage(4);
			callThread = new CallThread(number);
			callThread.start();
		}
	}
	
	private synchronized void internalStartTalking(){
		callState = CallState.TALKING;
		uiHandler.sendEmptyMessage(3);
		talkThread = new TalkThread();
		talkThread.start();
	}
		
	/**
	 * Attempts to read the magic bytes from the given stream and throws
	 * an exception if they are not present.
	 * 
	 * @throws IOException
	 */
	private static void readMagic(DataInputStream dis) throws IOException{
		byte[] magic = new byte[Constants.MAGICBYTES.length];
		dis.readFully(magic, 0, magic.length);
		if (! Arrays.equals(magic, Constants.MAGICBYTES))
			throw new IOException("Wrong magic bytes");
	}

	/**
	 * Attempts to read the expected type code from the given stream and throws
	 * an exception if it is not present.
	 * 
	 * @throws IOException
	 */
	private static void readType(DataInputStream dis, MessageType expectedType)
	throws IOException{
		if (dis.readByte() != expectedType.ordinal()){
			throw new IOException("Unexpected packet type");
		}
	}
	
	private static SkullMessage readMessage(DataInputStream dis, MessageType expectedType) throws IOException
	{
		readMagic(dis);
		readType(dis, expectedType);
		
		int dataLength = dis.readInt();
		if(dataLength <= 0)
			return new SkullMessage(expectedType);
		
		byte[] data = new byte[dataLength];
		dis.readFully(data);
		return new SkullMessage(expectedType, data);
	}
	
	private static SkullMessage readMessage(DataInputStream dis) throws IOException
	{
		readMagic(dis);
		byte typeByte = dis.readByte();
		MessageType mesType = MessageType.values()[typeByte];
		
		int dataLength = dis.readInt();
		if(dataLength <= 0)
			return new SkullMessage(mesType);
		
		byte[] data = new byte[dataLength];
		dis.readFully(data);
		return new SkullMessage(mesType, data);
	}
	
	private static void sendMessage(DataOutputStream dos, SkullMessage mes) throws IOException
	{
		dos.write(Constants.MAGICBYTES);
		dos.writeByte((byte) mes.getType().ordinal());
		byte[] data = mes.getData();
		if(data == null)
		{
			dos.writeInt(0);
			return;
		}
		dos.writeInt(data.length);
		dos.write(data);
	}
	
	private static void sendMessage(DataOutputStream dos, MessageType mType, byte[] data, int length) throws IOException
	{
		dos.write(Constants.MAGICBYTES);
		dos.writeByte((byte) mType.ordinal());
		dos.writeInt(length);
		dos.write(data, 0, length);
	}
	
	/**
	 * Key generation and exchange occurs here.
	 * @throws InvalidKeySpecException 
	 * @throws IOException 
	 */
	private void performHandshake(DataInputStream dis, DataOutputStream dos)
	throws InvalidKeySpecException, IOException
	{
		debug("Performing handshake");
		/*
		 * Generate local phone's keys for this conversation.
		 */
		KeyFactory keygen;
		BigInteger localPubMod, localPubExp;
		try{
			keygen = KeyFactory.getInstance(Constants.ASYMALGORITHM);
		}catch (NoSuchAlgorithmException nsae){
			throw new Error(nsae); // Can't recover from this
		}
		debug("Retrieving keys");
		localKeys = keyManager.getKeys();
		RSAPublicKey pub = (RSAPublicKey) localKeys.getPublic();
		localPubMod = pub.getModulus();
		localPubExp = pub.getPublicExponent();
		
		/*
		 * Write our public key Modulus and Exponent.
		 * Each one is written in its own packet.
		 */
		debug("Writing mod");
		info("Mod: " + localPubMod);
		sendMessage(dos, 
				new SkullMessage(MessageType.PUBMOD, localPubMod.toByteArray()));
		
		debug("Writing exp");
		info("Exp: " + localPubExp);
		sendMessage(dos,
				new SkullMessage(MessageType.PUBEXP, localPubExp.toByteArray()));
		
		/*
		 * Read remote phone's public key Modulus and Exponent.
		 * Each one is written in its own packet.
		 */
		debug("Reading foreign mod, exp");
		SkullMessage modMessage = readMessage(dis, MessageType.PUBMOD);
		SkullMessage expMessage = readMessage(dis, MessageType.PUBEXP);
		
		BigInteger remotePubMod = new BigInteger(modMessage.getData());
		BigInteger remotePubExp = new BigInteger(expMessage.getData());
		info("Public Mod: " + remotePubMod);
		info("Public Exp: " + remotePubExp);
		
		/*
		 * Generate a public key for the remote phone from the
		 * MOD and EXP parts received.
		 */
		RSAPublicKeySpec remotePubKeySpec =
		new RSAPublicKeySpec(remotePubMod, remotePubExp);
		remotePublicKey = keygen.generatePublic(remotePubKeySpec);
		
	}
	
	public class CallThread extends Thread{

		private int debug(String msg){return SkullTalkService.debug("CALL:" + msg);}
		@SuppressWarnings("unused")
		private int info(String msg){return SkullTalkService.info("CALL" + msg);}
		
		private String remotePhoneNumber;
		public CallThread(String number){ remotePhoneNumber = number; }
		
		private void doHandshake(DataInputStream dis, DataOutputStream dos)
		throws InvalidKeySpecException, IOException,
		NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException
		{
			
			performHandshake(dis, dos);
		
			/*
			 * Create encrypt (outgoing) cipher from remote public key.
			 * Create decrypt (incoming) cipher from local private key.
			 */
			debug("Creating ciphers");
			Cipher encryptor =
			Cipher.getInstance(remotePublicKey.getAlgorithm());
			encryptor.init(Cipher.ENCRYPT_MODE, remotePublicKey);
			
			Cipher decryptor =
			Cipher.getInstance(localKeys.getPrivate().getAlgorithm());
			decryptor.init(Cipher.DECRYPT_MODE, localKeys.getPrivate());
			
			debug("Reading AES key");
			SkullMessage keyMessage = readMessage(dis, MessageType.SESKEY);
			byte[] keyMessageData = decryptor.doFinal(keyMessage.getData());
			sessionKey = new SecretKeySpec(keyMessageData, Constants.SYMMALGORITHM);
		}
		
		public void run(){
			try{
				
				/*
				 * Get the contact info of the remote phone from server.
				 * If could not get info, report error to UI and quit.
				 */
				Object[] retvals = new Object[1];
				serverComm.request(remotePhoneNumber, retvals);
				InetSocketAddress remotePhoneAddress = (InetSocketAddress) retvals[0];
				
				/* 
				 * Open connection to remote phone.
				 * This is analogous to dialing.
				 * Open input and output streams.
				 * If could not connect, report error to UI and cleanup + quit.
				 */
				debug("CONNECT - Connecting to " + remotePhoneAddress);
				callSocket = new Socket();
				
				//TODO: Abort connection on hangup received. Should be done quickly.
				callSocket.connect(remotePhoneAddress, 30000);
				if (! callSocket.isConnected()){ 
					throw new SocketException();
				}
				debug("Connected to remote address: " + remotePhoneAddress);
				DataInputStream dis =
				new DataInputStream(callSocket.getInputStream());
				DataOutputStream dos =
				new DataOutputStream(callSocket.getOutputStream());
	
				debug("Writing CALL packet");
				/*
				 * The calling phone first sends a CALL packet containing
				 * info about the caller and calling phone.
				 */
				
				sendMessage(dos, new SkullMessage(MessageType.CALL, phoneNumber.getBytes()));
				
				debug("Receiving response");
				/*
				 * Make sure we receive an ACCEPT packet in return or fail.
				 */
				SkullMessage response = readMessage(dis);
				
				if (response.getType() == MessageType.BUSY){
					throw new IOException("Remote phone busy");
				}
				if (response.getType() != MessageType.ACCEPT){
					throw new IOException("Unexpected packet type");
				}

				/*
				 * Both phones at this point perform the handshake.
				 * The handshake is the same for
				 * calling (CallThread) and receiving (AcceptThread)
				 * phones.
				 */
				doHandshake(dis, dos);
				
				/*
				 * At this point, we have exchanged keys and are ready
				 * to talk.
				 * Start TalkThread and terminate this CallThread.
				 */
				internalStartTalking();
				
			}catch (InvalidKeySpecException ikse){
				Log.e(TAG, "Invalid Key Spec Exception", ikse);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ikse.getMessage()));
				hangup();
				closeCall();
				hangup();
			}catch (IOException ioe){
				Log.e(TAG, "IO Exception", ioe);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ioe.getMessage()));
				closeCall();
				hangup();
			} catch (InvalidKeyException ike) {
				Log.e(TAG, "IO Exception", ike);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ike.getMessage()));
				closeCall();
				hangup();
			} catch (NoSuchAlgorithmException nsae) {
				Log.e(TAG, "IO Exception", nsae);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, nsae.getMessage()));
				closeCall();
				hangup();
			} catch (NoSuchPaddingException nspe) {
				Log.e(TAG, "IO Exception", nspe);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, nspe.getMessage()));
				closeCall();
				hangup();
			} catch (IllegalBlockSizeException e) {
				Log.e(TAG, "Illegal Block Size Exception", e);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, e.getMessage()));
				closeCall();
				hangup();
			} catch (BadPaddingException e) {
				Log.e(TAG, "BadPadding Exception", e);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, e.getMessage()));
				closeCall();
				hangup();
			}
		}
	}
	
	public class AcceptThread extends Thread{
		private int debug(String msg){return SkullTalkService.debug("ACCEPT:" + msg);}
		private int info(String msg){return SkullTalkService.info("ACCEPT:" + msg);}
		private int listenPort;
		public AcceptThread(int port){ listenPort = port; }
		
		private void doHandshake(DataInputStream dis, DataOutputStream dos)
		throws NoSuchAlgorithmException, NoSuchPaddingException,
		InvalidKeySpecException, InvalidKeyException, IOException, IllegalBlockSizeException, BadPaddingException
		{
			performHandshake(dis, dos);
		
			/*
			 * Create encrypt (outgoing) cipher from remote public key.
			 * Create decrypt (incoming) cipher from local private key.
			 */
			Cipher encryptor =
			Cipher.getInstance(remotePublicKey.getAlgorithm());
			encryptor.init(Cipher.ENCRYPT_MODE, remotePublicKey);
			Cipher decryptor =
			Cipher.getInstance(localKeys.getPrivate().getAlgorithm());
			decryptor.init(Cipher.DECRYPT_MODE, localKeys.getPrivate());
			
			debug("Creating secret key");
			//Generate an AES key
			KeyGenerator kgen = KeyGenerator.getInstance(
			Constants.SYMMALGORITHM);
			kgen.init(Constants.SYMKEYSIZE);
			SecretKey sesKeyTemp = kgen.generateKey();
			
			sessionKey = new SecretKeySpec(
			sesKeyTemp.getEncoded(), sesKeyTemp.getAlgorithm());
			
			debug("Writing secret key");
			//Send the AES key
			//FIXME: Try to remedy a 'Too much data for RSA block' error.
			byte[] sessionKeyBytes = sessionKey.getEncoded();
			byte[] sessionKeyBytesMessage = encryptor.doFinal(sessionKeyBytes);
			
			sendMessage(dos, new SkullMessage(MessageType.SESKEY, sessionKeyBytesMessage));
		}
		
		public void run(){
			
			/*
			 * Initialize server socket.
			 * Set listen port and timeout.
			 * If this fails, then we won't be able to receive calls. 
			 */
			debug("Initializing server socket");
			ServerSocket serverSocket;
			
			try{
				serverSocket = new ServerSocket(listenPort);
				//serverSocket.setSoTimeout(250);
			}catch (IOException e){
				throw new Error(e);
			}

			/*
			 * Main call accept loop.
			 * Each iteration, it attempts to accept an incoming connection
			 * and respond.
			 * If the accept() call times-out, the loop can check for
			 * application status changes (closing) and try to accept() again.
			 */
			while (callState != CallState.STOPPED){
				Socket newConnection;
				
				try{
					
					/*
					 * Wait for a connection.
					 * If connection times-out an exception is thrown
					 * and we just loop again.
					 */
					debug("Waiting for a connection");
					newConnection = serverSocket.accept();
					debug("Connection established ");
					DataInputStream dis =
					new DataInputStream(newConnection.getInputStream());
					DataOutputStream dos =
					new DataOutputStream(newConnection.getOutputStream());
					
					/*
					 * Read the initial CALL packet sent by the caller.
					 */
					debug("Reading client info");
					SkullMessage clientInfo = readMessage(dis, MessageType.CALL);
					
					String newRemotePhoneNumber = new String(clientInfo.getData());

					/*
					 * After receiving the CALL packet,
					 * return an ACCEPT or a BUSY packet.
					 */
					if (callState != CallState.LISTENING){
						info("Call rejected; busy.");
						sendMessage(dos, new SkullMessage(MessageType.BUSY));
						newConnection.close();
						continue;
					} else {
						info("Call accepted");
						sendMessage(dos, new SkullMessage(MessageType.ACCEPT));
						callSocket = newConnection;
						remotePhoneNumber = newRemotePhoneNumber;
					}
					
					
					/*
					 * Both phones at this point perform the handshake.
					 * The handshake is the same for
					 * calling (CallThread) and receiving (AcceptThread)
					 * phones.
					 */
					doHandshake(dis, dos);
					
					/*
					 * At this point, we have exchanged keys and are ready
					 * to talk.
					 * Start TalkThread and continue accepting.
					 */
					internalStartTalking();
					
				}catch (SocketTimeoutException ste){
					
					/*
					 * On socket timeout, check to see if service is stopped
					 * and then loop again (attempting another accept).
					 */
					if (callState == CallState.STOPPED){
						acceptThread = null;
						return;
					}
					continue;

				}catch (IOException ioe){
					Log.e(TAG, "error'd", ioe);
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, ioe.getMessage()));
					closeCall();
					hangup();
				} catch (InvalidKeyException ike) {
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, ike.getMessage()));
					closeCall();
					hangup();
				} catch (InvalidKeySpecException ikse) {
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, ikse.getMessage()));
					closeCall();
					hangup();
				} catch (NoSuchAlgorithmException nsae) {
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, nsae.getMessage()));
					closeCall();
					hangup();
				} catch (NoSuchPaddingException nspe) {
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, nspe.getMessage()));
					closeCall();
					hangup();
				} catch (IllegalBlockSizeException e) {
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, e.getMessage()));
					closeCall();
					hangup();
				} catch (BadPaddingException e) {
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, e.getMessage()));
					closeCall();
					hangup();
				} 
				
			}
		}
	}
	
	public class TalkThread extends Thread{

		private int debug(String msg){return SkullTalkService.debug("TALK:" + msg);}
		private int info(String msg){return SkullTalkService.info("TALK:" + msg);}
		
		public void run(){

			DataInputStream in;
			DataOutputStream out;
			AudioTrack aout = null;
			AudioRecord ain = null;
			
			try{
				/*
				 * Create encrypt (outgoing) cipher from remote public key.
				 * Create decrypt (incoming) cipher from local private key.
				 */
				debug("Creating encrypted streams");
				Cipher encryptor =
				Cipher.getInstance(sessionKey.getAlgorithm());
				encryptor.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKey.getEncoded(), sessionKey.getAlgorithm()));
				Cipher decryptor =
				Cipher.getInstance(sessionKey.getAlgorithm());
				decryptor.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessionKey.getEncoded(), sessionKey.getAlgorithm()));
				 
				/*
				 * Open streams and prepare audio tracks.
				 */
				debug("Initializing audio transmission system");
				byte[] buf = new byte[1024 * 4];
				int bytesRead = 0;
				int readPackets = 0;
				in = new DataInputStream(new CipherInputStream(
						callSocket.getInputStream(), decryptor));
				out = new DataOutputStream(new CipherOutputStream(
						callSocket.getOutputStream(), encryptor));
				aout = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
						sampleRate, channelConfigOut, encoding, buf.length,
						AudioTrack.MODE_STREAM);
				ain = new AudioRecord(
						MediaRecorder.AudioSource.VOICE_RECOGNITION,
						sampleRate, channelConfigIn, encoding, buf.length);
				ain.startRecording();

				/*
				 * Main talking loop.
				 * Each iteration we record a block of audio data,
				 *     send it on an encrypted stream,
				 *     read a block from encrypted stream,
				 *     and play it back.
				 * The loop ends if the other phone hangs-up, disconnects
				 * or this phone hangs-up.
				 */
				debug("Starting to record");
				while (true) {

					if (ain.getRecordingState() !=
					AudioRecord.RECORDSTATE_RECORDING){
						throw new IOException("Audio record failure");
					}
					
					/*
					 * Read audio data from microphone.
					 * Don't bother if we aren't talking.
					 */
					if (callState == CallState.TALKING){
						bytesRead += ain.read(buf, bytesRead, buf.length);
					}
					
					/*
					 * Write a packet of voice data.
					 * If we are no longer TALKING, send HANGUP packet.
					 * Hangup if audio stops recording for whatever reason.
					 */
					if ((callState != CallState.TALKING) || (bytesRead < 0)){
						sendMessage(out, new SkullMessage(MessageType.HANGUP));
						break;
					}
					/*
					 * Only send data if we've got a substantial message to send.
					 */
					if(bytesRead >= 128){
						debug("Sending message");
						sendMessage(out, MessageType.VOICE, buf, bytesRead);
						bytesRead = 0;
						readPackets++;
					}

					/*
					 * Read a packet of voice data.
					 * If it is a HANGUP packet, then terminate thread.
					 * Read enough packets to flush out the cipher queue.
					 */
					if(readPackets  > 2){
						info("Reading message");
						SkullMessage audioDataIn = readMessage(in);
						if (audioDataIn.getType() == MessageType.HANGUP){
							throw new EOFException("Remote phone hung-up");
						} else if (audioDataIn.getType() != MessageType.VOICE){
							throw new IOException("Unexpected packet type");
						}
						
						/*
						 * Write audio data to speaker.
						 */
						aout.write(audioDataIn.getData(), 0, audioDataIn.getData().length);
					}
					if (aout.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
						aout.play();
					
				}
				
			}catch (IOException ioe){
				Log.e(TAG, "IO Error", ioe);
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ioe.getMessage()));
				closeCall();
				hangup();
			}catch (Exception e){
				closeCall();
				throw new Error(e);
			}finally{
				/*
				 * Make sure AudioRecord and AudioTrack are cleaned up.
				 * Make sure socket is closed and cleaned up.
				 */
				closeCall();
				
				if (ain != null)
					if (ain.getRecordingState() ==
					AudioRecord.RECORDSTATE_RECORDING)
						ain.stop();	
				if (aout != null)
					if (aout.getPlayState() ==
					AudioTrack.PLAYSTATE_PLAYING)
						aout.stop();
				if (callSocket != null){
					if (! callSocket.isClosed()){
						try{ callSocket.close(); }
						catch (IOException ioe2){}
						callSocket = null;
					}
				}
				talkThread = null;
			}
		}
	}
}
