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
import android.content.*;
import android.media.*;
import android.os.*;
import android.telephony.TelephonyManager;

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
	
	private SkullMessageFactory messageFact;
	private SkullUserInfoManager userManager;
	private final String phoneNumber;
	
	private Socket callSocket;
	private KeyPair localKeys;
	private PublicKey remotePublicKey;
	private Mac mac;
	
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
	private String serverIP = "10.0.2.2";
	private int serverPort = 9002;
	
	public SkullTalkService(Handler uiHandler, ContextWrapper context){
		this.uiHandler = uiHandler;
		phoneNumber = ((TelephonyManager) context.getSystemService(
		Context.TELEPHONY_SERVICE)).getLine1Number();
		serverComm = new SwitchStationClient();
	}
	
	public synchronized CallState getCallState(){
		return callState;
	}
	
	public synchronized void setCallState(CallState state){
		callState = state;
	}
	
	/* *********************************************************************
	 * Functions are synchronized so there won't be concurrent changes
	 * to the state of the service from the UI/TransmitThread/ReceiveThread
	 ***********************************************************************/
	
	public void start(){
		//TODO: Read public key from file
		keyManager = new SkullKeyManager(appContext);
		userManager = new SkullUserInfoManager(appContext);
		serverComm.connect(serverIP, serverPort);
		
		// TODO: How to find our own contact info?
//		serverComm.register(phoneNumber, new byte[]{1,1,1,1}, 9001);

		callState = CallState.LISTENING;
		acceptThread = new AcceptThread(9002);
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
	
	public synchronized void hangup(){
		if (callState == CallState.TALKING){
			callState = CallState.LISTENING;
			uiHandler.sendEmptyMessage(2);
			talkThread = null;
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
		if (dis.readByte() != MessageType.CALL.ordinal()){
			throw new IOException("Unexpected packet type");
		}
	}

// TODO: what to do with this?
/* *****************************************
 * Didn't know what to do with these but I thought they should be in here
 *
	private void writeMessage(DataOutputStream dos, SkullMessage mes)
	throws IOException{
		byte[] hash = mes.getHash();
		byte[] data = mes.getData();
		SkullMessage.MessageType mesType = mes.getType();
		
		dos.write(Constants.MAGICBYTES);
		dos.write(hash);
		dos.writeByte((byte) mesType.ordinal());
		dos.writeInt(data.length);
		dos.write(data);
	}
	
	private SkullMessage readMessage(InputStream s) throws IOException{
		SkullMessage rMessage = null;
		
		//      Read MAC from the stream.
		//      Read Type from the stream.
		//      Read data length from the stream.
		//      Read data from the stream.
		//      Construct a SkullMessage
		//      Return our SkullMessage
		return rMessage;
	}
*/

	
	/**
	 * Key generation and exchange occurs here.
	 */
	private void performHandshake(DataInputStream dis, DataOutputStream dos)
	throws IOException, InvalidKeySpecException {

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
		localKeys = keyManager.getKeys();
		RSAPublicKey pub = (RSAPublicKey) localKeys.getPublic();
		localPubMod = pub.getModulus();
		localPubExp = pub.getPublicExponent();
		
		/*
		 * Write our public key Modulus and Exponent.
		 * Each one is written in its own packet.
		 */
		dos.write(Constants.MAGICBYTES);
		dos.write((byte) MessageType.PUBMOD.ordinal());
		dos.writeInt(localPubMod.toByteArray().length);
		dos.write(localPubMod.toByteArray());
		dos.write(Constants.MAGICBYTES);
		dos.write((byte) MessageType.PUBEXP.ordinal());
		dos.writeInt(localPubExp.toByteArray().length);
		dos.write(localPubExp.toByteArray());
		
		/*
		 * Read remote phone's public key Modulus and Exponent.
		 * Each one is written in its own packet.
		 */
		readMagic(dis);
		readType(dis, MessageType.PUBMOD);
		byte[] remotePubModBytes = new byte[dis.readInt()];
		dis.readFully(remotePubModBytes);
		readMagic(dis);
		readType(dis, MessageType.PUBEXP);
		byte[] remotePubExpBytes = new byte[dis.readInt()];
		dis.readFully(remotePubExpBytes);
		BigInteger remotePubMod = new BigInteger(remotePubModBytes);
		BigInteger remotePubExp = new BigInteger(remotePubExpBytes);
		
		/*
		 * Generate a public key for the remote phone from the
		 * MOD and EXP parts received.
		 */
		RSAPublicKeySpec remotePubKeySpec =
		new RSAPublicKeySpec(remotePubMod, remotePubExp);
		remotePublicKey = keygen.generatePublic(remotePubKeySpec);
		
		// TODO: figure this out:
		//       Generate a session key
		//       Generate a MAC key
		//       Send session, MAC key through encrypted thinger.
		
	}
	
	public class CallThread extends Thread{
		private String remotePhoneNumber;
		public CallThread(String number){ remotePhoneNumber = number; }
		public void run(){
			try{
				
				/*
				 * Get the contact info of the remote phone from server.
				 * If could not get info, report error to UI and quit.
				 */
				Object[] retvals = new Object[2];
				serverComm.request(remotePhoneNumber, retvals);
				int ip32 = ((Integer) retvals[0]).intValue();
				String ipString =
				Constants.ipBytesToString(
				Constants.ipIntToBytes(ip32));
				int port32 = ((Short) retvals[1]).shortValue();
				InetSocketAddress remotePhoneAddress =
				InetSocketAddress.createUnresolved(ipString, port32);
				
				/* 
				 * Open connection to remote phone.
				 * This is analogous to dialing.
				 * Makes 300 connect attempts at 100ms a piece for 30s timeout.
				 * Open input and output streams.
				 * If could not connect, report error to UI and cleanup + quit.
				 */
				callSocket = new Socket();
				for (int x = 0; x < 300; ++x){
					try{
						callSocket.connect(remotePhoneAddress, 100);
					}catch (SocketTimeoutException ste){
						continue;
					}catch (Exception e){
						throw new Error(e);
					}
				}
				if (! callSocket.isConnected()){
					throw new SocketException();
				}
				DataInputStream dis =
				new DataInputStream(callSocket.getInputStream());
				DataOutputStream dos =
				new DataOutputStream(callSocket.getOutputStream());
	
				/*
				 * The calling phone first sends a CALL packet containing
				 * info about the caller and calling phone.
				 */
				dos.write(Constants.MAGICBYTES);
				dos.write((byte) MessageType.CALL.ordinal());
				dos.writeInt(phoneNumber.length());
				dos.write(phoneNumber.getBytes());
				
				/*
				 * Make sure we receive an ACCEPT packet in return or fail.
				 */
				readMagic(dis);
				byte messageType = dis.readByte();
				if (messageType == MessageType.BUSY.ordinal()){
					throw new IOException("Remote phone busy");
				}
				if (messageType != MessageType.ACCEPT.ordinal()){
					throw new IOException("Unexpected packet type");
				}

				/*
				 * Both phones at this point perform the handshake.
				 * The handshake is the same for
				 * calling (CallThread) and receiving (AcceptThread)
				 * phones.
				 */
				performHandshake(dis, dos);
				
				/*
				 * At this point, we have exchanged keys and are ready
				 * to talk.
				 * Start TalkThread and terminate this CallThread.
				 */
				internalStartTalking();
				
			}catch (InvalidKeySpecException ikse){
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ikse.getMessage()));
			}catch (IOException ioe){
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ioe.getMessage()));
			}finally{

				/*
				 * Make sure socket is cleaned up.
				 */
				if (callSocket.isConnected()){
					try{ callSocket.close(); }
					catch (IOException ioe2){}
				}
				callSocket = null;
				callThread = null;
				
			}
		}
	}
	
	public class AcceptThread extends Thread{
		private int listenPort;
		public AcceptThread(int port){ listenPort = port; }
		public void run(){
			
			/*
			 * Initialize server socket.
			 * Set listen port and timeout.
			 * If this fails, then we won't be able to receive calls. 
			 */
			ServerSocket serverSocket;
			try{
				serverSocket = new ServerSocket(listenPort);
				serverSocket.setSoTimeout(100);
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
					newConnection = serverSocket.accept();
					DataInputStream dis =
					new DataInputStream(newConnection.getInputStream());
					DataOutputStream dos =
					new DataOutputStream(newConnection.getOutputStream());
					
					/*
					 * Read the initial CALL packet sent by the caller.
					 */
					readMagic(dis);
					readType(dis, MessageType.CALL);
					int remotePhoneNumberLength = dis.readInt();
					byte[] remotePhoneNumberBytes =
					new byte[remotePhoneNumberLength];
					dis.readFully(remotePhoneNumberBytes);
					remotePhoneNumber = new String(remotePhoneNumberBytes);

					/*
					 * After receiving the CALL packet,
					 * return an ACCEPT or a BUSY packet.
					 */
					if (callState != CallState.LISTENING){
						dos.write(Constants.MAGICBYTES);
						dos.write((byte) MessageType.BUSY.ordinal());
						continue;
					} else {
						dos.write(Constants.MAGICBYTES);
						dos.write((byte) MessageType.ACCEPT.ordinal());
					}
					
					/*
					 * Both phones at this point perform the handshake.
					 * The handshake is the same for
					 * calling (CallThread) and receiving (AcceptThread)
					 * phones.
					 */
					performHandshake(dis, dos);
					
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
					
				}catch (InvalidKeySpecException ikse){
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, ikse.getMessage()));
				}catch (IOException ioe){
					uiHandler.sendMessage(Message.obtain(
					uiHandler, 1, ioe.getMessage()));
				}finally{
					
					/*
					 * Make sure socket is cleaned up.
					 */
					if (! callSocket.isClosed()){
						try{ callSocket.close(); }
						catch (IOException ioe2){}
					}
					callSocket = null;
					acceptThread = null;
					
				}
			}
		}
	}
	
	public class TalkThread extends Thread{
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
				Cipher encryptor =
				Cipher.getInstance(remotePublicKey.getAlgorithm());
				encryptor.init(Cipher.ENCRYPT_MODE, remotePublicKey);
				Cipher decryptor =
				Cipher.getInstance(localKeys.getPrivate().getAlgorithm());
				decryptor.init(Cipher.DECRYPT_MODE, localKeys.getPrivate());
				
				/*
				 * Open streams and prepare audio tracks.
				 */
				byte[] buf = new byte[1024 * 4];
				int bytesRead = 0;
				in = new DataInputStream(new CipherInputStream(
						callSocket.getInputStream(), encryptor));
				out = new DataOutputStream(new CipherOutputStream(
						callSocket.getOutputStream(), decryptor));
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
						bytesRead = ain.read(buf, 0, buf.length);
					}
					
					/*
					 * Write a packet of voice data.
					 * If we are no longer TALKING, send HANGUP packet.
					 * Hangup if audio stops recording for whatever reason.
					 */
					if ((callState != CallState.TALKING) || (bytesRead < 0)){
						out.write(Constants.MAGICBYTES);
						out.writeByte((byte) MessageType.HANGUP.ordinal());
						break;
					}
					out.write(Constants.MAGICBYTES);
					out.writeByte((byte) MessageType.VOICE.ordinal());
					out.writeInt(bytesRead);
					out.write(buf, 0, bytesRead);

					/*
					 * Read a packet of voice data.
					 * If it is a HANGUP packet, then terminate thread.
					 */
					readMagic(in);
					int messageType = in.readByte();
					if (messageType == MessageType.HANGUP.ordinal()){
						throw new EOFException("Remote phone hung-up");
					} else if (messageType != MessageType.VOICE.ordinal()){
						throw new IOException("Unexpected packet type");
					}
					bytesRead = in.readInt();
					in.readFully(buf, 0, bytesRead);
					
					/*
					 * Write audio data to speaker.
					 */
					aout.write(buf, 0, bytesRead);
					if (aout.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
						aout.play();
					
				}
				
			}catch (IOException ioe){
				uiHandler.sendMessage(Message.obtain(
				uiHandler, 1, ioe.getMessage()));
				hangup();
			}catch (Exception e){
				throw new Error(e);
			}finally{
				
				/*
				 * Make sure AudioRecord and AudioTrack are cleaned up.
				 * Make sure socket is closed and cleaned up.
				 */
				if (ain != null)
					if (ain.getRecordingState() ==
					AudioRecord.RECORDSTATE_RECORDING)
						ain.stop();	
				if (aout != null)
					if (aout.getPlayState() ==
					AudioTrack.PLAYSTATE_PLAYING)
						aout.stop();
				if (! callSocket.isClosed()){
					try{ callSocket.close(); }
					catch (IOException ioe2){}
					callSocket = null;
				}
				talkThread = null;
				
			}
		}
	}
}
